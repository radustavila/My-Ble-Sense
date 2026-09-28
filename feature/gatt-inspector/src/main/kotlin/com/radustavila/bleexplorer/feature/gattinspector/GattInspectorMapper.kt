package com.radustavila.bleexplorer.feature.gattinspector

import com.radustavila.bleexplorer.core.bluetooth.mapper.BluetoothSigNames
import com.radustavila.bleexplorer.core.bluetooth.mapper.shortenBluetoothUuid
import com.radustavila.bleexplorer.core.common.format.toHexString
import com.radustavila.bleexplorer.core.common.format.toPrintableUtf8OrNull
import com.radustavila.bleexplorer.domain.model.GattCharacteristic
import com.radustavila.bleexplorer.domain.model.GattCharacteristicId
import com.radustavila.bleexplorer.domain.model.GattCharacteristicProperty
import com.radustavila.bleexplorer.domain.model.GattCharacteristicValue
import com.radustavila.bleexplorer.domain.model.GattConnection
import com.radustavila.bleexplorer.domain.model.GattService
import com.radustavila.bleexplorer.domain.model.GattValueSource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Everything the inspector ViewModel keeps, before it is formatted for the screen.
 *
 * Per-characteristic maps (values, pending operations, write inputs) are keyed by
 * [GattCharacteristicId] instead of living inside the characteristic model, so a new service
 * discovery never wipes what the user typed and the domain models stay pure descriptions of the
 * peripheral.
 */
internal data class InspectorState(
    val deviceId: String,
    val deviceName: String? = null,
    val connection: GattConnection = GattConnection.Disconnected,
    val services: List<GattService> = emptyList(),
    val notifying: Set<GattCharacteristicId> = emptySet(),
    val latestValues: Map<GattCharacteristicId, GattCharacteristicValue> = emptyMap(),
    val notificationLog: Map<GattCharacteristicId, List<GattCharacteristicValue>> = emptyMap(),
    val operations: Map<GattCharacteristicId, CharacteristicOperation> = emptyMap(),
    val writeInputs: Map<GattCharacteristicId, String> = emptyMap(),
    val writeErrors: Map<GattCharacteristicId, String> = emptyMap(),
    val expandedServices: Set<String> = emptySet(),
    val expandedCharacteristics: Set<GattCharacteristicId> = emptySet(),
    val message: UiMessage? = null,
)

/** Pure domain → presentation mapping; unit tested without Compose or Android. */
internal fun InspectorState.toUiState(): GattInspectorUiState = GattInspectorUiState(
    deviceName = deviceName ?: deviceId,
    connectionState = connection.state,
    isDiscoveringServices = connection.isConnected && !connection.servicesDiscovered,
    services = services.map { service -> toServiceUi(service) },
    message = message,
)

private fun InspectorState.toServiceUi(service: GattService): GattServiceUi = GattServiceUi(
    uuid = service.uuid,
    shortUuid = service.uuid.shortenBluetoothUuid(),
    name = BluetoothSigNames.serviceName(service.uuid),
    isExpanded = service.uuid in expandedServices,
    characteristics = service.characteristics.map { toCharacteristicUi(it) },
)

private fun InspectorState.toCharacteristicUi(
    characteristic: GattCharacteristic,
): GattCharacteristicUi {
    val id = characteristic.id
    return GattCharacteristicUi(
        id = id,
        uuid = characteristic.uuid,
        shortUuid = characteristic.uuid.shortenBluetoothUuid(),
        name = BluetoothSigNames.characteristicName(characteristic.uuid),
        properties = characteristic.properties.sorted().map { it.label() },
        canRead = characteristic.canRead,
        canWrite = characteristic.canWrite,
        // The peripheral decides how a write is acknowledged; the UI only says which one it is.
        writeLabel = if (characteristic.canWriteWithResponse) {
            "Write (with response)"
        } else {
            "Write (no response)"
        },
        supportsSubscription = characteristic.supportsSubscription,
        isNotifying = id in notifying,
        isExpanded = id in expandedCharacteristics,
        operation = operations[id] ?: CharacteristicOperation.IDLE,
        latestValue = latestValues[id]?.toValueUi(),
        notifications = notificationLog[id].orEmpty().map { it.toNotificationUi() },
        writeInput = writeInputs[id].orEmpty(),
        writeError = writeErrors[id],
    )
}

private fun GattCharacteristicValue.toValueUi(): CharacteristicValueUi = CharacteristicValueUi(
    hex = value.toHexString(),
    byteCount = value.size,
    utf8 = value.toPrintableUtf8OrNull(),
    decoded = StandardCharacteristicDecoder.decode(id.characteristicUuid, value),
    timestamp = formatTime(timestampMillis),
    fromNotification = source == GattValueSource.NOTIFICATION,
)

private fun GattCharacteristicValue.toNotificationUi(): NotificationUi = NotificationUi(
    timestamp = formatTime(timestampMillis),
    hex = value.toHexString(),
    decoded = StandardCharacteristicDecoder.decode(id.characteristicUuid, value),
)

private fun GattCharacteristicProperty.label(): String = when (this) {
    GattCharacteristicProperty.READ -> "READ"
    GattCharacteristicProperty.WRITE -> "WRITE"
    GattCharacteristicProperty.WRITE_NO_RESPONSE -> "WRITE NO RESP"
    GattCharacteristicProperty.NOTIFY -> "NOTIFY"
    GattCharacteristicProperty.INDICATE -> "INDICATE"
}

internal fun formatTime(millis: Long): String = TIME_FORMATTER.format(Instant.ofEpochMilli(millis))

private val TIME_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())
