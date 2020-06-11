package pl.todoit.industrialAndroidWebAppHost.model.extensions

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import java.io.Closeable

fun Lifecycle.addOnDestroyListener(callOnDestroy : ()->Unit) {
    addObserver(object : LifecycleObserver {
        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        fun onDestroy() =  callOnDestroy()
    })
}

fun Lifecycle.closeOnDestroy(vararg toClosesOnDestroy : Closeable) {
    addObserver(object : LifecycleObserver {
        @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        fun onDestroy() = toClosesOnDestroy.forEach { it.close() }
    })
}
