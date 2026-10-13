package com.radustavila.myblesense.feature.devicedetails

import com.radustavila.myblesense.core.common.format.formatDecimal
import com.radustavila.myblesense.core.common.time.formatClockTime
import com.radustavila.myblesense.core.common.format.toCompanyIdHex
import com.radustavila.myblesense.core.common.format.toHexString
import com.radustavila.myblesense.domain.model.BleDevice
import com.radustavila.myblesense.domain.model.GattConnection
import com.radustavila.myblesense.domain.model.GattConnectionState
import com.radustavila.myblesense.domain.model.HardwareEvent
import com.radustavila.myblesense.domain.model.PocTouchEvents
import com.radustavila.myblesense.domain.model.KnownDevice
import com.radustavila.myblesense.domain.model.PocService
import com.radustavila.myblesense.domain.model.ReconnectionStatus
import com.radustavila.myblesense.domain.model.SignalStrength
import kotlinx.datetime.TimeZone

/** Maps a domain device to its display form; kept out of Compose so it stays unit testable. */
internal fun BleDevice.toDetailsUi(): DeviceDetailsUi = DeviceDetailsUi(
    id = id,
    name = displayName ?: UNKNOWN_DEVICE,
    isNameKnown = displayName != null,
    rssiDbm = rssi,
    signalLabel = signalStrength.label(),
    address = address,
    txPower = txPowerDbm?.let { "$it dBm" },
    connectable = isConnectable?.let { if (it) "Yes" else "No" },
    advertisementCount = seenCount,
    serviceUuids = serviceUuids,
    manufacturerData = manufacturerData.map {
        ManufacturerDataUi(
            companyId = it.companyId.toCompanyIdHex(),
            companyName = it.companyName ?: UNKNOWN_COMPANY,
            payloadHex = it.payload.toHexString(),
            payloadSize = it.payload.size,
        )
    },
    serviceData = serviceData.map {
        ServiceDataUi(
            uuid = it.uuid,
            payloadHex = it.payload.toHexString(),
            payloadSize = it.payload.size,
        )
    },
)

/**
 * A device known from an earlier connection but not seen by the current scan: only its identity
 * is known, every advertising field is left empty rather than guessed.
 */
internal fun KnownDevice.toDetailsUi(): DeviceDetailsUi = DeviceDetailsUi(
    id = id,
    name = name ?: UNKNOWN_DEVICE,
    isNameKnown = name != null,
    rssiDbm = null,
    signalLabel = null,
    address = id,
    txPower = null,
    connectable = null,
    advertisementCount = 0,
    serviceUuids = emptyList(),
    manufacturerData = emptyList(),
    serviceData = emptyList(),
)

/**
 * Maps the discovered POC service, keeping the last status, command outcome and progress of
 * [previous] so a re-emission of the same GATT database does not wipe what is on
 * screen. [isEventsNotifying] is whether the Events characteristic's CCCD is currently enabled.
 */
internal fun PocService.toUi(
    previous: PocServiceUiState? = null,
    isEventsNotifying: Boolean = false,
): PocServiceUiState =
    PocServiceUiState(
        hasStatus = status != null,
        hasCommand = command != null,
        hasEvents = events != null,
        canReadStatus = status?.canRead == true,
        status = previous?.status,
        isReadingStatus = previous?.isReadingStatus == true,
        canSendCommand = command?.canWrite == true,
        commandState = previous?.commandState ?: PocCommandState.Idle,
        canSubscribeEvents = events?.supportsSubscription == true,
        isEventsNotifying = isEventsNotifying,
        isSubscribingEvents = previous?.isSubscribingEvents == true,
        eventsError = previous?.eventsError,
    )

/**
 * What the screen shows for the link to [deviceId], derived from the GATT client's actual state.
 *
 * A [reconnection] in progress wins over everything else: between two attempts the client is
 * simply disconnected, but the screen is still working on getting the link back.
 * [isConnectRequested] only bridges the instant between the user tapping Connect and the client
 * reporting that it started.
 */
internal fun GattConnection.toConnectionStatus(
    deviceId: String,
    isConnectRequested: Boolean,
    reconnection: ReconnectionStatus?,
): ConnectionStatus {
    val isThisDevice = this.deviceId == deviceId
    return when {
        reconnection != null -> ConnectionStatus.RECONNECTING
        isThisDevice && state == GattConnectionState.CONNECTED && servicesDiscovered ->
            ConnectionStatus.CONNECTED
        isThisDevice && state == GattConnectionState.CONNECTED -> ConnectionStatus.CONNECTING
        isThisDevice && state == GattConnectionState.CONNECTING -> ConnectionStatus.CONNECTING
        isThisDevice && state == GattConnectionState.DISCONNECTING -> ConnectionStatus.DISCONNECTING
        isConnectRequested -> ConnectionStatus.CONNECTING
        else -> ConnectionStatus.DISCONNECTED
    }
}

/** Connected to [deviceId], but its GATT database is not available yet. */
internal fun GattConnection.isDiscoveringServices(deviceId: String): Boolean =
    this.deviceId == deviceId && state == GattConnectionState.CONNECTED && !servicesDiscovered

/** `null` once the reconnection reached a terminal state: it is then no longer in progress. */
internal fun ReconnectionStatus.toUiOrNull(): ReconnectionUi? = when (this) {
    is ReconnectionStatus.Waiting,
    is ReconnectionStatus.Connecting,
    -> ReconnectionUi(attempt = attempt, maxAttempts = maxAttempts)

    is ReconnectionStatus.Reconnected,
    is ReconnectionStatus.GaveUp,
    -> null
}

/**
 * Adds [event] to the section, keeping only the latest [max] entries, and updates the live
 * intensity and the touch summary.
 *
 * A release completes the active touch entry started by its press, so the pair reads as one
 * entry; a release without one gets an entry of its own. Intensity samples only update the live
 * indicator: at ~10 per second they would push every other event out of the history. [sequence]
 * numbers the event; [zone] only exists so tests do not depend on the machine's zone.
 */
internal fun HardwareEventsUiState.plus(
    event: HardwareEvent,
    sequence: Long,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    max: Int = MAX_HARDWARE_EVENTS,
): HardwareEventsUiState {
    val timestamp = formatEventTime(event.timestampMillis, zone)
    return when (event) {
        // A sample means the sensor is held, even when its TOUCH_ON was not received.
        is HardwareEvent.TouchIntensity ->
            copy(isTouchPressed = true, liveIntensityPercent = event.percent)

        is HardwareEvent.TouchPressed -> copy(
            // A press while another is active means that one's TOUCH_OFF was lost.
            entries = (endActiveTouch().entries + HardwareEventEntryUi.Touch(
                key = sequence,
                pressedAt = timestamp,
                isActive = true,
            )).takeLast(max),
            isTouchPressed = true,
            liveIntensityPercent = null,
        )

        // A touch shorter than the minimum is a glitch: its press entry is removed, nothing is kept.
        is HardwareEvent.TouchReleased -> if (event.isTooShort()) {
            copy(
                entries = entries.filterNot { it is HardwareEventEntryUi.Touch && it.isActive },
                isTouchPressed = false,
                liveIntensityPercent = null,
            )
        } else {
            val active = entries.indexOfLast { it is HardwareEventEntryUi.Touch && it.isActive }
            val touch = HardwareEventEntryUi.Touch(
                key = if (active >= 0) entries[active].key else sequence,
                pressedAt = (entries.getOrNull(active) as? HardwareEventEntryUi.Touch)?.pressedAt,
                releasedAt = timestamp,
                duration = formatTouchDuration(event.durationMillis),
                intensity = event.intensity?.let {
                    "Peak ${formatIntensity(it.peakPercent)} · Avg ${formatIntensity(it.averagePercent)}"
                },
            )
            copy(
                entries = if (active >= 0) {
                    entries.toMutableList().apply { set(active, touch) }
                } else {
                    (entries + touch).takeLast(max)
                },
                isTouchPressed = false,
                liveIntensityPercent = null,
                lastTouch = event.toStatsUi(),
            )
        }

        is HardwareEvent.Other -> copy(
            entries = (entries + HardwareEventEntryUi.Single(
                key = sequence,
                type = event.text.ifEmpty { "(empty)" },
                timestamp = timestamp,
            )).takeLast(max),
        )
    }
}

/**
 * The touch in progress, if any, will never be released as far as this section knows (the link
 * went away, or a newer press superseded it): it stays in the history without a release.
 */
internal fun HardwareEventsUiState.endActiveTouch(): HardwareEventsUiState = copy(
    entries = entries.map {
        if (it is HardwareEventEntryUi.Touch && it.isActive) it.copy(isActive = false) else it
    },
    isTouchPressed = false,
    liveIntensityPercent = null,
)

private fun HardwareEvent.TouchReleased.isTooShort(): Boolean =
    (durationMillis ?: Long.MAX_VALUE) < PocTouchEvents.MIN_TOUCH_MILLIS

internal fun HardwareEvent.TouchReleased.toStatsUi(): TouchStatsUi = TouchStatsUi(
    duration = "Duration: ${formatTouchDuration(durationMillis)}",
    peak = "Peak: ${formatIntensity(intensity?.peakPercent)}",
    average = "Average: ${formatIntensity(intensity?.averagePercent)}",
)

/** A whole percentage, e.g. `94%`; `null` (no samples) is shown as a dash. */
internal fun formatIntensity(percent: Int?): String = percent?.let { "$it%" } ?: "—"

/**
 * One decimal of a second, e.g. `1.8s`: notifications arrive on the BLE connection interval, so
 * more precision than that would be noise. `null` means the press was never observed.
 */
internal fun formatTouchDuration(durationMillis: Long?): String = when {
    durationMillis == null -> "unknown"
    durationMillis < 100 -> "<0.1s"
    else -> formatDecimal(durationMillis / 1000.0, digits = 1) + "s"
}

internal fun formatEventTime(millis: Long, zone: TimeZone = TimeZone.currentSystemDefault()): String =
    formatClockTime(millis, zone, withMillis = true)

private fun SignalStrength.label(): String = when (this) {
    SignalStrength.STRONG -> "Strong"
    SignalStrength.MEDIUM -> "Medium"
    SignalStrength.WEAK -> "Weak"
}

internal const val UNKNOWN_DEVICE = "Unknown device"
internal const val UNKNOWN_COMPANY = "Unknown company"
