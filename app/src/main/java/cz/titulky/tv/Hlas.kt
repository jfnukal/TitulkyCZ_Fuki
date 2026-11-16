package cz.titulky.tv

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * [Fuki] Česky nahlas: čte přeložené titulky českým hlasem (Android převod textu na řeč).
 * Jména postav („Arthur: …“) se nečtou. Nová replika přeruší starou.
 */
object Hlas {
    const val PREF_ZAP = "hlasZap"
    /** "tv" = mluví televize, "tel" = mluví telefon (když titulky jdou z telefonu do TV) */
    const val PREF_KDE = "hlasKde"
    const val PREF_RYCHLOST = "hlasRychlost"

    private var tts: TextToSpeech? = null
    @Volatile private var pripraven = false
    @Volatile private var startuje = false
    private var cekajici: Pair<String, Float>? = null
    /** Poslední problém (prázdné = vše v pořádku). */
    @Volatile var chyba: String = ""
        private set

    private fun prefs(c: Context) = c.getSharedPreferences("gamelingo", Context.MODE_PRIVATE)
    fun zapnuto(c: Context) = prefs(c).getBoolean(PREF_ZAP, false)
    fun kde(c: Context) = prefs(c).getString(PREF_KDE, "tv") ?: "tv"
    fun rychlost(c: Context) = prefs(c).getFloat(PREF_RYCHLOST, 1f)

    private val JMENO = Regex("^[\\p{Lu}][\\p{L}0-9 .'-]{0,24}:\\s+")

    /** Text titulku bez jmen postav, řádky spojené do vět. */
    fun cisty(text: String) = text.lines().map { JMENO.replace(it.trim(), "") }.filter { it.isNotBlank() }
        .joinToString(" ") { if (it.last() in ".!?…") it else "$it." }

    fun rekni(c: Context, text: String, rychlost: Float = rychlost(c)) {
        val t = cisty(text)
        if (t.isBlank()) return
        synchronized(this) {
            if (!pripraven) {
                cekajici = t to rychlost
                if (!startuje) spust(c)
                return
            }
        }
        mluv(t, rychlost)
    }

    fun zkouska(c: Context) = rekni(c, "Ahoj, takhle budu číst české titulky.")

    fun ztlum() { runCatching { tts?.stop() } }

    private fun spust(c: Context) {
        startuje = true
        tts = TextToSpeech(c.applicationContext) { stav ->
            synchronized(this) {
                startuje = false
                if (stav != TextToSpeech.SUCCESS) { chyba = "Převod textu na řeč se nepodařilo spustit"; cekajici = null; return@synchronized }
                val r = tts?.setLanguage(Locale("cs", "CZ")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
                    chyba = "Český hlas není v zařízení nainstalovaný"
                    cekajici = null
                    return@synchronized
                }
                chyba = ""
                pripraven = true
                cekajici?.let { mluv(it.first, it.second) }
                cekajici = null
            }
        }
    }

    private fun mluv(text: String, rychlost: Float) {
        val t = tts ?: return
        runCatching {
            t.setSpeechRate(rychlost.coerceIn(0.5f, 2f))
            t.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "titulek")
        }
    }

    /** Otevře instalaci hlasových dat (když čeština chybí). */
    fun nainstalujCestinu(c: Context) {
        runCatching {
            c.startActivity(android.content.Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
