package pl.todoit.IndustrialWebViewWithQr

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout

class ScanQrFragment : Fragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,savedInstanceState: Bundle?): View? {
        var result = inflater.inflate(R.layout.fragment_scan_qr, container, false)

        var simulateScan = result.findViewById<Button>(R.id.btnSimulateScan)
        simulateScan.setOnClickListener {
            var act = activity

            if (act != null) {
                var popupView = act.findViewById<FrameLayout>(R.id.popup_fragment)
                popupView.visibility = View.GONE
            }
        }

        return result
    }
}
