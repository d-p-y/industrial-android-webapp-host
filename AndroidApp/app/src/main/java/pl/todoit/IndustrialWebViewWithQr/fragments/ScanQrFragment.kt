@file:Suppress("DEPRECATION") //due to Camera v1 API

package pl.todoit.IndustrialWebViewWithQr.fragments

import android.Manifest
import android.content.res.Resources
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
import pl.todoit.IndustrialWebViewWithQr.model.extensions.closeOnDestroy
import pl.todoit.IndustrialWebViewWithQr.model.extensions.sendAndClose
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File

fun setTorchStateEnabled(toggled : Boolean, torchToggler:ImageView) {
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

fun drawableFromFileWithoutAutoScalling(inp:File, res:Resources) : Drawable {
    val bmp = BitmapFactory.decodeFile(inp.absolutePath).apply { density = res.displayMetrics.densityDpi }
    return BitmapDrawable(res, bmp)
}

class ScanQrReq(val details:ScanRequest, val overlayImg:File?)

class ScanQrFragment : Fragment(), IProcessesBackButtonEvents, IRequiresPermissions,
                       IBeforeNavigationValidation, IMaybeHasTitle {
    private lateinit var _camera: CameraData
    private lateinit var _decoder : BarcodeDecoderForCameraPreview

    lateinit var req : ScanQrReq

    override fun getNeededAndroidManifestPermissions(): Array<String> = arrayOf(Manifest.permission.CAMERA)
    override fun onNeededPermissionRejected(rejectedPerms:List<String>) : PermissionRequestRejected {
        App.Instance.navigator.postNavigateTo(NavigationRequest.ScanQr_Back())
        return PermissionRequestRejected.MayNotOpenFragment
    }

    override fun getTitleMaybe() =
        when(val x = req.details.layoutStrategy) {
            is FitScreenLayoutStrategy ->x.screenTitle ?: "Scan QR code"
            is MatchWidthWithFixedHeightLayoutStrategy -> null
            else -> {
                Timber.e("unsupported layoutstartegy - cannot determine screen name")
                null}
        }

    private fun backButtonCausesCancellation() : Boolean =
        when(req.details.layoutStrategy) {
            is FitScreenLayoutStrategy -> true
            is MatchWidthWithFixedHeightLayoutStrategy -> false
            else -> {
                Timber.e("unsupported layoutstartegy - cannot determine if backbutton should be supported")
                false}
        }

    override suspend fun maybeGetBeforeNavigationError(act: AppCompatActivity) : String? =
        when(val result = initializeFirstBackFacingCameraWithContinuousFocus(act)) {
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

        val act = activity
        if (act !is MainActivity) {
            Timber.e("not running within MainActivity")
            return null
        }

        lifecycle.closeOnDestroy(_camera)

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

        val overlayImg = req.overlayImg
        val screenDensityDpi = act.resources

        Timber.d("overlayimage in request?=${overlayImg?.absolutePath}")

        if (overlayImg != null && scannerOverlay != null && screenDensityDpi != null) {
            scannerOverlay.setImageDrawable(drawableFromFileWithoutAutoScalling(overlayImg, act.resources))
            scannerOverlay.visibility = View.GONE //initially hidden as scanner starts active (hide early to avoid flicker)

            App.Instance.launchCoroutine {
                Timber.d("show/hide overview image listener - starting")

                val stateUpdate = req.details.scanResult.openSubscription()
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

        val layoutProp = computeParamsForTextureView(_camera, req.details.layoutStrategy)

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
                req.details.postSuccess,
                _camera,
                {
                    when(it) {
                        is BarcodeDecoderNotification.GotBarcode -> {
                            if (App.Instance.currentConnection.hapticFeedbackOnBarcodeRecognized) {
                                act.performHapticFeedback()
                            }

                            act.playSound(SndItem.ScanSuccess)

                            req.details.scanResult.send(ScannerStateChange.Scanned(it.decodedBarcode))
                            if (!it.expectMoreMessages) {
                                App.Instance.navigator.navigateTo(NavigationRequest.ScanQr_Scanned())
                            }
                        }
                        is BarcodeDecoderNotification.Cancelling -> {
                            req.details.scanResult.sendAndClose(ScannerStateChange.Cancelled())
                            App.Instance.navigator.navigateTo(NavigationRequest.ScanQr_Back())
                        }
                        is BarcodeDecoderNotification.Pausing  ->
                            req.details.scanResult.send(ScannerStateChange.Paused())
                        is BarcodeDecoderNotification.Resuming  ->
                            req.details.scanResult.send(ScannerStateChange.Resumed())
                    }
                })
        lifecycle.closeOnDestroy(_decoder)

        camSurfaceView.surfaceTextureListener = BarcodeDecoderSurfaceTextureListener(_camera, _decoder)

        return result
    }
}
