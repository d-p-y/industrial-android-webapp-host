package pl.todoit.IndustrialWebViewWithQr

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import pl.todoit.IndustrialWebViewWithQr.fragments.ConnectionsSettingsFragment
import pl.todoit.IndustrialWebViewWithQr.fragments.ScanQrFragment
import pl.todoit.IndustrialWebViewWithQr.fragments.WebViewFragment
import pl.todoit.IndustrialWebViewWithQr.model.*
import timber.log.Timber

enum class OkOrDismissed {
    OK,
    Dismissed
}

suspend fun <T> buildAndShowDialog(ctx: Context, bld:(AlertDialog.Builder, SendChannel<T>)->AlertDialog.Builder) : T {
    var result = Channel<T>()
    bld(AlertDialog.Builder(ctx), result).show()
    return result.receive()
}

class MainActivity : AppCompatActivity() {
    private var currentMasterFragmentTag : String? = null
    private var currentPopupFragmentTag : String? = null
    private var currentPopup: IProcessesBackButtonEvents? = null
    private var currentMasterFragment:Fragment? = null

    private var permissionRequestCode = 0
    private val permissionRequestToIsGrantedReplyChannel : MutableMap<Int, Channel<Pair<String,Boolean>>> = mutableMapOf()

    private suspend fun <T> buildAndShowDialog(bld:(AlertDialog.Builder, SendChannel<T>)->AlertDialog.Builder) : T =
        buildAndShowDialog(this, bld)

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        val maybeReply = permissionRequestToIsGrantedReplyChannel.get(requestCode)

        if (maybeReply == null) {
            Timber.e("onRequestPermissionsResult() called for unknown requestCode=$requestCode")
            return
        }

        permissionRequestToIsGrantedReplyChannel.remove(requestCode) //clean up

        if (permissions.size != 1 || grantResults.size != 1) {
            Timber.e("onRequestPermissionsResult() arrays not of expected size=1")
            return
        }

        var perm = permissions[0]
        val result = grantResults[0] == PackageManager.PERMISSION_GRANTED

        App.Instance.launchCoroutine {  maybeReply.send(Pair(perm, result)) }
    }

    private suspend fun grantPermission(manifestPermissionItm : String) : Boolean {
        val grant = ContextCompat.checkSelfPermission(this, manifestPermissionItm)
        if (grant == PackageManager.PERMISSION_GRANTED) {
            Timber.d("permission $manifestPermissionItm already granted")
            return true
        }

        var requestCode = ++permissionRequestCode
        val reply = Channel<Pair<String,Boolean>>()
        permissionRequestToIsGrantedReplyChannel[requestCode] = reply
        ActivityCompat.requestPermissions(this, arrayOf(manifestPermissionItm), requestCode)
        val (permission, granted) = reply.receive()

        if (manifestPermissionItm != permission) {
            Timber.e("grantPermission() got reply to different permission=${permission} than asked=${manifestPermissionItm}")
            return false
        }

        Timber.d("permission $manifestPermissionItm granted?=$granted")
        return granted
    }

    override fun onSupportNavigateUp(): Boolean = true

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            Timber.d("onOptionsItemSelected() up-navigation")
            App.Instance.launchCoroutine { App.Instance.navigation.send(NavigationRequest._Activity_Back()) }

            return true
        }

        if (item.itemId == R.id.menuItemSettings) {
            App.Instance.launchCoroutine { App.Instance.navigation.send(NavigationRequest._Toolbar_GoToConnectionSettings()) }

            return true
        }

        return super.onOptionsItemSelected(item)
    }

    private fun setToolbarTitle(title : String) {
        var tb = supportActionBar

        if (tb == null) {
            Timber.e("no toolbar - cannot change title")
            return
        }

        supportActionBar?.title = title
    }

    private fun setToolbarBackButtonState(enabled : Boolean) {
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

        setToolbarBackButtonState(false) //webapp may support it but this seems to be the sane default
        setToolbarTitle("Loading...")

        Timber.i("starting mainActivityNavigator()")

        App.Instance.launchCoroutine {
            startMainNavigatorLoop(NavigationRequest._Activity_GoToBrowser())
        }
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

    private fun <T> replaceMasterFragment(fragment:T, fragmentTagName:String) where T:Fragment, T:IHasTitle {
        removeMasterFragmentIfNeeded()
        supportFragmentManager.beginTransaction().add(R.id.base_fragment, fragment, fragmentTagName).commit()
        currentMasterFragmentTag = fragmentTagName
        currentMasterFragment = fragment
        setToolbarTitle(fragment.getTitle())

        Timber.d("replaceMasterFragment currentPopupFragmentTag=$currentMasterFragmentTag")
    }

    private fun replaceMasterWithWebBrowser(connInfo: ConnectionInfo) {
        App.Instance.webViewFragmentParams.set(connInfo)
        val fragment = WebViewFragment()
        replaceMasterFragment(fragment, "webBrowser")
    }

    private fun replaceMasterWithConnectionsSettings(connInfo: ConnectionInfo) {
        App.Instance.connSettFragmentParams.set(connInfo)
        var fragment = ConnectionsSettingsFragment()
        replaceMasterFragment(fragment, "connSett")
    }

    private fun removePopupFragmentIfNeeded() {
        Timber.d("removePopupFragmentIfNeeded currentPopupFragmentTag=$currentPopupFragmentTag")

        var old = currentPopupFragmentTag

        if (old != null) {
            removeFragment(old)
            currentPopupFragmentTag = null
        }
        findViewById<ViewGroup>(R.id.popup_fragment)?.visibility = View.GONE
        currentPopup = null

        val x = currentMasterFragment
        if (x is IHasTitle) {
            setToolbarTitle(x.getTitle())
        }
    }

    @Suppress("MoveLambdaOutsideParentheses")
    private suspend fun <T> replacePopupFragment(fragment:T, fragmentTagName:String)
            where T : IProcessesBackButtonEvents, T: Fragment, T:IMaybeHasTitle {

        if (fragment is IRequiresPermissions) {
            var failedToGetPermission =
                fragment.getRequiredAndroidManifestPermissions()
                    .map { Pair(it, grantPermission(it)) }
                    .filter { !it.second }

            if (failedToGetPermission.any()) {
                Timber.e("failed to get required permissions (user rejected?)")
                fragment.onRequiredPermissionRejected(failedToGetPermission.map { it.first })
                return
            }
        }

        if (fragment is IBeforeNavigationValidation) {
            var maybeError = fragment.maybeGetBeforeNavigationError(this)

            if (maybeError != null) {
                var result = buildAndShowDialog<OkOrDismissed> { bld, result ->
                    with(bld) {
                        setTitle("Failed to scan")
                        setMessage(maybeError)
                        setPositiveButton(
                            android.R.string.ok,
                            { _, _ -> App.Instance.launchCoroutine {  result.send(OkOrDismissed.OK) } })
                        setOnCancelListener{ App.Instance.launchCoroutine {result.send(OkOrDismissed.Dismissed)} }
                    }
                }

                Timber.d("dialog result=$result")

                return
            }
        }

        removePopupFragmentIfNeeded()
        supportFragmentManager.beginTransaction().add(R.id.popup_fragment, fragment, fragmentTagName).commit()
        currentPopupFragmentTag = fragmentTagName
        findViewById<ViewGroup>(R.id.popup_fragment)?.visibility = View.VISIBLE

        var maybeTitle = fragment.getTitleMaybe()
        if (maybeTitle != null) {
            setToolbarTitle(maybeTitle)
        }

        Timber.d("replacePopupFragment currentPopupFragmentTag=$currentPopupFragmentTag")
        currentPopup = fragment
    }

    private suspend fun replacePopupWithScanQr(scanReq: ScanRequest) {
        App.Instance.scanQrFragmentParams.set(scanReq)
        var fragment = ScanQrFragment()
        replacePopupFragment(fragment, "qrScanner")
    }

    private suspend fun consumeNavigationRequest(request:NavigationRequest) {
        Timber.d("mainActivityNavigator() received navigationrequest=$request currentMaster=$currentMasterFragmentTag currentPopup=$currentPopup")

        when (request) {
            is NavigationRequest._Activity_GoToBrowser -> replaceMasterWithWebBrowser(App.Instance.currentConnection)
            is NavigationRequest._Toolbar_GoToConnectionSettings -> replaceMasterWithConnectionsSettings(App.Instance.currentConnection)
            is NavigationRequest.ConnectionSettings_Save -> {
                App.Instance.currentConnection = request.connInfo
                replaceMasterWithWebBrowser(App.Instance.currentConnection)
            }
            is NavigationRequest.ConnectionSettings_Back -> replaceMasterWithWebBrowser(App.Instance.currentConnection)
            is NavigationRequest.WebBrowser_RequestedScanQr -> replacePopupWithScanQr(request.req)
            is NavigationRequest.WebBrowser_CancelScanQr -> {
                var currentPopupCopy = currentPopup
                if (currentPopupCopy is ScanQrFragment) {
                    Timber.d("requesting cancellation of scanning because scanner is still active")
                    currentPopupCopy.onReceivedScanningCancellationRequest(request.jsPromiseId)
                } else {
                    Timber.d("cancellation request ignored because scanner is not active anymore")
                }
            }
            is NavigationRequest.ScanQr_Scanned -> removePopupFragmentIfNeeded()
            is NavigationRequest.ScanQr_Back -> removePopupFragmentIfNeeded()
            is NavigationRequest._ToolbarBackButtonStateChanged ->
                if (request.sender == currentMasterFragment) {
                    setToolbarBackButtonState(request.sender.isBackButtonEnabled())
                } else {
                    Timber.d("ignored change back button state request from inactive master fragment")
                }
            is NavigationRequest._ToolbarTitleChanged ->
                if (request.sender == currentMasterFragment) {
                    setToolbarTitle(request.sender.getTitle())
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

                if (currentMasterCopy is IProcessesBackButtonEvents) {
                    currentMasterCopy.onBackPressed()
                    return
                }

                Timber.d("no fragment is eligible for backbutton processing")
            }
        }
    }

    private suspend fun startMainNavigatorLoop(initialRequest:NavigationRequest?) {
        removePopupFragmentIfNeeded()

        if (initialRequest != null) {
            consumeNavigationRequest(initialRequest)
        }

        while(true) {
            consumeNavigationRequest(App.Instance.navigation.receive())
        }
    }

    override fun onBackPressed() {
        App.Instance.launchCoroutine { App.Instance.navigation.send(NavigationRequest._Activity_Back()) }
    }
}
