package pl.todoit.industrialAndroidWebAppHost.model

import kotlinx.serialization.Serializable

fun urlWithoutQueryAndFragment(url:String) : String {
    val q = url.indexOf('?')
    val f = url.indexOf('#')

    return when {
        q >= 0 -> url.substring(0, q)
        f >= 0 -> url.substring(0, f)
        else -> url
    }
}

fun urlWithoutFragment(url:String) : String {
    val f = url.indexOf('#')

    return when {
        f >= 0 -> url.substring(0, f)
        else -> url
    }
}

fun urlAndMaybeFragment(url:String) : Pair<String,String?> {
    val f = url.indexOf('#')

    return when {
        f >= 0 -> Pair(url.substring(0, f), url.substring(f+1))
        else -> Pair(url, null)
    }
}

@Serializable
data class ConnectionInfo(
    var persisted : Boolean,
    /**
     * url MAY NOT contain fragment part as fragment is used for holding (optional) state
     */
    var url : String,
    var name : String,
    var webAppPersistentState : String? = null,
    var forceReloadFromNet : Boolean = false,
    var remoteDebuggerEnabled : Boolean = false,
    var forwardConsoleLogToLogCat : Boolean = false,
    val hapticFeedbackOnBarcodeRecognized : Boolean = true,
    val mayManageConnections : Boolean = false,
    val isConnectionManager : Boolean = false,
    val hapticFeedbackOnAutoFocused : Boolean = true,
    val hasPermissionToTakePhoto : Boolean = true,
    val photoJpegQuality : Int = 85, //valid range 1-100
    val saveFormData : Boolean = false //as it breaks "native app" impression
)

fun ConnectionInfo.restoreStateFrom(from: ConnectionInfo) {
    this.persisted = from.persisted
    this.webAppPersistentState = from.webAppPersistentState
}

fun ConnectionInfo.urlWithoutFragment() = urlWithoutFragment(url)
fun ConnectionInfo.urlWithoutQueryAndFragment() = urlWithoutQueryAndFragment(url)
fun ConnectionInfo.urlAndMaybeFragment() = urlAndMaybeFragment(url)
fun ConnectionInfo.buildUrlWithState() = if (webAppPersistentState == null) url else url + "#" + webAppPersistentState
