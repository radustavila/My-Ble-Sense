package com.radustavila.bleexplorer.domain.model

/**
 * Platform independent view of the GATT database exposed by a connected peripheral.
 *
 * As in the rest of the domain layer there is no Android type here: `BluetoothGatt`,
 * `BluetoothGattService` and `BluetoothGattCharacteristic` never leave `:core:bluetooth`.
 */
data class GattService(
    val uuid: String,
    val characteristics: List<GattCharacteristic>,
)

/**
 * A single characteristic of a service.
 *
 * [properties] is what the peripheral declares it supports; every action offered by the UI is
 * derived from it, never assumed.
 */
data class GattCharacteristic(
    val id: GattCharacteristicId,
    val properties: Set<GattCharacteristicProperty>,
) {
    val uuid: String get() = id.characteristicUuid

    val serviceUuid: String get() = id.serviceUuid

    val canRead: Boolean get() = GattCharacteristicProperty.READ in properties

    val canWriteWithResponse: Boolean get() = GattCharacteristicProperty.WRITE in properties

    val canWriteWithoutResponse: Boolean
        get() = GattCharacteristicProperty.WRITE_NO_RESPONSE in properties

    val canWrite: Boolean get() = canWriteWithResponse || canWriteWithoutResponse

    val canNotify: Boolean get() = GattCharacteristicProperty.NOTIFY in properties

    val canIndicate: Boolean get() = GattCharacteristicProperty.INDICATE in properties

    /**
     * Notifications and indications are configured through the same descriptor (the CCCD) and are
     * presented as one on/off action; only the value written to the descriptor differs.
     */
    val supportsSubscription: Boolean get() = canNotify || canIndicate
}

/**
 * Addresses a characteristic inside the connected device's GATT database.
 *
 * A characteristic UUID is only unique *within* its service, so the service UUID is part of the
 * identity — using the characteristic UUID alone is a classic source of "read the wrong one" bugs.
 */
data class GattCharacteristicId(
    val serviceUuid: String,
    val characteristicUuid: String,
)

enum class GattCharacteristicProperty {
    READ,
    WRITE,
    WRITE_NO_RESPONSE,
    NOTIFY,
    INDICATE,
}

/**
 * How a write is performed on the link layer:
 *  - [WITH_RESPONSE] waits for the peripheral's acknowledgement (property `WRITE`);
 *  - [WITHOUT_RESPONSE] is fire-and-forget (property `WRITE_NO_RESPONSE`).
 *
 * The type is chosen from the characteristic's declared properties, never guessed.
 */
enum class GattWriteType {
    WITH_RESPONSE,
    WITHOUT_RESPONSE,
}

/** Lifecycle of the single GATT connection the app maintains. Failures are reported separately. */
enum class GattConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCONNECTING,
}

/**
 * Snapshot of the current connection.
 *
 * Errors are deliberately *not* part of this type: a failed connection ends in [DISCONNECTED] and
 * the reason travels through its own error stream, so a transient failure can never leave the
 * state machine in a permanently broken state.
 */
data class GattConnection(
    val deviceId: String?,
    val state: GattConnectionState,
    val servicesDiscovered: Boolean = false,
) {
    val isConnected: Boolean get() = state == GattConnectionState.CONNECTED

    val isBusy: Boolean
        get() = state == GattConnectionState.CONNECTING || state == GattConnectionState.DISCONNECTING

    fun isConnectedTo(deviceId: String): Boolean = isConnected && this.deviceId == deviceId

    companion object {
        val Disconnected = GattConnection(deviceId = null, state = GattConnectionState.DISCONNECTED)
    }
}

/** Where a characteristic value came from. Notifications are pushed, reads are requested. */
enum class GattValueSource {
    READ,
    NOTIFICATION,
}

/**
 * A raw value observed on a characteristic.
 *
 * The payload stays a [ByteArray]: only the presentation layer may *optionally* offer an
 * interpretation, and only for characteristics whose format is defined by the Bluetooth SIG.
 */
class GattCharacteristicValue(
    val id: GattCharacteristicId,
    val value: ByteArray,
    val timestampMillis: Long,
    val source: GattValueSource,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GattCharacteristicValue) return false
        return id == other.id &&
            timestampMillis == other.timestampMillis &&
            source == other.source &&
            value.contentEquals(other.value)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + value.contentHashCode()
        result = 31 * result + timestampMillis.hashCode()
        result = 31 * result + source.hashCode()
        return result
    }

    override fun toString(): String =
        "GattCharacteristicValue(id=$id, size=${value.size}, source=$source)"
}
