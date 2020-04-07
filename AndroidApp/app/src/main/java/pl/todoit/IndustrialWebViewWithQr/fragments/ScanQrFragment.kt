package pl.todoit.IndustrialWebViewWithQr.fragments

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlinx.coroutines.channels.SendChannel
import pl.todoit.IndustrialWebViewWithQr.MainActivity
import pl.todoit.IndustrialWebViewWithQr.NavigationRequest
import pl.todoit.IndustrialWebViewWithQr.R
import pl.todoit.IndustrialWebViewWithQr.model.IProcessesBackButtonEvents
import pl.todoit.IndustrialWebViewWithQr.model.IRequiresPermissions
import pl.todoit.IndustrialWebViewWithQr.model.ScanRequest
import timber.log.Timber

class ScanQrFragment(
        private val navigation:SendChannel<NavigationRequest>,
        private val req: ScanRequest) : Fragment(), IProcessesBackButtonEvents, IRequiresPermissions {

    private fun getMainActivity() : MainActivity? {
        var act = activity

        if (act !is MainActivity) {
            Timber.e("activity is not MainActivity")
            return null
        }

        return act
    }

    override fun getRequiredAndroidManifestPermissions(): Array<String> = arrayOf(Manifest.permission.CAMERA)
    override fun onRequiredPermissionRejected(perm:String) {
        getMainActivity()?.launchCoroutine(suspend {
            navigation.send(NavigationRequest.ScanQr_Back())
        })
    }

    override fun onBackPressed() {
        getMainActivity()?.launchCoroutine(suspend { navigation.send(NavigationRequest.ScanQr_Back()) })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,savedInstanceState: Bundle?): View? {
        var result = inflater.inflate(R.layout.fragment_scan_qr, container, false)

        result.findViewById<TextView>(R.id.qrLabel).text = req.label
        result.findViewById<TextView>(R.id.qrRegexp).text = req.regexp

        result.findViewById<Button>(R.id.btnSimulateScan).setOnClickListener {
            var scannedQr = result.findViewById<TextView>(R.id.qrInput)

            if (scannedQr != null) {
                var qr = scannedQr.text.toString()

                getMainActivity()?.launchCoroutine(suspend {
                    req.scanResult.send(qr)
                    navigation.send(NavigationRequest.ScanQr_Scanned())
                })
            }
        }

        result.findViewById<Button>(R.id.btnSimulateCancel).setOnClickListener {
            var scannedQr = result.findViewById<TextView>(R.id.qrInput)

            if (scannedQr != null) {
                getMainActivity()?.launchCoroutine(suspend {
                    req.scanResult.send(null)
                    navigation.send(NavigationRequest.ScanQr_Back())
                })
            }
        }

        return result
    }
}
