package pl.todoit.IndustrialWebViewWithQr.model

import kotlinx.coroutines.channels.SendChannel

class ScanRequest(
    var label : String,
    var regexp : String?,
    val scanResult : SendChannel<String?>,
    val layoutDimensions : LayoutStrategy = LayoutStrategy() //TODO to be implemented properly
) {}
