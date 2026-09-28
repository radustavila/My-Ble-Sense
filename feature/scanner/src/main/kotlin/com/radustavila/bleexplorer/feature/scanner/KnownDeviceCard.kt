package com.radustavila.bleexplorer.feature.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radustavila.bleexplorer.core.designsystem.component.SignalIndicator

/**
 * The device remembered from an earlier connection, shown above the scan results without needing
 * a scan. Tapping it opens its details; the buttons act on the link itself.
 */
@Composable
internal fun KnownDeviceCard(
    device: KnownDeviceUi,
    onOpen: (String) -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedCard(onClick = { onOpen(device.id) }, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Known device",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = device.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(device.status.dotColor(), CircleShape),
                )
                Text(text = device.statusLabel, style = MaterialTheme.typography.titleSmall)
                if (device.isBusy) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                }
                if (device.rssi != null && device.signalLevel != null) {
                    SignalIndicator(level = device.signalLevel, rssiDbm = device.rssi)
                }
            }
            device.hint?.let { hint ->
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            KnownDeviceActions(
                device = device,
                onOpen = { onOpen(device.id) },
                onConnect = onConnect,
                onDisconnect = onDisconnect,
            )
        }
    }
}

@Composable
private fun KnownDeviceActions(
    device: KnownDeviceUi,
    onOpen: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    when {
        device.canDisconnect -> Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Button(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("Open") }
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.weight(1f)) {
                Text("Disconnect")
            }
        }

        device.canCancel ->
            OutlinedButton(onClick = onDisconnect, modifier = Modifier.fillMaxWidth()) {
                Text("Cancel")
            }

        // Disabled while Bluetooth is off, the permission is missing or the link is closing.
        else -> Button(
            onClick = onConnect,
            enabled = device.canConnect,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Connect")
        }
    }
}

@Composable
private fun KnownDeviceStatus.dotColor(): Color = when (this) {
    KnownDeviceStatus.CONNECTED, KnownDeviceStatus.AVAILABLE -> MaterialTheme.colorScheme.primary
    KnownDeviceStatus.CONNECTING, KnownDeviceStatus.DISCONNECTING ->
        MaterialTheme.colorScheme.tertiary

    KnownDeviceStatus.UNAVAILABLE,
    KnownDeviceStatus.BLUETOOTH_OFF,
    KnownDeviceStatus.PERMISSION_REQUIRED,
    -> MaterialTheme.colorScheme.error

    KnownDeviceStatus.DISCONNECTED -> MaterialTheme.colorScheme.outline
}
