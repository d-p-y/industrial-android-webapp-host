@file:Suppress("DEPRECATION")

package pl.todoit.IndustrialWebViewWithQr.fragments.barcodeDecoding

import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.view.TextureView
import pl.todoit.IndustrialWebViewWithQr.model.CameraData
import timber.log.Timber

class BarcodeDecoderSurfaceTextureListener(
    private val _camera : CameraData,
    private val _camPrev : BarcodeDecoderForCameraPreview
) : TextureView.SurfaceTextureListener {

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
        Timber.d("onSurfaceTextureSizeChanged() hasValue?=${surface != null}")

    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
        //Timber.d("onSurfaceTextureUpdated() hasValue?=${surface != null}")

    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
        Timber.d("onSurfaceTextureDestroyed()")
        _camera.camera.stopPreview()
        _camPrev.cancelDecoder()
        _camera.camera.release()

        return true //meaning: complies with request - no need to manually release
    }

    private fun onAutoFocusMoving(startMoving: Boolean) {
        _camPrev.setHasFocus(!startMoving)
        _camera.camera.setAutoFocusMoveCallback { start, _ -> onAutoFocusMoving(start) }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
        Timber.d("onSurfaceTextureAvailable() holder hasValue?=${surface != null}")

        if (surface == null) {
            return
        }

        _camera.camera.setPreviewTexture(surface)
        _camera.camera.startPreview()
        _camPrev.requestCameraFrameCapture()

        _camera.camera.setAutoFocusMoveCallback{ start, _ -> onAutoFocusMoving(start) }
    }
}