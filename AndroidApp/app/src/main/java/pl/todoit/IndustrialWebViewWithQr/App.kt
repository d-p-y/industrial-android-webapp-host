package pl.todoit.IndustrialWebViewWithQr

import android.app.Application

class ScanRequest(
    public var label : String,
    public var regexp : String?) {}

class App : Application() {
    var showScanQrImpl : ((req:ScanRequest)->Unit)? = null
    var hideScanQrImpl : (()->Unit)? = null

    var onQrScanSuccessImpl : ((x:String) -> Unit)? = null
    var onQrScanCancelImpl : ((_:Unit) -> Unit)? = null

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
}
