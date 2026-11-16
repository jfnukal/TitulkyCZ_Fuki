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
    /** Hlasitost hlasu 0,1–1 (1 = jako hlasitost médií v zařízení) */
    const val PREF_HLASITOST = "hlasHlasitost"

    private var tts: TextToSpeech? = null
    @Volatile private var pripraven = false
    @Volatile private var startuje = false
    private var cekajici: Triple<String, Float, Float>? = null
    /** Poslední problém (prázdné = vše v pořádku). */
    @Volatile var chyba: String = ""
        private set

    private fun prefs(c: Context) = c.getSharedPreferences("gamelingo", Context.MODE_PRIVATE)
    fun zapnuto(c: Context) = prefs(c).getBoolean(PREF_ZAP, false)
    fun kde(c: Context) = prefs(c).getString(PREF_KDE, "tv") ?: "tv"
    fun rychlost(c: Context) = prefs(c).getFloat(PREF_RYCHLOST, 1f)
    fun hlasitost(c: Context) = prefs(c).getFloat(PREF_HLASITOST, 1f)

    private val JMENO = Regex("^[\\p{Lu}][\\p{L}0-9 .'-]{0,24}:\\s+")

    /** Text titulku bez jmen postav, řádky spojené do vět. */
    fun cisty(text: String) = text.lines().map { JMENO.replace(it.trim(), "") }.filter { it.isNotBlank() }
        .joinToString(" ") { if (it.last() in ".!?…") it else "$it." }

    fun rekni(c: Context, text: String, rychlost: Float = rychlost(c), hlasitost: Float = hlasitost(c)) {
        val t = cisty(text)
        if (t.isBlank()) return
        synchronized(this) {
            if (!pripraven) {
                cekajici = Triple(t, rychlost, hlasitost)
                if (!startuje) spust(c)
                return
            }
        }
        mluv(t, rychlost, hlasitost)
    }

    fun zkouska(c: Context) = rekni(c, "Ahoj, takhle budu číst české titulky.")

    fun ztlum() { dalsi = null; runCatching { tts?.stop() } }

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
                cekajici?.let { mluv(it.first, it.second, it.third) }
                cekajici = null
            }
        }
    }

    /**
     * [Fuki] Čtení bez přerušování: rozečtená replika se vždy dočte. Co přijde mezitím, počká;
     * čeká jen ta nejnovější (starší čekající se zahodí, ať hlas nezaostává) a přečte se o něco rychleji.
     */
    @Volatile private var dalsi: Triple<String, Float, Float>? = null
    @Volatile private var mluvi = false
    @Volatile private var naposledy = ""
    @Volatile private var naposledyKdy = 0L
    private var cisloPromluvy = 0
    private var posluchac = false

    private fun mluv(text: String, rychlost: Float, hlasitost: Float) {
        val t = tts ?: return
        if (!posluchac) {
            posluchac = true
            t.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                override fun onStart(id: String?) { mluvi = true }
                override fun onDone(id: String?) { mluvi = false; dalsiNaRadu() }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) { mluvi = false; dalsiNaRadu() }
                override fun onStop(id: String?, interrupted: Boolean) { mluvi = false }
            })
        }
        // stejná / jen doplněná věta (hra ji vypisuje postupně) → nečíst znovu
        val nedavno = System.currentTimeMillis() - naposledyKdy < 4000
        if (naposledy.isNotEmpty() && ((text == naposledy && nedavno) || (mluvi && text.startsWith(naposledy.take(25))))) return
        if (mluvi || t.isSpeaking) {
            dalsi = Triple(text, rychlost * 1.15f, hlasitost)
            return
        }
        rekniTed(text, rychlost, hlasitost)
    }

    private fun dalsiNaRadu() {
        val d = dalsi ?: return
        dalsi = null
        rekniTed(d.first, d.second, d.third)
    }

    private fun rekniTed(text: String, rychlost: Float, hlasitost: Float) {
        val t = tts ?: return
        runCatching {
            // delší replika = o něco rychlejší řeč, ať stihne doběhnout, než přijde další
            val zrychleni = when {
                text.length > 110 -> 1.3f
                text.length > 70 -> 1.15f
                else -> 1f
            }
            t.setSpeechRate((rychlost * zrychleni).coerceIn(0.5f, 2.2f))
            val par = Bundle().apply { putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, hlasitost.coerceIn(0.05f, 1f)) }
            naposledy = text
            naposledyKdy = System.currentTimeMillis()
            mluvi = true
            t.speak(text, TextToSpeech.QUEUE_FLUSH, par, "titulek${++cisloPromluvy}")
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
