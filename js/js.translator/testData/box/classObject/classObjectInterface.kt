package foo

interface I {
    fun foo(): String
}

class C() {
    companion object : I {
        override fun foo() = "OK"
    }
}

private fun useInterface(instance: I) = instance.foo()

fun box() = useInterface(C)
