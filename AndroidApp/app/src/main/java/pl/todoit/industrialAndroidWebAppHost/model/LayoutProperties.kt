@file:Suppress("DEPRECATION")

package pl.todoit.industrialAndroidWebAppHost.model

import android.graphics.Matrix
import android.view.Gravity
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
    val layoutGravity : Int,
    val matrix: Matrix,
    val dimensions : Pair<LayoutDimension,LayoutDimension>,
    val marginURBL : Array<Int>?) {}

fun computeMatrix(cam : CameraData, expectedHeightPx:Int) : Matrix {
    val result = Matrix()
    val toScreenSizeByWidthFactor = cam.screenResolution.widthAfterRotation.toFloat() / cam.camPreviewSize.widthAfterRotation
    val toScreenSizeByHeightFactor = expectedHeightPx.toFloat() / cam.camPreviewSize.heightAfterRotation
    val toScreenSizeFactor = Math.max(toScreenSizeByWidthFactor, toScreenSizeByHeightFactor)

    val onScreenAspectRatio = cam.screenResolution.widthAfterRotation.toFloat() / expectedHeightPx
    var correctAspectBy = onScreenAspectRatio / cam.camPreviewSize.aspectRatio

    Timber.d("expectedPreviewSize=(${cam.screenResolution.widthAfterRotation};${expectedHeightPx}) onScreenAspectRatio=${onScreenAspectRatio} correctAspectBy=${correctAspectBy} toScreenSizeFactor=(${toScreenSizeByWidthFactor};${toScreenSizeByHeightFactor}) toScreenSizeFactor=$toScreenSizeFactor")

    val translX = (cam.camPreviewSize.widthAfterRotation - (cam.camPreviewSize.widthAfterRotation / toScreenSizeFactor))
    val translY = (cam.camPreviewSize.heightAfterRotation - (cam.camPreviewSize.heightAfterRotation / (toScreenSizeFactor*correctAspectBy)))

    result.preTranslate(translX, translY)

    result.setScale(
        toScreenSizeFactor, toScreenSizeFactor * correctAspectBy,
        cam.camPreviewSize.widthAfterRotation/2.0f,
        (cam.camPreviewSize.heightAfterRotation-translY)/2.0f
    )

    Timber.d("transl=(${translX};${translY})")
    return result
}

fun computeParamsForTextureView(cam : CameraData, strategy : LayoutStrategy) : LayoutProperties {
    when(strategy) {
        is FillScreenLayoutStrategy -> {
            val toolbarHeight = if (strategy.hideToolbar) 0 else cam.toolbar.heightPx
            val expectedHeightPx = (cam.screenResolution.heightAfterRotation + toolbarHeight)

            Timber.d("FillScreenLayoutStrategy expectedHeightPx = $expectedHeightPx")
            return LayoutProperties(
                Gravity.TOP,
                computeMatrix(cam, expectedHeightPx),
                Pair(
                    LayoutDimension.MatchParent(),
                    LayoutDimension.ValuePx(expectedHeightPx)
                ),
                arrayOf(0, 0, 0, 0)
            )
        }

        is MatchWidthWithFixedHeightLayoutStrategy -> {
            val expectedHeightPx = (strategy.heightMm * cam.mmToPx).toInt()
            Timber.d("MatchWidthWithFixedHeightLayoutStrategy expectedHeightPx = $expectedHeightPx")

            return LayoutProperties(
                if (strategy.paddingOriginIsTop) Gravity.TOP else Gravity.BOTTOM,
                computeMatrix(cam, expectedHeightPx),
                Pair(
                    LayoutDimension.MatchParent(),
                    LayoutDimension.ValuePx(expectedHeightPx)
                ),
                arrayOf(
                    //up
                    if (strategy.paddingOriginIsTop) (strategy.paddingMm * cam.mmToPx).toInt() else 0,
                    0,
                    //bottom
                    if (!strategy.paddingOriginIsTop) (strategy.paddingMm * cam.mmToPx).toInt() else 0,
                    0)
            )
        }
    }

    throw Exception("unsupported ILayoutStrategy")
}
