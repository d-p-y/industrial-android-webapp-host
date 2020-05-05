package pl.todoit.IndustrialWebViewWithQr

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.*
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
    val result = Channel<T>()
    bld(AlertDialog.Builder(ctx), result).show()
    return result.receive()
}

fun Activity.performHapticFeedback() =
    window?.decorView?.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)

class OverlayImage(val content:ByteArray)

class MainActivity : AppCompatActivity() {
    private var _scanPromiseId : String? = null

    private var permissionRequestCode = 0
    private val permissionRequestToIsGrantedReplyChannel : MutableMap<Int, Channel<Pair<String,Boolean>>> = mutableMapOf()

    private fun getCurrentMasterFragment() : Fragment? {
        val result = supportFragmentManager.findFragmentById(R.id.base_fragment)
        Timber.d("curr master fragment=$result")
        return result
    }

    private fun getCurrentPopupFragment() : Fragment? {
        val result = supportFragmentManager.findFragmentById(R.id.popup_fragment)
        Timber.d("curr popup fragment=$result")
        return result
    }

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

        val perm = permissions[0]
        val result = grantResults[0] == PackageManager.PERMISSION_GRANTED

        App.Instance.launchCoroutine {  maybeReply.send(Pair(perm, result)) }
    }

    private suspend fun grantPermission(manifestPermissionItm : String) : Boolean {
        val grant = ContextCompat.checkSelfPermission(this, manifestPermissionItm)
        if (grant == PackageManager.PERMISSION_GRANTED) {
            Timber.d("permission $manifestPermissionItm already granted")
            return true
        }

        val requestCode = ++permissionRequestCode
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

    override fun onSupportNavigateUp() = true

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
        val tb = supportActionBar

        if (tb == null) {
            Timber.e("no toolbar - cannot change title")
            return
        }

        supportActionBar?.title = title
    }

    private fun setToolbarBackButtonState(enabled : Boolean) {
        val tb = supportActionBar

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

        val toolBar = findViewById<Toolbar>(R.id.toolBar)
        setSupportActionBar(toolBar)

        setToolbarBackButtonState(false) //webapp may support it but this seems to be the sane default
        setToolbarTitle("Loading...")

        Timber.i("starting mainActivityNavigator()")

        App.Instance.launchCoroutine {
            startMainNavigatorLoop(NavigationRequest._Activity_GoToBrowser())
        }
    }

    private fun removeMasterFragmentIfNeeded() {
        val old = getCurrentMasterFragment()
        Timber.d("removeMasterFragmentIfNeeded currentMasterFragment=$old")

        if (old != null) {
            supportFragmentManager.beginTransaction().remove(old).commit()
        }
    }

    private fun <T> replaceMasterFragment(fragment:T) where T:Fragment, T:IHasTitle {
        removeMasterFragmentIfNeeded()
        supportFragmentManager.beginTransaction().add(R.id.base_fragment, fragment).commit()

        setToolbarTitle(fragment.getTitle())

        Timber.d("replaceMasterFragment currentMasterFragment=$fragment")
    }

    private fun replaceMasterWithWebBrowser(connInfo: ConnectionInfo) {
        App.Instance.webViewFragmentParams.set(connInfo)
        val fragment = WebViewFragment()
        replaceMasterFragment(fragment)
    }

    private fun replaceMasterWithConnectionsSettings(connInfo: ConnectionInfo) {
        App.Instance.connSettFragmentParams.set(connInfo)
        val fragment = ConnectionsSettingsFragment()
        replaceMasterFragment(fragment)
    }

    private fun removePopupFragmentIfNeeded() {
        val old = getCurrentPopupFragment()
        Timber.d("removePopupFragmentIfNeeded currentPopupFragment=$old")

        if (old != null) {
            supportFragmentManager.beginTransaction().remove(old).commit()
        }
        findViewById<ViewGroup>(R.id.popup_fragment)?.visibility = View.GONE

        val currentMasterFragment = getCurrentMasterFragment()
        if (currentMasterFragment is IHasTitle) {
            setToolbarTitle(currentMasterFragment.getTitle())
        }
    }

    private suspend fun <T> replacePopupFragment(fragment:T) : Boolean
            where T : IProcessesBackButtonEvents, T: Fragment, T:IMaybeHasTitle {

        if (fragment is IRequiresPermissions) {
            val failedToGetPermission =
                fragment.getRequiredAndroidManifestPermissions()
                    .map { Pair(it, grantPermission(it)) }
                    .filter { !it.second }

            if (failedToGetPermission.any()) {
                Timber.e("failed to get required permissions (user rejected?)")
                fragment.onRequiredPermissionRejected(failedToGetPermission.map { it.first })
                return false
            }
        }

        if (fragment is IBeforeNavigationValidation) {
            val maybeError = fragment.maybeGetBeforeNavigationError(this)

            if (maybeError != null) {
                val result = buildAndShowDialog<OkOrDismissed> { bld, result ->
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

                return false
            }
        }

        removePopupFragmentIfNeeded()
        supportFragmentManager.beginTransaction().add(R.id.popup_fragment, fragment).commit()

        findViewById<ViewGroup>(R.id.popup_fragment)?.visibility = View.VISIBLE

        val maybeTitle = fragment.getTitleMaybe()
        if (maybeTitle != null) {
            setToolbarTitle(maybeTitle)
        }

        Timber.d("replacePopupFragment currentPopupFragmentTag=${getCurrentPopupFragment()}")

        return true
    }

    /**
     * @return true if actually shown. false if request was rejected
     */
    private suspend fun replacePopupWithScanQr(scanReq: ScanRequest, overlayImage: OverlayImage?) : Boolean {
        App.Instance.scanQrFragmentParams.set(Pair(scanReq, overlayImage))
        val fragment = ScanQrFragment()
        return replacePopupFragment(fragment)
    }

    private suspend fun consumeNavigationRequest(request:NavigationRequest) {
        Timber.d("mainActivityNavigator() received navigationrequest=$request currentMaster=${getCurrentMasterFragment()} currentPopup=${getCurrentPopupFragment()}")

        when (request) {
            is NavigationRequest._Activity_GoToBrowser -> replaceMasterWithWebBrowser(App.Instance.currentConnection)
            is NavigationRequest._Toolbar_GoToConnectionSettings -> replaceMasterWithConnectionsSettings(
                App.Instance.currentConnection
            )
            is NavigationRequest.ConnectionSettings_Save -> {
                App.Instance.currentConnection = request.connInfo
                replaceMasterWithWebBrowser(App.Instance.currentConnection)
            }
            is NavigationRequest.ConnectionSettings_Back -> replaceMasterWithWebBrowser(App.Instance.currentConnection)
            is NavigationRequest.WebBrowser_SetScanOverlayImage -> {
                Timber.d("setting scan overlay image")
                App.Instance.overlayImageOnPause = OverlayImage(request.content)
            }
            is NavigationRequest.WebBrowser_RequestedScanQr -> {
                if (_scanPromiseId != null) {
                    Timber.d("rejected request to start scanner as former scan request is still active")
                    return
                }

                if (replacePopupWithScanQr(request.req, App.Instance.overlayImageOnPause)) {
                    _scanPromiseId = request.req.jsPromiseId
                }
            }
            is NavigationRequest.WebBrowser_ResumeScanQr -> {
                val currentPopup = getCurrentPopupFragment()
                if (currentPopup !is ScanQrFragment || _scanPromiseId != request.jsPromiseId) {
                    Timber.e("resuming request ignored because scanner is not active anymore OR jsPromiseId not matches")
                    return
                }

                Timber.d("requesting resuming scanning because scanner is still active AND jsPromiseId matches")
                currentPopup.onReceivedScanningResumeRequest()
            }
            is NavigationRequest.WebBrowser_CancelScanQr -> {
                val currentPopup = getCurrentPopupFragment()
                if (currentPopup !is ScanQrFragment || _scanPromiseId != request.jsPromiseId) {
                    Timber.e("cancellation request ignored because scanner is not active anymore OR jsPromiseId not matches")
                    return
                }

                Timber.d("requesting cancellation of scanning because scanner is still active AND jsPromiseId matches")
                currentPopup.onReceivedScanningCancellationRequest()
            }
            is NavigationRequest.ScanQr_Scanned -> {
                removePopupFragmentIfNeeded()
                _scanPromiseId = null
            }
            is NavigationRequest.ScanQr_Back -> {
                removePopupFragmentIfNeeded()
                _scanPromiseId = null
            }
            is NavigationRequest._ToolbarBackButtonStateChanged -> {
                if (request.sender != getCurrentMasterFragment()) {
                    Timber.d("ignored change back button state request from inactive master fragment")
                    return
                }

                setToolbarBackButtonState(request.sender.isBackButtonEnabled())
            }
            is NavigationRequest._ToolbarTitleChanged -> {
                if (request.sender != getCurrentMasterFragment()) {
                    Timber.d("ignored change name request from inactive master fragment")
                    return
                }

                setToolbarTitle(request.sender.getTitle())
            }
            is NavigationRequest._Activity_Back -> {
                val currentPopup = getCurrentPopupFragment()
                val currentMaster = getCurrentMasterFragment()

                if (currentPopup is IProcessesBackButtonEvents) {
                    Timber.d("delegating back button to popup fragment")
                    val backConsumed = currentPopup.onBackPressedConsumed()
                    Timber.d("popup consumed backbutton event?=$backConsumed")
                    if (backConsumed) {
                        return
                    }
                }

                if (currentMaster is IProcessesBackButtonEvents) {
                    //delegate question to master fragment (likely webapp)
                    Timber.d("trying to delegate back button to master fragment")
                    val backConsumed = currentMaster.onBackPressedConsumed()

                    Timber.d("master fragment backButton consumed?=$backConsumed")
                    if (backConsumed) {
                        return
                    }

                    finish()
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
