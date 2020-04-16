@file:Suppress("DEPRECATION") //due to Camera v1 API

package pl.todoit.IndustrialWebViewWithQr.model

import android.graphics.Matrix
import android.hardware.Camera
import android.util.TypedValue
import android.view.Surface
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import pl.todoit.IndustrialWebViewWithQr.App
import timber.log.Timber
import java.lang.Exception

enum class RightAngleRotation(val angle : Int) {
    RotateBy0(0),
    RotateBy90(90),
    RotateBy180(180),
    RotateBy270(270)
}

enum class Orientation {
    Portrait,
    Landscape
}

class WidthAndHeight(
    val width : Int,
    val height : Int) {

    fun biggerDim() = Math.max(width, height)
}

class WidthAndHeightWithOrientation(
    val widthWithoutRotation : Int,
    val heightWithoutRotation : Int,
    val o:RightAngleRotation) {

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
}

val backFacingCamera = Camera.CameraInfo.CAMERA_FACING_BACK

fun findBiggestDimensions(prevs : List<Camera.Size>) : WidthAndHeight? {
    val result = prevs.fold(
        WidthAndHeight(0, 0),
        { acc, x -> if (Math.max(x.width, x.height) > acc.biggerDim()) WidthAndHeight(x.width, x.height) else acc})

    return if (result.width <= 0 || result.height <= 0) null else result
}

class CameraData(
    val camPreviewSize : WidthAndHeightWithOrientation,
    val cameraIndex : Int,
    val cameraInfo : Camera.CameraInfo,
    val camera : Camera,
    val cameraFocusModeAutoMode : Boolean,
    val displayToCameraAngle : Int,
    val screenResolution : WidthAndHeightWithOrientation,
    val mmToPx:Float) {}

fun initializeFirstMatchingCamera(act: AppCompatActivity, condition : (Camera.CameraInfo) -> Boolean) : Result<CameraData, String> {
    val cameras = sequence {
        for (i in 0..Camera.getNumberOfCameras()) {
            val ci = Camera.CameraInfo()
            Camera.getCameraInfo(i, ci)
            yield(Pair(i, ci))
        }
    }

    val cameraIndexAndInfo = cameras
        .filter { condition(it.second) }
        .firstOrNull()
        ?: return Result.Error("No matching camera found")

    val cameraIndex = cameraIndexAndInfo.first
    val cameraInfo = cameraIndexAndInfo.second

    Timber.d("will use camera index=$cameraIndex")

    val camera = Camera.open(cameraIndex)

    val params = camera.parameters
    val maybeContinuousFocusMode =
        params.supportedFocusModes.filter { continuousFocusModes.contains(it) }.firstOrNull()

    var cameraFocusModeAutoMode = false

    if (maybeContinuousFocusMode != null) {
        params.focusMode = maybeContinuousFocusMode
        Timber.d("camera supports continuous auto focus mode=${maybeContinuousFocusMode}")
    } else {
        //emulator doesn't support any focus mode
        if (!App.Instance.permitNoContinousFocusInCamera) return Result.Error("Camera doesn't support continuous focus")
    }

    var camPreviewSize =
        findBiggestDimensions(params.supportedPreviewSizes)
        ?: return Result.Error("Camera doesn't seem to have any sane preview size")

    params.setPreviewSize(camPreviewSize.width, camPreviewSize.height)
    params.setPictureSize(camPreviewSize.width, camPreviewSize.height)

    camera.parameters = params //needed otherwise above parameters changes are ignored

    val display =
        act.windowManager?.defaultDisplay
        ?: return Result.Error("Cannot retrieve instance of android.view.Display")

    val (naturalToDisplayAngle, screenAngle) = when(display.rotation) {
        Surface.ROTATION_0 -> Pair(0, RightAngleRotation.RotateBy0)
        Surface.ROTATION_90 -> Pair(90, RightAngleRotation.RotateBy90)
        Surface.ROTATION_180 -> Pair(180, RightAngleRotation.RotateBy180)
        Surface.ROTATION_270 -> Pair(270, RightAngleRotation.RotateBy270)
        else -> null
    } ?: return Result.Error("android.view.Display.rotation has unsupported value ${display.rotation}")

    Timber.d("camera preview size (${camPreviewSize.width};${camPreviewSize.height})")

    val screenRes = WidthAndHeight(display.width, display.height - (act.supportActionBar?.height ?: 0))

    Timber.d("screen size (${screenRes.width};${screenRes.height}) orientation=$screenAngle")

    val naturalToCameraAngle = cameraInfo.orientation
    var cameraAngle = when(cameraInfo.orientation) {
        0 -> RightAngleRotation.RotateBy0
        90 -> RightAngleRotation.RotateBy90
        180 -> RightAngleRotation.RotateBy180
        270 -> RightAngleRotation.RotateBy270
        else -> null
    } ?: return Result.Error("android.view.Display.rotation has unsupported value ${display.rotation}")

    val displayToCameraAngle = (360 + naturalToCameraAngle - naturalToDisplayAngle) % 360

    Timber.d("camera displayToCameraAngle=$displayToCameraAngle because camera.orientation is ${cameraInfo.orientation} and display.rotation is ${display.rotation}")

    camera.setDisplayOrientation(displayToCameraAngle)

    return Result.Ok(CameraData(
        WidthAndHeightWithOrientation(camPreviewSize.width, camPreviewSize.height, cameraAngle),
        cameraIndex,
        cameraInfo,
        camera,
        cameraFocusModeAutoMode,
        displayToCameraAngle,
        WidthAndHeightWithOrientation(screenRes.width, screenRes.height, screenAngle),
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_MM,
            1.0f,
            act.resources.displayMetrics
        )
    ))
}

fun initializeFirstBackFacingCamera(act: AppCompatActivity) : Result<CameraData, String> =
    initializeFirstMatchingCamera(act, {it.facing == backFacingCamera })

val continuousFocusModes = listOf(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE, Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)

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
