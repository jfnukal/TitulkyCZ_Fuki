package cz.titulky.tv

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * PlayCaption na telefonu: černá obrazovka, na ní velkým písmem překlad toho, co se ve hře říká.
 * Telefon stojí u televize, displej nezhasíná. Zavřením obrazovky se spojení s PS5 ukončí.
 */
class TelefonActivity : Activity() {

    private val ui = Handler(Looper.getMainLooper())
    private val nast by lazy { Nastaveni(this) }
    private fun t(klic: String) = Texty.t(nast.jazykNeboCs, klic)
    private val KOD_TABULKA = 4711
    /** [Fuki] klíč nastavení: pozadí titulků v % (0–100) */
    private val PREF_POZADI = "pozadiTitulku"
    private val prefs by lazy { getSharedPreferences("gamelingo", MODE_PRIVATE) }

    private lateinit var titulek: TextView
    private lateinit var obalTitulku: FrameLayout
    private lateinit var posunUprav: android.widget.ScrollView
    private lateinit var napoveda: TextView
    private lateinit var stav: TextView
    private lateinit var tecka: View
    private lateinit var tvTlacitko: LinearLayout
    private lateinit var segTv: TextView
    private lateinit var segTel: TextView
    private lateinit var tip: TextView
    private lateinit var horniPanel: View
    /** Tlačítko ⇄ Xbox / ⇄ PS5 (jen když jsou zapnuté obě konzole). */
    private var prepnoutKonzoli: TextView? = null
    /** Na šířku (jen titulky v telefonu): malá zelená tečka „Titulky aktivní“ v rohu. */
    private lateinit var sirkaZnacka: LinearLayout
    private lateinit var sirkaTecka: View
    /** Telefon je otočený na šířku a titulky se ukazují jen v telefonu → černá obrazovka jen s titulky. */
    private var naSirku = false

    /** Velikosti písma – ťuknutím na text se přepínají. */
    private val VELIKOSTI = floatArrayOf(28f, 36f, 46f)
    private var velikost: Int
        get() = prefs.getInt("telefonPismo", 1).coerceIn(0, VELIKOSTI.size - 1)
        set(v) = prefs.edit().putInt("telefonPismo", v).apply()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Titulky do televize (obrazovka s posuvníky) → jen na výšku.
        // Titulky jen v telefonu → jde otočit na šířku (černá obrazovka jen s titulky).
        // Otočení obrazovku nestaví znovu (configChanges v manifestu) → spojení se nepřeruší.
        nastavOtaceni(Propojeni.Vysilac.cilIp != null)
        Propojeni.Vysilac.pozadi = prefs.getInt(PREF_POZADI, 100) / 100f
        // [Fuki] tabulky překladů načíst hned (ať je v Úpravách vidět jejich stav)
        Tabulka.nacti(getExternalFilesDir("tabulky"), hotovo = { ui.post { if (::upravy.isInitialized) obnovUpravy() } })
        Pady.hlidej(this)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        Volby.nacti(this)
        OblastTitulku.nacti(this)
        // televize musí znát polohu rámečku voleb (český seznam ukazuje vedle něj)
        Propojeni.Vysilac.trvaleOblast = Volby.zapnuto && Volby.ukazatNaTv
        Propojeni.Vysilac.upravujiOblast(Volby.oblast, ukazat = false)
        postav()
        // spuštěno rovnou na šířku (jen telefon) → hned černá obrazovka s titulky
        prepniSirku(resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
            Propojeni.Vysilac.cilIp == null)
        // spustit spojení s PS5 (bez kreslení přes jiné aplikace)
        RemotePlayService.pozastaveno = false
        TelefonTitulky.odpojeno = false
        // obrazovka jen postavená znovu (savedInstanceState) → spojení s televizí nechat, jak je
        if (savedInstanceState == null) {
            TelefonTitulky.nastav("", 0)
            TelefonTitulky.napoveda = ""
            // televize, která čeká na titulky z telefonu, se najde a připojí sama
            Propojeni.Vysilac.nastavCil(null, "")
        }
        obnovTvTlacitko()
        ui.postDelayed(hledaniTv, 500)
        // převzít PS5 – když je připojená televize, sama se odpojí
        runCatching { startForegroundService(Intent(this, RemotePlayService::class.java).putExtra(RemotePlayService.EXTRA_PREVZIT, true)) }
    }

    override fun onResume() {
        super.onResume()
        ui.removeCallbacks(obnovZive)
        ui.post(obnovZive)
        ui.removeCallbacks(obnova)
        ui.post(obnova)
    }

    override fun onPause() {
        ui.removeCallbacks(obnovZive)
        ui.removeCallbacks(obnova)
        super.onPause()
    }

    // ---------------------------------------------------------------- automatické hledání televize

    /** Uživatel ručně zvolil "jen telefon" → automaticky do televize neposílat. */
    private var rucneJenTelefon = false
    @Volatile private var hledam = false

    /**
     * Dokud se titulky neposílají do televize, každých 8 s se podívat, jestli v síti nečeká
     * televize v režimu "Titulky čte telefon". Jedna → připojit sama. Víc → nabídnout výběr
     * (jednou). Poslední vybraná televize má přednost.
     */
    private var nabidnutVyber = true
    private val hledaniTv: Runnable = object : Runnable {
        override fun run() {
            if (isFinishing) return
            if (!rucneJenTelefon && Propojeni.Vysilac.cilIp == null && !hledam) {
                hledam = true
                Propojeni.hledej(this@TelefonActivity, 2500) { seznam ->
                    ui.post {
                        hledam = false
                        if (isFinishing || rucneJenTelefon || Propojeni.Vysilac.cilIp != null) return@post
                        val posledni = prefs.getString("tvId", null)
                        val tv = seznam.firstOrNull { it.id == posledni } ?: seznam.singleOrNull()
                        when {
                            tv != null -> pripojTv(tv)
                            seznam.size > 1 && nabidnutVyber -> { nabidnutVyber = false; vyberTelevizi() }
                        }
                    }
                }
            }
            ui.postDelayed(this, 8000)
        }
    }

    private fun pripojTv(tv: Propojeni.Televize) {
        Propojeni.Vysilac.nastavCil(tv.ip, tv.jmeno)
        prefs.edit().putString("tvId", tv.id).putString("tvJmeno", tv.jmeno).apply()
        android.widget.Toast.makeText(this, t("tvPosilam").replace("{tv}", tv.jmeno), android.widget.Toast.LENGTH_LONG).show()
        obnovTvTlacitko()
    }

    /**
     * Telefon odložený (zamčený) → za 1 min spojení s PS5 ukončit.
     * Zamčený telefon totiž nedostane zprávu "PS5 si bere televize" (Wi-Fi spí) a držel by PS5
     * dál – televize by se pak nemohla připojit ("already in use").
     */
    private val odlozeno = Runnable {
        if (!isFinishing) finish()
    }

    /** PlayCaption je v pozadí (foťák, jiná aplikace…). */
    private var vPozadi = false

    /**
     * Odpojit se jen když je telefon ZAMČENÝ (displej zhasl) – ne když je jen otevřená jiná
     * aplikace (třeba foťák při natáčení videa). Zhasne displej → za 1 min konec.
     */
    private val displej = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context?, i: Intent?) {
            when (i?.action) {
                Intent.ACTION_SCREEN_OFF -> if (vPozadi) { ui.removeCallbacks(odlozeno); ui.postDelayed(odlozeno, 60_000) }
                Intent.ACTION_SCREEN_ON -> ui.removeCallbacks(odlozeno)
            }
        }
    }
    private var displejHlidam = false

    override fun onStart() {
        super.onStart()
        vPozadi = false
        ui.removeCallbacks(odlozeno)
        if (!displejHlidam) runCatching {
            registerReceiver(displej, android.content.IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_SCREEN_ON)
            })
            displejHlidam = true
        }
    }

    override fun onStop() {
        if (!isChangingConfigurations && !isFinishing) {
            vPozadi = true
            // displej už nesvítí (telefon zamčený) → za 1 min konec; svítí (jiná aplikace) → čekat na zhasnutí
            val sviti = runCatching { (getSystemService(POWER_SERVICE) as android.os.PowerManager).isInteractive }.getOrDefault(false)
            if (!sviti) ui.postDelayed(odlozeno, 60_000)
        }
        super.onStop()
    }

    /**
     * Zpět (tlačítko / gesto) dřív aplikaci zavřelo a tím i ukončilo spojení s PS5/Xboxem.
     * Teď ji jen schová do pozadí – titulky běží dál. Ukončit jde tlačítkem v aplikaci.
     */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (RemotePlayService.aktivni) moveTaskToBack(true)
        else @Suppress("DEPRECATION") super.onBackPressed()
    }

    override fun onDestroy() {
        if (displejHlidam) { runCatching { unregisterReceiver(displej) }; displejHlidam = false }
        if (isFinishing) zastav()
        super.onDestroy()
    }

    // [Fuki] ztlumení displeje: jen když titulky jdou do TV a na telefon 30 s nikdo nesáhne
    private var tlumitDisplej = false
    private val TLUMIT_PO = 30_000L
    private val ztlum = Runnable { if (tlumitDisplej) nastavJas(true) }
    private fun nastavJas(tlumeny: Boolean) {
        window.attributes = window.attributes.apply {
            screenBrightness = if (tlumeny) 0.05f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        }
    }
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (tlumitDisplej) {
            if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) nastavJas(false)
            ui.removeCallbacks(ztlum)
            ui.postDelayed(ztlum, TLUMIT_PO)
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun zastav() {
        // televizi říct, ať titulek schová, a přestat jí posílat
        Propojeni.Vysilac.zmena("", 0, "")
        Propojeni.Vysilac.nastavCil(null, "")
        runCatching {
            startService(Intent(this, RemotePlayService::class.java).setAction(RemotePlayService.ACTION_STOP))
        }
    }

    private fun obnovTvTlacitko() {
        if (!::tvTlacitko.isInitialized) return
        // přepínač "Titulky: 📺 Televize | 📱 Telefon" – zeleně to, kde se titulky právě ukazují
        val naTv = Propojeni.Vysilac.cilIp != null
        fun pozadiSeg(vybrano: Boolean) = if (vybrano) android.graphics.drawable.LayerDrawable(arrayOf(
            android.graphics.drawable.ColorDrawable(Metro.MODRA_VYBER), android.graphics.drawable.ColorDrawable(Metro.ZLUTA))).apply {
            setLayerGravity(1, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL); setLayerHeight(1, dp(3))
        } else android.graphics.drawable.ColorDrawable(Color.TRANSPARENT)
        segTv.background = pozadiSeg(naTv)
        segTel.background = pozadiSeg(!naTv)
        segTv.setTextColor(if (naTv) Color.WHITE else Metro.OBRYS)
        segTel.setTextColor(if (!naTv) Color.WHITE else Metro.OBRYS)
        segTv.alpha = 1f
        segTel.alpha = 1f
    }

    /** Přepínač: titulky do televize. Poslední televize (nebo jediná v síti) se připojí hned. */
    private fun prepniNaTv() {
        rucneJenTelefon = false
        if (Propojeni.Vysilac.cilIp != null) return
        segTv.alpha = 0.85f
        Propojeni.hledej(this, 2500) { seznam ->
            ui.post {
                if (isFinishing || rucneJenTelefon || Propojeni.Vysilac.cilIp != null) return@post
                val posledni = prefs.getString("tvId", null)
                val tv = seznam.firstOrNull { it.id == posledni } ?: seznam.singleOrNull()
                when {
                    tv != null -> pripojTv(tv)
                    seznam.size > 1 -> vyberTelevizi()
                    else -> android.widget.Toast.makeText(this, t("tvNenalezenaToast"), android.widget.Toast.LENGTH_LONG).show()
                }
                obnovTvTlacitko()
            }
        }
    }

    /** Přepínač: titulky jen v telefonu (televizi titulek schovat a přestat jí posílat). */
    private fun prepniNaTelefon() {
        rucneJenTelefon = true
        Propojeni.Vysilac.zmena("", 0, "")
        Propojeni.Vysilac.nastavCil(null, "")
        obnovTvTlacitko()
    }

    /** Najít televize s PlayCaption v síti a vybrat, kam posílat titulky. */
    private fun vyberTelevizi() {
        val hledam = android.app.AlertDialog.Builder(this)
            .setTitle(t("tvVyber"))
            .setMessage(t("tvHledam"))
            .show()
        Propojeni.hledej(this) { seznam ->
            ui.post {
                if (isFinishing) return@post
                runCatching { hledam.dismiss() }
                val polozky = seznam.map { "📺  " + it.jmeno } + ("📱  " + t("tvJenTelefon"))
                android.app.AlertDialog.Builder(this)
                    .setTitle(t(if (seznam.isEmpty()) "tvNenalezena" else "tvVyber"))
                    .setItems(polozky.toTypedArray()) { _, i ->
                        val tv = seznam.getOrNull(i)
                        if (tv != null) {
                            rucneJenTelefon = false
                            pripojTv(tv)
                        } else {
                            rucneJenTelefon = true
                            Propojeni.Vysilac.zmena("", 0, "")
                            Propojeni.Vysilac.nastavCil(null, "")
                        }
                        obnovTvTlacitko()
                    }
                    .show()
            }
        }
    }

    private fun dp(x: Int) = (x * resources.displayMetrics.density).toInt()

    private fun zaobleni(barva: Int) = GradientDrawable().apply {
        setColor(barva)
        cornerRadius = 999f * resources.displayMetrics.density
    }

    // ---------------------------------------------------------------- poloha titulků na televizi

    /** (střed X, spodní hrana Y, šířka, velikost písma) – stejné jako výchozí poloha na televizi. */
    private var rozvrh = floatArrayOf(0.5f, 0.93f, 0.62f, 1f)
    private lateinit var upravy: LinearLayout
    /** Zámeček zeleného boxu (poloha českých titulků na televizi). */
    private var zelenaZamcena: Boolean
        get() = prefs.getBoolean("rozvrhZamek", false)
        set(v) = prefs.edit().putBoolean("rozvrhZamek", v).apply()

    /** Kulaté tlačítko se zámečkem: zamčeno = plné barvou, odemčeno = jen obrys. */
    private fun zamek(barva: Int, zamceno: Boolean, prepni: () -> Unit) = TextView(this).apply {
        text = if (zamceno) "🔒" else "🔓"
        textSize = 20f
        gravity = Gravity.CENTER
        alpha = if (zamceno) 1f else 0.55f
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            if (zamceno) setColor(barva) else { setColor(Color.TRANSPARENT); setStroke(dp(2), barva) }
        }
        setOnClickListener { prepni(); obnovUpravy() }
    }

    private fun nactiRozvrh() {
        rozvrh = floatArrayOf(
            prefs.getFloat("tvX", 0.5f), prefs.getFloat("tvY", 0.93f),
            prefs.getFloat("tvSirka", 0.62f), prefs.getFloat("tvPismo", 1f))
    }

    private fun ulozAPosliRozvrh(ukazat: Boolean = true) {
        prefs.edit().putFloat("tvX", rozvrh[0]).putFloat("tvY", rozvrh[1])
            .putFloat("tvSirka", rozvrh[2]).putFloat("tvPismo", rozvrh[3]).apply()
        Propojeni.Vysilac.nastavRozvrh(rozvrh.copyOf())
        // na televizi ukázat ukázkový titulek, ať je vidět, kam se posunul
        if (ukazat) Propojeni.Vysilac.ukazka(t("tvUkazka"))
    }

    /** [Fuki] Rozbalené „Pokročilé“ v úpravách. */
    private var pokrocile = false
    /** [Fuki] Živé údaje v úpravách: hra z Xboxu a rychlost překladu (obnovuje se každou sekundu). */
    private var hraText: TextView? = null
    private var rychlostText: TextView? = null
    private val obnovZive = object : Runnable {
        override fun run() {
            val hra = RemotePlayService.hraNaXboxu
            hraText?.text = if (hra.isNotEmpty()) "Hraje se: $hra (podle Xboxu) – tabulka a AI se nastavily samy." else "Hru zatím Xbox nehlásí – AI ji pozná podle titulků."
            rychlostText?.text = buildString {
                append("Poslední titulek: ").append(RemotePlayService.rychlostPosledni.ifEmpty { "zatím nic" })
                append("\nZa posledních 30 s: ").append(RemotePlayService.rychlostPrumer.ifEmpty { "zatím nic" })
            }
            ui.postDelayed(this, 1000)
        }
    }

    /** [Fuki] Volba v řadě (Dole / Uprostřed / Nahoře …): vybraná = světlejší modrá se žlutou lištou. */
    private fun volbaDl(popis: String, zvoleno: Boolean, akce: () -> Unit) = TextView(this).apply {
        text = popis
        gravity = Gravity.CENTER
        textSize = 14f
        typeface = Metro.pismo(this@TelefonActivity, if (zvoleno) 600 else 400)
        setTextColor(if (zvoleno) Color.WHITE else Metro.TEXT_NA_MODRE)
        background = if (zvoleno) android.graphics.drawable.LayerDrawable(arrayOf(
            android.graphics.drawable.ColorDrawable(Metro.MODRA_VYBER), android.graphics.drawable.ColorDrawable(Metro.ZLUTA))).apply {
            setLayerGravity(1, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL); setLayerHeight(1, dp(4))
        } else Metro.pozadi(Metro.MODRA, Metro.MODRA_VYBER)
        setPadding(dp(6), dp(14), dp(6), dp(14))
        setOnClickListener { akce() }
    }

    /** Řada voleb vedle sebe (mezery 4 dp). */
    private fun radekVoleb(polozky: List<Pair<String, Boolean>>, akce: (Int) -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        polozky.forEachIndexed { i, (popis, zvoleno) ->
            addView(volbaDl(popis, zvoleno) { akce(i) }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) leftMargin = dp(4) })
        }
    }

    /** Větší dlaždice s nadpisem a stavem (AI, tabulky, pokročilé). */
    private fun infoDl(nadpis: String, stav: String, barvaStavu: Int, sedaDl: Boolean = false, akce: () -> Unit) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = if (sedaDl) Metro.pozadi(Metro.SEDA_DLAZDICE, Metro.SEDA_VYBER) else Metro.pozadi(Metro.MODRA, Metro.MODRA_VYBER)
        setPadding(dp(14), dp(12), dp(14), dp(12))
        addView(Metro.text(this@TelefonActivity, nadpis, 15f, Color.WHITE, 600))
        addView(Metro.text(this@TelefonActivity, stav, 12f, barvaStavu, 400), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(4) })
        isClickable = true
        setOnClickListener { akce() }
    }

    /**
     * [Fuki] Úpravy (titulky jdou do televize), zjednodušené:
     * náhled herní scény s titulkem, poloha / velikost / pozadí jedním klepnutím, co překládat,
     * AI a tabulky; rámečky a jemné posuvníky jen v „Pokročilých“.
     */
    private fun postavUpravy(): LinearLayout {
        nactiRozvrh()
        rychlostText = null
        val c = this
        val obal = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(4), dp(16), dp(16))
            setBackgroundColor(Metro.POZADI)
            visibility = View.GONE
        }
        fun stitek(s: String) = obal.addView(Metro.text(c, s, 12f, Metro.OBRYS, 600),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(18); bottomMargin = dp(6) })
        fun napoveda(s: String) = obal.addView(Metro.text(c, s, 12f, Metro.OBRYS, 400).apply { setLineSpacing(0f, 1.2f) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(6) })
        fun pridej(v: View, nahore: Int = 0) = obal.addView(v,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(nahore) })

        // ---- náhled: herní scéna a na ní titulek tam, kde bude na televizi
        val nahled = FrameLayout(this)
        nahled.addView(ImageView(this).apply { setImageResource(R.drawable.nahled_hra); scaleType = ImageView.ScaleType.FIT_XY },
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val ukazka = TextView(this).apply {
            text = "Musíme přes hory, než padne tma."
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Metro.pismo(c, 600)
            setShadowLayer(4f, 0f, 1f, Color.BLACK)
            includeFontPadding = false
        }
        nahled.addView(ukazka, FrameLayout.LayoutParams(10, ViewGroup.LayoutParams.WRAP_CONTENT))
        val ramecek = SrafovanyRamecek(this, t("volbyRamecek")).apply { visibility = if (pokrocile && Volby.zapnuto) View.VISIBLE else View.GONE }
        nahled.addView(ramecek, FrameLayout.LayoutParams(10, 10))
        val modry = SrafovanyRamecek(this, t("titOblRamecek"), OblastTitulku.MODRA).apply { visibility = if (pokrocile && OblastTitulku.rucne) View.VISIBLE else View.GONE }
        nahled.addView(modry, FrameLayout.LayoutParams(10, 10))
        fun prekresli() {
            val w = nahled.width.toFloat(); val h = nahled.height.toFloat()
            if (w <= 0 || h <= 0) return
            val bw = (rozvrh[2] * w).coerceAtLeast(dp(40).toFloat())
            ukazka.textSize = (7.5f * rozvrh[3]).coerceIn(5f, 18f)
            val a = prefs.getInt(PREF_POZADI, 100) / 100f
            ukazka.setBackgroundColor(Color.argb((a * 255).toInt(), 0, 0, 0))
            ukazka.setPadding(dp(6), dp(3), dp(6), dp(3))
            ukazka.measure(View.MeasureSpec.makeMeasureSpec(bw.toInt(), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val bh = ukazka.measuredHeight.toFloat()
            ukazka.layoutParams = (ukazka.layoutParams as FrameLayout.LayoutParams).apply {
                width = bw.toInt()
                leftMargin = (rozvrh[0] * w - bw / 2).coerceIn(0f, w - bw).toInt()
                topMargin = (rozvrh[1] * h - bh).coerceIn(0f, (h - bh).coerceAtLeast(0f)).toInt()
            }
            val o = Volby.oblast
            ramecek.layoutParams = (ramecek.layoutParams as FrameLayout.LayoutParams).apply {
                width = ((o[2] - o[0]) * w).toInt().coerceAtLeast(10); height = ((o[3] - o[1]) * h).toInt().coerceAtLeast(10)
                leftMargin = (o[0] * w).toInt(); topMargin = (o[1] * h).toInt()
            }
            val m = OblastTitulku.oblast
            modry.layoutParams = (modry.layoutParams as FrameLayout.LayoutParams).apply {
                width = ((m[2] - m[0]) * w).toInt().coerceAtLeast(10); height = ((m[3] - m[1]) * h).toInt().coerceAtLeast(10)
                leftMargin = (m[0] * w).toInt(); topMargin = (m[1] * h).toInt()
            }
        }
        // výška = 16:9 podle šířky
        nahled.addOnLayoutChangeListener { v, l, _, r, _, ol, _, or_, _ ->
            val w = r - l
            val chci = w * 9 / 16
            if (v.layoutParams.height != chci) { v.layoutParams = v.layoutParams.apply { height = chci }; v.requestLayout() }
            if (w != or_ - ol) v.post { prekresli() } else prekresli()
        }
        var posledniPoslani = 0L
        var tahamVolby = false
        var tahamModry = false
        var chytX = 0f; var chytY = 0f
        nahled.setOnTouchListener { v, e ->
            // prst v náhledu posouvá titulek (a v Pokročilých i rámečky) – stránka se při tom nehýbe
            if (e.action == android.view.MotionEvent.ACTION_DOWN) { v.parent?.requestDisallowInterceptTouchEvent(true); ui.removeCallbacks(konecUprav) }
            if (e.action == android.view.MotionEvent.ACTION_UP || e.action == android.view.MotionEvent.ACTION_CANCEL) ui.postDelayed(konecUprav, 1000)
            val w = v.width.toFloat(); val h = v.height.toFloat()
            if (w <= 0 || h <= 0) return@setOnTouchListener true
            val fx = e.x / w; val fy = e.y / h
            if (e.action == android.view.MotionEvent.ACTION_DOWN) {
                val o = Volby.oblast; val m = OblastTitulku.oblast
                tahamModry = pokrocile && OblastTitulku.rucne && fx in m[0]..m[2] && fy in m[1]..m[3]
                tahamVolby = !tahamModry && pokrocile && Volby.zapnuto && fx in o[0]..o[2] && fy in o[1]..o[3]
                if (tahamModry) { chytX = fx - m[0]; chytY = fy - m[1] } else { chytX = fx - o[0]; chytY = fy - o[1] }
            }
            val ted = System.currentTimeMillis()
            val poslat = e.action == android.view.MotionEvent.ACTION_UP || ted - posledniPoslani > 150
            when {
                tahamModry -> {
                    val m = OblastTitulku.oblast.copyOf(); val mw = m[2] - m[0]; val mh = m[3] - m[1]
                    m[0] = (fx - chytX).coerceIn(0f, 1f - mw); m[1] = (fy - chytY).coerceIn(0f, 1f - mh); m[2] = m[0] + mw; m[3] = m[1] + mh
                    OblastTitulku.oblast = m; prekresli()
                    if (poslat) { posledniPoslani = ted; ulozAPosliOblastTitulku() }
                }
                tahamVolby -> {
                    val o = Volby.oblast.copyOf(); val ow = o[2] - o[0]; val oh = o[3] - o[1]
                    o[0] = (fx - chytX).coerceIn(0f, 1f - ow); o[1] = (fy - chytY).coerceIn(0f, 1f - oh); o[2] = o[0] + ow; o[3] = o[1] + oh
                    Volby.oblast = o; prekresli()
                    if (poslat) { posledniPoslani = ted; ulozAPosliVolby() }
                }
                else -> {
                    rozvrh[0] = fx.coerceIn(0f, 1f)
                    val pulBoxu = (0.06f * rozvrh[3]).coerceIn(0.03f, 0.17f)
                    rozvrh[1] = (fy + pulBoxu).coerceIn(0.1f, 1f)
                    prekresli()
                    if (poslat) { posledniPoslani = ted; ulozAPosliRozvrh() }
                    if (e.action == android.view.MotionEvent.ACTION_UP) obnovUpravy()
                }
            }
            true
        }
        obal.addView(nahled, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(180)).apply { topMargin = dp(8) })

        // ---- poloha
        stitek("Kde mají být titulky")
        val polohy = listOf("Dole" to 0.93f, "Uprostřed" to 0.62f, "Nahoře" to 0.22f)
        pridej(radekVoleb(polohy.map { (n, y) -> n to (kotlin.math.abs(rozvrh[1] - y) < 0.03f && kotlin.math.abs(rozvrh[0] - 0.5f) < 0.03f) }) { i ->
            rozvrh[0] = 0.5f; rozvrh[1] = polohy[i].second
            ulozAPosliRozvrh(); ui.removeCallbacks(konecUprav); ui.postDelayed(konecUprav, 2000); obnovUpravy()
        })
        napoveda("Nebo titulek posuň prstem přímo v náhledu.")

        // ---- velikost písma
        stitek("Velikost písma")
        val velikosti = listOf("Malé" to 0.8f, "Střední" to 1f, "Velké" to 1.35f)
        pridej(radekVoleb(velikosti.map { (n, v) -> n to (kotlin.math.abs(rozvrh[3] - v) < 0.06f) }) { i ->
            rozvrh[3] = velikosti[i].second
            ulozAPosliRozvrh(); ui.removeCallbacks(konecUprav); ui.postDelayed(konecUprav, 2000); obnovUpravy()
        })

        // ---- pozadí za titulky
        stitek("Pozadí za titulky")
        val pozadi = listOf("Žádné" to 0, "Poloviční" to 50, "Plné" to 100)
        val tedPozadi = prefs.getInt(PREF_POZADI, 100)
        pridej(radekVoleb(pozadi.map { (n, v) -> n to (kotlin.math.abs(tedPozadi - v) <= 10) }) { i ->
            prefs.edit().putInt(PREF_POZADI, pozadi[i].second).apply()
            Propojeni.Vysilac.nastavPozadi(pozadi[i].second / 100f)
            ulozAPosliRozvrh(); ui.removeCallbacks(konecUprav); ui.postDelayed(konecUprav, 2000); obnovUpravy()
        })

        // ---- jak dlouho nechat titulky (chytrá doba podle délky × násobek)
        stitek("Jak dlouho nechat titulky")
        val doby = listOf("Kratší" to 0.7f, "Normálně" to 1f, "Déle (děti)" to 1.6f)
        val tedDoba = prefs.getFloat(RemotePlayService.PREF_DOBA, 1f)
        pridej(radekVoleb(doby.map { (n, v) -> n to (kotlin.math.abs(tedDoba - v) < 0.05f) }) { i ->
            prefs.edit().putFloat(RemotePlayService.PREF_DOBA, doby[i].second).apply()
            obnovUpravy()
        })
        napoveda("Krátká replika zmizí rychle, dlouhá vydrží déle. „Déle“ se hodí, když čtení nestíhají děti.")

        // ---- česky nahlas
        stitek("Česky nahlas")
        val hlasZap = Hlas.zapnuto(this)
        val hlasKde = Hlas.kde(this)
        pridej(radekVoleb(listOf("Vypnuto" to !hlasZap, "Mluví televize" to (hlasZap && hlasKde == "tv"), "Mluví telefon" to (hlasZap && hlasKde == "tel"))) { i ->
            prefs.edit().putBoolean(Hlas.PREF_ZAP, i != 0).apply {
                if (i == 1) putString(Hlas.PREF_KDE, "tv"); if (i == 2) putString(Hlas.PREF_KDE, "tel")
            }.apply()
            if (i == 1) Propojeni.Vysilac.mluv("Ahoj, takhle budu číst české titulky.", Hlas.rychlost(this))
            if (i == 2) Hlas.zkouska(this)
            obnovUpravy()
        })
        if (hlasZap) {
            val rych = listOf("Pomaleji" to 0.85f, "Normálně" to 1f, "Rychleji" to 1.25f)
            val tedR = Hlas.rychlost(this)
            pridej(radekVoleb(rych.map { (n, v) -> n to (kotlin.math.abs(tedR - v) < 0.05f) }) { i ->
                prefs.edit().putFloat(Hlas.PREF_RYCHLOST, rych[i].second).apply()
                if (hlasKde == "tv") Propojeni.Vysilac.mluv("Takhle rychle budu mluvit.", rych[i].second) else Hlas.rekni(this, "Takhle rychle budu mluvit.", rych[i].second)
                obnovUpravy()
            }, 4)
            val hl = listOf("Tiše" to 0.35f, "Středně" to 0.65f, "Naplno" to 1f)
            val tedH = Hlas.hlasitost(this)
            pridej(radekVoleb(hl.map { (n, v) -> n to (kotlin.math.abs(tedH - v) < 0.05f) }) { i ->
                prefs.edit().putFloat(Hlas.PREF_HLASITOST, hl[i].second).apply()
                if (hlasKde == "tv") Propojeni.Vysilac.mluv("Takhle nahlas budu mluvit.", Hlas.rychlost(this), hl[i].second)
                else Hlas.rekni(this, "Takhle nahlas budu mluvit.", Hlas.rychlost(this), hl[i].second)
                obnovUpravy()
            }, 4)
            if (Hlas.chyba.isNotEmpty() && hlasKde == "tel") pridej(volbaDl(Hlas.chyba + " – nainstalovat", false) { Hlas.nainstalujCestinu(this) }, 4)
        }
        napoveda("Hlasitost je vůči hlasitosti zařízení – „Naplno“ = jako zvuk televize / telefonu. Přeložené titulky čte český hlas (jména postav vynechá). Když televize nemluví, chybí v ní český hlas – nastav ho v TV: Nastavení → Systém → Převod textu na řeč.")

        // ---- co překládat navíc (dialogy se překládají vždy)
        stitek("Co překládat navíc k dialogům")
        val rezim = if (!Volby.zapnuto) 0 else if (Volby.rezimText) 2 else if (Volby.rezimMenu) 3 else 1
        fun nastavRezim(k: Int) {
            if (k == rezim) return
            Volby.zapnuto = k != 0
            Volby.rezimText = k == 2
            Volby.rezimMenu = k == 3
            Volby.uloz(this)
            Propojeni.Vysilac.trvaleOblast = Volby.zapnuto && Volby.ukazatNaTv
            Propojeni.Vysilac.nastavVolby(emptyList(), -1)
            if (!Volby.zapnuto) Propojeni.Vysilac.upravujiOblast(Volby.oblast, ukazat = false)
            obnovUpravy()
        }
        pridej(radekVoleb(listOf("Nic" to (rezim == 0), "Volby odpovědí" to (rezim == 1))) { nastavRezim(it) })
        pridej(radekVoleb(listOf("Dopisy a SMS" to (rezim == 2), "Menu a deník" to (rezim == 3))) { nastavRezim(it + 2) }, 4)
        napoveda(when (rezim) {
            1 -> t("rezimVolbyNap"); 2 -> t("rezimTextNap"); 3 -> t("rezimMenuNap")
            else -> "Překládají se jen titulky rozhovorů."
        })

        // ---- AI a tabulky
        stitek("Překlad")
        val aiKlic = AiPrekladac.klic(this)
        val aiZap = aiKlic.isNotEmpty() && AiPrekladac.povoleno(this)
        val radekAi = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        radekAi.addView(infoDl("AI překlad", when { aiKlic.isEmpty() -> "Bez klíče – nastavit"; aiZap -> "Zapnuto"; else -> "Vypnuto" },
            if (aiZap) Metro.ZLUTA else Metro.TEXT_NA_MODRE) { nastaveniAi() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        radekAi.addView(infoDl("Tabulky", if (Tabulka.pocet > 0) "%,d vět".format(Tabulka.pocet).replace(',', ' ') + " · nahrát další" else "Nahrát tabulku",
            if (Tabulka.pocet > 0) Metro.ZLUTA else Metro.TEXT_NA_MODRE) { vyberTabulku() },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { leftMargin = dp(4) })
        pridej(radekAi)
        hraText = Metro.text(c, "", 12f, Metro.OBRYS, 400).apply { setLineSpacing(0f, 1.2f) }
        pridej(hraText!!, 6)
        pridej(volbaDl("Změnil jsem hru", false) {
            RemotePlayService.zmenaHry(this)
            android.widget.Toast.makeText(this, "Zjišťuji novou hru…", android.widget.Toast.LENGTH_SHORT).show()
        }, 6)
        if (aiZap) {
            stitek("AI překládá")
            val usp = AiPrekladac.usporne(this)
            pridej(radekVoleb(listOf("Každý titulek" to !usp, "Jen delší věty (úsporně)" to usp)) { i ->
                prefs.edit().putBoolean(AiPrekladac.PREF_USPORNE, i == 1).apply(); obnovUpravy()
            })
            napoveda("Úsporně: krátké repliky („Yeah.“, „Come on!“) přeloží Google zdarma, AI jen věty od 6 slov. Ušetří zhruba polovinu dotazů.")
        }

        // ---- pokročilé
        pridej(infoDl(if (pokrocile) "Pokročilé ▴" else "Pokročilé ▾",
            "Šířka titulků, kde aplikace čte titulky, rámeček voleb", Metro.OBRYS, sedaDl = true) {
            pokrocile = !pokrocile; obnovUpravy()
        }, 22)
        if (pokrocile) {
            fun posuvnik(popis: String, od: Int, doo: Int, hodnota: () -> Int, nastav: (Int) -> Unit, posli: () -> Unit = { ulozAPosliRozvrh() }) {
                val radek = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val st = Metro.text(c, "", 13f, Metro.TEXT, 400)
                fun popisek() { st.text = "$popis ${hodnota()} %" }
                popisek()
                radek.addView(st, LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.WRAP_CONTENT))
                radek.addView(android.widget.SeekBar(this).apply {
                    max = doo - od
                    progress = hodnota() - od
                    progressTintList = android.content.res.ColorStateList.valueOf(Metro.ZLUTA)
                    thumbTintList = android.content.res.ColorStateList.valueOf(Metro.ZLUTA)
                    setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(sb: android.widget.SeekBar, p: Int, odUzivatele: Boolean) {
                            if (!odUzivatele) return
                            nastav(p + od); popisek(); prekresli()
                            val t2 = System.currentTimeMillis()
                            if (t2 - posledniPoslani > 150) { posledniPoslani = t2; posli() }
                        }
                        override fun onStartTrackingTouch(sb: android.widget.SeekBar) { ui.removeCallbacks(konecUprav) }
                        override fun onStopTrackingTouch(sb: android.widget.SeekBar) { posli(); ui.postDelayed(konecUprav, 1000) }
                    })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                pridej(radek, 8)
            }
            stitek("Rychlost překladu")
            rychlostText = Metro.text(c, "", 12f, Metro.TEXT, 400).apply { setLineSpacing(0f, 1.3f) }
            pridej(rychlostText!!)
            napoveda("tabulka = hotový lidský překlad · paměť = už přeloženo dřív · AI = Gemini · google / edge / tv = strojový překlad")
            napoveda("V náhledu teď jdou posouvat i rámečky: modrý = kde aplikace čte titulky, žlutý = kde hra ukazuje volby.")
            posuvnik(t("tvSirka"), 20, 100, { (rozvrh[2] * 100).toInt() }, { rozvrh[2] = it / 100f })
            // [Fuki] pozadí za titulky ručně po 5 % (0 % = úplně průhledné, 100 % = plně černé)
            posuvnik("Pozadí (krytí)", 0, 100, { prefs.getInt(PREF_POZADI, 100) }, {
                val v = (it / 5) * 5
                prefs.edit().putInt(PREF_POZADI, v).apply()
                Propojeni.Vysilac.nastavPozadi(v / 100f)
            })
            napoveda("0 % = bez pozadí, jen písmo se stínem · 100 % = plně černý pruh. Na televizi se změna ukáže hned.")

            stitek("Kde aplikace čte titulky")
            pridej(radekVoleb(listOf("Automaticky" to !OblastTitulku.rucne, "Ručně (modrý rámeček)" to OblastTitulku.rucne)) { i ->
                val rucne = i == 1
                if (rucne == OblastTitulku.rucne) return@radekVoleb
                OblastTitulku.rucne = rucne
                OblastTitulku.zamceno = false
                OblastTitulku.uloz(this@TelefonActivity)
                if (rucne) { ulozAPosliOblastTitulku(); ui.removeCallbacks(konecUprav); ui.postDelayed(konecUprav, 3000) }
                else Propojeni.Vysilac.oblastTitulku(OblastTitulku.oblast, ukazat = false)
                obnovUpravy()
            })
            if (OblastTitulku.rucne) {
                fun zmenModry(sirka: Float?, vyska: Float?) {
                    val o = OblastTitulku.oblast.copyOf()
                    if (sirka != null) { val cx = (o[0] + o[2]) / 2; o[0] = (cx - sirka / 2).coerceIn(0f, 1f - sirka); o[2] = o[0] + sirka }
                    if (vyska != null) { val cy = (o[1] + o[3]) / 2; o[1] = (cy - vyska / 2).coerceIn(0f, 1f - vyska); o[3] = o[1] + vyska }
                    OblastTitulku.oblast = o
                }
                posuvnik(t("titOblSirka"), 20, 100, { ((OblastTitulku.oblast[2] - OblastTitulku.oblast[0]) * 100).toInt() },
                    { zmenModry(it / 100f, null) }, { ulozAPosliOblastTitulku() })
                posuvnik(t("titOblVyska"), 5, 60, { ((OblastTitulku.oblast[3] - OblastTitulku.oblast[1]) * 100).toInt() },
                    { zmenModry(null, it / 100f) }, { ulozAPosliOblastTitulku() })
            }

            if (Volby.zapnuto) {
                stitek("Žlutý rámeček (kde hra ukazuje volby)")
                pridej(radekVoleb(listOf("Na TV jen při posouvání" to !Volby.ukazatNaTv, "Na TV pořád" to Volby.ukazatNaTv)) { i ->
                    val ano = i == 1
                    if (ano == Volby.ukazatNaTv) return@radekVoleb
                    Volby.ukazatNaTv = ano
                    Volby.zamceno = false
                    Volby.uloz(this@TelefonActivity)
                    Propojeni.Vysilac.nastavTrvaleOblast(Volby.ukazatNaTv)
                    obnovUpravy()
                })
                fun zmenRamecek(sirka: Float?, vyska: Float?) {
                    val o = Volby.oblast.copyOf()
                    if (sirka != null) { val cx = (o[0] + o[2]) / 2; o[0] = (cx - sirka / 2).coerceIn(0f, 1f - sirka); o[2] = o[0] + sirka }
                    if (vyska != null) { val cy = (o[1] + o[3]) / 2; o[1] = (cy - vyska / 2).coerceIn(0f, 1f - vyska); o[3] = o[1] + vyska }
                    Volby.oblast = o
                }
                posuvnik(t("volbySirka"), 10, 100, { ((Volby.oblast[2] - Volby.oblast[0]) * 100).toInt() },
                    { zmenRamecek(it / 100f, null) }, { ulozAPosliVolby() })
                posuvnik(t("volbyVyska"), 10, 100, { ((Volby.oblast[3] - Volby.oblast[1]) * 100).toInt() },
                    { zmenRamecek(null, it / 100f) }, { ulozAPosliVolby() })
            }

            pridej(Metro.text(c, "Obnovit výchozí vzhled titulků", 13f, Metro.PRIMARNI, 600).apply {
                setPadding(0, dp(12), 0, dp(4))
                setOnClickListener {
                    rozvrh = floatArrayOf(0.5f, 0.93f, 0.62f, 1f)
                    prefs.edit().putInt(PREF_POZADI, 100).apply()
                    Propojeni.Vysilac.nastavPozadi(1f)
                    ulozAPosliRozvrh(); obnovUpravy()
                }
            }, 10)
        }

        // podpisová značka Fuki – patička
        pridej(FukiZnacka.podpis(this), 22)
        return obal
    }

    /** [Fuki] Výběr tabulky překladů (systémový výběr souboru – Stažené soubory, Disk…). */
    private fun vyberTabulku() {
        val i = android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(android.content.Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        runCatching { startActivityForResult(i, KOD_TABULKA) }
            .onFailure { android.widget.Toast.makeText(this, t("tabulkaChyba").replace("{e}", it.message ?: ""), android.widget.Toast.LENGTH_LONG).show() }
    }

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != KOD_TABULKA || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        // název souboru (kvůli koncovce .tsv / .tsv.gz)
        val jmeno = runCatching {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "tabulka.tsv"
        if (!jmeno.endsWith(".tsv") && !jmeno.endsWith(".tsv.gz")) {
            android.widget.Toast.makeText(this, t("tabulkaSpatna"), android.widget.Toast.LENGTH_LONG).show()
            return
        }
        android.widget.Toast.makeText(this, t("tabulkaNahravam"), android.widget.Toast.LENGTH_SHORT).show()
        val slozka = getExternalFilesDir("tabulky") ?: java.io.File(filesDir, "tabulky")
        Thread {
            val chyba = runCatching {
                slozka.mkdirs()
                val cil = java.io.File(slozka, jmeno.replace(Regex("[^A-Za-z0-9._-]"), "_"))
                contentResolver.openInputStream(uri)!!.use { vstup -> cil.outputStream().use { vstup.copyTo(it) } }
            }.exceptionOrNull()
            ui.post {
                if (chyba != null) {
                    android.widget.Toast.makeText(this, t("tabulkaChyba").replace("{e}", chyba.message ?: ""), android.widget.Toast.LENGTH_LONG).show()
                    return@post
                }
                Tabulka.znovuNacti(slozka, hotovo = {
                    ui.post {
                        android.widget.Toast.makeText(this, t("tabulkaHotovo").replace("{n}", Tabulka.pocet.toString()), android.widget.Toast.LENGTH_LONG).show()
                        if (::upravy.isInitialized) obnovUpravy()
                    }
                })
            }
        }.start()
    }

    /** [Fuki] Okno: zadat / změnit klíč ke Gemini, zapnout / vypnout AI překlad. */
    private fun nastaveniAi() {
        val pole = android.widget.EditText(this).apply {
            hint = t("aiKlic")
            setText(prefs.getString(AiPrekladac.PREF_KLIC, "") ?: "")
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        }
        val ramec = FrameLayout(this).apply { setPadding(dp(20), dp(8), dp(20), 0); addView(pole) }
        android.app.AlertDialog.Builder(this)
            .setTitle(t("aiNadpis"))
            .setMessage(t("aiNap"))
            .setView(ramec)
            .setPositiveButton(t("aiUlozit")) { _, _ ->
                prefs.edit().putString(AiPrekladac.PREF_KLIC, pole.text.toString().trim())
                    .putBoolean(AiPrekladac.PREF_ZAP, true).apply()
                obnovUpravy()
            }
            .setNeutralButton(t("aiVypnout")) { _, _ ->
                prefs.edit().putBoolean(AiPrekladac.PREF_ZAP, false).apply()
                obnovUpravy()
            }
            .setNegativeButton(t("aiZrusit"), null)
            .show()
    }

    /** Prst puštěn → zelený i žlutý rámeček na televizi schovat (pokud žlutý nemá svítit pořád). */
    private val konecUprav = Runnable {
        Propojeni.Vysilac.konecUkazky()
        Propojeni.Vysilac.konecOblasti()
        Propojeni.Vysilac.oblastTitulku(OblastTitulku.oblast, ukazat = false)
    }

    /** Uložit modrý rámeček (oblast titulků) a ukázat ho na televizi (do puštění prstu). */
    private fun ulozAPosliOblastTitulku() {
        OblastTitulku.uloz(this)
        Propojeni.Vysilac.oblastTitulku(OblastTitulku.oblast, ukazat = true)
    }

    /** Uložit rámeček voleb a ukázat ho na televizi (žlutý šrafovaný, do puštění prstu). */
    private fun ulozAPosliVolby() {
        Volby.uloz(this)
        Propojeni.Vysilac.upravujiOblast(Volby.oblast)
    }

    private fun obnovUpravy() {
        // znovu postavit (posuvníky se nastaví na nové hodnoty)
        val rodic = upravy.parent as? LinearLayout ?: return
        val i = rodic.indexOfChild(upravy)
        val viditelne = upravy.visibility
        rodic.removeView(upravy)
        upravy = postavUpravy().apply { visibility = viditelne }
        rodic.addView(upravy, i, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    private fun postav() {
        upravy = postavUpravy()
        // horní lišta: logo, stav, Zastavit
        tecka = View(this).apply { background = zaobleni(Color.GRAY) }
        stav = TextView(this).apply {
            textSize = 13f
            typeface = Metro.pismo(this@TelefonActivity, 400)
            setTextColor(Metro.TEXT)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val zastavit = TextView(this).apply {
            text = t("zastavit")
            textSize = 14f
            typeface = Metro.pismo(this@TelefonActivity, 600)
            setTextColor(Color.WHITE)
            background = Metro.pozadi(Metro.CERVENA, Metro.CERVENA_VYBER)
            setPadding(dp(14), dp(9), dp(14), dp(9))
            setOnClickListener { finish() }
        }
        // kde ukazovat titulky: dvě volby vedle sebe (jedním klepnutím)
        fun segment(popis: String, akce: () -> Unit) = TextView(this).apply {
            text = popis.replace("📺", "").replace("📱", "").trim()
            textSize = 14f
            typeface = Metro.pismo(this@TelefonActivity, 600)
            setTextColor(Color.WHITE)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            setOnClickListener { akce() }
        }
        segTv = segment(t("segTv")) { prepniNaTv() }
        segTel = segment(t("segTel")) { prepniNaTelefon() }
        tvTlacitko = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(Metro.SEDA_DLAZDICE)
            addView(segTv)
            addView(segTel)
            // podržet → seznam televizí (když jich je v síti víc)
            setOnLongClickListener { vyberTelevizi(); true }
            segTv.setOnLongClickListener { vyberTelevizi(); true }
        }
        val lista = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(4))
            addView(ImageView(this@TelefonActivity).apply {
                setImageResource(R.drawable.hlavicka)
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_START
                // podržet logo → záznam (pro hledání chyb)
                setOnLongClickListener { Zaznam.ukaz(this@TelefonActivity); true }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(28)))
            // mezera – tlačítka doprava
            addView(View(this@TelefonActivity), LinearLayout.LayoutParams(0, 1, 1f))
            addView(tvTlacitko, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(10) })
            addView(zastavit)
        }

        // velký text překladu uprostřed
        titulek = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            // co nejtučnější písmo, ať je titulek výrazný
            typeface = if (android.os.Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 900, false) else Typeface.DEFAULT_BOLD
            setLineSpacing(0f, 1.15f)
            setPadding(dp(20), dp(20), dp(20), dp(20))
            // zvolená velikost je horní hranice; když se dlouhá věta nevejde (hlavně na šířku), písmo se samo zmenší
            nastavVelikost(this)
            setOnClickListener {
                velikost = (velikost + 1) % VELIKOSTI.size
                nastavVelikost(this)
            }
        }
        // nápověda k ovládání (když je zapnutá)
        napoveda = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 18f
            setTextColor(Color.parseColor("#FFD54A"))
            setBackgroundColor(Color.parseColor("#1E1A0C"))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            visibility = View.GONE
        }
        // stav spojení na samostatném řádku pod lištou (jinak se text mačkal po písmenech pod sebe)
        val stavRadek = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(2), dp(16), dp(10))
            addView(tecka, LinearLayout.LayoutParams(dp(9), dp(9)).apply { rightMargin = dp(8) })
            addView(stav, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            // obě konzole zapnuté → přepnout (stejně jako na televizi)
            addView(TextView(this@TelefonActivity).apply {
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(Color.WHITE)
                background = zaobleni(Color.parseColor("#E5091A"))
                setPadding(dp(14), dp(6), dp(14), dp(6))
                visibility = View.GONE
                setOnClickListener { RemotePlayService.prepni(this@TelefonActivity, naXbox = !RemotePlayService.naXboxu) }
                prepnoutKonzoli = this
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
        }
        val horni = LinearLayout(this).apply {
            horniPanel = this
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Metro.POZADI)
            addView(lista, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(stavRadek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        tip = TextView(this).apply {
            text = t("telefonTip")
            gravity = Gravity.CENTER
            textSize = 13f
            setTextColor(Color.parseColor("#555555"))
            setPadding(0, dp(6), 0, dp(10))
        }

        val koren = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            addView(horni, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            obalTitulku = FrameLayout(this@TelefonActivity).apply {
                addView(titulek, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                // na šířku: malá zelená tečka v rohu, ať to neruší
                sirkaTecka = View(this@TelefonActivity).apply { background = zaobleni(Color.parseColor("#3CFF6E")) }
                sirkaZnacka = LinearLayout(this@TelefonActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    alpha = 0.6f
                    visibility = View.GONE
                    addView(sirkaTecka, LinearLayout.LayoutParams(dp(7), dp(7)).apply { rightMargin = dp(6) })
                    addView(TextView(this@TelefonActivity).apply {
                        text = t("titulkyAktivni")
                        textSize = 11f
                        setTextColor(Color.parseColor("#3CFF6E"))
                    })
                }
                addView(sirkaZnacka, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.END).apply { topMargin = dp(10); rightMargin = dp(16) })
            }
            addView(obalTitulku, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            // nastavení (mini televize, rámečky, posuvníky) jde posouvat, ať se vejde i na menší telefon
            posunUprav = android.widget.ScrollView(this@TelefonActivity).apply {
                setBackgroundColor(Metro.POZADI)
                visibility = View.GONE
                addView(LinearLayout(this@TelefonActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(upravy, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
                })
            }
            addView(posunUprav, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(napoveda, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(tip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(koren)
    }

    // ---------------------------------------------------------------- na šířku (jen titulky v telefonu)

    /** Do televize → jen na výšku. Jen telefon → otáčí se podle telefonu. */
    private fun nastavOtaceni(doTv: Boolean) {
        val chci = if (doTv) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                   else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR
        runCatching { if (requestedOrientation != chci) requestedOrientation = chci }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        prepniSirku(newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE &&
            Propojeni.Vysilac.cilIp == null)
    }

    /** Na šířku: černá obrazovka jen s titulky přes celou šířku + malá zelená tečka. */
    private fun prepniSirku(ano: Boolean) {
        if (!::horniPanel.isInitialized || naSirku == ano) return
        naSirku = ano
        horniPanel.visibility = if (ano) View.GONE else View.VISIBLE
        tip.visibility = if (ano || Propojeni.Vysilac.cilIp != null) View.GONE else View.VISIBLE
        if (ano) napoveda.visibility = View.GONE
        sirkaZnacka.visibility = if (ano) View.VISIBLE else View.GONE
        titulek.setPadding(dp(if (ano) 32 else 20), dp(20), dp(if (ano) 32 else 20), dp(20))
        // schovat horní a spodní lištu systému (hodiny, tlačítka) – přejetím od kraje se ukážou
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val c = window.insetsController
                if (ano) {
                    c?.hide(android.view.WindowInsets.Type.systemBars())
                    c?.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else c?.show(android.view.WindowInsets.Type.systemBars())
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (ano)
                    (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
                else View.SYSTEM_UI_FLAG_VISIBLE
            }
        }
    }

    /** Jméno postavy ("Vivian:") v barvě jako ve hře, zbytek bíle. */
    private fun obarviJmena(text: String, barvy: IntArray?): CharSequence {
        if (barvy == null || barvy.none { it != 0 }) return text
        val sb = android.text.SpannableStringBuilder(text)
        var zacatek = 0
        text.split("\n").forEachIndexed { i, radek ->
            val barva = barvy.getOrNull(i) ?: 0
            val dvojtecka = radek.indexOf(':')
            if (barva != 0 && dvojtecka in 1..25)
                sb.setSpan(android.text.style.ForegroundColorSpan(barva), zacatek, zacatek + dvojtecka + 1,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            zacatek += radek.length + 1
        }
        return sb
    }

    private fun nastavVelikost(t: TextView) {
        t.setAutoSizeTextTypeUniformWithConfiguration(
            14, VELIKOSTI[velikost].toInt(), 1, android.util.TypedValue.COMPLEX_UNIT_SP)
    }

    private var posledni: String? = null
    private var posledniDoTv = false

    private val obnova: Runnable = object : Runnable {
        override fun run() {
            // PS5 si mezitím vzala televize → zavřít
            if (TelefonTitulky.odpojeno) {
                TelefonTitulky.odpojeno = false
                android.widget.Toast.makeText(this@TelefonActivity, t("prevzato"), android.widget.Toast.LENGTH_LONG).show()
                finish()
                return
            }
            // stav spojení
            val (klic, barva) = when {
                RemotePlayService.bezi -> "stavPripojeno" to Color.parseColor("#14A23C")
                else -> "stavPripojuji" to Color.parseColor("#E08A00")
            }
            // při potížích ukázat i přesný důvod (např. "Remote Play ukončeno: …")
            val duvod = RemotePlayService.stav
            val k = if (RemotePlayService.naXboxu) klic + "Xbox" else klic
            stav.text = if (!RemotePlayService.bezi && (duvod.contains("ukončeno", true) || duvod.contains("✘")))
                t(k) + "\n" + duvod else t(k)
            tecka.background = zaobleni(barva)
            prepnoutKonzoli?.let { b ->
                val ukazat = RemotePlayService.obeZapnute && RemotePlayUdaje(this@TelefonActivity).sparovano &&
                    XboxUcet.konzole(this@TelefonActivity) != null
                b.visibility = if (ukazat) View.VISIBLE else View.GONE
                b.text = if (RemotePlayService.naXboxu) "⇄ Xbox" else "⇄ PS5"
            }

            // titulky jdou do televize → telefon jen ukazuje stav a ztlumí displej (šetří baterii)
            val doTv = Propojeni.Vysilac.cilIp != null
            if (doTv != posledniDoTv) {
                posledniDoTv = doTv
                // do televize → zpátky na výšku (obrazovka s posuvníky); jen telefon → jde otočit
                if (doTv) prepniSirku(false)
                nastavOtaceni(doTv)
                upravy.visibility = if (doTv) View.VISIBLE else View.GONE
                tip.visibility = if (doTv) View.GONE else View.VISIBLE
                if (doTv) {
                    // titulky jdou do televize → nahoře jen malý zelený řádek, víc místa pro nastavení
                    titulek.setAutoSizeTextTypeWithDefaults(android.widget.TextView.AUTO_SIZE_TEXT_TYPE_NONE)
                    titulek.textSize = 13f
                    titulek.setPadding(dp(20), dp(4), dp(20), dp(4))
                    obalTitulku.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    posunUprav.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                } else {
                    titulek.setBackgroundColor(Color.TRANSPARENT)
                    titulek.typeface = if (android.os.Build.VERSION.SDK_INT >= 28) Typeface.create(Typeface.DEFAULT, 900, false) else Typeface.DEFAULT_BOLD
                    titulek.setPadding(dp(20), dp(20), dp(20), dp(20))
                    nastavVelikost(titulek)
                    obalTitulku.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                    posunUprav.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                posunUprav.visibility = if (doTv) View.VISIBLE else View.GONE
                if (doTv) Propojeni.Vysilac.nastavRozvrh(rozvrh.copyOf())
                // [Fuki] ztlumit až po 30 s bez dotyku (při nastavování zůstane jas normální)
                tlumitDisplej = doTv
                nastavJas(false)
                ui.removeCallbacks(ztlum)
                if (doTv) ui.postDelayed(ztlum, TLUMIT_PO)
                posledni = null
            }
            if (doTv) {
                val s = "Titulky běží v televizi · telefon dej na nabíječku"
                if (titulek.text.toString() != s) {
                    titulek.text = s
                    titulek.typeface = Metro.pismo(this@TelefonActivity, 600)
                    titulek.setTextColor(Metro.ZLUTA)
                    titulek.setBackgroundColor(Metro.POZADI)
                }
                napoveda.visibility = View.GONE
                ui.postDelayed(this, 300)
                return
            }

            // překlad
            val text = TelefonTitulky.aktualni()
            if (text != posledni) {
                posledni = text
                if (text.isEmpty()) {
                    titulek.text = t("telefonCekam")
                    titulek.setTextColor(Color.parseColor("#555555"))
                } else {
                    titulek.text = obarviJmena(text, TelefonTitulky.barvy)
                    titulek.setTextColor(Color.WHITE)
                }
            }
            val n = if (nast.napovedy) TelefonTitulky.napoveda else ""
            napoveda.text = n
            napoveda.visibility = if (n.isEmpty() || naSirku) View.GONE else View.VISIBLE
            // na šířku: tečka zelená = spojeno s PS5, oranžová = připojuji
            if (naSirku) sirkaTecka.background = zaobleni(Color.parseColor(if (RemotePlayService.bezi) "#3CFF6E" else "#E08A00"))
            ui.postDelayed(this, 100)
        }
    }
}
