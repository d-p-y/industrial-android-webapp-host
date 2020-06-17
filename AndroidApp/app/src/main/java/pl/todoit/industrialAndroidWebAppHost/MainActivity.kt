package pl.todoit.industrialAndroidWebAppHost

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.SearchView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import pl.todoit.industrialAndroidWebAppHost.fragments.WebViewFragment
import pl.todoit.industrialAndroidWebAppHost.fragments.drawableFromFileWithoutAutoScalling
import pl.todoit.industrialAndroidWebAppHost.model.*
import pl.todoit.industrialAndroidWebAppHost.model.extensions.playOnce
import timber.log.Timber
import java.io.Closeable
import java.io.File
import java.util.*
import kotlin.system.exitProcess

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

enum class SndItem {
    PictureTaken,
    ScanSuccess
}

class DelegatingSensorEventListener(val onChanged:(Pair<Float,Float>)->Unit) : SensorEventListener {
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onSensorChanged(event: SensorEvent?) {
        val ev = event
        if (ev == null) {
            Timber.e("null onSensorChanged parameter")
            return
        }
        if (ev.values.size <2) {
            Timber.e("onSensorChanged expected at least two values")
            return
        }
        onChanged(Pair(ev.values[0], ev.values[1]))
    }
}

val appBarActionIds = arrayOf(
    R.id.menuActionProgrammatic0, R.id.menuActionProgrammatic1, R.id.menuActionProgrammatic2, R.id.menuActionProgrammatic3)
val appBarMenuItmsIds = arrayOf(
    R.id.menuItemProgrammatic0, R.id.menuItemProgrammatic1, R.id.menuItemProgrammatic2, R.id.menuItemProgrammatic3,
    R.id.menuItemProgrammatic4, R.id.menuItemProgrammatic5, R.id.menuItemProgrammatic6,
    R.id.menuItemProgrammatic7, R.id.menuItemProgrammatic8, R.id.menuItemProgrammatic9)

class MainActivity : AppCompatActivity() {
    private var _menu : Menu? = null
    private var _menuItems = listOf<MenuItemInfo>()
    private var _toolbarSearchIsActive = false

    private lateinit var _sm: SensorManager
    private val _sensorListeners = mutableListOf<Closeable>()

    private lateinit var _sndPool: SoundPool
    private var _sndCameraClickId : Int? = null
    private var _sndScanSuccessId : Int? = null

    private var permissionRequestCode = 0
    private val permissionRequestToIsGrantedReplyChannel : MutableMap<Int, Channel<Pair<String,Boolean>>> = mutableMapOf()

    private fun sensorValuesAsRotation(sensorValues : Pair<Float,Float>) =
        if (sensorValues.first > -6.6 && sensorValues.first < 6.6) {
            if (sensorValues.second > 0) RightAngleRotation.RotateBy0 else RightAngleRotation.RotateBy180
        } else if (sensorValues.first <= -6.6) RightAngleRotation.RotateBy90
        else RightAngleRotation.RotateBy270
        /* Rotations from natural portrait position (clockwise values). +-6.6 is experimentally for +-45 deg
            0;9 -> 0 deg
            -9;0 -> 90deg
            0;-9 -> 180
            9;0 -> 270
        */

    fun getCurrentMasterFragment() : Fragment? {
        val result = supportFragmentManager.findFragmentById(R.id.base_fragment)
        Timber.d("curr master fragment=$result")
        return result
    }

    fun getCurrentPopupFragment() : Fragment? {
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
        _menu = menu
        val menuCreationError = setAppBarMenuItems(_menuItems)

        if (menuCreationError != null) {
            Timber.e(menuCreationError)
        }

        setToolbarSearchState(_toolbarSearchIsActive)

        return super.onCreateOptionsMenu(menu)
    }

    /**
     * @return error if any
     */
    fun setAppBarMenuItems(items:List<MenuItemInfo>) : String? {
        val (actions, menus) = items.partition { it.isAction() }

        if (actions.size > appBarActionIds.size) {
            return "requested more actions than available slots"
        }

        if (menus.size > appBarMenuItmsIds.size) {
            return "requested more menuItems than available slots"
        }

        _menuItems = items

        var menu = _menu

        if (menu == null) {
            Timber.d("menu not available yet")
            return null
        }

        (appBarActionIds + appBarMenuItmsIds)
            .forEach { menu.findItem(it).isVisible = false }

        val res = resources

        if (res == null) {
            Timber.e("resources is not available")
            return "null resources"
        }

        val wrkItms =
            items
                .withIndex()
                .map { Pair(
                    it.value,
                    if (it.value.isAction()) appBarActionIds[it.index] else appBarMenuItmsIds[it.index])
                }
                .map { Triple(
                    it.first,
                    it.second,
                    menu.findItem(it.second))
                }

        if (wrkItms.any { it.third == null }) {
            return "problem with finding menu items"
        }

        if (wrkItms.any {it.first.iconMediaIdentifierId != null && !App.Instance.isAssetPresent(it.first.iconMediaIdentifierId!!) }) {
            return "problem with unavailable or unsafe icon not present in cacheDir"
        }

        wrkItms
            .filter {it.third != null}
            .forEach {
                it.first.physicalMenuItemId = it.second
                it.third.apply {
                    isVisible = true
                    isEnabled = it.first.enabled
                    title = it.first.title

                    val iconId = it.first.iconMediaIdentifierId
                    if (iconId != null) {
                        icon = drawableFromFileWithoutAutoScalling(File(cacheDir, iconId), res)
                    }
                }
            }

        return null //no error
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            Timber.d("onOptionsItemSelected() up-navigation")
            App.Instance.navigator.postNavigateTo(NavigationRequest._Activity_Back())

            return true
        }

        if (item.itemId == R.id.menuItemSettings) {
            App.Instance.navigator.postNavigateTo(NavigationRequest._Toolbar_GoToConnectionSettings())

            return true
        }

        val maybeUserItem = _menuItems.firstOrNull { it.physicalMenuItemId == item.itemId }

        if (maybeUserItem != null) {
            Timber.d("onOptionsItemSelected() activated user item ${maybeUserItem.webMenuItemId}, delegating to browser")
            App.Instance.navigator.postNavigateTo(NavigationRequest._Toolbar_ItemActivated(maybeUserItem))
            return true
        }

        return super.onOptionsItemSelected(item)
    }

    fun setToolbarTitle(title : String) {
        val tb = supportActionBar

        if (tb == null) {
            Timber.e("no toolbar - cannot change title")
            return
        }

        supportActionBar?.title = title
    }

    fun setToolbarVisibility(visible : Boolean) {
        val tb = supportActionBar

        if (tb == null) {
            Timber.e("no toolbar - cannot change button state")
            return
        }

        Timber.d("changing toolbar visibility to $visible")

        if (visible) {
            tb.show()
        } else {
            tb.hide()
        }
    }

    fun setToolbarColors(bgColor : String, fgColor : String) {
        val tb = findViewById<Toolbar>(R.id.toolBar)

        if (tb !is Toolbar) {
            Timber.e("no toolbar or not appcompat Toolbar- cannot change button state")
            return
        }

        Timber.d("changing toolbar colors to $bgColor $fgColor")

        tb.setBackgroundColor(Color.parseColor(bgColor))
        tb.setTitleTextColor(Color.parseColor(fgColor))
    }

    fun setToolbarSearchState(isActive : Boolean) {
        _toolbarSearchIsActive = isActive

        val searchItemId = _menu?.findItem(R.id.menuItemSearch)

        if (searchItemId == null) {
            Timber.d("didn't find menuItemSearch within menu")
            return
        }

        val sv = searchItemId.actionView

        if (sv !is SearchView) {
            Timber.d("menuItemSearch is not of SearchView type")
            return
        }

        if (!_toolbarSearchIsActive) {
            //clear it on deactivation while it is still visible
            sv.setQuery("", false) //clear former entry
            sv.isIconified = true
        }
        searchItemId.isVisible = _toolbarSearchIsActive

        sv.setOnQueryTextListener( object : SearchView.OnQueryTextListener {
            fun webAppNotifySearchUpdate(query : String?, committed : Boolean) : Boolean {
                val webViewFrag = getCurrentMasterFragment()

                if (webViewFrag !is WebViewFragment) {
                    Timber.e("onQueryTextSubmit without webviewfragment in master")
                    return true //search handled
                }

                if (_toolbarSearchIsActive) {
                    webViewFrag.onToolbarSearchUpdate(committed, query ?: "")
                }

                return true//search handled
            }

            override fun onQueryTextSubmit(query: String?) : Boolean = webAppNotifySearchUpdate(query, true)
            override fun onQueryTextChange(newText: String?) : Boolean = webAppNotifySearchUpdate(newText, false)
        })
    }

    fun setToolbarBackButtonState(enabled : Boolean) {
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
        Thread.setDefaultUncaughtExceptionHandler { _, exc -> saveExceptionToDisk(exc) }

        setContentView(R.layout.activity_main)

        val toolBar = findViewById<Toolbar>(R.id.toolBar)
        setSupportActionBar(toolBar)
    }

    fun playSound(sndItem:SndItem) {
        val maybeSnd = when(sndItem) {
            SndItem.PictureTaken -> _sndCameraClickId
            SndItem.ScanSuccess -> _sndScanSuccessId
        }
        if (maybeSnd == null) {
            Timber.d("sound $sndItem is null (not loaded?)")
            return
        }
        _sndPool.playOnce(maybeSnd)
    }

    override fun onStart() {
        super.onStart()
        _sm = getSystemService(SENSOR_SERVICE) as SensorManager

        setToolbarBackButtonState(false) //webapp may support it but this seems to be the sane default
        setToolbarTitle("Loading...")

        _sndPool = SoundPool
            .Builder()
            .setAudioAttributes(AudioAttributes.Builder().build())
            .setMaxStreams(2)
            .build()
        _sndCameraClickId = _sndPool.load("/system/media/audio/ui/camera_click.ogg", 1 /*unused param*/)

        val maybeRequestedUrl = intent.dataString
        Timber.i("starting mainActivityNavigator() for url?={$maybeRequestedUrl}")

        App.Instance.navigator.postNavigateTo(NavigationRequest._Activity_MainActivityActivated(this, maybeRequestedUrl))
    }

    /**
     * call close on returned object
     */
    fun createSensorListener(listener : (RightAngleRotation)->Unit) : Closeable {
        val result = object : Closeable {
            private val _sensorListener = DelegatingSensorEventListener { listener(sensorValuesAsRotation(it)) }
            private var _closed = false

            init {
                //https://developer.android.com/reference/android/hardware/SensorEvent
                _sm.registerListener(_sensorListener, _sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL)
            }

            override fun close() {
                if (_closed) {
                    return
                }
                _closed = true
                _sm.unregisterListener(_sensorListener)
                _sensorListeners.remove(this)
            }
        }

        _sensorListeners.add(result)
        return result
    }

    private fun saveExceptionToDisk(exc: Throwable) {
        File(this.filesDir, "exceptions.txt")
            .appendText(
                "millisSinceEpoch=${Date().time} exception=${Log.getStackTraceString(exc)}\n"
            )

        Timber.e(Log.getStackTraceString(exc))

        //app is potentially in invalid state
        finishAffinity()
        exitProcess(1)
    }

    override fun onPause() {
        val webApp = getCurrentMasterFragment()
        if (webApp is WebViewFragment) {
            val currentUrl =  webApp.maybeGetWebAppUrl()
            Timber.d("onPause() maybe persisting state for url=$currentUrl")
            if (currentUrl != null) {
                val (_, newState) = urlAndMaybeFragment(currentUrl)
                val existState = App.Instance.getOrCreateConnectionByUrl(currentUrl)

                if (newState != existState.webAppPersistentState) {
                    //in-memory persistence
                    existState.webAppPersistentState = newState
                    App.Instance.persistConnection(existState)
                }
            }
        }

        super.onPause()
    }

    override fun onStop() {
        App.Instance.persistKnownConnections() //to-disk persistence

        val cpy = _sensorListeners.toList()
        _sensorListeners.clear()

        _sndPool.release()

        cpy.forEach { it.close() }

        App.Instance.navigator.postNavigateTo(NavigationRequest._Activity_MainActivityInactivated(this))
        super.onStop()
    }

    private fun removeMasterFragmentIfNeeded() {
        val old = getCurrentMasterFragment()
        Timber.d("removeMasterFragmentIfNeeded currentMasterFragment=$old")

        if (old != null) {
            supportFragmentManager.beginTransaction().remove(old).commit()
        }
    }

    fun <T> replaceMasterFragment(fragment:T) where T:Fragment {
        removeMasterFragmentIfNeeded()
        supportFragmentManager.beginTransaction().add(R.id.base_fragment, fragment).commit()

        if (fragment is IHasTitle) {
            setToolbarTitle(fragment.getTitle())
        }

        if (fragment is ITogglesToolbarVisibility) {
            setToolbarVisibility(fragment.isToolbarVisible())
        }

        Timber.d("replaceMasterFragment currentMasterFragment=$fragment")
    }

    fun removePopupFragmentIfNeeded() {
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
        if (currentMasterFragment is ITogglesToolbarVisibility) {
            setToolbarVisibility(currentMasterFragment.isToolbarVisible())
        }
    }

    suspend fun <T> replacePopupFragment(fragment:T) : Boolean
            where T : IProcessesBackButtonEvents, T: Fragment {

        if (fragment is IRequiresPermissions) {
            val failedToGetPermission =
                fragment.getNeededAndroidManifestPermissions()
                    .map { Pair(it, grantPermission(it)) }
                    .filter { !it.second }

            if (failedToGetPermission.any()) {
                Timber.e("failed to get required permissions (user rejected?)")
                when(fragment.onNeededPermissionRejected(failedToGetPermission.map { it.first })) {
                    PermissionRequestRejected.MayNotOpenFragment -> return false
                    PermissionRequestRejected.MayOpenFragment -> {}
                }
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

        if (fragment is IMaybeHasTitle) {
            val maybeTitle = fragment.getTitleMaybe()
            if (maybeTitle != null) {
                setToolbarTitle(maybeTitle)
            }
        }

        setToolbarVisibility(
            if (fragment is ITogglesToolbarVisibility) fragment.isToolbarVisible() else true)

        Timber.d("replacePopupFragment currentPopupFragmentTag=${getCurrentPopupFragment()}")

        return true
    }

    override fun onBackPressed() = App.Instance.navigator.postNavigateTo(NavigationRequest._Activity_Back())

    fun updateSoundSuccessScan(f : File) {
        val _sndScanSuccessIdCopy = _sndScanSuccessId
        if (_sndScanSuccessIdCopy != null) {
            _sndPool.unload(_sndScanSuccessIdCopy)
        }

        _sndScanSuccessId = _sndPool.load(f.absolutePath, 1 /*unused param*/)
    }
}
