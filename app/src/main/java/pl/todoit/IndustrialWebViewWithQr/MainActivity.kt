package pl.todoit.IndustrialWebViewWithQr

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
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

        var popupFrag = findViewById<FrameLayout>(R.id.popup_fragment)
        popupFrag.visibility = View.GONE

        var btnScanNeeded = findViewById<Button>(R.id.btnScanNeeded)
        btnScanNeeded.setOnClickListener {
            popupFrag.visibility = View.VISIBLE
        }
    }
}
