package com.tschuchort.compiletesting

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JavacUtilsTest {

    @Test
    fun `Old version scheme less than 9 is parsed correctly`() {
        assertFalse(isJavac9OrLater("1.8.0"))
    }

    @Test
    fun `Old version scheme greater equal 9 is parsed correctly`() {
        assertTrue(isJavac9OrLater("1.9.1"))
        assertTrue(isJavac9OrLater("1.11.0"))
    }

    @Test
    fun `New version scheme less than 9 is parsed correctly`() {
        assertFalse(isJavac9OrLater("8.1.0.1"))
        assertFalse(isJavac9OrLater("8.1.0"))
        assertFalse(isJavac9OrLater("8.1"))
        assertFalse(isJavac9OrLater("8"))
    }

    @Test
    fun `New version scheme greater equal 9 is parsed correctly`() {
        assertTrue(isJavac9OrLater("9.0.0.1"))
        assertTrue(isJavac9OrLater("9.0.0"))
        assertTrue(isJavac9OrLater("9.1.0"))
        assertTrue(isJavac9OrLater("9.1"))
        assertTrue(isJavac9OrLater("9"))
        assertTrue(isJavac9OrLater("12"))
    }

    @Test
    fun `Old version scheme with extra info is parsed correctly`() {
        assertTrue(isJavac9OrLater("1.11.0-bla"))
    }
}