@file:Suppress("DEPRECATION")

package pl.todoit.IndustrialWebViewWithQr.fragments.barcodeDecoding

import android.hardware.Camera
import com.google.zxing.BarcodeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pl.todoit.IndustrialWebViewWithQr.App
import pl.todoit.IndustrialWebViewWithQr.model.CameraData
import pl.todoit.IndustrialWebViewWithQr.model.WidthAndHeight
import pl.todoit.IndustrialWebViewWithQr.model.extensions.diffInMilisecTo
import timber.log.Timber
import java.util.*

class BarcodeDecoderCameraPreviewCallback(
    barcodeFormats : Array<BarcodeFormat>,
    private val _camera: CameraData,
    val onScan : suspend (String?) -> Unit
) : Camera.PreviewCallback {
    private val _barcodeDecoder =
        ImageWithBarcodeConsumerWorker(
            barcodeFormats
        )
    private var _milisecsWithoutRequestingImage : Long = 0
    private var _senderSleepingSince : Date? = null
    private var _hasFocus = false
    private var _sent = 0
    private var _skipped = 0
    private var _received = 0
    private var _stopSending = false

    init {
        App.Instance.launchCoroutine {
            withContext(Dispatchers.Default) {
                _barcodeDecoder.startDecoderWorker()
            }
        }
        App.Instance.launchCoroutine { responseLoop() }
    }

    private suspend fun responseLoop() {
        Timber.d("start listening for barcode decode requests")

        while(!_stopSending) {
            var answer = _barcodeDecoder.outputChannel().receive()
            _received++

            with(answer.stats) { addSkipped(_skipped) }

            Timber.d("received barcodeDecoder reply answer=${answer.resultBarcode} decodingPerItem[ms]=${answer.stats.timeSpentPerItemMs()} percentageConsumed=${answer.stats.itemsConsumedPercent()}")

            _stopSending = true

            Timber.d("notifying about decoded barcode")
            onScan(answer.resultBarcode)
        }

        Timber.d("stopped listening for barcode decode requests")
    }

    fun cancelPreviewing() {
        Timber.d("received request to cancel previewing")
        _stopSending = true
    }

    override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
        //Timber.d("onPreviewFrame() hasData?=${data != null}")

        if (data == null) {
            return
        }

        val itm =
            ImageWithBarcode(
                _hasFocus,
                WidthAndHeight(
                    _camera.camPreviewSize.widthWithoutRotation,
                    _camera.camPreviewSize.heightWithoutRotation
                ),
                data
            )

        var scheduled =
            if (_barcodeDecoder.inputChannel().offer(itm)) {
                _sent++
                true
            } else {
                _skipped++
                false
            }

        Timber.d("onPreviewFrame received=$_received sent=$_sent skipped=$_skipped _stopSending=$_stopSending scheduled=$scheduled")

        if (!_stopSending) {
            val sss = _senderSleepingSince
            if (!scheduled && sss == null) {
                _senderSleepingSince = Date()
                Timber.d("onPreviewFrame start counting skipping time")
            } else if (scheduled && sss != null) {
                _milisecsWithoutRequestingImage += sss.diffInMilisecTo(Date())
                _senderSleepingSince = null
                Timber.d("onPreviewFrame end counting skipping time")
            }

            _camera.camera.setOneShotPreviewCallback(this)
        }
    }

    fun setHasFocus(success: Boolean) {
        _hasFocus = success
    }
}