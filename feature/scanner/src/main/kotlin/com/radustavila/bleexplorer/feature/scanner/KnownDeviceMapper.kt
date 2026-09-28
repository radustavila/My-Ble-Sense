package com.radustavila.bleexplorer.feature.scanner

import androidx.compose.runtime.Immutable
import com.radustavila.bleexplorer.core.designsystem.component.SignalLevel
import com.radustavila.bleexplorer.core.designsystem.text.toUserMessage
import com.radustavila.bleexplorer.domain.model.BlePermissionState
import com.radustavila.bleexplorer.domain.model.BluetoothState
import com.radustavila.bleexplorer.domain.model.GattConnectionState

/**
 * What the device list says about the known device.
 *
 * The connection part comes from the GATT client's actual state; availability from the current
 * scan (seen → [AVAILABLE]) or from the last attempt to reach it (failed, or a finished scan that
 * did not see it → [UNAVAILABLE]). [DISCONNECTED] means known but not checked yet this session.
 */
enum class KnownDeviceStatus {
    CONNECTED,

    /** Opening the link or discovering services. */
    CONNECTING,
    DISCONNECTING,
    AVAILABLE,
    UNAVAILABLE,
    DISCONNECTED,
    BLUETOOTH_OFF,
    PERMISSION_REQUIRED,
}

/** Display-ready known device entry: the Composable only renders it. */
@Immutable
data class KnownDeviceUi(
    val id: String,
    val name: String,
    val status: KnownDeviceStatus,
    val statusLabel: String,
    /** Why the device is not reachable, or what to do about it; `null` when all is well. */
    val hint: String?,
    /** Signal of the latest advertisement, while the current scan sees the device. */
    val rssi: Int? = null,
    val signalLevel: SignalLevel? = null,
) {
    /** Offered in every idle state, also "unavailable": out of range may no longer be true. */
    val canConnect: Boolean
        get() = status == KnownDeviceStatus.AVAILABLE ||
            status == KnownDeviceStatus.UNAVAILABLE ||
            status == KnownDeviceStatus.DISCONNECTED

    val canDisconnect: Boolean get() = status == KnownDeviceStatus.CONNECTED

    /** A connection attempt to an out of range device takes a while; it can be abandoned. */
    val canCancel: Boolean get() = status == KnownDeviceStatus.CONNECTING

    val isBusy: Boolean
        get() = status == KnownDeviceStatus.CONNECTING || status == KnownDeviceStatus.DISCONNECTING
}

/**
 * Maps the known device, merged with what the current scan reports for it, to its entry. `null`
 * when there is no known device, or when the phone has no BLE at all and it could never be used.
 */
internal fun ScannerUiState.toKnownDeviceUi(): KnownDeviceUi? {
    val known = knownDevice ?: return null
    if (!capability.isSupported) return null

    val scanned = devices.firstOrNull { it.id == known.id }
    val status = knownDeviceStatus(deviceId = known.id, isSeen = scanned != null)
    return KnownDeviceUi(
        id = known.id,
        name = scanned?.displayName ?: known.name ?: UNKNOWN_DEVICE,
        status = status,
        statusLabel = status.label(),
        hint = knownDeviceHint(status),
        rssi = scanned?.rssi,
        signalLevel = scanned?.signalStrength?.toUiLevel(),
    )
}

private fun ScannerUiState.knownDeviceStatus(deviceId: String, isSeen: Boolean): KnownDeviceStatus {
    val isThisDevice = connection.deviceId == deviceId
    return when {
        // The link wins over everything: it is the actual state, wherever it was opened.
        isThisDevice && connection.state == GattConnectionState.CONNECTED &&
            connection.servicesDiscovered -> KnownDeviceStatus.CONNECTED

        isThisDevice && (
            connection.state == GattConnectionState.CONNECTED ||
                connection.state == GattConnectionState.CONNECTING
            ) -> KnownDeviceStatus.CONNECTING

        isThisDevice && connection.state == GattConnectionState.DISCONNECTING ->
            KnownDeviceStatus.DISCONNECTING

        // Bridges the instant between the request and the client reporting it.
        isKnownDeviceConnectRequested -> KnownDeviceStatus.CONNECTING

        // UNKNOWN (not checked yet) blocks nothing; the connect call reports it if it has to.
        permissionState == BlePermissionState.DENIED ||
            permissionState == BlePermissionState.PERMANENTLY_DENIED ->
            KnownDeviceStatus.PERMISSION_REQUIRED

        bluetoothState == BluetoothState.DISABLED ||
            bluetoothState == BluetoothState.TURNING_OFF -> KnownDeviceStatus.BLUETOOTH_OFF

        isSeen -> KnownDeviceStatus.AVAILABLE
        knownDeviceProblem != null -> KnownDeviceStatus.UNAVAILABLE
        hasScannedOnce && !isScanning -> KnownDeviceStatus.UNAVAILABLE
        else -> KnownDeviceStatus.DISCONNECTED
    }
}

private fun ScannerUiState.knownDeviceHint(status: KnownDeviceStatus): String? = when (status) {
    KnownDeviceStatus.CONNECTED,
    KnownDeviceStatus.CONNECTING,
    KnownDeviceStatus.DISCONNECTING,
    KnownDeviceStatus.AVAILABLE,
    -> null

    KnownDeviceStatus.UNAVAILABLE ->
        knownDeviceProblem?.toUserMessage() ?: NOT_FOUND_IN_LAST_SCAN

    KnownDeviceStatus.DISCONNECTED ->
        if (isScanning) "Looking for it nearby…" else NOT_CHECKED_YET

    KnownDeviceStatus.BLUETOOTH_OFF -> "Turn Bluetooth on to connect."
    KnownDeviceStatus.PERMISSION_REQUIRED -> "Grant the nearby devices permission to connect."
}

private fun KnownDeviceStatus.label(): String = when (this) {
    KnownDeviceStatus.CONNECTED -> "Connected"
    KnownDeviceStatus.CONNECTING -> "Connecting…"
    KnownDeviceStatus.DISCONNECTING -> "Disconnecting…"
    KnownDeviceStatus.AVAILABLE -> "Available"
    KnownDeviceStatus.UNAVAILABLE -> "Unavailable"
    KnownDeviceStatus.DISCONNECTED -> "Disconnected"
    KnownDeviceStatus.BLUETOOTH_OFF -> "Bluetooth is off"
    KnownDeviceStatus.PERMISSION_REQUIRED -> "Permission required"
}

internal const val NOT_FOUND_IN_LAST_SCAN =
    "Not found in the last scan. It may be out of range or switched off."

internal const val NOT_CHECKED_YET =
    "Not seen yet. Connect directly, or scan to check that it is nearby."
