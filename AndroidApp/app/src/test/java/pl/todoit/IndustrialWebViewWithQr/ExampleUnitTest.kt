package pl.todoit.IndustrialWebViewWithQr

import org.junit.Test

import org.junit.Assert.*
import java.util.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */

enum class Foo {
    A,B,C
}

class ExampleUnitTest {
    @Test
    fun enumSetEqualsIsSane() {
        assertTrue(
            EnumSet.noneOf(Foo::class.java).also {
                it.add(Foo.B)
                it.add(Foo.C)
            }.equals(
                EnumSet.noneOf(Foo::class.java).also {
                    it.add(Foo.C)
                    it.add(Foo.B)
                })
            )
    }
}
