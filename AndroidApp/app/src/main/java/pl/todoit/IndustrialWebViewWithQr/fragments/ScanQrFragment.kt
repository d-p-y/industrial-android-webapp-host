@file:Suppress("DEPRECATION") //due to Camera v1 API

package pl.todoit.IndustrialWebViewWithQr.fragments

import android.Manifest
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.zxing.*
import pl.todoit.IndustrialWebViewWithQr.*
import pl.todoit.IndustrialWebViewWithQr.fragments.barcodeDecoding.BarcodeDecoderSurfaceTextureListener
import pl.todoit.IndustrialWebViewWithQr.fragments.barcodeDecoding.BarcodeDecoderForCameraPreview
import pl.todoit.IndustrialWebViewWithQr.fragments.barcodeDecoding.BarcodeDecoderNotification
import pl.todoit.IndustrialWebViewWithQr.model.*
import pl.todoit.IndustrialWebViewWithQr.model.Result
import pl.todoit.IndustrialWebViewWithQr.model.extensions.sendAndClose
import timber.log.Timber
import java.io.ByteArrayInputStream

class ScanQrFragment : Fragment(), IProcessesBackButtonEvents, IRequiresPermissions,
                       IBeforeNavigationValidation, IMaybeHasTitle {
    private lateinit var _camera: CameraData
    private lateinit var _decoder : BarcodeDecoderForCameraPreview

    private fun req() : Pair<ScanRequest,OverlayImage?>? = App.Instance.scanQrFragmentParams.get()

    override fun getRequiredAndroidManifestPermissions(): Array<String> = arrayOf(Manifest.permission.CAMERA)
    override fun onRequiredPermissionRejected(rejectedPerms:List<String>) =
        App.Instance.navigator.postNavigateTo(NavigationRequest.ScanQr_Back())

    override fun getTitleMaybe() =
        when(val x = req()?.first?.layoutStrategy) {
            is FitScreenLayoutStrategy ->x.screenTitle ?: "Scan QR code"
            is MatchWidthWithFixedHeightLayoutStrategy -> null
            else -> {
                Timber.e("unsupported layoutstartegy - cannot determine screen name")
                null}
        }

    private fun backButtonCausesCancellation() : Boolean =
        when(req()?.first?.layoutStrategy) {
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

    private fun setTorchStateEnabled(toggled : Boolean, torchToggler:ImageView) {
        if (toggled) {
            //enabling
            torchToggler.setImageResource(R.drawable.ic_wb_sunny_white)
            torchToggler.alpha = 0.65f
            return
        }

        //disabling
        torchToggler.setImageResource(R.drawable.ic_wb_sunny_black)
        torchToggler.alpha = 0.4f
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var result = inflater.inflate(R.layout.fragment_scan_qr, container, false)

        val req = req()

        if (req == null) {
            Timber.e("req is null")
            return result
        }

        val camSurfaceView = result.findViewById<TextureView>(R.id.camSurfaceView)
        val scannerOverlay = result.findViewById<ImageView>(R.id.scannerOverlay)
        val torchToggler = result.findViewById<ImageView>(R.id.torchToggler)

        var toggled = false
        setTorchStateEnabled(toggled, torchToggler)

        torchToggler.setOnClickListener {
            Timber.d("toggling flash")
            toggled = !toggled
            _camera.setTorchState(toggled)
            setTorchStateEnabled(toggled, torchToggler)
        }

        val overlayImg = req.second
        val screenDensityDpi = activity?.resources?.displayMetrics?.densityDpi

        if (overlayImg != null && scannerOverlay != null && screenDensityDpi != null) {
            val img = ByteArrayInputStream(overlayImg.content)
            val bmp = BitmapFactory.decodeStream(img).apply { density = screenDensityDpi }

            scannerOverlay.visibility = View.GONE //initially hidden as scanner starts active (hide early to avoid flicker)
            scannerOverlay.setImageDrawable(BitmapDrawable(activity?.resources, bmp))

            App.Instance.launchCoroutine {
                Timber.d("show/hide overview image listener - starting")

                val stateUpdate = req.first.scanResult.openSubscription()
                for (item in stateUpdate) {
                    val maybeVis =
                        when(item) {
                            is ScannerStateChange.Paused -> View.VISIBLE
                            is ScannerStateChange.Resumed -> View.GONE
                            is ScannerStateChange.Cancelled -> View.GONE
                            else -> null
                        }

                    if (maybeVis != null) {
                        Timber.d("show/hide overview image listener - changing due to info=$item")
                        scannerOverlay.visibility = maybeVis
                    }
                }
                Timber.d("show/hide overview image listener - ended")
            }
        }

        val layoutProp = computeParamsForTextureView(_camera, req.first.layoutStrategy)

        if (layoutProp.dimensions != null) {
            if (layoutProp.marginURBL != null) {
                container?.setPadding(
                    layoutProp.marginURBL[3], layoutProp.marginURBL[0], layoutProp.marginURBL[1], layoutProp.marginURBL[2])
            } else {
                container?.setPadding(0, 0, 0, 0) //to reset former request (if there was any)
            }

            camSurfaceView.layoutParams = FrameLayout.LayoutParams(
                layoutProp.dimensions.first.toAndroid(),
                layoutProp.dimensions.second.toAndroid()
            )
        }

        camSurfaceView.setTransform(layoutProp.matrix)

        _decoder =
            BarcodeDecoderForCameraPreview(
                arrayOf(BarcodeFormat.QR_CODE),
                req.first.postSuccess,
                _camera,
                {
                    when(it) {
                        is BarcodeDecoderNotification.GotBarcode -> {
                            if (App.Instance.currentConnection.hapticFeedbackOnBarcodeRecognized) {
                                activity?.performHapticFeedback()
                            }

                            req.first.scanResult.send(ScannerStateChange.Scanned(it.decodedBarcode))
                            if (!it.expectMoreMessages) {
                                App.Instance.navigator.navigateTo(NavigationRequest.ScanQr_Scanned())
                            }
                        }
                        is BarcodeDecoderNotification.Cancelling -> {
                            req.first.scanResult.sendAndClose(ScannerStateChange.Cancelled())
                            App.Instance.navigator.navigateTo(NavigationRequest.ScanQr_Back())
                        }
                        is BarcodeDecoderNotification.Pausing  ->
                            req.first.scanResult.send(ScannerStateChange.Paused())
                        is BarcodeDecoderNotification.Resuming  ->
                            req.first.scanResult.send(ScannerStateChange.Resumed())
                    }
                })

        camSurfaceView.surfaceTextureListener = BarcodeDecoderSurfaceTextureListener(_camera, _decoder)

        return result
    }
}
