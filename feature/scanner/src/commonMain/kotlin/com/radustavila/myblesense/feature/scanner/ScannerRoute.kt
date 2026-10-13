package com.radustavila.myblesense.feature.scanner

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.radustavila.myblesense.feature.scanner.permission.rememberBlePermissionController
import org.koin.compose.viewmodel.koinViewModel

/**
 * Stateful entry point of the scanner feature: wires the ViewModel, the platform permission
 * controller and the screen lifecycle.
 *
 * State is collected with [collectAsStateWithLifecycle] and the scan is stopped in `ON_STOP`, so
 * no scan survives the screen going to the background.
 */
@Composable
fun ScannerRoute(
    onDeviceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScannerViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val permissions = rememberBlePermissionController { granted, shouldShowRationale ->
        viewModel.onEvent(ScannerEvent.PermissionRequestCompleted(granted, shouldShowRationale))
    }

    // Permissions can be revoked while the app is in the background, so they are re-checked on
    // every return to the foreground instead of only once at startup.
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        viewModel.onEvent(ScannerEvent.PermissionsChecked(permissions.hasPermissions()))
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.onEvent(ScannerEvent.StopScan)
    }

    ScannerScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onDeviceClick = onDeviceClick,
        onRequestPermissions = permissions::requestPermissions,
        onEnableBluetooth = permissions::requestEnableBluetooth,
        onOpenSettings = permissions::openAppSettings,
        modifier = modifier,
    )
}
