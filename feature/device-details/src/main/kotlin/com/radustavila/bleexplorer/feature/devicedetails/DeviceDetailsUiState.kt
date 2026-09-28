package com.radustavila.bleexplorer.feature.devicedetails

import androidx.compose.runtime.Immutable

/** Immutable, fully formatted state of the details screen. */
@Immutable
data class DeviceDetailsUiState(
    val isLoading: Boolean = true,
    val device: DeviceDetailsUi? = null,
    val connection: ConnectionUiState = ConnectionUiState(),
    /** Present only while connected to this device *and* it exposes the ESP32 POC service. */
    val poc: PocServiceUiState? = null,
    /**
     * Touch sensor and other firmware events received on this screen. Kept apart from [poc] so the
     * history survives a link loss and the reconnection that follows.
     */
    val hardwareEvents: HardwareEventsUiState = HardwareEventsUiState(),
) {
    val isMissing: Boolean get() = !isLoading && device == null

    /** The section is shown while the POC device is connected, and afterwards while it has history. */
    val showsHardwareEvents: Boolean get() = poc != null || hardwareEvents.entries.isNotEmpty()
}

/**
 * What the screen says about the link to *this* device.
 *
 * The app keeps a single GATT connection, which may well belong to another device the user
 * connected to earlier: that one is [DISCONNECTED] from this screen's point of view.
 */
enum class ConnectionStatus {
    DISCONNECTED,

    /** Opening the link or discovering services; nothing can be used yet. */
    CONNECTING,

    /** Link up and GATT database discovered. */
    CONNECTED,
    DISCONNECTING,

    /** The link was lost unexpectedly and a bounded automatic reconnection is in progress. */
    RECONNECTING,
}

/**
 * Connection part of the screen.
 *
 * [status] is derived from the GATT client's actual connection state (plus the reconnection in
 * progress, which only the ViewModel knows about), never from what the user last tapped.
 */
@Immutable
data class ConnectionUiState(
    val status: ConnectionStatus = ConnectionStatus.DISCONNECTED,
    /** Connected, but the GATT database is still being discovered. */
    val isDiscoveringServices: Boolean = false,
    /** Non-null exactly while [status] is [ConnectionStatus.RECONNECTING]. */
    val reconnection: ReconnectionUi? = null,
    val isBluetoothEnabled: Boolean = true,
    /** Why the link is (or was last) down; already user facing. Cleared once connected. */
    val problem: String? = null,
    /** One-off message shown as a snackbar, then dismissed. */
    val message: String? = null,
) {
    val isConnectedToThisDevice: Boolean get() = status == ConnectionStatus.CONNECTED

    val isConnecting: Boolean get() = status == ConnectionStatus.CONNECTING

    val isReconnecting: Boolean get() = status == ConnectionStatus.RECONNECTING

    val statusLabel: String
        get() = when (status) {
            ConnectionStatus.CONNECTING ->
                if (isDiscoveringServices) "Discovering services…" else "Connecting…"
            ConnectionStatus.CONNECTED -> "Connected"
            ConnectionStatus.DISCONNECTING -> "Disconnecting…"
            ConnectionStatus.RECONNECTING -> reconnection?.label ?: "Reconnecting…"
            ConnectionStatus.DISCONNECTED ->
                if (isBluetoothEnabled) "Disconnected" else "Bluetooth is off"
        }

    val canConnect: Boolean
        get() = status == ConnectionStatus.DISCONNECTED && isBluetoothEnabled

    /** Offered while connected, and while reconnecting so the user can stop the attempts. */
    val canDisconnect: Boolean
        get() = status == ConnectionStatus.CONNECTED || status == ConnectionStatus.RECONNECTING

    val canOpenInspector: Boolean get() = isConnectedToThisDevice

    /** Shown under the status, e.g. why the link was lost or why Connect is disabled. */
    val hint: String?
        get() = when {
            status == ConnectionStatus.CONNECTED || status == ConnectionStatus.CONNECTING -> null
            status == ConnectionStatus.DISCONNECTED && !isBluetoothEnabled ->
                "Turn Bluetooth on to connect."
            else -> problem
        }
}

/** Progress of an automatic reconnection. */
@Immutable
data class ReconnectionUi(
    val attempt: Int,
    val maxAttempts: Int,
) {
    val label: String get() = "Reconnecting… (attempt $attempt of $maxAttempts)"
}

/**
 * The ESP32 POC service of the connected device.
 *
 * The three `has*` flags say which characteristics discovery actually found, so a firmware that
 * is missing one is visible at a glance.
 *
 * The events themselves live in [HardwareEventsUiState]; [isEventsNotifying] mirrors the CCCD
 * state reported by the GATT client, so a subscription switched off elsewhere (the inspector)
 * shows up here too.
 */
@Immutable
data class PocServiceUiState(
    val hasStatus: Boolean,
    val hasCommand: Boolean,
    val hasEvents: Boolean,
    val canReadStatus: Boolean,
    val status: String? = null,
    val isReadingStatus: Boolean = false,
    val canSendCommand: Boolean = false,
    val commandState: PocCommandState = PocCommandState.Idle,
    val canSubscribeEvents: Boolean = false,
    val isEventsNotifying: Boolean = false,
    val isSubscribingEvents: Boolean = false,
    /** Why the last subscription attempt failed; already user facing. */
    val eventsError: String? = null,
) {
    val statusLabel: String
        get() = when {
            !hasStatus -> "Characteristic not found"
            status != null -> status.ifEmpty { "(empty)" }
            isReadingStatus -> "Reading…"
            else -> "Not read yet"
        }

    val isReadStatusEnabled: Boolean get() = canReadStatus && !isReadingStatus

    val isSendPingEnabled: Boolean
        get() = canSendCommand && commandState != PocCommandState.Sending

    /** Outcome of the last command, or `null` when there is nothing to say yet. */
    val commandStatusLabel: String?
        get() = when {
            !hasCommand -> "Command characteristic not found"
            else -> when (commandState) {
                PocCommandState.Idle -> null
                PocCommandState.Sending -> "Sending…"
                PocCommandState.Sent -> "Command sent"
                is PocCommandState.Failed -> commandState.message
            }
        }

    val eventsStatusLabel: String
        get() = when {
            !hasEvents -> "Events characteristic not found"
            !canSubscribeEvents -> "Events characteristic does not support notifications"
            isSubscribingEvents -> "Subscribing…"
            isEventsNotifying -> "Listening for events"
            eventsError != null -> eventsError
            else -> "Not subscribed"
        }

    val isEventsStatusError: Boolean
        get() = !hasEvents || !canSubscribeEvents ||
            (eventsError != null && !isSubscribingEvents && !isEventsNotifying)

    /** Offered whenever the subscription is off, so a failed or dropped one can be restored. */
    val isSubscribeEventsEnabled: Boolean
        get() = canSubscribeEvents && !isSubscribingEvents && !isEventsNotifying
}

/**
 * Hardware events section: the latest entries in chronological order (oldest first), bounded to
 * [MAX_HARDWARE_EVENTS], the live intensity of the touch in progress, plus the outcome of the last
 * touch.
 *
 * A `TOUCH_ON` and the `TOUCH_OFF` that ends it are one [HardwareEventEntryUi.Touch] entry, so each
 * press reads as one unit. Intensity samples arrive about ten times a second, so they only drive
 * the live indicator; the touch entry carries the statistics of the whole touch once released.
 */
@Immutable
data class HardwareEventsUiState(
    val entries: List<HardwareEventEntryUi> = emptyList(),
    /** Whether the touch sensor is held right now, as far as the received events tell. */
    val isTouchPressed: Boolean = false,
    /** Latest intensity sample (`0..100`) of the touch in progress; `null` when there is none. */
    val liveIntensityPercent: Int? = null,
    /** Statistics of the last completed touch; `null` until one completes. */
    val lastTouch: TouchStatsUi? = null,
) {
    val isAtLimit: Boolean get() = entries.size >= MAX_HARDWARE_EVENTS

    /** Fill of the live intensity indicator, `0f..1f`; empty while no touch reports a sample. */
    val liveIntensityProgress: Float get() = (liveIntensityPercent ?: 0) / 100f

    val liveIntensityLabel: String
        get() = "Intensity: ${liveIntensityPercent?.let { "$it%" } ?: "—"}"
}

/** Summary of a completed touch, e.g. `"Duration: 2.4s"`, `"Peak: 94%"`, `"Average: 81%"`. */
@Immutable
data class TouchStatsUi(
    val duration: String,
    val peak: String,
    val average: String,
)

/** One entry of the hardware events history, fully formatted. */
@Immutable
sealed interface HardwareEventEntryUi {
    /** Sequence of the entry's first event, increasing per screen; a stable list key. */
    val key: Long

    /**
     * One touch: its `TOUCH_ON` and `TOUCH_OFF` edges.
     *
     * [pressedAt] is `null` when the press was never received, [releasedAt] while the touch is
     * still held (see [isActive]) or when its release was never received.
     */
    data class Touch(
        override val key: Long,
        val pressedAt: String?,
        val releasedAt: String? = null,
        /** Hold duration once released, e.g. `"1.8s"`, or `"unknown"`. */
        val duration: String? = null,
        /** Intensity statistics once released with samples, e.g. `"Peak 94% · Avg 81%"`. */
        val intensity: String? = null,
        /** Pressed and not released yet; the next `TOUCH_OFF` completes this entry. */
        val isActive: Boolean = false,
    ) : HardwareEventEntryUi {
        val pressedLabel: String get() = pressedAt ?: MISSING_TIMESTAMP
        val releasedLabel: String get() = releasedAt ?: MISSING_TIMESTAMP

        /** Shown on the `TOUCH_OFF` line instead of a duration while there is none. */
        val releaseStatus: String?
            get() = when {
                releasedAt != null -> null
                isActive -> "Holding…"
                else -> "Not received"
            }
    }

    /** Any other event, e.g. unknown firmware text; [type] is the raw text. */
    data class Single(
        override val key: Long,
        val type: String,
        val timestamp: String,
    ) : HardwareEventEntryUi
}

/** Placeholder for the timestamp of an edge that was never received. */
const val MISSING_TIMESTAMP = "—"

/**
 * How many history entries the section keeps (a touch counts once); older ones are dropped as new
 * ones arrive.
 */
const val MAX_HARDWARE_EVENTS = 50

/** Progress of the last command written to the POC Command characteristic. */
@Immutable
sealed interface PocCommandState {
    data object Idle : PocCommandState
    data object Sending : PocCommandState

    /** The peripheral acknowledged the write. */
    data object Sent : PocCommandState

    /** The write failed; [message] is already user facing. */
    data class Failed(val message: String) : PocCommandState
}

/**
 * Presentation model of a device. Advertising payloads are already formatted as hex here, so the
 * Composables never parse anything.
 */
@Immutable
data class DeviceDetailsUi(
    val id: String,
    val name: String,
    val isNameKnown: Boolean,
    /** `null` when the device is only known from an earlier connection, not from this scan. */
    val rssiDbm: Int?,
    val signalLabel: String?,
    val address: String,
    val txPower: String?,
    val connectable: String?,
    val advertisementCount: Int,
    val serviceUuids: List<String>,
    val manufacturerData: List<ManufacturerDataUi>,
    val serviceData: List<ServiceDataUi>,
) {
    val rssiLabel: String
        get() = rssiDbm?.let { "$it dBm ($signalLabel)" } ?: "Not seen in the current scan"
}

@Immutable
data class ManufacturerDataUi(
    val companyId: String,
    val companyName: String,
    val payloadHex: String,
    val payloadSize: Int,
)

@Immutable
data class ServiceDataUi(
    val uuid: String,
    val payloadHex: String,
    val payloadSize: Int,
)

sealed interface DeviceDetailsEvent {
    /**
     * A first connection is always explicit. The screen only connects on its own to restore a
     * link that was lost unexpectedly, and only a bounded number of times.
     */
    data object Connect : DeviceDetailsEvent

    /** Closes the link, or stops an automatic reconnection in progress. */
    data object Disconnect : DeviceDetailsEvent
    data object ReadPocStatus : DeviceDetailsEvent
    data object SendPocPing : DeviceDetailsEvent
    data object SubscribePocEvents : DeviceDetailsEvent
    data object DismissMessage : DeviceDetailsEvent
}
