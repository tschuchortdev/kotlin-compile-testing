package com.tschuchort.compiletesting

import org.junit.Assert.*
import org.junit.Test

class JavacUtilsTest {

    @Test
    fun `Old version scheme less than 9 is parsed correctly`() {
        assertFalse(isJavac9OrLater("1.8"))
    }

    @Test
    fun `New version scheme less than 9 is parsed correctly`() {
        assertFalse(isJavac9OrLater("8"))
    }

    @Test
    fun `New version scheme greater equal 9 is parsed correctly`() {
        assertTrue(isJavac9OrLater("9"))
        assertTrue(isJavac9OrLater("12"))
    }
}