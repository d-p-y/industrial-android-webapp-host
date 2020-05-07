package pl.todoit.IndustrialWebViewWithQr

import android.app.Application
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import pl.todoit.IndustrialWebViewWithQr.model.ConnectionInfo
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

    val navigation = Channel<NavigationRequest>()
    val webViewFragmentParams = ParamContainer<ConnectionInfo>()
    val connSettFragmentParams = ParamContainer<ConnectionInfo>()
    val scanQrFragmentParams = ParamContainer<Pair<ScanRequest,OverlayImage?>>()
    var maxComputationsAtOnce = 0

    //TODO use persistence
    var currentConnection =
        ConnectionInfo(
            url = "file:///android_asset/DemoWebSite/index.html",
            forceReloadFromNet = isForcedDevelopmentMode,
            remoteDebuggerEnabled = isForcedDevelopmentMode,
            forwardConsoleLogToLogCat = isForcedDevelopmentMode
        )
    val imagesToDecodeQueueSize = 10 // each raw FullHD photo consumes ~6MB
    val sufficientStatsSize = 5
    val decodeAtLeastOnceEveryMs = 1000 //unlikely needed
    val expectPictureTakenAtLeastAfterMs : Long = 300 //workaround for: E/Camera-JNI: Couldn't allocate byte array for JPEG data

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

    override fun onTerminate() {
        super.onTerminate()
        cancel() //coroutines cancellation
        _parallelComputations.close()

    }
}
