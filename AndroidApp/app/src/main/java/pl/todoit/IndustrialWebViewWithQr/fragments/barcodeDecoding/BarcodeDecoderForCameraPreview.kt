@file:Suppress("DEPRECATION")

package pl.todoit.IndustrialWebViewWithQr.fragments.barcodeDecoding

import com.google.zxing.BarcodeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import pl.todoit.IndustrialWebViewWithQr.App
import pl.todoit.IndustrialWebViewWithQr.model.*
import timber.log.Timber

suspend fun <T,U> receiveFromAny(fst: ReceiveChannel<T>, snd : ReceiveChannel<U>) =
    select<Choice2<T, U>> {
        fst.onReceive { Choice2.Choice1Of2(it) }
        snd.onReceive { Choice2.Choice2Of2(it) }
    }

sealed class BarcodeDecoderNotification {
    class GotBarcode(val decodedBarcode:String, val expectMoreMessages:Boolean) : BarcodeDecoderNotification()
    class Cancelling() : BarcodeDecoderNotification()
    class Pausing() : BarcodeDecoderNotification()
    class Resuming() : BarcodeDecoderNotification()
}

class BarcodeDecoderForCameraPreview(
    _barcodeFormats : Array<BarcodeFormat>,
    private val _postScanBehavior:PauseOrFinish,
    private val _camera: CameraData,
    val notify : suspend (BarcodeDecoderNotification) -> Unit
) {
    private val _barcodeDecoder = ImageWithBarcodeConsumerWorker(_barcodeFormats)
    private var _hasFocus = false
    private var _sent = 0
    private var _skipped = 0
    private var _received = 0
    private var _sendToDecoder = true
    private var _finish = false
    private var _resetStatsRequest = 0
    private var _onPreviewFrameRequested = false
    private val _commandChannel = Channel<CancelPauseResume>()

    init {
        App.Instance.launchCoroutine {
            withContext(Dispatchers.Default) {
                _barcodeDecoder.startDecoderWorker()
            }
        }
        App.Instance.launchCoroutine { responseLoop() }
    }

    fun cancelDecoder() {
        Timber.d("received request to cancel decoding")
        App.Instance.launchCoroutine { _commandChannel.send(CancelPauseResume.Cancel) }
    }

    fun resumeDecoder() {
        Timber.d("received request to resume decoding")
        App.Instance.launchCoroutine { _commandChannel.send(CancelPauseResume.Resume) }
    }

    private suspend fun onBarcode(answer: BarcodeReply) {
        _received++

        with(answer.stats) { addSkipped(_skipped) }

        Timber.d("received barcodeDecoder reply answer=${answer.resultBarcode} decodingPerItem[ms]=${answer.stats.timeSpentPerItemMs()} percentageConsumed=${answer.stats.itemsConsumedPercent()}")

        _sendToDecoder = false

        Timber.d("notifying about decoded barcode")
        notify(
            BarcodeDecoderNotification.GotBarcode(
                answer.resultBarcode, _postScanBehavior == PauseOrFinish.Pause))
    }

    private suspend fun onCommand(value: CancelPauseResume) {
        _barcodeDecoder.clearToDecode() //ease GC and don't use old images in new requests

        when(value) {
            CancelPauseResume.Cancel -> {
                Timber.d("canceling decoding")
                _finish = true
                _sendToDecoder = false
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

        while(!_finish) {
            when(val barcodeOrCommand = receiveFromAny(_barcodeDecoder.decoded(), _commandChannel)) {
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
        _camera.camera.setOneShotPreviewCallback({ data, _ -> cameraFrameCaptured(data) })
    }

    private fun cameraFrameCaptured(data: ByteArray?) {
        if (data == null) {
            return
        }

        val resetStats = _resetStatsRequest > 0

        if (_resetStatsRequest > 0) {
            _resetStatsRequest--
        }

        val itm =
            ImageWithBarcode(
                _hasFocus,
                resetStats,
                WidthAndHeight(
                    _camera.camPreviewSize.widthWithoutRotation,
                    _camera.camPreviewSize.heightWithoutRotation
                ),
                data
            )

        var sent =
            if (!_sendToDecoder) {
                false
            } else if (_barcodeDecoder.toDecode().offer(itm)) {
                _sent++
                true
            } else {
                _skipped++
                false
            }

        Timber.d("onPreviewFrame received=$_received sent=$_sent skipped=$_skipped _sendToDecoder=$_sendToDecoder sent=$sent")

        if (_sendToDecoder) {
            requestCameraFrameCapture()
        }
    }

    fun setHasFocus(success: Boolean) {
        _hasFocus = success
    }
}
