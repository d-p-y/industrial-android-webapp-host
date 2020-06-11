package pl.todoit.industrialAndroidWebAppHost.fragments.barcodeDecoding

import com.google.zxing.*
import com.google.zxing.common.HybridBinarizer
import pl.todoit.industrialAndroidWebAppHost.model.Result
import pl.todoit.industrialAndroidWebAppHost.model.extensions.toEnumSet
import java.util.HashMap

class ZxingMultiFormatReader(
    barcodeFormats : Array<BarcodeFormat>
) : IItemConsumer<ImageWithBarcode, String> {
    private val _mfr = MultiFormatReader().apply {
        val hints = HashMap<DecodeHintType, Any>()
        hints[DecodeHintType.POSSIBLE_FORMATS] = barcodeFormats.toEnumSet(BarcodeFormat::class.java) as Any
        setHints(hints)
    }

    override fun process(inp: ImageWithBarcode) : Result<String, Exception> =
        try {
            val src = PlanarYUVLuminanceSource(
                inp.imageData,
                inp.imageDim.width, inp.imageDim.height,
                0, 0,
                inp.imageDim.width, inp.imageDim.height,
                false
            )

            val bitmap = BinaryBitmap(HybridBinarizer(src))

            Result.Ok(_mfr.decodeWithState(bitmap).text)
        } catch (re : Exception) {
            Result.Error(re)
        } finally {
            _mfr.reset()
        }
}
