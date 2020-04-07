package pl.todoit.IndustrialWebViewWithQr.fragments

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import kotlinx.coroutines.channels.SendChannel
import pl.todoit.IndustrialWebViewWithQr.App
import pl.todoit.IndustrialWebViewWithQr.NavigationRequest
import pl.todoit.IndustrialWebViewWithQr.R
import pl.todoit.IndustrialWebViewWithQr.model.IProcessesBackButtonEvents
import pl.todoit.IndustrialWebViewWithQr.model.IRequiresPermissions
import pl.todoit.IndustrialWebViewWithQr.model.ScanRequest
import timber.log.Timber

class ScanQrFragment : Fragment(), IProcessesBackButtonEvents, IRequiresPermissions {
    private fun req() : ScanRequest? = App.Instance.scanQrFragmentParams.get()

    override fun getRequiredAndroidManifestPermissions(): Array<String> = arrayOf(Manifest.permission.CAMERA)

    override fun onRequiredPermissionRejected(perm:String) {
        App.Instance.launchCoroutine(suspend { navigationCancelScanning() })
    }

    private suspend fun navigationCancelScanning() {
        req()?.scanResult?.send(null)
        App.Instance.navigation.send(NavigationRequest.ScanQr_Back())
    }

    override fun onBackPressed() {
        App.Instance.launchCoroutine(suspend {navigationCancelScanning()})
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        var result = inflater.inflate(R.layout.fragment_scan_qr, container, false)

        result.findViewById<TextView>(R.id.qrLabel).text = req()?.label
        result.findViewById<TextView>(R.id.qrRegexp).text = req()?.regexp

        result.findViewById<Button>(R.id.btnSimulateScan).setOnClickListener {
            var scannedQr = result.findViewById<TextView>(R.id.qrInput)

            if (scannedQr != null) {
                var qr = scannedQr.text.toString()

                App.Instance.launchCoroutine(suspend {
                    req()?.scanResult?.send(qr)
                    App.Instance.navigation.send(NavigationRequest.ScanQr_Scanned())
                    Unit
                })
            }
        }

        result.findViewById<Button>(R.id.btnSimulateCancel).setOnClickListener {
            var scannedQr = result.findViewById<TextView>(R.id.qrInput)

            if (scannedQr != null) {
                App.Instance.launchCoroutine(suspend { navigationCancelScanning() })
            }
        }

        return result
    }
}
