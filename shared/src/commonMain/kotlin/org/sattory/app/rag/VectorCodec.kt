package org.sattory.app.rag

import kotlin.math.sqrt

object VectorCodec {
    fun encode(values: FloatArray): ByteArray {
        val bytes = ByteArray(values.size * Float.SIZE_BYTES)
        values.forEachIndexed { index, value ->
            val bits = value.toBits()
            val offset = index * Float.SIZE_BYTES
            bytes[offset] = bits.toByte()
            bytes[offset + 1] = (bits ushr 8).toByte()
            bytes[offset + 2] = (bits ushr 16).toByte()
            bytes[offset + 3] = (bits ushr 24).toByte()
        }
        return bytes
    }

    fun decode(bytes: ByteArray): FloatArray {
        require(bytes.size % Float.SIZE_BYTES == 0) { "Embedding invalido" }
        return FloatArray(bytes.size / Float.SIZE_BYTES) { index ->
            val offset = index * Float.SIZE_BYTES
            val bits = (bytes[offset].toInt() and 0xff) or
                ((bytes[offset + 1].toInt() and 0xff) shl 8) or
                ((bytes[offset + 2].toInt() and 0xff) shl 16) or
                ((bytes[offset + 3].toInt() and 0xff) shl 24)
            Float.fromBits(bits)
        }
    }

    fun normalized(values: FloatArray): FloatArray {
        val norm = sqrt(values.sumOf { (it * it).toDouble() }).toFloat()
        if (norm <= 0f) return values
        return FloatArray(values.size) { values[it] / norm }
    }

    fun cosine(left: FloatArray, right: FloatArray): Float {
        if (left.size != right.size) return 0f
        var dot = 0f
        for (index in left.indices) dot += left[index] * right[index]
        return dot
    }
}
