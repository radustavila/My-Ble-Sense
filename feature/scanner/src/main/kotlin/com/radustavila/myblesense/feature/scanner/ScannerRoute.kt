package com.radustavila.myblesense.feature.scanner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.myblesense.core.bluetooth.permission.BlePermissions
import com.radustavila.myblesense.feature.scanner.permission.hasBlePermissions
import com.radustavila.myblesense.feature.scanner.permission.openAppSettings
import com.radustavila.myblesense.feature.scanner.permission.rememberBlePermissionLauncher
import com.radustavila.myblesense.feature.scanner.permission.rememberEnableBluetoothIntent
import com.radustavila.myblesense.feature.scanner.permission.rememberEnableBluetoothLauncher

/**
 * Stateful entry point of the scanner feature: wires the ViewModel, the permission/enable-Bluetooth
 * launchers and the screen lifecycle.
 *
 * State is collected with [collectAsStateWithLifecycle] and the scan is stopped in `ON_STOP`, so
 * no scan survives the screen going to the background.
 */
@Composable
fun ScannerRoute(
    onDeviceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val permissionLauncher = rememberBlePermissionLauncher { granted, shouldShowRationale ->
        viewModel.onEvent(ScannerEvent.PermissionRequestCompleted(granted, shouldShowRationale))
    }
    val enableBluetoothIntent = rememberEnableBluetoothIntent()
    val enableBluetoothLauncher = rememberEnableBluetoothLauncher { /* adapter state flow reacts */ }

    // Permissions can be revoked while the app is in the background, so they are re-checked on
    // every return to the foreground instead of only once at startup.
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.onEvent(ScannerEvent.PermissionsChecked(context.hasBlePermissions()))
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.onEvent(ScannerEvent.StopScan)
    }

    ScannerScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onDeviceClick = onDeviceClick,
        onRequestPermissions = {
            permissionLauncher.launch(BlePermissions.required.toTypedArray())
        },
        onEnableBluetooth = { enableBluetoothLauncher.launch(enableBluetoothIntent) },
        onOpenSettings = context::openAppSettings,
        modifier = modifier,
    )
}
