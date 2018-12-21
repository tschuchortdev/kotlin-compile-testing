package com.tschuchort.compiletest

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import okio.Buffer
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.io.OutputStream
import java.io.PrintStream

class StreamUtilTests {

    @Test
    fun `TeeOutputStream prints to all streams`() {
        val buf1 = Buffer()
        val buf2 = Buffer()

        val s = "test test \ntest\n"

        PrintStream(TeeOutputStream(PrintStream(buf1.outputStream()), buf2.outputStream())).print(s)

        Assertions.assertThat(buf1.readUtf8()).isEqualTo(s)
        Assertions.assertThat(buf2.readUtf8()).isEqualTo(s)
    }

    @Test
    fun `TeeOutPutStream flushes all streams`() {
        val str1 = mock<OutputStream>()
        val str2 = mock<OutputStream>()

        TeeOutputStream(str1, str2).flush()

        verify(str1).flush()
        verify(str2).flush()
    }

    @Test
    fun `TeeOutPutStream closes all streams`() {
        val str1 = mock<OutputStream>()
        val str2 = mock<OutputStream>()

        TeeOutputStream(str1, str2).close()

        verify(str1).close()
        verify(str2).close()
    }
}