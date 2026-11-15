package cz.titulky.tv

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Podpisová značka „Fuki“ (stejná jako v ostatních aplikacích autora):
 *  1) podpis v patičce „created by Fuki. | e-mail“ – [podpis]
 *  2) úvodní animace „Fuki“ při spuštění – [FukiIntro.ukaz]
 * Neodstraňovat ani nepřepisovat.
 */
object FukiZnacka {
    /** Soukromý projekt → soukromý e-mail. */
    const val EMAIL = "jarek.fuki@gmail.com"

    private val TYRKYS = Color.parseColor("#0D9488")   // Tailwind teal-600
    private val SEDA = Color.parseColor("#6B7280")     // Tailwind gray-500
    private val MODRA = Color.parseColor("#2563EB")

    /**
     * Podpis do patičky: malý, šedý, vycentrovaný; tečka za „Fuki“ tyrkysová a tučná;
     * e-mail je odkaz (mailto) – při stisku/zaměření zmodrá a podtrhne se.
     */
    fun podpis(context: Context): TextView = TextView(context).apply {
        val zaklad = "created by Fuki"
        val sb = SpannableStringBuilder(zaklad)
        val tecka = sb.length
        sb.append(".")
        sb.setSpan(ForegroundColorSpan(TYRKYS), tecka, tecka + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(StyleSpan(Typeface.BOLD), tecka, tecka + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.append(" | ")
        val zacatekMailu = sb.length
        sb.append(EMAIL)
        text = sb
        textSize = 12f // text-xs
        setTextColor(SEDA)
        gravity = Gravity.CENTER
        isFocusable = true
        isClickable = true
        setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, (8 * resources.displayMetrics.density).toInt())
        fun zvyrazni(ano: Boolean) {
            val s = SpannableStringBuilder(sb)
            if (ano) {
                s.setSpan(ForegroundColorSpan(MODRA), zacatekMailu, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                s.setSpan(UnderlineSpan(), zacatekMailu, s.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            text = s
        }
        setOnFocusChangeListener { _, f -> zvyrazni(f) }
        setOnHoverListener { _, e -> zvyrazni(e.action != android.view.MotionEvent.ACTION_HOVER_EXIT); false }
        setOnClickListener {
            runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$EMAIL"))) }
        }
    }
}

/**
 * Úvodní animace „Fuki“ přes celou obrazovku (světlá varianta).
 * Jednou za spuštění aplikace, sama zmizí po ~3,2 s (prolnutí 0,5 s), jde přeskočit
 * klepnutím nebo tlačítkem OK / Zpět / Enter / mezerník. Při vypnutých animacích
 * v systému (Usnadnění → Odstranit animace) se neukáže.
 */
object FukiIntro {
    @Volatile private var videno = false
    private var aktivni: FrameLayout? = null

    fun ukaz(activity: Activity) {
        if (videno) return
        videno = true
        val meritko = runCatching {
            Settings.Global.getFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
        if (meritko == 0f) return // „omezit animace“
        val decor = activity.window?.decorView as? ViewGroup ?: return
        val d = activity.resources.displayMetrics.density
        fun dp(x: Float) = (x * d).toInt()

        val koren = FrameLayout(activity).apply {
            isClickable = true
            isFocusable = true
            elevation = 1000f
            addView(Pozadi(activity), FrameLayout.LayoutParams(-1, -1))
        }
        val sloupec = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val ikona = Ikona(activity)
        sloupec.addView(ikona, LinearLayout.LayoutParams(dp(96f), dp(96f)))
        val createdBy = TextView(activity).apply {
            text = "CREATED BY"; textSize = 11f; letterSpacing = 0.45f
            setTextColor(Color.parseColor("#6B6B8A")); typeface = Typeface.DEFAULT_BOLD
        }
        sloupec.addView(createdBy, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(18f) })
        val jmeno = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        val fuki = LeskleJmeno(activity)
        val tecka = TextView(activity).apply {
            text = "."; textSize = 64f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(Color.parseColor("#059EAB")); includeFontPadding = false
        }
        jmeno.addView(fuki); jmeno.addView(tecka)
        sloupec.addView(jmeno, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(2f) })
        val podtitul = TextView(activity).apply {
            text = "Aplikace pro rodinu i pro práci."; textSize = 16f
            setTextColor(Color.parseColor("#4A4A68"))
        }
        sloupec.addView(podtitul, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6f) })
        val cara = View(activity).apply {
            background = android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.TRANSPARENT, Color.parseColor("#7963DC"), Color.parseColor("#129FB3"), Color.TRANSPARENT)
            )
            scaleX = 0f
        }
        sloupec.addView(cara, LinearLayout.LayoutParams(dp(200f), dp(2f)).apply { topMargin = dp(22f) })
        koren.addView(sloupec, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        val napoveda = TextView(activity).apply {
            text = "Klepnutím nebo tlačítkem OK přeskočíte"; textSize = 11f
            setTextColor(Color.parseColor("#9A9AB0"))
        }
        koren.addView(napoveda, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(28f) })

        // postupné naskakování: ikona, CREATED BY, Fuki, podtitul, čára, nápověda
        val prvky = listOf(ikona, createdBy, jmeno, podtitul, napoveda)
        val zpozdeni = longArrayOf(0, 250, 450, 750, 1250)
        prvky.forEachIndexed { i, v -> naskoc(v, zpozdeni[i], dp(14f).toFloat()) }
        ObjectAnimator.ofFloat(cara, View.SCALE_X, 0f, 1f).apply {
            startDelay = 1000; duration = 700; interpolator = DecelerateInterpolator(); start()
        }
        // lehké vznášení ikony
        ObjectAnimator.ofFloat(ikona, View.TRANSLATION_Y, 0f, -dp(6f).toFloat()).apply {
            startDelay = 600; duration = 1400; repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE; start()
        }

        koren.setOnClickListener { zavri() }
        aktivni = koren
        decor.addView(koren, ViewGroup.LayoutParams(-1, -1))
        koren.requestFocus()
        koren.postDelayed({ zavri() }, 3200)
    }

    /** Volat z dispatchKeyEvent aktivity: OK / Zpět / Enter / Esc / mezerník intro přeskočí. */
    fun zachytKlavesu(e: KeyEvent): Boolean {
        if (aktivni == null) return false
        if (e.keyCode in setOf(KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_NUMPAD_ENTER)) {
            if (e.action == KeyEvent.ACTION_UP) zavri()
            return true
        }
        return true // během intra ostatní klávesy nic nedělají
    }

    private fun zavri() {
        val k = aktivni ?: return
        aktivni = null
        k.animate().alpha(0f).setDuration(500).setListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                (k.parent as? ViewGroup)?.removeView(k)
            }
        }).start()
    }

    private fun naskoc(v: View, zpozdeni: Long, posun: Float) {
        v.alpha = 0f
        v.translationY = posun
        v.animate().alpha(1f).translationY(0f).setStartDelay(zpozdeni).setDuration(600)
            .setInterpolator(DecelerateInterpolator()).start()
        // rozmazání → ostré (jen Android 12+)
        if (Build.VERSION.SDK_INT >= 31) {
            ValueAnimator.ofFloat(14f, 0.01f).apply {
                startDelay = zpozdeni; duration = 600
                addUpdateListener {
                    val r = it.animatedValue as Float
                    v.setRenderEffect(if (r < 0.1f) null else RenderEffect.createBlurEffect(r, r, Shader.TileMode.DECAL))
                }
                v.setRenderEffect(RenderEffect.createBlurEffect(14f, 14f, Shader.TileMode.DECAL))
                start()
            }
        }
    }

    /** Pozadí: jemný přechod #f4f0ff → #f8faff → #e4f9f7 + mřížka 30 dp vytrácející se do kraje. */
    private class Pozadi(c: Context) : View(c) {
        private val p = Paint()
        private val mriz = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 1f }
        private val krok = 30 * c.resources.displayMetrics.density
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat(); val h = height.toFloat()
            p.shader = LinearGradient(0f, 0f, w, h,
                intArrayOf(Color.parseColor("#F4F0FF"), Color.parseColor("#F8FAFF"), Color.parseColor("#E4F9F7")),
                null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, p)
            mriz.shader = RadialGradient(w / 2, h / 2, maxOf(w, h) * 0.6f,
                intArrayOf(Color.argb(40, 83, 69, 182), Color.argb(0, 83, 69, 182)), null, Shader.TileMode.CLAMP)
            var x = 0f
            while (x <= w) { canvas.drawLine(x, 0f, x, h, mriz); x += krok }
            var y = 0f
            while (y <= h) { canvas.drawLine(0f, y, w, y, mriz); y += krok }
        }
    }

    /** Ikona: blesk ve zkoseném zaobleném čtverečku a pomalu se otáčející přerušovaný kroužek. */
    private class Ikona(c: Context) : View(c) {
        private val ctverec = Paint(Paint.ANTI_ALIAS_FLAG)
        private val blesk = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        private val krouzek = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; color = Color.parseColor("#7963DC")
        }
        private var uhel = 0f
        private val otaceni = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 9000; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
            addUpdateListener { uhel = it.animatedValue as Float; invalidate() }
        }
        override fun onAttachedToWindow() { super.onAttachedToWindow(); otaceni.start() }
        override fun onDetachedFromWindow() { otaceni.cancel(); super.onDetachedFromWindow() }
        override fun onDraw(canvas: Canvas) {
            val s = width.toFloat(); val c = s / 2
            krouzek.strokeWidth = s * 0.02f
            krouzek.pathEffect = DashPathEffect(floatArrayOf(s * 0.05f, s * 0.04f), 0f)
            canvas.save(); canvas.rotate(uhel, c, c)
            canvas.drawCircle(c, c, s * 0.47f, krouzek)
            canvas.restore()
            // zkosený zaoblený čtvereček s přechodem fialová → tyrkysová
            val a = s * 0.27f
            ctverec.shader = LinearGradient(c - a, c - a, c + a, c + a,
                intArrayOf(Color.parseColor("#5345B6"), Color.parseColor("#7963DC"), Color.parseColor("#129FB3")),
                null, Shader.TileMode.CLAMP)
            canvas.save(); canvas.rotate(-8f, c, c); canvas.skew(-0.12f, 0f)
            canvas.translate(s * 0.06f, 0f)
            canvas.drawRoundRect(RectF(c - a, c - a, c + a, c + a), a * 0.35f, a * 0.35f, ctverec)
            canvas.restore()
            // blesk
            val b = Path().apply {
                moveTo(c + a * 0.15f, c - a * 0.75f)
                lineTo(c - a * 0.45f, c + a * 0.1f)
                lineTo(c - a * 0.02f, c + a * 0.1f)
                lineTo(c - a * 0.18f, c + a * 0.78f)
                lineTo(c + a * 0.48f, c - a * 0.12f)
                lineTo(c + a * 0.04f, c - a * 0.12f)
                close()
            }
            canvas.drawPath(b, blesk)
        }
    }

    /** Velké „Fuki“ s přechodem fialová → tyrkysová a jemným leskem, který přejede přes písmo. */
    private class LeskleJmeno(c: Context) : TextView(c) {
        private var posun = -1f
        private val lesk = ValueAnimator.ofFloat(-1f, 2f).apply {
            startDelay = 900; duration = 1600; interpolator = LinearInterpolator()
            addUpdateListener { posun = it.animatedValue as Float; invalidate() }
        }
        private val leskPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        init {
            text = "Fuki"; textSize = 64f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            includeFontPadding = false
            letterSpacing = -0.02f
        }
        override fun onAttachedToWindow() { super.onAttachedToWindow(); lesk.start() }
        override fun onDetachedFromWindow() { lesk.cancel(); super.onDetachedFromWindow() }
        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            paint.shader = LinearGradient(0f, 0f, w, 0f,
                intArrayOf(Color.parseColor("#5345B6"), Color.parseColor("#7963DC"), Color.parseColor("#129FB3")),
                floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
            super.onDraw(canvas)
            // lesk: světlý pruh přes písmo (jen přes text – SRC_ATOP)
            if (posun > -1f && posun < 2f) {
                val x = posun * w
                leskPaint.shader = LinearGradient(x - w * 0.25f, 0f, x + w * 0.25f, 0f,
                    intArrayOf(Color.TRANSPARENT, Color.argb(150, 255, 255, 255), Color.TRANSPARENT),
                    null, Shader.TileMode.CLAMP)
                leskPaint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_ATOP)
                val vrstva = canvas.saveLayer(0f, 0f, w, height.toFloat(), null)
                super.onDraw(canvas)
                canvas.drawRect(0f, 0f, w, height.toFloat(), leskPaint)
                canvas.restoreToCount(vrstva)
            }
        }
    }
}
