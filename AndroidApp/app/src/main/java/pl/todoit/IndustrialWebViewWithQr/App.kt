package pl.todoit.IndustrialWebViewWithQr

import android.app.Application
import kotlinx.serialization.Serializable
import timber.log.Timber

data class ScanRequest(
    public var label : String,
    public var regexp : String?) {}

@Serializable
data class AndroidReply (
    var PromiseId : String,
    var IsSuccess : Boolean,
    var Reply : String?
)

data class ConnectionInfo(var url : String)

class App : Application() {
    //base "canvas"
    var showWebBrowser : ((x:ConnectionInfo)->Unit)? = null
    //var hideWebBrowser : (()->Unit)? = null

    var showConnectionsSettings : ((x:ConnectionInfo)->Unit)? = null
    var hideConnectionsSettings : (()->Unit)? = null

    //popups
    var showScanQrImpl : ((req:ScanRequest)->Unit)? = null
    var hideScanQrImpl : (()->Unit)? = null

    var onQrScanSuccessImpl : ((x:String) -> Unit)? = null
    var onQrScanCancelImpl : ((_:Unit) -> Unit)? = null

    //TODO use persistence
    var currentConnection = ConnectionInfo("http://192.168.1.8:8888")

    public fun requestScanQr(req : ScanRequest, onSuccess : ((x:String) -> Unit), onCancel:((_:Unit) -> Unit) ) {
        onQrScanSuccessImpl = { x : String ->
            hideScanQrImpl?.invoke()
            onSuccess.invoke(x)
        }

        onQrScanCancelImpl = {
            hideScanQrImpl?.invoke()
            onCancel.invoke(Unit)
        }

        showScanQrImpl?.invoke(req)
    }

    override fun onCreate() {
        super.onCreate()
        Timber.plant(Timber.DebugTree())

        Timber.i("logging initialized")
    }
}
