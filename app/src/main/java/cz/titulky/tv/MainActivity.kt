package cz.titulky.tv

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * PlayCaption – hlavní obrazovka pro televizi (ovládání šipkami + OK).
 * Úvod s logem → výběr jazyka (jen poprvé) → jednoduchá hlavní obrazovka.
 * Stará obrazovka s testovacími tlačítky: VyvojActivity (podržet OK na logu).
 */
class MainActivity : Activity() {

    private val nast by lazy { Nastaveni(this) }
    private val udaje by lazy { RemotePlayUdaje(this) }
    private val ui = Handler(Looper.getMainLooper())
    private var obrazovka = ""
    /** Na telefonu: vše pod sebou a překlad na bílé obrazovce (TelefonActivity). */
    private val telefon by lazy { Rezim.jeTelefon(this) }
    private val sirkaObsahu get() = if (telefon) ViewGroup.LayoutParams.MATCH_PARENT else dp(900)
    private val jazyk get() = nast.jazykNeboCs
    private fun t(klic: String) = Texty.t(jazyk, klic)

    // barvy
    private val CERNA = Metro.POZADI // [Fuki] Metro HUD
    private val KARTA = Color.parseColor("#151B2A")
    private val CERVENA = Metro.CERVENA
    private val ZELENA = Metro.MODRA // [Fuki] akční tlačítka modře (dlaždice)
    private val SEDA = Color.parseColor("#2F3444")
    private val TEXT2 = Color.parseColor("#9AA3B5")

    companion object {
        /** QR Platba na příspěvky (bez částky – každý si zvolí sám). Prázdné = karta se neukáže. */
        /** Stažení aplikace do telefonu / tabletu (QR kód v okně „Přidat Android zařízení“). */
        // prázdné = QR kód se neukáže (doplnit odkaz ke stažení, např. Google Disk)
        const val STAHNOUT_URL = ""
        const val PODPORA_QR = "SPD*1.0*ACC:CZ0506000000000207980632*CC:CZK*MSG:PlayCaption podpora"
        /** Odkaz na Facebook skupinu PlayCaption – testování. Prázdné = QR v uvítání se neukáže. */
        const val SKUPINA_URL = ""
        const val DOBA_BETA_MS = 9000L
        /** Jazyky, které jdou zatím vybrat (beta pro českou a slovenskou komunitu). */
        val AKTIVNI_JAZYKY = setOf("cs")  // slovenština: backlog
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Pady.hlidej(this)
        prevezmiAccountId(intent)
        ukazUvod()
        // podpisová značka Fuki – úvodní animace (jednou za spuštění)
        FukiIntro.ukaz(this)
        // [Fuki] nová verze na GitHubu? (jednou za spuštění)
        Aktualizace.zkontroluj(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        prevezmiAccountId(intent)
    }

    /** Záložní cesta: skript psn-account-id.ps1 v počítači pošle Account ID sem. */
    private fun prevezmiAccountId(intent: Intent?) {
        val id = intent?.getStringExtra("psn_account_id") ?: return
        udaje.accountId = id
    }

    override fun onResume() {
        super.onResume()
        // telefon/tablet: když běží spojení s konzolí, nenechat displej zhasnout ani tady
        // (zhasnutý displej = za minutu konec spojení, Wi-Fi usne)
        if (Rezim.jeTelefon(this) && RemotePlayService.aktivni)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (obrazovka == "hlavni") ukazHlavni()
        // vrátil se z nastavení a povolení pořád nemá → televize ho asi schovává, ukázat návod
        if (zkouselPovoleni && !Rezim.jeTelefon(this) && !Settings.canDrawOverlays(this)) ukazNavodPovoleni()
    }

    override fun onPause() {
        ui.removeCallbacks(obnova)
        super.onPause()
    }

    override fun onDestroy() {
        zastavServer()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (obrazovka) {
            "psn", "psnqr" -> ukazPripojeni()
            "xboxkod" -> ukazPripojeniXbox()
            "xbox" -> { xboxRelace?.zastav(); xboxRelace = null; ukazHlavni() }
            "pripojeni", "jazyk" -> if (nast.jazyk != null) ukazHlavni() else super.onBackPressed()
            "prijem" -> zpetNaVyber()
            else -> super.onBackPressed()
        }
    }

    // ------------------------------------------------------------------ úvod

    /** [Fuki] Úvod je animace FukiIntro. Jazyk je zatím jen čeština (slovenština v backlogu). */
    private fun ukazUvod() {
        if (nast.jazyk !in AKTIVNI_JAZYKY) nast.jazyk = "cs"
        ukazHlavni()
    }

    private val verze by lazy { runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "" }
    private val prefsBeta by lazy { getSharedPreferences("beta", MODE_PRIVATE) }

    /** Malá značka s verzí do pravého horního rohu. */
    private fun betaZnacka() = TextView(this).apply {
        text = verze
        textSize = 12f * SKALA
        typeface = Metro.pismo(this@MainActivity, 600)
        setTextColor(Metro.OBRYS)
    }

    // ------------------------------------------------------------------ [Fuki] výběr jazyka (dlaždice)

    private fun ukazJazyk() {
        obrazovka = "jazyk"
        val obsah = svisle()
        obsah.addView(mt("Do jakého jazyka se titulky překládají. Zatím umíme češtinu, další jazyky připravujeme.", 0.026f, 14f, TEXT2),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = mez * 4 })
        val sloupcu = if (telefon) 2 else 4
        val vyskaDl = if (telefon) Metro.dp(this, 104f) else (vyskaObr * 0.2f).toInt()
        var vybrana: View? = null
        Texty.JAZYKY.chunked(sloupcu).forEachIndexed { ri, cast ->
            val r = vodorovne()
            cast.forEachIndexed { i, j ->
                val aktivni = j.kod in AKTIVNI_JAZYKY
                val zvoleny = j.kod == jazyk
                val dl = mdl(null, j.kod.uppercase(), j.nazev,
                    if (zvoleny) "Vybráno" else if (aktivni) "Vybrat" else "Připravujeme",
                    zaklad = if (aktivni) Metro.MODRA else Metro.SEDA_DLAZDICE,
                    vyber = if (aktivni) Metro.MODRA_VYBER else Metro.SEDA_VYBER,
                    lista = zvoleny, barvaPopisu = if (zvoleny) Metro.ZLUTA else Metro.OBRYS) {
                    if (aktivni) { nast.jazyk = j.kod; ukazHlavni() }
                }
                if (!aktivni) dl.d.alpha = 0.5f
                if (zvoleny) vybrana = dl.d
                r.addView(dl.d, LinearLayout.LayoutParams(0, vyskaDl, 1f).apply { if (i > 0) leftMargin = mez })
            }
            repeat(sloupcu - cast.size) { r.addView(View(this), LinearLayout.LayoutParams(0, vyskaDl, 1f).apply { leftMargin = mez }) }
            obsah.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (ri > 0) topMargin = mez })
        }
        metroRam("Jazyk překladu", obsah)
        vybrana?.requestFocus()
    }

    // ------------------------------------------------------------------ hlavní obrazovka

    private lateinit var stavText: TextView
    private lateinit var stavTecka: View
    private lateinit var stavKarta: LinearLayout

    private fun ukazHlavni() {
        if (!telefon && PrijemService.bezi) { ukazPrijem(); return }
        // [Fuki] telefon i televize: nová obrazovka v dlaždicích
        if (telefon) { ukazHlavniTelefon(); return }
        ukazHlavniTv()
    }

    // ------------------------------------------------------------------ titulky z telefonu (televize)

    /** Zpět z čekání na telefon → hlavní obrazovka s výběrem "Jak chceš hrát?". */
    private fun zpetNaVyber() {
        PrijemService.zastav(this)
        prefsBeta.edit().putString("rezimHry", "tv").apply()
        obrazovka = "hlavni"
        ukazHlavni()
    }

    /** Velká volba na hlavní obrazovce (ikona, nadpis, popis). */
    private fun dlazdice(ikona: String, nadpis: String, popis: String, barva: Int, akce: () -> Unit) = svisle().apply {
        // obě volby stejně tmavé; ta, na které je ovladač, se rozsvítí (červeně + bílý rámeček) a trochu zvětší
        background = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), zaobleni(CERVENA, 26f, Color.WHITE))
            addState(intArrayOf(), zaobleni(Color.parseColor("#2A2C3E"), 26f, Color.parseColor("#3A3D55")))
        }
        setPadding(dp(24), dp(20), dp(24), dp(20))
        isFocusable = true
        isClickable = true
        setOnClickListener { akce() }
        setOnFocusChangeListener { v, maFokus ->
            val m = if (maFokus) 1.06f else 1f
            v.animate().scaleX(m).scaleY(m).setDuration(150).start()
            v.elevation = if (maFokus) dp(10).toFloat() else 0f
        }
        addView(text(ikona, 34f, Color.WHITE))
        addView(text(nadpis, 24f, Color.WHITE, tucne = true), odsazeni(top = 4))
        addView(text(popis, 15f, Color.parseColor("#DDDDDD")).apply { setLineSpacing(0f, 1.25f) }, odsazeni(top = 4))
    }

    /**
     * Je televize slabá? Pozná se z toho, že minule v režimu „Jen televize“ spadla ("varovat"),
     * nebo z paměti (do ~2 GB). Jedno pomalé čtení ("slaba") jen zapne úsporný režim, nevaruje.
     */
    private fun jeSlabaTv(): Boolean {
        val up = getSharedPreferences("uspora", MODE_PRIVATE)
        // po pádu v režimu „Jen televize“ → upozornit (jedno pomalé čtení ale ne – to umí i silná TV)
        if (up.getBoolean("varovat", false)) return true
        // už se při hraní ukázalo, že stíhá (10× po sobě rychlé čtení) → neupozorňovat, i když má 2 GB
        if (up.getBoolean("rychla", false)) return false
        return runCatching {
            val am = getSystemService(android.app.ActivityManager::class.java)
            val info = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
            // televize „2 GB“ hlásí kvůli rezervě systému jen ~1,6–1,9 GB → hranice 2,5 GB pokryje všechny 2GB televize
            am.isLowRamDevice || info.totalMem < 2_500L * 1024 * 1024
        }.getOrDefault(false)
    }

    /** Tenký žlutočerný šikmý proužek (výstražná páska). */
    private fun vystraznyPruh(): View = object : View(this) {
        private val zluta = android.graphics.Paint().apply { color = Color.parseColor("#FFC800") }
        private val cerna = android.graphics.Paint().apply { color = Color.BLACK; isAntiAlias = true }
        private val cesta = android.graphics.Path()
        override fun onDraw(c: android.graphics.Canvas) {
            val h = height.toFloat()
            c.drawRect(0f, 0f, width.toFloat(), h, zluta)
            var x = -h
            while (x < width) {
                cesta.reset()
                cesta.moveTo(x, h); cesta.lineTo(x + h, 0f); cesta.lineTo(x + 2 * h, 0f); cesta.lineTo(x + h, h); cesta.close()
                c.drawPath(cesta, cerna)
                x += 2 * h
            }
        }
    }

    /** Slabá televize a přesto "Jen televize" → upozornit a nabídnout telefon. */
    private fun varovaniSlabaTv(nadpis: String = t("slabaNadpis"), zprava: String = t("slabaText")) {
        runCatching {
            // žlutočerný výstražný proužek (jako páska na stavbě) nad textem a pod ním
            val obsah = svisle().apply { setPadding(dp(24), dp(20), dp(24), dp(4)) }
            obsah.addView(vystraznyPruh(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)))
            obsah.addView(text(nadpis, 24f, Color.parseColor("#FFC800"), tucne = true), odsazeni(top = 16))
            obsah.addView(text(zprava, 19f, Color.WHITE), odsazeni(top = 10, bottom = 16))
            obsah.addView(vystraznyPruh(), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)))
            android.app.AlertDialog.Builder(this)
                .setView(obsah)
                // Zpět = jen zavřít (dlaždici „S telefonem“ si člověk vybere sám), Pokračovat = jen televize
                .setPositiveButton(t("slabaTel"), null)
                .setNegativeButton(t("slabaPresto")) { _, _ -> hratTelevizi() }
                .show()
                .getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.requestFocus()
        }.onFailure { hratTelevizi() }
    }

    /** "Jen televize" = dřívější Hotovo. */
    private fun hratTelevizi() {
        prefsBeta.edit().putString("rezimHry", "tv").apply()
        if (nejakaKonzole && Settings.canDrawOverlays(this)) {
            PrijemService.zastav(this)
            RemotePlayService.pozastaveno = false
            AutoStart.nastavZapnuto(this, true)
            run { AutoStart.uspech(this); RemotePlayService.spustVse(this, prevzit = true) }
            Oznameni.ukaz(this, t("hratInfo"))
        } else if (!nejakaKonzole) { ukazPripojeniXbox(); return }
        moveTaskToBack(true)
    }

    /** "S telefonem" = televize jen ukazuje titulky poslané z telefonu. */
    private fun hratSTelefonem() {
        if (!Settings.canDrawOverlays(this)) return
        prefsBeta.edit().putString("rezimHry", "tel").apply()
        // televize se od PS5 úplně odpojí (PS5 si vezme telefon)
        RemotePlayService.pozastaveno = true
        AutoStart.nastavZapnuto(this, false)
        // zastavit jen když opravdu běží – jinak by se čtečka zbytečně spouštěla (slabou TV to shodí)
        if (RemotePlayService.aktivni) runCatching { startService(Intent(this, RemotePlayService::class.java).setAction(RemotePlayService.ACTION_STOP)) }
        PrijemService.spust(this)
        ukazPrijem()
    }

    // ------------------------------------------------------------------ titulky z telefonu (televize)

    /** [Fuki] Televize čeká na titulky z telefonu – dlaždice na jednu obrazovku. */
    private fun ukazPrijem() {
        obrazovka = "prijem"
        val obsah = svisle()
        val kroky = vodorovne()
        listOf(
            Triple(R.drawable.ik_smartphone, "Krok 1", "V telefonu otevři TitulkyCZ_Fuki"),
            Triple(R.drawable.ik_check_circle, "Krok 2 · jen poprvé", "Spáruj telefon s televizí"),
            Triple(R.drawable.ik_sports_esports, "Krok 3", "V telefonu dej Hrát a polož ho")
        ).forEachIndexed { i, (ik, n, h) ->
            val dl = mdl(ik, n, h, null, lista = i == 1)
            kroky.addView(dl.d, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { if (i > 0) leftMargin = mez })
        }
        obsah.addView(kroky, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        val par = mdl(R.drawable.ik_info, "Jak spárovat telefon (stačí jednou)", null, t("ngPar1") + "\n" + t("ngPar2"),
            zaklad = Metro.SEDA_DLAZDICE, vyber = Metro.SEDA_VYBER, barvaPopisu = Metro.TEXT)
        obsah.addView(par.d, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = mez })
        val spodek = vodorovne()
        val stav = mdl(null, "Stav", "Čekám na telefon…", "Až se telefon připojí, televize sama přepne na hru.",
            zaklad = Metro.SEDA_DLAZDICE, vyber = Metro.SEDA_VYBER, barvaPopisu = Metro.OBRYS)
        val zpet = mdl(R.drawable.ik_tv, "Zpět", "Titulky čte televize", null) { zpetNaVyber() }
        spodek.addView(stav.d, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2f))
        spodek.addView(zpet.d, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { leftMargin = mez })
        obsah.addView(spodek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (vyskaObr * 0.19f).toInt()).apply { topMargin = mez })
        metroRam("Titulky čte telefon", obsah, posouvat = false)
        zpet.d.requestFocus()

        var bylPripojen = PrijemService.telefonPripojen()
        val obnovPrijem = object : Runnable {
            override fun run() {
                if (obrazovka != "prijem") return
                val ok = PrijemService.telefonPripojen()
                stav.hlavni?.text = if (ok) "Telefon připojen – titulky chodí" else "Čekám na telefon…"
                stav.hlavni?.setTextColor(if (ok) Metro.ZLUTA else Metro.TEXT)
                stav.d.listaZapnuta = ok
                // telefon se právě připojil → sama přepnout na hru
                if (ok && !bylPripojen) moveTaskToBack(true)
                bylPripojen = ok
                ui.postDelayed(this, 1000)
            }
        }
        ui.post(obnovPrijem)
    }

    private val obnova: Runnable = object : Runnable {
        override fun run() {
            if (obrazovka != "hlavni") return
            obnovStav()
            ui.postDelayed(this, 1000)
        }
    }

    private var prepnoutBtn: TextView? = null
    private var xboxRadek: TextView? = null
    private var ps5Radek: TextView? = null
    private var xboxRadekText = ""
    private var ps5RadekText = ""

    /** Tlačítko ⇄: přepnout překlad na druhou konzoli. */
    private fun prepniKonzoli() {
        val hrajuXbox = if (RemotePlayService.bezi || RemotePlayService.aktivni) RemotePlayService.naXboxu else RemotePlayService.preferXbox(this)
        RemotePlayService.prepni(this, naXbox = !hrajuXbox)
        obnovStav()
    }

    /** Spárovaná aspoň jedna konzole (PS5 nebo Xbox). */
    private val nejakaKonzole get() = udaje.sparovano || XboxUcet.konzole(this) != null

    private fun obnovStav() {
        if (!::stavText.isInitialized) return
        val (barva, klic) = when {
            !nejakaKonzole -> SEDA to "stavNesparovano"
            !RemotePlayService.aktivni || RemotePlayService.pozastaveno -> SEDA to "stavVypnuto"
            RemotePlayService.bezi -> ZELENA to "stavPripojeno"
            else -> Color.parseColor("#B07A10") to "stavPripojuji"
        }
        // při připojování ukázat i přesný důvod, proč to zatím nejde (např. "Remote Play ukončeno: …")
        val duvod = RemotePlayService.stav
        val k = if (RemotePlayService.naXboxu && (klic == "stavPripojeno" || klic == "stavPripojuji")) klic + "Xbox" else klic
        stavText.text = if (klic == "stavPripojuji" && duvod.isNotBlank() && !duvod.contains("běží", true) && !RemotePlayService.naXboxu)
            t(k) + "\n" + duvod.take(90) else t(k)
        stavTecka.background = zaobleni(if (barva == ZELENA) Color.parseColor("#3CFF6E") else Color.parseColor("#DDDDDD"), 999f)
        stavKarta.background = vyber(barva, radius = 999f)
        // na čem se hraje: připojeno → podle spojení, jinak podle volby
        val hrajuXbox = if (RemotePlayService.bezi || RemotePlayService.aktivni) RemotePlayService.naXboxu else RemotePlayService.preferXbox(this)
        // tlačítko ukazuje konzoli, na které se právě hraje (kliknutím → ta druhá)
        prepnoutBtn?.text = if (hrajuXbox) "⇄ Xbox" else "⇄ PS5"
        // tlačítko ⇄ jen když jsou zapnuté obě konzole (zjišťuje služba každých 20 s)
        // jen když překlad běží (jinak služba konzole nekontroluje a údaj by byl starý)
        val ukazat = RemotePlayService.aktivni && RemotePlayService.obeZapnute && udaje.sparovano && XboxUcet.konzole(this) != null
        prepnoutBtn?.let { b ->
            val v = if (ukazat) View.VISIBLE else View.GONE
            if (b.visibility != v) { if (!ukazat && b.isFocused) stavKarta.requestFocus(); b.visibility = v }
        }
        val hraje = RemotePlayService.bezi && udaje.sparovano && XboxUcet.konzole(this) != null
        xboxRadek?.text = xboxRadekText + if (hraje && hrajuXbox) "   ▶ ${t("hraju")}" else ""
        ps5Radek?.text = ps5RadekText + if (hraje && !hrajuXbox) "   ▶ ${t("hraju")}" else ""
    }

    private fun prepniPreklad() {
        if (!nejakaKonzole) { ukazPripojeniXbox(); return }
        if (telefon) { startActivity(Intent(this, TelefonActivity::class.java)); return }
        // vypnutí = jen pozastavit (spojení s PS5 zůstává, jinak by PS5 1–2 min hlásila "již se používá")
        if (RemotePlayService.aktivni && !RemotePlayService.pozastaveno) {
            RemotePlayService.pozastaveno = true
            AutoStart.nastavZapnuto(this, false)
        } else {
            RemotePlayService.pozastaveno = false
            AutoStart.nastavZapnuto(this, true)
            if (!RemotePlayService.aktivni) run { AutoStart.uspech(this); RemotePlayService.spustVse(this, prevzit = true) }
        }
        ui.postDelayed({ obnovStav() }, 600)
    }



    // ------------------------------------------------------------------ [Fuki] televize: hlavní obrazovka v dlaždicích

    /**
     * Televize: celá hlavní obrazovka v dlaždicích na jednu obrazovku (bez posouvání).
     * Rozměry se počítají z velikosti obrazovky; HRÁT je vybrané hned po spuštění.
     */
    private fun ukazHlavniTv() {
        obrazovka = "hlavni"
        Tabulka.nacti(getExternalFilesDir("tabulky"))
        val c = this
        val W = resources.displayMetrics.widthPixels
        val H = resources.displayMetrics.heightPixels
        val okrajX = (W * 0.045f).toInt()
        val okrajY = (H * 0.06f).toInt()
        val gap = (H * 0.008f).toInt().coerceAtLeast(4)
        val hlavaH = (H * 0.10f).toInt()
        val patickaH = (H * 0.10f).toInt()
        val mrizkaW = W - 2 * okrajX
        val mrizkaH = H - 2 * okrajY - hlavaH - patickaH
        val bw = (mrizkaW - 5 * gap) / 6f
        val bh = (mrizkaH - 2 * gap) / 3f
        fun px(v: Float) = android.util.TypedValue.COMPLEX_UNIT_PX to v
        fun txt(s: CharSequence, vyska: Float, barva: Int, vaha: Int) = Metro.text(c, s, 1f, barva, vaha).apply {
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, vyska)
            maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val koren = FrameLayout(this).apply { setBackgroundColor(Metro.POZADI); clipChildren = false }

        // hlavička
        koren.addView(ImageView(this).apply {
            setImageResource(R.drawable.hlavicka); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_START
            isFocusable = false
            setOnLongClickListener { Zaznam.ukaz(this@MainActivity, sVyvojem = true); true }
        }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (hlavaH * 0.62f).toInt()).apply { leftMargin = okrajX; topMargin = okrajY })
        // vpravo nahoře: verze + Restart + Vypnout (TCL a jiné TV drží aplikaci v paměti)
        val horniPravy = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
        horniPravy.addView(txt(verze, hlavaH * 0.2f, Metro.OBRYS, 600), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = (hlavaH * 0.3f).toInt() })
        fun horniTlacitko(ikona: Int, popis: String, akce: () -> Unit): View {
            val d = Metro.Dlazdice(c, Metro.SEDA_DLAZDICE, Metro.SEDA_VYBER, false)
            val r = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL; setPadding((hlavaH * 0.15f).toInt(), 0, (hlavaH * 0.2f).toInt(), 0) }
            r.addView(ImageView(c).apply { setImageResource(ikona); setColorFilter(Metro.PRIMARNI) }, LinearLayout.LayoutParams((hlavaH * 0.3f).toInt(), (hlavaH * 0.3f).toInt()).apply { rightMargin = (hlavaH * 0.1f).toInt() })
            r.addView(txt(popis, hlavaH * 0.2f, Metro.TEXT, 600).apply { maxLines = 1; includeFontPadding = false; setLineSpacing(0f, 1f) })
            d.obsah.gravity = Gravity.CENTER_VERTICAL
            d.obsah.addView(r)
            d.setOnClickListener { akce() }
            return d
        }
        horniPravy.addView(horniTlacitko(R.drawable.ik_restart, "Restart") {
            android.app.AlertDialog.Builder(this).setTitle("Restartovat aplikaci?")
                .setMessage("Zastaví překlad i spojení s konzolí a aplikaci spustí znovu načisto.")
                .setPositiveButton("Restartovat") { _, _ -> Vypinac.restart(this) }.setNegativeButton("Zpět", null).show()
        }, LinearLayout.LayoutParams((hlavaH * 2.3f).toInt(), (hlavaH * 0.6f).toInt()).apply { rightMargin = gap })
        horniPravy.addView(horniTlacitko(R.drawable.ik_vypnout, "Vypnout") {
            android.app.AlertDialog.Builder(this).setTitle("Vypnout aplikaci?")
                .setMessage("Zastaví překlad a aplikaci úplně ukončí (nezůstane v paměti).")
                .setPositiveButton("Vypnout") { _, _ -> Vypinac.vypnout(this) }.setNegativeButton("Zpět", null).show()
        }, LinearLayout.LayoutParams((hlavaH * 2.3f).toInt(), (hlavaH * 0.6f).toInt()))
        koren.addView(horniPravy, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (hlavaH * 0.7f).toInt(), Gravity.TOP or Gravity.END).apply { rightMargin = okrajX; topMargin = okrajY })

        val y0 = okrajY + hlavaH
        fun umisti(v: View, sl: Int, ra: Int, sirkaB: Int, vyskaB: Int) {
            val w = (bw * sirkaB + gap * (sirkaB - 1)).toInt()
            val h = (bh * vyskaB + gap * (vyskaB - 1)).toInt()
            koren.addView(v, FrameLayout.LayoutParams(w, h).apply {
                leftMargin = okrajX + (sl * (bw + gap)).toInt(); topMargin = y0 + (ra * (bh + gap)).toInt()
            })
        }
        /** Obsah dlaždice: ikona nahoře, dole malý nadpis, velký text, popisek. */
        fun naplnit(d: Metro.Dlazdice, ikona: Int, nadpis: String?, hlavni: String, pod: String?, vel: Float = 1f, barvaPod: Int = Metro.TEXT_NA_MODRE): TextView? {
            val p = (bh * 0.11f).toInt()
            d.obsah.setPadding(p, p, p, p)
            d.obsah.addView(ImageView(c).apply { setImageResource(ikona); setColorFilter(Metro.PRIMARNI) },
                LinearLayout.LayoutParams((bh * 0.24f * vel).toInt(), (bh * 0.24f * vel).toInt()))
            d.mezera()
            if (nadpis != null) d.obsah.addView(txt(nadpis, bh * 0.085f, Metro.OBRYS, 600))
            d.obsah.addView(txt(hlavni, bh * 0.17f * vel, Metro.TEXT, 300).apply { maxLines = 1 })
            var podT: TextView? = null
            if (pod != null) { podT = txt(pod, bh * 0.09f, barvaPod, 600); d.obsah.addView(podT) }
            return podT
        }

        val muzePrekryvat = Settings.canDrawOverlays(this)
        val xbox = XboxUcet.konzole(this)
        val rezimTel = prefsBeta.getString("rezimHry", "tv") == "tel" || jeSlabaTv()

        // HRÁT (2×2, červená)
        val hrat = Metro.Dlazdice(c, Metro.CERVENA, Metro.CERVENA_VYBER, lista = true)
        run {
            val p = (bh * 0.13f).toInt()
            hrat.obsah.setPadding(p, p, p, p)
            hrat.obsah.addView(ImageView(c).apply { setImageResource(R.drawable.ik_play_arrow); setColorFilter(Color.WHITE) },
                LinearLayout.LayoutParams((bh * 0.42f).toInt(), (bh * 0.42f).toInt()))
            hrat.mezera()
            hrat.obsah.addView(txt("Hrát", bh * 0.42f, Color.WHITE, 300).apply { maxLines = 1 })
            stavText = txt("", bh * 0.11f, Color.WHITE, 600)
            hrat.obsah.addView(stavText)
            hrat.obsah.addView(txt(when {
                !muzePrekryvat -> "Nejdřív povol zobrazení přes jiné aplikace"
                xbox == null -> "Nejdřív připoj Xbox"
                rezimTel -> "Titulky čte telefon"
                else -> "Titulky čte televize"
            }, bh * 0.1f, Metro.TEXT_NA_CERVENE, 400))
        }
        hrat.setOnClickListener {
            when {
                !muzePrekryvat -> pozadejOPovoleni()
                !nejakaKonzole -> upozorneniPredPrihlasenim(xbox = true)
                rezimTel -> hratSTelefonem()
                jeSlabaTv() -> varovaniSlabaTv()
                else -> hratTelevizi()
            }
        }
        umisti(hrat, 0, 0, 2, 2)

        // Konzole (2×1)
        val konzole = Metro.Dlazdice(c, Metro.MODRA, Metro.MODRA_VYBER, false)
        xboxRadekText = if (xbox != null) t("sparovano") else "Klepni a přihlas se"
        xboxRadek = naplnit(konzole, R.drawable.ik_sports_esports, "Konzole", "Xbox", xboxRadekText, barvaPod = if (xbox != null) Metro.ZLUTA else Metro.TEXT_NA_MODRE)
        konzole.setOnClickListener { upozorneniPredPrihlasenim(xbox = true) }
        umisti(konzole, 2, 0, 2, 1)

        // Telefon (2×1)
        val pridane = prefsBeta.getString("pridaneZarizeni", "") ?: ""
        val telD = Metro.Dlazdice(c, Metro.MODRA, Metro.MODRA_VYBER, false)
        naplnit(telD, R.drawable.ik_smartphone, "Telefon", pridane.ifBlank { "Přidat" }, if (pridane.isBlank()) "Telefon čte titulky rychleji" else "Spárováno")
        telD.setOnClickListener { if (nejakaKonzole) pridatZarizeni() else upozorneniPredPrihlasenim(xbox = true) }
        umisti(telD, 4, 0, 2, 1)

        // Kdo čte titulky (2×1) – přepínač
        val rezim = Metro.Dlazdice(c, Metro.MODRA, Metro.MODRA_VYBER, false)
        naplnit(rezim, R.drawable.ik_tv, "Titulky čte", if (rezimTel) "Telefon" else "Televize", if (rezimTel) "Rychlejší, doporučeno" else "OK přepne na telefon", barvaPod = Metro.ZLUTA)
        rezim.setOnClickListener {
            prefsBeta.edit().putString("rezimHry", if (rezimTel) "tv" else "tel").apply()
            if (rezimTel) getSharedPreferences("uspora", MODE_PRIVATE).edit().putBoolean("varovat", false).apply()
            ukazHlavni()
        }
        umisti(rezim, 2, 1, 2, 1)

        // Tabulky (2×1, šedá)
        val tab = Metro.Dlazdice(c, Metro.SEDA_DLAZDICE, Metro.SEDA_VYBER, false)
        val hry = Tabulka.hry()
        naplnit(tab, R.drawable.ik_table_chart, "Tabulky překladů", if (Tabulka.pocet > 0) "%,d vět".format(Tabulka.pocet).replace(',', ' ') else "Žádné",
            if (hry.isNotEmpty()) hry.joinToString(", ") else "Nahraj v telefonu (Úpravy)")
        tab.setOnClickListener { Nabidky.vyberTabulku(this) }
        umisti(tab, 4, 1, 2, 1)

        // spodní řada malých dlaždic
        fun mala(sl: Int, ikona: Int, nazev: String, stav: String?, zap: Boolean, akce: () -> Unit) {
            val d = Metro.Dlazdice(c, Metro.MODRA, Metro.MODRA_VYBER, zap)
            naplnit(d, ikona, null, nazev, stav, vel = 0.8f, barvaPod = if (zap) Metro.ZLUTA else Metro.TEXT_NA_MODRE)
            d.setOnClickListener { akce() }
            umisti(d, sl, 2, 1, 1)
        }
        // [Fuki] česky nahlas (místo výběru jazyka – zatím jen čeština)
        val hlasZap = Hlas.zapnuto(c)
        mala(0, R.drawable.ik_hlas, "Nahlas", if (hlasZap) "ZAP" else "VYP", hlasZap) {
            val ano = !Hlas.zapnuto(this)
            getSharedPreferences("gamelingo", MODE_PRIVATE).edit().putBoolean(Hlas.PREF_ZAP, ano).putString(Hlas.PREF_KDE, "tv").apply()
            if (ano) {
                Hlas.zkouska(this)
                // čeština chybí → nabídnout instalaci hlasu
                ui.postDelayed({
                    if (Hlas.chyba.isNotEmpty()) android.app.AlertDialog.Builder(this).setTitle("Česky nahlas")
                        .setMessage(Hlas.chyba + ". Otevřít instalaci hlasů a stáhnout češtinu?")
                        .setPositiveButton("Otevřít") { _, _ -> Hlas.nainstalujCestinu(this) }.setNegativeButton("Zavřít", null).show()
                }, 2500)
            }
            ukazHlavni()
        }
        mala(1, R.drawable.ik_chat, "Dialogy", if (nast.dialogy) "ZAP" else "VYP", nast.dialogy) { nast.dialogy = !nast.dialogy; ukazHlavni() }
        mala(2, R.drawable.ik_widgets, "Přes hru", if (nast.prekryvat) "ZAP" else "VYP", nast.prekryvat) { nast.prekryvat = !nast.prekryvat; ukazHlavni() }
        val aiZap = AiPrekladac.klic(c).isNotEmpty() && AiPrekladac.povoleno(c)
        mala(3, R.drawable.ik_auto_awesome, "AI překlad", if (aiZap) "ZAP" else "VYP", aiZap) { Nabidky.aiNastaveni(this) { ukazHlavni() } }
        mala(4, R.drawable.ik_system_update, "Podpořit", "autora PlayCaption", false) { podporaDialog() }
        mala(5, R.drawable.ik_info, "O aplikaci", "Aktualizace", false) { oAplikaci() }

        // patička: stav připojení, nápověda ovladače, podpis
        stavTecka = View(this)
        stavKarta = svisle()
        prepnoutBtn = null; ps5Radek = null; pripojitBtn = null
        val pat = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
        pat.addView(txt("◀ ▶ ▲ ▼ vybrat     OK potvrdit", patickaH * 0.24f, Metro.OBRYS, 400), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        pat.addView(FukiZnacka.podpis(this).apply { setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, patickaH * 0.24f); setPadding(0, 0, 0, 0) })
        koren.addView(pat, FrameLayout.LayoutParams(mrizkaW, patickaH, Gravity.BOTTOM).apply { leftMargin = okrajX; bottomMargin = okrajY / 2 })

        setContentView(koren)
        window.statusBarColor = Metro.POZADI

        // automaticky spustit, když je vše připravené a uživatel překlad nevypnul
        if (!PrijemService.bezi && nejakaKonzole && muzePrekryvat && AutoStart.jeZapnuto(this) && !RemotePlayService.aktivni) {
            val up = getSharedPreferences("uspora", MODE_PRIVATE)
            if (up.getBoolean("padlo", false) && prefsBeta.getString("rezimHry", "tv") == "tv") {
                up.edit().putBoolean("padlo", false).putBoolean("slaba", true).putBoolean("varovat", true).putBoolean("rychla", false).apply()
                varovaniSlabaTv(t("padloNadpis"), t("padloText"))
            } else {
                up.edit().putBoolean("padlo", false).apply()
                RemotePlayService.spustVse(this)
            }
        }
        obnovStav()
        ui.removeCallbacks(obnova)
        ui.postDelayed(obnova, 1000)
        hrat.requestFocus()
    }

    /** Podpora autora PlayCaption – QR Platba v okně (na TV). */
    private fun podporaDialog() {
        if (PODPORA_QR.isEmpty()) return
        val obsah = svisle().apply { setPadding(dp(24), dp(16), dp(24), dp(8)); gravity = Gravity.CENTER_HORIZONTAL }
        obsah.addView(text(t("podporaText"), 16f, Color.WHITE).apply { setLineSpacing(0f, 1.2f) })
        obsah.addView(text(t("podporaIban"), 14f, Metro.ZLUTA), odsazeni(top = 6))
        obsah.addView(ImageView(this).apply {
            setImageBitmap(PrihlaseniTelefonem.qr(PODPORA_QR, 400)); setBackgroundColor(Color.WHITE); setPadding(dp(8), dp(8), dp(8), dp(8))
        }, LinearLayout.LayoutParams(dp(180), dp(180)).apply { topMargin = dp(12) })
        android.app.AlertDialog.Builder(this).setTitle(t("podporaNadpis")).setView(obsah).setPositiveButton("Zavřít", null).show()
    }

    // ------------------------------------------------------------------ [Fuki] telefon: hlavní obrazovka v dlaždicích

    /** Telefon: hlavní obrazovka „hrát“ v dlaždicích (návrh TitulkyCZ Metro HUD ze Stitche). */
    private fun ukazHlavniTelefon() {
        obrazovka = "hlavni"
        Volby.nacti(this)
        Tabulka.nacti(getExternalFilesDir("tabulky"), hotovo = { ui.post { if (obrazovka == "hlavni" && !isFinishing) ukazHlavniTelefon() } })
        val c = this
        val okraj = dp(16)
        val gap = dp(4)
        val sirka = resources.displayMetrics.widthPixels - 2 * okraj
        val u = (sirka - 3 * gap) / 4
        val gp = getSharedPreferences("gamelingo", MODE_PRIVATE)
        val xbox = XboxUcet.konzole(this)
        val tvJmeno = Propojeni.Vysilac.cilJmeno.ifBlank { gp.getString("tvJmeno", "") ?: "" }

        val sloupec = svisle().apply { setPadding(okraj, dp(18), okraj, dp(12)) }

        // hlavička: ikona + název, verze vpravo
        val hlava = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
        hlava.addView(ImageView(this).apply {
            setImageResource(R.drawable.gamelingo_ikona)
            isFocusable = true
            setOnLongClickListener { Zaznam.ukaz(this@MainActivity, sVyvojem = true); true }
        }, LinearLayout.LayoutParams(dp(32), dp(32)).apply { rightMargin = dp(10) })
        hlava.addView(Metro.text(c, "TitulkyCZ_Fuki", 13f, Metro.TEXT, 700, 0.12f), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        hlava.addView(Metro.text(c, verze, 11f, Metro.OBRYS, 600, 0.06f))
        sloupec.addView(hlava)

        // pivot: hrát / úpravy / o aplikaci
        val pivot = vodorovne().apply { gravity = Gravity.BOTTOM }
        fun polozka(s: String, aktivni: Boolean, akce: () -> Unit) = svisle().apply {
            addView(Metro.text(c, s, 32f, if (aktivni) Metro.TEXT else Metro.OBRYS, 300, -0.01f))
            addView(View(c).apply { setBackgroundColor(if (aktivni) Metro.ZLUTA else 0) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(2)).apply { topMargin = dp(4) })
            isFocusable = true; isClickable = true
            setOnClickListener { akce() }
        }
        pivot.addView(polozka("hrát", true) {})
        pivot.addView(polozka("úpravy", false) { otevriHrani() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(22) })
        pivot.addView(polozka("o aplikaci", false) { oAplikaci() }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(22) })
        sloupec.addView(android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(pivot)
        }, odsazeni(top = 10, bottom = 14))

        // 1) Hrát – široká červená dlaždice
        val hrat = Metro.Dlazdice(c, Metro.CERVENA, Metro.CERVENA_VYBER, lista = true)
        val hr1 = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
        hr1.addView(Metro.ikona(c, R.drawable.ik_play_arrow, 34f, Color.WHITE))
        hr1.addView(Metro.text(c, "Hrát", 34f, Color.WHITE, 300, -0.02f), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
        hrat.obsah.addView(hr1)
        hrat.mezera()
        stavText = Metro.text(c, "", 16f, Color.WHITE, 600)
        hrat.obsah.addView(stavText)
        val podHrat = buildString {
            append(if (xbox != null) "Xbox připojen" else "Nejdřív připoj Xbox")
            if (tvJmeno.isNotBlank()) append(", TV $tvJmeno")
        }
        hrat.obsah.addView(Metro.text(c, podHrat, 13f, Metro.TEXT_NA_CERVENE, 400), odsazeni(top = 3))
        hrat.setOnClickListener { if (nejakaKonzole) otevriHrani() else ukazPripojeniXbox() }
        sloupec.addView(hrat, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (u * 1.35f).toInt()))
        // stav (tečka a karta jen kvůli obnovStav – v dlaždicích se neukazují)
        stavTecka = View(this)
        stavKarta = svisle()
        prepnoutBtn = null; ps5Radek = null; pripojitBtn = null

        // 2) Konzole + Televize
        val r2 = Metro.radek(c)
        val konzole = Metro.Dlazdice(c, Metro.MODRA, Metro.MODRA_VYBER, lista = false)
        konzole.obsah.addView(Metro.ikona(c, R.drawable.ik_sports_esports, 30f, Metro.PRIMARNI))
        konzole.mezera()
        konzole.obsah.addView(Metro.text(c, "Konzole", 12f, Metro.OBRYS, 600, 0.04f))
        konzole.obsah.addView(Metro.text(c, "Xbox", 26f, Metro.TEXT, 300), odsazeni(top = 2))
        xboxRadekText = if (xbox != null) t("sparovano") else "Nepřipojeno – klepni"
        konzole.obsah.addView(Metro.text(c, xboxRadekText, 12f, if (xbox != null) Metro.ZLUTA else Metro.TEXT_NA_MODRE, 600).also { xboxRadek = it }, odsazeni(top = 4))
        konzole.setOnClickListener { ukazPripojeniXbox() }
        r2.addView(konzole, Metro.lp(2 * u + gap, (u * 1.55f).toInt()))
        val tv = Metro.Dlazdice(c, Metro.MODRA, Metro.MODRA_VYBER, lista = false)
        tv.obsah.addView(Metro.ikona(c, R.drawable.ik_tv, 30f, Metro.PRIMARNI))
        tv.mezera()
        tv.obsah.addView(Metro.text(c, "Televize", 12f, Metro.OBRYS, 600, 0.04f))
        tv.obsah.addView(Metro.text(c, tvJmeno.ifBlank { "Vyhledat" }, 26f, Metro.TEXT, 300).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END }, odsazeni(top = 2))
        tv.obsah.addView(Metro.text(c, if (tvJmeno.isBlank()) "Klepni a najdi televizi" else "Titulky se ukážou tady", 12f, Metro.TEXT_NA_MODRE, 600), odsazeni(top = 4))
        tv.setOnClickListener { prevzitSparovaniZTv() }
        r2.addView(tv, Metro.lp(2 * u + gap, (u * 1.55f).toInt(), gap))
        sloupec.addView(r2, odsazeni(top = 4))

        // 3) malé dlaždice: Čeština, AI, Dialogy, Menu
        val r3 = Metro.radek(c)
        fun mala(ikona: Int, nazev: String, odznak: String?, odznakBarva: Int, lista: Boolean, akce: () -> Unit) =
            Metro.Dlazdice(c, Metro.MODRA, Metro.MODRA_VYBER, lista).apply {
                obsah.setPadding(dp(9), dp(9), dp(9), dp(9))
                val nahore = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
                nahore.addView(Metro.ikona(c, ikona, 20f, Metro.PRIMARNI))
                nahore.addView(View(c), LinearLayout.LayoutParams(0, 0, 1f))
                if (odznak != null) nahore.addView(Metro.text(c, odznak, 11f, odznakBarva, 700))
                obsah.addView(nahore)
                mezera()
                obsah.addView(Metro.text(c, nazev, 12.5f, Metro.TEXT, 600))
                setOnClickListener { akce() }
            }
        val j = Texty.JAZYKY.firstOrNull { it.kod == jazyk } ?: Texty.JAZYKY[0]
        r3.addView(mala(R.drawable.ik_translate, j.nazev, null, 0, false) { ukazJazyk() }, Metro.lp(u, (u * 0.95f).toInt()))
        val aiZap = AiPrekladac.klic(c).isNotEmpty() && AiPrekladac.povoleno(c)
        r3.addView(mala(R.drawable.ik_auto_awesome, "AI překlad", if (aiZap) "ZAP" else "VYP", if (aiZap) Metro.ZLUTA else Metro.OBRYS, aiZap) {
            Nabidky.aiNastaveni(this) { ukazHlavniTelefon() }
        }, Metro.lp(u, (u * 0.95f).toInt(), gap))
        r3.addView(mala(R.drawable.ik_chat, "Dialogy", if (nast.dialogy) "ZAP" else "VYP", if (nast.dialogy) Metro.ZLUTA else Metro.OBRYS, nast.dialogy) {
            nast.dialogy = !nast.dialogy; ukazHlavniTelefon()
        }, Metro.lp(u, (u * 0.95f).toInt(), gap))
        r3.addView(mala(R.drawable.ik_widgets, "Menu", if (Volby.zapnuto) "ZAP" else "VYP", if (Volby.zapnuto) Metro.ZLUTA else Metro.OBRYS, Volby.zapnuto) {
            Volby.zapnuto = !Volby.zapnuto; Volby.uloz(this); ukazHlavniTelefon()
        }, Metro.lp(u, (u * 0.95f).toInt(), gap))
        sloupec.addView(r3, odsazeni(top = 4))

        // 4) Tabulky překladů – široká šedá
        val tab = Metro.Dlazdice(c, Metro.SEDA_DLAZDICE, Metro.SEDA_VYBER, lista = false)
        val t1 = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
        t1.addView(Metro.ikona(c, R.drawable.ik_table_chart, 22f, Metro.ZLUTA))
        t1.addView(Metro.text(c, "Tabulky překladů", 16f, Metro.TEXT, 600), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(8) })
        val pocet = Tabulka.pocet
        t1.addView(Metro.text(c, if (pocet > 0) "%,d vět".format(pocet).replace(',', ' ') else "žádné", 11f, Metro.PRIMARNI, 700).apply {
            setBackgroundColor(Metro.POZADI); setPadding(dp(8), dp(3), dp(8), dp(3))
        })
        tab.obsah.addView(t1)
        tab.mezera()
        val hry = Tabulka.hry()
        tab.obsah.addView(Metro.text(c, if (hry.isNotEmpty()) hry.joinToString(", ") else "Klepni a nahraj komunitní češtinu hry (.tsv.gz)", 13f, Metro.TEXT_NA_MODRE, 400).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        })
        tab.setOnClickListener { Nabidky.vyberTabulku(this) }
        sloupec.addView(tab, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (u * 1.05f).toInt()).apply { topMargin = dp(4) })

        // 5) Vzhled titulků + O aplikaci
        val r5 = Metro.radek(c)
        fun stredni(ikona: Int, nadpis: String, nazev: String, akce: () -> Unit) = Metro.Dlazdice(c, Metro.MODRA, Metro.MODRA_VYBER, false).apply {
            val r = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
            val txt = svisle()
            txt.addView(Metro.text(c, nadpis, 11f, Metro.OBRYS, 600, 0.04f))
            txt.addView(Metro.text(c, nazev, 15f, Metro.TEXT, 600), odsazeni(top = 2))
            r.addView(txt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            r.addView(Metro.ikona(c, ikona, 26f, Metro.PRIMARNI))
            obsah.gravity = Gravity.CENTER_VERTICAL
            obsah.addView(r)
            setOnClickListener { akce() }
        }
        r5.addView(stredni(R.drawable.ik_text_fields, "Nastavení", "Vzhled titulků") { otevriHrani() }, Metro.lp(2 * u + gap, (u * 0.95f).toInt()))
        r5.addView(stredni(R.drawable.ik_info, "Verze", verze.substringAfter("0.20.1-").ifBlank { verze }) { oAplikaci() }, Metro.lp(2 * u + gap, (u * 0.95f).toInt(), gap))
        sloupec.addView(r5, odsazeni(top = 4))

        // podpis Fuki
        sloupec.addView(FukiZnacka.podpis(this), odsazeni(top = 18))

        setContentView(ScrollView(this).apply {
            setBackgroundColor(Metro.POZADI)
            isFillViewport = true
            addView(sloupec)
        })
        window.statusBarColor = Metro.POZADI
        window.navigationBarColor = Metro.POZADI
        obnovStav()
    }

    private fun otevriHrani() {
        if (nejakaKonzole) startActivity(Intent(this, TelefonActivity::class.java)) else ukazPripojeniXbox()
    }

    /** O aplikaci: verze, poděkování, podpis, kontrola aktualizace. */
    private fun oAplikaci() {
        val d = resources.displayMetrics.density
        val obsah = svisle().apply { setPadding((22 * d).toInt(), (10 * d).toInt(), (22 * d).toInt(), 0) }
        obsah.addView(Metro.text(this, "Verze $verze", 15f, Color.WHITE, 600))
        obsah.addView(Metro.text(this, t("zasluhy"), 13f, Metro.TEXT_NA_MODRE, 400).apply { setLineSpacing(0f, 1.25f) }, odsazeni(top = 10))
        obsah.addView(FukiZnacka.podpis(this), odsazeni(top = 12))
        android.app.AlertDialog.Builder(this)
            .setTitle("O aplikaci")
            .setView(ScrollView(this).apply { addView(obsah) })
            .setPositiveButton("Zkontrolovat aktualizace") { _, _ -> Aktualizace.zkontroluj(this, rucne = true) }
            .setNeutralButton("Změnil jsem hru") { _, _ ->
                RemotePlayService.zmenaHry(this)
                android.widget.Toast.makeText(this, if (RemotePlayService.aktivni) "Zjišťuji novou hru…" else "Překlad neběží", android.widget.Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Zavřít", null)
            .show()
    }

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == Nabidky.KOD_TABULKA && resultCode == RESULT_OK)
            Nabidky.zpracujTabulku(this, data) { if (obrazovka == "hlavni") ukazHlavni() }
    }

    // ------------------------------------------------------------------ připojení ke konzoli

    private var nalezenaIp: String? = null

    /** Tlačítko „Připojit / Znovu spárovat“ (telefon ho schová, když najde televizi s PlayCaption). */
    private var pripojitBtn: View? = null

    /**
     * Telefon: „Vyhledat televizi“ → seznam nalezených televizí → klepnout na svoji → opsat kód z televize.
     * Nenajde nic → zkusit znovu nebo zadat adresu televize ručně (je vidět v okně na televizi).
     */
    private fun prevzitSparovaniZTv() {
        val cekej = android.app.AlertDialog.Builder(this).setMessage(t("prevzitHledam")).show()
        Propojeni.hledej(this, 3000) { seznam ->
            ui.post {
                runCatching { cekej.dismiss() }
                if (isFinishing) return@post
                if (seznam.isEmpty()) {
                    android.app.AlertDialog.Builder(this).setMessage(t("prevzitNenalezena"))
                        .setPositiveButton(t("pridatZnovu")) { _, _ -> prevzitSparovaniZTv() }
                        .setNeutralButton(t("pridatAdresa")) { _, _ -> zadejAdresuTv() }
                        .setNegativeButton(android.R.string.cancel, null).show()
                } else {
                    android.app.AlertDialog.Builder(this).setTitle(t("pridatNalezena"))
                        .setItems(seznam.map { "📺  " + it.jmeno }.toTypedArray()) { _, i -> prevzitOd(seznam[i]) }
                        .setNegativeButton(android.R.string.cancel, null).show()
                }
            }
        }
    }

    /** Ruční adresa televize (když se telefon a televize v síti „nevidí“). */
    private fun zadejAdresuTv() {
        val pole = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_PHONE
            textSize = 24f
            gravity = Gravity.CENTER
            hint = "192.168.1.20"
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(t("pridatAdresa"))
            .setMessage(t("pridatAdresaText"))
            .setView(pole)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val ip = pole.text.toString().trim().replace(",", ".")
                if (ip.isNotEmpty()) prevzitOd(Propojeni.Televize("rucne", ip, ip))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    // ---------------------------------------------------------- televize: „+ Přidat Android zařízení“

    /** Dočasný příjem (jen kvůli přidání zařízení), když televize zrovna nečeká na telefon. */
    private var pridaniPrijimac: Propojeni.Prijimac? = null

    /**
     * Okno na televizi: QR kód ke stažení aplikace, adresa televize a 4místný kód (platí 180 s).
     * Telefon klepne na „Vyhledat televizi“, vybere ji a opíše kód → okno samo zmizí.
     */
    private fun pridatZarizeni() {
        if (!PrijemService.bezi && pridaniPrijimac == null)
            pridaniPrijimac = runCatching { Propojeni.Prijimac(this, priTitulku = { _, _, _, _, _ -> }) }.getOrNull()

        val obsah = svisle().apply { setPadding(dp(24), dp(18), dp(24), dp(8)) }
        val radek = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
        if (STAHNOUT_URL.isNotEmpty()) radek.addView(android.widget.ImageView(this).apply {
            setImageBitmap(PrihlaseniTelefonem.qr(STAHNOUT_URL, 360))
        }, LinearLayout.LayoutParams(dp(170), dp(170)))
        val kroky = svisle().apply { setPadding(if (STAHNOUT_URL.isNotEmpty()) dp(22) else 0, 0, 0, 0) }
        kroky.addView(text(t(if (STAHNOUT_URL.isNotEmpty()) "pridatKrok1" else "pridatKrok1BezQr"), 18f, Color.WHITE))
        kroky.addView(text(t("pridatKrok2"), 18f, Color.WHITE), odsazeni(top = 10))
        kroky.addView(text(t("pridatKrok3"), 18f, Color.WHITE), odsazeni(top = 10))
        radek.addView(kroky, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        obsah.addView(radek)
        val kodText = text("", 52f, Color.parseColor("#3CFF6E"), tucne = true).apply { gravity = Gravity.CENTER; letterSpacing = 0.3f }
        obsah.addView(kodText, odsazeni(top = 14))
        val odpocet = text("", 16f, TEXT2).apply { gravity = Gravity.CENTER }
        obsah.addView(odpocet)
        obsah.addView(text(t("pridatTvAdresa").replace("{ip}", PrihlaseniTelefonem.mojeIp(this) ?: "?"), 14f, TEXT2), odsazeni(top = 12))

        var okno: android.app.AlertDialog? = null
        val tikani = object : Runnable {
            override fun run() {
                val zbyva = ((Propojeni.Pridani.platiDo - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
                if (Propojeni.Pridani.kod != null && zbyva > 0) {
                    kodText.text = Propojeni.Pridani.kod!!.toCharArray().joinToString(" ")
                    odpocet.text = t("pridatPlati").replace("{s}", zbyva.toString())
                    ui.postDelayed(this, 1000)
                } else {
                    kodText.text = "– – – –"
                    odpocet.text = t("pridatVyprsel")
                    okno?.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.visibility = View.VISIBLE
                }
            }
        }
        fun novyKod() {
            Propojeni.Pridani.novy()
            okno?.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.visibility = View.GONE
            ui.removeCallbacks(tikani); tikani.run()
        }
        Propojeni.Pridani.hotovo = { jmeno ->
            ui.post {
                prefsBeta.edit().putString("pridaneZarizeni", jmeno).apply()
                runCatching { okno?.dismiss() }
                zprava(t("pridatHotovo").replace("{jmeno}", jmeno))
                if (obrazovka == "hlavni") ukazHlavni()
            }
        }
        okno = android.app.AlertDialog.Builder(this)
            .setTitle(t("pridatNadpis"))
            .setView(obsah)
            .setPositiveButton(t("pridatNovyKod"), null)
            .setNegativeButton(t("pridatZavrit"), null)
            .setOnDismissListener {
                ui.removeCallbacks(tikani)
                Propojeni.Pridani.zrus()
                pridaniPrijimac?.zavri(); pridaniPrijimac = null
            }
            .show()
        okno?.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setOnClickListener { novyKod() }
        novyKod()
    }

    private fun prevzitOd(tv: Propojeni.Televize) {
        Thread {
            val odp = Propojeni.sparovaniDotaz(tv, "ZADOST\t${Propojeni.jmenoZarizeni(this)}")
            ui.post {
                if (isFinishing) return@post
                when {
                    odp == null -> zprava(t("prevzitNeodpovida"))
                    odp[0] == "CHYBA" -> android.app.AlertDialog.Builder(this).setMessage(t("prevzitNesparovano"))
                        .setPositiveButton(t("pridatSparovatTel")) { _, _ -> ukazPripojeniXbox() }
                        .setNegativeButton(android.R.string.cancel, null).show()
                    else -> zadejKodZTv(tv)
                }
            }
        }.start()
    }

    private fun zadejKodZTv(tv: Propojeni.Televize) {
        val pole = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            textSize = 28f
            gravity = Gravity.CENTER
            hint = "0000"
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(t("prevzitKodNadpis"))
            .setMessage(t("prevzitKodText"))
            .setView(pole)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val kod = pole.text.toString().trim()
                Thread {
                    val odp = Propojeni.sparovaniDotaz(tv, "KOD\t$kod")
                    ui.post {
                        if (isFinishing) return@post
                        if (odp != null && odp[0] == "DATA" && odp.size >= 5) {
                            // PS5 (jen když ji televize má spárovanou)
                            if (odp[1] != "null" && odp[1].isNotEmpty() && odp[3].isNotEmpty()) {
                                val u = RemotePlayUdaje(this)
                                u.accountId = odp[1]
                                u.ipKonzole = odp[2]
                                u.registKey = android.util.Base64.decode(odp[3], android.util.Base64.NO_WRAP)
                                u.rpKey = android.util.Base64.decode(odp[4], android.util.Base64.NO_WRAP)
                            }
                            // Xbox (když ho televize má) – telefon se nemusí přihlašovat zvlášť
                            XboxUcet.prevezmi(this, odp)
                            zprava(t("prevzitHotovo"))
                            ukazHlavni()
                        } else if (odp == null) zprava(t("prevzitNeodpovida"))
                        else zprava(t("prevzitChybaKod"))
                    }
                }.start()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun zprava(s: String) {
        runCatching { android.app.AlertDialog.Builder(this).setMessage(s).setPositiveButton(android.R.string.ok, null).show() }
    }

    /**
     * Televize: než se začne přihlašovat k PS účtu – upozornit, že s Android telefonem / tabletem
     * se v televizi přihlašovat nemusí (lidi na „Připojit“ kliknou automaticky).
     */
    private fun upozorneniPredPrihlasenim(xbox: Boolean = false) {
        val obsah = svisle().apply { setPadding(dp(28), dp(22), dp(28), dp(6)) }
        obsah.addView(text(t("upozorneniNadpis"), 26f, Color.parseColor("#FFC24B"), tucne = true))
        obsah.addView(text(if (xbox) t("upozorneniText").replace("PS ", "Xbox ") else t("upozorneniText"), 21f, Color.WHITE), odsazeni(top = 12))
        android.app.AlertDialog.Builder(this)
            .setView(obsah)
            .setNegativeButton(t("upozorneniZpet")) { _, _ ->
                // bude používat mobilní aplikaci → druhá tabulka: kam kliknout; po OK výběr na dlaždici „S telefonem“
                val o2 = svisle().apply { setPadding(dp(28), dp(22), dp(28), dp(6)) }
                o2.addView(text(t("upozorneni2Text"), 22f, Color.WHITE))
                android.app.AlertDialog.Builder(this)
                    .setView(o2)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
            .setPositiveButton(t("upozorneniOk")) { _, _ -> ukazPripojeniXbox() }
            .show()
            .getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.requestFocus()
    }

    private fun ukazPripojeni() {
        zastavServer()
        obrazovka = "pripojeni"
        val sloupec = svisle()
        sloupec.addView(nadpisLogo())
        sloupec.addView(text(t("pripojitKonzoli"), 28f, Color.WHITE, tucne = true), odsazeni(bottom = 16))
        if (telefon) {
            val kTv = karta()
            kTv.addView(text(t("maTvNadpis"), 18f, Color.parseColor("#FFC24B"), tucne = true))
            kTv.addView(text(t("maTv"), 16f, Color.WHITE), odsazeni(top = 6))
            kTv.addView(tlacitkoZelene(t("prevzitZTv")) { prevzitSparovaniZTv() }, odsazeni(top = 10))
            // bez televize → sjet rovnou k přihlášení pod tímhle rámečkem
            kTv.addView(tlacitko(t("nemamTv"), 15f) {
                var v: View = kTv; var y = kTv.bottom
                while (v.parent is View && v.parent !is ScrollView) { v = v.parent as View; y += v.top }
                (v.parent as? ScrollView)?.smoothScrollTo(0, y)
            }, odsazeni(top = 8))
            sloupec.addView(kTv, odsazeni(bottom = 12))
        }

        // 0) než začneš – je potřeba druhý účet na PS5
        val k0 = karta()
        k0.addView(text(t("predemNadpis"), 20f, Color.parseColor("#FFC24B"), tucne = true))
        k0.addView(text(t("predem"), 17f, Color.WHITE), odsazeni(top = 6))
        sloupec.addView(k0, odsazeni(bottom = 12))

        // 1) účet PlayStation
        val k1 = karta()
        k1.addView(text(t("krok1"), 18f, Color.WHITE))
        val ucetStav = text(if (udaje.accountIdBajty() != null) t("ucetOk") else "", 17f, Color.parseColor("#7DFF9A"))
        k1.addView(ucetStav, odsazeni(top = 6))
        k1.addView(tlacitkoCervene(if (udaje.accountIdBajty() != null) t("zmenitUcet") else t("prihlasit")) { ukazPsnTelefonem() }, obal(top = 8))
        sloupec.addView(k1, odsazeni(bottom = 12))

        // 2) hledání PS5
        val k2 = karta()
        k2.addView(text(t("krok2"), 18f, Color.WHITE))
        val hledaniStav = text(t("hledam"), 17f, TEXT2)
        k2.addView(hledaniStav, odsazeni(top = 6))
        fun hledej() {
            hledaniStav.text = t("hledam")
            Thread {
                val seznam = runCatching { HledacPS.hledej() }.getOrDefault(emptyList())
                runOnUiThread {
                    val ps = seznam.firstOrNull { it.typ == "PS5" } ?: seznam.firstOrNull()
                    nalezenaIp = ps?.ip
                    hledaniStav.text = if (ps != null) "✔ ${t("nalezena")}: ${ps.jmeno} (${ps.ip})" else "✘ ${t("nenalezena")}"
                    hledaniStav.setTextColor(if (ps != null) Color.parseColor("#7DFF9A") else Color.parseColor("#FF8A8A"))
                }
            }.start()
        }
        k2.addView(tlacitko(t("hledatZnovu"), 18f) { hledej() }.apply { setPadding(dp(20), dp(10), dp(20), dp(10)) }, obal(top = 8))
        sloupec.addView(k2, odsazeni(bottom = 12))

        // 3) PIN
        val k3 = karta()
        k3.addView(text(t("krok3"), 18f, Color.WHITE))
        val pin = EditText(this).apply {
            hint = "12345678"
            textSize = 26f * SKALA
            setTextColor(Color.WHITE)
            setHintTextColor(SEDA)
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_DONE
            setSingleLine()
            background = vyber(Color.parseColor("#26262E"), CERVENA)
            setPadding(dp(18), dp(10), dp(18), dp(10))
        }
        k3.addView(pin, LinearLayout.LayoutParams(dp(320), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        val parovaniStav = text("", 17f, TEXT2)
        k3.addView(tlacitkoZelene(t("sparovat")) {
            val ip = nalezenaIp ?: udaje.ipKonzole
            val cislo = pin.text.toString().filter { it.isDigit() }
            when {
                udaje.accountIdBajty() == null -> parovaniStav.text = t("nejdrivUcet")
                ip == null -> parovaniStav.text = t("nenalezena")
                cislo.length != 8 -> parovaniStav.text = t("pinChyba")
                else -> {
                    parovaniStav.text = t("paruji")
                    Parovani.sparuj(this, ip, cislo.toInt(), log = {}, hotovo = { ok, zprava ->
                        runOnUiThread {
                            parovaniStav.text = if (ok) "✔ ${t("sparovano")}" else "✘ $zprava"
                            if (ok) {
                                AutoStart.nastavZapnuto(this, true)
                                if (!telefon && Settings.canDrawOverlays(this)) run { AutoStart.uspech(this); RemotePlayService.spustVse(this, prevzit = true) }
                                ui.postDelayed({ ukazHlavni() }, 1200)
                            }
                        }
                    })
                }
            }
        }, obal(top = 10))
        k3.addView(parovaniStav, odsazeni(top = 6))
        sloupec.addView(k3, odsazeni(bottom = 12))

        sloupec.addView(tlacitko(t("zpet"), 18f) { ukazHlavni() }.apply { setPadding(dp(20), dp(10), dp(20), dp(10)) }, obal())
        nastavObsah(sloupec, sirka = sirkaObsahu)
        hledej()
    }

    // ------------------------------------------------------------------ přihlášení PSN (prohlížeč)

    @SuppressLint("SetJavaScriptEnabled")
    private fun ukazPsn() {
        obrazovka = "psn"
        val cookies = CookieManager.getInstance()
        cookies.removeAllCookies(null)
        cookies.setAcceptCookie(true)
        val web = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            // Sony někdy odmítá vestavěný prohlížeč ("Can't connect to the server") → tvářit se jako běžný Chrome
            settings.userAgentString = settings.userAgentString
                .replace("; wv", "")
                .replace(Regex("Version/\\d+(\\.\\d+)* "), "")
            isFocusable = true
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    zachytKod(request.url.toString())

                override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                    zachytKod(url)
                }
            }
        }
        cookies.setAcceptThirdPartyCookies(web, true)
        setContentView(web)
        web.requestFocus()
        web.loadUrl(PsnPrihlaseni.ADRESA_PRIHLASENI)
    }

    private var zpracovavam = false

    /** Po přihlášení Sony přesměruje na adresu s kódem → vyměnit za Account ID. */
    private fun zachytKod(adresa: String): Boolean {
        val kod = PsnPrihlaseni.kodZAdresy(adresa) ?: return false
        vymenKod(kod)
        return true
    }

    /** Kód z přihlášení (z prohlížeče v TV nebo z telefonu) → Account ID. */
    private fun vymenKod(kod: String) {
        if (zpracovavam) return
        zpracovavam = true
        Thread {
            val id = runCatching { PsnPrihlaseni.accountId(kod) }.getOrNull()
            runOnUiThread {
                zpracovavam = false
                if (id != null) udaje.accountId = id
                ukazPripojeni()
                if (id == null) android.widget.Toast.makeText(this, t("ucetChyba"), android.widget.Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    // ------------------------------------------------------------------ přihlášení přes telefon (QR)

    private var server: PrihlaseniTelefonem? = null

    private fun zastavServer() {
        server?.zastav()
        server = null
    }

    private fun ukazPsnTelefonem() {
        obrazovka = "psnqr"
        zastavServer()
        val cesky = nast.jazykNeboCs in listOf("cs", "sk")
        server = PrihlaseniTelefonem(cesky) { kod -> runOnUiThread { if (obrazovka == "psnqr") vymenKod(kod) } }
            .also { it.spust() }
        val adresa = if (telefon) "http://127.0.0.1:${PrihlaseniTelefonem.PORT}"
            else "http://${PrihlaseniTelefonem.mojeIp(this) ?: "?"}:${PrihlaseniTelefonem.PORT}"

        val sloupec = svisle()
        sloupec.addView(nadpisLogo())
        sloupec.addView(text(t("qrNadpis"), 28f, Color.WHITE, tucne = true), odsazeni(bottom = 12))
        val k = karta()
        if (telefon) {
            k.addView(text(t("qrPopisTelefon"), 18f, Color.WHITE))
            k.addView(tlacitkoCervene(t("qrOtevrit")) {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(adresa))) }
            }, obal(top = 10))
        } else {
            val radek = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
            radek.addView(ImageView(this).apply {
                runCatching { setImageBitmap(PrihlaseniTelefonem.qr(adresa, 512)) }
            }, LinearLayout.LayoutParams(dp(330), dp(330)).apply { rightMargin = dp(30) })
            val popis = svisle()
            popis.addView(text(t("qrPopis"), 19f, Color.WHITE))
            popis.addView(text(adresa, 22f, Color.parseColor("#7DFF9A"), tucne = true), odsazeni(top = 12))
            radek.addView(popis, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            k.addView(radek)
        }
        k.addView(text(t("qrCekam"), 17f, TEXT2), odsazeni(top = 14))
        sloupec.addView(k, odsazeni(bottom = 12))
        // přihlášení přímo v prohlížeči televize Sony často odmítne (chyba) → na televizi nenabízet,
        // jen přes telefon (QR kód)
        if (telefon) sloupec.addView(tlacitko(t("qrVTelevizi"), 17f) { zastavServer(); ukazPsn() }
            .apply { setPadding(dp(20), dp(10), dp(20), dp(10)) }, obal(top = 4))
        sloupec.addView(tlacitko(t("zpet"), 17f) { ukazPripojeni() }
            .apply { setPadding(dp(20), dp(10), dp(20), dp(10)) }, obal(top = 10))
        nastavObsah(sloupec, sirka = sirkaObsahu)
    }

    // ------------------------------------------------------------------ Xbox (stejný postup jako PS5)

    private var xboxRelace: XboxSpojeni.Relace? = null
    private var xboxNalezena: XboxSpojeni.Konzole? = null

    private fun ukazPripojeniXbox() {
        zastavServer()
        obrazovka = "xbox"
        val obsah = svisle()
        // telefon: převzít přihlášení z televize (nemusí se přihlašovat znovu)
        if (telefon) {
            val tv = mdl(R.drawable.ik_tv, "Máš aplikaci už v televizi?", "Vyhledat televizi",
                "Nepřihlašuj se znovu. V televizi dej „Telefon → Přidat“ a tady klepni. Přihlášení si telefon vezme z televize.",
                barvaPopisu = Metro.TEXT) { prevzitSparovaniZTv() }
            obsah.addView(tv.d, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = mez * 4 })
        }
        val kroky = if (telefon) svisle() else vodorovne()
        fun pridej(v: View, i: Int) = kroky.addView(v,
            if (telefon) LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (i > 0) topMargin = mez }
            else LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { if (i > 0) leftMargin = mez })

        // 1) Microsoft účet
        val prihlasen = XboxUcet.prihlasen(this)
        val jmeno = XboxUcet.jmeno(this)
        val k1 = mdl(R.drawable.ik_check_circle, "1 · Účet Microsoft", if (prihlasen) "Přihlášeno" else "Přihlásit se",
            if (prihlasen) (jmeno.ifEmpty { "Účet z Xboxu" }) + " · OK změní účet" else "Účet, který máš na Xboxu",
            lista = prihlasen, barvaPopisu = if (prihlasen) Metro.ZLUTA else Metro.TEXT_NA_MODRE) {
            XboxUcet.odhlas(this); ukazXboxKod()
        }
        pridej(k1.d, 0)

        // 2) hledání Xboxu na účtu
        val k2 = mdl(R.drawable.ik_sports_esports, "2 · Najít Xbox", "…", "OK hledá znovu")
        fun hledej() {
            xboxNalezena = null
            k2.d.listaZapnuta = false
            if (!XboxUcet.prihlasen(this)) { k2.hlavni?.text = "Nejdřív účet"; return }
            k2.hlavni?.text = "Hledám…"
            Thread {
                val seznam = runCatching { XboxSpojeni.konzole(this) }
                runOnUiThread {
                    if (obrazovka != "xbox") return@runOnUiThread
                    val x = seznam.getOrNull()?.firstOrNull()
                    xboxNalezena = x
                    k2.hlavni?.text = x?.jmeno ?: "Nenalezen"
                    k2.popis?.text = when {
                        x != null -> "Nalezen na tvém účtu"
                        seznam.isFailure -> (seznam.exceptionOrNull()?.message ?: "Chyba") + " · OK znovu"
                        else -> "Je Xbox zapnutý a na stejné Wi-Fi? OK hledá znovu"
                    }
                    k2.d.listaZapnuta = x != null
                }
            }.start()
        }
        k2.d.setOnClickListener { hledej() }
        pridej(k2.d, 1)

        // 3) připojení – zkusí spojení a počká na obraz
        val k3 = mdl(R.drawable.ik_play_arrow, "3 · Připojit", "Připojit", "Zkontroluje, že chodí obraz",
            zaklad = Metro.CERVENA, vyber = Metro.CERVENA_VYBER, lista = true, barvaPopisu = Metro.TEXT_NA_CERVENE)
        k3.d.setOnClickListener {
            val x = xboxNalezena
            when {
                !XboxUcet.prihlasen(this) -> k3.popis?.text = "Nejdřív se přihlas (krok 1)"
                x == null -> k3.popis?.text = "Nejdřív najdi Xbox (krok 2)"
                xboxRelace != null -> {}
                else -> {
                    var hotovo = false
                    k3.hlavni?.text = "Připojuji…"
                    val r = XboxSpojeni.Relace(this, x.id, priStavu = { st ->
                        runOnUiThread {
                            if (obrazovka != "xbox" || hotovo) return@runOnUiThread
                            k3.popis?.text = st.removePrefix("✔ ").removePrefix("✘ ")
                            if (st.startsWith("✔")) {
                                hotovo = true
                                k3.hlavni?.text = "Připojeno"
                                XboxUcet.ulozKonzoli(this, x.id, x.jmeno)
                                xboxRelace?.zastav(); xboxRelace = null
                                ui.postDelayed({ if (obrazovka == "xbox") ukazHlavni() }, 1500)
                            } else if (st.startsWith("✘")) {
                                k3.hlavni?.text = "Zkusit znovu"
                                xboxRelace = null
                            }
                        }
                    })
                    xboxRelace = r
                    r.spust()
                }
            }
        }
        pridej(k3.d, 2)
        if (telefon) listOf(k1, k2, k3).forEach { it.d.minimumHeight = Metro.dp(this, 120f) }
        obsah.addView(kroky, if (telefon) LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        // než začneš (nastavení na Xboxu) + zpět
        val spodek = if (telefon) svisle() else vodorovne()
        val predem = mdl(R.drawable.ik_info, "Než začneš (na Xboxu stačí jednou)", null,
            t("xboxPredem").substringAfter("\n"), zaklad = Metro.SEDA_DLAZDICE, vyber = Metro.SEDA_VYBER, barvaPopisu = Metro.TEXT)
        val zpet = mdl(null, null, "Zpět", null, zaklad = Metro.SEDA_DLAZDICE, vyber = Metro.SEDA_VYBER) {
            xboxRelace?.zastav(); xboxRelace = null; ukazHlavni()
        }
        if (telefon) {
            spodek.addView(predem.d, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            spodek.addView(zpet.d, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Metro.dp(this, 56f)).apply { topMargin = mez })
        } else {
            spodek.addView(predem.d, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3f))
            spodek.addView(zpet.d, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { leftMargin = mez })
        }
        obsah.addView(spodek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = mez })
        metroRam("Připojit Xbox", obsah)
        (if (prihlasen) k2.d else k1.d).requestFocus()
        hledej()
    }

    /** Přihlášení Microsoft účtem: kód na microsoft.com/link (QR na televizi, odkaz v telefonu). */
    private fun ukazXboxKod() {
        obrazovka = "xboxkod"
        val obsah = if (telefon) svisle() else vodorovne()
        val qrStrana = (vyskaObr * 0.5f).toInt()
        // TV: vlevo bílá dlaždice s QR kódem
        val qr = ImageView(this).apply { setBackgroundColor(Color.WHITE); val p = (qrStrana * 0.05f).toInt(); setPadding(p, p, p, p) }
        if (!telefon) obsah.addView(qr, LinearLayout.LayoutParams(qrStrana, qrStrana).apply { gravity = Gravity.TOP; rightMargin = mez * 4 })
        val info = mdl(R.drawable.ik_check_circle, "Microsoft účet", "Připravuji kód…", null,
            zaklad = Metro.SEDA_DLAZDICE, vyber = Metro.SEDA_VYBER, barvaPopisu = Metro.TEXT)
        val prava = svisle()
        prava.addView(info.d, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        val kodT = mt("", 0.075f, 34f, Metro.ZLUTA, 700).apply { letterSpacing = 0.15f; visibility = View.GONE }
        prava.addView(kodT, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = mez * 3 })
        val tip = mt(t("xboxKodTip").removePrefix("💡 "), 0.022f, 13f, TEXT2).apply { visibility = View.GONE }
        prava.addView(tip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = mez * 3 })
        val tlacitka = vodorovne()
        val otevrit = mdl(null, null, "Otevřít přihlášení", null, zaklad = Metro.CERVENA, vyber = Metro.CERVENA_VYBER, lista = true)
        if (telefon) tlacitka.addView(otevrit.d, LinearLayout.LayoutParams(0, Metro.dp(this, 56f), 2f).apply { rightMargin = mez })
        otevrit.d.visibility = View.GONE
        val zpet = mdl(null, null, "Zpět", null, zaklad = Metro.SEDA_DLAZDICE, vyber = Metro.SEDA_VYBER) { ukazPripojeniXbox() }
        tlacitka.addView(zpet.d, LinearLayout.LayoutParams(if (telefon) 0 else (vyskaObr * 0.3f).toInt(),
            if (telefon) Metro.dp(this, 56f) else (vyskaObr * 0.1f).toInt(), if (telefon) 1f else 0f))
        prava.addView(tlacitka, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = mez * 4 })
        obsah.addView(prava, if (telefon) LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            else LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        metroRam("Přihlášení k Microsoft účtu", obsah)
        zpet.d.requestFocus()

        Thread {
            val kod = runCatching { XboxUcet.novyKod() }
            runOnUiThread {
                if (obrazovka != "xboxkod") return@runOnUiThread
                val kd = kod.getOrNull()
                if (kd == null) { info.hlavni?.text = "Kód se nepodařilo získat"; info.popis?.text = kod.exceptionOrNull()?.message ?: ""; return@runOnUiThread }
                val adresa = kd.adresa.removePrefix("https://").removePrefix("www.")
                if (telefon) {
                    info.hlavni?.text = "Přihlas se a zadej kód"
                    info.popis?.text = "Klepni na „Otevřít přihlášení“, přihlas se a vlož kód (už je zkopírovaný)."
                    otevrit.d.visibility = View.VISIBLE
                    otevrit.d.setOnClickListener {
                        runCatching {
                            val cb = getSystemService(android.content.ClipboardManager::class.java)
                            cb.setPrimaryClip(android.content.ClipData.newPlainText("kod", kd.uzivatelskyKod))
                        }
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(kd.adresa))) }
                    }
                } else {
                    runCatching { qr.setImageBitmap(PrihlaseniTelefonem.qr(kd.adresa, 512)) }
                    info.hlavni?.text = adresa
                    info.popis?.text = "Naskenuj QR kód telefonem (nebo otevři adresu na počítači), přihlas se a zadej tento kód:"
                }
                kodT.text = kd.uzivatelskyKod; kodT.visibility = View.VISIBLE
                tip.text = tip.text.toString() + "\n\n" + t("qrCekam"); tip.visibility = View.VISIBLE
                Thread {
                    val ok = XboxUcet.cekejNaPrihlaseni(this, kd) { obrazovka != "xboxkod" || isFinishing }
                    runOnUiThread {
                        if (obrazovka != "xboxkod") return@runOnUiThread
                        if (ok) ukazPripojeniXbox()
                        else android.widget.Toast.makeText(this, t("ucetChyba"), android.widget.Toast.LENGTH_LONG).show()
                    }
                }.start()
            }
        }.start()
    }

    // ------------------------------------------------------------------ povolení

    /** Zkusil uživatel otevřít povolení? (po návratu bez povolení → ukázat návod) */
    private var zkouselPovoleni = false

    private fun pozadejOPovoleni() {
        zkouselPovoleni = true
        // co nejblíž k povolení: 1) stránka povolení pro PlayCaption, 2) seznam aplikací s tímhle povolením,
        // 3) nastavení aplikace PlayCaption (tam bývá „Zobrazovat přes jiné aplikace“), 4) až pak návod
        val pokusy = listOf(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION),
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
        )
        for (i in pokusy) {
            if (runCatching { startActivity(i) }.isSuccess) return
        }
        // televize stránku s povolením nemá vůbec (schovaná) → návod
        ukazNavodPovoleni()
    }

    /** Návod, když televize povolení "Zobrazovat přes jiné aplikace" schovává. */
    private fun ukazNavodPovoleni() {
        zkouselPovoleni = false
        val ip = runCatching { PrihlaseniTelefonem.mojeIp(this) }.getOrNull() ?: "IP_TELEVIZE"
        runCatching {
            android.app.AlertDialog.Builder(this)
                .setTitle(t("povoleniNavodNadpis"))
                .setMessage(t("povoleniNavod").replace("%IP%", ip))
                .setPositiveButton("OK", null)
                .show()
        }
    }

    // ------------------------------------------------------------------ [Fuki] Metro: společný rám a dlaždice

    private val vyskaObr get() = resources.displayMetrics.heightPixels
    /** Mezera mezi dlaždicemi. */
    private val mez get() = if (telefon) Metro.dp(this, 4f) else (vyskaObr * 0.008f).toInt().coerceAtLeast(4)

    /** Text: na televizi velikost jako podíl výšky obrazovky (vejde se na jednu obrazovku), na telefonu v sp. */
    private fun mt(s: CharSequence, tv: Float, tel: Float, barva: Int = Metro.TEXT, vaha: Int = 400) =
        Metro.text(this, s, 1f, barva, vaha).apply {
            if (telefon) textSize = tel else setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, vyskaObr * tv)
            setLineSpacing(0f, 1.15f)
        }

    /** Dlaždice a její texty (hlavní a popisek jdou měnit). */
    private class Dl(val d: Metro.Dlazdice, val hlavni: TextView?, val popis: TextView?)

    /** Dlaždice: ikona, malý nadpis, hlavní text, popisek. Bez akce = jen informace (ovladač ji přeskočí). */
    private fun mdl(
        ikona: Int?, nadpis: String?, hlavni: String?, popis: String?,
        zaklad: Int = Metro.MODRA, vyber: Int = Metro.MODRA_VYBER, lista: Boolean = false,
        barvaPopisu: Int = Metro.TEXT_NA_MODRE, akce: (() -> Unit)? = null
    ): Dl {
        val d = Metro.Dlazdice(this, zaklad, vyber, lista)
        val p = if (telefon) Metro.dp(this, 14f) else (vyskaObr * 0.024f).toInt()
        d.obsah.setPadding(p, p, p, p)
        if (ikona != null) {
            val s = if (telefon) Metro.dp(this, 26f) else (vyskaObr * 0.05f).toInt()
            d.obsah.addView(ImageView(this).apply {
                setImageResource(ikona); setColorFilter(if (zaklad == Metro.CERVENA) Color.WHITE else Metro.PRIMARNI)
            }, LinearLayout.LayoutParams(s, s).apply { bottomMargin = p / 2 })
        }
        d.mezera()
        nadpis?.let { d.obsah.addView(mt(it, 0.02f, 11f, if (zaklad == Metro.CERVENA) Metro.TEXT_NA_CERVENE else Metro.OBRYS, 600)) }
        val h = hlavni?.let { mt(it, 0.036f, 19f, Color.WHITE, 300).also { t -> d.obsah.addView(t) } }
        val po = popis?.let { mt(it, 0.022f, 13f, barvaPopisu).also { t ->
            d.obsah.addView(t, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = p / 3 })
        } }
        if (akce != null) d.setOnClickListener { akce() } else { d.isFocusable = false; d.isClickable = false }
        return Dl(d, h, po)
    }

    /**
     * Rám obrazovky: hlavička (logo | nadpis … verze), obsah, patička s podpisem.
     * Televize: bez posouvání (obsah dostane zbytek výšky). Telefon: posouvá se.
     */
    private fun metroRam(nadpis: String, obsah: View, posouvat: Boolean = telefon) {
        val W = resources.displayMetrics.widthPixels; val H = vyskaObr
        val koren = svisle().apply {
            setBackgroundColor(Metro.POZADI)
            if (telefon) setPadding(Metro.dp(this@MainActivity, 16f), Metro.dp(this@MainActivity, 20f), Metro.dp(this@MainActivity, 16f), Metro.dp(this@MainActivity, 12f))
            else setPadding((W * 0.045f).toInt(), (H * 0.06f).toInt(), (W * 0.045f).toInt(), (H * 0.035f).toInt())
        }
        val vyskaLoga = if (telefon) Metro.dp(this, 32f) else (H * 0.06f).toInt()
        val hlava = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
        hlava.addView(ImageView(this).apply {
            setImageResource(R.drawable.hlavicka); adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_START
            setOnLongClickListener { Zaznam.ukaz(this@MainActivity, sVyvojem = true); true }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, vyskaLoga))
        if (!telefon) {
            hlava.addView(View(this).apply { setBackgroundColor(Color.parseColor("#424750")) },
                LinearLayout.LayoutParams(2, vyskaLoga).apply { leftMargin = mez * 4; rightMargin = mez * 4 })
            hlava.addView(mt(nadpis, 0.045f, 22f, Metro.TEXT, 300).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        } else hlava.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f))
        hlava.addView(mt(verze, 0.022f, 11f, Metro.OBRYS, 600))
        koren.addView(hlava)
        if (telefon) koren.addView(mt(nadpis, 0f, 28f, Metro.TEXT, 300),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = Metro.dp(this@MainActivity, 18f) })
        val nahore = if (telefon) Metro.dp(this, 14f) else (H * 0.035f).toInt()
        if (posouvat) koren.addView(ScrollView(this).apply {
            isFillViewport = true
            addView(obsah, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = nahore })
        else koren.addView(obsah, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply { topMargin = nahore })
        koren.addView(FukiZnacka.podpis(this), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = nahore / 2 })
        setContentView(koren)
        window.statusBarColor = Metro.POZADI
    }

    // ------------------------------------------------------------------ pomocníci pro vzhled

    /** Zmenšení celého prostředí (televize má velké písmo už samo o sobě). */
    private val SKALA = 0.72f
    private fun dp(x: Int) = (x * SKALA * resources.displayMetrics.density).toInt()

    /** Odsazení pro tlačítka – jen tak široká, jak je potřeba (ne přes celou šířku). */
    private fun obal(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top); bottomMargin = dp(bottom)
        }

    private fun zaobleni(barva: Int, radius: Float = 16f, ramecek: Int? = null) = GradientDrawable().apply {
        setColor(barva)
        // [Fuki] Metro HUD: ostré rohy
        cornerRadius = 0f
        if (ramecek != null) setStroke(dp(2), ramecek)
    }

    /** Pozadí, které při výběru ovladačem dostane výrazný rámeček. */
    private fun vyber(barva: Int, ramecekTrvale: Int? = null, radius: Float = 16f) = StateListDrawable().apply {
        // [Fuki] Metro HUD: vybraná položka = světlejší + žlutá lišta dole (jako titulek)
        val svetlejsi = androidx.core.graphics.ColorUtils.blendARGB(barva, Color.WHITE, 0.16f)
        fun sListou() = android.graphics.drawable.LayerDrawable(arrayOf(zaobleni(svetlejsi, radius, ramecekTrvale), android.graphics.drawable.ColorDrawable(Metro.ZLUTA))).apply {
            setLayerGravity(1, Gravity.BOTTOM or Gravity.FILL_HORIZONTAL)
            setLayerHeight(1, dp(4))
        }
        addState(intArrayOf(android.R.attr.state_focused), sListou())
        addState(intArrayOf(android.R.attr.state_pressed), zaobleni(svetlejsi, radius, ramecekTrvale))
        addState(intArrayOf(), zaobleni(barva, radius, ramecekTrvale))
    }

    private fun svisle() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun vodorovne() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

    private fun odsazeni(top: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(top); bottomMargin = dp(bottom)
        }

    private fun text(s: String, velikost: Float, barva: Int, tucne: Boolean = false) = TextView(this).apply {
        text = s
        textSize = velikost * SKALA
        setTextColor(barva)
        typeface = Metro.pismo(this@MainActivity, if (tucne) 600 else 400)
    }

    // [Fuki] Metro HUD: nadpis sekce velký a tenký, malými písmeny (jako pivot)
    private fun sekce(s: String) = TextView(this).apply {
        text = s.lowercase()
        textSize = 26f * SKALA
        setTextColor(Metro.TEXT)
        typeface = Metro.pismo(this@MainActivity, 300)
        setPadding(0, 0, 0, dp(8))
    }

    private fun karta() = svisle().apply {
        background = zaobleni(KARTA)
        setPadding(dp(22), dp(16), dp(22), dp(16))
    }

    // [Fuki] Metro HUD: malá hlavička (ikona + název) místo velkého loga
    private fun nadpisLogo() = ImageView(this).apply {
        setImageResource(R.drawable.hlavicka)
        adjustViewBounds = true
        scaleType = ImageView.ScaleType.FIT_START
        layoutParams = LinearLayout.LayoutParams(dp(220), ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(18) }
    }

    private fun tlacitko(s: String, velikost: Float, akce: () -> Unit) = TextView(this).apply {
        text = s
        textSize = velikost * SKALA
        setTextColor(Color.WHITE)
        typeface = Metro.pismo(this@MainActivity, 400)
        isFocusable = true
        isClickable = true
        background = vyber(KARTA)
        setOnClickListener { akce() }
    }

    private fun tlacitkoCervene(s: String, akce: () -> Unit) = tlacitko(s, 24f, akce).apply {
        typeface = Metro.pismo(this@MainActivity, 600)
        gravity = Gravity.CENTER
        background = vyber(CERVENA, radius = 999f)
        setPadding(dp(28), dp(14), dp(28), dp(14))
    }

    private fun tlacitkoZelene(s: String, akce: () -> Unit) = tlacitko(s, 20f, akce).apply {
        typeface = Metro.pismo(this@MainActivity, 600)
        gravity = Gravity.CENTER
        background = vyber(ZELENA, radius = 999f)
        setPadding(dp(26), dp(10), dp(26), dp(10))
    }

    /** Řádek "popis …… [Ano/Ne]" – OK přepíná. */
    private fun prepinac(popis: String, cti: () -> Boolean, zapis: (Boolean) -> Unit, zap: String, vyp: String, podpis: String? = null): View {
        val radek = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
        val texty = svisle()
        texty.addView(text(popis, 20f, Color.WHITE))
        // malé šedé vysvětlení pod názvem (co přesně se tím překládá)
        if (!podpis.isNullOrBlank()) texty.addView(text(podpis, 14f, TEXT2), odsazeni(top = 2))
        radek.addView(texty, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(12) })
        val pill = tlacitko("", 19f) {}.apply {
            typeface = Metro.pismo(this@MainActivity, 700)
            gravity = Gravity.CENTER
            minWidth = dp(120)
            setPadding(dp(20), dp(8), dp(20), dp(8))
        }
        fun obnov() {
            val ano = cti()
            pill.text = if (ano) zap else vyp
            pill.setTextColor(if (ano) Metro.ZLUTA else Metro.OBRYS)
            pill.background = vyber(if (ano) ZELENA else SEDA, radius = 999f)
        }
        pill.setOnClickListener { zapis(!cti()); obnov() }
        obnov()
        radek.addView(pill)
        return radek
    }

    private fun nastavObsah(obsah: View, sirka: Int = ViewGroup.LayoutParams.WRAP_CONTENT) {
        val ram = FrameLayout(this).apply {
            setBackgroundColor(CERNA)
            clipToPadding = false // značka BETA sedí v okraji
            if (telefon) setPadding(dp(20), dp(28), dp(20), dp(28)) else setPadding(dp(56), dp(36), dp(56), dp(36))
            addView(obsah, FrameLayout.LayoutParams(sirka, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL))
            addView(betaZnacka(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END).apply {
                topMargin = -dp(if (telefon) 16 else 20); rightMargin = -dp(if (telefon) 8 else 30)
            })
        }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(CERNA)
            isFillViewport = true
            addView(ram)
        })
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // během úvodní animace Fuki: OK / Zpět / Enter ji přeskočí
        if (FukiIntro.zachytKlavesu(event)) return true
        // tlačítko Menu na ovladači → vývojářská obrazovka
        if (event.action == KeyEvent.ACTION_UP && event.keyCode == KeyEvent.KEYCODE_MENU) {
            startActivity(Intent(this, VyvojActivity::class.java)); return true
        }
        return super.dispatchKeyEvent(event)
    }
}
