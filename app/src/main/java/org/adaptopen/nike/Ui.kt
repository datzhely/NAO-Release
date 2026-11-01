package org.adaptopen.nike

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Righe orizzontali sottili sopra lo schermo: l'effetto "monitor a tubo catodico" dei terminali. */
class ScanlineView(c: Context) : View(c) {
    private val paint = Paint().apply { color = Color.BLACK; alpha = 70 }

    init {
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        paint.strokeWidth = 1.2f * d
        val step = 3f * d
        var y = 0f
        while (y < height) {
            canvas.drawLine(0f, y, width.toFloat(), y, paint)
            y += step
        }
    }
}

/** Stile "terminale" (verde fosforescente su nero, bordi ASCII) e animazioni semplici, tutto a codice. */
object Ui {
    val BG: Int = Color.parseColor("#040904")
    val PANEL: Int = Color.parseColor("#061406")
    val GREEN: Int = Color.parseColor("#3CFF7C")
    val MUTED: Int = Color.parseColor("#27B058")
    val DARK: Int = Color.parseColor("#0E3B1D")
    val AMBER: Int = Color.parseColor("#FFB000")
    val RED: Int = Color.parseColor("#FF5040")
    val MONO: Typeface = Typeface.MONOSPACE
    val MONO_BOLD: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)

    val BANNER: String = listOf(
        "    _     ____      _     ____   _____ ",
        "   / \\   |  _ \\    / \\   |  _ \\ |_   _|",
        "  / _ \\  | | | |  / _ \\  | |_) |  | |  ",
        " / ___ \\ | |_| | / ___ \\ |  __/   | |  ",
        "/_/   \\_\\|____/ /_/   \\_\\|_|      |_|  "
    ).joinToString("\n")

    fun dp(c: Context, v: Int) = (v * c.resources.displayMetrics.density).toInt()

    /** Barra batteria ASCII, per esempio [##########----------] */
    fun bar(pct: Int, width: Int = 20): String {
        val p = pct.coerceIn(0, 100)
        val filled = (p * width + 50) / 100
        return "[" + "#".repeat(filled) + "-".repeat(width - filled) + "]"
    }

    fun box(c: Context, stroke: Int, fill: Int, strokeDp: Float = 1.5f): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(fill)
        setStroke((strokeDp * c.resources.displayMetrics.density).toInt().coerceAtLeast(1), stroke)
    }

    fun glow(tv: TextView, color: Int = GREEN, radiusDp: Float = 4f) {
        tv.setShadowLayer(radiusDp * tv.resources.displayMetrics.density, 0f, 0f, color)
    }

    /** Testo di un pulsante in stile terminale: [ TESTO ] */
    fun term(text: String) = "[ " + text.uppercase() + " ]"

    private fun invertOnPress(b: Button, accent: Int, restFill: Int, onDown: (() -> Unit)? = null, onUp: (() -> Unit)? = null, consume: Boolean = false) {
        b.setOnTouchListener { v, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    b.background = box(b.context, accent, accent)
                    b.setTextColor(BG)
                    b.setShadowLayer(0f, 0f, 0f, 0)
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(70).start()
                    onDown?.invoke()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    b.background = box(b.context, accent, restFill)
                    b.setTextColor(accent)
                    glow(b, accent)
                    v.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    onUp?.invoke()
                    if (consume && e.action == MotionEvent.ACTION_UP) v.performClick()
                }
            }
            consume
        }
    }

    fun btn(c: Context, text: String, primary: Boolean = false, onClick: () -> Unit): Button = Button(c).apply {
        this.text = term(text)
        isAllCaps = false
        typeface = MONO
        textSize = 13f
        setTextColor(GREEN)
        val rest = if (primary) DARK else Color.TRANSPARENT
        background = box(c, GREEN, rest)
        stateListAnimator = null
        minHeight = dp(c, 46)
        minimumHeight = dp(c, 46)
        glow(this)
        setOnClickListener { onClick() }
        invertOnPress(this, GREEN, rest)
    }

    /** Pulsante "tieni premuto": onDown quando lo tocchi, onUp quando lo rilasci. */
    fun holdBtn(c: Context, text: String, accent: Int, onDown: () -> Unit, onUp: () -> Unit): Button = Button(c).apply {
        this.text = term(text)
        isAllCaps = false
        typeface = MONO_BOLD
        textSize = 18f
        setTextColor(accent)
        background = box(c, accent, DARK, 2f)
        stateListAnimator = null
        minHeight = dp(c, 96)
        minimumHeight = dp(c, 96)
        glow(this, accent)
        invertOnPress(this, accent, DARK, onDown, onUp, consume = true)
    }

    fun row(c: Context, vararg views: View): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach {
            addView(it, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(dp(c, 3), dp(c, 3), dp(c, 3), dp(c, 3))
            })
        }
    }

    fun card(c: Context): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.VERTICAL
        background = box(c, GREEN, PANEL, 1.5f)
        setPadding(dp(c, 12), dp(c, 10), dp(c, 12), dp(c, 12))
    }

    /** Intestazione di sezione: +-[ TITOLO ]---------- */
    fun title(c: Context, t: String): LinearLayout = LinearLayout(c).apply {
        orientation = LinearLayout.HORIZONTAL
        val left = TextView(c).apply {
            text = "+-[ ${t.uppercase()} ]"
            typeface = MONO_BOLD; textSize = 13f; setTextColor(GREEN); maxLines = 1
            glow(this)
        }
        val right = TextView(c).apply {
            text = "-".repeat(120)
            typeface = MONO; textSize = 13f; setTextColor(MUTED); maxLines = 1
        }
        addView(left, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        setPadding(0, 0, 0, dp(c, 8))
    }

    fun text(c: Context, t: String, size: Float = 14f, color: Int = GREEN): TextView = TextView(c).apply {
        text = t
        textSize = size
        typeface = MONO
        setTextColor(color)
        if (color == GREEN) glow(this, GREEN, 3f)
    }

    fun number(c: Context, hint: String, initial: String = ""): EditText = EditText(c).apply {
        this.hint = hint
        typeface = MONO
        setHintTextColor(MUTED)
        setTextColor(GREEN)
        inputType = InputType.TYPE_CLASS_NUMBER
        background = box(c, MUTED, Color.TRANSPARENT, 1f)
        setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8))
        if (initial.isNotEmpty()) setText(initial)
    }

    fun field(c: Context, hint: String): EditText = EditText(c).apply {
        this.hint = hint
        typeface = MONO
        textSize = 13f
        setHintTextColor(MUTED)
        setTextColor(GREEN)
        background = box(c, MUTED, Color.TRANSPARENT, 1f)
        setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8))
    }

    fun add(parent: LinearLayout, child: View, topDp: Int = 10) {
        parent.addView(child, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(parent.context, topDp) })
    }

    /** Schermata completa: sfondo nero, contenuto scorrevole e righe CRT sopra. */
    fun screen(a: Activity, content: View): FrameLayout {
        val frame = FrameLayout(a).apply { setBackgroundColor(BG) }
        val sv = ScrollView(a).apply { isFillViewport = true; addView(content) }
        frame.addView(sv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        frame.addView(ScanlineView(a), FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        return frame
    }

    /** Finestra di dialogo in stile terminale con un campo di testo. */
    fun prompt(a: Activity, title: String, initial: String, onOk: (String) -> Unit) {
        val d = Dialog(a)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val frame = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = box(a, GREEN, BG, 2f)
            setPadding(dp(a, 16), dp(a, 14), dp(a, 16), dp(a, 14))
        }
        frame.addView(title(a, title))
        val input = field(a, "")
        input.setText(initial)
        input.setSelection(initial.length)
        frame.addView(input)
        frame.addView(row(a,
            btn(a, S.t("cancel")) { d.dismiss() },
            btn(a, S.t("ok"), true) { onOk(input.text.toString()); d.dismiss() }
        ))
        d.setContentView(frame)
        d.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        d.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        d.show()
    }

    // ------------------------------------------------------------ animazioni

    /** Entrata a cascata dei blocchi di una schermata. */
    fun enter(parent: ViewGroup) {
        val d = dp(parent.context, 14).toFloat()
        for (i in 0 until parent.childCount) {
            val v = parent.getChildAt(i)
            v.alpha = 0f
            v.translationY = d
            v.animate().alpha(1f).translationY(0f).setStartDelay(i * 55L).setDuration(260)
                .setInterpolator(DecelerateInterpolator()).start()
        }
    }

    /** Scrive il testo lettera per lettera, come un terminale. */
    fun typewriter(tv: TextView, full: String, msPerChar: Long = 16) {
        tv.text = ""
        ValueAnimator.ofInt(0, full.length).apply {
            duration = (full.length * msPerChar).coerceAtLeast(1)
            interpolator = LinearInterpolator()
            addUpdateListener { tv.text = full.substring(0, it.animatedValue as Int) }
            start()
        }
    }

    /** Piccolo "rimbalzo" per confermare un'azione. */
    fun bump(v: View) {
        v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(110).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
        }.start()
    }

    /** Pulsazione continua (per "connessione in corso" e per il cursore). Restituisce l'animatore da fermare. */
    fun pulse(v: View, minAlpha: Float = 0.2f, ms: Long = 600): ObjectAnimator =
        ObjectAnimator.ofFloat(v, "alpha", 1f, minAlpha).apply {
            duration = ms
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            start()
        }

    /** Cambia un numero con un conteggio animato. */
    fun countTo(tv: TextView, from: Int, to: Int) {
        ValueAnimator.ofInt(from, to).apply {
            duration = 260
            addUpdateListener { tv.text = (it.animatedValue as Int).toString() }
            start()
        }
    }

    /** Indicatore di stato quadrato (stile terminale). */
    fun dot(c: Context, color: Int): View = View(c).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(color) }
    }

    fun setDotColor(dot: View, color: Int) {
        (dot.background as? GradientDrawable)?.setColor(color)
    }

    /** Banner "ADAPT" in ASCII, con effetto luminoso. */
    fun banner(c: Context): TextView = TextView(c).apply {
        text = BANNER
        typeface = MONO_BOLD
        textSize = 8f
        setTextColor(GREEN)
        setHorizontallyScrolling(true)
        includeFontPadding = false
        glow(this, GREEN, 6f)
    }
}
