package pl.todoit.IndustrialWebViewWithQr.fragments

import android.Manifest
import android.hardware.Camera
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import pl.todoit.IndustrialWebViewWithQr.App
import pl.todoit.IndustrialWebViewWithQr.NavigationRequest
import pl.todoit.IndustrialWebViewWithQr.R
import pl.todoit.IndustrialWebViewWithQr.model.IBeforeNavigationValidation
import pl.todoit.IndustrialWebViewWithQr.model.IProcessesBackButtonEvents
import pl.todoit.IndustrialWebViewWithQr.model.IRequiresPermissions
import pl.todoit.IndustrialWebViewWithQr.model.ScanRequest
import timber.log.Timber

val backFacingCamera = Camera.CameraInfo.CAMERA_FACING_BACK

class CamSurfaceHolderCallbacks(private var host: ScanQrFragment) : SurfaceHolder.Callback {
    override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) {
        Timber.d("surfaceChanged()")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder?) {
        Timber.d("surfaceDestroyed()")
    }

    override fun surfaceCreated(holder: SurfaceHolder?) {
        Timber.d("surfaceCreated()")

        val cam = Camera.open(host.getCameraIndex())
        cam.setPreviewDisplay(holder)

        cam.startPreview()
    }
}

class ScanQrFragment : Fragment(), IProcessesBackButtonEvents, IRequiresPermissions, IBeforeNavigationValidation {
    private var _cameraIndex : Int = -1
    private lateinit var _cameraInfo : Camera.CameraInfo

    fun getCameraIndex() = _cameraIndex
    private fun req() : ScanRequest? = App.Instance.scanQrFragmentParams.get()

    override fun getRequiredAndroidManifestPermissions(): Array<String> = arrayOf(Manifest.permission.CAMERA)

    override fun onRequiredPermissionRejected(perm:String) {
        App.Instance.launchCoroutine { navigationCancelScanning() }
    }

    override suspend fun maybeGetBeforeNavigationError() : String? {
        val cameras = sequence {
            for (i in 0..Camera.getNumberOfCameras()) {
                val ci = Camera.CameraInfo()
                Camera.getCameraInfo(i, ci)
                yield(Pair(i, ci))
            }
        }

        val cameraIndexAndInfo = cameras
            .filter { it.second.facing == backFacingCamera }
            .firstOrNull()
            ?: return "No back facing camera found"

        _cameraIndex = cameraIndexAndInfo.first
        _cameraInfo = cameraIndexAndInfo.second

        Timber.d("will use camera index=$_cameraIndex")

        return null
    }

    private suspend fun navigationCancelScanning() {
        req()?.scanResult?.send(null)
        App.Instance.navigation.send(NavigationRequest.ScanQr_Back())
    }

    override fun onBackPressed() {
        App.Instance.launchCoroutine { navigationCancelScanning() }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var result = inflater.inflate(R.layout.fragment_scan_qr, container, false)

        result.findViewById<TextView>(R.id.qrLabel).text = req()?.label
        result.findViewById<TextView>(R.id.qrRegexp).text = req()?.regexp

        val camSurfaceView = result.findViewById<SurfaceView>(R.id.camSurfaceView)

        camSurfaceView.holder.addCallback(CamSurfaceHolderCallbacks(this))

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
