package com.radustavila.myblesense.feature.scanner

import androidx.compose.runtime.Immutable
import com.radustavila.myblesense.domain.model.BleCapability
import com.radustavila.myblesense.domain.model.BleDevice
import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.BlePermissionState
import com.radustavila.myblesense.domain.model.BluetoothState
import com.radustavila.myblesense.domain.model.DeviceSortOrder
import com.radustavila.myblesense.domain.model.GattConnection
import com.radustavila.myblesense.domain.model.KnownDevice

/** Single immutable snapshot the scanner screen renders; there is no other UI state anywhere. */
@Immutable
data class ScannerUiState(
    val devices: List<BleDevice> = emptyList(),
    val isScanning: Boolean = false,
    val hasScannedOnce: Boolean = false,
    val capability: BleCapability = BleCapability(hasBluetooth = true, hasBluetoothLowEnergy = true),
    val bluetoothState: BluetoothState = BluetoothState.UNKNOWN,
    val permissionState: BlePermissionState = BlePermissionState.UNKNOWN,
    val sortOrder: DeviceSortOrder = DeviceSortOrder.SIGNAL_STRENGTH,
    val error: BleError? = null,
    /** The device remembered from an earlier connection; `null` until there is one. */
    val knownDevice: KnownDevice? = null,
    /** The app's single GATT connection, whichever device and screen it belongs to. */
    val connection: GattConnection = GattConnection.Disconnected,
    /** A connection to [knownDevice] was started here and has not completed yet. */
    val isKnownDeviceConnectRequested: Boolean = false,
    /** Why the last attempt to connect to [knownDevice] from here failed; cleared by the next. */
    val knownDeviceProblem: BleError? = null,
    /** "Forget device" is in progress: closing the link, then removing the stored device. */
    val isForgettingKnownDevice: Boolean = false,
) {
    val deviceCount: Int get() = devices.size

    /** Supported, switched on and permitted: scanning and connecting can both start. */
    val isBluetoothReady: Boolean
        get() = capability.isSupported && bluetoothState.isEnabled && permissionState.isGranted

    val canScan: Boolean get() = isBluetoothReady

    /**
     * Scan results without the known device: a scan that finds it updates the known device entry
     * above the list instead of listing it a second time.
     */
    val otherDevices: List<BleDevice>
        get() = knownDevice?.let { known -> devices.filterNot { it.id == known.id } } ?: devices

    /** The current scan has seen the known device. */
    val isKnownDeviceSeen: Boolean
        get() = knownDevice?.let { known -> devices.any { it.id == known.id } } == true

    /** Which of the mutually exclusive "not scanning yet" screens to show. */
    val content: ScannerContent
        get() = when {
            !capability.isSupported -> ScannerContent.BleUnsupported
            !permissionState.isGranted -> ScannerContent.PermissionRequired
            !bluetoothState.isEnabled -> ScannerContent.BluetoothDisabled
            otherDevices.isNotEmpty() -> ScannerContent.Devices
            isScanning -> ScannerContent.Scanning
            else -> ScannerContent.Empty
        }
}

enum class ScannerContent {
    BleUnsupported,
    PermissionRequired,
    BluetoothDisabled,
    Scanning,
    Devices,
    Empty,
}

/** Everything the user can do on the scanner screen. */
sealed interface ScannerEvent {
    data object StartScan : ScannerEvent
    data object StopScan : ScannerEvent
    data object DismissError : ScannerEvent
    data class SortOrderSelected(val order: DeviceSortOrder) : ScannerEvent

    /** Connect to the known device, whether or not the current scan has seen it. */
    data object ConnectKnownDevice : ScannerEvent

    /** Close the link to the known device, or cancel the attempt in progress. */
    data object DisconnectKnownDevice : ScannerEvent

    /**
     * Stop remembering the known device: close any link to it and remove it from storage. The
     * system's Bluetooth pairing is left alone; a later scan lists the device again.
     */
    data object ForgetKnownDevice : ScannerEvent

    /** Permissions re-checked on every return to the foreground; they can be revoked there. */
    data class PermissionsChecked(val granted: Boolean) : ScannerEvent

    /**
     * Result of an actual permission request. [shouldShowRationale] is only meaningful here: on a
     * denial it tells "don't ask again" (false) apart from a plain denial (true).
     */
    data class PermissionRequestCompleted(
        val granted: Boolean,
        val shouldShowRationale: Boolean,
    ) : ScannerEvent
}
