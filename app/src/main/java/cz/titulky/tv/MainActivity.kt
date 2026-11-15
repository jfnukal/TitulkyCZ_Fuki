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
        val AKTIVNI_JAZYKY = setOf("cs", "sk")
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

    private fun ukazUvod() {
        obrazovka = "uvod"
        val bar = View(this).apply { setBackgroundColor(CERVENA) }
        val drazka = FrameLayout(this).apply {
            background = zaobleni(Color.parseColor("#26262E"), 999f)
            addView(bar, FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT))
        }
        val koren = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.BLACK)
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.logo)
                adjustViewBounds = true
            }, LinearLayout.LayoutParams(if (telefon) dp(380) else dp(520), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(drazka, LinearLayout.LayoutParams(dp(340), dp(10)).apply { topMargin = dp(36) })
        }
        setContentView(koren)
        // animace načítání ~1,6 s
        val start = System.currentTimeMillis()
        val krok = object : Runnable {
            override fun run() {
                if (obrazovka != "uvod") return
                val podil = ((System.currentTimeMillis() - start) / 1600f).coerceAtMost(1f)
                bar.layoutParams = bar.layoutParams.apply { width = (drazka.width * podil).toInt() }
                bar.requestLayout()
                if (podil < 1f) ui.postDelayed(this, 30)
                // poprvé: nejdřív výběr jazyka, uvítání až potom (ve zvoleném jazyce)
                else if (nast.jazyk == null) ukazJazyk()
                else if (maUkazatBetu()) ukazBetu() else pokracuj()
            }
        }
        ui.post(krok)
    }

    private fun pokracuj() { if (nast.jazyk == null) ukazJazyk() else ukazHlavni() }

    // ------------------------------------------------------------------ beta: uvítání

    private val verze by lazy { runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "" }
    private val prefsBeta by lazy { getSharedPreferences("beta", MODE_PRIVATE) }

    /** Uvítání ukázat při prvních 3 spuštěních každé verze. */
    private fun maUkazatBetu(): Boolean {
        val klic = "uvitani_$verze"
        val kolikrat = prefsBeta.getInt(klic, 0)
        if (kolikrat >= 3) return false
        prefsBeta.edit().putInt(klic, kolikrat + 1).apply()
        return true
    }

    /** Malá značka "BETA 0.9" do pravého horního rohu. */
    private fun betaZnacka() = TextView(this).apply {
        text = verze
        textSize = 12f * SKALA
        typeface = Metro.pismo(this@MainActivity, 600)
        letterSpacing = 0.06f
        setTextColor(Metro.OBRYS)
        setBackgroundColor(Color.parseColor("#151B2A"))
        setPadding(dp(10), dp(4), dp(10), dp(4))
    }

    private fun ukazBetu() {
        obrazovka = "beta"
        okBtn = null
        val zelena = Color.parseColor("#3CFF6E")
        val box = svisle().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            background = zaobleni(Color.parseColor("#111116"), 26f, zelena)
            setPadding(dp(54), dp(30), dp(54), dp(30))
        }
        box.addView(text(t("betaNadpis"), 34f, zelena, tucne = true).apply { gravity = Gravity.CENTER })
        val radek = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
        radek.addView(text(t("betaText"), 21f, Color.parseColor("#E8E8EE")).apply {
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.3f)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        // QR na Facebook skupinu (jen na televizi – telefon by skenoval sám sebe)
        if (!telefon && SKUPINA_URL.isNotEmpty()) {
            val q = svisle().apply { gravity = Gravity.CENTER_HORIZONTAL }
            q.addView(ImageView(this).apply {
                setImageBitmap(PrihlaseniTelefonem.qr(SKUPINA_URL, 400))
                background = zaobleni(Color.WHITE, 14f)
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }, LinearLayout.LayoutParams(dp(190), dp(190)))
            q.addView(text(t("betaSkupina"), 15f, TEXT2).apply { gravity = Gravity.CENTER }, odsazeni(top = 8))
            radek.addView(q, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(36) })
        }
        box.addView(radek, odsazeni(top = 14))

        val stred = svisle().apply {
            gravity = Gravity.CENTER
            addView(ImageView(this@MainActivity).apply {
                setImageResource(R.drawable.logo); adjustViewBounds = true
            }, LinearLayout.LayoutParams(if (telefon) dp(300) else dp(340), ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(box, LinearLayout.LayoutParams(if (telefon) ViewGroup.LayoutParams.MATCH_PARENT else dp(1250), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(30) })
            // nemizí samo – člověk potvrdí, že si to přečetl
            addView(tlacitkoZelene(t("betaOk")) { if (obrazovka == "beta") pokracuj() }.also { okBtn = it },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(26) })
        }
        val koren = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(stred, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(betaZnacka(), FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
        }
        setContentView(koren)
        okBtn?.requestFocus()
    }

    /** Tlačítko OK na uvítání (výběr ovladačem rovnou na něm). */
    private var okBtn: View? = null

    // ------------------------------------------------------------------ výběr jazyka

    private fun ukazJazyk() {
        obrazovka = "jazyk"
        var vybrany = nast.jazyk?.takeIf { it in AKTIVNI_JAZYKY } ?: "cs"
        val sloupec = svisle().apply { gravity = Gravity.CENTER_HORIZONTAL }
        sloupec.addView(nadpisLogo())
        sloupec.addView(text(Texty.t(vybrany, "vyberJazyk"), 30f, Color.WHITE, tucne = true).apply { gravity = Gravity.CENTER })
        val popis = text(Texty.t(vybrany, "vyberJazykPopis"), 18f, TEXT2).apply { gravity = Gravity.CENTER }
        sloupec.addView(popis, odsazeni(top = 6, bottom = 20))

        val mrizka = svisle()
        val polozky = mutableListOf<TextView>()
        lateinit var pokracovat: TextView
        fun obnov() {
            polozky.forEachIndexed { i, v ->
                val j = Texty.JAZYKY[i]
                val aktivni = j.kod in AKTIVNI_JAZYKY
                v.text = "${j.vlajka}   ${j.nazev}" + when {
                    !aktivni -> "   · " + Texty.t(vybrany, "brzy")
                    j.kod == vybrany -> "      ✔"
                    else -> ""
                }
                v.background = vyber(KARTA, if (j.kod == vybrany) CERVENA else null)
                // zatím jen čeština a slovenština – ostatní šedé, nejdou vybrat
                v.alpha = if (aktivni) 1f else 0.35f
                v.isFocusable = aktivni
                v.isClickable = aktivni
            }
            pokracovat.text = Texty.t(vybrany, "pokracovat")
        }
        // dva sloupce jazyků
        Texty.JAZYKY.chunked(if (telefon) Texty.JAZYKY.size else 4).let { sloupce ->
            val radek = vodorovne()
            sloupce.forEach { cast ->
                val s = svisle()
                cast.forEach { j ->
                    val v = tlacitko("", 22f) {
                        if (j.kod !in AKTIVNI_JAZYKY) return@tlacitko
                        vybrany = j.kod
                        obnov()
                        sloupec.getChildAt(1).let { (it as TextView).text = Texty.t(vybrany, "vyberJazyk") }
                        popis.text = Texty.t(vybrany, "vyberJazykPopis")
                    }.apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(24), dp(14), dp(24), dp(14)) }
                    polozky += v
                    s.addView(v, LinearLayout.LayoutParams(if (telefon) ViewGroup.LayoutParams.MATCH_PARENT else dp(330), ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(8), dp(6), dp(8), dp(6)) })
                }
                radek.addView(s)
            }
            mrizka.addView(radek)
        }
        sloupec.addView(mrizka)
        pokracovat = tlacitkoCervene("") {
            nast.jazyk = vybrany
            // první spuštění: po výběru jazyka uvítání v tom jazyce
            if (obrazovka == "jazyk" && maUkazatBetu()) ukazBetu() else ukazHlavni()
        }
        sloupec.addView(pokracovat, LinearLayout.LayoutParams(if (telefon) ViewGroup.LayoutParams.MATCH_PARENT else dp(680), ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(20) })
        obnov()
        nastavObsah(sloupec, sirka = if (telefon) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT)
        polozky[Texty.JAZYKY.indexOfFirst { it.kod == vybrany }.coerceAtLeast(0)].requestFocus()
    }

    // ------------------------------------------------------------------ hlavní obrazovka

    private lateinit var stavText: TextView
    private lateinit var stavTecka: View
    private lateinit var stavKarta: LinearLayout

    private fun ukazHlavni() {
        if (!telefon && PrijemService.bezi) { ukazPrijem(); return }
        // [Fuki] telefon: nová obrazovka v dlaždicích
        if (telefon) { ukazHlavniTelefon(); return }
        obrazovka = "hlavni"
        val vlevo = svisle()
        val vpravo = svisle()

        // logo (podržením OK se otevře stará vývojářská obrazovka)
        vlevo.addView(nadpisLogo().apply {
            isFocusable = true
            setOnLongClickListener { Zaznam.ukaz(this@MainActivity, sVyvojem = true); true }
            // televize: menší logo, ať se celá stránka (i s QR podpory) vejde na jednu obrazovku
            if (!telefon) layoutParams = LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(2) }
        })

        // povolení přes jiné aplikace (jen když chybí)
        if (!telefon && !Settings.canDrawOverlays(this)) {
            val k = karta()
            k.addView(text(t("povoleniPopis"), 17f, Color.parseColor("#FFC24B")))
            k.addView(tlacitkoCervene(t("povoleni")) { pozadejOPovoleni() }, obal(top = 10))
            k.addView(tlacitko(t("povoleniNavodBtn"), 17f) { ukazNavodPovoleni() }
                .apply { setPadding(dp(20), dp(10), dp(20), dp(10)) }, obal(top = 8))
            vlevo.addView(k, odsazeni(bottom = 14))
        }

        // jazyk
        vlevo.addView(sekce(t("jazykPrekladu")))
        val j = Texty.JAZYKY.firstOrNull { it.kod == jazyk } ?: Texty.JAZYKY[0]
        val popisZdroje = t("zdroj")
        val jazykBtn = tlacitko("${j.vlajka}   ${j.nazev}   ›\n$popisZdroje", 22f) { ukazJazyk() }.apply {
            setPadding(dp(24), dp(14), dp(24), dp(14))
        }
        vlevo.addView(jazykBtn, odsazeni(bottom = if (telefon) 18 else 10))

        // konzole
        vlevo.addView(sekce(t("pripojitKonzoli")))
        val konzole = karta()
        // Xbox – nad PS5, úplně stejný řádek a tlačítko jako PS5
        val xbox = XboxUcet.konzole(this)
        val rx = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
        xboxRadekText = "🎮  Xbox" + if (xbox != null) "   ✔ ${t("sparovano")}" else ""
        rx.addView(text(xboxRadekText, 21f, Color.WHITE).also { xboxRadek = it },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        rx.addView(tlacitkoZelene(if (xbox != null) t("znovuSparovat") else t("pripojit")) {
            if (telefon) ukazPripojeniXbox() else upozorneniPredPrihlasenim(xbox = true)
        })
        konzole.addView(rx)
        konzole.addView(text(t("xboxPopis"), 14f, TEXT2), odsazeni(top = 6))
        konzole.addView(View(this).apply { setBackgroundColor(Color.parseColor("#424750")) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(10); bottomMargin = dp(8) })
        val r = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
        ps5RadekText = "🎮  PlayStation 5" + if (udaje.sparovano) "   ✔ ${t("sparovano")}" else ""
        r.addView(text(ps5RadekText, 21f, Color.WHITE).also { ps5Radek = it },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(tlacitkoZelene(if (udaje.sparovano) t("znovuSparovat") else t("pripojit")) {
            if (telefon) ukazPripojeni() else upozorneniPredPrihlasenim()
        }.also { pripojitBtn = it })
        konzole.addView(r)
        konzole.addView(text(t("pripojitPopis"), 14f, TEXT2), odsazeni(top = 6))
        // telefon: najít televizi a převzít od ní spárování s PS5 (bez přihlašování účtu a PINu z PS5)
        if (telefon) {
            konzole.addView(tlacitkoZelene(t("prevzitZTv")) { prevzitSparovaniZTv() }, odsazeni(top = 12))
            // nespárovaný telefon a v síti je televize s PlayCaption → přihlašování schovat, ať nikdo neklikne špatně
            if (!udaje.sparovano) Propojeni.hledej(this, 2500) { nalez ->
                ui.post {
                    // jen spárovaná televize umí telefonu předat spárování; nespárovaná jen ukazuje titulky
                    if (nalez.any { it.sparovano == true } && obrazovka == "hlavni" && !isFinishing) {
                        pripojitBtn?.visibility = View.GONE
                        konzole.addView(text(t("pridatNalezenaTv"), 15f, Color.parseColor("#3CFF6E")), odsazeni(top = 8))
                    }
                }
            }
        }
        // televize (spárovaná): přidat Android telefon / tablet – kód v okně, telefon ho jen opíše
        if (!telefon && udaje.sparovano) {
            konzole.addView(View(this).apply { setBackgroundColor(Color.parseColor("#424750")) },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(10); bottomMargin = dp(8) })
            val rz = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
            val pridane = prefsBeta.getString("pridaneZarizeni", "") ?: ""
            rz.addView(text("📱  " + t("pridatZarizeni") + "   " + (if (pridane.isNotEmpty()) "✔ $pridane" else t("pridatZadne")), 19f, Color.WHITE),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            rz.addView(tlacitkoZelene(t("pridatBtn")) { pridatZarizeni() })
            konzole.addView(rz)
        }
        vlevo.addView(konzole)

        // podpora – QR Platba (jen televize: telefon by musel skenovat sám sebe)
        if (!telefon && PODPORA_QR.isNotEmpty()) {
            vlevo.addView(sekce(t("podpora")), odsazeni(top = 6))
            val k = vodorovne().apply {
                gravity = Gravity.CENTER_VERTICAL
                background = zaobleni(KARTA, 16f, Color.parseColor("#5A4F22"))
                setPadding(dp(22), dp(8), dp(10), dp(8))
            }
            val txt = svisle()
            txt.addView(text(t("podporaNadpis"), 20f, Color.parseColor("#FFD54A"), tucne = true))
            txt.addView(text(t("podporaText"), 14f, Color.parseColor("#D8D8E0")).apply { setLineSpacing(0f, 1.2f) }, odsazeni(top = 4))
            // QR je česká QR Platba – slovenské banky ji nenačtou → IBAN k ručnímu zadání
            txt.addView(text(t("podporaIban"), 13f, Color.parseColor("#FFD54A")), odsazeni(top = 4))
            k.addView(txt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            k.addView(ImageView(this).apply {
                setImageBitmap(PrihlaseniTelefonem.qr(PODPORA_QR, 400))
                background = zaobleni(Color.WHITE, 14f)
                setPadding(dp(8), dp(8), dp(8), dp(8))
            }, LinearLayout.LayoutParams(dp(120), dp(120)).apply { leftMargin = dp(16) })
            vlevo.addView(k)
        }

        // stav
        stavKarta = vodorovne().apply {
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(18), dp(20), dp(18))
            isFocusable = true
            setOnClickListener { prepniPreklad() }
        }
        stavTecka = View(this).apply { background = zaobleni(Color.GRAY, 999f) }
        stavText = text("", 24f, Color.WHITE, tucne = true)
        stavKarta.addView(stavTecka, LinearLayout.LayoutParams(dp(18), dp(18)).apply { rightMargin = dp(16) })
        stavKarta.addView(stavText)
        val stavRadek = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
        stavRadek.addView(stavKarta, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        // obě konzole spárované → malé červené tlačítko na přepnutí (po najetí bílý rámeček)
        prepnoutBtn = tlacitko("", 20f) { prepniKonzoli() }.apply {
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            background = vyber(CERVENA, radius = 999f)
            setPadding(dp(24), dp(14), dp(24), dp(14))
            visibility = if (RemotePlayService.obeZapnute && udaje.sparovano && XboxUcet.konzole(this@MainActivity) != null) View.VISIBLE else View.GONE
        }
        stavRadek.addView(prepnoutBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(12) })
        vpravo.addView(stavRadek, odsazeni(top = if (telefon) 24 else 70, bottom = 22))

        // zobrazení
        if (!telefon) {
            vpravo.addView(sekce(t("zobrazeni")))
            val zobr = karta()
            zobr.addView(prepinac(t("prekryvat"), { nast.prekryvat }, { nast.prekryvat = it }, t("zapnuto"), t("vypnuto")))
            vpravo.addView(zobr, odsazeni(bottom = 18))
        }

        // co překládat
        vpravo.addView(sekce(t("coPrekladat")))
        val co = karta()
        co.addView(prepinac(t("dialogy"), { nast.dialogy }, { nast.dialogy = it }, t("ano"), t("ne"), t("dialogyPopis")))
        co.addView(View(this).apply { setBackgroundColor(SEDA) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1).apply { setMargins(0, dp(8), 0, dp(8)) })
        // menu a nápovědy se nepřekládají samy – v mobilní aplikaci si je každý přeloží žlutým rámečkem (Text)
        nast.napovedy = false
        run {
            val r = svisle()
            r.addView(text(t("napovedy"), 20f, Color.WHITE))
            r.addView(text(t("napovedyJenMobil"), 15f, TEXT2), odsazeni(top = 2))
            co.addView(r)
        }
        vpravo.addView(co, odsazeni(bottom = 22))

        var dlazdiceTv: View? = null
        var dlazdiceTel: View? = null
        if (telefon) {
            vpravo.addView(tlacitkoCervene(t("hotovo")) {
                if (nejakaKonzole) startActivity(Intent(this, TelefonActivity::class.java)) else ukazPripojeni()
            })
        } else {
            // "Jak chceš hrát?" – dvě velké volby místo nenápadného tlačítka
            vpravo.addView(sekce(t("hratJak")))
            // okraj kolem dlaždic, ať se zvětšená vybraná dlaždice neořízne
            val volby = vodorovne().apply { clipChildren = false; clipToPadding = false; setPadding(dp(12), dp(10), dp(12), dp(10)) }
            // slabá televize (dřív nestíhala číst nebo jí padalo spojení) → doporučit telefon
            val slaba = jeSlabaTv()
            dlazdiceTv = dlazdice("📺", t("hratTv"), if (slaba) t("hratTvSlaba") else t("hratTvPopis"), CERVENA) {
                if (slaba) varovaniSlabaTv() else hratTelevizi()
            }
            dlazdiceTel = dlazdice("📱➜📺", t("hratTel"), if (slaba) t("hratTelDoporuceno") else t("hratTelPopis"), Color.parseColor("#2A2C3E")) { hratSTelefonem() }
            volby.addView(dlazdiceTv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(18) })
            volby.addView(dlazdiceTel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            vpravo.addView(volby)
            vpravo.addView(text(t("hratVyber"), 16f, TEXT2).apply { gravity = Gravity.CENTER }, odsazeni(top = 10))
        }

        // tip: na čem nejvíc záleží rychlost titulků
        vpravo.addView(text(t("tipPripojeni"), 16f, TEXT2).apply {
            setLineSpacing(0f, 1.3f)
            background = zaobleni(KARTA, 16f)
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }, odsazeni(top = 18))
        // poděkování open-source projektu Chiaki / chiaki-ng (licence AGPLv3)
        vpravo.addView(text(t("zasluhy"), 13f, TEXT2).apply {
            setLineSpacing(0f, 1.25f)
            alpha = 0.85f
            setPadding(dp(24), dp(4), dp(24), dp(8))
        }, odsazeni(top = 10))
        // podpisová značka Fuki – patička
        vpravo.addView(FukiZnacka.podpis(this), odsazeni(top = 4, bottom = 8))

        val obsah = if (telefon) svisle().apply {
            addView(vlevo, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(vpravo, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        } else vodorovne().apply {
            addView(vlevo, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { rightMargin = dp(40) })
            addView(vpravo, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        nastavObsah(obsah, sirka = ViewGroup.LayoutParams.MATCH_PARENT)

        // automaticky spustit, když je vše připravené a uživatel překlad nevypnul
        if (!telefon && !PrijemService.bezi && nejakaKonzole && Settings.canDrawOverlays(this) && AutoStart.jeZapnuto(this) && !RemotePlayService.aktivni) {
            val up = getSharedPreferences("uspora", MODE_PRIVATE)
            if (up.getBoolean("padlo", false) && prefsBeta.getString("rezimHry", "tv") == "tv") {
                // minule v režimu „Jen televize“ spadla → nepřipojovat se samo dokola, zeptat se
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
        if (!telefon) (if (jeSlabaTv() || prefsBeta.getString("rezimHry", "tv") == "tel") dlazdiceTel else dlazdiceTv)?.requestFocus()
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
        } else if (!nejakaKonzole) { ukazPripojeni(); return }
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

    private fun ukazPrijem() {
        obrazovka = "prijem"
        val sloupec = svisle().apply { gravity = Gravity.CENTER_HORIZONTAL }
        sloupec.addView(nadpisLogo())
        sloupec.addView(text(t("prijemNadpis"), 32f, Color.WHITE, tucne = true).apply { gravity = Gravity.CENTER }, odsazeni(top = 14))

        // tři kroky s obrázky
        val kroky = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
        listOf("📱" to "ngKrok1", "🔗" to "ngKrok2", "🎮" to "ngKrok3").forEachIndexed { i, (ikona, klic) ->
            if (i > 0) kroky.addView(text("➜", 40f, Color.parseColor("#555555")), LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(dp(18), 0, dp(18), 0) })
            val k = svisle().apply {
                gravity = Gravity.CENTER_HORIZONTAL
                background = zaobleni(Color.parseColor("#1C1D28"), 26f).apply {
                    // krok 2 (spárování) je jen poprvé → žlutý rámeček, ať je vidět, že se týká rámečku pod ním
                    if (i == 1) setStroke(dp(2), Color.parseColor("#FFC24B"))
                }
                setPadding(dp(26), dp(18), dp(26), dp(18))
            }
            val nadpisKroku = "${t("ngKrokN")} ${i + 1}" + if (i == 1) " – ${t("ngKrok2N")}" else ""
            k.addView(text(nadpisKroku, 15f, CERVENA, tucne = true).apply { gravity = Gravity.CENTER })
            k.addView(text(ikona, 52f, Color.WHITE).apply { gravity = Gravity.CENTER }, odsazeni(top = 4))
            k.addView(text(t(klic), 20f, Color.WHITE, tucne = true).apply { gravity = Gravity.CENTER }, odsazeni(top = 4))
            kroky.addView(k, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        sloupec.addView(kroky, odsazeni(top = 20))

        // jak spárovat telefon – obě cesty (přihlášená televize / přihlášení v telefonu)
        val par = svisle().apply {
            background = zaobleni(Color.parseColor("#161722"), 22f)
            setPadding(dp(24), dp(14), dp(24), dp(14))
        }
        par.addView(text(t("ngParNadpis"), 18f, Color.parseColor("#FFC24B"), tucne = true))
        par.addView(text(t("ngPar1"), 17f, Color.WHITE), odsazeni(top = 4))
        par.addView(text(t("ngPar2"), 17f, Color.WHITE), odsazeni(top = 4))
        sloupec.addView(par, odsazeni(top = 16))

        val stav = text("", 24f, Color.WHITE, tucne = true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(40), dp(14), dp(40), dp(14))
        }
        sloupec.addView(stav, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(16); gravity = Gravity.CENTER_HORIZONTAL
        })

        val zpet = tlacitko(t("prijemZpetKratce"), 20f) { zpetNaVyber() }.apply {
            gravity = Gravity.CENTER
            background = vyber(Color.parseColor("#3A3C50"), radius = 999f)
            setPadding(dp(34), dp(14), dp(34), dp(14))
        }
        sloupec.addView(zpet, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(20); gravity = Gravity.CENTER_HORIZONTAL
        })
        sloupec.addView(text(t("prijemSam"), 16f, TEXT2).apply { gravity = Gravity.CENTER }, odsazeni(top = 16))

        nastavObsah(sloupec, sirka = ViewGroup.LayoutParams.MATCH_PARENT)
        zpet.requestFocus()

        var bylPripojen = PrijemService.telefonPripojen()
        val obnovPrijem = object : Runnable {
            override fun run() {
                if (obrazovka != "prijem") return
                val ok = PrijemService.telefonPripojen()
                stav.text = t(if (ok) "prijemPripojen" else "prijemCekam")
                stav.background = zaobleni(if (ok) ZELENA else Color.parseColor("#B07A10"), 999f)
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
        if (!nejakaKonzole) { ukazPripojeni(); return }
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
                        .setPositiveButton(t("pridatSparovatTel")) { _, _ -> ukazPripojeni() }
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
            .setPositiveButton(t("upozorneniOk")) { _, _ -> if (xbox) ukazPripojeniXbox() else ukazPripojeni() }
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
        val sloupec = svisle()
        sloupec.addView(nadpisLogo())
        sloupec.addView(text(t("xboxNadpis"), 28f, Color.WHITE, tucne = true), odsazeni(bottom = 16))
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

        // 0) než začneš – nastavení na Xboxu
        val k0 = karta()
        k0.addView(text(t("predemNadpis"), 20f, Color.parseColor("#FFC24B"), tucne = true))
        k0.addView(text(t("xboxPredem"), 17f, Color.WHITE), odsazeni(top = 6))
        sloupec.addView(k0, odsazeni(bottom = 12))

        // 1) Microsoft účet
        val prihlasen = XboxUcet.prihlasen(this)
        val k1 = karta()
        k1.addView(text(t("xboxKrok1"), 18f, Color.WHITE))
        val jmeno = XboxUcet.jmeno(this)
        k1.addView(text(if (prihlasen) t("xboxUcetOk") + (if (jmeno.isNotEmpty()) " ($jmeno)" else "") else "", 17f, Color.parseColor("#7DFF9A")), odsazeni(top = 6))
        k1.addView(tlacitkoCervene(if (prihlasen) t("zmenitUcet") else t("prihlasit")) {
            XboxUcet.odhlas(this); ukazXboxKod()
        }, obal(top = 8))
        sloupec.addView(k1, odsazeni(bottom = 12))

        // 2) hledání Xboxu (na účtu)
        val k2 = karta()
        k2.addView(text(t("xboxKrok2"), 18f, Color.WHITE))
        val hledaniStav = text("", 17f, TEXT2)
        k2.addView(hledaniStav, odsazeni(top = 6))
        fun hledej() {
            xboxNalezena = null
            if (!XboxUcet.prihlasen(this)) { hledaniStav.text = t("nejdrivUcet"); return }
            hledaniStav.text = t("xboxHledam"); hledaniStav.setTextColor(TEXT2)
            Thread {
                val seznam = runCatching { XboxSpojeni.konzole(this) }
                runOnUiThread {
                    if (obrazovka != "xbox") return@runOnUiThread
                    val x = seznam.getOrNull()?.firstOrNull()
                    xboxNalezena = x
                    hledaniStav.text = when {
                        x != null -> "✔ ${t("xboxNalezen")}: ${x.jmeno}"
                        seznam.isFailure -> "✘ " + (seznam.exceptionOrNull()?.message ?: "")
                        else -> "✘ ${t("xboxNenalezen")}"
                    }
                    hledaniStav.setTextColor(if (x != null) Color.parseColor("#7DFF9A") else Color.parseColor("#FF8A8A"))
                }
            }.start()
        }
        k2.addView(tlacitko(t("hledatZnovu"), 18f) { hledej() }.apply { setPadding(dp(20), dp(10), dp(20), dp(10)) }, obal(top = 8))
        sloupec.addView(k2, odsazeni(bottom = 12))

        // 3) připojení – zkusí spojení a počká na obraz
        val k3 = karta()
        k3.addView(text(t("xboxKrok3"), 18f, Color.WHITE))
        val pripojStav = text("", 17f, TEXT2)
        k3.addView(tlacitkoZelene(t("xboxPripojitBtn")) {
            val x = xboxNalezena
            when {
                !XboxUcet.prihlasen(this) -> pripojStav.text = t("nejdrivUcet")
                x == null -> pripojStav.text = t("xboxNenalezen")
                xboxRelace != null -> {}
                else -> {
                    pripojStav.setTextColor(TEXT2)
                    var hotovo = false
                    val r = XboxSpojeni.Relace(this, x.id, priStavu = { st ->
                        runOnUiThread {
                            if (obrazovka != "xbox" || hotovo) return@runOnUiThread
                            pripojStav.text = st
                            if (st.startsWith("✔")) {
                                hotovo = true
                                pripojStav.setTextColor(Color.parseColor("#7DFF9A"))
                                XboxUcet.ulozKonzoli(this, x.id, x.jmeno)
                                xboxRelace?.zastav(); xboxRelace = null
                                ui.postDelayed({ if (obrazovka == "xbox") ukazHlavni() }, 1500)
                            } else if (st.startsWith("✘")) {
                                pripojStav.setTextColor(Color.parseColor("#FF8A8A"))
                                xboxRelace = null
                            }
                        }
                    })
                    xboxRelace = r
                    r.spust()
                }
            }
        }, obal(top = 10))
        k3.addView(pripojStav, odsazeni(top = 6))
        sloupec.addView(k3, odsazeni(bottom = 12))

        sloupec.addView(tlacitko(t("zpet"), 18f) { xboxRelace?.zastav(); xboxRelace = null; ukazHlavni() }
            .apply { setPadding(dp(20), dp(10), dp(20), dp(10)) }, obal())
        nastavObsah(sloupec, sirka = sirkaObsahu)
        hledej()
    }

    /** Přihlášení Microsoft účtem: kód na microsoft.com/link (QR na televizi, odkaz v telefonu). */
    private fun ukazXboxKod() {
        obrazovka = "xboxkod"
        val sloupec = svisle()
        sloupec.addView(nadpisLogo())
        sloupec.addView(text(t("xboxKodNadpis"), 28f, Color.WHITE, tucne = true), odsazeni(bottom = 12))
        val k = karta()
        val stav = text(t("xboxKodNacitam"), 17f, TEXT2)
        k.addView(stav)
        sloupec.addView(k, odsazeni(bottom = 12))
        sloupec.addView(tlacitko(t("zpet"), 17f) { ukazPripojeniXbox() }
            .apply { setPadding(dp(20), dp(10), dp(20), dp(10)) }, obal(top = 10))
        nastavObsah(sloupec, sirka = sirkaObsahu)

        Thread {
            val kod = runCatching { XboxUcet.novyKod() }
            runOnUiThread {
                if (obrazovka != "xboxkod") return@runOnUiThread
                val kd = kod.getOrNull()
                if (kd == null) { stav.text = "✘ " + (kod.exceptionOrNull()?.message ?: ""); return@runOnUiThread }
                k.removeAllViews()
                val velkyKod = text(kd.uzivatelskyKod, 44f, Color.parseColor("#7DFF9A"), tucne = true).apply {
                    letterSpacing = 0.15f
                }
                if (telefon) {
                    k.addView(text(t("xboxKodTelefon"), 18f, Color.WHITE))
                    k.addView(velkyKod, odsazeni(top = 12))
                    k.addView(tlacitkoCervene(t("xboxOtevritOdkaz")) {
                        runCatching {
                            val cb = getSystemService(android.content.ClipboardManager::class.java)
                            cb.setPrimaryClip(android.content.ClipData.newPlainText("kod", kd.uzivatelskyKod))
                        }
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(kd.adresa))) }
                    }, obal(top = 12))
                } else {
                    val radek = vodorovne().apply { gravity = Gravity.CENTER_VERTICAL }
                    radek.addView(ImageView(this).apply {
                        runCatching { setImageBitmap(PrihlaseniTelefonem.qr(kd.adresa, 512)) }
                    }, LinearLayout.LayoutParams(dp(330), dp(330)).apply { rightMargin = dp(30) })
                    val popis = svisle()
                    popis.addView(text(t("xboxKodTv"), 19f, Color.WHITE))
                    popis.addView(text(kd.adresa.removePrefix("https://").removePrefix("www."), 22f, Color.WHITE, tucne = true), odsazeni(top = 12))
                    popis.addView(velkyKod, odsazeni(top = 10))
                    radek.addView(popis, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    k.addView(radek)
                }
                // přihlášení „klíčem“ (passkey) v prohlížeči telefonu často nejde → poradit jinou cestu
                k.addView(text(t("xboxKodTip"), 16f, Color.parseColor("#FFC24B")).apply { setLineSpacing(0f, 1.2f) }, odsazeni(top = 12))
                k.addView(text(t("qrCekam"), 17f, TEXT2), odsazeni(top = 10))
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
