package pl.todoit.IndustrialWebViewWithQr

import android.app.Application
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import pl.todoit.IndustrialWebViewWithQr.model.ConnectionInfo
import pl.todoit.IndustrialWebViewWithQr.model.Navigator
import pl.todoit.IndustrialWebViewWithQr.model.ParamContainer
import pl.todoit.IndustrialWebViewWithQr.model.ScanRequest
import timber.log.Timber
import java.util.concurrent.Executors

val Dispatchers_UI = Dispatchers.Main

class App : Application(), CoroutineScope by MainScope() {
    companion object {
        lateinit var Instance : App
    }

    private lateinit var _parallelComputations : ExecutorCoroutineDispatcher
    val isForcedDevelopmentMode = true
    val isRunningInEmulator = Build.PRODUCT.toLowerCase().contains("sdk")
    val permitNoContinousFocusInCamera = isRunningInEmulator
    val navigator = Navigator()
    var maxComputationsAtOnce = 0

    //TODO read from private storage + add from assets
    var knownConnections = listOf(
        ConnectionInfo(
            url = "file:///android_asset/ConnectionsManager/index.html",
            name = "Connection Manager",
            forceReloadFromNet = isForcedDevelopmentMode,
            remoteDebuggerEnabled = isForcedDevelopmentMode,
            forwardConsoleLogToLogCat = isForcedDevelopmentMode,
            mayManageConnections = true,
            isConnectionManager = true
        ),
        ConnectionInfo(
            url = "file:///android_asset/DemoWebSite/index.html",
            name = "Demo",
            forceReloadFromNet = isForcedDevelopmentMode,
            remoteDebuggerEnabled = isForcedDevelopmentMode,
            hapticFeedbackOnBarcodeRecognized = true,
            forwardConsoleLogToLogCat = isForcedDevelopmentMode
        ),
        ConnectionInfo(
            url = "file:///android_asset/SimpleQrScanner/index.html",
            name = "Simple scanner",
            forceReloadFromNet = isForcedDevelopmentMode,
            remoteDebuggerEnabled = isForcedDevelopmentMode,
            hapticFeedbackOnBarcodeRecognized = true,
            forwardConsoleLogToLogCat = isForcedDevelopmentMode
        )
    )

    //TODO enumerate assets programmatically to find all index.html
    //TODO use persistence for per-URL-settings-and-permissions
    lateinit var currentConnection : ConnectionInfo

    val imagesToDecodeQueueSize = 20 //each raw FullHD photo consumes ~6MB. Nexus 5 produces photos every ~40ms and decodes 4 of them in parallel every ~200ms
    val decodeAtLeastOnceEveryMs = 1000 //unlikely needed
    val expectPictureTakenAtLeastAfterMs : Long = 300 //workaround for unlikely: E/Camera-JNI: Couldn't allocate byte array for JPEG data

    var overlayImageOnPause : OverlayImage? = null

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

    /**
     * @return true if created
     */
    fun getOrCreateConnectionByUrl(currentUrl: String): Pair<Boolean, ConnectionInfo> {
        val result = knownConnections.firstOrNull { it.url == currentUrl}
        //TODO store somewhere information that given connection is persisted (as user asked) or is transient (on unkown URL it should ask for permissions)
        return if (result == null) Pair(true, ConnectionInfo(url = currentUrl, name = "Unknown")) else Pair(false, result)
    }

    fun getConnectionByUrl(currentUrl: String): ConnectionInfo? = knownConnections.firstOrNull { it.url == currentUrl}
    fun getConnectionMenuUrl() : ConnectionInfo = knownConnections.first {it.isConnectionManager}

    //TODO unknown connection - need more data, ask connection manager for that
    fun getConnectionManagerNewUrl(url : String) = getConnectionMenuUrl()
}
