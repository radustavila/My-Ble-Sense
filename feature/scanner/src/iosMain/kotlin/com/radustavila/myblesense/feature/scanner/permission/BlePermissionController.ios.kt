package com.radustavila.myblesense.feature.scanner.permission

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerOptionShowPowerAlertKey
import platform.CoreBluetooth.CBManager
import platform.CoreBluetooth.CBManagerAuthorizationDenied
import platform.CoreBluetooth.CBManagerAuthorizationRestricted
import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

/**
 * iOS has one Bluetooth authorization, and the system asks for it by itself the first time the
 * app creates its `CBCentralManager`. An undetermined status therefore counts as "allowed": the
 * prompt is about to appear and the adapter state reports the answer. Once denied, the app cannot
 * ask again — only the Settings app can change it, which is what a permanent denial means.
 */
@Composable
actual fun rememberBlePermissionController(
    onPermissionResult: (granted: Boolean, shouldShowRationale: Boolean) -> Unit,
): BlePermissionController {
    val onResult = rememberUpdatedState(onPermissionResult)
    return remember {
        object : BlePermissionController {
            override fun hasPermissions(): Boolean = when (CBManager.authorization) {
                CBManagerAuthorizationDenied, CBManagerAuthorizationRestricted -> false
                else -> true
            }

            override fun requestPermissions() =
                onResult.value(hasPermissions(), false)

            // Kept so the manager outlives the call; CoreBluetooth drops the alert otherwise.
            private var powerAlertManager: CBCentralManager? = null

            // An app cannot switch Bluetooth on, nor open the system Bluetooth settings (the
            // app's own settings page only holds the per-app permission). What it can do is
            // make iOS show its "turn on Bluetooth" alert, whose Settings button leads there:
            // the alert appears whenever a central manager is created while Bluetooth is off.
            override fun requestEnableBluetooth() {
                powerAlertManager = CBCentralManager(
                    delegate = null,
                    queue = null,
                    options = mapOf<Any?, Any>(CBCentralManagerOptionShowPowerAlertKey to true),
                )
            }

            override fun openAppSettings() {
                val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
                UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
            }
        }
    }
}

// The Control Center button does not switch Bluetooth off: it only suspends connections, and the
// switch in Settings stays on. Apps see that as "off", so the way back has to be spelled out.
internal actual val bluetoothDisabledHint: String =
    "Turn Bluetooth on to scan for nearby devices: tap the Bluetooth button in Control Center, " +
        "or switch Bluetooth off and on again in Settings."
