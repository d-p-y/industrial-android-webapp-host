@file:Suppress("DEPRECATION")

package pl.todoit.IndustrialWebViewWithQr.fragments

import android.Manifest
import android.graphics.YuvImage
import android.media.SoundPool
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.exifinterface.media.ExifInterface
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import kotlinx.coroutines.channels.SendChannel
import pl.todoit.IndustrialWebViewWithQr.*
import pl.todoit.IndustrialWebViewWithQr.fragments.photoTaking.PhotoTakerSurfaceTextureListener
import pl.todoit.IndustrialWebViewWithQr.model.*
import pl.todoit.IndustrialWebViewWithQr.model.extensions.closeOnDestroy
import timber.log.Timber
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream

fun rightAngleToMaybeExifOrientation(angle:RightAngleRotation) =
    when(angle) {
        RightAngleRotation.RotateBy90 -> ExifInterface.ORIENTATION_ROTATE_90.toString()
        RightAngleRotation.RotateBy180 -> ExifInterface.ORIENTATION_ROTATE_180.toString()
        RightAngleRotation.RotateBy270 -> ExifInterface.ORIENTATION_ROTATE_270.toString()
        else -> null
    }

/**
 * @param imageFormat @must be android.graphics.ImageFormat.Format
 */
fun savePhotoToFile(size:WidthAndHeightWithOrientation, imageFormat: Int, data : ByteArray, photoOrientation:RightAngleRotation) : File {
    val jpegPath = App.Instance.buildJpegFilePath()

    val maybeExifOrientation = rightAngleToMaybeExifOrientation(photoOrientation)

    Timber.d("got photo at computed photoOrientation=$photoOrientation exifOrientation=$maybeExifOrientation and will save into $jpegPath")

    val resultPath = File(jpegPath)
    FileOutputStream(resultPath).use {
        YuvImage(
            data,
            imageFormat,
            size.widthWithoutRotation,
            size.heightWithoutRotation,
            null /*no strides*/)
            .compressToJpeg(
                size.toRect(),
                App.Instance.currentConnection.photoJpegQuality,
                it
            )
    }

    if (maybeExifOrientation != null) {
        ExifInterface(resultPath.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, maybeExifOrientation)
            saveAttributes()
        }
    }

    Timber.d("photo saved")
    return resultPath
}

class TakePhotoFragment : Fragment(), IProcessesBackButtonEvents, IRequiresPermissions,
                          IBeforeNavigationValidation, ITogglesToolbarVisibility {

    private lateinit var _camera: CameraData
    private var _orientation = RightAngleRotation.RotateBy0
    lateinit var req : SendChannel<File>
    private lateinit var _photoTaker : PhotoTakerSurfaceTextureListener
    private lateinit var _sensorListener : Closeable

    override fun getNeededAndroidManifestPermissions(): Array<String> = arrayOf(Manifest.permission.CAMERA)
    override fun onNeededPermissionRejected(rejectedPerms:List<String>) : PermissionRequestRejected {
        App.Instance.navigator.postNavigateTo(NavigationRequest.ScanQr_Back())
        return PermissionRequestRejected.MayNotOpenFragment
    }

    override fun isToolbarVisible() = false

    override suspend fun onBackPressedConsumed() : Boolean {
        Timber.d("canceling photo taking due to back button")
        req.close()
        App.Instance.navigator.navigateTo(NavigationRequest.TakePhoto_Back())
        return true
    }

    override suspend fun maybeGetBeforeNavigationError(act: AppCompatActivity) : String? =
        when(val result = initializeFirstBackFacingCameraWithTouchToFocus(act)) {
            is Result.Error -> result.error
            is Result.Ok -> {
                _camera = result.value
                null }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        lifecycle.closeOnDestroy(_camera)
        container?.setPadding(0, 0, 0, 0) //to reset former request (if there was any)
        val result = inflater.inflate(R.layout.fragment_take_photo, container, false)
        val act = activity

        if (act !is MainActivity) {
            Timber.e("not running withing main activity")
            return result
        }

        _sensorListener = act.createSensorListener { _orientation = it }
        lifecycle.closeOnDestroy(_sensorListener)

        val camSurfaceView = result.findViewById<TextureView>(R.id.camSurfaceView)
        val torchToggler = result.findViewById<ImageView>(R.id.torchToggler)
        val takePictureAction = result.findViewById<ImageView>(R.id.takePictureAction)

        var toggled = false
        setTorchStateEnabled(toggled, torchToggler)

        torchToggler.setOnClickListener {
            Timber.d("toggling flash")
            toggled = !toggled
            _camera.setTorchState(toggled)
            setTorchStateEnabled(toggled, torchToggler)
        }

        takePictureAction.setOnClickListener {
            Timber.d("taking picture")

            _camera.camera.setOneShotPreviewCallback { data, _ ->
                App.Instance.launchCoroutine {
                    if (App.Instance.playPictureTakenSound) {
                        act.playSound(SndItem.PictureTaken)
                    }

                    App.Instance.navigator.navigateTo(NavigationRequest.TakePhoto_Back())
                }

                App.Instance.launchParallelInBackground {
                    req.send(savePhotoToFile(
                        _camera.camPreviewSize,
                        _camera.camPreviewFormat,
                        data,
                        _orientation + _camera.displayToCameraAngle))
                }
            }
        }

        _photoTaker = PhotoTakerSurfaceTextureListener(_camera, arrayOf(torchToggler, takePictureAction))
        camSurfaceView.surfaceTextureListener = _photoTaker

        camSurfaceView.setOnTouchListener { _, ev ->
            onMaybeRequestAutoFocus(camSurfaceView, ev)
            true}

        return result
    }

    private fun onFocus() {
        //TODO draw some fancy image on focus/failure?
        //TODO play sound focus/failure?

        if (App.Instance.currentConnection.hapticFeedbackOnAutoFocused) {
            activity?.performHapticFeedback()
        }
    }

    private fun onMaybeRequestAutoFocus(camSurfaceView: TextureView?, ev: MotionEvent?) {
        if (camSurfaceView == null || ev?.action != MotionEvent.ACTION_DOWN) {
            return
        }

        _camera.requestAutoFocus(camSurfaceView, ev, ::onFocus)
    }
}
