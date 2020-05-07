package pl.todoit.IndustrialWebViewWithQr.model.extensions

import kotlinx.coroutines.channels.Channel
import java.io.Closeable

fun <T> Channel<T>.toCloseable() = Closeable { this@toCloseable.close() }
