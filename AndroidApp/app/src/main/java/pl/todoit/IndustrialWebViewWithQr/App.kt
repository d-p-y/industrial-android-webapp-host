package pl.todoit.IndustrialWebViewWithQr

import android.app.Application
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import pl.todoit.IndustrialWebViewWithQr.fragments.ConnectionsSettingsFragment
import pl.todoit.IndustrialWebViewWithQr.fragments.ScanQrFragment
import pl.todoit.IndustrialWebViewWithQr.fragments.WebViewFragment
import pl.todoit.IndustrialWebViewWithQr.model.ConnectionInfo
import pl.todoit.IndustrialWebViewWithQr.model.ParamContainer
import pl.todoit.IndustrialWebViewWithQr.model.ScanRequest
import timber.log.Timber
import java.lang.Exception

val Dispatchers_UI = Dispatchers.Main

class App : Application(), CoroutineScope by MainScope() {
    companion object {
        public lateinit var Instance : App
    }

    public val navigation = Channel<NavigationRequest>()

    public val webViewFragmentParams = ParamContainer<ConnectionInfo>()
    public val connSettFragmentParams = ParamContainer<ConnectionInfo>()
    public val scanQrFragmentParams = ParamContainer<ScanRequest>()

    //TODO use persistence
    var currentConnection =
        ConnectionInfo("http://192.168.1.8:8888")

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())

        Instance = this

        Timber.i("logging initialized")
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
