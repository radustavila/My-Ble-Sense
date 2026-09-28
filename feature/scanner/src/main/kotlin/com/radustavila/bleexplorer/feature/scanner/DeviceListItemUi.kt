package com.radustavila.bleexplorer.feature.scanner

import androidx.compose.runtime.Immutable
import com.radustavila.bleexplorer.core.designsystem.component.SignalLevel
import com.radustavila.bleexplorer.domain.model.SignalStrength

/** Display-ready row model: the Composables only render strings. */
@Immutable
internal data class DeviceListItemUi(
    val id: String,
    val name: String,
    val subtitle: String?,
    val detail: String?,
    val rssi: Int,
    val signalLevel: SignalLevel,
)

internal fun SignalStrength.toUiLevel(): SignalLevel = when (this) {
    SignalStrength.STRONG -> SignalLevel.STRONG
    SignalStrength.MEDIUM -> SignalLevel.MEDIUM
    SignalStrength.WEAK -> SignalLevel.WEAK
}
