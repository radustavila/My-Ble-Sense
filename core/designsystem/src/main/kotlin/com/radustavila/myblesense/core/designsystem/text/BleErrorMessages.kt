package com.radustavila.myblesense.core.designsystem.text

import com.radustavila.myblesense.domain.model.BleError

/**
 * Single mapping from domain errors to user facing copy, shared by every feature.
 *
 * Messages say what happened and what the user can do about it; GATT status codes and Android
 * exceptions stay in the logs, where they are useful, instead of in the UI, where they are not.
 */
fun BleError.toUserMessage(): String = when (this) {
    BleError.BleUnsupported -> "Bluetooth LE is not supported on this device."
    BleError.BluetoothDisabled -> "Bluetooth is disabled."
    BleError.BluetoothTurnedOffDuringScan -> "Bluetooth was turned off, scan stopped."
    BleError.PermissionDenied -> "Nearby devices permission is required."
    BleError.ScannerUnavailable -> "The Bluetooth scanner is currently unavailable."
    BleError.ScanAlreadyStarted -> "A scan is already running."
    BleError.ScanThrottled -> "Too many scans were started. Please wait a moment and retry."
    is BleError.ScanFailed -> reason?.let { "Scan failed: $it" } ?: "Scan failed (code $code)."

    BleError.DeviceNotFound -> "This device is no longer reachable."
    is BleError.ConnectionFailed -> "Could not connect to the device."
    BleError.ConnectionTimeout -> "The device did not answer in time. It may be out of range."
    BleError.Disconnected -> "The device disconnected."
    BleError.NotConnected -> "Not connected to a device."
    BleError.ServiceDiscoveryFailed -> "Could not discover the device's services."
    BleError.CharacteristicNotFound -> "This characteristic is no longer available."
    BleError.CharacteristicOperationUnsupported ->
        "The device does not allow this operation on this characteristic."
    BleError.DescriptorNotFound ->
        "This characteristic has no notification descriptor, so it cannot be subscribed to."
    is BleError.CharacteristicReadFailed -> "Read failed."
    is BleError.CharacteristicWriteFailed -> "Write failed."
    is BleError.NotificationEnableFailed -> "Could not change the notification setting."
    BleError.GattOperationTimeout -> "The device did not answer in time."
    BleError.GattOperationRejected -> "The Bluetooth stack rejected the operation. Try again."
    is BleError.UnknownGattError -> "The device reported a Bluetooth error."

    BleError.KnownDeviceNotForgotten -> "Could not forget the device. Try again."

    is BleError.Unknown -> "Unexpected Bluetooth error."
}
