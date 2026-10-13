package com.radustavila.myblesense.domain.testing

import kotlin.math.abs
import kotlin.reflect.KClass
import kotlin.test.fail

/**
 * Fluent assertions for multiplatform tests, on top of `kotlin.test`.
 *
 * The suites were written against Truth, which is JVM-only; this keeps the same reading order
 * (`assertThat(actual).isEqualTo(expected)`) so they run unchanged on the JVM and on iOS.
 */
fun <T> assertThat(actual: T): Subject<T> = Subject(actual)

class Subject<T>(private val actual: T) {

    fun isEqualTo(expected: Any?) {
        if (!sameValue(actual, expected)) fail("expected: <${describe(expected)}> but was: <${describe(actual)}>")
    }

    fun isNotEqualTo(unexpected: Any?) {
        if (sameValue(actual, unexpected)) fail("expected not to be: <${describe(unexpected)}>")
    }

    fun isTrue() {
        if (actual != true) fail("expected to be true but was: <$actual>")
    }

    fun isFalse() {
        if (actual != false) fail("expected to be false but was: <$actual>")
    }

    fun isNull() {
        if (actual != null) fail("expected null but was: <${describe(actual)}>")
    }

    fun isNotNull() {
        if (actual == null) fail("expected a value but was null")
    }

    fun isInstanceOf(type: KClass<*>) {
        if (!type.isInstance(actual)) {
            fail("expected an instance of ${type.simpleName} but was: <${describe(actual)}>")
        }
    }

    fun isEmpty() {
        val size = sizeOf(actual)
        if (size != 0) fail("expected to be empty but was: <${describe(actual)}>")
    }

    fun isNotEmpty() {
        if (sizeOf(actual) == 0) fail("expected not to be empty")
    }

    fun hasSize(expected: Int) {
        val size = sizeOf(actual)
        if (size != expected) fail("expected size <$expected> but was <$size>: <${describe(actual)}>")
    }

    fun contains(element: Any?) {
        val found = when (val value = actual) {
            is CharSequence -> element is CharSequence && value.contains(element)
            else -> elementsOf(value).any { sameValue(it, element) }
        }
        if (!found) fail("expected <${describe(actual)}> to contain <${describe(element)}>")
    }

    fun doesNotContain(element: Any?) {
        val found = when (val value = actual) {
            is CharSequence -> element is CharSequence && value.contains(element)
            else -> elementsOf(value).any { sameValue(it, element) }
        }
        if (found) fail("expected <${describe(actual)}> not to contain <${describe(element)}>")
    }

    /** Checks the elements regardless of order; chain [Ordered.inOrder] to also check the order. */
    fun containsExactly(vararg expected: Any?): Ordered {
        val actualElements = elementsOf(actual)
        val remaining = actualElements.toMutableList()
        val missing = expected.filterNot { wanted ->
            val index = remaining.indexOfFirst { sameValue(it, wanted) }
            if (index >= 0) remaining.removeAt(index)
            index >= 0
        }
        if (missing.isNotEmpty() || remaining.isNotEmpty()) {
            fail(
                "expected exactly <${describe(expected.toList())}> but was <${describe(actualElements)}>" +
                    " (missing: ${describe(missing)}, unexpected: ${describe(remaining)})",
            )
        }
        return Ordered {
            val inOrder = actualElements.indices.all { sameValue(actualElements[it], expected[it]) }
            if (!inOrder) {
                fail("expected in order <${describe(expected.toList())}> but was <${describe(actualElements)}>")
            }
        }
    }

    fun isWithin(tolerance: Number): TolerantComparison = TolerantComparison { expected ->
        val value = (actual as? Number)?.toDouble() ?: fail("expected a number but was: <$actual>")
        if (abs(value - expected.toDouble()) > tolerance.toDouble()) {
            fail("expected <$expected> within <$tolerance> but was: <$value>")
        }
    }
}

fun interface Ordered {
    fun inOrder()
}

fun interface TolerantComparison {
    fun of(expected: Number)
}

private fun sameValue(a: Any?, b: Any?): Boolean = when {
    a is ByteArray && b is ByteArray -> a.contentEquals(b)
    a is Array<*> && b is Array<*> -> a.contentDeepEquals(b)
    a.isIntegral() && b.isIntegral() -> (a as Number).toLong() == (b as Number).toLong()
    else -> a == b
}

private fun Any?.isIntegral(): Boolean = this is Byte || this is Short || this is Int || this is Long

private fun elementsOf(value: Any?): List<Any?> = when (value) {
    null -> fail("expected a collection but was null")
    is Iterable<*> -> value.toList()
    is ByteArray -> value.toList()
    is Array<*> -> value.toList()
    is Map<*, *> -> value.entries.toList()
    else -> fail("expected a collection but was: <$value>")
}

private fun sizeOf(value: Any?): Int = when (value) {
    is CharSequence -> value.length
    is Map<*, *> -> value.size
    else -> elementsOf(value).size
}

private fun describe(value: Any?): String = when (value) {
    is ByteArray -> value.contentToString()
    is Array<*> -> value.contentDeepToString()
    else -> value.toString()
}

/** Stands in for a storage failure in tests; `java.io.IOException` is not available on iOS. */
class TestIoException(message: String? = null) : Exception(message)
