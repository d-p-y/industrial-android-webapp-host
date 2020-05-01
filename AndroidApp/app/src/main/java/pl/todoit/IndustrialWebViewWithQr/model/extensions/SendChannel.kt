package pl.todoit.IndustrialWebViewWithQr.model.extensions

import kotlinx.coroutines.channels.SendChannel

suspend fun <T> SendChannel<T>.sendAndClose(msg:T) {
    this.send(msg)
    this.close()
}