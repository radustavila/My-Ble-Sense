package com.radustavila.bleexplorer.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.radustavila.bleexplorer.core.designsystem.theme.SignalColors

/** Qualitative signal levels rendered by [SignalIndicator]. */
enum class SignalLevel(val label: String, val color: Color) {
    STRONG("Strong", SignalColors.strong),
    MEDIUM("Medium", SignalColors.medium),
    WEAK("Weak", SignalColors.weak),
}

/**
 * Coloured dot plus the RSSI value in dBm.
 *
 * RSSI is deliberately never translated into a distance: it only supports a coarse
 * strong / medium / weak reading.
 */
@Composable
fun SignalIndicator(
    level: SignalLevel,
    rssiDbm: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clearAndSetSemantics {
            contentDescription = "${level.label} signal, $rssiDbm dBm"
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(level.color, CircleShape),
        )
        Text(
            text = "$rssiDbm dBm",
            style = MaterialTheme.typography.labelLarge,
        )
    }
}
