package pl.todoit.IndustrialWebViewWithQr.model.extensions

import kotlinx.coroutines.channels.ReceiveChannel

suspend fun <T> ReceiveChannel<T>.receiveExactly(count: Int) = (0 until count).map { this.receive() }
