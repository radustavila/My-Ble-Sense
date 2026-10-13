package com.radustavila.myblesense.feature.scanner.permission

import androidx.compose.runtime.Composable

/**
 * The platform side of getting Bluetooth usable: permissions, the adapter, the system settings.
 *
 * Android has runtime permissions and an "enable Bluetooth" dialog; iOS has a single
 * authorization prompt the system shows on its own, and can only be sent to the app's settings.
 */
interface BlePermissionController {
    /** Whether the app may scan and connect right now. */
    fun hasPermissions(): Boolean

    /** Asks for the missing permissions; the outcome arrives through `onPermissionResult`. */
    fun requestPermissions()

    /** Asks the user to turn Bluetooth on. The adapter state flow reports the outcome. */
    fun requestEnableBluetooth()

    /** Opens this app's page in the system settings, the only path out of a permanent denial. */
    fun openAppSettings()
}

/**
 * [onPermissionResult] reports whether everything was granted and whether the system would still
 * show a rationale — `false` after a denial means "don't ask again", which is how the app knows
 * to send the user to the system settings instead of re-prompting forever.
 */
@Composable
expect fun rememberBlePermissionController(
    onPermissionResult: (granted: Boolean, shouldShowRationale: Boolean) -> Unit,
): BlePermissionController

/** What the user has to do while Bluetooth is off; the way to turn it on differs per platform. */
internal expect val bluetoothDisabledHint: String
