package com.radustavila.myblesense.feature.devicedetails

import com.google.common.truth.Truth.assertThat
import com.radustavila.myblesense.domain.model.BleDevice
import com.radustavila.myblesense.domain.model.GattConnection
import com.radustavila.myblesense.domain.model.GattConnectionState
import com.radustavila.myblesense.domain.model.HardwareEvent
import com.radustavila.myblesense.domain.model.TouchIntensityStats
import com.radustavila.myblesense.domain.model.ManufacturerData
import com.radustavila.myblesense.domain.model.ReconnectionStatus
import com.radustavila.myblesense.domain.model.ServiceData
import org.junit.Test
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds

class DeviceDetailsMapperTest {

    private fun device(
        name: String? = "Venu Sq",
        txPowerDbm: Int? = 4,
        isConnectable: Boolean? = true,
        manufacturerData: List<ManufacturerData> = emptyList(),
        serviceData: List<ServiceData> = emptyList(),
    ) = BleDevice(
        id = "AA:BB:CC:DD:EE:FF",
        address = "AA:BB:CC:DD:EE:FF",
        name = name,
        rssi = -52,
        txPowerDbm = txPowerDbm,
        isConnectable = isConnectable,
        serviceUuids = listOf("180D"),
        manufacturerData = manufacturerData,
        serviceData = serviceData,
        seenCount = 7,
    )

    @Test
    fun `maps the values shown on the details screen`() {
        val ui = device().toDetailsUi()

        assertThat(ui.name).isEqualTo("Venu Sq")
        assertThat(ui.isNameKnown).isTrue()
        assertThat(ui.rssiDbm).isEqualTo(-52)
        assertThat(ui.signalLabel).isEqualTo("Strong")
        assertThat(ui.txPower).isEqualTo("4 dBm")
        assertThat(ui.connectable).isEqualTo("Yes")
        assertThat(ui.serviceUuids).containsExactly("180D")
        assertThat(ui.advertisementCount).isEqualTo(7)
    }

    @Test
    fun `exposes the hardware address as advertised`() {
        val ui = device().toDetailsUi()

        assertThat(ui.address).isEqualTo("AA:BB:CC:DD:EE:FF")
    }

    @Test
    fun `falls back to a placeholder when the device advertises no name`() {
        val ui = device(name = null).toDetailsUi()

        assertThat(ui.name).isEqualTo("Unknown device")
        assertThat(ui.isNameKnown).isFalse()
    }

    @Test
    fun `missing optional values map to null instead of fake data`() {
        val ui = device(txPowerDbm = null, isConnectable = null).toDetailsUi()

        assertThat(ui.txPower).isNull()
        assertThat(ui.connectable).isNull()
    }

    @Test
    fun `manufacturer payloads are shown as hex and never decoded`() {
        val ui = device(
            manufacturerData = listOf(
                ManufacturerData(
                    companyId = 0x004C,
                    companyName = "Apple",
                    payload = byteArrayOf(0x02, 0x15, 0xFF.toByte()),
                ),
            ),
        ).toDetailsUi()

        val data = ui.manufacturerData.single()
        assertThat(data.companyId).isEqualTo("0x004C")
        assertThat(data.companyName).isEqualTo("Apple")
        assertThat(data.payloadHex).isEqualTo("02 15 FF")
        assertThat(data.payloadSize).isEqualTo(3)
    }

    @Test
    fun `unknown company identifiers stay labelled as unknown`() {
        val ui = device(
            manufacturerData = listOf(
                ManufacturerData(companyId = 0xFFFF, companyName = null, payload = ByteArray(0)),
            ),
        ).toDetailsUi()

        assertThat(ui.manufacturerData.single().companyName).isEqualTo("Unknown company")
        assertThat(ui.manufacturerData.single().payloadHex).isEmpty()
    }

    @Test
    fun `service data payloads are formatted as hex`() {
        val ui = device(
            serviceData = listOf(ServiceData(uuid = "FE9F", payload = byteArrayOf(0x01, 0x0A))),
        ).toDetailsUi()

        val data = ui.serviceData.single()
        assertThat(data.uuid).isEqualTo("FE9F")
        assertThat(data.payloadHex).isEqualTo("01 0A")
    }

    // --- Hardware events --------------------------------------------------------------

    private val utc = ZoneOffset.UTC

    @Test
    fun `touch durations are shown with one decimal of a second`() {
        assertThat(formatTouchDuration(1_800)).isEqualTo("1.8s")
        assertThat(formatTouchDuration(1_849)).isEqualTo("1.8s")
        assertThat(formatTouchDuration(12_000)).isEqualTo("12.0s")
        assertThat(formatTouchDuration(40)).isEqualTo("<0.1s")
        assertThat(formatTouchDuration(null)).isEqualTo("unknown")
    }

    @Test
    fun `intensities are shown as whole percentages`() {
        assertThat(formatIntensity(94)).isEqualTo("94%")
        assertThat(formatIntensity(0)).isEqualTo("0%")
        assertThat(formatIntensity(null)).isEqualTo("—")
    }

    private fun HardwareEventsUiState.after(vararg events: HardwareEvent): HardwareEventsUiState =
        events.fold(this) { state, event -> state.plus(event, sequence = event.timestampMillis, zone = utc) }

    @Test
    fun `a press opens an active touch entry`() {
        val pressed = HardwareEventsUiState().after(HardwareEvent.TouchPressed(3_723_456))

        assertThat(pressed.isTouchPressed).isTrue()
        assertThat(pressed.lastTouch).isNull()
        val touch = pressed.entries.single() as HardwareEventEntryUi.Touch
        assertThat(touch).isEqualTo(
            HardwareEventEntryUi.Touch(key = 3_723_456, pressedAt = "01:02:03.456", isActive = true),
        )
        assertThat(touch.releasedLabel).isEqualTo("—")
        assertThat(touch.releaseStatus).isEqualTo("Holding…")
    }

    @Test
    fun `a release completes its press into one touch entry`() {
        val stats = TouchIntensityStats(peakPercent = 94, averagePercent = 81, sampleCount = 24)
        val released = HardwareEventsUiState().after(
            HardwareEvent.TouchPressed(1_000),
            HardwareEvent.TouchIntensity(2_300, 70),
            HardwareEvent.TouchReleased(3_400, 2_400, stats),
        )

        assertThat(released.entries).containsExactly(
            HardwareEventEntryUi.Touch(
                key = 1_000,
                pressedAt = "00:00:01.000",
                releasedAt = "00:00:03.400",
                duration = "2.4s",
                intensity = "Peak 94% · Avg 81%",
            ),
        )
        assertThat(released.isTouchPressed).isFalse()
        assertThat(released.liveIntensityPercent).isNull()
        assertThat(released.liveIntensityProgress).isEqualTo(0f)
        assertThat(released.lastTouch).isEqualTo(
            TouchStatsUi(duration = "Duration: 2.4s", peak = "Peak: 94%", average = "Average: 81%"),
        )
    }

    @Test
    fun `consecutive touches are separate entries in chronological order`() {
        val state = HardwareEventsUiState().after(
            HardwareEvent.TouchPressed(0),
            HardwareEvent.TouchReleased(500, 500),
            HardwareEvent.Other("EVENT 1", 600),
            HardwareEvent.TouchPressed(1_000),
            HardwareEvent.TouchReleased(2_800, 1_800),
        )

        assertThat(state.entries.map { it.key }).containsExactly(0L, 600L, 1_000L).inOrder()
        assertThat(state.entries.filterIsInstance<HardwareEventEntryUi.Touch>().map { it.duration })
            .containsExactly("0.5s", "1.8s").inOrder()
        assertThat(state.lastTouch?.duration).isEqualTo("Duration: 1.8s")
    }

    @Test
    fun `an event received while touching does not split the touch`() {
        val state = HardwareEventsUiState().after(
            HardwareEvent.TouchPressed(0),
            HardwareEvent.Other("EVENT 1", 100),
            HardwareEvent.TouchReleased(500, 500),
        )

        assertThat(state.entries).hasSize(2)
        val touch = state.entries.first() as HardwareEventEntryUi.Touch
        assertThat(touch.releasedAt).isEqualTo("00:00:00.500")
        assertThat(state.entries.last())
            .isEqualTo(HardwareEventEntryUi.Single(key = 100, type = "EVENT 1", timestamp = "00:00:00.100"))
    }

    @Test
    fun `a release without an observed press gets an entry of its own`() {
        val released = HardwareEventsUiState().after(HardwareEvent.TouchReleased(2_400, null))

        val touch = released.entries.single() as HardwareEventEntryUi.Touch
        assertThat(touch.pressedAt).isNull()
        assertThat(touch.pressedLabel).isEqualTo("—")
        assertThat(touch.duration).isEqualTo("unknown")
        assertThat(touch.intensity).isNull()
        assertThat(released.lastTouch).isEqualTo(
            TouchStatsUi(duration = "Duration: unknown", peak = "Peak: —", average = "Average: —"),
        )
    }

    @Test
    fun `a new press ends a touch whose release was lost`() {
        val state = HardwareEventsUiState().after(
            HardwareEvent.TouchPressed(0),
            HardwareEvent.TouchPressed(1_000),
        )

        val (lost, current) = state.entries.map { it as HardwareEventEntryUi.Touch }
        assertThat(lost.isActive).isFalse()
        assertThat(lost.releaseStatus).isEqualTo("Not received")
        assertThat(current.isActive).isTrue()
    }

    @Test
    fun `ending the active touch keeps it in the history without a release`() {
        val ended = HardwareEventsUiState()
            .after(HardwareEvent.TouchPressed(0), HardwareEvent.TouchIntensity(100, 60))
            .endActiveTouch()

        assertThat(ended.isTouchPressed).isFalse()
        assertThat(ended.liveIntensityPercent).isNull()
        assertThat((ended.entries.single() as HardwareEventEntryUi.Touch).releaseStatus)
            .isEqualTo("Not received")

        val nextRelease = ended.after(HardwareEvent.TouchReleased(900, null))
        assertThat(nextRelease.entries).hasSize(2)
    }

    @Test
    fun `intensity samples update the live indicator without adding entries`() {
        val pressed = HardwareEventsUiState().after(HardwareEvent.TouchPressed(0))
        assertThat(pressed.liveIntensityLabel).isEqualTo("Intensity: —")
        assertThat(pressed.liveIntensityProgress).isEqualTo(0f)

        val sampled = pressed.after(
            HardwareEvent.TouchIntensity(100, 40),
            HardwareEvent.TouchIntensity(200, 94),
        )

        assertThat(sampled.liveIntensityPercent).isEqualTo(94)
        assertThat(sampled.liveIntensityLabel).isEqualTo("Intensity: 94%")
        assertThat(sampled.liveIntensityProgress).isWithin(1e-6f).of(0.94f)
        assertThat(sampled.entries).hasSize(1)
    }

    @Test
    fun `a sample without an observed press still shows the touch as held`() {
        val sampled = HardwareEventsUiState().after(HardwareEvent.TouchIntensity(0, 55))

        assertThat(sampled.isTouchPressed).isTrue()
        assertThat(sampled.liveIntensityPercent).isEqualTo(55)
        assertThat(sampled.entries).isEmpty()
    }

    @Test
    fun `unknown events keep their raw text`() {
        val state = HardwareEventsUiState().after(
            HardwareEvent.Other("EVENT 3", 0),
            HardwareEvent.Other("", 1),
        )

        assertThat(state.entries.map { (it as HardwareEventEntryUi.Single).type })
            .containsExactly("EVENT 3", "(empty)").inOrder()
    }

    @Test
    fun `the oldest entry is dropped once the limit is reached`() {
        var state = HardwareEventsUiState()
        repeat(MAX_HARDWARE_EVENTS + 1) { i ->
            state = state.plus(HardwareEvent.Other("EVENT ${i + 1}", 0), i.toLong() + 1, utc)
        }

        val types = state.entries.map { (it as HardwareEventEntryUi.Single).type }
        assertThat(types).hasSize(MAX_HARDWARE_EVENTS)
        assertThat(types.first()).isEqualTo("EVENT 2")
        assertThat(types.last()).isEqualTo("EVENT ${MAX_HARDWARE_EVENTS + 1}")
        assertThat(state.isAtLimit).isTrue()
    }

    // --- Connection status ------------------------------------------------------------

    private val deviceId = "AA:BB:CC:DD:EE:FF"

    private fun status(
        connection: GattConnection,
        isConnectRequested: Boolean = false,
        reconnection: ReconnectionStatus? = null,
    ) = connection.toConnectionStatus(deviceId, isConnectRequested, reconnection)

    @Test
    fun `the connection status follows the link lifecycle of this device`() {
        assertThat(status(GattConnection.Disconnected)).isEqualTo(ConnectionStatus.DISCONNECTED)
        assertThat(status(GattConnection.Disconnected, isConnectRequested = true))
            .isEqualTo(ConnectionStatus.CONNECTING)
        assertThat(status(GattConnection(deviceId, GattConnectionState.CONNECTING)))
            .isEqualTo(ConnectionStatus.CONNECTING)
        assertThat(status(GattConnection(deviceId, GattConnectionState.CONNECTED)))
            .isEqualTo(ConnectionStatus.CONNECTING)
        assertThat(
            status(GattConnection(deviceId, GattConnectionState.CONNECTED, servicesDiscovered = true)),
        ).isEqualTo(ConnectionStatus.CONNECTED)
        assertThat(status(GattConnection(deviceId, GattConnectionState.DISCONNECTING)))
            .isEqualTo(ConnectionStatus.DISCONNECTING)
    }

    @Test
    fun `services still being discovered are called out`() {
        val discovering = GattConnection(deviceId, GattConnectionState.CONNECTED)

        assertThat(discovering.isDiscoveringServices(deviceId)).isTrue()
        assertThat(
            ConnectionUiState(status = ConnectionStatus.CONNECTING, isDiscoveringServices = true)
                .statusLabel,
        ).isEqualTo("Discovering services…")
    }

    @Test
    fun `a reconnection in progress wins over the raw link state`() {
        val waiting = ReconnectionStatus.Waiting(2, 3, 2.seconds)

        assertThat(status(GattConnection.Disconnected, reconnection = waiting))
            .isEqualTo(ConnectionStatus.RECONNECTING)
        assertThat(waiting.toUiOrNull()).isEqualTo(ReconnectionUi(attempt = 2, maxAttempts = 3))
        assertThat(ReconnectionStatus.Reconnected(2, 3).toUiOrNull()).isNull()
    }

    @Test
    fun `a link owned by another device is disconnected from this screen`() {
        val other = GattConnection("11:22:33:44:55:66", GattConnectionState.CONNECTED, true)

        assertThat(status(other)).isEqualTo(ConnectionStatus.DISCONNECTED)
    }

    @Test
    fun `drops a touch held for less than 0_1s`() {
        val ui = HardwareEventsUiState()
            .plus(HardwareEvent.TouchPressed(1_000), sequence = 1, zone = ZoneOffset.UTC)
            .plus(HardwareEvent.TouchReleased(1_050, durationMillis = 50), sequence = 2, zone = ZoneOffset.UTC)

        assertThat(ui.entries).isEmpty()
        assertThat(ui.lastTouch).isNull()
        assertThat(ui.isTouchPressed).isFalse()
    }

    @Test
    fun `keeps a touch held for exactly 0_1s`() {
        val ui = HardwareEventsUiState()
            .plus(HardwareEvent.TouchPressed(1_000), sequence = 1, zone = ZoneOffset.UTC)
            .plus(HardwareEvent.TouchReleased(1_100, durationMillis = 100), sequence = 2, zone = ZoneOffset.UTC)

        assertThat(ui.entries).hasSize(1)
    }
}
