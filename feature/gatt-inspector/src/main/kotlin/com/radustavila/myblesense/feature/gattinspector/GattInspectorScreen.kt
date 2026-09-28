package com.radustavila.myblesense.feature.gattinspector

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radustavila.myblesense.core.designsystem.component.LabeledValue
import com.radustavila.myblesense.core.designsystem.component.StatusMessage
import com.radustavila.myblesense.core.designsystem.theme.MonospaceValueStyle
import com.radustavila.myblesense.domain.model.GattConnectionState

/**
 * Stateless GATT inspector: services, their characteristics, and the actions each characteristic
 * actually declares support for. Disabled actions are never shown as available.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GattInspectorScreen(
    state: GattInspectorUiState,
    onEvent: (GattInspectorEvent) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message.text)
        onEvent(GattInspectorEvent.DismissMessage)
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.deviceName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.isConnected) {
                        IconButton(onClick = { onEvent(GattInspectorEvent.Disconnect) }) {
                            Icon(Icons.Default.LinkOff, contentDescription = "Disconnect")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            ConnectionBanner(state.connectionState)

            when {
                !state.isConnected -> StatusMessage(
                    icon = Icons.Default.BluetoothDisabled,
                    title = "Not connected",
                    description = "The connection to this device is gone. Go back to the device " +
                        "details screen to connect again.",
                    primaryActionLabel = "Back",
                    onPrimaryAction = onBackClick,
                )

                state.isDiscoveringServices -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Text(
                            text = "Discovering services…",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }

                state.hasNoServices -> StatusMessage(
                    icon = Icons.Default.SearchOff,
                    title = "No services",
                    description = "This device did not expose any GATT service. Some peripherals " +
                        "only allow a bonded or vendor application to see their services.",
                )

                else -> ServiceList(services = state.services, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun ConnectionBanner(connectionState: GattConnectionState, modifier: Modifier = Modifier) {
    val (label, color) = when (connectionState) {
        GattConnectionState.CONNECTED -> "Connected" to MaterialTheme.colorScheme.primary
        GattConnectionState.CONNECTING -> "Connecting…" to MaterialTheme.colorScheme.tertiary
        GattConnectionState.DISCONNECTING -> "Disconnecting…" to MaterialTheme.colorScheme.tertiary
        GattConnectionState.DISCONNECTED -> "Disconnected" to MaterialTheme.colorScheme.error
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Text(text = label, style = MaterialTheme.typography.titleSmall, color = color)
    }
}

@Composable
private fun ServiceList(
    services: List<GattServiceUi>,
    onEvent: (GattInspectorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items = services, key = { it.uuid }) { service ->
            ServiceCard(service = service, onEvent = onEvent)
        }
    }
}

@Composable
private fun ServiceCard(
    service: GattServiceUi,
    onEvent: (GattInspectorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEvent(GattInspectorEvent.ToggleService(service.uuid)) }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = service.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = service.shortUuid,
                    style = MonospaceValueStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${service.characteristicCount} characteristic(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (service.isExpanded) {
                    Icons.Default.ExpandLess
                } else {
                    Icons.Default.ExpandMore
                },
                contentDescription = if (service.isExpanded) "Collapse service" else "Expand service",
            )
        }

        if (service.isExpanded) {
            if (service.characteristics.isEmpty()) {
                Text(
                    text = "This service exposes no characteristics.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            service.characteristics.forEach { characteristic ->
                HorizontalDivider()
                CharacteristicItem(characteristic = characteristic, onEvent = onEvent)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CharacteristicItem(
    characteristic: GattCharacteristicUi,
    onEvent: (GattInspectorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Only the header toggles the row, so tapping the write field cannot collapse it.
                .clickable {
                    onEvent(GattInspectorEvent.ToggleCharacteristic(characteristic.id))
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = characteristic.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = characteristic.uuid,
                    style = MonospaceValueStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = if (characteristic.isExpanded) {
                    Icons.Default.ExpandLess
                } else {
                    Icons.Default.ExpandMore
                },
                contentDescription = if (characteristic.isExpanded) {
                    "Collapse characteristic"
                } else {
                    "Expand characteristic"
                },
            )
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            characteristic.properties.forEach { property ->
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(property, style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }
        }

        characteristic.latestValue?.let { value -> ValueBlock(value) }

        if (characteristic.isExpanded) {
            CharacteristicActions(characteristic = characteristic, onEvent = onEvent)
        } else if (!characteristic.hasActions) {
            Text(
                text = "No readable, writable or notifying property.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ValueBlock(value: CharacteristicValueUi, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        LabeledValue(
            label = if (value.fromNotification) "Latest value (notified)" else "Latest value (read)",
            value = value.hex,
            valueStyle = MonospaceValueStyle,
        )
        Text(
            text = "${value.byteCount} byte(s) · ${value.timestamp}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        value.decoded?.let {
            Text(
                text = "Decoded: $it",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        value.utf8?.let {
            Text(
                // Made explicit: arbitrary binary data can decode into text that means nothing.
                text = "UTF-8 interpretation: $it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CharacteristicActions(
    characteristic: GattCharacteristicUi,
    onEvent: (GattInspectorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (characteristic.canRead) {
            OutlinedButton(
                onClick = { onEvent(GattInspectorEvent.Read(characteristic.id)) },
                enabled = !characteristic.isBusy,
            ) {
                if (characteristic.operation == CharacteristicOperation.READING) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(text = "Reading…", modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text("Read")
                }
            }
        }

        if (characteristic.canWrite) {
            OutlinedTextField(
                value = characteristic.writeInput,
                onValueChange = {
                    onEvent(GattInspectorEvent.WriteInputChanged(characteristic.id, it))
                },
                label = { Text("Value (HEX)") },
                placeholder = { Text("01 FF 2A") },
                singleLine = true,
                isError = characteristic.writeError != null,
                supportingText = characteristic.writeError?.let { { Text(it) } },
                textStyle = MonospaceValueStyle,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = { onEvent(GattInspectorEvent.Write(characteristic.id)) },
                enabled = !characteristic.isBusy && characteristic.writeInput.isNotBlank(),
            ) {
                Text(
                    if (characteristic.operation == CharacteristicOperation.WRITING) {
                        "Writing…"
                    } else {
                        characteristic.writeLabel
                    },
                )
            }
        }

        if (characteristic.supportsSubscription) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Switch(
                    checked = characteristic.isNotifying,
                    onCheckedChange = { enabled ->
                        onEvent(GattInspectorEvent.SetNotifications(characteristic.id, enabled))
                    },
                    enabled = !characteristic.isBusy,
                )
                Text(
                    text = if (characteristic.isNotifying) {
                        "Notifications ON"
                    } else {
                        "Notifications OFF"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (characteristic.notifications.isNotEmpty()) {
                Text(
                    text = "Incoming values",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                characteristic.notifications.forEach { notification ->
                    Text(
                        text = buildString {
                            append(notification.timestamp)
                            append("  ")
                            append(notification.hex)
                            notification.decoded?.let { append("   ($it)") }
                        },
                        style = MonospaceValueStyle,
                    )
                }
            }
        }
    }
}
