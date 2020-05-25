package pl.todoit.IndustrialWebViewWithQr.model

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionInfo(
    var persisted : Boolean,
    var url : String,
    var name : String,
    var forceReloadFromNet : Boolean = false,
    var remoteDebuggerEnabled : Boolean = false,
    var forwardConsoleLogToLogCat : Boolean = false,
    val hapticFeedbackOnBarcodeRecognized : Boolean = true,
    val mayManageConnections : Boolean = false,
    val isConnectionManager : Boolean = false,
    val hapticFeedbackOnAutoFocused : Boolean = true,
    val hasPermissionToTakePhoto : Boolean = true,
    val photoJpegQuality : Int = 85 //valid range 1-100
)

//TODO consider using it within implementation of ConnectionInfo.equals()
fun ConnectionInfo.urlWithoutQueryAndFragment() : String {
    val q = url.indexOf('?')
    val f = url.indexOf('#')

    return when {
        q >= 0 -> url.substring(0, q)
        f >= 0 -> url.substring(0, f)
        else -> url
    }
}
