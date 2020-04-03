package pl.todoit.IndustrialWebViewWithQr

import android.app.Application
import timber.log.Timber

class App : Application() {
    //TODO use persistence
    var currentConnection = ConnectionInfo("http://192.168.1.8:8888")

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())

        Timber.i("logging initialized")
    }
}
