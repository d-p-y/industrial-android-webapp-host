package pl.todoit.IndustrialWebViewWithQr

import android.app.Application
import android.os.Build
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import pl.todoit.IndustrialWebViewWithQr.model.ConnectionInfo
import pl.todoit.IndustrialWebViewWithQr.model.ParamContainer
import pl.todoit.IndustrialWebViewWithQr.model.ScanRequest
import timber.log.Timber

val Dispatchers_UI = Dispatchers.Main

class App : Application(), CoroutineScope by MainScope() {
    companion object {
        lateinit var Instance : App
    }

    val isForcedDevelopmentMode = true
    val isRunningInEmulator = Build.PRODUCT.toLowerCase().contains("sdk")
    val permitNoContinousFocusInCamera = isRunningInEmulator

    val navigation = Channel<NavigationRequest>()
    val webViewFragmentParams = ParamContainer<ConnectionInfo>()
    val connSettFragmentParams = ParamContainer<ConnectionInfo>()
    val scanQrFragmentParams = ParamContainer<Pair<ScanRequest,OverlayImage?>>()

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
        Timber.i("logging initialized")

        Instance = this
    }

    fun launchCoroutine (block : suspend () -> Unit) {
        launch {
            withContext(Dispatchers_UI) {
                block.invoke()
            }
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        cancel() //coroutines cancellation
    }
}
