package com.tschuchort.compiletesting

import org.junit.Assert.*
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

    @Test
    fun `Standard javac -version output is parsed correctly`() {
        assertEquals("1.8.0_252", parseVersionString("javac 1.8.0_252"))
    }

    @Test
    fun `javac -version output with JAVA OPTIONS is parsed correctly`() {
        assertEquals(
            "1.8.0_222",
            parseVersionString(
                "Picked up _JAVA_OPTIONS: -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap javac 1.8.0_222"
            )
        )
    }

    @Test
    fun `Wrong javac -version output is returning null`() {
        assertNull(
            parseVersionString(
                "wrong javac"
            )
        )
    }
}