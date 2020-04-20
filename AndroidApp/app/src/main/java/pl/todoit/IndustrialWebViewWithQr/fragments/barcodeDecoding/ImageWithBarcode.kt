package pl.todoit.IndustrialWebViewWithQr.fragments.barcodeDecoding

import pl.todoit.IndustrialWebViewWithQr.model.WidthAndHeight
import java.util.*

class ImageWithBarcode(
    val hasFocus : Boolean,
    val imageDim: WidthAndHeight,
    var imageData:ByteArray
) {
    val requestedAt = Date()
}