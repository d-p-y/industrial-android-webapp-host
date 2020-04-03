package pl.todoit.IndustrialWebViewWithQr

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import kotlinx.coroutines.channels.Channel
import timber.log.Timber

class ScanQrFragment(val navigation:Channel<NavigationRequest>, val req:ScanRequest) : Fragment(),IBackAcceptingFragment {
    override fun onBackPressed() {
        var act = activity

        if (act !is MainActivity) {
            Timber.e("activity is not MainActivity")
            return
        }

        act.launchCoroutine(suspend { navigation.send(NavigationRequest.ScanQr_Back()) })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,savedInstanceState: Bundle?): View? {
        var result = inflater.inflate(R.layout.fragment_scan_qr, container, false)

        result.findViewById<TextView>(R.id.qrLabel).text = req.label
        result.findViewById<TextView>(R.id.qrRegexp).text = req.regexp

        result.findViewById<Button>(R.id.btnSimulateScan).setOnClickListener {
            var act = activity
            var scannedQr = result.findViewById<TextView>(R.id.qrInput)

            if (act is MainActivity && scannedQr != null) {
                var qr = scannedQr.text.toString()

                act.launchCoroutine(suspend {
                    req.scanResult.send(qr)
                    navigation.send(NavigationRequest.ScanQr_Scanned())
                })
            }
        }

        result.findViewById<Button>(R.id.btnSimulateCancel).setOnClickListener {
            var act = activity

            var scannedQr = result.findViewById<TextView>(R.id.qrInput)

            if (act is MainActivity && scannedQr != null) {
                act.launchCoroutine(suspend {
                    req.scanResult.send(null)
                    navigation.send(NavigationRequest.ScanQr_Back())
                })
            }
        }

        return result
    }
}
