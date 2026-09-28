package com.radustavila.bleexplorer.feature.gattinspector

import com.radustavila.bleexplorer.core.bluetooth.mapper.shortenBluetoothUuid
import com.radustavila.bleexplorer.core.common.format.toPrintableUtf8OrNull

/**
 * Optional, clearly labelled interpretation of a handful of Bluetooth SIG characteristics.
 *
 * Rules this deliberately follows:
 *  - only characteristics whose format the SIG defines are decoded, identified by their assigned
 *    16-bit UUID — a vendor may reuse a UUID, but it is the only signal that exists;
 *  - a payload that does not match the expected length is *not* decoded, rather than
 *    misinterpreted;
 *  - everything else returns `null` and the UI shows raw bytes only.
 *
 * No device-specific decoding lives here, and none should: the raw value is always shown too.
 */
internal object StandardCharacteristicDecoder {

    fun decode(characteristicUuid: String, value: ByteArray): String? {
        if (value.isEmpty()) return null
        return when (characteristicUuid.shortenBluetoothUuid()) {
            BATTERY_LEVEL -> value.singleOrNull()?.let { "${it.toInt() and 0xFF}%" }
            HEART_RATE_MEASUREMENT -> value.decodeHeartRate()
            TEMPERATURE -> value.decodeSint16(scale = 100.0, unit = "°C")
            HUMIDITY -> value.decodeUint16(scale = 100.0, unit = "%")
            in STRING_CHARACTERISTICS -> value.toPrintableUtf8OrNull()
            else -> null
        }
    }

    /**
     * Heart Rate Measurement: bit 0 of the flags byte selects an 8-bit or 16-bit measurement.
     * Anything after it (energy expended, RR intervals) is left alone.
     */
    private fun ByteArray.decodeHeartRate(): String? {
        val isUint16 = this[0].toInt() and 0x01 != 0
        val bpm = when {
            isUint16 && size >= 3 -> (this[1].toInt() and 0xFF) or ((this[2].toInt() and 0xFF) shl 8)
            !isUint16 && size >= 2 -> this[1].toInt() and 0xFF
            else -> return null
        }
        return "$bpm bpm"
    }

    private fun ByteArray.decodeSint16(scale: Double, unit: String): String? {
        if (size < 2) return null
        val raw = (((this[1].toInt() and 0xFF) shl 8) or (this[0].toInt() and 0xFF)).toShort()
        return "%.2f %s".format(raw / scale, unit)
    }

    private fun ByteArray.decodeUint16(scale: Double, unit: String): String? {
        if (size < 2) return null
        val raw = ((this[1].toInt() and 0xFF) shl 8) or (this[0].toInt() and 0xFF)
        return "%.2f %s".format(raw / scale, unit)
    }

    private const val BATTERY_LEVEL = "2A19"
    private const val HEART_RATE_MEASUREMENT = "2A37"
    private const val TEMPERATURE = "2A6E"
    private const val HUMIDITY = "2A6F"

    /** Characteristics the spec defines as UTF-8 strings. */
    private val STRING_CHARACTERISTICS = setOf(
        "2A00", // Device Name
        "2A24", // Model Number String
        "2A25", // Serial Number String
        "2A26", // Firmware Revision String
        "2A27", // Hardware Revision String
        "2A28", // Software Revision String
        "2A29", // Manufacturer Name String
    )
}
