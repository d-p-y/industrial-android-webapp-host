@file:Suppress("DEPRECATION") //due to Camera v1 API

package pl.todoit.IndustrialWebViewWithQr.model

import android.graphics.Matrix
import android.hardware.Camera
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
    val landscapeWidth : Int,
    val landscapeHeight : Int) {

    fun biggerDim() = Math.max(landscapeWidth, landscapeHeight)
}

class WidthAndHeightWithOrientation(
    val landscapeWidth : Int,
    val landscapeHeight : Int,
    val o:Orientation) {

    val aspectRatio = when(o) {
        Orientation.Landscape -> landscapeWidth.toDouble() / landscapeHeight
        Orientation.Portrait -> landscapeHeight.toDouble() / landscapeWidth
    }

    val actualWidth = when(o) {
        Orientation.Landscape -> landscapeWidth.toDouble() / landscapeHeight
        Orientation.Portrait -> landscapeHeight.toDouble() / landscapeWidth
    }

    val actualHeight = when(o) {
        Orientation.Landscape -> landscapeHeight.toDouble() / landscapeWidth
        Orientation.Portrait -> landscapeWidth.toDouble() / landscapeHeight
    }
}

val backFacingCamera = Camera.CameraInfo.CAMERA_FACING_BACK

fun findBiggestDimensions(prevs : List<Camera.Size>) : WidthAndHeight? {
    val result = prevs.fold(
        WidthAndHeight(0, 0),
        { acc, x -> if (Math.max(x.width, x.height) > acc.biggerDim()) WidthAndHeight(x.width, x.height) else acc})

    return if (result.landscapeWidth <= 0 || result.landscapeHeight <= 0) null else result
}

class CameraData(
    val camPreviewSize : WidthAndHeightWithOrientation,
    val cameraIndex : Int,
    val cameraInfo : Camera.CameraInfo,
    val camera : Camera,
    val cameraFocusModeAutoMode : Boolean,
    val displayToCameraAngle : Int,
    val screenResolution : WidthAndHeightWithOrientation) {}

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

    params.setPreviewSize(camPreviewSize.landscapeWidth, camPreviewSize.landscapeHeight)
    params.setPictureSize(camPreviewSize.landscapeWidth, camPreviewSize.landscapeHeight)

    camera.parameters = params //needed otherwise above parameters changes are ignored

    val display =
        act.windowManager?.defaultDisplay
        ?: return Result.Error("Cannot retrieve instance of android.view.Display")

    val (naturalToDisplayAngle, screenOrientation) = when(display.rotation) {
        Surface.ROTATION_0 -> Pair(0, Orientation.Portrait)
        Surface.ROTATION_90 -> Pair(90, Orientation.Landscape)
        Surface.ROTATION_180 -> Pair(180, Orientation.Portrait)
        Surface.ROTATION_270 -> Pair(270, Orientation.Landscape)
        else -> null
    } ?: return Result.Error("android.view.Display.rotation has unsupported value ${display.rotation}")

    Timber.d("camera preview size (${camPreviewSize.landscapeWidth};${camPreviewSize.landscapeHeight})")

    val screenRes = WidthAndHeight(display.width, display.height - (act.supportActionBar?.height ?: 0))

    Timber.d("screen size (${screenRes.landscapeWidth};${screenRes.landscapeHeight}) orientation=$screenOrientation")

    val naturalToCameraAngle = cameraInfo.orientation
    var cameraOrientation = when(cameraInfo.orientation) {
        0 -> Orientation.Portrait
        90 -> Orientation.Landscape
        180 -> Orientation.Portrait
        270 -> Orientation.Landscape
        else -> null
    } ?: return Result.Error("android.view.Display.rotation has unsupported value ${display.rotation}")

    val displayToCameraAngle = (360 + naturalToCameraAngle - naturalToDisplayAngle) % 360

    Timber.d("camera displayToCameraAngle=$displayToCameraAngle because camera.orientation is ${cameraInfo.orientation} and display.rotation is ${display.rotation}")

    camera.setDisplayOrientation(displayToCameraAngle)

    return Result.Ok(CameraData(
        WidthAndHeightWithOrientation(camPreviewSize.landscapeWidth, camPreviewSize.landscapeHeight, cameraOrientation),
        cameraIndex,
        cameraInfo,
        camera,
        cameraFocusModeAutoMode,
        displayToCameraAngle,
        WidthAndHeightWithOrientation(screenRes.landscapeWidth, screenRes.landscapeHeight, screenOrientation)
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

class LayoutStrategy {
    val simpleFitOnScreen : Boolean = true
}

data class LayoutProperties(
    val matrix: Matrix,
    val dimensions : Pair<LayoutDimension,LayoutDimension>?) {}

fun computeParamsForTextureView(cam : CameraData, strategy : LayoutStrategy) : LayoutProperties {
    val m = Matrix()

    val camAspRatio = cam.camPreviewSize.aspectRatio
    val scrAspRatio = cam.screenResolution.aspectRatio

    if (strategy.simpleFitOnScreen) {
        val toScreenSizeByWidthFactor = cam.screenResolution.actualWidth / cam.camPreviewSize.actualWidth
        val toScreenSizeByHeightFactor = cam.screenResolution.actualHeight / cam.camPreviewSize.actualHeight
        val toScreenSizeFactor = Math.min(toScreenSizeByWidthFactor, toScreenSizeByHeightFactor)

        Timber.d("camAspectRatio=($camAspRatio) screenAspectRatio=$scrAspRatio toScreenSizeFactorWidth=$toScreenSizeByWidthFactor toScreenSizeByHeightFactor=$toScreenSizeByHeightFactor")

        var w = cam.screenResolution.landscapeWidth * toScreenSizeFactor
        var h = cam.screenResolution.landscapeHeight * toScreenSizeFactor

        Timber.d("requesting preview size=(${w};${h})")

        val rotation = App.Instance.forcedCameraPreviewRotation
        if (rotation != null) {
            //android emulator bug workaround
            m.postRotate(rotation.angle.toFloat(), w.toFloat()/2, h.toFloat()/2)
        }

        return LayoutProperties(
            m,
            Pair(LayoutDimension.ValuePx(w.toInt()), LayoutDimension.ValuePx(h.toInt()))
        )
    }

    throw Exception("other fit preview strategy not implemented yet")
}
