package pl.todoit.IndustrialWebViewWithQr

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.widget.Toolbar

class MainActivity : AppCompatActivity() {
    val webviewFragmentName = "baseWebBrowser"
    val connsSettsFragmentName = "baseConnsSetts"
    val scanQrFragmentName = "qrPopup"

    fun setWebBrowserBase() : WebViewFragment {
        var baseViewSetFragmentTran = supportFragmentManager.beginTransaction()
        var frag = WebViewFragment()
        baseViewSetFragmentTran.add(R.id.base_fragment, frag, webviewFragmentName)
        baseViewSetFragmentTran.commit()
        return frag
    }

    fun setConnectionsSettingsBase() : ConnectionsSettingsFragment {
        var baseViewSetFragmentTran = supportFragmentManager.beginTransaction()
        var frag = ConnectionsSettingsFragment()
        baseViewSetFragmentTran.add(R.id.base_fragment, frag, connsSettsFragmentName)
        baseViewSetFragmentTran.commit()
        return frag
    }

    fun setQrPopup() : ScanQrFragment {
        var popupViewSetFragmentTran = supportFragmentManager.beginTransaction()
        var scanQrFrag = ScanQrFragment()
        popupViewSetFragmentTran.add(R.id.popup_fragment, scanQrFrag, scanQrFragmentName)
        popupViewSetFragmentTran.commit()
        return scanQrFrag
    }

    fun unsetFragment(fragmentTagName : String) {
        val frag = supportFragmentManager.findFragmentByTag(fragmentTagName)

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

        app.showWebBrowser = { it:ConnectionInfo ->
            var frag = setWebBrowserBase()
            frag.setNavigation(it)
        }

        app.hideWebBrowser = { runOnUiThread({ unsetFragment(webviewFragmentName) }) }

        app.hideConnectionsSettings = { runOnUiThread({
            unsetFragment(connsSettsFragmentName)
            var frag = setWebBrowserBase()
            frag.setNavigation(app.currentConnection)
        }) }

        app.showConnectionsSettings = {
            app.hideWebBrowser?.invoke()
            var frag = setConnectionsSettingsBase()
            frag.setNavigation(it, {
                app.hideConnectionsSettings?.invoke()
            })
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
                unsetFragment(scanQrFragmentName)
            })
        }

        app.showWebBrowser?.invoke(app.currentConnection)
        app.hideScanQrImpl?.invoke()
    }
}
