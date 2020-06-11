@file:Suppress("DEPRECATION")

package pl.todoit.industrialAndroidWebAppHost.model

import android.graphics.Matrix
import android.widget.LinearLayout
import timber.log.Timber

sealed class LayoutDimension {
    class MatchParent() : LayoutDimension()
    class WrapContent() : LayoutDimension()
    class ValuePx(val px:Int) : LayoutDimension()

    fun toAndroid() : Int {
        return when(this) {
            is MatchParent -> LinearLayout.LayoutParams.MATCH_PARENT
            is WrapContent -> LinearLayout.LayoutParams.WRAP_CONTENT
            is ValuePx -> this.px
        }
    }
}

data class LayoutProperties(
    val matrix: Matrix,
    val dimensions : Pair<LayoutDimension,LayoutDimension>?,
    val marginURBL : Array<Int>?) {}

fun computeParamsForTextureView(cam : CameraData, strategy : LayoutStrategy) : LayoutProperties {
    val m = Matrix()

    val camAspRatio = cam.camPreviewSize.aspectRatio
    val scrAspRatio = cam.screenResolution.aspectRatio

    when(strategy) {
        is FitScreenLayoutStrategy -> {
            val toScreenSizeByWidthFactor = cam.screenResolution.widthAfterRotation.toFloat() / cam.camPreviewSize.widthAfterRotation
            val toScreenSizeByHeightFactor = cam.screenResolution.heightAfterRotation.toFloat() / cam.camPreviewSize.heightAfterRotation
            val toScreenSizeFactor = Math.min(toScreenSizeByWidthFactor, toScreenSizeByHeightFactor)

            Timber.d("camAspectRatio=($camAspRatio) screenAspectRatio=$scrAspRatio toScreenSizeFactorWidth=$toScreenSizeByWidthFactor toScreenSizeByHeightFactor=$toScreenSizeByHeightFactor")

            var w = cam.camPreviewSize.widthAfterRotation * toScreenSizeFactor
            var h = cam.camPreviewSize.heightAfterRotation * toScreenSizeFactor

            Timber.d("requesting preview size=(${w};${h})")

            cam.camera.parameters.setPreviewSize(w.toInt(), h.toInt())
            cam.camera.parameters = cam.camera.parameters

            var horizontalSpaceLeft = cam.screenResolution.widthAfterRotation - w
            var verticalSpaceLeft = cam.screenResolution.heightAfterRotation - h

            return LayoutProperties(
                m,
                Pair(LayoutDimension.ValuePx(w.toInt()), LayoutDimension.ValuePx(h.toInt())),
                arrayOf(
                    (verticalSpaceLeft/2.0f).toInt(),
                    (horizontalSpaceLeft/2.0f).toInt(),
                    (verticalSpaceLeft/2.0f).toInt(),
                    (horizontalSpaceLeft/2.0f).toInt()
                )
            )
        }

        is MatchWidthWithFixedHeightLayoutStrategy -> {
            val expectedHeightPx = (strategy.heightMm * cam.mmToPx).toInt()

            val toScreenSizeByWidthFactor = cam.screenResolution.widthAfterRotation.toFloat() / cam.camPreviewSize.widthAfterRotation
            val toScreenSizeByHeightFactor = expectedHeightPx.toFloat() / cam.camPreviewSize.heightAfterRotation
            val toScreenSizeFactor = Math.max(toScreenSizeByWidthFactor, toScreenSizeByHeightFactor)

            val onScreenAspectRatio = cam.screenResolution.widthAfterRotation.toFloat() / expectedHeightPx
            var correctAspectBy = onScreenAspectRatio / cam.camPreviewSize.aspectRatio

            Timber.d("expectedPreviewSize=(${cam.screenResolution.widthAfterRotation};${expectedHeightPx}) onScreenAspectRatio=${onScreenAspectRatio} correctAspectBy=${correctAspectBy} toScreenSizeFactor=(${toScreenSizeByWidthFactor};${toScreenSizeByHeightFactor}) toScreenSizeFactor=$toScreenSizeFactor")

            val translX = (cam.camPreviewSize.widthAfterRotation - (cam.camPreviewSize.widthAfterRotation / toScreenSizeFactor))
            val translY = (cam.camPreviewSize.heightAfterRotation - (cam.camPreviewSize.heightAfterRotation / (toScreenSizeFactor*correctAspectBy)))

            m.preTranslate(translX, translY)

            m.setScale(
                toScreenSizeFactor, toScreenSizeFactor * correctAspectBy,
                cam.camPreviewSize.widthAfterRotation/2.0f,
                (cam.camPreviewSize.heightAfterRotation-translY)/2.0f
            )

            Timber.d("transl=(${translX};${translY})")

            return LayoutProperties(
                m,
                Pair(
                    LayoutDimension.MatchParent(),
                    LayoutDimension.ValuePx(expectedHeightPx)
                ),
                arrayOf(
                    (strategy.paddingTopMm * cam.mmToPx).toInt(),
                    0,0,0)
            )
        }
    }

    throw Exception("unsupported ILayoutStrategy")
}
