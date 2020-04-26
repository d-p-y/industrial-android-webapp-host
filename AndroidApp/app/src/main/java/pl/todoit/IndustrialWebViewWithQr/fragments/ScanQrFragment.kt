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
import pl.todoit.IndustrialWebViewWithQr.fragments.barcodeDecoding.BarcodeDecoderCameraPreviewCallback
import pl.todoit.IndustrialWebViewWithQr.model.*
import pl.todoit.IndustrialWebViewWithQr.model.Result
import timber.log.Timber

class ScanQrFragment : Fragment(), IProcessesBackButtonEvents, IRequiresPermissions,
                       IBeforeNavigationValidation, IMaybeHasTitle {
    private lateinit var _camera: CameraData

    private fun req() : ScanRequest? = App.Instance.scanQrFragmentParams.get()

    override fun getRequiredAndroidManifestPermissions(): Array<String> = arrayOf(Manifest.permission.CAMERA)
    override fun onRequiredPermissionRejected(rejectedPerms:List<String>) = App.Instance.launchCoroutine { navigationCancelScanning() }
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

    override suspend fun maybeGetBeforeNavigationError(act: AppCompatActivity) : String? {
        val result = initializeFirstBackFacingCamera(act)

        when(result) {
            is Result.Error -> return result.error
            is Result.Ok -> {
                _camera = result.value
                return null }
        }
    }

    fun onReceivedScanningCancellationRequest(promiseId:String) {
        val matchingJsPromiseId = req()?.jsPromiseId == promiseId
        Timber.d("will cancel scanning request if jsPromiseId matches result=$matchingJsPromiseId")
        if (matchingJsPromiseId) {
            App.Instance.launchCoroutine { navigationCancelScanning() }
        }
    }

    private suspend fun navigationCancelScanning() {
        req()?.scanResult?.send(null)
        App.Instance.navigation.send(NavigationRequest.ScanQr_Back())
    }

    override suspend fun onBackPressedConsumed() : Boolean {
        val backbuttonSupported = backButtonCausesCancellation()
        Timber.d("does current layoutstrategy determines that backbutton should cause cancellation?=$backbuttonSupported")
        if (backbuttonSupported) {
            App.Instance.launchCoroutine { navigationCancelScanning() }
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
                container?.setPadding(layoutProp.marginURBL[3], layoutProp.marginURBL[0], layoutProp.marginURBL[1], layoutProp.marginURBL[2])
            } else {
                container?.setPadding(0, 0, 0, 0) //to reset former request (if any)
            }

            camSurfaceView.layoutParams = LinearLayout.LayoutParams(
                layoutProp.dimensions.first.toAndroid(),
                layoutProp.dimensions.second.toAndroid()
            )
        }

        camSurfaceView.setTransform(layoutProp.matrix)
        val camPrev =
            BarcodeDecoderCameraPreviewCallback(
                arrayOf(BarcodeFormat.QR_CODE),
                _camera,
                {
                    if (App.Instance.currentConnection.hapticFeedbackOnBarcodeRecognized) {
                        activity?.window?.decorView?.performHapticFeedback(
                            HapticFeedbackConstants.VIRTUAL_KEY,
                            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                    }

                    req.scanResult.send(it)
                    App.Instance.navigation.send(NavigationRequest.ScanQr_Scanned())
                })
        camSurfaceView.surfaceTextureListener = BarcodeDecoderSurfaceTextureListener(_camera, camPrev)

        result.findViewById<Button>(R.id.btnSimulateScan).setOnClickListener {
            var scannedQr = result.findViewById<TextView>(R.id.qrInput)

            if (scannedQr != null) {
                var qr = scannedQr.text.toString()

                App.Instance.launchCoroutine {
                    req()?.scanResult?.send(qr)
                    App.Instance.navigation.send(NavigationRequest.ScanQr_Scanned())
                    Unit
                }
            }
        }

        result.findViewById<Button>(R.id.btnSimulateCancel).setOnClickListener {
            var scannedQr = result.findViewById<TextView>(R.id.qrInput)

            if (scannedQr != null) {
                App.Instance.launchCoroutine { navigationCancelScanning() }
            }
        }

        return result
    }
}
