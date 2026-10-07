package com.excavplayer.player.chapters

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

interface SeekableSource : Closeable {
    val size: Long
    var position: Long
    fun read(buffer: ByteArray, offset: Int = 0, length: Int = buffer.size): Int
    fun skip(bytes: Long) {
        position = (position + bytes).coerceAtMost(size)
    }
}

class ByteArraySeekableSource(private val data: ByteArray) : SeekableSource {
    override val size: Long = data.size.toLong()
    override var position: Long = 0L

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (position >= size) return -1
        val available = (size - position).toInt().coerceAtMost(length)
        System.arraycopy(data, position.toInt(), buffer, offset, available)
        position += available
        return available
    }

    override fun close() {}
}

class FileChannelSeekableSource(private val channel: FileChannel) : SeekableSource {
    override val size: Long get() = channel.size()
    override var position: Long
        get() = channel.position()
        set(value) { channel.position(value) }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val byteBuffer = ByteBuffer.wrap(buffer, offset, length)
        return channel.read(byteBuffer)
    }

    override fun close() {
        channel.close()
    }
}
