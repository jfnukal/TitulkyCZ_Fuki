package cz.titulky.tv

import android.content.Context
import java.io.File

/**
 * [Fuki] Co si aplikace ukládá z textů her a jak to smazat.
 * - paměť překladů (preklady_<jazyk>.tsv): anglická věta → český překlad, strop 20 000 vět (pak se zkrátí na 5 000)
 * - profil hry pro AI (profil_hry.txt): jedna hra, přepisuje se
 * - záznamy pro hledání chyb (titulky.log, xbox.log, chiaki.log, pad.txt): strop 300 kB
 * - tabulky překladů (složka tabulky): jen to, co si uživatel sám nahrál
 */
object Uloziste {
    const val PREF_PAMET = "ukladatPreklady"

    fun ukladat(c: Context) = c.getSharedPreferences("gamelingo", Context.MODE_PRIVATE).getBoolean(PREF_PAMET, true)

    private fun texty(c: Context): List<File> =
        (c.filesDir.listFiles()?.filter { it.name.startsWith("preklady_") && it.name.endsWith(".tsv") } ?: emptyList()) +
            listOf("profil_hry.txt", "titulky.log", "xbox.log", "chiaki.log", "pad.txt").map { File(c.filesDir, it) }.filter { it.exists() }

    private fun tabulky(c: Context): List<File> = c.getExternalFilesDir("tabulky")?.listFiles()?.filter { it.isFile } ?: emptyList()

    private fun kb(b: Long) = if (b >= 1024 * 1024) "%.1f MB".format(b / 1048576f) else "${b / 1024} kB"

    fun prehled(c: Context): String {
        val pamet = texty(c).filter { it.name.startsWith("preklady_") }.sumOf { it.length() }
        val ostatni = texty(c).filter { !it.name.startsWith("preklady_") }.sumOf { it.length() }
        val tab = tabulky(c).sumOf { it.length() }
        return "Paměť překladů ${kb(pamet)} · záznamy a profil hry ${kb(ostatni)} · tabulky ${kb(tab)}"
    }

    /** Smaže paměť překladů, profil hry a záznamy (tabulky jen na výslovné přání). */
    fun vymaz(c: Context, iTabulky: Boolean = false) {
        texty(c).forEach { runCatching { it.delete() } }
        if (iTabulky) { tabulky(c).forEach { runCatching { it.delete() } }; runCatching { Tabulka.znovuNacti(c.getExternalFilesDir("tabulky")) } }
    }
}
