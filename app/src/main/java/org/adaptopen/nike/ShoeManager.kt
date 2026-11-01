package org.adaptopen.nike

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

enum class Side(val tag: String) {
    LEFT("SX"),
    RIGHT("DX")
}

val Side.title: String get() = S.t(if (this == Side.LEFT) "side_left_title" else "side_right_title")
val Side.short: String get() = S.t(if (this == Side.LEFT) "side_left" else "side_right")

class CitadelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ShoeManager.init(this)
        S.init(this)
    }
}

class Preset(val name: String, val position: Int?)

/** Preset salvati sul telefono (3 per scarpa). */
class PresetStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("presets", Context.MODE_PRIVATE)

    fun get(side: Side, slot: Int): Preset {
        val name = sp.getString("${side.tag}_name_$slot", null) ?: S.t("preset_default", slot + 1)
        val pos = if (sp.contains("${side.tag}_pos_$slot")) sp.getInt("${side.tag}_pos_$slot", 0) else null
        return Preset(name, pos)
    }

    fun put(side: Side, slot: Int, p: Preset) {
        val e = sp.edit().putString("${side.tag}_name_$slot", p.name)
        if (p.position != null) e.putInt("${side.tag}_pos_$slot", p.position) else e.remove("${side.tag}_pos_$slot")
        e.apply()
    }

    fun clearPosition(side: Side, slot: Int) {
        sp.edit().remove("${side.tag}_pos_$slot").apply()
    }
}

/** Tiene le connessioni alle scarpe vive mentre si passa da una schermata all'altra. */
object ShoeManager {
    private var inited = false
    lateinit var app: Context
        private set
    lateinit var keys: KeyStore
        private set
    lateinit var presets: PresetStore
        private set

    private val links = ConcurrentHashMap<Side, ShoeLink>()
    private val logBuf = StringBuilder()
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val logListeners = CopyOnWriteArrayList<(String) -> Unit>()
    private val stateListeners = CopyOnWriteArrayList<() -> Unit>()

    fun init(ctx: Context) {
        if (inited) return
        inited = true
        app = ctx.applicationContext
        keys = KeyStore(app)
        presets = PresetStore(app)
    }

    fun link(side: Side): ShoeLink? = links[side]

    fun isReady(side: Side) = links[side]?.state == ShoeLink.State.AUTHENTICATED

    // ------------------------------------------------------------ log

    fun log(msg: String) {
        val line = timeFmt.format(Date()) + " " + msg + "\n"
        synchronized(logBuf) {
            logBuf.append(line)
            if (logBuf.length > 30000) logBuf.delete(0, logBuf.length - 24000)
        }
        logListeners.forEach { it(line) }
    }

    fun logText(): String = synchronized(logBuf) { logBuf.toString() }

    fun addLogListener(l: (String) -> Unit) { logListeners.add(l) }
    fun removeLogListener(l: (String) -> Unit) { logListeners.remove(l) }
    fun addStateListener(l: () -> Unit) { stateListeners.add(l) }
    fun removeStateListener(l: () -> Unit) { stateListeners.remove(l) }
    fun notifyState() { stateListeners.forEach { it() } }

    // ------------------------------------------------------------ connessioni

    fun connect(device: BluetoothDevice, side: Side) {
        val other = if (side == Side.LEFT) Side.RIGHT else Side.LEFT
        links[other]?.let {
            if (it.device.address == device.address) { it.disconnect(); links.remove(other) }
        }
        val existing = links[side]
        if (existing != null) {
            if (existing.device.address == device.address &&
                existing.state != ShoeLink.State.DISCONNECTED && existing.state != ShoeLink.State.FAILED) {
                log(S.t("l_already", existing.label))
                return
            }
            existing.disconnect()
        }
        val link = ShoeLink(app, device, side.tag, keys, { log(it) }, { notifyState() })
        links[side] = link
        notifyState()
        link.connect()
    }

    fun disconnect(side: Side) {
        links[side]?.disconnect()
    }

    fun disconnectAll() {
        links.values.forEach { it.disconnect() }
    }
}
