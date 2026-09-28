package com.radustavila.bleexplorer.domain.model

/**
 * Platform independent representation of a BLE peripheral as seen through its advertisements.
 *
 * Deliberately free of any Android type: the domain layer never sees `BluetoothDevice`,
 * `ScanResult` or `ScanRecord`.
 *
 * @param id stable identifier of the device for the current scan session (the hardware address
 *   reported by the scanner). Device names are *not* unique and must never be used as a key.
 */
data class BleDevice(
    val id: String,
    val address: String,
    val name: String?,
    val rssi: Int,
    val txPowerDbm: Int?,
    val isConnectable: Boolean?,
    val serviceUuids: List<String> = emptyList(),
    val manufacturerData: List<ManufacturerData> = emptyList(),
    val serviceData: List<ServiceData> = emptyList(),
    val advertisedName: String? = null,
    val firstSeenAtMillis: Long = 0L,
    val lastSeenAtMillis: Long = 0L,
    val seenCount: Int = 1,
) {
    val displayName: String? get() = name ?: advertisedName

    val signalStrength: SignalStrength get() = SignalStrength.fromRssi(rssi)

    /** Best-effort vendor name, derived from the Bluetooth SIG company identifier. */
    val manufacturerName: String? get() = manufacturerData.firstNotNullOfOrNull { it.companyName }
}

/**
 * Manufacturer specific advertising payload. The payload is intentionally kept as raw bytes:
 * proprietary formats are not decoded, only displayed.
 */
class ManufacturerData(
    val companyId: Int,
    val companyName: String?,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ManufacturerData) return false
        return companyId == other.companyId &&
            companyName == other.companyName &&
            payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int {
        var result = companyId
        result = 31 * result + (companyName?.hashCode() ?: 0)
        result = 31 * result + payload.contentHashCode()
        return result
    }

    override fun toString(): String = "ManufacturerData(companyId=$companyId, size=${payload.size})"
}

/** Service specific advertising payload, keyed by the advertised service UUID. */
class ServiceData(
    val uuid: String,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ServiceData) return false
        return uuid == other.uuid && payload.contentEquals(other.payload)
    }

    override fun hashCode(): Int = 31 * uuid.hashCode() + payload.contentHashCode()

    override fun toString(): String = "ServiceData(uuid=$uuid, size=${payload.size})"
}

/** Qualitative bucket for an RSSI value. Never presented as a distance. */
enum class SignalStrength {
    STRONG,
    MEDIUM,
    WEAK,
    ;

    companion object {
        fun fromRssi(rssi: Int): SignalStrength = when {
            rssi >= STRONG_THRESHOLD_DBM -> STRONG
            rssi >= MEDIUM_THRESHOLD_DBM -> MEDIUM
            else -> WEAK
        }

        private const val STRONG_THRESHOLD_DBM = -60
        private const val MEDIUM_THRESHOLD_DBM = -80
    }
}
