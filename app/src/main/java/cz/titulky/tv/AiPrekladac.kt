package cz.titulky.tv

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * [Fuki] AI překlad s kontextem hry (Google Gemini).
 *
 * 1) Po pár titulcích se AI zeptá (s vyhledáváním Google), o jakou hru jde, a sestaví si
 *    „profil hry“: postavy a jejich rod, místa, termíny, tón, tykání/vykání.
 * 2) Každou repliku pak přeloží s tímto profilem a s posledními replikami (kontext),
 *    takže sedí rod („udělala jsem“), jména a termíny.
 *
 * Klíč a model se berou z local.properties (gemini.key, gemini.model) – viz app/build.gradle.kts.
 * Bez klíče je vypnutý a aplikace funguje jako dřív.
 */
class AiPrekladac(context: Context, private val zapis: (String) -> Unit) {

    private val appContext = context.applicationContext
    /** Klíč z nastavení v telefonu (má přednost), jinak z local.properties při sestavení. */
    private val klic: String get() = klic(appContext)
    private val model = BuildConfig.GEMINI_MODEL.trim().ifEmpty { "gemini-3.5-flash-lite" }

    val zapnuto: Boolean get() = !zavreno && povoleno(appContext) && klic.isNotEmpty()

    private val ui = Handler(Looper.getMainLooper())
    /** Překlady jedním vláknem (kvůli pořadí); profil hry zvlášť, ať nebrzdí titulky. */
    private val prekladVlakno = Executors.newSingleThreadExecutor()
    private val profilVlakno = Executors.newSingleThreadExecutor()
    @Volatile private var zavreno = false

    /** Profil hry (text pro AI), dokud není znám, je prázdný. */
    @Volatile var profil: String = ""
        private set
    @Volatile var nazevHry: String = ""
        private set

    /** Nejnovější replika, o kterou se žádalo – starší, které čekají ve frontě, se přeskočí. */
    @Volatile private var nejnovejsi = 0L

    /** Posledních pár replik (anglicky → česky) jako kontext. */
    private class Radek(val cislo: Long, val en: String, var cs: String)
    private val historie = ArrayDeque<Radek>()
    /** Anglické repliky pro poznání hry. */
    private val nasbirane = ArrayList<String>()
    private var pokusuOProfil = 0
    @Volatile private var profilBezi = false
    @Volatile private var chybZaSebou = 0
    @Volatile private var pauzaDo = 0L

    /**
     * Přeloží repliky (každá může začínat „Jméno: “) s kontextem. Výsledek (repliky spojené \n)
     * vrací na hlavním vlákně; při chybě nebo přeskočení nevolá nic.
     */
    fun preloz(cislo: Long, repliky: List<String>, cil: String, hotovo: (String) -> Unit) {
        if (!zapnuto || repliky.isEmpty()) return
        if (System.currentTimeMillis() < pauzaDo) return
        if (cislo > nejnovejsi) nejnovejsi = cislo
        val kontext = synchronized(historie) { historie.toList().map { it.en to it.cs } }
        val profilTed = profil
        runCatching { prekladVlakno.execute {
            if (zavreno || cislo < nejnovejsi) return@execute // mezitím přišla novější replika
            val zacatek = System.currentTimeMillis()
            val vysledek = runCatching { prelozAi(repliky, cil, kontext, profilTed) }
                .onFailure { e -> log("[AI chyba: ${e.javaClass.simpleName} ${e.message?.take(120)}]") }
                .getOrNull()
            if (vysledek == null) {
                chybZaSebou++
                // opakované chyby (špatný klíč, limit) → chvíli nezkoušet, ať se zbytečně nečeká
                if (chybZaSebou >= 3) { pauzaDo = System.currentTimeMillis() + 60_000L; chybZaSebou = 0; log("[AI: 3 chyby za sebou – minutu nezkouším]") }
                return@execute
            }
            chybZaSebou = 0
            val ms = System.currentTimeMillis() - zacatek
            ui.post {
                if (zavreno) return@post
                log("  → AI ($ms ms, $model): " + vysledek.replace("\n", " / "))
                hotovo(vysledek)
            }
        } }
        // sbírat repliky pro poznání hry
        synchronized(nasbirane) { repliky.forEach { if (nasbirane.size < 60) nasbirane += it } }
        zkusProfil(cil)
    }

    /** Zapamatuje si, co se nakonec ukázalo (kontext pro další repliky). Stejné číslo = oprava. */
    fun zapamatuj(cislo: Long, repliky: List<String>, cesky: String) {
        val en = repliky.joinToString(" / ")
        synchronized(historie) {
            val posledni = historie.lastOrNull()
            if (posledni != null && posledni.cislo == cislo) { posledni.cs = cesky; return }
            if (posledni != null && posledni.en == en) { posledni.cs = cesky; return }
            historie.addLast(Radek(cislo, en, cesky))
            while (historie.size > 6) historie.removeFirst()
        }
    }

    // ---------------------------------------------------------------------------------------

    private fun prelozAi(repliky: List<String>, cil: String, kontext: List<Pair<String, String>>, profilTed: String): String? {
        val jazyk = JAZYKY[cil] ?: "Czech"
        val sb = StringBuilder()
        sb.append("You translate English video game subtitles into $jazyk for a live TV overlay.\n")
        sb.append("Rules:\n")
        sb.append("- Return JSON {\"t\": [...]} with exactly ${repliky.size} item(s), same order as the input lines.\n")
        sb.append("- A line may start with a speaker prefix \"Name: \". Keep the prefix exactly as is (never translate names) and translate only the rest.\n")
        sb.append("- Use the correct grammatical gender of the speaker and of the person addressed (use the game profile and previous lines). If unknown, prefer the gender of the main character from the profile.\n")
        sb.append("- Keep the established form of address (informal/formal) consistent with previous lines.\n")
        sb.append("- Proper nouns and game terms: follow the glossary in the profile; otherwise keep names untranslated.\n")
        sb.append("- Natural, short, spoken $jazyk. Fix obvious OCR errors silently. No explanations.\n")
        if (profilTed.isNotBlank()) sb.append("\nGame profile:\n").append(profilTed.take(2500)).append("\n")
        // [Fuki] jak jména a názvy překládá komunitní/oficiální čeština hry
        val nazvy = repliky.flatMap { Tabulka.nazvy(it) }.distinctBy { it.first }.take(8)
        if (nazvy.isNotEmpty()) {
            sb.append("\nHow the game's $jazyk localization renders these names/terms (may be in vocative or another case – adapt the case):\n")
            nazvy.forEach { (en, cs) -> sb.append("- ").append(en).append(" → ").append(cs.trimEnd('.', '!', '?', ',')).append("\n") }
        }
        if (kontext.isNotEmpty()) {
            sb.append("\nPrevious lines (English → $jazyk):\n")
            kontext.forEach { (en, cs) -> sb.append("- ").append(en).append(" → ").append(cs.replace("\n", " / ")).append("\n") }
        }
        sb.append("\nLines to translate:\n")
        repliky.forEachIndexed { i, r -> sb.append(i + 1).append(". ").append(r).append("\n") }

        val telo = JSONObject()
            .put("contents", JSONArray().put(JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", sb.toString())))))
            .put("generationConfig", JSONObject().put("responseMimeType", "application/json"))
        val text = volej(telo, cteniMs = 6000) ?: return null
        val json = JSONObject(text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim())
        val pole = json.optJSONArray("t") ?: return null
        if (pole.length() != repliky.size) return null
        val vysledky = (0 until pole.length()).map { pole.optString(it).trim() }
        if (vysledky.any { it.isEmpty() }) return null
        // jméno mluvčího vrátit přesně podle originálu (kdyby ho AI přece jen změnila)
        return vysledky.mapIndexed { i, cs ->
            val jm = JMENO.find(repliky[i])?.groupValues?.get(1)
            if (jm != null && !cs.startsWith("$jm:")) "$jm: " + cs.substringAfter(':', cs).trim() else cs
        }.joinToString("\n")
    }

    /** [Fuki] Hráč změnil hru → zapomenout profil, novou hru poznat znovu (z Xboxu nebo z titulků). */
    fun zapomenHru() {
        nazevHry = ""
        profil = ""
        pokusuOProfil = 0
        synchronized(nasbirane) { nasbirane.clear() }
        synchronized(historie) { historie.clear() }
        log("[AI: změna hry – profil zapomenut]")
    }

    /**
     * [Fuki] Hru poznal Xbox → profil (postavy, rod, pojmy) hned podle názvu, bez čekání na repliky.
     */
    fun znamaHra(nazev: String, cil: String) {
        if (zavreno || nazev.isBlank() || nazev.equals(nazevHry, ignoreCase = true) || profilBezi) return
        nazevHry = nazev
        profil = ""
        profilBezi = true
        runCatching { profilVlakno.execute {
            try {
                val jazyk = JAZYKY[cil] ?: "Czech"
                val dotaz = buildString {
                    append("Someone is playing the console video game \"$nazev\" right now.\n")
                    append("Use Google Search and write a compact profile (max 250 words) ")
                    append("that will help translate its subtitles into $jazyk. Use exactly this format:\n")
                    append("GAME: $nazev\n")
                    append("SETTING AND TONE: ...\n")
                    append("CHARACTERS: Name – gender (male/female/creature), role, how they talk; ...\n")
                    append("TERMS: English term – recommended $jazyk translation (or 'keep'); ...\n")
                    append("ADDRESS: which characters use informal/formal address with each other\n")
                }
                val telo = JSONObject()
                    .put("contents", JSONArray().put(JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", dotaz)))))
                    .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
                val odpoved = volej(telo, cteniMs = 45_000)?.trim().orEmpty()
                if (odpoved.isNotEmpty()) {
                    profil = odpoved
                    log("[AI: profil hry $nazev připraven (podle Xboxu)]")
                    runCatching { File(appContext.filesDir, "profil_hry.txt").writeText(odpoved) }
                }
            } catch (e: Exception) {
                log("[AI profil chyba: ${e.javaClass.simpleName} ${e.message?.take(120)}]")
            } finally {
                profilBezi = false
            }
        } }
    }

    /** Po 8 replikách se zeptat na hru (s vyhledáváním Google). Když ji nepozná, zkusí to znovu později. */
    private fun zkusProfil(cil: String) {
        if (profil.isNotEmpty() || profilBezi || pokusuOProfil >= 3) return
        val repliky = synchronized(nasbirane) { nasbirane.toList() }
        if (repliky.size < 8 + pokusuOProfil * 15) return
        profilBezi = true
        pokusuOProfil++
        runCatching { profilVlakno.execute {
            try {
                val jazyk = JAZYKY[cil] ?: "Czech"
                val dotaz = buildString {
                    append("These subtitle lines were read (OCR, may contain errors) from a console video game someone is playing right now:\n")
                    repliky.takeLast(40).forEach { append("- ").append(it).append("\n") }
                    append("\nUse Google Search to identify the game. Then write a compact profile (max 250 words) ")
                    append("that will help translate its subtitles into $jazyk. Use exactly this format:\n")
                    append("GAME: <exact title, or UNKNOWN>\n")
                    append("SETTING AND TONE: ...\n")
                    append("CHARACTERS: Name – gender (male/female/creature), role, how they talk; ...\n")
                    append("TERMS: English term – recommended $jazyk translation (or 'keep'); ...\n")
                    append("ADDRESS: which characters use informal/formal address with each other\n")
                }
                val telo = JSONObject()
                    .put("contents", JSONArray().put(JSONObject().put("role", "user")
                        .put("parts", JSONArray().put(JSONObject().put("text", dotaz)))))
                    .put("tools", JSONArray().put(JSONObject().put("google_search", JSONObject())))
                val odpoved = volej(telo, cteniMs = 45_000)?.trim().orEmpty()
                val nazev = Regex("(?im)^\\**GAME\\**:\\s*(.+)$").find(odpoved)?.groupValues?.get(1)?.trim()?.trim('*')?.trim().orEmpty()
                if (odpoved.isEmpty() || nazev.isEmpty() || nazev.uppercase().startsWith("UNKNOWN")) {
                    log("[AI: hru zatím nepoznávám (pokus $pokusuOProfil)]")
                } else {
                    profil = odpoved
                    nazevHry = nazev
                    log("[AI: hra = $nazev – profil připraven]")
                    // [Fuki] tabulka hotových překladů jen pro tuhle hru
                    if (Tabulka.pocet > 0) {
                        val t = Tabulka.nastavHru(nazev)
                        log(if (t != null) "[tabulka pro tuto hru: $t]" else "[pro tuto hru tabulka není – tabulky vypnuty]")
                    }
                    runCatching { File(appContext.filesDir, "profil_hry.txt").writeText(odpoved) }
                }
            } catch (e: Exception) {
                log("[AI profil chyba: ${e.javaClass.simpleName} ${e.message?.take(120)}]")
            } finally {
                profilBezi = false
            }
        } }
    }

    /** Zavolá Gemini, vrátí text odpovědi (nebo null). */
    private fun volej(telo: JSONObject, cteniMs: Int): String? {
        val spojeni = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            .openConnection() as HttpURLConnection
        try {
            spojeni.connectTimeout = 2500
            spojeni.readTimeout = cteniMs
            spojeni.requestMethod = "POST"
            spojeni.doOutput = true
            spojeni.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            spojeni.setRequestProperty("x-goog-api-key", klic)
            spojeni.outputStream.use { it.write(telo.toString().toByteArray(Charsets.UTF_8)) }
            val kod = spojeni.responseCode
            if (kod != 200) {
                val chyba = runCatching { spojeni.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
                log("[AI HTTP $kod: ${chyba.replace("\n", " ").take(200)}]")
                return null
            }
            val json = JSONObject(spojeni.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() })
            val casti = json.optJSONArray("candidates")?.optJSONObject(0)
                ?.optJSONObject("content")?.optJSONArray("parts") ?: return null
            val sb = StringBuilder()
            for (i in 0 until casti.length()) {
                val c = casti.optJSONObject(i) ?: continue
                if (c.optBoolean("thought", false)) continue
                sb.append(c.optString("text", ""))
            }
            return sb.toString().ifBlank { null }
        } finally {
            runCatching { spojeni.disconnect() }
        }
    }

    private fun log(t: String) { ui.post { if (!zavreno) zapis(t) } }

    fun zavri() {
        zavreno = true
        prekladVlakno.shutdownNow()
        profilVlakno.shutdownNow()
    }

    companion object {
        const val PREF_KLIC = "aiKlic"
        const val PREF_ZAP = "aiZap"
        private fun prefs(c: Context) = c.getSharedPreferences("gamelingo", Context.MODE_PRIVATE)
        fun klic(c: Context): String =
            prefs(c).getString(PREF_KLIC, null)?.trim().takeUnless { it.isNullOrEmpty() } ?: BuildConfig.GEMINI_KEY.trim()
        fun povoleno(c: Context): Boolean = prefs(c).getBoolean(PREF_ZAP, true)
        /** [Fuki] Úsporně: AI jen na delší věty (od 6 slov), krátké repliky přeloží Google. */
        const val PREF_USPORNE = "aiUsporne"
        fun usporne(c: Context): Boolean = prefs(c).getBoolean(PREF_USPORNE, false)

        private val JMENO = Regex("^([A-Z][A-Za-z0-9 .'-]{1,24}):\\s*")
        private val JAZYKY = mapOf(
            "cs" to "Czech", "sk" to "Slovak", "de" to "German", "pl" to "Polish",
            "fr" to "French", "es" to "Spanish", "it" to "Italian"
        )
    }
}
