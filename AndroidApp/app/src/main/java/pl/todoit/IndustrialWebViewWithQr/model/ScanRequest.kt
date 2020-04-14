package pl.todoit.IndustrialWebViewWithQr.model

import kotlinx.coroutines.channels.SendChannel

class ScanRequest(
    var label : String,
    var regexp : String?,
    val scanResult : SendChannel<String?>,
    val layoutDimensions : LayoutStrategy = LayoutStrategy(
        strategySimpleFillScreen = false,
        strategyMatchParentWidthWithFixedHeight = true,
        paddingTopMm = 30,
        heightMm = 20
    )
) {}
