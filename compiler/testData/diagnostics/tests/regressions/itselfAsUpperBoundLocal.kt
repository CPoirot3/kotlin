fun bar() {
    fun <<!CYCLIC_GENERIC_UPPER_BOUND!>T: T?<!>> foo() {}
    <!TYPE_INFERENCE_NO_INFORMATION_FOR_PARAMETER!>foo<!>()
}
