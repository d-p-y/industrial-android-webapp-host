package pl.todoit.IndustrialWebViewWithQr

import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast

class ScanQrFragment : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    fun setInput(self:ViewGroup, req:ScanRequest) {
        self.findViewById<TextView>(R.id.qrLabel)?.text = req.label
        self.findViewById<TextView>(R.id.qrRegexp)?.text = req.regexp
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,savedInstanceState: Bundle?): View? {
        var result = inflater.inflate(R.layout.fragment_scan_qr, container, false)

        result.findViewById<Button>(R.id.btnSimulateScan).setOnClickListener {
            var act = activity
            var app = act?.application

            var scannedQr = result.findViewById<TextView>(R.id.qrInput)

            if (act != null && app is App && scannedQr != null) {
                var qr = scannedQr.text.toString()
                Toast.makeText(act, "scanned ${qr}", Toast.LENGTH_SHORT).show()

                app.onQrScanSuccessImpl?.invoke(qr)
            }
        }

        result.findViewById<Button>(R.id.btnSimulateCancel).setOnClickListener {
            var act = activity
            var app = act?.application

            var scannedQr = result.findViewById<TextView>(R.id.qrInput)

            if (act != null && app is App && scannedQr != null) {
                app.onQrScanCancelImpl?.invoke(Unit)
            }
        }

        return result
    }
}
