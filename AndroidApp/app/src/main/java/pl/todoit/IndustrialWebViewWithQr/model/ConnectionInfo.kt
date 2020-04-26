package pl.todoit.IndustrialWebViewWithQr.model

data class ConnectionInfo(
    var url : String,
    var forceReloadFromNet : Boolean = false,
    var remoteDebuggerEnabled : Boolean = false,
    var forwardConsoleLogToLogCat : Boolean = false,
    val hapticFeedbackOnBarcodeRecognized : Boolean = true
)
