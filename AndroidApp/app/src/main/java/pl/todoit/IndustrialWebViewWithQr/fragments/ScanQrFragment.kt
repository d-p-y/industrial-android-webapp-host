@file:Suppress("DEPRECATION") //due to Camera v1 API

package pl.todoit.IndustrialWebViewWithQr.fragments

import android.Manifest
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.zxing.*
import pl.todoit.IndustrialWebViewWithQr.App
import pl.todoit.IndustrialWebViewWithQr.NavigationRequest
import pl.todoit.IndustrialWebViewWithQr.R
import pl.todoit.IndustrialWebViewWithQr.fragments.barcodeDecoding.BarcodeDecoderSurfaceTextureListener
import pl.todoit.IndustrialWebViewWithQr.fragments.barcodeDecoding.BarcodeDecoderForCameraPreview
import pl.todoit.IndustrialWebViewWithQr.fragments.barcodeDecoding.BarcodeDecoderNotification
import pl.todoit.IndustrialWebViewWithQr.model.*
import pl.todoit.IndustrialWebViewWithQr.model.Result
import pl.todoit.IndustrialWebViewWithQr.model.extensions.sendAndClose
import pl.todoit.IndustrialWebViewWithQr.performHapticFeedback
import timber.log.Timber

class ScanQrFragment : Fragment(), IProcessesBackButtonEvents, IRequiresPermissions,
                       IBeforeNavigationValidation, IMaybeHasTitle {
    private lateinit var _camera: CameraData
    private lateinit var _decoder : BarcodeDecoderForCameraPreview

    private fun req() : ScanRequest? = App.Instance.scanQrFragmentParams.get()

    override fun getRequiredAndroidManifestPermissions(): Array<String> = arrayOf(Manifest.permission.CAMERA)
    override fun onRequiredPermissionRejected(rejectedPerms:List<String>) =
        App.Instance.launchCoroutine { App.Instance.navigation.send(NavigationRequest.ScanQr_Back()) }

    override fun getTitleMaybe() =
        when(val x = req()?.layoutStrategy) {
            is FitScreenLayoutStrategy ->x.screenTitle ?: "Scan QR code"
            is MatchWidthWithFixedHeightLayoutStrategy -> null
            else -> {
                Timber.e("unsupported layoutstartegy - cannot determine screen name")
                null}
        }

    private fun backButtonCausesCancellation() : Boolean =
        when(req()?.layoutStrategy) {
            is FitScreenLayoutStrategy -> true
            is MatchWidthWithFixedHeightLayoutStrategy -> false
            else -> {
                Timber.e("unsupported layoutstartegy - cannot determine if backbutton should be supported")
                false}
        }

    override suspend fun maybeGetBeforeNavigationError(act: AppCompatActivity) : String? =
        when(val result = initializeFirstBackFacingCamera(act)) {
            is Result.Error -> result.error
            is Result.Ok -> {
                _camera = result.value
                null }
        }

    fun onReceivedScanningResumeRequest() = _decoder.resumeDecoder()
    fun onReceivedScanningCancellationRequest() = _decoder.cancelDecoder()

    override suspend fun onBackPressedConsumed() : Boolean {
        val backbuttonSupported = backButtonCausesCancellation()
        Timber.d("does current layoutstrategy determine that backbutton causes cancellation?=$backbuttonSupported")
        if (backbuttonSupported) {
            _decoder.cancelDecoder()
        }
        return backbuttonSupported
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var result = inflater.inflate(R.layout.fragment_scan_qr, container, false)

        val req = req()

        if (req == null) {
            Timber.e("req is null")
            return result
        }

        result.findViewById<TextView>(R.id.qrLabel).text = ""
        result.findViewById<TextView>(R.id.qrRegexp).text = ""

        val camSurfaceView = result.findViewById<TextureView>(R.id.camSurfaceView)

        val layoutProp = computeParamsForTextureView(_camera, req.layoutStrategy)

        if (layoutProp.dimensions != null) {
            if (layoutProp.marginURBL != null) {
                container?.setPadding(
                    layoutProp.marginURBL[3], layoutProp.marginURBL[0], layoutProp.marginURBL[1], layoutProp.marginURBL[2])
            } else {
                container?.setPadding(0, 0, 0, 0) //to reset former request (if any)
            }

            camSurfaceView.layoutParams = LinearLayout.LayoutParams(
                layoutProp.dimensions.first.toAndroid(),
                layoutProp.dimensions.second.toAndroid()
            )
        }

        camSurfaceView.setTransform(layoutProp.matrix)
        _decoder =
            BarcodeDecoderForCameraPreview(
                arrayOf(BarcodeFormat.QR_CODE),
                req.postSuccess,
                _camera,
                {
                    when(it) {
                        is BarcodeDecoderNotification.GotBarcode -> {
                            if (App.Instance.currentConnection.hapticFeedbackOnBarcodeRecognized) {
                                activity?.performHapticFeedback()
                            }

                            req.scanResult.send(Choice2.Choice1Of2(it.decodedBarcode))
                            if (!it.expectMoreMessages) {
                                req.scanResult.close()
                                App.Instance.navigation.send(NavigationRequest.ScanQr_Scanned())
                            }
                        }
                        is BarcodeDecoderNotification.Cancelling -> {
                            req.scanResult.sendAndClose(Choice2.Choice2Of2(Unit))
                            App.Instance.navigation.send(NavigationRequest.ScanQr_Back())
                        }
                    }
                })

        camSurfaceView.surfaceTextureListener = BarcodeDecoderSurfaceTextureListener(_camera, _decoder)

        return result
    }
}
