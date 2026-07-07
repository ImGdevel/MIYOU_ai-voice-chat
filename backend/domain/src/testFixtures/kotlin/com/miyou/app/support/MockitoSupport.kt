package com.miyou.app.support

import org.mockito.ArgumentMatchers

@Suppress("UNCHECKED_CAST")
inline fun <reified T> anyValue(): T {
    ArgumentMatchers.any(T::class.java)
    return uninitialized()
}

fun anyIntValue(): Int {
    ArgumentMatchers.anyInt()
    return 0
}

fun anyLongValue(): Long {
    ArgumentMatchers.anyLong()
    return 0L
}

fun anyFloatValue(): Float {
    ArgumentMatchers.anyFloat()
    return 0.0f
}

fun anyStringValue(): String {
    ArgumentMatchers.anyString()
    return ""
}

fun <T> eqValue(value: T): T {
    ArgumentMatchers.eq(value)
    return value
}

@PublishedApi
@Suppress("UNCHECKED_CAST")
internal fun <T> uninitialized(): T = null as T
