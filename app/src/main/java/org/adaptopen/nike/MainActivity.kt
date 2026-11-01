package org.adaptopen.nike

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView

/** Home: collegamento delle scarpe e accesso alle due schermate di comando (sinistra / destra). */
@SuppressLint("MissingPermission", "SetTextI18n")
class MainActivity : Activity() {

    private val found = LinkedHashMap<String, ScanResult>()
    private var scanning = false

    private lateinit var filterBox: CheckBox
    private lateinit var deviceBox: LinearLayout
    private lateinit var logView: TextView
    private val statusViews = HashMap<Side, TextView>()
    private val dots = HashMap<Side, View>()
    private val pulses = HashMap<Side, ObjectAnimator?>()
    private val modes = HashMap<Side, Int>()
    private var cursorAnim: ObjectAnimator? = null

    private val stateCb: () -> Unit = { runOnUiThread { refresh() } }
    private val logCb: (String) -> Unit = { runOnUiThread { logView.text = ShoeManager.logText() } }

    private fun dp(v: Int) = Ui.dp(this, v)

    /** Lato (sinistra/destra) dedotto dai dati del produttore trasmessi dalla scarpa, come fa l'app originale. */
    private fun sideOf(r: ScanResult): Side? {
        val md = r.scanRecord?.getManufacturerSpecificData(0x78) ?: return null
        if (md.size < 8) return null
        return if ((md[7].toInt() and 1) == 1) Side.RIGHT else Side.LEFT
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(22), dp(16), dp(28))
        }

        // intestazione: banner ASCII + righe "di avvio" scritte a macchina
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        header.addView(Ui.banner(this))
        val boot = Ui.text(this, "", 12f).apply { setPadding(0, dp(8), 0, 0) }
        header.addView(boot)
        val cursor = TextView(this).apply {
            text = "_"; typeface = Ui.MONO_BOLD; textSize = 14f; setTextColor(Ui.GREEN); Ui.glow(this)
        }
        header.addView(cursor)
        cursorAnim = Ui.pulse(cursor, 0f, 500)
        root.addView(header)
        val bootText = S.t("boot_1") + "\n" + S.t("boot_2") + "\n" + S.t("boot_3")

        // le due scarpe
        Side.values().forEach { Ui.add(root, sideCard(it), 16) }

        // ricerca
        val scan = Ui.card(this)
        scan.addView(Ui.title(this, S.t("find_title")))
        filterBox = CheckBox(this).apply {
            text = S.t("filter_nike"); isChecked = true
            typeface = Ui.MONO; textSize = 13f; setTextColor(Ui.GREEN)
            buttonTintList = ColorStateList.valueOf(Ui.GREEN)
        }
        scan.addView(filterBox)
        scan.addView(Ui.row(this,
            Ui.btn(this, S.t("scan"), true) { startScan() },
            Ui.btn(this, S.t("stop")) { stopScan() }
        ))
        scan.addView(Ui.text(this, S.t("scan_help"), 12f, Ui.MUTED))
        deviceBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scan.addView(deviceBox)
        Ui.add(root, scan, 12)

        // info: Discord, termini, lingua
        val info = Ui.card(this)
        info.addView(Ui.title(this, S.t("info_title")))
        info.addView(Ui.btn(this, S.t("discord_btn"), true) { Flow.openDiscord(this) })
        info.addView(Ui.row(this,
            Ui.btn(this, S.t("terms_btn")) { openTerms() },
            Ui.btn(this, S.t("lang_btn")) { switchLanguage() }
        ))
        Ui.add(root, info, 12)

        // log
        val logCard = Ui.card(this)
        logCard.addView(Ui.title(this, S.t("log_title")))
        logView = TextView(this).apply {
            typeface = Ui.MONO; textSize = 11f; setTextIsSelectable(true)
            setTextColor(Ui.MUTED)
            text = ShoeManager.logText().ifEmpty { S.t("ready_log") }
        }
        logCard.addView(logView)
        logCard.addView(Ui.btn(this, S.t("copy_log")) {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("log", ShoeManager.logText()))
            ShoeManager.log(S.t("log_copied"))
        })
        Ui.add(root, logCard, 12)

        setContentView(Ui.screen(this, root))
        Ui.enter(root)
        Ui.typewriter(boot, bootText, 14)
    }

    private fun sideCard(side: Side): LinearLayout {
        val card = Ui.card(this)
        card.addView(Ui.title(this, side.title))
        val statusRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val dot = Ui.dot(this, Ui.MUTED)
        statusRow.addView(dot, LinearLayout.LayoutParams(dp(12), dp(12)).apply { rightMargin = dp(10) })
        val st = Ui.text(this, "", 14f)
        statusRow.addView(st)
        card.addView(statusRow)
        card.addView(Ui.row(this,
            Ui.btn(this, S.t("open_controls"), true) { openShoe(side) },
            Ui.btn(this, S.t("disconnect")) { ShoeManager.disconnect(side) }
        ))
        statusViews[side] = st
        dots[side] = dot
        return card
    }

    @Suppress("DEPRECATION")
    private fun openShoe(side: Side) {
        startActivity(Intent(this, ShoeActivity::class.java).putExtra("side", side.name))
        overridePendingTransition(R.anim.enter, R.anim.exit)
    }

    @Suppress("DEPRECATION")
    private fun openTerms() {
        startActivity(Intent(this, TermsActivity::class.java).putExtra("mode", "view"))
        overridePendingTransition(R.anim.enter, R.anim.exit)
    }

    private fun switchLanguage() {
        val next = if (S.lang == "it") "en" else "it"
        Settings.setLang(this, next)
        S.lang = next
        recreate()
    }

    private fun refresh() {
        Side.values().forEach { side ->
            val link = ShoeManager.link(side)
            val (text, color, pulsing) = when (link?.state) {
                null -> Triple(S.t("st_none"), Ui.MUTED, false)
                ShoeLink.State.DISCONNECTED -> Triple(S.t("st_disconnected"), Ui.MUTED, false)
                ShoeLink.State.CONNECTING -> Triple(S.t("st_connecting"), Ui.AMBER, true)
                ShoeLink.State.READY -> Triple(S.t("st_preparing"), Ui.AMBER, true)
                ShoeLink.State.AUTHENTICATING -> Triple(S.t("st_pairing"), Ui.AMBER, true)
                ShoeLink.State.AUTHENTICATED -> Triple(S.t("st_ready"), Ui.GREEN, false)
                ShoeLink.State.FAILED -> Triple(S.t("st_error"), Ui.RED, false)
            }
            val tv = statusViews.getValue(side)
            tv.text = if (link == null) "> $text" else "> ${link.label}: $text"
            tv.setTextColor(color)
            val dot = dots.getValue(side)
            Ui.setDotColor(dot, color)
            val mode = if (pulsing) 1 else 0
            if (modes[side] != mode) {
                pulses[side]?.cancel()
                dot.alpha = 1f
                pulses[side] = if (pulsing) Ui.pulse(dot) else null
                modes[side] = mode
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ShoeManager.addStateListener(stateCb)
        ShoeManager.addLogListener(logCb)
        logView.text = ShoeManager.logText().ifEmpty { S.t("ready_log") }
        refresh()
    }

    override fun onPause() {
        ShoeManager.removeStateListener(stateCb)
        ShoeManager.removeLogListener(logCb)
        super.onPause()
    }

    // ------------------------------------------------------------------ permessi e scansione

    private fun neededPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun hasPermissions() = neededPermissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasPermissions()) startScan() else ShoeManager.log(S.t("perm_denied"))
    }

    private val scanCb = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            if (!filterBox.isChecked && result.device.name == null) return
            found[result.device.address] = result
            runOnUiThread { rebuildDevices() }
        }

        override fun onScanFailed(errorCode: Int) { ShoeManager.log(S.t("scan_failed", errorCode)) }
    }

    private fun startScan() {
        if (!hasPermissions()) { requestPermissions(neededPermissions(), 1); return }
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled) { ShoeManager.log(S.t("bt_off")); return }
        val scanner = adapter.bluetoothLeScanner ?: run { ShoeManager.log(S.t("scanner_na")); return }
        if (scanning) stopScan()
        found.clear(); rebuildDevices()
        val filters = if (filterBox.isChecked)
            listOf(ScanFilter.Builder().setManufacturerData(0x78, byteArrayOf(0xAF.toByte(), 0x28)).build())
        else emptyList()
        scanner.startScan(filters, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), scanCb)
        scanning = true
        ShoeManager.log(S.t("scan_started", filterBox.isChecked))
    }

    private fun stopScan() {
        if (!scanning) return
        val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        try { adapter?.bluetoothLeScanner?.stopScan(scanCb) } catch (_: Exception) {}
        scanning = false
        ShoeManager.log(S.t("scan_stopped"))
    }

    private fun rebuildDevices() {
        deviceBox.removeAllViews()
        for (r in found.values.sortedByDescending { it.rssi }) {
            val detected = sideOf(r)
            val info = "${r.device.name ?: S.t("no_name")}  ${r.device.address}\n" +
                S.t("signal_line", r.rssi, detected?.short ?: "?")
            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(10), 0, dp(4))
            }
            box.addView(Ui.text(this, info, 12f))
            fun mark(s: Side) = if (detected == s) " *" else ""
            box.addView(Ui.row(this,
                Ui.btn(this, Side.LEFT.short + mark(Side.LEFT), detected == Side.LEFT) { connect(r, Side.LEFT) },
                Ui.btn(this, Side.RIGHT.short + mark(Side.RIGHT), detected == Side.RIGHT) { connect(r, Side.RIGHT) }
            ))
            deviceBox.addView(box)
        }
    }

    private fun connect(r: ScanResult, side: Side) {
        stopScan()
        ShoeManager.connect(r.device, side)
    }

    override fun onDestroy() {
        stopScan()
        cursorAnim?.cancel()
        super.onDestroy()
    }
}
