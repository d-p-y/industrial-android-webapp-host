package pl.todoit.industrialAndroidWebAppHost

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import kotlinx.coroutines.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.list
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import pl.todoit.industrialAndroidWebAppHost.model.*
import timber.log.Timber
import java.io.File
import java.lang.Exception
import java.net.URLEncoder
import java.util.concurrent.Executors

val Dispatchers_UI = Dispatchers.Main

sealed class ConnectionManagerMode {
    class ConnectionChooser() : ConnectionManagerMode()
    class EditConnection(val connInfo:ConnectionInfo) : ConnectionManagerMode()
    class NewConnection(val url:String) : ConnectionManagerMode()
}

class App : Application(), CoroutineScope by MainScope() {
    companion object {
        lateinit var Instance : App
    }

    lateinit var _parallelComputations : ExecutorCoroutineDispatcher

    val navigator = Navigator()
    var maxComputationsAtOnce = 0
    var overlayImageOnPause : File? = null
    var soundSuccessScan : File? = null
    private var _jpegTempFileNo = 0

    lateinit var knownConnections : MutableList<ConnectionInfo>

    //consider enumerating assets programmatically to find all index.html
    //TODO use persistence for per-URL-settings-and-permissions
    lateinit var currentConnection : ConnectionInfo

    val isRunningInEmulator = Build.PRODUCT.toLowerCase().contains("sdk")

    //TODO: encapsulate following variables as "global settings" adjustable somewhere
    val playPictureTakenSound = true
    val isForcedDevelopmentMode = true
    val permitNoContinousFocusInCamera = isRunningInEmulator
    val tempJpegFilesRotateAt = 10
    val imagesToDecodeQueueSize = 20 //each raw FullHD photo consumes ~6MB. Nexus 5 produces photos every ~40ms and decodes 4 of them in parallel every ~200ms
    val decodeAtLeastOnceEveryMs = 1000 //unlikely needed
    val expectPictureTakenAtLeastAfterMs : Long = 300 //workaround for unlikely: E/Camera-JNI: Couldn't allocate byte array for JPEG data

    private val connectionInfosFileName = "connectionInfos.json"
    private val scanSuccessSoundFileName = "successScanSound"

    fun isAssetPresent(cacheFileName : String) : Boolean {
        val pth = File(cacheDir, cacheFileName)
        if ((cacheDir.absolutePath + "/" + cacheFileName) != pth.absolutePath) {
            Timber.e("attempted to check presence of file outside of cache dir.Potentially insecure thus rejected name=$cacheFileName")
            return false
        }

        return pth.exists()
    }

    fun getBuiltinConnections() = listOf(
        ConnectionInfo(
            persisted = false,
            url = "file:///android_asset/ConnectionsManager/index.html",
            name = "Connection Manager",
            forceReloadFromNet = isForcedDevelopmentMode,
            remoteDebuggerEnabled = isForcedDevelopmentMode,
            forwardConsoleLogToLogCat = isForcedDevelopmentMode,
            mayManageConnections = true,
            isConnectionManager = true
        ),
        ConnectionInfo(
            persisted = false,
            url = "file:///android_asset/DemoWebSite/index.html",
            name = "Demo",
            forceReloadFromNet = isForcedDevelopmentMode,
            remoteDebuggerEnabled = isForcedDevelopmentMode,
            hapticFeedbackOnBarcodeRecognized = true,
            forwardConsoleLogToLogCat = isForcedDevelopmentMode
        ),
        ConnectionInfo(
            persisted = false,
            url = "file:///android_asset/SimpleQrScanner/index.html",
            name = "Simple scanner",
            forceReloadFromNet = isForcedDevelopmentMode,
            remoteDebuggerEnabled = isForcedDevelopmentMode,
            hapticFeedbackOnBarcodeRecognized = true,
            forwardConsoleLogToLogCat = isForcedDevelopmentMode
        ))

    fun buildJpegFilePath() : String {
        if (cacheDir == null) {
            throw Exception("cacheDir is not available yet. Is App created?")
        }

        val result = File(cacheDir, "photo_${_jpegTempFileNo++}.jpg").absolutePath
        Timber.e("generated jpegFilePath set to $result")
        return result
    }

    private fun maybeReadPersistedKnownConnections() : List<ConnectionInfo>? {
        val f = File(filesDir, connectionInfosFileName)

        return if (f.exists()) {
            try {
                jsonStrict.decodeFromString<List<ConnectionInfo>>(f.readText())
                    .also { it.forEach { it.persisted = true }  }
            } catch (ex : Exception) {
                Timber.e("unable to deserialize or read persisted connection infos")
                null
            }
        } else null
    }

    fun persistKnownConnections() {
        //TODO first write to temp file then rename for extra safety
        File(this.filesDir, connectionInfosFileName)
            .writeText(jsonStrict.encodeToString(knownConnections))
        knownConnections.forEach {it.persisted = true}
    }

    fun createShortcut(act : Context,  conn:ConnectionInfo) : Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(act)) {
            Timber.e("ShortcutManagerCompat.isRequestPinShortcutSupported is false")
            Toast.makeText(act, "Launcher doesn't support pinned shortcuts", Toast.LENGTH_SHORT).show()
            return false
        }

        //on API 23 calling ShortcutManagerCompat.getDynamicShortcuts(activity) gets empty list even if there are pinned shortcuts
        //on API 23 calling ShortcutManagerCompat.removeDynamicShortcuts(activity, id) doesn't seem to remove pinned shortcuts

        val x = ShortcutInfoCompat.Builder(act, conn.url).apply {
            setShortLabel(conn.name)
            setIcon(IconCompat.createWithResource(act, R.mipmap.ic_launcher))

            //https://developer.android.com/guide/components/activities/tasks-and-back-stack
            setIntent(
                Intent(act, MainActivity::class.java).apply {
                    action = Intent.ACTION_MAIN
                    data = Uri.parse(conn.url)
                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP + Intent.FLAG_ACTIVITY_NEW_TASK
                })
        }

        ShortcutManagerCompat.requestPinShortcut(act, x.build(), null)
        return true
    }

    fun removeConnection(conn:ConnectionInfo) : Boolean {
        val neededUrl = conn.urlWithoutFragment()
        Timber.d("looking for connectionInfo using $neededUrl")

        val maybeExisting =
            knownConnections
                .mapIndexed { i,x -> if (x.urlWithoutFragment() == neededUrl) Pair(i,x) else null }
                .filterNotNull()
                .firstOrNull()

        if (maybeExisting == null) {
            Timber.d("could not find connection")
            return false
        }
        Timber.d("removing connectionInfo at ${maybeExisting.first}")

        knownConnections.removeAt(maybeExisting.first)
        return true
    }

    fun persistConnection(maybeExistingUrl : String?, conn:ConnectionInfo) {
        val maybeExisting =
            if (maybeExistingUrl == null) null
            else {
                val existingUrl = urlWithoutFragment(maybeExistingUrl)
                Timber.d("looking for connectionInfo using $existingUrl")

                knownConnections
                    .mapIndexed { i,x -> if (x.urlWithoutFragment() == existingUrl) Pair(i,x) else null }
                    .filterNotNull()
                    .firstOrNull()
            }

        if (maybeExisting != null) {
            Timber.d("replacing connectionInfo at ${maybeExisting.first}")

            conn.restoreStateFrom(maybeExisting.second)

            knownConnections.removeAt(maybeExisting.first)
            knownConnections.add(maybeExisting.first, conn)
        } else {
            Timber.d("appending new connectionInfo because didn't find url=$maybeExisting")
            knownConnections.add(conn)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())
        Timber.d("logging initialized")

        maxComputationsAtOnce = Runtime.getRuntime().availableProcessors()
        Timber.d("available processors=$maxComputationsAtOnce")

        if (maxComputationsAtOnce <= 0) {
            Timber.e("wrong processors count")
            maxComputationsAtOnce = 2 //newer devices have to have at least 2 cores
        }

        _parallelComputations = Executors.newFixedThreadPool(maxComputationsAtOnce).asCoroutineDispatcher()

        Instance = this

        soundSuccessScan = null
        overlayImageOnPause = null

        //if executed as field initializer it fails because context.getFilesDir() fails
        knownConnections = maybeReadPersistedKnownConnections()?.toMutableList() ?: getBuiltinConnections().toMutableList()

        launchCoroutine {
            navigator.startMainNavigatorLoop()
        }
    }

    fun launchParallelInBackground (block : suspend () -> Unit) {
        launch(_parallelComputations) {
            block.invoke()
        }
    }

    fun launchCoroutine (block : suspend () -> Unit) {
        launch(Dispatchers_UI) {
            block.invoke()
        }
    }

    /**
     * silly cleanup code that in reality will not be called on real device
     */
    override fun onTerminate() {
        super.onTerminate()
        cancel() //coroutines cancellation
        _parallelComputations.close()
    }

    private fun createConnectionByUrl(currentUrl: String) : ConnectionInfo {
        val (url, state) = urlAndMaybeFragment(currentUrl)
        return ConnectionInfo(url = url, name = "Unknown", persisted = false, webAppPersistentState = state)
    }

    fun getOrCreateConnectionByUrl(currentUrl: String) : ConnectionInfo {
        val x = urlWithoutQueryAndFragment(currentUrl)
        val result = knownConnections.firstOrNull { it.urlWithoutQueryAndFragment() == x}
        return result ?: createConnectionByUrl(currentUrl)
    }

    fun getConnectionByUrl(currentUrl: String): ConnectionInfo? {
        val x = urlWithoutQueryAndFragment(currentUrl)
        return knownConnections.firstOrNull { it.urlWithoutQueryAndFragment() == x}
    }

    fun getConnectionMenuUrl(mode : ConnectionManagerMode) : ConnectionInfo {
        val x = knownConnections.first {it.isConnectionManager}

        val result = when(mode) {
            is ConnectionManagerMode.ConnectionChooser -> x.copy(url = x.url + "?mode=connect")
            is ConnectionManagerMode.EditConnection -> x.copy(url = x.url + "?mode=edit&url=${URLEncoder.encode(mode.connInfo.url, "UTF-8")}")
            is ConnectionManagerMode.NewConnection -> x.copy(url = x.url + "?mode=new&url=${URLEncoder.encode(mode.url, "UTF-8")}")
        }

        Timber.d("getConnectionMenuUrl for mode=${mode} gave=${result}")
        return result
    }

    fun getConnectionManagerNewUrl(url : String) = getConnectionMenuUrl(ConnectionManagerMode.NewConnection(url))
    fun getConnectionManagerEditUrl(ci : ConnectionInfo) = getConnectionMenuUrl(ConnectionManagerMode.EditConnection(ci))
}
