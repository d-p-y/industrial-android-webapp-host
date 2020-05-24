package pl.todoit.IndustrialWebViewWithQr.model.extensions

import android.media.SoundPool
import timber.log.Timber
import java.io.Closeable

fun SoundPool.asClosable() : Closeable = Closeable { this.release() }
fun SoundPool.playOnce(sndId : Int) {
    val zeroIfFailed = this.play(sndId, 1f, 1f, 0, 0, 1f)

    if (zeroIfFailed == 0) {
        Timber.e("failed to play sound")
    }
}
