package pl.todoit.IndustrialWebViewWithQr.model

import pl.todoit.IndustrialWebViewWithQr.App

data class ConnectionInfo(
    var url : String,
    var forceReloadFromNet : Boolean = false,
    var remoteDebuggerEnabled : Boolean = false,
    var forwardConsoleLogToLogCat : Boolean = false)
