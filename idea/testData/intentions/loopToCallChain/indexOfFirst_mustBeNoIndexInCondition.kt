// WITH_RUNTIME
// IS_APPLICABLE: false
fun foo(list: List<String>) {
    var result = -1
    <caret>for ((index, s) in list.withIndex()) {
        if (s.length > index) {
            result = index
            break
        }
    }
}