package com.tschuchort.compiletesting

import junit.framework.TestCase.assertEquals
import org.junit.Test

class EncodingUtilsTest {
    @Test
    fun encodePath_returnsSamePath_whenPathAlreadyValidEncoded() {
        // Given: A valid path
        val path = "file://users/johndoe/.gradle/something/somewhere/kotlin-compiler.jar"

        // When: Path is encoded
        val actualPath = encodePath(path)

        // Then: The same path should be returned
        assertEquals(path, actualPath)
    }

    @Test
    fun encodePath_returnsEncodedPath_whenPathIsNotAlreadyValidEncoded() {
        // Given: A path having whitespace in it
        val path = "file://users/john doe/.gradle/something/somewhere/kotlin-compiler.jar"

        // When: Path is encoded
        val actualPath = encodePath(path)

        // Then: The valid encoded path should be returned
        val expectedPath = "file://users/john%20doe/.gradle/something/somewhere/kotlin-compiler.jar"
        assertEquals(expectedPath, actualPath)
    }
}