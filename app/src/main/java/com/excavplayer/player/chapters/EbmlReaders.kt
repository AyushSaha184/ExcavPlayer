package com.excavplayer.player.chapters

import java.nio.charset.StandardCharsets

/**
 * Common surface for reading Matroska/EBML elements, shared by both
 * extraction paths:
 * - [EbmlBufferReader] over an in-memory byte range (bounded scan of a stream)
 * - [EbmlSeekableReader] over a random-access [SeekableSource]
 *
 * The chapter parsers are written once against this interface.
 */
interface EbmlReader {
    /** Current read offset, in bytes from the start of the container. */
    var position: Long

    /** Offset one past the last readable byte (buffer size / source size). */
    val limit: Long

    fun hasRemaining(): Boolean

    /** Advances by [bytes]; unknown-size and negative values are guarded. */
    fun skip(bytes: Long)

    /** Reads an EBML variable-length Element ID (length bit preserved). */
    fun readElementId(): Long?

    /** Reads an EBML variable-length Data Size (length bit stripped, -1 = unknown). */
    fun readElementSize(): Long?

    /** Reads a big-endian unsigned integer of [size] bytes. */
    fun readUint(size: Long): Long?

    fun readUtf8String(size: Long): String
}

/** Reads an EBML variable-length-integer byte count from the leading byte. */
internal fun ebmlVintLength(firstByte: Int): Int? {
    if (firstByte == 0) return null
    return when {
        (firstByte and 0x80) != 0 -> 1
        (firstByte and 0x40) != 0 -> 2
        (firstByte and 0x20) != 0 -> 3
        (firstByte and 0x10) != 0 -> 4
        (firstByte and 0x08) != 0 -> 5
        (firstByte and 0x04) != 0 -> 6
        (firstByte and 0x02) != 0 -> 7
        (firstByte and 0x01) != 0 -> 8
        else -> null
    }
}

/** EBML reader over an in-memory byte range. */
class EbmlBufferReader(private val data: ByteArray) : EbmlReader {

    override val limit: Long = data.size.toLong()
    override var position: Long = 0L

    override fun hasRemaining(): Boolean = position < data.size

    override fun skip(bytes: Long) {
        position = (position + bytes).coerceIn(0L, limit)
    }

    override fun readElementId(): Long? {
        if (!hasRemaining()) return null
        val firstByte = data[position.toInt()].toInt() and 0xFF
        val numBytes = ebmlVintLength(firstByte) ?: return null
        if (position + numBytes > limit) return null

        var id = 0L
        for (i in 0 until numBytes) {
            id = (id shl 8) or (data[position.toInt() + i].toLong() and 0xFF)
        }
        position += numBytes
        return id
    }

    override fun readElementSize(): Long? {
        if (!hasRemaining()) return null
        val firstByte = data[position.toInt()].toInt() and 0xFF
        val numBytes = ebmlVintLength(firstByte) ?: return null
        if (position + numBytes > limit) return null

        val mask = (0xFF shr numBytes)
        var size = (firstByte and mask).toLong()
        for (i in 1 until numBytes) {
            size = (size shl 8) or (data[position.toInt() + i].toLong() and 0xFF)
        }
        position += numBytes

        val isUnknown = when (numBytes) {
            1 -> size == 0x7FL
            2 -> size == 0x3FFFL
            3 -> size == 0x1FFFFFL
            4 -> size == 0x0FFFFFFFL
            8 -> size == 0x00FFFFFFFFFFFFFFL
            else -> false
        }
        return if (isUnknown) -1L else size
    }

    override fun readUint(size: Long): Long? {
        if (size <= 0 || position + size > limit) return null
        var value = 0L
        for (i in 0 until size.toInt()) {
            value = (value shl 8) or (data[position.toInt() + i].toLong() and 0xFF)
        }
        position += size.toInt()
        return value
    }

    override fun readUtf8String(size: Long): String {
        if (size <= 0 || position + size > limit) return ""
        val str = String(data, position.toInt(), size.toInt(), StandardCharsets.UTF_8)
        position += size.toInt()
        return str
    }
}

/** Random-access EBML reader over a [SeekableSource]. */
class EbmlSeekableReader(private val source: SeekableSource) : EbmlReader {

    override val limit: Long = source.size

    override var position: Long
        get() = source.position
        set(value) { source.position = value }

    override fun hasRemaining(): Boolean = source.position < source.size

    override fun skip(bytes: Long) {
        source.skip(bytes)
    }

    override fun readElementId(): Long? {
        if (!hasRemaining()) return null
        val buf = ByteArray(1)
        if (source.read(buf, 0, 1) <= 0) return null
        val firstByte = buf[0].toInt() and 0xFF
        val numBytes = ebmlVintLength(firstByte) ?: return null
        if (source.position + numBytes - 1 > source.size) return null

        var id = firstByte.toLong()
        if (numBytes > 1) {
            val rest = ByteArray(numBytes - 1)
            val read = source.read(rest, 0, numBytes - 1)
            if (read != numBytes - 1) return null
            for (i in 0 until numBytes - 1) {
                id = (id shl 8) or (rest[i].toLong() and 0xFF)
            }
        }
        return id
    }

    override fun readElementSize(): Long? {
        if (!hasRemaining()) return null
        val buf = ByteArray(1)
        if (source.read(buf, 0, 1) <= 0) return null
        val firstByte = buf[0].toInt() and 0xFF
        val numBytes = ebmlVintLength(firstByte) ?: return null
        if (source.position + numBytes - 1 > source.size) return null

        val mask = (0xFF shr numBytes)
        var size = (firstByte and mask).toLong()
        if (numBytes > 1) {
            val rest = ByteArray(numBytes - 1)
            val read = source.read(rest, 0, numBytes - 1)
            if (read != numBytes - 1) return null
            for (i in 0 until numBytes - 1) {
                size = (size shl 8) or (rest[i].toLong() and 0xFF)
            }
        }

        val isUnknown = when (numBytes) {
            1 -> size == 0x7FL
            2 -> size == 0x3FFFL
            3 -> size == 0x1FFFFFL
            4 -> size == 0x0FFFFFFFL
            5 -> size == 0x07FFFFFFFFL
            6 -> size == 0x03FFFFFFFFFFL
            7 -> size == 0x01FFFFFFFFFFFFL
            8 -> size == 0x00FFFFFFFFFFFFFFL
            else -> false
        }
        return if (isUnknown) -1L else size
    }

    override fun readUint(size: Long): Long? {
        if (size <= 0 || size > 8 || source.position + size > source.size) return null
        val len = size.toInt()
        val buf = ByteArray(len)
        if (source.read(buf, 0, len) != len) return null
        var value = 0L
        for (i in 0 until len) {
            value = (value shl 8) or (buf[i].toLong() and 0xFF)
        }
        return value
    }

    override fun readUtf8String(size: Long): String {
        if (size <= 0 || source.position + size > source.size) return ""
        val len = size.coerceAtMost(64 * 1024).toInt()
        val buf = ByteArray(len)
        val read = source.read(buf, 0, len)
        if (size > len) {
            source.skip(size - len)
        }
        if (read <= 0) return ""
        return String(buf, 0, read, StandardCharsets.UTF_8)
    }
}
