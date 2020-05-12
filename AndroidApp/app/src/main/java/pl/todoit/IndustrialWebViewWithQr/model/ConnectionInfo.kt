package pl.todoit.IndustrialWebViewWithQr.model

import kotlinx.serialization.Serializable

@Serializable
data class ConnectionInfo(
    var url : String,
    var name : String,
    var forceReloadFromNet : Boolean = false,
    var remoteDebuggerEnabled : Boolean = false,
    var forwardConsoleLogToLogCat : Boolean = false,
    val hapticFeedbackOnBarcodeRecognized : Boolean = true,
    val mayManageConnections : Boolean = false,
    val isConnectionManager : Boolean = false
)
