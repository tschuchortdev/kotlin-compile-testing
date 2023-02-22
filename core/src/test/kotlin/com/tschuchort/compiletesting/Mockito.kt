package com.tschuchort.compiletesting

import org.mockito.AdditionalMatchers
import org.mockito.kotlin.internal.createInstance

class MockitoAdditionalMatchersKotlin {
    companion object {
        inline fun <reified T : Any> not(matcher: T): T {
            return AdditionalMatchers.not(matcher) ?: createInstance()
        }

        inline fun <reified T : Any> or(left: T, right: T): T {
            return AdditionalMatchers.or(left, right) ?: createInstance()
        }

        inline fun <reified T : Any> and(left: T, right: T): T {
            return AdditionalMatchers.and(left, right) ?: createInstance()
        }

        inline fun <reified T : Comparable<T>> geq(value: T): T {
            return AdditionalMatchers.geq(value) ?: createInstance()
        }

        inline fun <reified T : Comparable<T>> leq(value: T): T {
            return AdditionalMatchers.leq(value) ?: createInstance()
        }

        inline fun <reified T : Comparable<T>> gt(value: T): T {
            return AdditionalMatchers.gt(value) ?: createInstance()
        }

        inline fun <reified T : Comparable<T>> lt(value: T): T {
            return AdditionalMatchers.lt(value) ?: createInstance()
        }

        inline fun <reified T : Comparable<T>> cmpEq(value: T): T {
            return AdditionalMatchers.cmpEq(value) ?: createInstance()
        }

        fun find(regex: Regex): String {
            return AdditionalMatchers.find(regex.pattern) ?: createInstance()
        }

        fun eq(value: Float, delta: Float): Float {
            return AdditionalMatchers.eq(value, delta)
        }

        fun eq(value: Double, delta: Double): Double {
            return AdditionalMatchers.eq(value, delta)
        }
    }
}