package com.radustavila.bleexplorer.feature.scanner

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.radustavila.bleexplorer.core.designsystem.component.SignalIndicator
import com.radustavila.bleexplorer.domain.model.BleDevice

/**
 * One row per physical device. Everything shown here is pre-computed by the presentation mapper:
 * no parsing, no formatting logic and certainly no Bluetooth call inside the Composable.
 */
@Composable
internal fun DeviceListItem(
    device: DeviceListItemUi,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick(device.id) },
        headlineContent = {
            Text(
                text = device.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                device.subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    SignalIndicator(level = device.signalLevel, rssiDbm = device.rssi)
                    device.detail?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
    )
}

internal fun BleDevice.toListItemUi(): DeviceListItemUi = DeviceListItemUi(
    id = id,
    name = displayName ?: UNKNOWN_DEVICE,
    subtitle = manufacturerName,
    detail = serviceUuids.firstOrNull()?.let { "Service $it" }
        ?: txPowerDbm?.let { "Tx $it dBm" },
    rssi = rssi,
    signalLevel = signalStrength.toUiLevel(),
)

internal const val UNKNOWN_DEVICE = "Unknown device"
