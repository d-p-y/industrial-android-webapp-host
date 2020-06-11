package pl.todoit.industrialAndroidWebAppHost.model.extensions

import java.util.*

fun <T : Enum<T>> Array<T>.toEnumSet(clazz:Class<T>) : EnumSet<T> {
    val decodeFormats = EnumSet.noneOf(clazz)
    decodeFormats.addAll(this.asIterable())
    return decodeFormats
}
