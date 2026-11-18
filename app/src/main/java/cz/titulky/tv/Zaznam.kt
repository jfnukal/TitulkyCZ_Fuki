package cz.titulky.tv

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/** Okno se záznamem (pád, titulky.log, chiaki.log) – stačí vyfotit a poslat. */
object Zaznam {
    fun ukaz(a: Activity, sVyvojem: Boolean = false) {
        fun konec(jmeno: String, radku: Int): String = runCatching {
            val f = File(a.filesDir, jmeno)
            if (!f.exists()) "(soubor $jmeno neexistuje)"
            else f.readLines().takeLast(radku).joinToString("\n").ifBlank { "(prázdný)" }
        }.getOrDefault("(nejde přečíst $jmeno)")
        // chiaki.log: opakované "re-sending" řádky jen spočítat, ať je vidět i to ostatní
        fun chiaki(): String = runCatching {
            val f = File(a.filesDir, "chiaki.log")
            if (!f.exists()) return@runCatching "(soubor chiaki.log neexistuje)"
            val radky = f.readLines().takeLast(3000)
            val znovu = radky.count { it.contains("re-sending") }
            "(posledních ${radky.size} řádků, z toho opakované odeslání: $znovu×)\n" +
                radky.filter { !it.contains("re-sending") }.takeLast(40).joinToString("\n")
        }.getOrDefault("(nejde přečíst chiaki.log)")
        // připojení a odpojení zvlášť – v titulky.log by je titulky rychle odsunuly z dohledu
        fun spojeni(): String = runCatching {
            val f = File(a.filesDir, "titulky.log")
            if (!f.exists()) return@runCatching "(nic)"
            val klice = listOf("připojuji", "PŘIPOJENO", "UKONČENO", "žádný obraz", "převz", "odpoj", "Hotovo – hrát",
                "Xbox:", "staré spojení", "kodek", "obraz: ", "jiné zařízení", "ukončeno v aplikaci")
            f.readLines().filter { r -> klice.any { it in r } }.takeLast(30).joinToString("\n").ifBlank { "(nic)" }
        }.getOrDefault("(nejde přečíst)")
        val verze = runCatching { a.packageManager.getPackageInfo(a.packageName, 0).versionName }.getOrNull()
        val obsah = "Verze: $verze   Stav: ${RemotePlayService.stav}\n\n=== pád ===\n" + konec("pad.txt", 25) +
            "\n\n=== spojení (připojení / odpojení) ===\n" + spojeni() +
            "\n\n=== titulky.log ===\n" + konec("titulky.log", 120) +
            "\n\n=== xbox.log ===\n" + konec("xbox.log", 15) +
            "\n\n=== chiaki.log ===\n" + chiaki()
        // [podle PlayCaption 0.22] přes celou obrazovku; šipky ▲▼ listují, Zpět zavře – stačí vyfotit
        val d = a.resources.displayMetrics.density
        fun px(x: Int) = (x * d).toInt()
        val telefon = Rezim.jeTelefon(a)
        val dlg = android.app.Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val text = TextView(a).apply {
            this.text = obsah
            textSize = if (telefon) 11f else 13f
            typeface = Typeface.MONOSPACE
            setTextColor(Metro.TEXT)
            setLineSpacing(0f, 1.2f)
            if (telefon) setTextIsSelectable(true)
            setPadding(px(16), px(12), px(16), px(12))
        }
        val pocet = obsah.count { it == '\n' } + 1
        val pozice = Metro.text(a, "", 12f, Metro.OBRYS, 600)
        val posuv = ScrollView(a).apply {
            isFocusable = true
            isVerticalScrollBarEnabled = true
            isScrollbarFadingEnabled = false
            setBackgroundColor(Metro.SEDA_DLAZDICE)
            addView(text)
        }
        fun obnovPozici() {
            val vyska = text.lineHeight.coerceAtLeast(1)
            pozice.text = "Řádky ${posuv.scrollY / vyska + 1}–${((posuv.scrollY + posuv.height) / vyska).coerceAtMost(pocet)} z $pocet   ·   ▲▼ listovat   ·   Zpět zavře"
        }
        posuv.viewTreeObserver.addOnScrollChangedListener { obnovPozici() }
        posuv.setOnKeyListener { _, kod, ev ->
            if (ev.action != android.view.KeyEvent.ACTION_DOWN) return@setOnKeyListener false
            val krok = text.lineHeight * 4
            when (kod) {
                android.view.KeyEvent.KEYCODE_DPAD_DOWN -> if (!posuv.canScrollVertically(1)) false else { posuv.smoothScrollBy(0, krok); true }
                android.view.KeyEvent.KEYCODE_DPAD_UP -> if (!posuv.canScrollVertically(-1)) false else { posuv.smoothScrollBy(0, -krok); true }
                android.view.KeyEvent.KEYCODE_PAGE_DOWN, android.view.KeyEvent.KEYCODE_CHANNEL_DOWN -> { posuv.smoothScrollBy(0, posuv.height); true }
                android.view.KeyEvent.KEYCODE_PAGE_UP, android.view.KeyEvent.KEYCODE_CHANNEL_UP -> { posuv.smoothScrollBy(0, -posuv.height); true }
                else -> false
            }
        }
        fun tlacitko(popis: String, akce: () -> Unit) = Metro.text(a, popis, 14f, Color.WHITE, 600).apply {
            gravity = android.view.Gravity.CENTER
            isFocusable = true; isClickable = true
            background = Metro.pozadi(Metro.MODRA, Metro.MODRA_VYBER)
            setPadding(px(18), px(10), px(18), px(10))
            setOnClickListener { akce() }
        }
        val dole = android.widget.LinearLayout(a).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(pozice, android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (telefon) addView(tlacitko("Sdílet") {
                runCatching { a.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain")
                    .putExtra(Intent.EXTRA_TEXT, obsah.takeLast(60_000)), "Sdílet záznam")) }
            }, android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = px(4) })
            if (sVyvojem) addView(tlacitko("Vývoj") { a.startActivity(Intent(a, VyvojActivity::class.java)) },
                android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = px(4) })
            addView(tlacitko("Vymazat uložené texty") {
                AlertDialog.Builder(a).setTitle("Vymazat uložené texty?")
                    .setMessage(Uloziste.prehled(a) + "\n\nSmaže paměť přeložených vět, profil hry a záznamy. Nahrané tabulky zůstanou.")
                    .setPositiveButton("Vymazat") { _, _ -> Uloziste.vymaz(a); dlg.dismiss()
                        android.widget.Toast.makeText(a, "Vymazáno", android.widget.Toast.LENGTH_SHORT).show() }
                    .setNeutralButton("I tabulky") { _, _ -> Uloziste.vymaz(a, iTabulky = true); dlg.dismiss()
                        android.widget.Toast.makeText(a, "Vymazáno včetně tabulek", android.widget.Toast.LENGTH_SHORT).show() }
                    .setNegativeButton("Zpět", null).show()
            }, android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = px(4) })
            addView(tlacitko("Zavřít") { dlg.dismiss() })
        }
        val koren = android.widget.LinearLayout(a).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(Metro.POZADI)
            val o = if (telefon) px(12) else px(28)
            setPadding(o, o, o, o)
            addView(Metro.text(a, "Diagnostika", 22f, Metro.TEXT, 300))
            addView(posuv, android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = px(10); bottomMargin = px(10) })
            addView(dole)
        }
        dlg.setContentView(koren)
        dlg.show()
        // začít na konci (nejnovější řádky) a s výběrem na záznamu
        posuv.post { posuv.fullScroll(android.view.View.FOCUS_DOWN); posuv.requestFocus(); obnovPozici() }
    }
}
