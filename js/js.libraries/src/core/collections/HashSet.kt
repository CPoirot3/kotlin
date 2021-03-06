/*
 * Copyright 2010-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Based on GWT HashSet
 * Copyright 2008 Google Inc.
 */

package kotlin.collections


open class HashSet<E> : AbstractSet<E> {

    private val map: HashMap<E, Any>

    constructor() {
        map = HashMap<E, Any>()
    }

    constructor(c: Collection<E>) {
        map = HashMap<E, Any>(c.size)
        addAll(c)
    }

    constructor(initialCapacity: Int, loadFactor: Float = 0.0f) {
        map = HashMap<E, Any>(initialCapacity, loadFactor)
    }

    /**
     * Protected constructor to specify the underlying map. This is used by
     * LinkedHashSet.

     * @param map underlying map to use.
     */
    internal constructor(map: HashMap<E, Any>) {
        this.map = map
    }

    override fun add(element: E): Boolean {
        val old = map.put(element, this)
        return old == null
    }

    override fun clear() {
        map.clear()
    }

//    public override fun clone(): Any {
//        return HashSet<E>(this)
//    }

    override operator fun contains(element: E): Boolean = map.containsKey(element)

    override fun isEmpty(): Boolean = map.isEmpty()

    override fun iterator(): MutableIterator<E> = map.keys.iterator()

    override fun remove(element: E): Boolean = map.remove(element) != null

    override val size: Int get() = map.size

}

public fun stringSetOf(vararg elements: String): HashSet<String> {
    return HashSet(stringMapOf<Any>()).apply { addAll(elements) }
}
