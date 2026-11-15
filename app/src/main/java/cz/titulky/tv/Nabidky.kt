package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.widget.FrameLayout
import android.widget.Toast

/** [Fuki] Společná okna pro hlavní obrazovku i obrazovku hraní: AI klíč, nahrání tabulky. */
object Nabidky {
    const val KOD_TABULKA = 4711

    /** Zadat / změnit klíč ke Gemini, zapnout / vypnout AI překlad. */
    fun aiNastaveni(a: Activity, hotovo: () -> Unit = {}) {
        val prefs = a.getSharedPreferences("gamelingo", Activity.MODE_PRIVATE)
        val d = a.resources.displayMetrics.density
        val pole = android.widget.EditText(a).apply {
            hint = "API klíč Gemini (AIza…)"
            setText(prefs.getString(AiPrekladac.PREF_KLIC, "") ?: "")
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        val ramec = FrameLayout(a).apply { setPadding((20 * d).toInt(), (8 * d).toInt(), (20 * d).toInt(), 0); addView(pole) }
        AlertDialog.Builder(a)
            .setTitle("AI překlad s kontextem hry")
            .setMessage("Gemini pozná hru a překládá s ohledem na postavy (rod), jména a předchozí věty. Klíč získáš na aistudio.google.com → Get API key. Text titulků se posílá Googlu.")
            .setView(ramec)
            .setPositiveButton("Uložit") { _, _ ->
                prefs.edit().putString(AiPrekladac.PREF_KLIC, pole.text.toString().trim()).putBoolean(AiPrekladac.PREF_ZAP, true).apply()
                hotovo()
            }
            .setNeutralButton("Vypnout AI") { _, _ -> prefs.edit().putBoolean(AiPrekladac.PREF_ZAP, false).apply(); hotovo() }
            .setNegativeButton("Zrušit", null)
            .show()
    }

    /** Systémový výběr souboru s tabulkou (.tsv / .tsv.gz). Výsledek → [zpracujTabulku] v onActivityResult. */
    fun vyberTabulku(a: Activity) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "*/*" }
        runCatching { a.startActivityForResult(i, KOD_TABULKA) }
            .onFailure { Toast.makeText(a, "Výběr souboru se nepodařilo otevřít: ${it.message}", Toast.LENGTH_LONG).show() }
    }

    fun zpracujTabulku(a: Activity, data: Intent?, hotovo: () -> Unit) {
        val uri = data?.data ?: return
        val jmeno = runCatching {
            a.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "tabulka.tsv"
        if (!jmeno.endsWith(".tsv") && !jmeno.endsWith(".tsv.gz")) {
            Toast.makeText(a, "Vyber soubor .tsv nebo .tsv.gz (např. rdr2_cs.tsv.gz)", Toast.LENGTH_LONG).show()
            return
        }
        Toast.makeText(a, "Nahrávám tabulku…", Toast.LENGTH_SHORT).show()
        val slozka = a.getExternalFilesDir("tabulky") ?: java.io.File(a.filesDir, "tabulky")
        val ui = android.os.Handler(android.os.Looper.getMainLooper())
        Thread {
            val chyba = runCatching {
                slozka.mkdirs()
                val cil = java.io.File(slozka, jmeno.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                a.contentResolver.openInputStream(uri)!!.use { vstup -> cil.outputStream().use { vstup.copyTo(it) } }
            }.exceptionOrNull()
            ui.post {
                if (chyba != null) {
                    Toast.makeText(a, "Tabulku se nepodařilo nahrát: ${chyba.message}", Toast.LENGTH_LONG).show()
                    return@post
                }
                Tabulka.znovuNacti(slozka, hotovo = {
                    ui.post {
                        Toast.makeText(a, "Tabulka nahrána: ${Tabulka.pocet} vět", Toast.LENGTH_LONG).show()
                        hotovo()
                    }
                })
            }
        }.start()
    }
}
