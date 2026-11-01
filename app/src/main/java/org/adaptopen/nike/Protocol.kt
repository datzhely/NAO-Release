package org.adaptopen.nike

import java.io.ByteArrayOutputStream

object Action {
    const val REQUEST = 0
    const val ACK = 1
    const val NAK = 2
    const val EVENT = 3
}

/** Codici comando usati dall'interfaccia. */
object Op {
    const val SERVO_MOVE = 0x00          // payload: campo1 = 1 stringi, 3 allenta
    const val SERVO_PRESET_SET = 0x01    // payload: campo1 = valore (uint32)
    const val SERVO_PRESET_GET = 0x02
    const val SERVO_POSITION_SET = 0x03  // payload: campo1 = valore (uint32)
    const val SERVO_POSITION_GET = 0x04
    const val IDENTIFY = 0x14            // payload vuoto
    const val SERIAL_NUMBER_GET = 0x32
    const val BATTERY_GET = 0x51         // payload vuoto
    const val FOOT_SENSOR = 0x52         // payload: campo1 = bool
    const val START_AUTH_KEY_GENERATION = 0x6E
    const val EXCHANGE_PUBLIC_KEY = 0x6F
    const val START_AUTHENTICATION = 0x70
    const val AUTHENTICATION_CHALLENGE = 0x71
    const val SET_LED_BASE = 0x82        // payload: R,G,B,modo (vedi note)
    const val GET_LED_BASE = 0x83

    /** Tabella completa dei comandi "sicuri" (nomi originali dell'app). Quelli di fabbrica/firmware sono esclusi di proposito. */
    val table: Map<String, Int> = mapOf(
        "SERVO_PRESET_SET" to 0x01,
        "SERVO_PRESET_GET" to 0x02,
        "SESSION_GET_STORAGE_SIZE" to 0xA2,
        "SERVO_MOVE" to 0x00,
        "SERVO_POSITION_SET" to 0x03,
        "SERVO_POSITION_GET" to 0x04,
        "SERVO_MOVE_COMPLETE_EVENT" to 0x05,
        "SERVO_SET_MAX_SEGS" to 0x06,
        "SERVO_SET_SEG_DIST" to 0x07,
        "SERVO_GET_PARAMS" to 0x08,
        "IDENTIFY" to 0x14,
        "GET_DEVICE_LOGS" to 0x1E,
        "GET_LOG_COUNT" to 0x1F,
        "RTC_TIME_SET" to 0x28,
        "RTC_TIME_GET" to 0x29,
        "RTC_TIME_SET_UTC" to 0x2A,
        "RTC_TIME_GET_UTC" to 0x2B,
        "SERIAL_NUMBER_GET" to 0x32,
        "BATTERY_LEVEL_GET" to 0x4F,
        "BATTERY_CHARGER_STATE_GET" to 0x51,
        "TOGGLE_FPS" to 0x52,
        "FPS_VALUE_GET" to 0x54,
        "FPS_STATUS_GET" to 0x57,
        "BLE_DISCONNECT" to 0x5A,
        "USER_STATS_GET" to 0x64,
        "START_AUTH_KEY_GENERATION" to 0x6E,
        "EXCHANGE_PUBLIC_KEY" to 0x6F,
        "START_AUTHENTICATION" to 0x70,
        "AUTHENTICATION_CHALLENGE" to 0x71,
        "SYSTEM_STATS_GET" to 0x79,
        "SET_LED_BASE" to 0x82,
        "GET_LED_BASE" to 0x83,
        "LOG_GET_NUM_AVAIL_ENTRIES" to 0x8C,
        "LOG_GET_LOG_ENTRIES" to 0x8D,
        "LOG_TRANSMIT_DATA" to 0x8E,
        "LOG_TRANSMIT_DATA_COMPLETE" to 0x8F,
        "SESSION_START" to 0x96,
        "SESSION_STOP" to 0x97,
        "SESSION_ENDED" to 0x98,
        "SESSION_GET_ID_LIST" to 0x99,
        "SESSION_GET_INFO" to 0x9A,
        "SESSION_GET_CONFIGURATION" to 0x9B,
        "SESSION_SET_CONFIGURATION" to 0x9C,
        "SESSION_GET_MEMS_RATE" to 0x9D,
        "SESSION_SET_MEMS_RATE" to 0x9E,
        "SESSION_DATA_METRICS_NOTIFICATION" to 0x9F,
        "SESSION_DATA_AGGREGATE_NOTIFICATION" to 0xA0,
        "SESSION_PULL_REQUEST" to 0xA1,
        "SESSION_PAUSE" to 0xA3,
        "SESSION_RESUME" to 0xA4,
        "SESSION_DATA_MEMS_NOTIFICATION" to 0xA5,
        "SESSION_GET_ACTIVE_ID" to 0xA6,
        "SESSION_AUTOADAPT_ENABLE" to 0xA7,
        "SESSION_AUTOADAPT_DISABLE" to 0xA8,
        "SESSION_GET_AUTOADAPT_CONFIG" to 0xA9,
        "SESSION_SET_AUTOADAPT_CONFIG" to 0xAA,
        "SESSION_ACTIVITY_NOTIFICATION" to 0xAB,
        "SESSION_GET_ACTIVITY_STATE" to 0xAC,
        "SESSION_AUTOADAPT_GET_CURRENT_STATE" to 0xAD,
        "SESSION_GET_CAPSENSE_RATE" to 0xAE,
        "SESSION_SET_CAPSENSE_RATE" to 0xAF,
        "SESSION_DATA_CAPSENSE_NOTIFICATION" to 0xB0,
        "SESSION_DATA_TEMP_NOTIFICATION" to 0xB1,
        "SESSION_SET_GESTURE_CONFIGURATION" to 0xB2,
        "SESSION_GET_GESTURE_CONFIGURATION" to 0xB3,
        "SESSION_CLEAR_GESTURE_DETECTION_COUNT" to 0xB4,
        "SESSION_GET_GESTURE_DETECTION_COUNT" to 0xB5,
        "SESSION_GESTURE_EVENT_NOTIFICATION" to 0xB6,
        "SYS_CONFIG_SET_FPS_AUTOLACE_TARGET" to 0xCC,
        "SYS_CONFIG_GET_FPS_AUTOLACE_TARGET" to 0xCD,
        "SYS_CONFIG_GET_MFG_DATE" to 0xCE,
        "SYS_CONFIG_GET_IMP_DATE" to 0xCF,
        "SYS_CONFIG_GET_RL" to 0xD0,
        "SYS_CONFIG_GET_SMART_SN_SHOE" to 0xD1,
        "SYS_CONFIG_GET_SMART_SN_ENG" to 0xD2,
        "SYS_CONFIG_SET_LED_INTENSITY" to 0xD3,
        "SYS_CONFIG_GET_LED_INTENSITY" to 0xD4,
        "SYS_CONFIG_GET_BLE_FW_VERSION" to 0xD5,
        "SYS_CONFIG_GET_MCU_FW_VERSION" to 0xD6,
        "SYS_CONFIG_GET_CAPSENSE_FW_VERSION" to 0xD7,
        "LED_CLEAR" to 0xD8,
        "MOTOR_STOP" to 0xD9,
        "ANIMATION_EXECUTE" to 0xDA,
        "ANIMATION_SAVE_DATA" to 0xDB,
        "ANIMATION_LEVEL_COLORS" to 0xDC,
        "ANIMATION_GET_COLOR" to 0xDD,
        "ANIMATION_SET_COLOR" to 0xDE,
        "ANIMATION_SET_PULSE_CYCLE" to 0xDF,
        "ANIMATION_SET_ANIMATION" to 0xE0,
        "ANIMATION_SET_ALL_DEFAULTS" to 0xE1,
        "ANIMATION_SET_BRIGHTNESS" to 0xE2,
        "MSERVO_GET_CURRENT_HISTOGRAM" to 0xE4,
        "ENTER_PLAY_MODE" to 0xE5,
        "EXIT_PLAY_MODE" to 0xE6,
        "BATTERY_GET_HEALTH_METRICS" to 0xE9,
        "USER_ANIMATION_SET_ANIMATION" to 0xEA,
        "USER_ANIMATION_GET_ANIMATION" to 0xEB,
        "USER_ANIMATION_START_ANIMATION" to 0xEC,
        "USER_ANIMATION_STOP_ANIMATION" to 0xED,
        "CUSTOM_ANIMATION_GET_ANIMATION" to 0xEE
    )
}

class Frame(val type: Int, val action: Int, val payload: ByteArray) {
    /** [tipo][lunghezza basso][azione<<6 | lunghezza alto][payload] */
    fun encode(): ByteArray {
        val len = payload.size
        require(len <= 513) { "payload troppo lungo ($len)" }
        val b = ByteArray(3 + len)
        b[0] = type.toByte()
        b[1] = (len and 0xFF).toByte()
        b[2] = (((action and 3) shl 6) or ((len shr 8) and 0x3F)).toByte()
        System.arraycopy(payload, 0, b, 3, len)
        return b
    }

    companion object {
        fun decode(b: ByteArray): Frame? {
            if (b.size < 3) return null
            val b1 = b[1].toInt() and 0xFF
            val b2 = b[2].toInt() and 0xFF
            val action = (b2 and 0xC0) shr 6
            val len = ((b2 and 0x3F) shl 8) or b1
            if (len > b.size - 3) return null
            return Frame(b[0].toInt() and 0xFF, action, b.copyOfRange(3, 3 + len))
        }
    }
}

object Transport {
    const val CHUNK = 19

    /** Spezza un frame in scritture BLE: [seq | 0x80 se ultimo][fino a 19 byte]. */
    fun fragment(frame: ByteArray, startSeq: Int): List<ByteArray> {
        val out = ArrayList<ByteArray>()
        var off = 0
        var seq = startSeq
        while (off < frame.size) {
            val n = minOf(CHUNK, frame.size - off)
            val last = off + n >= frame.size
            val chunk = ByteArray(n + 1)
            chunk[0] = ((seq and 0x3F) or (if (last) 0x80 else 0)).toByte()
            System.arraycopy(frame, off, chunk, 1, n)
            out.add(chunk)
            off += n
            seq = (seq + 1) % 64
        }
        return out
    }

    /** Distanza avanti da a verso b su un contatore a 64 valori. */
    fun dist(a: Int, b: Int): Int = ((b - a) % 64 + 64) % 64
}

/** Mini protobuf: solo quello che serve. */
object Pb {
    class Field(val num: Int, val wire: Int, val value: Long, val bytes: ByteArray?)

    private fun putVarint(out: ByteArrayOutputStream, value: Long) {
        var v = value
        while (true) {
            if ((v and 0x7FL.inv()) == 0L) { out.write(v.toInt()); return }
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
    }

    fun uint32(num: Int, v: Long): ByteArray = ByteArrayOutputStream().also {
        putVarint(it, (num shl 3).toLong()); putVarint(it, v and 0xFFFFFFFFL)
    }.toByteArray()

    /** int32 con segno esteso a 64 bit (come protobuf: i negativi occupano 10 byte). */
    fun int32(num: Int, v: Int): ByteArray = ByteArrayOutputStream().also {
        putVarint(it, (num shl 3).toLong()); putVarint(it, v.toLong())
    }.toByteArray()

    fun bool(num: Int, v: Boolean): ByteArray = uint32(num, if (v) 1 else 0)

    fun bytes(num: Int, data: ByteArray): ByteArray = ByteArrayOutputStream().also {
        putVarint(it, ((num shl 3) or 2).toLong()); putVarint(it, data.size.toLong()); it.write(data)
    }.toByteArray()

    fun concat(vararg parts: ByteArray): ByteArray {
        val o = ByteArrayOutputStream(); parts.forEach { o.write(it) }; return o.toByteArray()
    }

    fun parse(b: ByteArray): List<Field> {
        val out = ArrayList<Field>()
        var i = 0
        fun varint(): Long {
            var r = 0L; var s = 0
            while (i < b.size) {
                val x = b[i++].toInt() and 0xFF
                r = r or ((x and 0x7F).toLong() shl s); s += 7
                if (x and 0x80 == 0) break
            }
            return r
        }
        while (i < b.size) {
            val tag = varint().toInt()
            val num = tag ushr 3
            when (tag and 7) {
                0 -> out.add(Field(num, 0, varint(), null))
                1 -> { if (i + 8 > b.size) return out; out.add(Field(num, 1, 0, b.copyOfRange(i, i + 8))); i += 8 }
                2 -> { val n = varint().toInt(); if (n < 0 || i + n > b.size) return out; out.add(Field(num, 2, 0, b.copyOfRange(i, i + n))); i += n }
                5 -> { if (i + 4 > b.size) return out; out.add(Field(num, 5, 0, b.copyOfRange(i, i + 4))); i += 4 }
                else -> return out
            }
        }
        return out
    }

    fun firstBytes(b: ByteArray, num: Int): ByteArray? = parse(b).firstOrNull { it.num == num && it.wire == 2 }?.bytes
    fun firstInt(b: ByteArray, num: Int): Long? = parse(b).firstOrNull { it.num == num && it.wire == 0 }?.value

    fun describe(b: ByteArray): String {
        if (b.isEmpty()) return "(vuoto)"
        val f = parse(b)
        if (f.isEmpty()) return "raw=" + hex(b)
        return f.joinToString(" ") {
            when (it.wire) {
                0 -> "#${it.num}=${it.value}"
                else -> "#${it.num}=0x" + hex(it.bytes!!)
            }
        }
    }
}

fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

fun unhex(s: String): ByteArray {
    val c = s.filter { it.isLetterOrDigit() }
    require(c.length % 2 == 0) { "hex di lunghezza dispari" }
    return ByteArray(c.length / 2) { c.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}
