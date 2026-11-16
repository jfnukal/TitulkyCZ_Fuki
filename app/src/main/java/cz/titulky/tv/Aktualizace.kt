package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.LinearLayout
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * [Fuki] Automatická aktualizace z GitHub Releases (jfnukal/TitulkyCZ_Fuki).
 * Při spuštění se podívá na nejnovější vydání; když je novější než nainstalovaná verze,
 * nabídne „Aktualizovat“ → stáhne APK a spustí instalaci.
 */
object Aktualizace {
    private const val API = "https://api.github.com/repos/jfnukal/TitulkyCZ_Fuki/releases/latest"
    @Volatile private var zkontrolovano = false
    private val ui = Handler(Looper.getMainLooper())

    /** Číslo verze Fuki z názvu verze („0.20.1-fuki10“ → 10), jinak -1. */
    fun cislo(verze: String): Int = Regex("fuki(\\d+)").find(verze)?.groupValues?.get(1)?.toIntOrNull() ?: -1

    data class Vydani(val verze: String, val apkUrl: String, val popis: String)

    /** Jednou za spuštění aplikace: zkontrolovat a případně nabídnout aktualizaci. */
    /** [Fuki] Úklid: stažený instalační soubor po aktualizaci už není potřeba (desítky MB). */
    fun uklid(c: android.content.Context) {
        runCatching {
            c.getExternalFilesDir("aktualizace")?.listFiles()?.forEach { f ->
                if (System.currentTimeMillis() - f.lastModified() > 10 * 60_000L) f.delete()
            }
        }
    }

    fun zkontroluj(a: Activity, rucne: Boolean = false) {
        if (zkontrolovano && !rucne) return
        zkontrolovano = true
        Thread {
            val v = runCatching { nejnovejsi() }.getOrNull()
            ui.post {
                if (a.isFinishing) return@post
                if (v != null && cislo(v.verze) > cislo(BuildConfig.VERSION_NAME)) nabidni(a, v)
                else if (rucne) android.widget.Toast.makeText(a,
                    if (v == null) "Nepodařilo se zjistit novou verzi. Zkontroluj připojení k internetu."
                    else "Máš nejnovější verzi (${BuildConfig.VERSION_NAME}).",
                    android.widget.Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun nejnovejsi(): Vydani? {
        val c = URL(API).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 5000; c.readTimeout = 8000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "TitulkyCZ_Fuki")
            if (c.responseCode != 200) return null
            val j = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            val tag = j.optString("tag_name").removePrefix("v")
            val assets = j.optJSONArray("assets") ?: return null
            for (i in 0 until assets.length()) {
                val as_ = assets.getJSONObject(i)
                if (as_.optString("name").endsWith(".apk"))
                    return Vydani(tag, as_.optString("browser_download_url"), j.optString("body").take(600))
            }
            return null
        } finally { runCatching { c.disconnect() } }
    }

    private fun nabidni(a: Activity, v: Vydani) {
        AlertDialog.Builder(a)
            .setTitle("Nová verze ${v.verze}")
            .setMessage("Máš ${BuildConfig.VERSION_NAME}. Chceš aktualizovat teď?\n\nStáhne se z GitHubu (asi 80 MB) a spustí se instalace.")
            .setPositiveButton("Aktualizovat") { _, _ -> stahni(a, v) }
            .setNegativeButton("Později", null)
            .show()
    }

    private fun stahni(a: Activity, v: Vydani) {
        // povolení instalovat aplikace (Android 8+) – jednou
        if (Build.VERSION.SDK_INT >= 26 && !a.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(a)
                .setTitle("Povol instalaci")
                .setMessage("Aby se aktualizace mohla nainstalovat, povol pro TitulkyCZ_Fuki „Instalovat neznámé aplikace“. Pak se vrať a dej Aktualizovat znovu.")
                .setPositiveButton("Otevřít nastavení") { _, _ ->
                    runCatching { a.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${a.packageName}"))) }
                    zkontrolovano = false
                }
                .setNegativeButton("Zrušit", null)
                .show()
            return
        }
        val d = a.resources.displayMetrics.density
        val pruh = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        val popis = TextView(a).apply { text = "Stahuji…" }
        val obal = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * d).toInt(), (16 * d).toInt(), (24 * d).toInt(), 0)
            addView(pruh); addView(popis)
        }
        val okno = AlertDialog.Builder(a).setTitle("Aktualizace ${v.verze}").setView(obal).setCancelable(false).show()
        Thread {
            val soubor = File(a.getExternalFilesDir("aktualizace"), "TitulkyCZ_Fuki.apk")
            val chyba = runCatching {
                soubor.parentFile?.mkdirs()
                val c = URL(v.apkUrl).openConnection() as HttpURLConnection
                c.instanceFollowRedirects = true
                c.connectTimeout = 10000; c.readTimeout = 30000
                c.setRequestProperty("User-Agent", "TitulkyCZ_Fuki")
                val celkem = c.contentLengthLong
                c.inputStream.use { vstup ->
                    soubor.outputStream().use { vystup ->
                        val buf = ByteArray(64 * 1024)
                        var hotovo = 0L
                        var posledni = -1
                        while (true) {
                            val n = vstup.read(buf)
                            if (n < 0) break
                            vystup.write(buf, 0, n)
                            hotovo += n
                            val p = if (celkem > 0) (hotovo * 100 / celkem).toInt() else 0
                            if (p != posledni) {
                                posledni = p
                                ui.post { pruh.progress = p; popis.text = "Stahuji… $p % (${hotovo / 1_048_576} MB)" }
                            }
                        }
                    }
                }
                c.disconnect()
            }.exceptionOrNull()
            ui.post {
                runCatching { okno.dismiss() }
                if (chyba != null) {
                    android.widget.Toast.makeText(a, "Stažení se nepovedlo: ${chyba.message}", android.widget.Toast.LENGTH_LONG).show()
                    return@post
                }
                val uri = FileProvider.getUriForFile(a, "${a.packageName}.soubory", soubor)
                runCatching {
                    a.startActivity(Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "application/vnd.android.package-archive")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }.onFailure {
                    android.widget.Toast.makeText(a, "Instalaci se nepodařilo spustit: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }
}
