package cz.titulky.tv

import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.GZIPInputStream

/**
 * [Fuki] Tabulky hotových překladů „anglicky → česky“ (např. z komunitní češtiny ke hře).
 *
 * Soubory *.tsv nebo *.tsv.gz (řádek: anglický text TAB český text) se dávají do složky
 *   Android/data/<balíček>/files/tabulky/
 * (např. přes adb push). Hlavička souboru může říct, ke které hře patří:
 *   #hra: Red Dead Redemption 2
 *   #alias: RDR2, RDR 2
 * Když AI pozná hru, používá se jen tabulka té hry (méně falešných shod u krátkých vět).
 *
 * Přečtený titulek se v tabulce najde i s drobnou chybou čtení a i když hra dlouhou
 * repliku rozdělí do dvou titulků (najde se začátek / konec věty) → ukáže se lidský překlad.
 */
object Tabulka {

    private class Index(val soubor: String) {
        var hra: String = soubor.substringBefore('.')
        val aliasy = ArrayList<String>()
        /** Normalizovaný anglický text → český překlad. */
        val presne = HashMap<String, String>()
        /** Pro hledání s chybou / části věty: začátek / konec normalizovaného textu → kandidáti. */
        val podleZacatku = HashMap<String, MutableList<String>>()
        val podleKonce = HashMap<String, MutableList<String>>()
    }

    private val indexy = CopyOnWriteArrayList<Index>()
    /** Které tabulky se používají: null = všechny (hra zatím není známá). */
    @Volatile private var aktivni: List<Index>? = null

    @Volatile var nacteno = false
        private set
    @Volatile var pocet = 0
        private set
    @Volatile private var nacitaSe = false

    /** Kolik znaků začátku/konce se bere pro hledání s chybou. */
    private const val KUS = 8
    /** Kratší texty jen přesně (u krátkých vět by „podobné“ bylo něco úplně jiného). */
    private const val MIN_PRO_PODOBNE = 12
    /** Část věty (rozdělená replika) musí být aspoň takhle dlouhá, ať je jednoznačná. */
    private const val MIN_PRO_CAST = 20

    fun norm(t: String): String {
        val sb = StringBuilder(t.length)
        for (c in t) if (c.isLetterOrDigit()) sb.append(c.lowercaseChar())
        return sb.toString()
    }

    private fun pouzivane(): List<Index> = aktivni ?: indexy

    /** [Fuki] Zahodit načtené a načíst znovu (po nahrání nové tabulky z telefonu). */
    fun znovuNacti(slozka: File?, zapis: (String) -> Unit = {}, hotovo: () -> Unit = {}) {
        if (nacitaSe) return
        indexy.clear(); aktivni = null; pocet = 0; nacteno = false
        nacti(slozka, zapis, hotovo)
    }

    /** Načte všechny tabulky ze složky (na pozadí, jen jednou). */
    fun nacti(slozka: File?, zapis: (String) -> Unit = {}, hotovo: () -> Unit = {}) {
        if (slozka == null || nacteno || nacitaSe) return
        nacitaSe = true
        Thread({
            try {
                runCatching { slozka.mkdirs() }
                val soubory = slozka.listFiles { f -> f.name.endsWith(".tsv") || f.name.endsWith(".tsv.gz") }
                    ?.sortedBy { it.name }.orEmpty()
                val zacatek = System.currentTimeMillis()
                for (f in soubory) runCatching { nactiSoubor(f) }
                    .onFailure { zapis("[tabulka ${f.name}: chyba ${it.message}]") }
                if (soubory.isNotEmpty())
                    zapis("[tabulky: " + indexy.joinToString { "${it.soubor} (${it.hra}, ${it.presne.size} vět)" } +
                        " – za ${System.currentTimeMillis() - zacatek} ms]")
                nacteno = true
            } finally {
                nacitaSe = false
                runCatching { hotovo() }
            }
        }, "tabulka").apply { priority = Thread.MIN_PRIORITY }.start()
    }

    fun nactiSoubor(f: File) {
        val ix = Index(f.name)
        val vstup = if (f.name.endsWith(".gz")) GZIPInputStream(f.inputStream().buffered()) else f.inputStream()
        vstup.bufferedReader(Charsets.UTF_8).useLines { radky ->
            for (r in radky) {
                if (r.startsWith("#")) {
                    val h = r.drop(1).trim()
                    when {
                        h.startsWith("hra:", true) -> ix.hra = h.substringAfter(':').trim()
                        h.startsWith("alias:", true) -> h.substringAfter(':').split(',').map { it.trim() }
                            .filter { it.isNotEmpty() }.forEach { ix.aliasy += it }
                    }
                    continue
                }
                val i = r.indexOf('\t')
                if (i <= 0 || i == r.length - 1) continue
                val en = norm(r.substring(0, i))
                // titulky jsou krátké – dlouhé texty (deník, popisy) by jen zabíraly paměť
                if (en.length < 2 || en.length > 300) continue
                if (ix.presne.containsKey(en)) continue // první výskyt vyhrává
                ix.presne[en] = r.substring(i + 1).trim()
                if (en.length >= MIN_PRO_PODOBNE) {
                    ix.podleZacatku.getOrPut(en.substring(0, KUS)) { ArrayList(2) } += en
                    ix.podleKonce.getOrPut(en.substring(en.length - KUS)) { ArrayList(2) } += en
                }
            }
        }
        indexy += ix
        pocet = indexy.sumOf { it.presne.size }
    }

    /**
     * AI poznala hru → používat jen tabulku té hry. Vrátí název vybrané tabulky,
     * nebo null (pro tuhle hru tabulka není → tabulky se nepoužijí vůbec).
     */
    fun nastavHru(nazev: String): String? {
        if (indexy.isEmpty()) return null
        val n = norm(nazev)
        val shoda = indexy.filter { ix ->
            (listOf(ix.hra) + ix.aliasy).map { norm(it) }.any { a -> a.length >= 3 && (n.contains(a) || a.contains(n)) }
        }
        aktivni = shoda
        return shoda.firstOrNull()?.soubor
    }

    /** Překlad z tabulky: celá věta, nebo část rozdělené repliky. Jinak null. */
    fun preloz(anglicky: String): String? = najdi(anglicky) ?: najdiCast(anglicky)

    /** Najde překlad celého titulku (i s drobnou chybou čtení), nebo null. */
    fun najdi(anglicky: String): String? {
        val ixy = pouzivane()
        if (ixy.isEmpty()) return null
        val n = norm(anglicky)
        if (n.length < 2) return null
        for (ix in ixy) ix.presne[n]?.let { return it }
        if (n.length < MIN_PRO_PODOBNE) return null
        // s chybou čtení: kandidáti se stejným začátkem nebo koncem
        val povoleno = maxOf(1, n.length / 10)
        var nejlepsi: String? = null
        var nejmensi = Int.MAX_VALUE
        for (ix in ixy) {
            val kandidati = LinkedHashSet<String>()
            ix.podleZacatku[n.substring(0, KUS)]?.let { kandidati += it.take(200) }
            ix.podleKonce[n.substring(n.length - KUS)]?.let { kandidati += it.take(200) }
            for (k in kandidati) {
                if (kotlin.math.abs(k.length - n.length) > povoleno) continue
                val v = vzdalenost(n, k, povoleno)
                if (v < nejmensi) { nejmensi = v; nejlepsi = ix.presne[k] }
            }
        }
        return if (nejmensi <= povoleno) nejlepsi else null
    }

    /**
     * Hra dlouhou repliku ukáže ve dvou titulcích („we need to“ → „get that fire lit quick.“).
     * Když je přečtený text začátkem nebo koncem jedné věty z tabulky, vrátí celou českou větu.
     * Když by to mohlo být víc různých vět, vrátí null (raději nic než špatně).
     */
    fun najdiCast(anglicky: String): String? {
        val ixy = pouzivane()
        if (ixy.isEmpty()) return null
        val n = norm(anglicky)
        if (n.length < MIN_PRO_CAST) return null
        val povoleno = maxOf(1, n.length / 12)
        val nalezene = HashSet<String>()
        for (ix in ixy) {
            ix.podleZacatku[n.substring(0, KUS)]?.let { seznam ->
                for (k in seznam.take(400)) {
                    if (k.length < n.length + KUS) continue
                    if (vzdalenost(n, k.substring(0, n.length), povoleno) <= povoleno) ix.presne[k]?.let { nalezene += it }
                }
            }
            ix.podleKonce[n.substring(n.length - KUS)]?.let { seznam ->
                for (k in seznam.take(400)) {
                    if (k.length < n.length + KUS) continue
                    if (vzdalenost(n, k.substring(k.length - n.length), povoleno) <= povoleno) ix.presne[k]?.let { nalezene += it }
                }
            }
            if (nalezene.size > 1) return null
        }
        return nalezene.singleOrNull()
    }

    /**
     * Jména a názvy z repliky, které má tabulka přeložené (pro AI: „Mrs. Grimshaw → paní Grimshawová“).
     * Bere 1–3 slova za sebou s velkým počátečním písmenem.
     */
    fun nazvy(text: String, max: Int = 6): List<Pair<String, String>> {
        val ixy = pouzivane()
        if (ixy.isEmpty()) return emptyList()
        val slova = text.split(Regex("[^\\p{L}\\p{N}.'-]+")).map { it.trim('.', '\'', '-') }.filter { it.isNotEmpty() }
        val vysledek = LinkedHashMap<String, String>()
        for (i in slova.indices) {
            for (delka in 3 downTo 1) {
                if (i + delka > slova.size) continue
                val kus = slova.subList(i, i + delka)
                if (kus.any { !it[0].isUpperCase() }) continue
                val fraze = kus.joinToString(" ")
                val n = norm(fraze)
                if (n.length < 4 || fraze in vysledek) continue
                val cs = ixy.firstNotNullOfOrNull { it.presne[n] } ?: continue
                if (norm(cs) == n || cs.length > 60) continue
                vysledek[fraze] = cs
                break
            }
            if (vysledek.size >= max) break
        }
        return vysledek.toList()
    }

    /** Levenshteinova vzdálenost s horní mezí (nad mez vrátí mez + 1). */
    private fun vzdalenost(a: String, b: String, mez: Int): Int {
        var pred = IntArray(b.length + 1) { it }
        var ted = IntArray(b.length + 1)
        for (i in 1..a.length) {
            ted[0] = i
            var minRadek = ted[0]
            for (j in 1..b.length) {
                val cena = if (a[i - 1] == b[j - 1]) 0 else 1
                ted[j] = minOf(pred[j] + 1, ted[j - 1] + 1, pred[j - 1] + cena)
                if (ted[j] < minRadek) minRadek = ted[j]
            }
            if (minRadek > mez) return mez + 1
            val x = pred; pred = ted; ted = x
        }
        return pred[b.length]
    }
}
