package com.tschuchort.compiletesting

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Encode the given URI [path] with UTF_8 encoding
 */
fun encodePath(path: String): String {
    if (path.isBlank()) {
        return path
    }
    val bytes = path.encodeToByteArray()
    var alreadyEncoded = true
    for (b in bytes) {
        if (!isValidEncodedChar(b.toInt())) {
            alreadyEncoded = false
            break
        }
    }
    if (alreadyEncoded) {
        return path
    }

    val baos = ByteArrayOutputStream(bytes.size)
    for (b in bytes) {
        val byte = b.toInt()
        if (isValidEncodedChar(byte)) {
            baos.write(byte)
        } else {
            baos.write('%'.code)
            val hex1 = Character.forDigit(/* digit = */ b.toInt() shr 4 and 0xF, /* radix = */ 16).uppercaseChar()
            val hex2 = Character.forDigit(/* digit = */ b.toInt() and 0xF, /* radix = */ 16).uppercaseChar()
            baos.write(hex1.code)
            baos.write(hex2.code)
        }
    }
    return baos.toString(StandardCharsets.UTF_8.toString())
}

/**
 * Checks whether input char [c] is valid and already an encoded character or not.
 */
fun isValidEncodedChar(c: Int): Boolean {
    return isPchar(c) || '/'.code == c
}

/**
 * Indicates whether the given character is in the {@code pchar} set.
 * @see <a href="https://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
 */
fun isPchar(c: Int): Boolean {
    return (isUnreserved(c) || isSubDelimiter(c) || ':'.code == c || '@'.code == c)
}

/**
 * Indicates whether the given character is in the `sub-delims` set.
 * @see <a href="https://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
 */
fun isSubDelimiter(c: Int): Boolean {
    return '!'.code == c || '$'.code == c || '&'.code == c || '\''.code == c || '('.code == c || ')'.code == c || '*'.code == c || '+'.code == c || ','.code == c || ';'.code == c || '='.code == c
}

/**
 * Indicates whether the given character is in the `unreserved` set.
 * @see <a href="https://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
 */
fun isUnreserved(c: Int): Boolean {
    return isAlpha(c) || Character.isDigit(c) || '-'.code == c || '.'.code == c || '_'.code == c || '~'.code == c
}

/**
 * Indicates whether the given character is in the `ALPHA` set.
 * @see <a href="https://www.ietf.org/rfc/rfc3986.txt">RFC 3986, appendix A</a>
 */
fun isAlpha(c: Int): Boolean {
    return c >= 'a'.code && c <= 'z'.code || c >= 'A'.code && c <= 'Z'.code
}

