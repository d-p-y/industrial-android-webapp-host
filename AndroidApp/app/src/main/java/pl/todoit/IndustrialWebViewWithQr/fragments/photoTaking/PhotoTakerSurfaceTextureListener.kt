package pl.todoit.IndustrialWebViewWithQr.fragments.photoTaking

import android.graphics.SurfaceTexture
import android.view.TextureView
import android.view.View
import pl.todoit.IndustrialWebViewWithQr.App
import pl.todoit.IndustrialWebViewWithQr.model.CameraData
import timber.log.Timber

class PhotoTakerSurfaceTextureListener(
    private val _camera : CameraData,
    private val viewsToActivateLater : Array<View>
) : TextureView.SurfaceTextureListener {
    var _stopped = false
    var _started = false

    init {
        viewsToActivateLater.forEach { it.visibility = View.GONE }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture?, width: Int, height: Int) {
        Timber.d("onSurfaceTextureSizeChanged() hasValue?=${surface != null}")
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture?) {
        //Timber.d("onSurfaceTextureUpdated() hasValue?=${surface != null}")

        if (!_started) {
            //to avoid weird "buttons visible yet still no preview for several milliseconds"
            _started = true
            viewsToActivateLater.forEach { it.visibility = View.VISIBLE }
        }
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture?): Boolean {
        Timber.d("onSurfaceTextureDestroyed() stopped?=$_stopped")

        return true //meaning: complies with request - no need to manually release
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture?, width: Int, height: Int) {
        Timber.d("onSurfaceTextureAvailable() holder hasValue?=${surface != null}")

        if (surface == null) {
            return
        }

        _camera.startPreviewInto(surface)
    }
}
