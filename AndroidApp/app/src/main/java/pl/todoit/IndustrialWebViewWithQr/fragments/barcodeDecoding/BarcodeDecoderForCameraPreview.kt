@file:Suppress("DEPRECATION")

package pl.todoit.IndustrialWebViewWithQr.fragments.barcodeDecoding

import com.google.zxing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.sendBlocking
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import pl.todoit.IndustrialWebViewWithQr.App
import pl.todoit.IndustrialWebViewWithQr.model.*
import pl.todoit.IndustrialWebViewWithQr.model.extensions.toCloseable
import timber.log.Timber
import java.io.Closeable
import java.util.*

suspend fun <T,U> receiveFromAny(fst: ReceiveChannel<T>, snd : ReceiveChannel<U>) =
    select<Choice2<T, U>?> {
        fst.onReceiveOrNull { if (it == null) null else Choice2.Choice1Of2(it) }
        snd.onReceiveOrNull { if (it == null) null else Choice2.Choice2Of2(it) }
    }

sealed class BarcodeDecoderNotification {
    class GotBarcode(val decodedBarcode:ProcessorSuccess<String>, val expectMoreMessages:Boolean) : BarcodeDecoderNotification()
    class Cancelling() : BarcodeDecoderNotification()
    class Pausing() : BarcodeDecoderNotification()
    class Resuming() : BarcodeDecoderNotification()
}

fun newestFocusedFirst(inp:List<ImageWithBarcode>) : List<ImageWithBarcode> =
    inp.sortedByDescending { it.hasFocus }.sortedByDescending { it.requested }

class BarcodeDecoderForCameraPreview(
    _barcodeFormats : Array<BarcodeFormat>,
    private val _postScanBehavior:PauseOrFinish,
    private val _camera: CameraData,
    val notify : suspend (BarcodeDecoderNotification) -> Unit
) : Closeable {

    private val _barcodeDecoder = EstimatingConsumerWorker(
        { ZxingMultiFormatReader(_barcodeFormats) },
        ::newestFocusedFirst,
        App.Instance.maxComputationsAtOnce,
        { it.resetStats },
        { it.timeToProduceMs()}
    )
    private var _hasFocus = false
    private var _sent = 0
    private var _skipped = 0
    private var _received = 0
    private var _sendToDecoder = true
    private var _finish = false
    private var _resetStatsRequest = 0
    private var _onPreviewFrameRequested = false
    private var _requestedAt : Date? = null
    private val _commandChannel = Channel<CancelPauseResume>()

    init {
        App.Instance.launchCoroutine {
            withContext(Dispatchers.Default) {
                _barcodeDecoder.startConsumerWorker()
            }
        }
        App.Instance.launchCoroutine { responseLoop() }
    }

    override fun close() = cancelDecoder()

    fun cancelDecoder() {
        Timber.d("received request to cancel decoding")
        App.Instance.launchCoroutine { _commandChannel.send(CancelPauseResume.Cancel) }
    }

    fun resumeDecoder() {
        Timber.d("received request to resume decoding")
        App.Instance.launchCoroutine { _commandChannel.send(CancelPauseResume.Resume) }
    }

    private suspend fun onBarcode(answer: ProcessorSuccess<String>) {
        _received++

        with(answer.stats) { addSkipped(_skipped) }

        Timber.d("received barcodeDecoder reply answer=${answer.result} processingTimePerItem=${answer.stats.processingTimePerItemMs()}[ms] decodingLibraryTimePerItem=${answer.stats.oneItemDecodeTimeMs()}[ms] percentageConsumed=${answer.stats.itemsConsumedPercent()} photosProducedEvery=${answer.stats.productingEveryMs}[ms]")

        _sendToDecoder = false

        Timber.d("notifying about decoded barcode")
        notify(
            BarcodeDecoderNotification.GotBarcode(
                answer, _postScanBehavior == PauseOrFinish.Pause))
    }

    private suspend fun onCommand(value: CancelPauseResume) {
        _barcodeDecoder.clearToConsume() //ease GC and don't use old images in new requests

        when(value) {
            CancelPauseResume.Cancel -> {
                Timber.d("canceling decoding")
                _finish = true
                _sendToDecoder = false

                _barcodeDecoder.endConsumerWorker()
                notify(BarcodeDecoderNotification.Cancelling())
            }
            CancelPauseResume.Pause -> {
                Timber.d("pausing decoding")
                _sendToDecoder = false
                notify(BarcodeDecoderNotification.Pausing())
            }
            CancelPauseResume.Resume -> {
                Timber.d("resuming decoding")
                notify(BarcodeDecoderNotification.Resuming())
                _sendToDecoder = true

                //reset stats
                _resetStatsRequest++

                _skipped = 0
                _received = 0
                _sent = 0

                requestCameraFrameCapture()
            }
        }
    }

    private suspend fun responseLoop() {
        Timber.d("start listening for barcode decode requests")

        var done = false

        while(!_finish || done) {
            when(val barcodeOrCommand = receiveFromAny(_barcodeDecoder.consumed(), _commandChannel)) {
                null -> {
                    Timber.d("responseLoop ending due to channel close")
                    done = true
                }
                is Choice2.Choice1Of2 -> {
                    onBarcode(barcodeOrCommand.value)

                    when(_postScanBehavior) {
                        PauseOrFinish.Finish -> onCommand(CancelPauseResume.Cancel)
                        PauseOrFinish.Pause -> onCommand(CancelPauseResume.Pause)
                    }
                }
                is Choice2.Choice2Of2 -> onCommand(barcodeOrCommand.value)
            }
        }

        Timber.d("stopped listening for barcode decode requests")
    }

    fun requestCameraFrameCapture() {
        _onPreviewFrameRequested = true
        _requestedAt = Date()

        val captured = Channel<ByteArray>(1) //nonblocking for sender

        App.Instance.launchCoroutine {
            captured.toCloseable().use {
                while(true) {
                    if (!_sendToDecoder) {
                        Timber.d("not requesting setOneShotPreviewCallback as decoder is not active")
                        break
                    }
                    _camera.camera.setOneShotPreviewCallback { data, _ -> captured.sendBlocking(data) }

                    val data =
                        withTimeoutOrNull(App.Instance.expectPictureTakenAtLeastAfterMs) {
                            captured.receive()
                        }

                    if (data != null) {
                        Timber.d("got requested camera frame in time")
                        cameraFrameCaptured(data)
                        break
                    }

                    Timber.e("didn't received requested camera frame in foreseen time - re-requesting") //E/Camera-JNI: Couldn't allocate byte array for JPEG data
                }
            }
        }
    }

    private fun cameraFrameCaptured(data: ByteArray?) {
        if (data == null) {
            Timber.e("null frame data")
            return
        }

        val resetStats = _resetStatsRequest > 0

        if (_resetStatsRequest > 0) {
            _resetStatsRequest--
        }

        val requestedAt = _requestedAt

        if (requestedAt == null) {
            Timber.e("_requestedAt is not available")
            return
        }

        val itm =
            ImageWithBarcode(
                requestedAt,
                _hasFocus,
                resetStats,
                WidthAndHeight(
                    _camera.camPreviewSize.widthWithoutRotation,
                    _camera.camPreviewSize.heightWithoutRotation
                ),
                data
            )

        _requestedAt = null

        var sent =
            if (!_sendToDecoder) {
                false
            } else if (_barcodeDecoder.toConsume().offer(itm)) {
                _sent++
                true
            } else {
                _skipped++
                false
            }

        Timber.d("time=${Date().time} onPreviewFrame received=$_received sent=$_sent skipped=$_skipped _sendToDecoder=$_sendToDecoder sent=$sent")

        if (_sendToDecoder) {
            requestCameraFrameCapture()
        }
    }

    fun setHasFocus(success: Boolean) {
        _hasFocus = success
    }
}
