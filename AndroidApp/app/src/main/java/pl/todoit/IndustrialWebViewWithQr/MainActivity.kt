package pl.todoit.IndustrialWebViewWithQr

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import kotlinx.serialization.Serializable
import timber.log.Timber

class ScanRequest(
    public var label : String,
    public var regexp : String?,
    public val scanResult : Channel<String?> = Channel() ) {}

@Serializable
data class AndroidReply (
    var PromiseId : String,
    var IsSuccess : Boolean,
    var Reply : String?
)

data class ConnectionInfo(var url : String)

interface IBackAcceptingFragment {
    fun onBackPressed()
}

interface IHasTitleFragment {
    fun getTitle() : String
}

interface ISupportsBackButtonFragment {
    fun isBackButtonEnabled() : Boolean
}

/**
 * convention for form initiated navigation (form requests navigation): Sender_ActionName
 * convention for non-form initiated navigation (top level request to navigate to some form): _Sender_ActionName
 */
public sealed class NavigationRequest {
    class _Activity_Back() : NavigationRequest()
    class _Activity_GoToBrowser() : NavigationRequest()
    class _Toolbar_GoToConnectionSettings() : NavigationRequest()
    class ConnectionSettings_Back() : NavigationRequest()
    class ConnectionSettings_Save(val connInfo:ConnectionInfo) : NavigationRequest()
    class WebBrowser_RequestedScanQr(val req:ScanRequest) : NavigationRequest()
    class ScanQr_Scanned() : NavigationRequest()
    class ScanQr_Back() : NavigationRequest()
    class _TitleChanged(val sender : IHasTitleFragment) : NavigationRequest()
    class _BackButtonStateChanged(val sender : ISupportsBackButtonFragment) : NavigationRequest()
}

class MainActivity : AppCompatActivity(), CoroutineScope by MainScope() {
    private val Dispatchers_UI = Dispatchers.Main

    private val navigation = Channel<NavigationRequest>()
    private var currentMasterFragmentTag : String? = null
    private var currentPopupFragmentTag : String? = null
    private var currentPopup:IBackAcceptingFragment? = null
    private var currentMasterFragment:Fragment? = null

    fun launchCoroutine (block : suspend () -> Unit) {
        launch {
            withContext(Dispatchers_UI) {
                block.invoke()
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean = true

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            Timber.d("onOptionsItemSelected() up-navigation")
            launchCoroutine { navigation.send(NavigationRequest._Activity_Back()) }

            return true
        }

        if (item.itemId == R.id.menuItemSettings) {
            launchCoroutine { navigation.send(NavigationRequest._Toolbar_GoToConnectionSettings()) }

            return true
        }

        return super.onOptionsItemSelected(item)
    }

    private fun setBackButtonState(enabled : Boolean) {
        var tb = supportActionBar

        if (tb == null) {
            Timber.e("no toolbar - cannot change button state")
            return
        }

        with(tb) {
            setDisplayShowHomeEnabled(enabled)
            setDisplayHomeAsUpEnabled(enabled)
            setHomeButtonEnabled(enabled)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        var toolBar = findViewById<Toolbar>(R.id.toolBar)
        setSupportActionBar(toolBar)

        setBackButtonState(false) //webapp may support it but this seems to be the sane default

        var app = application

        if (app !is App) {
            Timber.e("no application instance")
            return
        }

        Timber.i("starting mainActivityNavigator()")

        launchCoroutine(suspend {
            startMainNavigatorLoop(app, NavigationRequest._Activity_GoToBrowser())
        })
    }

    private fun removeFragment(fragmentTagName : String) {
        val frag = supportFragmentManager.findFragmentByTag(fragmentTagName)

        if (frag == null) {
            Timber.e("cannot unset fragment $fragmentTagName")
            return
        }

        supportFragmentManager.beginTransaction().remove(frag).commit()
    }

    private fun removeMasterFragmentIfNeeded() {
        Timber.d("removeMasterFragmentIfNeeded currentPopupFragmentTag=$currentMasterFragmentTag")

        var old = currentMasterFragmentTag

        if (old != null) {
            removeFragment(old)
            currentMasterFragmentTag = null
        }
    }

    private fun replaceMasterFragment(fragment:Fragment, fragmentTagName:String) {
        removeMasterFragmentIfNeeded()
        supportFragmentManager.beginTransaction().add(R.id.base_fragment, fragment, fragmentTagName).commit()
        currentMasterFragmentTag = fragmentTagName
        currentMasterFragment = fragment
        supportActionBar?.title = if (fragment is IHasTitleFragment) fragment.getTitle() else fragmentTagName

        Timber.d("replaceMasterFragment currentPopupFragmentTag=$currentMasterFragmentTag")
    }

    private fun replaceMasterWithWebBrowser(navigation : Channel<NavigationRequest>, connInfo:ConnectionInfo) =
        replaceMasterFragment(WebViewFragment(navigation, connInfo), "webBrowser")

    private fun replaceMasterWithConnectionsSettings(navigation : Channel<NavigationRequest>, connInfo:ConnectionInfo) =
        replaceMasterFragment(ConnectionsSettingsFragment(navigation, connInfo), "connSett")

    private fun removePopupFragmentIfNeeded() {
        Timber.d("removePopupFragmentIfNeeded currentPopupFragmentTag=$currentPopupFragmentTag")

        var old = currentPopupFragmentTag

        if (old != null) {
            removeFragment(old)
            currentPopupFragmentTag = null
        }
        findViewById<ViewGroup>(R.id.popup_fragment)?.visibility = View.GONE
        currentPopup = null
    }

    private fun <T> replacePopupFragment(fragment:T, fragmentTagName:String) where T : IBackAcceptingFragment, T: Fragment {
        removePopupFragmentIfNeeded()
        supportFragmentManager.beginTransaction().add(R.id.popup_fragment, fragment, fragmentTagName).commit()
        currentPopupFragmentTag = fragmentTagName
        findViewById<ViewGroup>(R.id.popup_fragment)?.visibility = View.VISIBLE
        Timber.d("replacePopupFragment currentPopupFragmentTag=$currentPopupFragmentTag")
        currentPopup = fragment
    }

    private fun replacePopupWithScanQr(navigation : Channel<NavigationRequest>, scanReq:ScanRequest) =
        replacePopupFragment(ScanQrFragment(navigation, scanReq), "qrScanner")

    private suspend fun consumeNavigationRequest(app:App, request:NavigationRequest) {
        Timber.d("mainActivityNavigator() received navigationrequest=$request currentMaster=$currentMasterFragmentTag currentPopup=$currentPopup")

        when (request) {
            is NavigationRequest._Activity_GoToBrowser -> replaceMasterWithWebBrowser(navigation, app.currentConnection)
            is NavigationRequest._Toolbar_GoToConnectionSettings -> replaceMasterWithConnectionsSettings(navigation, app.currentConnection)
            is NavigationRequest.ConnectionSettings_Save -> {
                app.currentConnection = request.connInfo
                replaceMasterWithWebBrowser(navigation, app.currentConnection)
            }
            is NavigationRequest.ConnectionSettings_Back -> replaceMasterWithWebBrowser(navigation, app.currentConnection)
            is NavigationRequest.WebBrowser_RequestedScanQr -> replacePopupWithScanQr(navigation, request.req)
            is NavigationRequest.ScanQr_Scanned -> removePopupFragmentIfNeeded()
            is NavigationRequest.ScanQr_Back -> removePopupFragmentIfNeeded()
            is NavigationRequest._BackButtonStateChanged ->
                if (request.sender == currentMasterFragment) {
                    setBackButtonState(request.sender.isBackButtonEnabled())
                } else {
                    Timber.d("ignored change back button state request from inactive master fragment")
                }
            is NavigationRequest._TitleChanged ->
                if (request.sender == currentMasterFragment) {
                    supportActionBar?.title = request.sender.getTitle()
                } else {
                    Timber.d("ignored change name request from inactive master fragment")
                }

            is NavigationRequest._Activity_Back -> {
                var currentPopupCopy = currentPopup
                var currentMasterCopy = currentMasterFragment

                if (currentPopupCopy != null) {
                    Timber.d("delegating back button to popup fragment")
                    currentPopupCopy.onBackPressed()
                    return
                }

                if (currentMasterCopy is WebViewFragment) {
                    //delegate question to webapp
                    Timber.d("trying to delegate back button to webView fragment")
                    var backConsumed = currentMasterCopy.onBackPressedConsumed()

                    Timber.d("webView backButton consume=$backConsumed")
                    if (backConsumed == false) {
                        finish()
                    }

                    return
                }

                if (currentMasterCopy is IBackAcceptingFragment) {
                    currentMasterCopy.onBackPressed()
                    return
                }

                Timber.d("no fragment is eligible for backbutton processing")
            }
        }
    }

    private suspend fun startMainNavigatorLoop(app:App, initialRequest:NavigationRequest?) {
        removePopupFragmentIfNeeded()

        if (initialRequest != null) {
            consumeNavigationRequest(app, initialRequest)
        }

        while(true) {
            consumeNavigationRequest(app, navigation.receive())
        }
    }

    override fun onBackPressed() {
        launchCoroutine { navigation.send(NavigationRequest._Activity_Back()) }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel() //coroutines cancellation
    }
}
