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

    public val navigation = Channel<NavigationRequest>()
    public val webViewFragmentParams = ParamContainer<ConnectionInfo>()
    public val connSettFragmentParams = ParamContainer<ConnectionInfo>()
    public val scanQrFragmentParams = ParamContainer<ScanRequest>()

    //TODO use persistence
    var currentConnection =
        ConnectionInfo(
            url = "http://192.168.1.8:8888",
            forceReloadFromNet = isForcedDevelopmentMode,
            remoteDebuggerEnabled = isForcedDevelopmentMode,
            forwardConsoleLogToLogCat = isForcedDevelopmentMode
        )
    val imagesToDecodeQueueSize = 10 // ~~60MB
    val barcodeReplyQueueSize = 2 //bigger than one so that it is not suspended
    val sufficientStatsSize =5
    val decodeAtLeastOnceEveryMs = 1000 //unlikely needed

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
