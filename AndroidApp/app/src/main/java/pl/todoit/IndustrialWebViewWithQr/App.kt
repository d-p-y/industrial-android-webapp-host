package pl.todoit.IndustrialWebViewWithQr

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
import kotlinx.serialization.builtins.list
import pl.todoit.IndustrialWebViewWithQr.model.*
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

    private lateinit var _parallelComputations : ExecutorCoroutineDispatcher

    val navigator = Navigator()
    var maxComputationsAtOnce = 0
    var overlayImageOnPause : OverlayImage? = null
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
                jsonStrict.parse(ConnectionInfo.serializer().list, f.readText())
                    .also { it.forEach { it.persisted = true }  }
            } catch (ex : Exception) {
                Timber.e("unable to deserialize or read persisted connection infos")
                null
            }
        } else null
    }

    private fun persistKnownConnections() {
        //TODO first write to temp file then rename for extra safety
        File(this.filesDir, connectionInfosFileName)
            .writeText(jsonStrict.stringify(ConnectionInfo.serializer().list, knownConnections))
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

    fun persistConnection(conn:ConnectionInfo) {
        val existingAt = knownConnections
            .mapIndexed { i,x -> if (x.urlWithoutQueryAndFragment() == conn.urlWithoutQueryAndFragment()) i else null }
            .filterNotNull()
            .firstOrNull()

        if (existingAt != null) {
            Timber.d("replacing connectionInfo at $existingAt")
            knownConnections.removeAt(existingAt)
            knownConnections.add(existingAt, conn)
        } else {
            Timber.d("appending new connectionInfo")
            knownConnections.add(conn)
        }

        persistKnownConnections()
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

        cleanSoundSuccessScanIfNeeded()

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

    fun getOrCreateConnectionByUrl(currentUrl: String) : ConnectionInfo {
        val newConn = ConnectionInfo(url = currentUrl, name = "Unknown", persisted = false)
        val result = knownConnections.firstOrNull { it.urlWithoutQueryAndFragment() == newConn.urlWithoutQueryAndFragment()}
        return result ?: newConn
    }

    fun getConnectionByUrl(currentUrl: String): ConnectionInfo? = knownConnections.firstOrNull { it.url == currentUrl}
    fun getConnectionMenuUrl(mode : ConnectionManagerMode) : ConnectionInfo {
        val x = knownConnections.first {it.isConnectionManager}

        val result = when(mode) {
            is ConnectionManagerMode.ConnectionChooser -> x.copy(url = x.url + "#mode=choice")
            is ConnectionManagerMode.EditConnection -> x.copy(url = x.url + "#mode=edit&url=${URLEncoder.encode(mode.connInfo.url, "UTF-8")}")
            is ConnectionManagerMode.NewConnection -> x.copy(url = x.url + "#mode=new&url=${URLEncoder.encode(mode.url, "UTF-8")}")
        }

        Timber.d("getConnectionMenuUrl for mode=${mode} gave=${result}")
        return result
    }

    fun getConnectionManagerNewUrl(url : String) = getConnectionMenuUrl(ConnectionManagerMode.NewConnection(url))
    fun getConnectionManagerEditUrl(ci : ConnectionInfo) = getConnectionMenuUrl(ConnectionManagerMode.EditConnection(ci))

    private fun cleanSoundSuccessScanIfNeeded() {
        var f = getScanSuccessSoundFilePath()
        if (f.exists()) {
            f.delete()
        }
    }

    fun setSoundSuccessScan(content: ByteArray) {
        var f = getScanSuccessSoundFilePath()
        f.writeBytes(content)
    }

    fun getScanSuccessSoundFilePath() = File(filesDir, scanSuccessSoundFileName)
}
