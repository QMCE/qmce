package rj.qmce.lite.fix

import kotlin.collections.CharIterator
import kotlin.text.MatchResult
import kotlin.text.Regex

/**
 * Kotlin stdlib compatibility bridge for patched QQWatch bytecode.
 *
 * Some QQWatch classes were compiled against a Kotlin stdlib layout where they
 * directly call implementation classes such as StringsKt__IndentKt. In this app
 * those calls can fail with IllegalAccessError, so patched call sites can route
 * through these public static bridge methods instead.
 */
@Suppress("unused")
object KtFix {
    @JvmStatic
    fun checkRadix(radix: Int): Int {
        if (radix in Character.MIN_RADIX..Character.MAX_RADIX) return radix
        throw IllegalArgumentException("radix $radix was not in valid range ${Character.MIN_RADIX}..${Character.MAX_RADIX}")
    }

    /** Bridge target for patched `new-instance kotlin/text/Regex` call sites. */
    @JvmStatic
    fun newRegex(pattern: String): Regex = Regex(pattern)

    @JvmStatic
    fun `find$default`(regex: Regex, input: CharSequence, startIndex: Int, mask: Int, marker: Any?): MatchResult? {
        val actualStartIndex = if ((mask and 2) != 0) 0 else startIndex
        return regex.find(input, actualStartIndex)
    }

    @JvmStatic
    fun replace(regex: Regex, input: CharSequence, replacement: String): String = regex.replace(input, replacement)

    @JvmStatic
    fun split(regex: Regex, input: CharSequence, limit: Int): List<String> = regex.split(input, limit)

    @JvmStatic
    fun getValue(matchResult: MatchResult): String = matchResult.value

    @JvmStatic
    fun toLongOrNull(value: String): Long? = value.toLongOrNull()

    @JvmStatic
    fun trimIndent(value: String): String = value.trimIndent()

    @JvmStatic
    fun clear(value: StringBuilder): StringBuilder {
        value.setLength(0)
        return value
    }

    @JvmStatic
    fun toIntOrNull(value: String): Int? = value.toIntOrNull()

    @JvmStatic
    fun decodeToString(value: ByteArray): String = value.toString(Charsets.UTF_8)

    @JvmStatic
    fun `endsWith$default`(value: String, suffix: String, ignoreCase: Boolean, mask: Int, marker: Any?): Boolean {
        val actualIgnoreCase = if ((mask and 2) != 0) false else ignoreCase
        return value.endsWith(suffix, actualIgnoreCase)
    }

    @JvmStatic
    fun equals(a: String?, b: String?, ignoreCase: Boolean): Boolean = a.equals(b, ignoreCase)

    @JvmStatic
    fun `equals$default`(a: String?, b: String?, ignoreCase: Boolean, mask: Int, marker: Any?): Boolean {
        val actualIgnoreCase = if ((mask and 2) != 0) false else ignoreCase
        return a.equals(b, actualIgnoreCase)
    }

    @JvmStatic
    fun isBlank(value: CharSequence): Boolean = value.isBlank()

    @JvmStatic
    fun `replace$default`(
        value: String,
        oldValue: String,
        newValue: String,
        ignoreCase: Boolean,
        mask: Int,
        marker: Any?
    ): String {
        val actualIgnoreCase = if ((mask and 4) != 0) false else ignoreCase
        return value.replace(oldValue, newValue, actualIgnoreCase)
    }

    @JvmStatic
    fun `startsWith$default`(value: String, prefix: String, ignoreCase: Boolean, mask: Int, marker: Any?): Boolean {
        val actualIgnoreCase = if ((mask and 2) != 0) false else ignoreCase
        return value.startsWith(prefix, actualIgnoreCase)
    }

    @JvmStatic
    fun `contains$default`(value: CharSequence, other: Char, ignoreCase: Boolean, mask: Int, marker: Any?): Boolean {
        val actualIgnoreCase = if ((mask and 2) != 0) false else ignoreCase
        return value.contains(other, actualIgnoreCase)
    }

    @JvmStatic
    fun `contains$default`(value: CharSequence, other: CharSequence, ignoreCase: Boolean, mask: Int, marker: Any?): Boolean {
        val actualIgnoreCase = if ((mask and 2) != 0) false else ignoreCase
        return value.contains(other, actualIgnoreCase)
    }

    @JvmStatic
    fun `indexOf$default`(value: CharSequence, char: Char, startIndex: Int, ignoreCase: Boolean, mask: Int, marker: Any?): Int {
        val actualStartIndex = if ((mask and 2) != 0) 0 else startIndex
        val actualIgnoreCase = if ((mask and 4) != 0) false else ignoreCase
        return value.indexOf(char, actualStartIndex, actualIgnoreCase)
    }

    @JvmStatic
    fun `indexOf$default`(value: CharSequence, string: String, startIndex: Int, ignoreCase: Boolean, mask: Int, marker: Any?): Int {
        val actualStartIndex = if ((mask and 2) != 0) 0 else startIndex
        val actualIgnoreCase = if ((mask and 4) != 0) false else ignoreCase
        return value.indexOf(string, actualStartIndex, actualIgnoreCase)
    }

    @JvmStatic
    fun iterator(value: CharSequence): CharIterator = value.iterator()

    @JvmStatic
    fun `lastIndexOf$default`(value: CharSequence, char: Char, startIndex: Int, ignoreCase: Boolean, mask: Int, marker: Any?): Int {
        val actualStartIndex = if ((mask and 2) != 0) value.length - 1 else startIndex
        val actualIgnoreCase = if ((mask and 4) != 0) false else ignoreCase
        return value.lastIndexOf(char, actualStartIndex, actualIgnoreCase)
    }

    @JvmStatic
    fun `lastIndexOf$default`(value: CharSequence, string: String, startIndex: Int, ignoreCase: Boolean, mask: Int, marker: Any?): Int {
        val actualStartIndex = if ((mask and 2) != 0) value.length - 1 else startIndex
        val actualIgnoreCase = if ((mask and 4) != 0) false else ignoreCase
        return value.lastIndexOf(string, actualStartIndex, actualIgnoreCase)
    }

    @JvmStatic
    fun removePrefix(value: String, prefix: CharSequence): String = value.removePrefix(prefix)

    @JvmStatic
    fun removeSuffix(value: String, suffix: CharSequence): String = value.removeSuffix(suffix)

    @JvmStatic
    fun `split$default`(
        value: CharSequence,
        delimiters: CharArray,
        ignoreCase: Boolean,
        limit: Int,
        mask: Int,
        marker: Any?
    ): List<String> {
        val actualIgnoreCase = if ((mask and 2) != 0) false else ignoreCase
        val actualLimit = if ((mask and 4) != 0) 0 else limit
        return value.split(*delimiters, ignoreCase = actualIgnoreCase, limit = actualLimit)
    }

    @JvmStatic
    fun `split$default`(
        value: CharSequence,
        delimiters: Array<String>,
        ignoreCase: Boolean,
        limit: Int,
        mask: Int,
        marker: Any?
    ): List<String> {
        val actualIgnoreCase = if ((mask and 2) != 0) false else ignoreCase
        val actualLimit = if ((mask and 4) != 0) 0 else limit
        return value.split(*delimiters, ignoreCase = actualIgnoreCase, limit = actualLimit)
    }

    @JvmStatic
    fun `substringBeforeLast$default`(value: String, delimiter: Char, missingDelimiterValue: String, mask: Int, marker: Any?): String {
        val actualMissingDelimiterValue = if ((mask and 2) != 0) value else missingDelimiterValue
        return value.substringBeforeLast(delimiter, actualMissingDelimiterValue)
    }

    @JvmStatic
    fun trim(value: CharSequence): CharSequence = value.trim()

    @JvmStatic
    fun first(value: CharSequence): Char = value.first()

    @JvmStatic
    fun copyOfRange(array: ByteArray, fromIndex: Int, toIndex: Int): ByteArray = java.util.Arrays.copyOfRange(array, fromIndex, toIndex)

    @JvmStatic
    fun plus(array: Array<Any?>, other: Array<Any?>): Array<Any?> {
        val result = java.util.Arrays.copyOf(array, array.size + other.size)
        System.arraycopy(other, 0, result, array.size, other.size)
        return result
    }

    @JvmStatic
    fun contains(array: IntArray, element: Int): Boolean = array.contains(element)

    @JvmStatic
    fun contains(array: Array<Any?>, element: Any?): Boolean = array.contains(element)

    @JvmStatic
    fun filterNotNull(array: Array<Any?>): List<Any> {
        val out = ArrayList<Any>()
        for (e in array) if (e != null) out.add(e)
        return out
    }

    @JvmStatic
    fun first(array: Array<Any?>): Any? {
        if (array.isEmpty()) throw NoSuchElementException("Array is empty.")
        return array[0]
    }

    @JvmStatic
    fun getOrNull(array: Array<Any?>, index: Int): Any? = if (index in array.indices) array[index] else null

    @JvmStatic
    fun `joinToString$default`(
        array: IntArray,
        separator: CharSequence?,
        prefix: CharSequence?,
        postfix: CharSequence?,
        limit: Int,
        truncated: CharSequence?,
        transform: kotlin.jvm.functions.Function1<Int, CharSequence>?,
        mask: Int,
        marker: Any?
    ): String {
        val actualSeparator = if ((mask and 1) != 0) ", " else separator ?: "null"
        val actualPrefix = if ((mask and 2) != 0) "" else prefix ?: "null"
        val actualPostfix = if ((mask and 4) != 0) "" else postfix ?: "null"
        val actualLimit = if ((mask and 8) != 0) -1 else limit
        val actualTruncated = if ((mask and 16) != 0) "..." else truncated ?: "null"
        val actualTransform = if ((mask and 32) != 0) null else transform
        return joinArray(array.asIterable(), actualSeparator, actualPrefix, actualPostfix, actualLimit, actualTruncated, actualTransform)
    }

    @JvmStatic
    fun `joinToString$default`(
        array: Array<Any?>,
        separator: CharSequence?,
        prefix: CharSequence?,
        postfix: CharSequence?,
        limit: Int,
        truncated: CharSequence?,
        transform: kotlin.jvm.functions.Function1<Any?, CharSequence>?,
        mask: Int,
        marker: Any?
    ): String {
        val actualSeparator = if ((mask and 1) != 0) ", " else separator ?: "null"
        val actualPrefix = if ((mask and 2) != 0) "" else prefix ?: "null"
        val actualPostfix = if ((mask and 4) != 0) "" else postfix ?: "null"
        val actualLimit = if ((mask and 8) != 0) -1 else limit
        val actualTruncated = if ((mask and 16) != 0) "..." else truncated ?: "null"
        val actualTransform = if ((mask and 32) != 0) null else transform
        return joinArray(array.asIterable(), actualSeparator, actualPrefix, actualPostfix, actualLimit, actualTruncated, actualTransform)
    }

    @JvmStatic
    fun toCollection(array: Array<Any?>, destination: MutableCollection<Any?>): MutableCollection<Any?> {
        for (e in array) destination.add(e)
        return destination
    }

    @JvmStatic
    fun toList(array: Array<Any?>): List<Any?> = when (array.size) {
        0 -> emptyList()
        1 -> java.util.Collections.singletonList(array[0])
        else -> java.util.ArrayList<Any?>(java.util.Arrays.asList(*array))
    }

    @JvmStatic
    fun listOf(element: Any?): List<Any?> = java.util.Collections.singletonList(element)

    @JvmStatic
    fun arrayListOf(elements: Array<Any?>): ArrayList<Any?> = ArrayList(java.util.Arrays.asList(*elements))

    @JvmStatic
    fun emptyList(): List<Any?> = java.util.Collections.emptyList()

    @JvmStatic
    fun getLastIndex(list: List<Any?>): Int = list.size - 1

    @JvmStatic
    fun listOf(elements: Array<Any?>): List<Any?> = toList(elements)

    @JvmStatic
    fun mutableListOf(elements: Array<Any?>): MutableList<Any?> = ArrayList(java.util.Arrays.asList(*elements))

    @JvmStatic
    fun throwIndexOverflow(): Nothing = throw ArithmeticException("Index overflow has happened.")

    @JvmStatic
    fun collectionSizeOrDefault(iterable: Iterable<Any?>, default: Int): Int = if (iterable is Collection<*>) iterable.size else default

    @JvmStatic
    fun flatten(iterable: Iterable<Iterable<Any?>>): List<Any?> {
        val out = ArrayList<Any?>()
        for (inner in iterable) for (e in inner) out.add(e)
        return out
    }

    @JvmStatic
    fun sortWith(list: MutableList<Any?>, comparator: Comparator<in Any?>) {
        java.util.Collections.sort(list, comparator)
    }

    @JvmStatic
    fun addAll(collection: MutableCollection<Any?>, iterable: Iterable<Any?>): Boolean {
        var changed = false
        for (e in iterable) if (collection.add(e)) changed = true
        return changed
    }

    @JvmStatic
    fun addAll(collection: MutableCollection<Any?>, array: Array<Any?>): Boolean {
        var changed = false
        for (e in array) if (collection.add(e)) changed = true
        return changed
    }

    @JvmStatic
    fun reverse(list: MutableList<Any?>) = java.util.Collections.reverse(list)

    @JvmStatic
    fun distinct(iterable: Iterable<Any?>): List<Any?> = ArrayList(java.util.LinkedHashSet<Any?>(toMutableListFromIterable(iterable)))

    @JvmStatic
    fun filterNotNull(iterable: Iterable<Any?>): List<Any> {
        val out = ArrayList<Any>()
        for (e in iterable) if (e != null) out.add(e)
        return out
    }

    @JvmStatic
    fun first(iterable: Iterable<Any?>): Any? {
        if (iterable is List<*>) return first(iterable as List<Any?>)
        val it = iterable.iterator()
        if (!it.hasNext()) throw NoSuchElementException("Collection is empty.")
        return it.next()
    }

    @JvmStatic
    fun first(list: List<Any?>): Any? {
        if (list.isEmpty()) throw NoSuchElementException("List is empty.")
        return list[0]
    }

    @JvmStatic
    fun firstOrNull(list: List<Any?>): Any? = if (list.isEmpty()) null else list[0]

    @JvmStatic
    fun getOrNull(list: List<Any?>, index: Int): Any? = if (index >= 0 && index <= list.size - 1) list[index] else null

    @JvmStatic
    fun intersect(iterable: Iterable<Any?>, other: Iterable<Any?>): Set<Any?> {
        val set = java.util.LinkedHashSet<Any?>(toMutableListFromIterable(iterable))
        set.retainAll(toMutableSetFromIterable(other))
        return set
    }

    @JvmStatic
    fun `joinToString$default`(
        iterable: Iterable<Any?>,
        separator: CharSequence?,
        prefix: CharSequence?,
        postfix: CharSequence?,
        limit: Int,
        truncated: CharSequence?,
        transform: kotlin.jvm.functions.Function1<Any?, CharSequence>?,
        mask: Int,
        marker: Any?
    ): String {
        val actualSeparator = if ((mask and 1) != 0) ", " else separator ?: "null"
        val actualPrefix = if ((mask and 2) != 0) "" else prefix ?: "null"
        val actualPostfix = if ((mask and 4) != 0) "" else postfix ?: "null"
        val actualLimit = if ((mask and 8) != 0) -1 else limit
        val actualTruncated = if ((mask and 16) != 0) "..." else truncated ?: "null"
        val actualTransform = if ((mask and 32) != 0) null else transform
        return joinArray(iterable, actualSeparator, actualPrefix, actualPostfix, actualLimit, actualTruncated, actualTransform)
    }

    @JvmStatic
    fun last(list: List<Any?>): Any? {
        if (list.isEmpty()) throw NoSuchElementException("List is empty.")
        return list[list.size - 1]
    }

    @JvmStatic
    fun lastOrNull(list: List<Any?>): Any? = if (list.isEmpty()) null else list[list.size - 1]

    @JvmStatic
    fun minus(iterable: Iterable<Any?>, other: Iterable<Any?>): List<Any?> {
        val remove = toMutableSetFromIterable(other)
        val out = ArrayList<Any?>()
        for (e in iterable) if (!remove.contains(e)) out.add(e)
        return out
    }

    @JvmStatic
    fun plus(iterable: Iterable<Any?>, other: Iterable<Any?>): List<Any?> {
        val out = toMutableListFromIterable(iterable)
        for (e in other) out.add(e)
        return out
    }

    @JvmStatic
    fun plus(collection: Collection<Any?>, other: Iterable<Any?>): List<Any?> {
        val out = ArrayList<Any?>(collection.size + collectionSizeOrDefault(other, 10))
        out.addAll(collection)
        for (e in other) out.add(e)
        return out
    }

    @JvmStatic
    fun plus(collection: Collection<Any?>, element: Any?): List<Any?> {
        val out = ArrayList<Any?>(collection.size + 1)
        out.addAll(collection)
        out.add(element)
        return out
    }

    @JvmStatic
    fun random(collection: Collection<Any?>, random: kotlin.random.Random): Any? {
        if (collection.isEmpty()) throw NoSuchElementException("Collection is empty.")
        val index = random.nextInt(collection.size)
        if (collection is List<*>) return collection[index]
        var i = 0
        for (e in collection) if (i++ == index) return e
        throw NoSuchElementException("Collection is empty.")
    }

    @JvmStatic
    fun sortedWith(iterable: Iterable<Any?>, comparator: Comparator<in Any?>): List<Any?> {
        val out = toMutableListFromIterable(iterable)
        java.util.Collections.sort(out, comparator)
        return out
    }

    @JvmStatic
    fun take(iterable: Iterable<Any?>, n: Int): List<Any?> {
        if (n < 0) throw IllegalArgumentException("Requested element count $n is less than zero.")
        if (n == 0) return emptyList()
        val out = ArrayList<Any?>(n)
        var count = 0
        for (e in iterable) {
            out.add(e)
            count++
            if (count == n) break
        }
        return out
    }

    @JvmStatic
    fun toByteArray(collection: Collection<Byte>): ByteArray {
        val out = ByteArray(collection.size)
        var i = 0
        for (e in collection) out[i++] = e
        return out
    }

    @JvmStatic
    fun toFloatArray(collection: Collection<Float>): FloatArray {
        val out = FloatArray(collection.size)
        var i = 0
        for (e in collection) out[i++] = e
        return out
    }

    @JvmStatic
    fun toHashSet(iterable: Iterable<Any?>): HashSet<Any?> = HashSet(toMutableListFromIterable(iterable))

    @JvmStatic
    fun toIntArray(collection: Collection<Int>): IntArray {
        val out = IntArray(collection.size)
        var i = 0
        for (e in collection) out[i++] = e
        return out
    }

    @JvmStatic
    fun toList(iterable: Iterable<Any?>): List<Any?> = when (iterable) {
        is Collection<*> -> when (iterable.size) {
            0 -> emptyList()
            1 -> java.util.Collections.singletonList(iterable.iterator().next())
            else -> ArrayList(iterable as Collection<Any?>)
        }
        else -> toMutableListFromIterable(iterable).let { if (it.isEmpty()) emptyList() else it }
    }

    @JvmStatic
    fun toMutableList(collection: Collection<Any?>): MutableList<Any?> = ArrayList(collection)

    @JvmStatic
    fun toSet(iterable: Iterable<Any?>): Set<Any?> = when (iterable) {
        is Collection<*> -> when (iterable.size) {
            0 -> emptySet()
            1 -> java.util.Collections.singleton(iterable.iterator().next())
            else -> java.util.LinkedHashSet(iterable as Collection<Any?>)
        }
        else -> toMutableSetFromIterable(iterable)
    }

    @JvmStatic
    fun mapCapacity(expectedSize: Int): Int = when {
        expectedSize < 0 -> expectedSize
        expectedSize < 3 -> expectedSize + 1
        expectedSize < 1073741824 -> expectedSize + expectedSize / 3
        else -> Int.MAX_VALUE
    }

    @JvmStatic
    fun mapOf(pair: Pair<Any?, Any?>): Map<Any?, Any?> = java.util.Collections.singletonMap(pair.first, pair.second)

    @JvmStatic
    fun emptyMap(): Map<Any?, Any?> = java.util.Collections.emptyMap()

    @JvmStatic
    fun hashMapOf(pairs: Array<Pair<Any?, Any?>>): HashMap<Any?, Any?> {
        val map = HashMap<Any?, Any?>(mapCapacity(pairs.size))
        for (p in pairs) map[p.first] = p.second
        return map
    }

    @JvmStatic
    fun mapOf(pairs: Array<Pair<Any?, Any?>>): Map<Any?, Any?> {
        if (pairs.isEmpty()) return emptyMap()
        val map = java.util.LinkedHashMap<Any?, Any?>(mapCapacity(pairs.size))
        for (p in pairs) map[p.first] = p.second
        return map
    }

    @JvmStatic
    fun mutableMapOf(pairs: Array<Pair<Any?, Any?>>): MutableMap<Any?, Any?> {
        val map = java.util.LinkedHashMap<Any?, Any?>(mapCapacity(pairs.size))
        for (p in pairs) map[p.first] = p.second
        return map
    }

    @JvmStatic
    fun toMap(iterable: Iterable<Pair<Any?, Any?>>): Map<Any?, Any?> {
        val map = java.util.LinkedHashMap<Any?, Any?>()
        for (p in iterable) map[p.first] = p.second
        return if (map.isEmpty()) emptyMap() else map
    }

    @JvmStatic
    fun toMap(map: Map<Any?, Any?>): Map<Any?, Any?> = when (map.size) {
        0 -> emptyMap()
        1 -> java.util.Collections.singletonMap(map.entries.iterator().next().key, map.entries.iterator().next().value)
        else -> java.util.LinkedHashMap(map)
    }

    @JvmStatic
    fun toMutableMap(map: Map<Any?, Any?>): MutableMap<Any?, Any?> = java.util.LinkedHashMap(map)

    @JvmStatic
    fun emptySet(): Set<Any?> = java.util.Collections.emptySet()

    @JvmStatic
    fun hashSetOf(elements: Array<Any?>): HashSet<Any?> = HashSet(java.util.Arrays.asList(*elements))

    @JvmStatic
    fun mutableSetOf(elements: Array<Any?>): MutableSet<Any?> = java.util.LinkedHashSet(java.util.Arrays.asList(*elements))

    @JvmStatic
    fun setOf(elements: Array<Any?>): Set<Any?> = when (elements.size) {
        0 -> emptySet()
        1 -> java.util.Collections.singleton(elements[0])
        else -> java.util.LinkedHashSet(java.util.Arrays.asList(*elements))
    }

    @JvmStatic
    fun plus(set: Set<Any?>, elements: Array<Any?>): Set<Any?> {
        val out = java.util.LinkedHashSet<Any?>(mapCapacity(set.size + elements.size))
        out.addAll(set)
        for (e in elements) out.add(e)
        return out
    }

    private fun toMutableListFromIterable(iterable: Iterable<Any?>): ArrayList<Any?> {
        val out = ArrayList<Any?>()
        for (e in iterable) out.add(e)
        return out
    }

    private fun toMutableSetFromIterable(iterable: Iterable<Any?>): LinkedHashSet<Any?> {
        val out = LinkedHashSet<Any?>()
        for (e in iterable) out.add(e)
        return out
    }

    private fun <T> joinArray(
        iterable: Iterable<T>,
        separator: CharSequence,
        prefix: CharSequence,
        postfix: CharSequence,
        limit: Int,
        truncated: CharSequence,
        transform: kotlin.jvm.functions.Function1<T, CharSequence>?
    ): String {
        val sb = StringBuilder()
        sb.append(prefix)
        var count = 0
        for (element in iterable) {
            count++
            if (count > 1) sb.append(separator)
            if (limit < 0 || count <= limit) {
                val text = transform?.invoke(element) ?: element.toString()
                sb.append(text)
            } else {
                break
            }
        }
        if (limit >= 0 && count > limit) sb.append(truncated)
        sb.append(postfix)
        return sb.toString()
    }


    @JvmStatic
    fun stackTraceToString(throwable: Throwable): String {
        val sw = java.io.StringWriter()
        throwable.printStackTrace(java.io.PrintWriter(sw))
        return sw.toString()
    }

    @JvmStatic
    fun lazy(mode: LazyThreadSafetyMode, initializer: () -> Any?): Lazy<Any?> = kotlin.lazy(mode, initializer)

    @JvmStatic
    fun lazy(initializer: () -> Any?): Lazy<Any?> = kotlin.lazy(initializer)

    @JvmStatic
    fun compareValues(a: Comparable<Any?>?, b: Comparable<Any?>?): Int = when {
        a === b -> 0
        a == null -> -1
        b == null -> 1
        else -> a.compareTo(b)
    }

    @JvmStatic
    fun intercepted(continuation: kotlin.coroutines.Continuation<Any?>): kotlin.coroutines.Continuation<Any?> {
        val interceptor = continuation.context[kotlin.coroutines.ContinuationInterceptor]
        @Suppress("UNCHECKED_CAST")
        return interceptor?.interceptContinuation(continuation) as? kotlin.coroutines.Continuation<Any?> ?: continuation
    }

    @JvmStatic
    fun getCOROUTINE_SUSPENDED(): Any = kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED

    @JvmStatic
    fun getProgressionLastElement(start: Int, end: Int, step: Int): Int {
        fun mod(a: Int, b: Int): Int { val r = a % b; return if (r >= 0) r else r + b }
        fun differenceModulo(a: Int, b: Int, c: Int): Int = mod(mod(a, c) - mod(b, c), c)
        return if (step > 0) end - differenceModulo(end, start, step) else end + differenceModulo(start, end, -step)
    }

    @JvmStatic
    fun `copyTo$default`(input: java.io.InputStream, out: java.io.OutputStream, bufferSize: Int, mask: Int, marker: Any?): Long {
        val actualBufferSize = if ((mask and 2) != 0) DEFAULT_BUFFER_SIZE else bufferSize
        val buffer = ByteArray(actualBufferSize)
        var bytes = input.read(buffer)
        var total = 0L
        while (bytes >= 0) {
            out.write(buffer, 0, bytes)
            total += bytes.toLong()
            bytes = input.read(buffer)
        }
        return total
    }

    @JvmStatic
    fun closeFinally(closeable: java.io.Closeable?, cause: Throwable?) {
        if (closeable == null) return
        if (cause == null) {
            closeable.close()
        } else {
            try {
                closeable.close()
            } catch (closeException: Throwable) {
                cause.addSuppressed(closeException)
            }
        }
    }

    @JvmStatic
    fun `copyTo$default`(
        from: java.io.File,
        target: java.io.File,
        overwrite: Boolean,
        bufferSize: Int,
        mask: Int,
        marker: Any?
    ): java.io.File {
        val actualOverwrite = if ((mask and 2) != 0) false else overwrite
        val actualBufferSize = if ((mask and 4) != 0) DEFAULT_BUFFER_SIZE else bufferSize
        if (target.exists()) {
            if (!actualOverwrite) throw kotlin.io.FileAlreadyExistsException(from, target, "The destination file already exists.")
            if (!target.delete()) throw kotlin.io.FileAlreadyExistsException(from, target, "Tried to overwrite the destination, but failed to delete it.")
        }
        if (from.isDirectory) {
            if (!target.mkdirs()) throw kotlin.io.FileSystemException(from, target, "Failed to create target directory.")
        } else {
            target.parentFile?.mkdirs()
            java.io.FileInputStream(from).use { input ->
                java.io.FileOutputStream(target).use { output ->
                    `copyTo$default`(input, output, actualBufferSize, 0, null)
                }
            }
        }
        return target
    }

    @JvmStatic
    fun getExtension(file: java.io.File): String {
        val name = file.name
        val index = name.lastIndexOf('.')
        return if (index < 0) "" else name.substring(index + 1)
    }

    @JvmStatic
    fun readText(reader: java.io.Reader): String {
        val sw = java.io.StringWriter()
        val buffer = CharArray(DEFAULT_BUFFER_SIZE)
        var n = reader.read(buffer)
        while (n >= 0) {
            sw.write(buffer, 0, n)
            n = reader.read(buffer)
        }
        return sw.toString()
    }

    @JvmStatic
    fun roundToInt(value: Float): Int {
        if (value.isNaN()) throw IllegalArgumentException("Cannot round NaN value.")
        return Math.round(value)
    }

    @JvmStatic
    fun coerceAtLeast(value: Float, minimumValue: Float): Float = if (value < minimumValue) minimumValue else value

    @JvmStatic
    fun coerceAtLeast(value: Int, minimumValue: Int): Int = if (value < minimumValue) minimumValue else value

    @JvmStatic
    fun coerceAtMost(value: Float, maximumValue: Float): Float = if (value > maximumValue) maximumValue else value

    @JvmStatic
    fun coerceAtMost(value: Int, maximumValue: Int): Int = if (value > maximumValue) maximumValue else value

    @JvmStatic
    fun coerceAtMost(value: Long, maximumValue: Long): Long = if (value > maximumValue) maximumValue else value

    @JvmStatic
    fun coerceIn(value: Double, minimumValue: Double, maximumValue: Double): Double {
        if (minimumValue > maximumValue) throw IllegalArgumentException("Cannot coerce value to an empty range: maximum $maximumValue is less than minimum $minimumValue.")
        return when {
            value < minimumValue -> minimumValue
            value > maximumValue -> maximumValue
            else -> value
        }
    }

    @JvmStatic
    fun coerceIn(value: Long, minimumValue: Long, maximumValue: Long): Long {
        if (minimumValue > maximumValue) throw IllegalArgumentException("Cannot coerce value to an empty range: maximum $maximumValue is less than minimum $minimumValue.")
        return when {
            value < minimumValue -> minimumValue
            value > maximumValue -> maximumValue
            else -> value
        }
    }

    @JvmStatic
    fun contains(sequence: Sequence<Any?>, element: Any?): Boolean {
        for (e in sequence) if (e == element) return true
        return false
    }

    @JvmStatic
    fun count(sequence: Sequence<Any?>): Int {
        var count = 0
        for (ignored in sequence) {
            if (count == Int.MAX_VALUE) throw ArithmeticException("Count overflow has happened.")
            count++
        }
        return count
    }

    @JvmStatic
    fun first(sequence: Sequence<Any?>): Any? {
        val it = sequence.iterator()
        if (!it.hasNext()) throw NoSuchElementException("Sequence is empty.")
        return it.next()
    }

    @JvmStatic
    fun toList(sequence: Sequence<Any?>): List<Any?> {
        val out = ArrayList<Any?>()
        for (e in sequence) out.add(e)
        return when (out.size) {
            0 -> emptyList()
            1 -> java.util.Collections.singletonList(out[0])
            else -> out
        }
    }

}
