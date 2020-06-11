@file:Suppress("DEPRECATION")

package pl.todoit.industrialAndroidWebAppHost.fragments.barcodeDecoding

import android.graphics.SurfaceTexture
import android.view.TextureView
import pl.todoit.industrialAndroidWebAppHost.model.CameraData
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

        _camera.startPreviewInto(surface)
        _camPrev.requestCameraFrameCapture()

        _camera.camera.setAutoFocusMoveCallback{ start, _ -> onAutoFocusMoving(start) }
    }
}
