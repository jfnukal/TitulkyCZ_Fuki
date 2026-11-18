package cz.titulky.tv

import java.nio.ByteBuffer
import java.security.MessageDigest

/** Otisk celého výřezu před OCR; zahrnuje i pixely, které rychlý detektor změn přeskakuje. */
internal object OtiskOcr {
    private fun zacni(w: Int, h: Int) = MessageDigest.getInstance("SHA-256").apply {
        update(ByteBuffer.allocate(8).putInt(w).putInt(h).array())
    }
    private fun konec(hash: MessageDigest) = hash.digest().joinToString("") { "%02x".format(it) }

    fun sedy(pixely: ByteArray, w: Int, h: Int): String = konec(zacni(w, h).apply { update(pixely) })

    fun barvy(pixely: IntArray, w: Int, h: Int): String {
        val hash = zacni(w, h)
        val blok = ByteBuffer.allocate(4096)
        for (pixel in pixely) {
            blok.putInt(pixel)
            if (!blok.hasRemaining()) { hash.update(blok.array()); blok.clear() }
        }
        hash.update(blok.array(), 0, blok.position())
        return konec(hash)
    }
}
