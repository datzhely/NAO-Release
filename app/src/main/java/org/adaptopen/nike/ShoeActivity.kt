package org.adaptopen.nike

import android.animation.ObjectAnimator
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/** Schermata di comando di UNA scarpa (sinistra o destra): i comandi vanno solo a quella scarpa. */
class ShoeActivity : Activity() {

    private lateinit var side: Side
    private lateinit var root: LinearLayout
    private lateinit var controls: LinearLayout
    private lateinit var statusDot: View
    private lateinit var statusText: TextView
    private lateinit var posView: TextView
    private lateinit var batteryBar: TextView
    private lateinit var batteryDetail: TextView
    private lateinit var advanced: LinearLayout
    private lateinit var logView: TextView
    private val presetButtons = ArrayList<Button>()
    private var pulse: ObjectAnimator? = null
    private var pulsing = false
    private var shownPos = 0
    private val handler = Handler(Looper.getMainLooper())

    private val stateCb: () -> Unit = { runOnUiThread { refreshState() } }
    private val logCb: (String) -> Unit = { runOnUiThread { logView.text = ShoeManager.logText() } }
    private val posCb: (Int) -> Unit = { p -> runOnUiThread { showPosition(p) } }
    private val batCb: (BatteryInfo) -> Unit = { b -> runOnUiThread { showBattery(b) } }

    /** Rilegge la batteria ogni minuto finche' la schermata e' aperta. */
    private val batteryTick = object : Runnable {
        override fun run() {
            link()?.let { if (it.state == ShoeLink.State.AUTHENTICATED) it.readBattery { } }
            handler.postDelayed(this, 60_000)
        }
    }

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun link(): ShoeLink? = ShoeManager.link(side)
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    /** Esegue l'azione solo se la scarpa e' collegata e autenticata. */
    private fun ready(block: (ShoeLink) -> Unit) {
        val l = link()
        if (l == null || l.state != ShoeLink.State.AUTHENTICATED) {
            toast(S.t("not_ready_toast", side.short))
            ShoeManager.log(S.t("not_ready_log", side.short))
            return
        }
        block(l)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        side = try { Side.valueOf(intent.getStringExtra("side") ?: "LEFT") } catch (e: Exception) { Side.LEFT }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(28))
        }

        // intestazione: indietro + titolo + stato
        val head = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        head.addView(Ui.btn(this, S.t("back")) { finish() }, LinearLayout.LayoutParams(dp(130), ViewGroup.LayoutParams.WRAP_CONTENT))
        head.addView(TextView(this).apply {
            text = side.title.uppercase(); textSize = 20f; typeface = Ui.MONO_BOLD
            setTextColor(Ui.GREEN); setPadding(0, dp(10), 0, dp(4)); Ui.glow(this)
        })
        val statusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        statusDot = Ui.dot(this, Ui.MUTED)
        statusRow.addView(statusDot, LinearLayout.LayoutParams(dp(12), dp(12)).apply { rightMargin = dp(10) })
        statusText = Ui.text(this, "", 13f, Ui.MUTED)
        statusRow.addView(statusText)
        head.addView(statusRow)
        root.addView(head)

        // blocco dei comandi (si attenua quando la scarpa non e' pronta)
        controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        controls.addView(batteryCard())
        Ui.add(controls, lacesCard(), 12)
        Ui.add(controls, presetsCard(), 12)
        Ui.add(controls, lightsCard(), 12)
        Ui.add(controls, otherCard(), 12)
        Ui.add(root, controls, 12)

        Ui.add(root, advancedToggle(), 12)
        advanced = advancedCard()
        advanced.visibility = View.GONE
        Ui.add(root, advanced, 8)

        setContentView(Ui.screen(this, root))
        Ui.enter(controls)
    }

    // ------------------------------------------------------------------ blocchi

    private fun batteryCard(): LinearLayout {
        val card = Ui.card(this)
        card.addView(Ui.title(this, S.t("battery_title")))
        batteryBar = TextView(this).apply {
            text = Ui.bar(0) + " --%"
            typeface = Ui.MONO_BOLD; textSize = 15f; setTextColor(Ui.MUTED)
        }
        card.addView(batteryBar)
        batteryDetail = Ui.text(this, S.t("battery_none"), 12f, Ui.MUTED)
        card.addView(batteryDetail)
        card.addView(Ui.btn(this, S.t("battery_refresh")) { ready { it.readBattery { } } })
        return card
    }

    private fun lacesCard(): LinearLayout {
        val card = Ui.card(this)
        card.addView(Ui.title(this, S.t("laces_title")))
        val posRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        posRow.addView(Ui.text(this, S.t("position").uppercase(), 13f, Ui.MUTED), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        posView = TextView(this).apply {
            text = "-"; textSize = 38f; typeface = Ui.MONO_BOLD; setTextColor(Ui.GREEN); Ui.glow(this, Ui.GREEN, 8f)
        }
        posRow.addView(posView)
        card.addView(posRow)
        card.addView(Ui.row(this,
            Ui.holdBtn(this, S.t("loosen"), Ui.GREEN, { ready { it.startMove(ShoeLink.DIR_LOOSEN) } }, { link()?.stopMove() }),
            Ui.holdBtn(this, S.t("tighten"), Ui.AMBER, { ready { it.startMove(ShoeLink.DIR_TIGHTEN) } }, { link()?.stopMove() })
        ))
        card.addView(Ui.text(this, S.t("hold_hint"), 12f, Ui.MUTED))
        return card
    }

    private fun weightedRow(vararg items: Pair<View, Float>): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        items.forEach { (v, w) ->
            addView(v, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w).apply {
                setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
    }

    private fun presetsCard(): LinearLayout {
        val card = Ui.card(this)
        card.addView(Ui.title(this, S.t("presets_title")))
        presetButtons.clear()
        for (slot in 0..2) {
            val applyBtn = Ui.btn(this, "", false) { applyPreset(slot) }
            applyBtn.setOnLongClickListener { renamePreset(slot); true }
            presetButtons.add(applyBtn)
            val save = Ui.btn(this, S.t("save_here")) { savePreset(slot) }
            val clear = Ui.btn(this, "X") { clearPreset(slot) }
            card.addView(weightedRow(applyBtn to 3f, save to 2f, clear to 1f))
        }
        card.addView(Ui.text(this, S.t("presets_hint"), 12f, Ui.MUTED))
        refreshPresets()
        return card
    }

    private fun refreshPresets() {
        for (slot in 0..2) {
            val p = ShoeManager.presets.get(side, slot)
            val second = if (p.position != null) S.t("preset_pos", p.position) else S.t("preset_empty")
            presetButtons[slot].text = Ui.term(p.name + "\n" + second)
        }
    }

    private fun applyPreset(slot: Int) {
        val p = ShoeManager.presets.get(side, slot)
        val pos = p.position
        if (pos == null) { toast(S.t("preset_empty_toast")); return }
        ready { l ->
            l.setPosition(pos)
            Ui.bump(presetButtons[slot])
            toast(S.t("preset_applied", p.name, pos))
        }
    }

    private fun savePreset(slot: Int) {
        ready { l ->
            l.readPosition { pos ->
                runOnUiThread {
                    if (pos == null) {
                        toast(S.t("pos_read_fail"))
                    } else {
                        val old = ShoeManager.presets.get(side, slot)
                        ShoeManager.presets.put(side, slot, Preset(old.name, pos))
                        refreshPresets()
                        Ui.bump(presetButtons[slot])
                        toast(S.t("preset_saved", old.name, pos))
                    }
                }
            }
        }
    }

    private fun clearPreset(slot: Int) {
        ShoeManager.presets.clearPosition(side, slot)
        refreshPresets()
        Ui.bump(presetButtons[slot])
    }

    private fun renamePreset(slot: Int) {
        val old = ShoeManager.presets.get(side, slot)
        Ui.prompt(this, S.t("rename_title"), old.name) { text ->
            val n = text.trim().ifEmpty { S.t("preset_default", slot + 1) }
            ShoeManager.presets.put(side, slot, Preset(n.take(20), old.position))
            refreshPresets()
        }
    }

    private fun lightsCard(): LinearLayout {
        val card = Ui.card(this)
        card.addView(Ui.title(this, S.t("lights_title")))
        card.addView(Ui.row(this,
            Ui.btn(this, S.t("red")) { ready { it.setLed(255, 0, 0) } },
            Ui.btn(this, S.t("green")) { ready { it.setLed(0, 255, 0) } },
            Ui.btn(this, S.t("blue")) { ready { it.setLed(0, 0, 255) } }
        ))
        card.addView(Ui.row(this,
            Ui.btn(this, S.t("white")) { ready { it.setLed(255, 255, 255) } },
            Ui.btn(this, S.t("off")) { ready { it.setLed(0, 0, 0) } }
        ))
        return card
    }

    private fun otherCard(): LinearLayout {
        val card = Ui.card(this)
        card.addView(Ui.title(this, S.t("other_title")))
        card.addView(Ui.row(this,
            Ui.btn(this, S.t("identify")) { ready { it.identify() } },
            Ui.btn(this, S.t("battery")) { ready { it.battery() } }
        ))
        card.addView(Ui.row(this,
            Ui.btn(this, S.t("foot_on")) { ready { it.footSensor(true) } },
            Ui.btn(this, S.t("foot_off")) { ready { it.footSensor(false) } }
        ))
        return card
    }

    private fun advancedToggle(): Button = Ui.btn(this, S.t("adv_toggle")) {
        if (advanced.visibility == View.VISIBLE) {
            advanced.animate().alpha(0f).setDuration(160).withEndAction { advanced.visibility = View.GONE }.start()
        } else {
            advanced.alpha = 0f
            advanced.visibility = View.VISIBLE
            advanced.animate().alpha(1f).setDuration(220).start()
        }
    }

    private fun advancedCard(): LinearLayout {
        val card = Ui.card(this)
        card.addView(Ui.title(this, S.t("adv_title")))
        card.addView(Ui.row(this,
            Ui.btn(this, S.t("disconnect")) { ShoeManager.disconnect(side) },
            Ui.btn(this, S.t("forget_pairing")) { link()?.forgetKey() ?: toast(S.t("no_shoe")) }
        ))
        card.addView(Ui.row(this,
            Ui.btn(this, S.t("serial")) { ready { it.serial() } },
            Ui.btn(this, S.t("read_led")) { ready { it.getLed() } },
            Ui.btn(this, S.t("read_pos_preset")) { ready { it.getPosition(); it.getPreset() } }
        ))
        val r = Ui.number(this, "R 0-255", "255")
        val g = Ui.number(this, "G 0-255", "0")
        val b = Ui.number(this, "B 0-255", "128")
        card.addView(Ui.row(this, r, g, b))
        card.addView(Ui.btn(this, S.t("custom_color")) {
            ready { l ->
                fun v(e: android.widget.EditText) = (e.text.toString().toIntOrNull() ?: 0).coerceIn(0, 255)
                l.setLed(v(r), v(g), v(b))
            }
        })
        val posInput = Ui.number(this, S.t("pos_hint"), "0")
        card.addView(Ui.row(this, posInput, Ui.btn(this, S.t("go_to_pos")) {
            ready { l -> l.setPosition(posInput.text.toString().toIntOrNull() ?: 0) }
        }))

        card.addView(Ui.title(this, S.t("console_title")).apply { setPadding(0, dp(12), 0, dp(8)) })
        val cmdInput = Ui.field(this, S.t("console_cmd_hint"))
        val payloadInput = Ui.field(this, S.t("console_payload_hint"))
        card.addView(cmdInput)
        card.addView(payloadInput)
        card.addView(Ui.btn(this, S.t("send")) {
            val txt = cmdInput.text.toString().trim()
            val code = Op.table[txt.uppercase()] ?: txt.removePrefix("0x").toIntOrNull(16)
            if (txt.isEmpty() || code == null || code !in 0..255) {
                toast(S.t("cmd_unknown"))
            } else {
                val payload = try { unhex(payloadInput.text.toString()) } catch (e: Exception) { toast(S.t("bad_hex")); null }
                if (payload != null) ready { it.raw(code, payload) }
            }
        })

        card.addView(Ui.title(this, S.t("log_title")).apply { setPadding(0, dp(12), 0, dp(8)) })
        logView = TextView(this).apply {
            typeface = Ui.MONO; textSize = 11f; setTextIsSelectable(true); setTextColor(Ui.MUTED)
            text = ShoeManager.logText().ifEmpty { S.t("ready_log") }
        }
        card.addView(logView)
        card.addView(Ui.btn(this, S.t("copy_log")) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("log", ShoeManager.logText()))
            toast(S.t("toast_log_copied"))
        })
        return card
    }

    // ------------------------------------------------------------------ stato

    private fun showPosition(p: Int) {
        val empty = posView.text.toString() == "-"
        if (p == shownPos && !empty) return
        val from = if (empty) p else shownPos
        Ui.countTo(posView, from, p)
        shownPos = p
    }

    private fun showBattery(b: BatteryInfo) {
        val pct = b.percent.coerceIn(0, 100)
        val color = when {
            pct <= 15 -> Ui.RED
            pct <= 30 -> Ui.AMBER
            else -> Ui.GREEN
        }
        batteryBar.text = Ui.bar(pct) + " " + pct + "%"
        batteryBar.setTextColor(color)
        Ui.glow(batteryBar, color, 5f)
        batteryDetail.text = S.t("battery_detail", b.millivolts, b.tempC)
    }

    private fun refreshState() {
        val l = link()
        val (text, color, isPulsing) = when (l?.state) {
            null -> Triple(S.t("st_none_hint"), Ui.MUTED, false)
            ShoeLink.State.DISCONNECTED -> Triple(S.t("st_disconnected"), Ui.MUTED, false)
            ShoeLink.State.CONNECTING -> Triple(S.t("st_connecting"), Ui.AMBER, true)
            ShoeLink.State.READY -> Triple(S.t("st_preparing"), Ui.AMBER, true)
            ShoeLink.State.AUTHENTICATING -> Triple(S.t("st_pairing"), Ui.AMBER, true)
            ShoeLink.State.AUTHENTICATED -> Triple(S.t("st_ready"), Ui.GREEN, false)
            ShoeLink.State.FAILED -> Triple(S.t("st_error"), Ui.RED, false)
        }
        statusText.text = "> $text"
        statusText.setTextColor(color)
        Ui.setDotColor(statusDot, color)
        if (isPulsing != pulsing) {
            pulse?.cancel()
            statusDot.alpha = 1f
            pulse = if (isPulsing) Ui.pulse(statusDot) else null
            pulsing = isPulsing
        }
        val ok = l?.state == ShoeLink.State.AUTHENTICATED
        controls.animate().alpha(if (ok) 1f else 0.45f).setDuration(220).start()
        l?.onPosition = posCb
        l?.onBattery = batCb
        if (ok) {
            val lp = l?.lastPosition
            if (lp != null) showPosition(lp) else l?.readPosition { }
            val lb = l?.lastBattery
            if (lb != null) showBattery(lb) else l?.readBattery { }
        }
    }

    override fun onResume() {
        super.onResume()
        ShoeManager.addStateListener(stateCb)
        ShoeManager.addLogListener(logCb)
        logView.text = ShoeManager.logText().ifEmpty { S.t("ready_log") }
        refreshState()
        refreshPresets()
        handler.removeCallbacks(batteryTick)
        handler.postDelayed(batteryTick, 60_000)
    }

    override fun onPause() {
        handler.removeCallbacks(batteryTick)
        link()?.stopMove()
        link()?.onPosition = null
        link()?.onBattery = null
        ShoeManager.removeStateListener(stateCb)
        ShoeManager.removeLogListener(logCb)
        super.onPause()
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.pop_enter, R.anim.pop_exit)
    }
}
