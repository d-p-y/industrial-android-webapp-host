package pl.todoit.industrialAndroidWebAppHost.model.extensions

import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.receiveOrNull

suspend fun <T> ReceiveChannel<T>.receiveExactly(count: Int) = (0 until count).map { this.receive() }

/**
 * @return suspends and returns at least one element
 */
suspend fun <T : Any> ReceiveChannel<T>.receiveAllPending() : MutableList<T> {
    val result = mutableListOf<T>()

    val fstValue = this.receiveOrNull() ?: return result

    result.add(fstValue)

    while (!this.isEmpty && !this.isClosedForReceive) {
        val value = this.receiveOrNull() ?: break

        result.add(value)
    }

    return result
}
