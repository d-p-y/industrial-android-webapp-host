package pl.todoit.IndustrialWebViewWithQr

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup

class MainActivity : AppCompatActivity() {
    val webviewFragmentName = "base"
    val scanQrFragmentName = "popup"

    fun setQrPopup() : ScanQrFragment {
        var popupViewSetFragmentTran = supportFragmentManager.beginTransaction()
        var scanQrFrag = ScanQrFragment()
        popupViewSetFragmentTran.add(R.id.popup_fragment, scanQrFrag, scanQrFragmentName)
        popupViewSetFragmentTran.commit()
        return scanQrFrag
    }

    fun unsetQrPopup() {
        val frag = supportFragmentManager.findFragmentByTag(scanQrFragmentName)

        if (frag == null) {
            return
        }

        supportFragmentManager.beginTransaction().remove(frag).commit()
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.menuItemSettings) {
            Toast.makeText(this,"Settings run", Toast.LENGTH_SHORT).show()

            var app = application

            if (app is App) {
                app.showConnectionsSettings?.invoke(app.currentConnection)
            }

            return true
        }

        return super.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var toolBar = findViewById<Toolbar>(R.id.toolBar)
        setSupportActionBar(toolBar)

        var app = application

        if (app !is App) {
            return
        }

        app.showScanQrImpl = { x:ScanRequest ->
            runOnUiThread({
                val frag = setQrPopup()
                var popupFrag = findViewById<ViewGroup>(R.id.popup_fragment)
                frag.setInput(popupFrag, x)
                popupFrag.visibility = View.VISIBLE
            })
        }

        app.hideScanQrImpl = {
            runOnUiThread({
                var popupFrag = findViewById<ViewGroup>(R.id.popup_fragment)
                popupFrag.visibility = View.GONE
                unsetQrPopup()
            })
        }

        app.hideScanQrImpl?.invoke()
    }
}
