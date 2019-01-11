package com.tschuchort.compiletesting

import java.io.*


/** An output stream that does nothing, like /dev/null */
internal object NullStream : OutputStream() {
    override fun write(b: Int) {
        //NoOp
    }

    override fun close() {
        //NoOp
    }

    override fun flush() {
        //NoOp
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        //NoOp
    }

    override fun write(b: ByteArray) {
        //NoOp
    }
}

/** A combined stream that writes to all the output streams in [streams]. */
@Suppress("MemberVisibilityCanBePrivate")
internal class TeeOutputStream(val streams: Collection<OutputStream>) : OutputStream() {

    constructor(vararg streams: OutputStream) : this(streams.toList())

    @Synchronized
    @Throws(IOException::class)
    override fun write(b: Int) {
        for(stream in streams)
            stream.write(b)
    }

    @Synchronized
    @Throws(IOException::class)
    override fun write(b: ByteArray) {
        for(stream in streams)
            stream.write(b)
    }

    @Synchronized
    @Throws(IOException::class)
    override fun write(b: ByteArray, off: Int, len: Int) {
        for(stream in streams)
            stream.write(b, off, len)
    }

    @Throws(IOException::class)
    override fun flush() {
        for(stream in streams)
            stream.flush()
    }

    @Throws(IOException::class)
    override fun close() {
        closeImpl(streams)
    }

    @Throws(IOException::class)
    private fun closeImpl(streamsToClose : Collection<OutputStream>) {
        try {
            streamsToClose.firstOrNull()?.close()
        }
        finally {
            if(streamsToClose.size > 1)
                closeImpl(streamsToClose.drop(1))
        }
    }
}