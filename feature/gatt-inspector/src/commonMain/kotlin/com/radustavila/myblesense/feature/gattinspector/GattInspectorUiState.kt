package com.radustavila.myblesense.feature.gattinspector

import androidx.compose.runtime.Immutable
import com.radustavila.myblesense.domain.model.GattCharacteristicId
import com.radustavila.myblesense.domain.model.GattConnectionState

/**
 * Fully formatted, immutable snapshot of the inspector screen.
 *
 * Every string the Composables draw is produced by [toUiState]; no hex formatting, UUID lookup or
 * decoding happens during composition.
 */
@Immutable
data class GattInspectorUiState(
    val deviceName: String,
    val connectionState: GattConnectionState,
    val isDiscoveringServices: Boolean,
    val services: List<GattServiceUi>,
    val message: UiMessage? = null,
) {
    val isConnected: Boolean get() = connectionState == GattConnectionState.CONNECTED

    val hasNoServices: Boolean get() = isConnected && !isDiscoveringServices && services.isEmpty()
}

@Immutable
data class GattServiceUi(
    val uuid: String,
    val shortUuid: String,
    /** Assigned name, or `null` for a UUID the app makes no claim about. */
    val name: String?,
    val isExpanded: Boolean,
    val characteristics: List<GattCharacteristicUi>,
) {
    val title: String get() = name ?: "Unknown service"

    val characteristicCount: Int get() = characteristics.size
}

@Immutable
data class GattCharacteristicUi(
    val id: GattCharacteristicId,
    val uuid: String,
    val shortUuid: String,
    val name: String?,
    val properties: List<String>,
    val canRead: Boolean,
    val canWrite: Boolean,
    val writeLabel: String,
    val supportsSubscription: Boolean,
    val isNotifying: Boolean,
    val isExpanded: Boolean,
    val operation: CharacteristicOperation,
    val latestValue: CharacteristicValueUi?,
    val notifications: List<NotificationUi>,
    val writeInput: String,
    val writeError: String?,
) {
    val title: String get() = name ?: "Unknown characteristic"

    val isBusy: Boolean get() = operation != CharacteristicOperation.IDLE

    val hasActions: Boolean get() = canRead || canWrite || supportsSubscription
}

/** What the screen is currently waiting for on a given characteristic. */
enum class CharacteristicOperation {
    IDLE,
    READING,
    WRITING,
    UPDATING_NOTIFICATIONS,
}

/**
 * A raw value, plus the optional interpretations.
 *
 * [decoded] is only ever filled for characteristics whose format the Bluetooth SIG defines, and
 * [utf8] only when the bytes are printable text — both are labelled as interpretations in the UI.
 */
@Immutable
data class CharacteristicValueUi(
    val hex: String,
    val byteCount: Int,
    val utf8: String?,
    val decoded: String?,
    val timestamp: String,
    val fromNotification: Boolean,
)

@Immutable
data class NotificationUi(
    val timestamp: String,
    val hex: String,
    val decoded: String?,
)

/** One-shot user feedback, shown in a snackbar and then dismissed. */
@Immutable
data class UiMessage(
    val text: String,
    val isError: Boolean,
)

/** Everything the user can do on the inspector screen. */
sealed interface GattInspectorEvent {
    data class ToggleService(val serviceUuid: String) : GattInspectorEvent
    data class ToggleCharacteristic(val id: GattCharacteristicId) : GattInspectorEvent
    data class Read(val id: GattCharacteristicId) : GattInspectorEvent
    data class WriteInputChanged(val id: GattCharacteristicId, val input: String) :
        GattInspectorEvent

    /** Writes are only ever sent by this explicit event, never as a side effect of typing. */
    data class Write(val id: GattCharacteristicId) : GattInspectorEvent
    data class SetNotifications(val id: GattCharacteristicId, val enabled: Boolean) :
        GattInspectorEvent

    data object Disconnect : GattInspectorEvent
    data object DismissMessage : GattInspectorEvent
}
