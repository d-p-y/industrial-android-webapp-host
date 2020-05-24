package pl.todoit.IndustrialWebViewWithQr.model

import android.graphics.Rect

class WidthAndHeightWithOrientation(
    val widthWithoutRotation : Int,
    val heightWithoutRotation : Int,
    o:RightAngleRotation) {

    val widthAfterRotation = when(o) {
        RightAngleRotation.RotateBy0, RightAngleRotation.RotateBy180 -> widthWithoutRotation
        RightAngleRotation.RotateBy90, RightAngleRotation.RotateBy270 -> heightWithoutRotation
    }

    val heightAfterRotation = when(o) {
        RightAngleRotation.RotateBy0, RightAngleRotation.RotateBy180 -> heightWithoutRotation
        RightAngleRotation.RotateBy90, RightAngleRotation.RotateBy270 -> widthWithoutRotation
    }

    val aspectRatio = when(o) {
        RightAngleRotation.RotateBy0, RightAngleRotation.RotateBy180 -> widthWithoutRotation.toFloat() / heightWithoutRotation
        RightAngleRotation.RotateBy90, RightAngleRotation.RotateBy270 -> heightWithoutRotation.toFloat() / widthWithoutRotation
    }

    fun toRect() : Rect = Rect(0, 0, widthWithoutRotation, heightWithoutRotation)
}
