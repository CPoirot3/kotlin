// WITH_RUNTIME
import java.util.*

fun foo(): List<Int> {
    val result = ArrayList<Int>()
    <caret>for (i in 1..10) {
        if (i % 3 == 0) {
            result.add(i)
        }
    }
    return result
}
