package com.callerid.number.lookup.home

internal object Veiled {
    private const val K: Int = 0x5A

    fun s(b: ByteArray): String {
        val out = ByteArray(b.size)
        for (i in b.indices) out[i] = (b[i].toInt() xor K).toByte()
        return String(out, Charsets.UTF_8)
    }
}
