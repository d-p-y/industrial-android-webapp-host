@file:Suppress("DEPRECATION") //due to Camera v1 API

package pl.todoit.industrialAndroidWebAppHost.model

import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.util.TypedValue
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import androidx.appcompat.app.AppCompatActivity
import pl.todoit.industrialAndroidWebAppHost.App
import timber.log.Timber
import java.io.Closeable
import kotlin.math.absoluteValue

val backFacingCamera = Camera.CameraInfo.CAMERA_FACING_BACK

fun findBiggestDimensions(prevs : List<Camera.Size>) : WidthAndHeight? {
    val result = prevs.fold(
        WidthAndHeight(0, 0),
        { acc, x -> if (Math.max(x.width, x.height) > acc.biggerDim()) WidthAndHeight(x.width, x.height) else acc})

    return if (result.width <= 0 || result.height <= 0) null else result
}

class CameraData(
    val camPreviewSize : WidthAndHeightWithOrientation,
    val camPreviewFormat : Int,
    val cameraIndex : Int,
    val cameraInfo : Camera.CameraInfo,
    val camera : Camera,
    val displayToCameraAngle : RightAngleRotation,
    val screenResolution : WidthAndHeightWithOrientation,
    val mmToPx:Float,
    val autoFocusSupported : Boolean) : Closeable {

    private var _isOpen = true
    private var _askedForAutoFocus = false

    fun startPreviewInto(intoSurface:SurfaceTexture) {
        camera.setPreviewTexture(intoSurface)
        camera.startPreview()
    }

    override fun close() {
        Timber.d("close() _isOpen=$_isOpen")

        if (!_isOpen) {
            return
        }

        camera.stopPreview()
        camera.release()
        _isOpen = false
    }

    /**
     * @return true if success
     */
    fun requestAutoFocus(camSurfaceView: TextureView, ev: MotionEvent, onSuccess:()->Unit) : Boolean {
        if (!autoFocusSupported) {
            Timber.e("autoFocus is not supported in instance. Did you request it?")
            return false
        }

        val focusedRect = transformTouchInRectIntoRect(
            Pair(ev.x, ev.y),
            Pair(camSurfaceView.width, camSurfaceView.height),
            cameraAreaBounds,
            25)
        val focusedArea = Camera.Area(focusedRect, 1000/*max weight*/)

        Timber.d("touch-to-focus preview at (${ev.x}; ${ev.y}) as rect=${focusedRect}")

        if (_askedForAutoFocus) {
            camera.cancelAutoFocus()
            _askedForAutoFocus = false
        }

        val params = camera.parameters
        params.focusAreas = listOf(focusedArea)
        params.meteringAreas = listOf(focusedArea)
        camera.parameters = params //this is actually applying parameters

        _askedForAutoFocus = true
        camera.autoFocus { success:Boolean, _:Camera ->
            _askedForAutoFocus = false
            Timber.d("got autofocus?=$success")

            if (success) {
                onSuccess()
            }
        }
        return true
    }
}

val cameraAreaBounds = Rect(-1000, -1000, 1000, 1000) //according to https://developer.android.com/reference/kotlin/android/hardware/Camera.Area

fun transformTouchInRectIntoRect(touchAt:Pair<Float,Float>, within:Pair<Int,Int>, into: Rect, fingerRadius:Int) : Rect {
    val percx = touchAt.first / within.first
    val percy = touchAt.second / within.second

    val intow = into.left.absoluteValue + into.right
    val intoh = into.top.absoluteValue + into.bottom

    return Rect(
        Math.max(into.left, ((percx * intow) - intow/2 - fingerRadius).toInt()), //left
        Math.max(into.top, ((percy * intoh) - intoh/2 - fingerRadius).toInt()), //top
        Math.min(into.right, ((percx * intow) - intow/2 + fingerRadius).toInt()), //right
        Math.min(into.bottom, ((percy * intoh) - intoh/2 + fingerRadius).toInt()) //bottom
    )
}

fun CameraData.setTorchState(enabled : Boolean) {
    val params = this.camera.parameters
    params.flashMode = if (enabled) Camera.Parameters.FLASH_MODE_TORCH else Camera.Parameters.FLASH_MODE_OFF
    this.camera.parameters = params //needed to trigger changes
}

fun initializeFirstMatchingCamera(act: AppCompatActivity, hasAnyOfFocusMode:List<String>, condition : (Camera.CameraInfo) -> Boolean) : Result<CameraData, String> {
    val cameras =
        (0 until Camera.getNumberOfCameras())
            .map {
                val ci = Camera.CameraInfo()
                Camera.getCameraInfo(it, ci)
                Pair(it, ci)
            }

    val cameraIndexAndInfo =
        cameras.firstOrNull { condition(it.second) }
        ?: return Result.Error("No matching camera found")

    val cameraIndex = cameraIndexAndInfo.first
    val cameraInfo = cameraIndexAndInfo.second

    Timber.d("will use camera index=$cameraIndex")

    val camera = Camera.open(cameraIndex)
    val params = camera.parameters
    val maybeFocusMode = params.supportedFocusModes
        .filter { hasAnyOfFocusMode.contains(it) }
        .firstOrNull()

    if (maybeFocusMode != null) {
        params.focusMode = maybeFocusMode
        Timber.d("camera supports requested focus mode=${maybeFocusMode}")

        if (maybeFocusMode == Camera.Parameters.FOCUS_MODE_AUTO && params.maxNumFocusAreas < 1) {
            return Result.Error("Camera in FOCUS_MODE_AUTO doesn't have at least one focus area")
        }
    } else {
        //emulator doesn't support any focus mode
        if (!App.Instance.permitNoContinousFocusInCamera) return Result.Error("Camera doesn't support requested focus mode")
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

    val actBar = act.supportActionBar
    val actBarHeight = if (actBar == null) 0 else (if (actBar.isShowing) actBar.height else 0)
    val screenRes = WidthAndHeight(display.width, display.height - actBarHeight)

    val previewFormat = camera.parameters.previewFormat
    Timber.d("screen size (${screenRes.width}; ${screenRes.height - actBarHeight} = ${screenRes.height} - $actBarHeight) orientation=$screenAngle previewFrmat=$previewFormat")

    val naturalToCameraAngle = cameraInfo.orientation
    var cameraAngle = when(cameraInfo.orientation) {
        0 -> RightAngleRotation.RotateBy0
        90 -> RightAngleRotation.RotateBy90
        180 -> RightAngleRotation.RotateBy180
        270 -> RightAngleRotation.RotateBy270
        else -> null
    } ?: return Result.Error("android.view.Display.rotation has unsupported value ${display.rotation}")

    val displayToCameraAngle = (360 + naturalToCameraAngle - naturalToDisplayAngle) % 360
    val displayToCameraRightAngle = angleToRightAngle(displayToCameraAngle)

    Timber.d("camera displayToCameraAngle=$displayToCameraAngle displayToCameraRightAngle=$displayToCameraRightAngle because camera.orientation is ${cameraInfo.orientation} and display.rotation is ${display.rotation}")

    if (displayToCameraRightAngle == null) {
        return Result.Error("displayToCameraRightAngle is null")
    }

    camera.setDisplayOrientation(displayToCameraAngle)

    return Result.Ok(CameraData(
        WidthAndHeightWithOrientation(camPreviewSize.width, camPreviewSize.height, cameraAngle),
        previewFormat,
        cameraIndex,
        cameraInfo,
        camera,
        displayToCameraRightAngle,
        WidthAndHeightWithOrientation(screenRes.width, screenRes.height, screenAngle),
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_MM,
            1.0f,
            act.resources.displayMetrics
        ),
        maybeFocusMode == Camera.Parameters.FOCUS_MODE_AUTO
    ))
}

val continuousFocusModes = listOf(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE, Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)

fun initializeFirstBackFacingCameraWithContinuousFocus(act: AppCompatActivity) : Result<CameraData, String> =
    initializeFirstMatchingCamera(act, continuousFocusModes, {it.facing == backFacingCamera })

fun initializeFirstBackFacingCameraWithTouchToFocus(act: AppCompatActivity) : Result<CameraData, String> =
    initializeFirstMatchingCamera(act, listOf(Camera.Parameters.FOCUS_MODE_AUTO), {it.facing == backFacingCamera })
