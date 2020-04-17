@file:Suppress("DEPRECATION") //due to Camera v1 API

package pl.todoit.IndustrialWebViewWithQr.fragments

import android.Manifest
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import kotlinx.android.synthetic.main.fragment_scan_qr.view.*
import pl.todoit.IndustrialWebViewWithQr.App
import pl.todoit.IndustrialWebViewWithQr.NavigationRequest
import pl.todoit.IndustrialWebViewWithQr.R
import pl.todoit.IndustrialWebViewWithQr.model.*
import timber.log.Timber

class CamSurfaceHolderCallbacks(
        private val _camera : CameraData,
        private val textureView: TextureView) : TextureView.SurfaceTextureListener {

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
        Timber.d("onSurfaceTextureSizeChanged() hasValue?=${surface != null}")

    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
        Timber.d("onSurfaceTextureUpdated() hasValue?=${surface != null}")

    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
        Timber.d("onSurfaceTextureDestroyed()")
        _camera.camera.stopPreview()
        return true
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
        Timber.d("onSurfaceTextureAvailable() holder hasValue?=${surface != null}")

        if (surface == null) {
            return
        }

        _camera.camera.setPreviewTexture(surface)
        _camera.camera.startPreview()
    }
}

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

    override suspend fun maybeGetBeforeNavigationError(act: AppCompatActivity) : String? {
        val result = initializeFirstBackFacingCamera(act)

        when(result) {
            is Result.Error -> return result.error
            is Result.Ok -> {
                _camera = result.value
                return null }
        }
    }

    private suspend fun navigationCancelScanning() {
        req()?.scanResult?.send(null)
        App.Instance.navigation.send(NavigationRequest.ScanQr_Back())
    }

    override fun onBackPressed() = App.Instance.launchCoroutine { navigationCancelScanning() }

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
        camSurfaceView.surfaceTextureListener = CamSurfaceHolderCallbacks(_camera, camSurfaceView)

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
