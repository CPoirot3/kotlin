// WITH_RUNTIME
fun foo(list: List<String>) {
    var result: String? = null
    <caret>for (s in list) {
        if (s.length > 0) {
            result = s.substring(0, s.length - 1)
            break
        }
    }
}

fun bar(s: String): String = s
