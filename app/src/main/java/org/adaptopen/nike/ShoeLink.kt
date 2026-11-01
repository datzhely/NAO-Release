package org.adaptopen.nike

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import java.io.IOException
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/** Stato della batteria letto dalla scarpa. */
class BatteryInfo(val percent: Int, val millivolts: Int, val tempC: Double, val state: Int)

/** Chiavi di pairing salvate per indirizzo della scarpa. */
class KeyStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("adapt_keys", Context.MODE_PRIVATE)
    fun get(addr: String): ByteArray? = sp.getString(addr, null)?.let { unhex(it) }
    fun put(addr: String, key: ByteArray) { sp.edit().putString(addr, hex(key)).apply() }
    fun remove(addr: String) { sp.edit().remove(addr).apply() }
}

/**
 * Collegamento con UNA scarpa: BLE (servizio "Ripley"), frammentazione, controllo di flusso,
 * pairing/autenticazione e comandi.
 */
@SuppressLint("MissingPermission")
class ShoeLink(
    private val ctx: Context,
    val device: BluetoothDevice,
    val tag: String,
    private val keys: KeyStore,
    private val log: (String) -> Unit,
    private val onState: (ShoeLink) -> Unit
) {
    enum class State { DISCONNECTED, CONNECTING, READY, AUTHENTICATING, AUTHENTICATED, FAILED }

    companion object {
        val SERVICE: UUID = UUID.fromString("1A2328AF-3D0B-4B04-A2AA-973C239D3904")
        val CH_WRITE: UUID = UUID.fromString("226BAEA6-1543-40C2-8EAE-A69B02171B08")
        val CH_NOTIFY: UUID = UUID.fromString("30C4142F-B083-42CF-865A-D5B91801BCD7")
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        const val DIR_TIGHTEN = 1
        const val DIR_LOOSEN = 3
    }

    @Volatile var state = State.DISCONNECTED
        private set

    val label: String get() = tag + " " + device.address.takeLast(5)

    private var gatt: BluetoothGatt? = null
    private var chWrite: BluetoothGattCharacteristic? = null
    private var chNotify: BluetoothGattCharacteristic? = null

    private val worker = Executors.newSingleThreadExecutor()
    private val ioExec = Executors.newSingleThreadExecutor()
    private val gattLock = ReentrantLock()
    @Volatile private var writeLatch: CountDownLatch? = null

    // trasporto: invio
    private var sendSeq = 0
    @Volatile private var windowBase = 0
    private val windowSync = Object()

    // trasporto: ricezione
    private var recvCount = 0
    private var ackBase = 0
    private val gather = ArrayList<ByteArray>()

    private val pending = ConcurrentHashMap<Int, CompletableFuture<Frame>>()
    @Volatile private var sawNakWait = false

    /** Ultima posizione dei lacci nota (dagli eventi della scarpa o dalla lettura diretta). */
    @Volatile var lastPosition: Int? = null
    @Volatile var onPosition: ((Int) -> Unit)? = null
    @Volatile var lastBattery: BatteryInfo? = null
    @Volatile var onBattery: ((BatteryInfo) -> Unit)? = null
    @Volatile private var moving = false
    val isMoving: Boolean get() = moving

    private fun setState(s: State) { state = s; onState(this) }

    // ------------------------------------------------------------------ connessione

    fun connect() {
        setState(State.CONNECTING)
        log(S.t("l_connecting", label))
        gatt = device.connectGatt(ctx, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        try { gatt?.disconnect() } catch (_: Exception) {}
        unregisterBondReceiver()
    }

    fun forgetKey() {
        keys.remove(device.address)
        log(S.t("l_key_forgotten", label))
        removeBond()
    }

    /** Cancella l'accoppiamento Bluetooth di Android con questa scarpa (equivale a "Dimentica" nelle impostazioni). */
    fun removeBond() {
        try {
            val m = device.javaClass.getMethod("removeBond")
            val ok = m.invoke(device) as? Boolean
            log(S.t(if (ok == true) "l_removebond_ok" else "l_removebond_fail", label))
        } catch (e: Exception) {
            log(S.t("l_removebond_unavail", label))
        }
    }

    // ------------------------------------------------------------------ bonding Bluetooth di Android

    private var bondReceiver: BroadcastReceiver? = null

    private fun bondName(st: Int) = when (st) {
        BluetoothDevice.BOND_NONE -> S.t("bond_none")
        BluetoothDevice.BOND_BONDING -> S.t("bond_bonding")
        BluetoothDevice.BOND_BONDED -> S.t("bond_bonded")
        else -> "?$st"
    }

    private fun registerBondReceiver() {
        if (bondReceiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) {
                @Suppress("DEPRECATION")
                val d: BluetoothDevice? = if (Build.VERSION.SDK_INT >= 33)
                    i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                else i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                if (d?.address != device.address) return
                val st = i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)
                val prev = i.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, BluetoothDevice.ERROR)
                log(S.t("l_bond_change", label, bondName(prev), bondName(st)))
                if (st == BluetoothDevice.BOND_BONDED) {
                    startDiscovery()
                } else if (st == BluetoothDevice.BOND_NONE && prev == BluetoothDevice.BOND_BONDING) {
                    log(S.t("l_bond_failed", label))
                    setState(State.FAILED)
                }
            }
        }
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) ctx.registerReceiver(r, filter, Context.RECEIVER_EXPORTED)
        else ctx.registerReceiver(r, filter)
        bondReceiver = r
    }

    private fun unregisterBondReceiver() {
        bondReceiver?.let { try { ctx.unregisterReceiver(it) } catch (_: Exception) {} }
        bondReceiver = null
    }

    private fun startDiscovery() {
        Thread {
            try { Thread.sleep(600) } catch (_: InterruptedException) {}
            val g = gatt
            if (g != null) { log(S.t("l_discovering", label)); g.discoverServices() }
        }.start()
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                log(S.t("l_connected", label, status, bondName(device.bondState)))
                registerBondReceiver()
                when (device.bondState) {
                    BluetoothDevice.BOND_BONDED -> startDiscovery()
                    BluetoothDevice.BOND_BONDING ->
                        log(S.t("l_bonding_wait", label))
                    else -> {
                        log(S.t("l_bonding_start", label))
                        val ok = device.createBond()
                        log(S.t("l_bond_request", label, ok))
                    }
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                log(S.t("l_disconnected", label, status))
                unregisterBondReceiver()
                pending.values.forEach { it.completeExceptionally(IOException(S.t("ex_disconnected"))) }
                pending.clear()
                setState(State.DISCONNECTED)
                try { g.close() } catch (_: Exception) {}
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val s = g.getService(SERVICE)
            if (s == null) {
                log(S.t("l_no_service", label, g.services.joinToString { it.uuid.toString() }))
                setState(State.FAILED); return
            }
            chWrite = s.getCharacteristic(CH_WRITE)
            chNotify = s.getCharacteristic(CH_NOTIFY)
            val n = chNotify
            if (chWrite == null || n == null) {
                log(S.t("l_no_chars", label, chWrite != null, n != null))
                setState(State.FAILED); return
            }
            g.setCharacteristicNotification(n, true)
            val d = n.getDescriptor(CCCD)
            if (d == null) { log(S.t("l_no_cccd", label)); setState(State.FAILED); return }
            if (Build.VERSION.SDK_INT >= 33) {
                g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                run { d.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE; g.writeDescriptor(d) }
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) {
            log(S.t("l_notify_on", label, status))
            if (status != BluetoothGatt.GATT_SUCCESS) {
                log(S.t("l_notify_fail", label, status))
                setState(State.FAILED); return
            }
            setState(State.READY)
            worker.execute { authenticate() }
        }

        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) log(S.t("l_gatt_write", label, status))
            writeLatch?.countDown()
        }

        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            c.value?.let { onNotify(it.copyOf()) }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            onNotify(value.copyOf())
        }
    }

    // ------------------------------------------------------------------ trasporto

    private fun writeRaw(data: ByteArray): Boolean {
        val g = gatt ?: return false
        val ch = chWrite ?: return false
        gattLock.lock()
        try {
            val latch = CountDownLatch(1)
            writeLatch = latch
            val wt = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0)
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT else BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            val ok = if (Build.VERSION.SDK_INT >= 33) {
                g.writeCharacteristic(ch, data, wt) == 0 // BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                run { ch.writeType = wt; ch.value = data; g.writeCharacteristic(ch) }
            }
            if (!ok) return false
            return latch.await(2, TimeUnit.SECONDS)
        } finally {
            gattLock.unlock()
        }
    }

    private fun sendFrame(f: Frame) {
        val bytes = f.encode()
        val frags = synchronized(this) {
            val fr = Transport.fragment(bytes, sendSeq)
            sendSeq = (sendSeq + fr.size) % 64
            fr
        }
        for (frag in frags) {
            if (!writeRaw(frag)) throw IOException(S.t("ex_write_failed"))
            val seq = frag[0].toInt() and 0x3F
            val last = (frag[0].toInt() and 0x80) != 0
            if (!last && Transport.dist(windowBase, seq) >= 4) waitWindow(seq)
        }
    }

    private fun waitWindow(seq: Int) {
        val deadline = System.currentTimeMillis() + 1500
        synchronized(windowSync) {
            while (Transport.dist(windowBase, seq) >= 4 && System.currentTimeMillis() < deadline) {
                (windowSync as Object).wait(50)
            }
        }
    }

    private fun onNotify(data: ByteArray) {
        if (data.isEmpty()) return
        recvCount++
        val h = data[0].toInt() and 0xFF
        if (h and 0x40 != 0) { // pacchetto di controllo di flusso dalla scarpa
            val s = h and 0x3F
            val kind = if (data.size >= 2) data[1].toInt() else 0
            if (kind == 0) {
                windowBase = (s + 1) % 64
                synchronized(windowSync) { (windowSync as Object).notifyAll() }
            } else {
                log(S.t("l_resend", label, hex(data)))
            }
            return
        }
        val seq = h and 0x3F
        val last = (h and 0x80) != 0
        gather.add(data.copyOfRange(1, data.size))
        if (recvCount > 1 && Transport.dist(ackBase, seq) == 1) {
            ioExec.execute { writeRaw(byteArrayOf((0xC0 or seq).toByte(), 0)) }
            ackBase = (seq + 1) % 64
        }
        if (last) {
            val total = gather.sumOf { it.size }
            val msg = ByteArray(total)
            var o = 0
            for (p in gather) { System.arraycopy(p, 0, msg, o, p.size); o += p.size }
            gather.clear()
            val f = Frame.decode(msg)
            if (f == null) { log(S.t("l_bad_msg", label, hex(msg))); return }
            dispatch(f)
        }
    }

    private fun dispatch(f: Frame) {
        if (f.action == Action.EVENT) {
            when (f.type) {
                Op.EXCHANGE_PUBLIC_KEY ->
                    log(S.t("l_press_button", label))
                0x05 -> {
                    val p = (Pb.firstInt(f.payload, 2) ?: 0L).toInt()
                    lastPosition = p
                    onPosition?.invoke(p)
                    if (!moving) log(S.t("l_position", label, p))
                }
                else -> log(S.t("l_event", label, f.type, Pb.describe(f.payload)))
            }
            return
        }
        // Durante START_AUTH_KEY_GENERATION (0x6E) la scarpa manda subito un 0x6F "di attesa":
        // l'app originale lo ignora e aspetta la vera risposta, che arriva dopo la pressione del pulsante.
        if (f.type == Op.EXCHANGE_PUBLIC_KEY && pending.containsKey(Op.START_AUTH_KEY_GENERATION) &&
            !pending.containsKey(Op.EXCHANGE_PUBLIC_KEY)) {
            if (!sawNakWait) {
                sawNakWait = true
                log(S.t("l_nak_wait", label))
            }
            return
        }
        val p = pending[f.type]
        if (p != null) p.complete(f)
        else log(S.t("l_unexpected", label, f.type, f.action, Pb.describe(f.payload)))
    }

    /** Invia una richiesta e aspetta la risposta con lo stesso codice. */
    private fun request(type: Int, payload: ByteArray, timeoutMs: Long = 5000): Frame {
        val fut = CompletableFuture<Frame>()
        pending[type] = fut
        try {
            sendFrame(Frame(type, Action.REQUEST, payload))
            return try {
                fut.get(timeoutMs, TimeUnit.MILLISECONDS)
            } catch (e: java.util.concurrent.TimeoutException) {
                throw IOException(S.t("ex_timeout", type))
            }
        } finally {
            pending.remove(type)
        }
    }

    // ------------------------------------------------------------------ pairing e autenticazione

    private fun pair(): ByteArray {
        sawNakWait = false
        log(S.t("l_pairing_start", label))
        val r1 = request(Op.START_AUTH_KEY_GENERATION, ByteArray(0), 30000)
        if (r1.action == Action.NAK) throw IOException(S.t("ex_pair_refused"))
        val idx = (Pb.firstInt(r1.payload, 1) ?: 0L).toInt()
        if (idx !in 0..3) throw IOException(S.t("ex_dh_index", idx))
        log(S.t("l_dh_group", label, idx))
        val dh = Dh(idx)
        val r2 = request(Op.EXCHANGE_PUBLIC_KEY, Pb.bytes(1, dh.publicBytes), 33000)
        if (r2.action == Action.NAK) throw IOException(S.t("ex_kx_refused"))
        val peer = Pb.firstBytes(r2.payload, 1) ?: throw IOException(S.t("ex_no_pubkey", hex(r2.payload)))
        return dh.authKey(peer)
    }

    private fun doAuth(key: ByteArray): Boolean {
        val nonce = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val r = request(Op.START_AUTHENTICATION, Pb.bytes(1, nonce), 8000)
        if (r.action == Action.NAK) return false
        val challenge = Pb.firstBytes(r.payload, 2) ?: throw IOException(S.t("ex_no_challenge", hex(r.payload)))
        val enc = Crypto.aesEcb(challenge, key)
        val r2 = request(Op.AUTHENTICATION_CHALLENGE, Pb.bytes(1, enc), 8000)
        return r2.action != Action.NAK
    }

    private fun authenticate() {
        try {
            setState(State.AUTHENTICATING)
            val stored = keys.get(device.address)
            if (stored != null) {
                log(S.t("l_auth_stored", label))
                if (doAuth(stored)) { setState(State.AUTHENTICATED); log(S.t("l_authenticated", label)); return }
                log(S.t("l_stored_rejected", label))
                setState(State.FAILED); return
            }
            for (attempt in 1..2) {
                val key = pair()
                if (doAuth(key)) {
                    keys.put(device.address, key)
                    setState(State.AUTHENTICATED)
                    log(S.t("l_pair_done", label))
                    return
                }
                log(S.t("l_auth_after_pair_fail", label, attempt))
            }
            setState(State.FAILED)
        } catch (e: Exception) {
            log(S.t("l_pair_error", label, e.message ?: ""))
            setState(State.FAILED)
        }
    }

    // ------------------------------------------------------------------ comandi

    private fun actionName(a: Int) = when (a) { 0 -> "REQ"; 1 -> "ACK"; 2 -> "NAK"; 3 -> "EVT"; else -> "?$a" }

    private fun parseBattery(p: ByteArray): BatteryInfo {
        val mv = (Pb.firstInt(p, 2) ?: 0L).toInt()
        val pct = (Pb.firstInt(p, 4) ?: 0L).toInt()
        val st = (Pb.firstInt(p, 5) ?: 0L).toInt()
        val tb = Pb.parse(p).firstOrNull { it.num == 3 && it.wire == 1 }?.bytes
        val temp = if (tb != null)
            java.lang.Double.longBitsToDouble(java.nio.ByteBuffer.wrap(tb).order(java.nio.ByteOrder.LITTLE_ENDIAN).long)
        else Double.NaN
        return BatteryInfo(pct, mv, temp, st)
    }

    private fun fmtBattery(b: BatteryInfo): String = S.t("bat_text", b.percent, b.millivolts, b.tempC, b.state)

    /** Legge la batteria della scarpa. Il risultato arriva in cb (null se errore). */
    fun readBattery(cb: (BatteryInfo?) -> Unit) {
        worker.execute {
            if (state != State.AUTHENTICATED) { cb(null); return@execute }
            try {
                val r = request(Op.BATTERY_GET, ByteArray(0), 3000)
                val b = parseBattery(r.payload)
                lastBattery = b
                onBattery?.invoke(b)
                cb(b)
            } catch (e: Exception) {
                log(S.t("l_cmd_error", label, S.t("c_battery"), e.message ?: ""))
                cb(null)
            }
        }
    }

    private fun cmd(name: String, type: Int, payload: ByteArray = ByteArray(0), timeoutMs: Long = 5000) {
        worker.execute {
            if (state != State.AUTHENTICATED) { log(S.t("l_cmd_not_auth", label, name)); return@execute }
            try {
                val r = request(type, payload, timeoutMs)
                log("$label $name -> ${actionName(r.action)} ${Pb.describe(r.payload)}")
            } catch (e: Exception) {
                log(S.t("l_cmd_error", label, name, e.message ?: ""))
            }
        }
    }

    /**
     * Muove i lacci: un tocco = un passo, tenendo premuto continua fino al limite.
     * direction: DIR_TIGHTEN (stringi) o DIR_LOOSEN (allarga). Si ferma con stopMove().
     */
    fun startMove(direction: Int) {
        if (state != State.AUTHENTICATED) { log(S.t("l_not_ready", label)); return }
        if (moving) return
        moving = true
        worker.execute {
            var count = 0
            var unchanged = 0
            var lastSeen = lastPosition
            val t0 = System.currentTimeMillis()
            try {
                while (moving && state == State.AUTHENTICATED) {
                    request(Op.SERVO_MOVE, Pb.int32(1, direction), 2000)
                    count++
                    if (count == 1) {
                        // pausa iniziale: se rilasci subito e' stato solo un tocco (un passo)
                        var waited = 0
                        while (moving && waited < 280) { Thread.sleep(20); waited += 20 }
                    } else {
                        Thread.sleep(30)
                        val p = lastPosition
                        if (p == lastSeen) unchanged++ else { unchanged = 0; lastSeen = p }
                        if (unchanged >= 10) { log(S.t("l_limit", label)); break }
                    }
                    if (System.currentTimeMillis() - t0 > 25000) break
                }
            } catch (e: Exception) {
                log(S.t("l_move_stopped", label, e.message ?: ""))
            } finally {
                moving = false
            }
        }
    }

    fun stopMove() { moving = false }

    /** Legge la posizione attuale dei lacci. Il risultato arriva in cb (null se errore). */
    fun readPosition(cb: (Int?) -> Unit) {
        worker.execute {
            if (state != State.AUTHENTICATED) { cb(null); return@execute }
            try {
                val r = request(Op.SERVO_POSITION_GET, ByteArray(0), 3000)
                val p = (Pb.firstInt(r.payload, 1) ?: 0L).toInt()
                lastPosition = p
                onPosition?.invoke(p)
                cb(p)
            } catch (e: Exception) {
                log(S.t("l_readpos_failed", label, e.message ?: ""))
                cb(null)
            }
        }
    }

    fun tighten() = cmd(S.t("c_tighten"), Op.SERVO_MOVE, Pb.int32(1, 1))
    fun loosen() = cmd(S.t("c_loosen"), Op.SERVO_MOVE, Pb.int32(1, 3))
    fun identify() = cmd(S.t("c_identify"), Op.IDENTIFY)
    fun battery() = readBattery { b ->
        if (b != null) log("$label ${S.t("c_battery")} -> ACK ${fmtBattery(b)}")
    }
    fun serial() = cmd(S.t("c_serial"), Op.SERIAL_NUMBER_GET)
    fun footSensor(on: Boolean) = cmd(S.t(if (on) "c_foot_on" else "c_foot_off"), Op.FOOT_SENSOR, Pb.bool(1, on))
    fun setPreset(v: Int) = cmd(S.t("c_preset", v), Op.SERVO_PRESET_SET, Pb.uint32(1, v.toLong()))
    fun getPreset() = cmd(S.t("c_preset_q"), Op.SERVO_PRESET_GET)
    fun setPosition(v: Int) = cmd(S.t("c_position", v), Op.SERVO_POSITION_SET, Pb.uint32(1, v.toLong()))
    fun getPosition() = cmd(S.t("c_position_q"), Op.SERVO_POSITION_GET)
    fun getLed() = cmd(S.t("c_led_q"), Op.GET_LED_BASE)
    fun setLed(r: Int, g: Int, b: Int, mode: Int = 0) = cmd(
        S.t("c_led", r, g, b), Op.SET_LED_BASE,
        Pb.concat(Pb.uint32(1, r.toLong()), Pb.uint32(2, g.toLong()), Pb.uint32(3, b.toLong()), Pb.int32(4, mode))
    )
    fun raw(type: Int, payload: ByteArray) = cmd("RAW 0x%02X".format(type), type, payload)
}
