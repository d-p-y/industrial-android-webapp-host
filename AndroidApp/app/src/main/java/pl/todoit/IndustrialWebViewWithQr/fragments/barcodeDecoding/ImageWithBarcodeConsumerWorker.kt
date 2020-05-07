package pl.todoit.IndustrialWebViewWithQr.fragments.barcodeDecoding

import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import pl.todoit.IndustrialWebViewWithQr.App
import pl.todoit.IndustrialWebViewWithQr.model.Result
import pl.todoit.IndustrialWebViewWithQr.model.extensions.*
import timber.log.Timber
import java.io.Closeable
import java.util.*

fun <T : Enum<T>> Array<T>.toEnumSet(clazz:Class<T>) : EnumSet<T> {
    val decodeFormats = EnumSet.noneOf(clazz)
    decodeFormats.addAll(this.asIterable())
    return decodeFormats
}

/**
 * @return suspends and returns at least one element
 */
suspend fun <T> ReceiveChannel<T>.receiveAllPending() : MutableList<T> {
    val result = mutableListOf<T>()

    val value = this.receiveOrNull()

    if (value == null) {
        return result
    }

    result.add(value)

    while (!this.isEmpty && !this.isClosedForReceive) {
        val value = this.receiveOrNull()

        if (value == null) {
            break
        }

        result.add(value)
    }

    return result
}

fun createMultiFormatReaderFor(formats : Array<BarcodeFormat>) : MultiFormatReader {
    val mfr = MultiFormatReader()
    val hints = HashMap<DecodeHintType, Any>()
    hints[DecodeHintType.POSSIBLE_FORMATS] = formats.toEnumSet(BarcodeFormat::class.java) as Any
    mfr.setHints(hints)
    return mfr
}

fun asClosable(vararg itms : Closeable) = Closeable { itms.forEach { it.close() } }

/**
 * @param prioritizeFramesStrategy sorts available frames according to expected usefulness from most useful to less useful
 */
class ImageWithBarcodeConsumerWorker(
    val formats : Array<BarcodeFormat>,
    val prioritizeFramesStrategy:(List<ImageWithBarcode>)->List<ImageWithBarcode>,
    val maxSimultaneousComputations : Int
) {
    private val _input = Channel<ImageWithBarcode>(App.Instance.imagesToDecodeQueueSize)
    private val _output = Channel<BarcodeReply>()
    private var _stats = WorkerEstimator(maxSimultaneousComputations)
    private val _mfrs = (0 until maxSimultaneousComputations).map { createMultiFormatReaderFor(formats) }

    fun toDecode() : SendChannel<ImageWithBarcode> = _input
    fun decoded() : ReceiveChannel<BarcodeReply> = _output

    fun clearToDecode() = App.Instance.launchCoroutine { _input.receiveAllPending().clear() }

    private fun decode(mfr: MultiFormatReader, toDecode: ImageWithBarcode) : Result<String, Exception> =
        try {
            val src = PlanarYUVLuminanceSource(
                toDecode.imageData,
                toDecode.imageDim.width, toDecode.imageDim.height,
                0, 0,
                toDecode.imageDim.width, toDecode.imageDim.height,
                false
            )

            val bitmap = BinaryBitmap(
                HybridBinarizer(src)
            )

            Result.Ok(mfr.decodeWithState(bitmap).text)
        } catch (re : Exception) {
            Result.Error(re)
        } finally {
            mfr.reset()
        }

    private fun decodeOne(mfr: MultiFormatReader, toDecode: ImageWithBarcode) : String? {
        return when(val result = decode(mfr, toDecode)) {
            is Result.Ok -> {
                Timber.d("time=${Date().time} MultiFormatReader for focused?=${toDecode.hasFocus} got code=${result.value}")
                result.value
            }
            is Result.Error -> {
                Timber.d("time=${Date().time} MultiFormatReader for focused?=${toDecode.hasFocus} didn't decode because of ${result.error}")
                null
            }
        }
    }

    suspend fun startDecoderWorker() {
        Timber.i("barcodeDecoderWorker started with parallelism=$maxSimultaneousComputations")

        val toDoBatch = Channel<Pair<MultiFormatReader,ImageWithBarcode>>(maxSimultaneousComputations)
        val decodedWithMillis = Channel<Pair<String?,Int>>(maxSimultaneousComputations)

        asClosable(_output.toCloseable(), toDoBatch.toCloseable(), decodedWithMillis.toCloseable()).use {
            //workers will be auto closed once scope ends
            (0 until maxSimultaneousComputations).forEach {
                App.Instance.launchParallelInBackground {
                    Timber.d("startDecoderWorker starting worker id=$it")
                    for ((mfr, img) in toDoBatch) {
                        val started = Date()
                        val resultCode = decodeOne(mfr, img)
                        val tookMs = started.diffInMilisecTo(Date())
                        decodedWithMillis.send(Pair(resultCode, tookMs.toInt()))
                    }
                    Timber.d("startDecoderWorker ending worker id=$it")
                }
            }

            while (!_input.isClosedForReceive) {
                var toDecodes = prioritizeFramesStrategy(_input.receiveAllPending()).toMutableList()
                Timber.d("barcodeDecoderWorker time=${Date().time} received ${toDecodes.size} items of which focused ${toDecodes.filter { it.hasFocus }.count()}")

                if (toDecodes.any { it.resetStats }) {
                    Timber.d("barcodeDecoderWorker got resetStats request")
                    _stats = WorkerEstimator(maxSimultaneousComputations)
                }

                _stats.measureProducer(toDecodes.map { it.timeToProduceMs() })

                var estimationMade = false

                while (toDecodes.size > 0) {
                    _stats.batchConsumeStarts()

                    if (!estimationMade) {
                        val shouldConsumeItemsCount = _stats.shouldConsumeItemsCount()
                        if (shouldConsumeItemsCount != null) {
                            estimationMade = true

                            val oldSize = toDecodes.size
                            toDecodes = toDecodes.subList(0, shouldConsumeItemsCount)
                            Timber.d("barcodeDecoderWorker items list shortened from $oldSize to ${toDecodes.size} items")

                            if (toDecodes.size <= 0) {
                                continue
                            }
                        }
                    }

                    val batch = toDecodes.popAtMostFirstItems(maxSimultaneousComputations)
                    Timber.d("barcodeDecoderWorker will now process ${batch.size} items in parallel")

                    batch
                        .zip(_mfrs)
                        .forEach { (img, mfr) -> toDoBatch.send(Pair(mfr, img)) }

                    val workerAnswers = decodedWithMillis.receiveExactly(batch.size)
                    workerAnswers.forEach { _stats.consumerMeasurementRegister(it.second) }

                    _stats.batchConsumeEnds()

                    val maybeBarcode = workerAnswers
                        .filter { it.first != null }
                        .map { it.first }
                        .firstOrNull()

                    if (maybeBarcode != null) {
                        //no need to decode the rest + help with GC

                        _input.receiveAllPending() //purge queue to ease GC
                        _stats.ignoreItemsFollowingSuccess(toDecodes.size)
                        toDecodes.clear() //no need to decode the rest

                        _output.send(
                            BarcodeReply(maybeBarcode, _stats.createSummary()))
                    }
                }

                Timber.d("barcodeDecoderWorker time=${Date().time} finished with current batch")
            }
            Timber.d("barcodeDecoderWorker ending")
        }
    }

    fun endDecoderWorker() = _input.close()
}
