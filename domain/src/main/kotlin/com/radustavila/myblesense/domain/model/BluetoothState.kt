package com.radustavila.myblesense.domain.model

/** Current state of the Bluetooth adapter, as far as the app is allowed to observe it. */
enum class BluetoothState {
    UNKNOWN,
    DISABLED,
    TURNING_ON,
    ENABLED,
    TURNING_OFF,
    ;

    val isEnabled: Boolean get() = this == ENABLED
}

/** Static hardware capabilities of the current device. */
data class BleCapability(
    val hasBluetooth: Boolean,
    val hasBluetoothLowEnergy: Boolean,
) {
    val isSupported: Boolean get() = hasBluetooth && hasBluetoothLowEnergy

    companion object {
        val Unsupported = BleCapability(hasBluetooth = false, hasBluetoothLowEnergy = false)
    }
}

/** Runtime permission state for the permissions BLE scanning needs on this API level. */
enum class BlePermissionState {
    UNKNOWN,
    GRANTED,
    DENIED,

    /** Denied with "don't ask again" — the app must send the user to system settings instead. */
    PERMANENTLY_DENIED,
    ;

    val isGranted: Boolean get() = this == GRANTED
}
