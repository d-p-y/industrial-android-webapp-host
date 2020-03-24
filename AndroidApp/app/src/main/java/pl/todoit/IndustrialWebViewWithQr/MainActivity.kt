package pl.todoit.IndustrialWebViewWithQr

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.fragment.app.FragmentContainer

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var baseViewSetFragmentTran = supportFragmentManager.beginTransaction()
        var webViewFrag = WebViewFragment()
        baseViewSetFragmentTran.add(R.id.base_fragment, webViewFrag, "base")
        baseViewSetFragmentTran.commit()

        var popupViewSetFragmentTran = supportFragmentManager.beginTransaction()
        var scanQrFrag = ScanQrFragment()
        popupViewSetFragmentTran.add(R.id.popup_fragment, scanQrFrag, "popup")
        popupViewSetFragmentTran.commit()

        var app = application

        if (app !is App) {
            return
        }

        app.showScanQrImpl = { x:ScanRequest ->
            runOnUiThread({
                var popupFrag = findViewById<ViewGroup>(R.id.popup_fragment)
                scanQrFrag.setInput(popupFrag, x)
                popupFrag.visibility = View.VISIBLE
            })
        }

        app.hideScanQrImpl = {
            runOnUiThread({
                var popupFrag = findViewById<ViewGroup>(R.id.popup_fragment)
                popupFrag.visibility = View.GONE
            })
        }

        app?.hideScanQrImpl?.invoke()
    }
}
