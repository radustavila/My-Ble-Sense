package com.radustavila.myblesense.feature.scanner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.radustavila.myblesense.core.designsystem.component.StatusMessage
import com.radustavila.myblesense.core.designsystem.text.toUserMessage
import com.radustavila.myblesense.domain.model.BlePermissionState
import com.radustavila.myblesense.domain.model.DeviceSortOrder

/**
 * Stateless scanner screen: it renders [ScannerUiState] and forwards [ScannerEvent]s. Every
 * decision (what to scan, how to sort, which error to show) has already been made upstream.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScannerScreen(
    state: ScannerUiState,
    onEvent: (ScannerEvent) -> Unit,
    onDeviceClick: (String) -> Unit,
    onRequestPermissions: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    // A LazyColumn with stable keys anchors the viewport to whatever row was first visible, so a
    // re-sort drags the list along with that row. Ordering is meaningless across sort orders, so
    // the list goes back to the top instead - the initial order is skipped to keep a restored
    // scroll position after navigating back.
    var appliedSortOrder by remember { mutableStateOf(state.sortOrder) }
    LaunchedEffect(state.sortOrder) {
        if (state.sortOrder != appliedSortOrder) {
            appliedSortOrder = state.sortOrder
            listState.scrollToItem(0)
        }
    }

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(error.toUserMessage())
        onEvent(ScannerEvent.DismissError)
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("MyBleSense") },
                actions = {
                    SortMenu(
                        selected = state.sortOrder,
                        onSelected = { onEvent(ScannerEvent.SortOrderSelected(it)) },
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            state.toKnownDeviceUi()?.let { knownDevice ->
                KnownDeviceCard(
                    device = knownDevice,
                    onOpen = onDeviceClick,
                    onConnect = { onEvent(ScannerEvent.ConnectKnownDevice) },
                    onDisconnect = { onEvent(ScannerEvent.DisconnectKnownDevice) },
                    onForget = { onEvent(ScannerEvent.ForgetKnownDevice) },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
                )
            }
            ScanControls(
                state = state,
                onStart = { onEvent(ScannerEvent.StartScan) },
                onStop = { onEvent(ScannerEvent.StopScan) },
            )
            if (state.isScanning) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            HorizontalDivider()

            when (state.content) {
                ScannerContent.BleUnsupported -> StatusMessage(
                    icon = Icons.Default.BluetoothDisabled,
                    title = "Bluetooth LE is not supported on this device.",
                    description = "This app needs Bluetooth Low Energy hardware to scan for " +
                        "nearby devices.",
                )

                ScannerContent.PermissionRequired -> PermissionMessage(
                    permissionState = state.permissionState,
                    onRequestPermissions = onRequestPermissions,
                    onOpenSettings = onOpenSettings,
                )

                ScannerContent.BluetoothDisabled -> StatusMessage(
                    icon = Icons.Default.BluetoothDisabled,
                    title = "Bluetooth is disabled.",
                    description = "Turn Bluetooth on to scan for nearby devices.",
                    primaryActionLabel = "Enable Bluetooth",
                    onPrimaryAction = onEnableBluetooth,
                )

                ScannerContent.Scanning -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator()
                        Text("Scanning...", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                ScannerContent.Empty -> StatusMessage(
                    icon = Icons.AutoMirrored.Filled.BluetoothSearching,
                    title = when {
                        state.hasScannedOnce && state.isKnownDeviceSeen -> "No other devices found"
                        state.hasScannedOnce -> "No devices found"
                        else -> "Ready to scan"
                    },
                    description = when {
                        state.hasScannedOnce && state.isKnownDeviceSeen ->
                            "Only the known device advertised nearby during the last scan."
                        state.hasScannedOnce ->
                            "No BLE peripherals advertised nearby during the last scan."
                        else -> "Start a scan to discover nearby Bluetooth LE devices."
                    },
                )

                ScannerContent.Devices -> DeviceList(
                    devices = state.otherDevices.map { it.toListItemUi() },
                    onDeviceClick = onDeviceClick,
                    listState = listState,
                )
            }
        }
    }
}

@Composable
private fun ScanControls(
    state: ScannerUiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Button(
            onClick = if (state.isScanning) onStop else onStart,
            enabled = state.isScanning || state.canScan,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isScanning) "Stop scan" else "Start scan")
        }
        Text(
            text = when {
                state.isScanning -> "Scanning... ${deviceCountLabel(state.deviceCount)} found"
                state.deviceCount > 0 -> "${deviceCountLabel(state.deviceCount)} found"
                else -> "Idle"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DeviceList(
    devices: List<DeviceListItemUi>,
    onDeviceClick: (String) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    LazyColumn(state = listState, modifier = modifier.fillMaxSize()) {
        // Stable keys: a re-sorted or updated device moves, it is never recreated.
        items(items = devices, key = { it.id }) { device ->
            DeviceListItem(device = device, onClick = onDeviceClick)
            HorizontalDivider()
        }
    }
}

@Composable
private fun PermissionMessage(
    permissionState: BlePermissionState,
    onRequestPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val permanentlyDenied = permissionState == BlePermissionState.PERMANENTLY_DENIED
    StatusMessage(
        icon = if (permanentlyDenied) Icons.Default.Lock else Icons.AutoMirrored.Filled.BluetoothSearching,
        title = if (permanentlyDenied) {
            "Permission permanently denied"
        } else {
            "Nearby devices permission required"
        },
        description = if (permanentlyDenied) {
            "Scanning needs the nearby devices permission. Grant it in the system settings to " +
                "continue."
        } else {
            "MyBleSense needs permission to detect nearby Bluetooth devices. Location is " +
                "never derived from the scan results."
        },
        primaryActionLabel = if (permanentlyDenied) "Open settings" else "Grant permission",
        onPrimaryAction = if (permanentlyDenied) onOpenSettings else onRequestPermissions,
    )
}

@Composable
private fun SortMenu(
    selected: DeviceSortOrder,
    onSelected: (DeviceSortOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort devices")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DeviceSortOrder.entries.forEach { order ->
                DropdownMenuItem(
                    text = { Text(order.label()) },
                    trailingIcon = if (order == selected) {
                        { Icon(Icons.Default.Check, contentDescription = "Selected") }
                    } else {
                        null
                    },
                    onClick = {
                        expanded = false
                        onSelected(order)
                    },
                )
            }
        }
    }
}

private fun deviceCountLabel(count: Int): String =
    if (count == 1) "1 device" else "$count devices"

private fun DeviceSortOrder.label(): String = when (this) {
    DeviceSortOrder.SIGNAL_STRENGTH -> "Signal strength"
    DeviceSortOrder.NAME -> "Name"
    DeviceSortOrder.FIRST_SEEN -> "Discovery order"
}
