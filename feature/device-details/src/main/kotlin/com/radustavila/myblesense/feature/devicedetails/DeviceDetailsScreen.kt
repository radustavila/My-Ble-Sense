package com.radustavila.myblesense.feature.devicedetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import com.radustavila.myblesense.domain.model.PocTouchEvents
import com.radustavila.myblesense.core.designsystem.component.LabeledValue
import com.radustavila.myblesense.core.designsystem.component.SectionHeader
import com.radustavila.myblesense.core.designsystem.component.StatusMessage
import com.radustavila.myblesense.core.designsystem.theme.MonospaceValueStyle

private const val NOT_AVAILABLE = "Not advertised"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DeviceDetailsScreen(
    state: DeviceDetailsUiState,
    onEvent: (DeviceDetailsEvent) -> Unit,
    onBackClick: () -> Unit,
    onOpenGattInspector: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.connection.message) {
        val message = state.connection.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        onEvent(DeviceDetailsEvent.DismissMessage)
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(state.device?.name ?: "Device details") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.device == null -> StatusMessage(
                    icon = Icons.Default.SearchOff,
                    title = "Device no longer available",
                    description = "This device is not part of the current scan session anymore. " +
                        "Start a new scan to find it again.",
                    primaryActionLabel = "Back to scanner",
                    onPrimaryAction = onBackClick,
                )

                else -> DeviceDetailsContent(
                    device = state.device,
                    connection = state.connection,
                    poc = state.poc,
                    hardwareEvents = state.hardwareEvents.takeIf { state.showsHardwareEvents },
                    onEvent = onEvent,
                    onOpenGattInspector = { onOpenGattInspector(state.device.id) },
                )
            }
        }
    }
}

@Composable
private fun DeviceDetailsContent(
    device: DeviceDetailsUi,
    connection: ConnectionUiState,
    poc: PocServiceUiState?,
    hardwareEvents: HardwareEventsUiState?,
    onEvent: (DeviceDetailsEvent) -> Unit,
    onOpenGattInspector: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ConnectionCard(
            connection = connection,
            onConnect = { onEvent(DeviceDetailsEvent.Connect) },
            onDisconnect = { onEvent(DeviceDetailsEvent.Disconnect) },
            onOpenGattInspector = onOpenGattInspector,
        )

        if (poc != null) {
            PocServiceCard(
                poc = poc,
                onReadStatus = { onEvent(DeviceDetailsEvent.ReadPocStatus) },
                onSendPing = { onEvent(DeviceDetailsEvent.SendPocPing) },
            )
        }
        if (hardwareEvents != null) {
            HardwareEventsCard(
                events = hardwareEvents,
                poc = poc,
                onSubscribe = { onEvent(DeviceDetailsEvent.SubscribePocEvents) },
            )
        }

        LabeledValue(label = "Name", value = device.name)
        LabeledValue(label = "RSSI", value = device.rssiLabel)
        LabeledValue(
            label = "Address",
            value = device.address,
            valueStyle = MonospaceValueStyle,
        )
        LabeledValue(label = "TX power", value = device.txPower ?: NOT_AVAILABLE)
        LabeledValue(label = "Connectable", value = device.connectable ?: "Unknown")
        LabeledValue(label = "Advertisements seen", value = device.advertisementCount.toString())

        HorizontalDivider()
        SectionHeader("Service UUIDs")
        if (device.serviceUuids.isEmpty()) {
            Text(NOT_AVAILABLE, style = MaterialTheme.typography.bodyMedium)
        } else {
            device.serviceUuids.forEach { uuid ->
                Text(
                    text = uuid,
                    style = MonospaceValueStyle,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        HorizontalDivider()
        SectionHeader("Manufacturer data")
        if (device.manufacturerData.isEmpty()) {
            Text(NOT_AVAILABLE, style = MaterialTheme.typography.bodyMedium)
        } else {
            device.manufacturerData.forEach { data ->
                LabeledValue(
                    label = "Company ${data.companyId} — ${data.companyName}",
                    value = data.payloadHex.ifEmpty { "(empty)" },
                    valueStyle = MonospaceValueStyle,
                )
                Text(
                    text = "${byteCountLabel(data.payloadSize)}, proprietary payload — not decoded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider()
        SectionHeader("Service data")
        if (device.serviceData.isEmpty()) {
            Text(NOT_AVAILABLE, style = MaterialTheme.typography.bodyMedium)
        } else {
            device.serviceData.forEach { data ->
                LabeledValue(
                    label = "Service ${data.uuid}",
                    value = data.payloadHex.ifEmpty { "(empty)" },
                    valueStyle = MonospaceValueStyle,
                )
            }
        }
    }
}

private fun byteCountLabel(count: Int): String =
    if (count == 1) "1 byte" else "$count bytes"

/**
 * Connection controls.
 *
 * The GATT inspector is only reachable once the device is actually connected: a peripheral
 * advertising nearby says nothing about whether it accepts a connection at all.
 */
@Composable
private fun ConnectionCard(
    connection: ConnectionUiState,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onOpenGattInspector: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = when (connection.status) {
                                ConnectionStatus.CONNECTED -> MaterialTheme.colorScheme.primary
                                ConnectionStatus.RECONNECTING -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.outline
                            },
                            shape = CircleShape,
                        ),
                )
                Text(
                    text = connection.statusLabel,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (connection.isConnecting || connection.isReconnecting ||
                    connection.status == ConnectionStatus.DISCONNECTING
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            connection.hint?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (connection.isBluetoothEnabled && !connection.isReconnecting) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            when (connection.status) {
                ConnectionStatus.CONNECTED -> {
                    Button(onClick = onOpenGattInspector, modifier = Modifier.fillMaxWidth()) {
                        Text("Open GATT Inspector")
                    }
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                        Text("Disconnect")
                    }
                }

                ConnectionStatus.RECONNECTING ->
                    OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                        Text("Stop reconnecting")
                    }

                else -> Button(
                    onClick = onConnect,
                    enabled = connection.canConnect,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (connection.isConnecting) "Connecting…" else "Connect")
                }
            }
        }
    }
}

/**
 * The ESP32 POC service: its live status, the command action, and which of its characteristics
 * were discovered.
 */
@Composable
private fun PocServiceCard(
    poc: PocServiceUiState,
    onReadStatus: () -> Unit,
    onSendPing: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "POC Service", style = MaterialTheme.typography.titleMedium)

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = poc.statusLabel, style = MaterialTheme.typography.headlineSmall)
                    if (poc.isReadingStatus) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                }
            }

            Button(
                onClick = onReadStatus,
                enabled = poc.isReadStatusEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Read Status")
            }

            Button(
                onClick = onSendPing,
                enabled = poc.isSendPingEnabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Send PING")
            }

            poc.commandStatusLabel?.let { label ->
                val isError = poc.commandState is PocCommandState.Failed || !poc.hasCommand
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (poc.commandState == PocCommandState.Sending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isError) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Text(
                text = "Characteristics: " + listOf(
                    "Status" to poc.hasStatus,
                    "Command" to poc.hasCommand,
                    "Events" to poc.hasEvents,
                ).joinToString(" · ") { (name, found) -> if (found) name else "$name (missing)" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Hardware events pushed by the ESP32 on its Events characteristic, oldest first.
 *
 * Touch releases carry the hold duration and intensity statistics computed in the domain layer;
 * the card only renders them. Intensity samples drive the live indicator, not the list.
 * Only the latest [MAX_HARDWARE_EVENTS] events are kept, so it never grows past a few screens.
 * [poc] is `null` while the device is not connected: the history stays, the actions go.
 */
@Composable
private fun HardwareEventsCard(
    events: HardwareEventsUiState,
    poc: PocServiceUiState?,
    onSubscribe: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Hardware Events", style = MaterialTheme.typography.titleMedium)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (poc?.isSubscribingEvents == true) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(
                    text = poc?.eventsStatusLabel ?: "Not connected",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (poc?.isEventsStatusError == true) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            if (poc != null && poc.canSubscribeEvents && !poc.isEventsNotifying) {
                OutlinedButton(
                    onClick = onSubscribe,
                    enabled = poc.isSubscribeEventsEnabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Subscribe")
                }
            }

            TouchSummary(events)

            if (events.entries.isEmpty()) {
                if (poc?.isEventsNotifying == true) {
                    Text(
                        text = "No events received yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                HorizontalDivider()
                val timestampWidth = rememberTimestampWidth()
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    events.entries.forEach { entry ->
                        key(entry.key) {
                            when (entry) {
                                is HardwareEventEntryUi.Touch -> TouchEntry(entry, timestampWidth)
                                is HardwareEventEntryUi.Single -> EventLine(
                                    timestamp = entry.timestamp,
                                    type = entry.type,
                                    timestampWidth = timestampWidth,
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                )
                            }
                        }
                    }
                }
                if (events.isAtLimit) {
                    Text(
                        text = "Showing the latest $MAX_HARDWARE_EVENTS events",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Live state and intensity of the touch sensor, and the statistics of the last completed touch. */
@Composable
private fun TouchSummary(events: HardwareEventsUiState, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = if (events.isTouchPressed) "Touch: pressed" else "Touch: released",
                style = MaterialTheme.typography.labelMedium,
                color = if (events.isTouchPressed) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(text = events.liveIntensityLabel, style = MaterialTheme.typography.labelMedium)
        }
        LinearProgressIndicator(
            progress = { events.liveIntensityProgress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            // Visible on the card's surface; Material's default track blends into it.
            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
            strokeCap = StrokeCap.Round,
            // No gap and no stop dot: an empty bar reads as 0%, not as a stray dot.
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        val lastTouch = events.lastTouch
        Text(
            text = lastTouch?.duration ?: "Duration: —",
            style = MaterialTheme.typography.headlineSmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(text = lastTouch?.peak ?: "Peak: —", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = lastTouch?.average ?: "Average: —",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * One touch: its `TOUCH_ON` and `TOUCH_OFF` lines grouped on a tinted block, so each press reads
 * as a unit; the duration and intensity statistics sit on the release line.
 */
@Composable
private fun TouchEntry(
    touch: HardwareEventEntryUi.Touch,
    timestampWidth: Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = if (touch.isActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            EventLine(
                timestamp = touch.pressedLabel,
                type = PocTouchEvents.TOUCH_ON,
                timestampWidth = timestampWidth,
            )
            EventLine(
                timestamp = touch.releasedLabel,
                type = PocTouchEvents.TOUCH_OFF,
                timestampWidth = timestampWidth,
                trailing = touch.duration ?: touch.releaseStatus,
            )
            touch.intensity?.let { intensity ->
                Text(
                    text = intensity,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = timestampWidth + EVENT_COLUMN_GAP),
                )
            }
        }
    }
}

/**
 * Timestamp, event name and an optional trailing value. The timestamp column has a fixed width,
 * so event names line up whatever the digits' widths.
 */
@Composable
private fun EventLine(
    timestamp: String,
    type: String,
    timestampWidth: Dp,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(EVENT_COLUMN_GAP),
    ) {
        Text(
            text = timestamp,
            style = eventTimestampStyle(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(timestampWidth),
        )
        Text(text = type, style = MonospaceValueStyle, modifier = Modifier.weight(1f))
        trailing?.let { Text(text = it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun eventTimestampStyle(): TextStyle =
    // Tabular figures keep every timestamp the same width.
    MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum")

/** Width of the widest timestamp (`HH:mm:ss.SSS`) at the current font scale. */
@Composable
private fun rememberTimestampWidth(): Dp {
    val measurer = rememberTextMeasurer()
    val style = eventTimestampStyle()
    val density = LocalDensity.current
    return remember(measurer, style, density) {
        with(density) { measurer.measure("88:88:88.888", style).size.width.toDp() }
    }
}

private val EVENT_COLUMN_GAP = 12.dp
