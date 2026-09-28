package com.radustavila.bleexplorer.domain.model

/**
 * Application level BLE failures. Infrastructure exceptions (SecurityException, scan callback
 * error codes, ...) are mapped onto this type before they reach the presentation layer.
 */
sealed interface BleError {
    /** The device has no Bluetooth LE hardware; nothing the user can do. */
    data object BleUnsupported : BleError

    /** Bluetooth is off. Recoverable by asking the user to turn it on. */
    data object BluetoothDisabled : BleError

    /** Required runtime permissions are missing or were revoked. */
    data object PermissionDenied : BleError

    /** The system scanner could not be obtained (adapter off or in a transient state). */
    data object ScannerUnavailable : BleError

    /** A scan was already running for this app. */
    data object ScanAlreadyStarted : BleError

    /** Too many scans were started in a short window; Android throttles the app. */
    data object ScanThrottled : BleError

    /** The platform rejected or aborted the scan. [code] is the raw Android error code. */
    data class ScanFailed(val code: Int, val reason: String? = null) : BleError

    /** Bluetooth was turned off (or the adapter reset) while a scan was running. */
    data object BluetoothTurnedOffDuringScan : BleError

    // --- GATT (Phase 2) -------------------------------------------------------------------

    /** The address is not known to the adapter, or is not a valid hardware address. */
    data object DeviceNotFound : BleError

    /** The peripheral refused the connection or the link dropped during setup. */
    data class ConnectionFailed(val status: Int? = null) : BleError

    /** The peripheral never reported a connection within the timeout (out of range, asleep). */
    data object ConnectionTimeout : BleError

    /** The link went away, either on request or unexpectedly (out of range, peripheral reset). */
    data object Disconnected : BleError

    /** An operation was requested while no device is connected. */
    data object NotConnected : BleError

    /** `discoverServices()` failed or reported a non-success status. */
    data object ServiceDiscoveryFailed : BleError

    /** The requested service/characteristic is not part of the connected device's GATT database. */
    data object CharacteristicNotFound : BleError

    /** The characteristic exists but does not declare the property the action requires. */
    data object CharacteristicOperationUnsupported : BleError

    /** The characteristic has no Client Characteristic Configuration Descriptor. */
    data object DescriptorNotFound : BleError

    data class CharacteristicReadFailed(val status: Int? = null) : BleError

    data class CharacteristicWriteFailed(val status: Int? = null) : BleError

    data class NotificationEnableFailed(val status: Int? = null) : BleError

    /** The platform never invoked the callback of a GATT operation in time. */
    data object GattOperationTimeout : BleError

    /** The platform rejected the operation outright (busy stack, invalid state). */
    data object GattOperationRejected : BleError

    /** A GATT status code with no more specific meaning for the user. */
    data class UnknownGattError(val status: Int) : BleError

    /** Anything unexpected. The original message is kept for logs only. */
    data class Unknown(val message: String? = null) : BleError
}

/** Carrier used to surface a [BleError] through cold flows. */
class BleException(
    val error: BleError,
    cause: Throwable? = null,
) : Exception(error.toString(), cause)

fun Throwable.toBleError(): BleError = when (this) {
    is BleException -> error
    is SecurityException -> BleError.PermissionDenied
    else -> BleError.Unknown(message)
}
