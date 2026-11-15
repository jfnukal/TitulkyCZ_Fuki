package cz.titulky.tv

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * [Fuki] Vzhled „TitulkyCZ Metro HUD“ (návrh ze Stitche, DESIGN.md):
 * ploché dlaždice s ostrými rohy, mezery 4 dp, česká modrá, červená jen pro „Hrát“,
 * žlutá lišta (barva titulků) pod vybranou / zapnutou dlaždicí, písmo Plus Jakarta Sans.
 */
object Metro {
    val POZADI = Color.parseColor("#0D1321")
    val POZADI_SPODNI = Color.parseColor("#080E1C")
    val MODRA = Color.parseColor("#11457E")
    val MODRA_VYBER = Color.parseColor("#1C5BA6")
    val SEDA_DLAZDICE = Color.parseColor("#242A39")
    val SEDA_VYBER = Color.parseColor("#2F3444")
    val CERVENA = Color.parseColor("#C90011")
    val CERVENA_VYBER = Color.parseColor("#E0141F")
    val ZLUTA = Color.parseColor("#EBC238")
    val TEXT = Color.parseColor("#DDE2F6")
    val TEXT_NA_MODRE = Color.parseColor("#8BB4F4")
    val TEXT_NA_CERVENE = Color.parseColor("#FFD7D2")
    val OBRYS = Color.parseColor("#8D919B")
    val PRIMARNI = Color.parseColor("#A6C8FF")

    private var lehke: Typeface? = null
    private var normalni: Typeface? = null
    private var polotucne: Typeface? = null
    private var tucne: Typeface? = null

    fun pismo(c: Context, vaha: Int): Typeface {
        fun nacti(id: Int) = runCatching { c.resources.getFont(id) }.getOrDefault(Typeface.DEFAULT)
        return when {
            vaha <= 300 -> lehke ?: nacti(R.font.jakarta_light).also { lehke = it }
            vaha <= 400 -> normalni ?: nacti(R.font.jakarta_regular).also { normalni = it }
            vaha <= 600 -> polotucne ?: nacti(R.font.jakarta_semibold).also { polotucne = it }
            else -> tucne ?: nacti(R.font.jakarta_bold).also { tucne = it }
        }
    }

    fun dp(c: Context, x: Float) = (x * c.resources.displayMetrics.density).toInt()

    fun text(c: Context, s: CharSequence, sp: Float, barva: Int, vaha: Int = 400, mezery: Float = 0f) = TextView(c).apply {
        text = s
        textSize = sp
        setTextColor(barva)
        typeface = pismo(c, vaha)
        letterSpacing = mezery
        includeFontPadding = false
    }

    fun ikona(c: Context, id: Int, velikostDp: Float, barva: Int) = ImageView(c).apply {
        setImageResource(id)
        setColorFilter(barva)
        layoutParams = LinearLayout.LayoutParams(dp(c, velikostDp), dp(c, velikostDp))
    }

    /** Pozadí dlaždice: při stisku / výběru ovladačem světlejší. */
    fun pozadi(zaklad: Int, vyber: Int) = StateListDrawable().apply {
        addState(intArrayOf(android.R.attr.state_pressed), ColorDrawable(vyber))
        addState(intArrayOf(android.R.attr.state_focused), android.graphics.drawable.GradientDrawable().apply {
            setColor(vyber); setStroke(3, Color.WHITE)
        })
        addState(intArrayOf(), ColorDrawable(zaklad))
    }

    /**
     * Dlaždice: FrameLayout s obsahem (svislý LinearLayout s odsazením) a žlutou lištou dole.
     * [lista] = lišta vidět vždy (zapnuto / hlavní akce); jinak jen při výběru ovladačem.
     */
    class Dlazdice(c: Context, zaklad: Int, vyber: Int, lista: Boolean) : FrameLayout(c) {
        val obsah = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(c, 12f)
            setPadding(p, p, p, p)
        }
        private val pruh = View(c).apply { setBackgroundColor(ZLUTA) }
        var listaZapnuta = lista
            set(v) { field = v; obnovPruh() }

        init {
            background = pozadi(zaklad, vyber)
            isFocusable = true
            isClickable = true
            addView(obsah, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            addView(pruh, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 4f), Gravity.BOTTOM))
            setOnFocusChangeListener { _, _ -> obnovPruh() }
            obnovPruh()
        }

        private fun obnovPruh() { pruh.visibility = if (listaZapnuta || isFocused) View.VISIBLE else View.INVISIBLE }

        /** Pružná mezera – další obsah se přisune dolů. */
        fun mezera() = obsah.addView(View(context), LinearLayout.LayoutParams(0, 0, 1f))
    }

    /** Řádek dlaždic s mezerami 4 dp. */
    fun radek(c: Context) = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }

    fun lp(sirka: Int, vyska: Int, vlevo: Int = 0) = LinearLayout.LayoutParams(sirka, vyska).apply { leftMargin = vlevo }
}
