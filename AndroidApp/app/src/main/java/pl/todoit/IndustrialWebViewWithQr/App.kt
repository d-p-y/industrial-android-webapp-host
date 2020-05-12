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
    val webViewFragmentParams = ParamContainer<ConnectionInfo>()
    val connSettFragmentParams = ParamContainer<ConnectionInfo>()
    val scanQrFragmentParams = ParamContainer<Pair<ScanRequest,OverlayImage?>>()
    var maxComputationsAtOnce = 0

    //TODO enumerate assets programmatically to find all index.html
    //TODO use persistence for per-URL-settings-and-permissions
    var currentConnection =
        ConnectionInfo(
            url = "file:///android_asset/DemoWebSite/index.html",
            forceReloadFromNet = isForcedDevelopmentMode,
            remoteDebuggerEnabled = isForcedDevelopmentMode,
            forwardConsoleLogToLogCat = isForcedDevelopmentMode
        )
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

    fun initializeConnection(requestedUrl: String) {
        //TODO retrieve settings from preferences. If missing them, ask user for them
        currentConnection.url = requestedUrl
    }
}
