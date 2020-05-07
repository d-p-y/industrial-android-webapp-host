package pl.todoit.IndustrialWebViewWithQr.fragments.barcodeDecoding

import pl.todoit.IndustrialWebViewWithQr.model.WidthAndHeight
import pl.todoit.IndustrialWebViewWithQr.model.extensions.diffInMilisecTo
import java.util.*

class ImageWithBarcode(
    val requested : Date,
    val hasFocus : Boolean,
    val resetStats : Boolean,
    val imageDim: WidthAndHeight,
    var imageData:ByteArray
) {
    val received = Date()
    fun timeToProduceMs() = requested.diffInMilisecTo(received).toInt()
}