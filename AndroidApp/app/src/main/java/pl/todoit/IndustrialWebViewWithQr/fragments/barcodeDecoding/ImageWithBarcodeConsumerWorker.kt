package pl.todoit.IndustrialWebViewWithQr.fragments.barcodeDecoding

import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import pl.todoit.IndustrialWebViewWithQr.App
import pl.todoit.IndustrialWebViewWithQr.model.extensions.diffInMilisecTo
import pl.todoit.IndustrialWebViewWithQr.model.Result
import kotlinx.coroutines.channels.receiveOrNull
import timber.log.Timber
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

class ImageWithBarcodeConsumerWorker(val formats : Array<BarcodeFormat>) {
    private val _input = Channel<ImageWithBarcode>(App.Instance.imagesToDecodeQueueSize)
    private val _output = Channel<BarcodeReply>()
    private var _lastProducerDate : Date? = null

    fun toDecode() : SendChannel<ImageWithBarcode> = _input
    fun decoded() : ReceiveChannel<BarcodeReply> = _output

    fun clearToDecode() = App.Instance.launchCoroutine { _input.receiveAllPending().clear() }

    private fun decode(mfr: MultiFormatReader, toDecode: ImageWithBarcode) : Result<String, Exception> {
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

            return Result.Ok(
                mfr.decodeWithState(
                    bitmap
                ).text
            )
        } catch (re : Exception) {
            return Result.Error(re)
        } finally {
            mfr.reset()
        }
    }

    private suspend fun decodeOne(mfr: MultiFormatReader, stats: WorkerEstimator, toDecode: ImageWithBarcode) : String? {
        return when(val result = stats.measureConsumer { decode(mfr, toDecode) }) {
            is Result.Ok -> {
                Timber.d("MultiFormatReader for focused?=${toDecode.hasFocus} got code=${result.value}")

                _input.receiveAllPending() //purge queue so that new requests won't decode old queued code
                result.value
            }
            is Result.Error -> {
                Timber.d("MultiFormatReader for focused?=${toDecode.hasFocus} didn't decode because of ${result.error}")
                null
            }
        }
    }

    suspend fun startDecoderWorker() {
        Timber.i("barcodeDecoderWorker started")

        val startedAt = Date()
        val stats = WorkerEstimator()
        val mfr = createMultiFormatReaderFor(formats)

        while(!_input.isClosedForReceive) {
            var toDecodes = _input.receiveAllPending().sortedByDescending { it.hasFocus }.toMutableList()
            Timber.d("barcodeDecoderWorker received ${toDecodes.size} items of which focused ${toDecodes.filter { it.hasFocus }
                .count()}")

            stats.measureProducer(
                toDecodes
                    .map {
                        val last = _lastProducerDate
                        _lastProducerDate = it.requestedAt
                        last?.diffInMilisecTo(it.requestedAt) ?: -1 })

            var estimationMade = false

            while (toDecodes.size > 0) {
                if (!estimationMade) {
                    val shouldConsumeItemsCount = stats.shouldConsumeItemsCount()
                    if (shouldConsumeItemsCount != null) {
                        estimationMade = true
                        toDecodes = toDecodes.subList(0, shouldConsumeItemsCount)
                        Timber.d("barcodeDecoderWorker items list shortened to ${toDecodes.size}")

                        if (toDecodes.size <= 0) {
                            continue
                        }
                    }
                }

                val toDecode = toDecodes.first() //favor focused frames
                toDecodes.removeAt(0)

                val resultBarcode = decodeOne(mfr, stats, toDecode)

                if (resultBarcode != null) {
                    //no need to decode the rest + help with GC
                    stats.skipItemsFollowingSuccess(toDecodes.size)
                    toDecodes.clear() //no need to decode the rest

                    _output.send(
                        BarcodeReply(
                            resultBarcode,
                            stats.createSummary(startedAt)
                        )
                    )
                }
            }

            Timber.d("barcodeDecoderWorker finished with current batch")
        }
        Timber.d("barcodeDecoderWorker ending")
        _output.close()
    }
}
