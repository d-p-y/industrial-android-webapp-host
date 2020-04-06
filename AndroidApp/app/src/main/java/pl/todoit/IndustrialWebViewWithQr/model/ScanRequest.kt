package pl.todoit.IndustrialWebViewWithQr.model

import kotlinx.coroutines.channels.Channel

class ScanRequest(
    var label : String,
    var regexp : String?,
    val scanResult : Channel<String?> = Channel()
) {}