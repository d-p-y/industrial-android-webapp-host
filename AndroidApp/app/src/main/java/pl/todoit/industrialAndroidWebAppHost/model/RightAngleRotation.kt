package pl.todoit.industrialAndroidWebAppHost.model

enum class RightAngleRotation(val angle : Int) {
    RotateBy0(0),
    RotateBy90(90),
    RotateBy180(180),
    RotateBy270(270)
}

fun angleToRightAngle(inp : Int) : RightAngleRotation? =
    when(inp) {
        0 -> RightAngleRotation.RotateBy0
        90 -> RightAngleRotation.RotateBy90
        180 -> RightAngleRotation.RotateBy180
        270 -> RightAngleRotation.RotateBy270
        else -> null
    }

operator fun RightAngleRotation.plus(other : RightAngleRotation) =
    angleToRightAngle(((this.angle + other.angle) + 360) % 360) ?: throw Exception("unreachable code: rightangles sum is not a rightangle")
