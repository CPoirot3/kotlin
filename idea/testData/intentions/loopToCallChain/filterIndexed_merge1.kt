// WITH_RUNTIME
// INTENTION_TEXT: "Replace with 'filterIndexed{}.firstOrNull()'"
fun foo(list: List<String>): String? {
    <caret>for ((index, s) in list.withIndex()) {
        if (s.length > index * 10) continue
        if (s.length > index) {
            return s
        }
    }
    return null
}