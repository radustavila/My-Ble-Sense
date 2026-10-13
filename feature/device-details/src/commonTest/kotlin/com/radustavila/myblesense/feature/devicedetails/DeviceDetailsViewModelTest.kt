package com.radustavila.myblesense.feature.devicedetails

import androidx.lifecycle.SavedStateHandle
import com.radustavila.myblesense.domain.testing.assertThat
import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.BluetoothState
import com.radustavila.myblesense.domain.model.GattCharacteristicProperty
import com.radustavila.myblesense.domain.model.KnownDevice
import com.radustavila.myblesense.domain.model.PocProfile
import com.radustavila.myblesense.domain.model.findPocService
import com.radustavila.myblesense.domain.model.GattWriteType
import com.radustavila.myblesense.domain.session.InMemoryScanSessionStore
import com.radustavila.myblesense.domain.testing.FakeBleGattClient.RecordedWrite
import com.radustavila.myblesense.domain.testing.FakeBleGattRepository
import com.radustavila.myblesense.domain.testing.FakeBleScanRepository
import com.radustavila.myblesense.domain.testing.FakeKnownDeviceRepository
import com.radustavila.myblesense.domain.testing.MainDispatcherRule
import com.radustavila.myblesense.domain.testing.POC_COMMAND_ID
import com.radustavila.myblesense.domain.testing.POC_EVENTS_ID
import com.radustavila.myblesense.domain.testing.POC_STATUS_ID
import com.radustavila.myblesense.domain.testing.bleDevice
import com.radustavila.myblesense.domain.testing.gattCharacteristic
import com.radustavila.myblesense.domain.testing.gattService
import com.radustavila.myblesense.domain.testing.pocGattService
import com.radustavila.myblesense.domain.usecase.ConnectToDeviceUseCase
import com.radustavila.myblesense.domain.usecase.ObserveCharacteristicValuesUseCase
import com.radustavila.myblesense.domain.usecase.DisconnectFromDeviceUseCase
import com.radustavila.myblesense.domain.usecase.ObserveBluetoothStateUseCase
import com.radustavila.myblesense.domain.usecase.ObserveGattConnectionUseCase
import com.radustavila.myblesense.domain.usecase.ObserveGattErrorsUseCase
import com.radustavila.myblesense.domain.usecase.ObserveKnownDeviceUseCase
import com.radustavila.myblesense.domain.usecase.ObserveNotifyingCharacteristicsUseCase
import com.radustavila.myblesense.domain.usecase.ObservePocEventsUseCase
import com.radustavila.myblesense.domain.usecase.ObservePocHardwareEventsUseCase
import com.radustavila.myblesense.domain.usecase.ObservePocServiceUseCase
import com.radustavila.myblesense.domain.usecase.ObserveScannedDeviceUseCase
import com.radustavila.myblesense.domain.usecase.ReadCharacteristicUseCase
import com.radustavila.myblesense.domain.usecase.ReadPocStatusUseCase
import com.radustavila.myblesense.domain.usecase.ReconnectToDeviceUseCase
import com.radustavila.myblesense.domain.usecase.SendPocCommandUseCase
import com.radustavila.myblesense.domain.usecase.SetCharacteristicNotificationsUseCase
import com.radustavila.myblesense.domain.usecase.SetPocEventsNotificationsUseCase
import com.radustavila.myblesense.domain.usecase.WriteCharacteristicUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

private const val DEVICE_ID = "AA:BB:CC:DD:EE:FF"

class DeviceDetailsViewModelTest {

    private val mainDispatcherRule = MainDispatcherRule()

    @BeforeTest
    fun installMainDispatcher() = mainDispatcherRule.install()

    @AfterTest
    fun resetMainDispatcher() = mainDispatcherRule.reset()

    private val repository = FakeBleGattRepository()
    private val client = repository.client
    private val sessionStore = InMemoryScanSessionStore()
    private val scanRepository = FakeBleScanRepository()
    private val knownDevices = FakeKnownDeviceRepository()

    @Test
    fun `shows the device from the scan session and no connection`() = runTest {
        sessionStore.publish(listOf(bleDevice(id = DEVICE_ID, name = "Venu Sq")))

        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertThat(state.device?.name).isEqualTo("Venu Sq")
        assertThat(state.connection.statusLabel).isEqualTo("Disconnected")
        assertThat(state.connection.canConnect).isTrue()
        assertThat(state.connection.canOpenInspector).isFalse()
    }

    @Test
    fun `connecting moves through connecting to connected`() = runTest {
        client.connectDelayMillis = 500
        client.servicesOnConnect = listOf(gattService())
        val viewModel = createViewModel()

        viewModel.onEvent(DeviceDetailsEvent.Connect)

        advanceTimeBy(100)
        assertThat(viewModel.uiState.value.connection.statusLabel).isEqualTo("Connecting…")

        advanceTimeBy(500)
        val connection = viewModel.uiState.value.connection
        assertThat(connection.status).isEqualTo(ConnectionStatus.CONNECTED)
        assertThat(connection.statusLabel).isEqualTo("Connected")
        assertThat(connection.isConnectedToThisDevice).isTrue()
        assertThat(connection.canOpenInspector).isTrue()
    }

    @Test
    fun `a failed connection is reported and the inspector stays unreachable`() = runTest {
        client.connectFailure = BleError.ConnectionFailed(status = 133)
        val viewModel = createViewModel()

        viewModel.onEvent(DeviceDetailsEvent.Connect)

        val state = viewModel.uiState.value
        assertThat(state.connection.message).isEqualTo("Could not connect to the device.")
        assertThat(state.connection.canOpenInspector).isFalse()
        assertThat(state.connection.canConnect).isTrue()
    }

    @Test
    fun `a connection timeout says the device may be out of range`() = runTest {
        client.connectFailure = BleError.ConnectionTimeout
        val viewModel = createViewModel()

        viewModel.onEvent(DeviceDetailsEvent.Connect)

        assertThat(viewModel.uiState.value.connection.message).contains("out of range")
    }

    @Test
    fun `connecting twice does not open a second connection`() = runTest {
        val viewModel = createViewModel()

        viewModel.onEvent(DeviceDetailsEvent.Connect)
        viewModel.onEvent(DeviceDetailsEvent.Connect)

        assertThat(client.connectCount).isEqualTo(1)
    }

    @Test
    fun `disconnecting releases the connection`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)

        viewModel.onEvent(DeviceDetailsEvent.Disconnect)

        assertThat(client.disconnectCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.connection.isConnectedToThisDevice).isFalse()
    }

    @Test
    fun `losing the link is reported and a reconnection starts`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)

        client.simulateUnexpectedDisconnect(BleError.Disconnected)

        val connection = viewModel.uiState.value.connection
        assertThat(connection.message).isEqualTo("The device disconnected.")
        assertThat(connection.status).isEqualTo(ConnectionStatus.RECONNECTING)
        assertThat(connection.statusLabel).isEqualTo("Reconnecting… (attempt 1 of 3)")
        assertThat(connection.hint).isEqualTo("The device disconnected.")
        assertThat(connection.canConnect).isFalse()
        assertThat(connection.canDisconnect).isTrue()
    }

    @Test
    fun `a connection to another device does not look like this one`() = runTest {
        val viewModel = createViewModel()

        client.connect("11:22:33:44:55:66")

        val connection = viewModel.uiState.value.connection
        assertThat(connection.status).isEqualTo(ConnectionStatus.DISCONNECTED)
        assertThat(connection.isConnectedToThisDevice).isFalse()
        assertThat(connection.canOpenInspector).isFalse()
    }

    @Test
    fun `leaving the screen closes the connection`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)

        viewModel.releaseConnection()

        assertThat(client.disconnectCount).isEqualTo(1)
    }

    @Test
    fun `the known device is shown when the scan session does not have it`() = runTest {
        knownDevices.save(KnownDevice(DEVICE_ID, "ESP32-BLE-POC"))

        val viewModel = createViewModel()

        val device = viewModel.uiState.value.device
        assertThat(viewModel.uiState.value.isMissing).isFalse()
        assertThat(device?.name).isEqualTo("ESP32-BLE-POC")
        assertThat(device?.rssiLabel).isEqualTo("Not seen in the current scan")
        assertThat(viewModel.uiState.value.connection.canConnect).isTrue()
    }

    @Test
    fun `the scan session wins over the known device`() = runTest {
        knownDevices.save(KnownDevice(DEVICE_ID, "ESP32"))
        sessionStore.publish(listOf(bleDevice(id = DEVICE_ID, name = "ESP32-BLE-POC", rssi = -50)))

        val device = createViewModel().uiState.value.device

        assertThat(device?.name).isEqualTo("ESP32-BLE-POC")
        assertThat(device?.rssiDbm).isEqualTo(-50)
    }

    @Test
    fun `a known device that is another one is not shown`() = runTest {
        knownDevices.save(KnownDevice("11:22:33:44:55:66", "Venu Sq"))

        assertThat(createViewModel().uiState.value.isMissing).isTrue()
    }

    @Test
    fun `leaving the screen keeps a link it did not open`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        // Opened by the device list, which connects to the known device on its own.
        client.connect(DEVICE_ID)
        val viewModel = createViewModel()
        assertThat(viewModel.uiState.value.connection.isConnectedToThisDevice).isTrue()

        viewModel.releaseConnection()

        assertThat(client.disconnectCount).isEqualTo(0)
        // Its own subscription is still switched off, as the list knows nothing about it.
        assertThat(client.notificationChanges.last()).isEqualTo(POC_EVENTS_ID to false)
    }

    @Test
    fun `leaving the screen without a connection does nothing`() = runTest {
        val viewModel = createViewModel()

        viewModel.releaseConnection()

        assertThat(client.disconnectCount).isEqualTo(0)
    }

    // --- ESP32 POC service ----------------------------------------------------------------

    @Test
    fun `connecting to the poc device reads its status right away`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        client.readValues[POC_STATUS_ID] = "ONLINE".encodeToByteArray()
        val viewModel = createViewModel()

        viewModel.onEvent(DeviceDetailsEvent.Connect)

        val poc = viewModel.uiState.value.poc
        assertThat(poc).isNotNull()
        assertThat(poc!!.status).isEqualTo("ONLINE")
        assertThat(poc.hasStatus && poc.hasCommand && poc.hasEvents).isTrue()
        assertThat(poc.isReadStatusEnabled).isTrue()
    }

    @Test
    fun `read status fetches the latest value`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        client.readValues[POC_STATUS_ID] = "BOOTING".encodeToByteArray()
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)

        client.readValues[POC_STATUS_ID] = "ONLINE".encodeToByteArray()
        viewModel.onEvent(DeviceDetailsEvent.ReadPocStatus)

        assertThat(viewModel.uiState.value.poc?.status).isEqualTo("ONLINE")
    }

    @Test
    fun `a failed status read is reported and can be retried`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        client.readFailure = BleError.CharacteristicReadFailed(status = 2)
        val viewModel = createViewModel()

        viewModel.onEvent(DeviceDetailsEvent.Connect)

        val state = viewModel.uiState.value
        assertThat(state.connection.message).isEqualTo("Read failed.")
        assertThat(state.poc?.status).isNull()
        assertThat(state.poc?.isReadStatusEnabled).isTrue()
    }

    @Test
    fun `a device without the poc service shows no poc section`() = runTest {
        client.servicesOnConnect = listOf(gattService())
        val viewModel = createViewModel()

        viewModel.onEvent(DeviceDetailsEvent.Connect)

        assertThat(viewModel.uiState.value.connection.isConnectedToThisDevice).isTrue()
        assertThat(viewModel.uiState.value.poc).isNull()
    }

    @Test
    fun `losing the link clears the poc section`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        client.readValues[POC_STATUS_ID] = "ONLINE".encodeToByteArray()
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)

        client.simulateUnexpectedDisconnect()

        assertThat(viewModel.uiState.value.poc).isNull()
    }

    @Test
    fun `the poc service of another connected device is not shown here`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        val viewModel = createViewModel()

        client.connect("11:22:33:44:55:66")

        assertThat(viewModel.uiState.value.poc).isNull()
    }

    @Test
    fun `send ping writes PING to the command characteristic and reports success`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        client.writeDelayMillis = 200
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)
        assertThat(viewModel.uiState.value.poc?.commandState).isEqualTo(PocCommandState.Idle)
        assertThat(viewModel.uiState.value.poc?.isSendPingEnabled).isTrue()

        viewModel.onEvent(DeviceDetailsEvent.SendPocPing)

        advanceTimeBy(100)
        val sending = viewModel.uiState.value.poc!!
        assertThat(sending.commandState).isEqualTo(PocCommandState.Sending)
        assertThat(sending.commandStatusLabel).isEqualTo("Sending…")
        assertThat(sending.isSendPingEnabled).isFalse()

        advanceTimeBy(200)
        val sent = viewModel.uiState.value.poc!!
        assertThat(sent.commandState).isEqualTo(PocCommandState.Sent)
        assertThat(sent.commandStatusLabel).isEqualTo("Command sent")
        assertThat(sent.isSendPingEnabled).isTrue()
        assertThat(client.writes).containsExactly(
            RecordedWrite(POC_COMMAND_ID, "PING".encodeToByteArray(), GattWriteType.WITH_RESPONSE),
        )
    }

    @Test
    fun `tapping send ping twice while sending writes once`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        client.writeDelayMillis = 200
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)

        viewModel.onEvent(DeviceDetailsEvent.SendPocPing)
        viewModel.onEvent(DeviceDetailsEvent.SendPocPing)
        advanceTimeBy(500)

        assertThat(client.writes).hasSize(1)
    }

    @Test
    fun `a failed ping shows a friendly error and can be retried`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        client.writeFailure = BleError.CharacteristicWriteFailed(status = 3)
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)

        viewModel.onEvent(DeviceDetailsEvent.SendPocPing)

        val failed = viewModel.uiState.value.poc!!
        assertThat(failed.commandState)
            .isEqualTo(PocCommandState.Failed("Could not send command: Write failed."))
        assertThat(failed.commandStatusLabel).isEqualTo("Could not send command: Write failed.")
        assertThat(failed.isSendPingEnabled).isTrue()

        client.writeFailure = null
        viewModel.onEvent(DeviceDetailsEvent.SendPocPing)

        assertThat(viewModel.uiState.value.poc?.commandState).isEqualTo(PocCommandState.Sent)
    }

    @Test
    fun `a timed out ping says the device did not answer`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        client.writeFailure = BleError.GattOperationTimeout
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)

        viewModel.onEvent(DeviceDetailsEvent.SendPocPing)

        assertThat(viewModel.uiState.value.poc?.commandStatusLabel)
            .isEqualTo("Could not send command: The device did not answer in time.")
    }

    @Test
    fun `send ping is unavailable when the command characteristic is missing`() = runTest {
        client.servicesOnConnect = listOf(
            pocGattService(
                characteristics = listOf(
                    gattCharacteristic(
                        serviceUuid = PocProfile.SERVICE_UUID,
                        characteristicUuid = PocProfile.STATUS_UUID,
                        properties = setOf(GattCharacteristicProperty.READ),
                    ),
                ),
            ),
        )
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)

        viewModel.onEvent(DeviceDetailsEvent.SendPocPing)

        val poc = viewModel.uiState.value.poc!!
        assertThat(poc.isSendPingEnabled).isFalse()
        assertThat(poc.commandStatusLabel).isEqualTo("Command characteristic not found")
        assertThat(client.writes).isEmpty()
    }

    @Test
    fun `losing the link while sending clears the poc section`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        client.writeDelayMillis = 200
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)
        viewModel.onEvent(DeviceDetailsEvent.SendPocPing)

        advanceTimeBy(100)
        client.simulateUnexpectedDisconnect()
        advanceTimeBy(200)

        assertThat(viewModel.uiState.value.poc).isNull()
        assertThat(viewModel.uiState.value.connection.message).isEqualTo("The device disconnected.")
    }

    @Test
    fun `the command outcome survives a re-emission of the gatt database`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)
        viewModel.onEvent(DeviceDetailsEvent.SendPocPing)

        val remapped = listOf(pocGattService()).findPocService()!!
            .toUi(previous = viewModel.uiState.value.poc)

        assertThat(remapped.commandState).isEqualTo(PocCommandState.Sent)
    }

    // --- ESP32 POC events ------------------------------------------------------------------

    @Test
    fun `connecting to the poc device subscribes to its events`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        val viewModel = createViewModel()

        viewModel.onEvent(DeviceDetailsEvent.Connect)

        val poc = viewModel.uiState.value.poc!!
        assertThat(client.notificationChanges).containsExactly(POC_EVENTS_ID to true)
        assertThat(poc.isEventsNotifying).isTrue()
        assertThat(poc.isSubscribingEvents).isFalse()
        assertThat(poc.eventsStatusLabel).isEqualTo("Listening for events")
        assertThat(poc.isSubscribeEventsEnabled).isFalse()
        assertThat(viewModel.uiState.value.hardwareEvents.entries).isEmpty()
        assertThat(viewModel.uiState.value.showsHardwareEvents).isTrue()
    }

    @Test
    fun `incoming events are shown live in arrival order`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)

        client.emitNotification(POC_EVENTS_ID, "EVENT 1".encodeToByteArray())
        client.emitNotification(POC_EVENTS_ID, "EVENT 2".encodeToByteArray())
        client.emitNotification(POC_EVENTS_ID, "EVENT 3".encodeToByteArray())

        assertThat(viewModel.uiState.value.hardwareEvents.summary())
            .containsExactly("EVENT 1", "EVENT 2", "EVENT 3").inOrder()
    }

    @Test
    fun `only the latest events are kept`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)

        repeat(MAX_HARDWARE_EVENTS + 5) { i ->
            client.emitNotification(POC_EVENTS_ID, "EVENT ${i + 1}".encodeToByteArray())
        }

        val events = viewModel.uiState.value.hardwareEvents.summary()
        assertThat(events).hasSize(MAX_HARDWARE_EVENTS)
        assertThat(events.first()).isEqualTo("EVENT 6")
        assertThat(events.last()).isEqualTo("EVENT ${MAX_HARDWARE_EVENTS + 5}")
    }

    @Test
    fun `touch duration is measured from TOUCH_ON to TOUCH_OFF`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)

        client.currentTimeMillis = 1_000
        client.emitNotification(POC_EVENTS_ID, "TOUCH_ON".encodeToByteArray())
        assertThat(viewModel.uiState.value.hardwareEvents.isTouchPressed).isTrue()

        client.currentTimeMillis = 2_800
        client.emitNotification(POC_EVENTS_ID, "TOUCH_OFF".encodeToByteArray())

        val section = viewModel.uiState.value.hardwareEvents
        assertThat(section.isTouchPressed).isFalse()
        assertThat(section.lastTouch?.duration).isEqualTo("Duration: 1.8s")
        assertThat(section.summary()).containsExactly("TOUCH 1.8s")
    }

    @Test
    fun `touch intensity is shown live and summarised on release`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)

        client.currentTimeMillis = 1_000
        client.emitNotification(POC_EVENTS_ID, "TOUCH_ON".encodeToByteArray())
        listOf(60, 94, 88, 82).forEach { percent ->
            client.currentTimeMillis += 100
            client.emitNotification(POC_EVENTS_ID, "TOUCH_INTENSITY:$percent".encodeToByteArray())
        }

        val live = viewModel.uiState.value.hardwareEvents
        assertThat(live.liveIntensityPercent).isEqualTo(82)
        assertThat(live.liveIntensityLabel).isEqualTo("Intensity: 82%")
        assertThat(live.summary()).containsExactly("TOUCH Holding…")

        client.currentTimeMillis = 3_400
        client.emitNotification(POC_EVENTS_ID, "TOUCH_OFF".encodeToByteArray())

        val section = viewModel.uiState.value.hardwareEvents
        assertThat(section.liveIntensityPercent).isNull()
        assertThat(section.lastTouch).isEqualTo(
            TouchStatsUi(duration = "Duration: 2.4s", peak = "Peak: 94%", average = "Average: 81%"),
        )
        val touch = section.entries.single() as HardwareEventEntryUi.Touch
        assertThat(touch.duration).isEqualTo("2.4s")
        assertThat(touch.intensity).isEqualTo("Peak 94% · Avg 81%")
    }

    @Test
    fun `the live intensity is cleared on disconnect but the history stays`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)
        client.emitNotification(POC_EVENTS_ID, "TOUCH_ON".encodeToByteArray())
        client.emitNotification(POC_EVENTS_ID, "TOUCH_INTENSITY:70".encodeToByteArray())

        viewModel.onEvent(DeviceDetailsEvent.Disconnect)

        val section = viewModel.uiState.value.hardwareEvents
        assertThat(section.isTouchPressed).isFalse()
        assertThat(section.liveIntensityPercent).isNull()
        assertThat(section.summary()).containsExactly("TOUCH Not received")
    }

    @Test
    fun `a release whose press was not seen has an unknown duration`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)

        client.emitNotification(POC_EVENTS_ID, "TOUCH_OFF".encodeToByteArray())

        assertThat(viewModel.uiState.value.hardwareEvents.lastTouch?.duration)
            .isEqualTo("Duration: unknown")
    }

    @Test
    fun `a failed subscription is reported and can be retried`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        client.notificationFailure = BleError.NotificationEnableFailed(status = 5)
        val viewModel = createViewModel()

        viewModel.onEvent(DeviceDetailsEvent.Connect)

        val failed = viewModel.uiState.value.poc!!
        assertThat(failed.isEventsNotifying).isFalse()
        assertThat(failed.eventsStatusLabel)
            .isEqualTo("Could not subscribe to events: Could not change the notification setting.")
        assertThat(failed.isEventsStatusError).isTrue()
        assertThat(failed.isSubscribeEventsEnabled).isTrue()

        client.notificationFailure = null
        viewModel.onEvent(DeviceDetailsEvent.SubscribePocEvents)

        val retried = viewModel.uiState.value.poc!!
        assertThat(retried.isEventsNotifying).isTrue()
        assertThat(retried.eventsError).isNull()
        assertThat(retried.eventsStatusLabel).isEqualTo("Listening for events")
    }

    @Test
    fun `a subscription switched off elsewhere can be restored`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)

        // e.g. from the GATT inspector, which shares the connection
        client.setNotificationsEnabled(POC_EVENTS_ID, enabled = false)

        val off = viewModel.uiState.value.poc!!
        assertThat(off.eventsStatusLabel).isEqualTo("Not subscribed")
        assertThat(off.isSubscribeEventsEnabled).isTrue()

        viewModel.onEvent(DeviceDetailsEvent.SubscribePocEvents)

        assertThat(viewModel.uiState.value.poc?.isEventsNotifying).isTrue()
    }

    @Test
    fun `an events characteristic without notify cannot be subscribed to`() = runTest {
        client.servicesOnConnect = listOf(
            pocGattService(
                characteristics = listOf(
                    gattCharacteristic(
                        serviceUuid = PocProfile.SERVICE_UUID,
                        characteristicUuid = PocProfile.EVENTS_UUID,
                        properties = setOf(GattCharacteristicProperty.READ),
                    ),
                ),
            ),
        )
        val viewModel = createViewModel()

        viewModel.onEvent(DeviceDetailsEvent.Connect)
        viewModel.onEvent(DeviceDetailsEvent.SubscribePocEvents)

        val poc = viewModel.uiState.value.poc!!
        assertThat(poc.eventsStatusLabel)
            .isEqualTo("Events characteristic does not support notifications")
        assertThat(poc.isSubscribeEventsEnabled).isFalse()
        assertThat(client.notificationChanges).isEmpty()
    }

    @Test
    fun `a device without the events characteristic says so and never subscribes`() = runTest {
        client.servicesOnConnect = listOf(
            pocGattService(
                characteristics = listOf(
                    gattCharacteristic(
                        serviceUuid = PocProfile.SERVICE_UUID,
                        characteristicUuid = PocProfile.STATUS_UUID,
                        properties = setOf(GattCharacteristicProperty.READ),
                    ),
                ),
            ),
        )
        val viewModel = createViewModel()

        viewModel.onEvent(DeviceDetailsEvent.Connect)

        assertThat(viewModel.uiState.value.poc?.eventsStatusLabel)
            .isEqualTo("Events characteristic not found")
        assertThat(client.notificationChanges).isEmpty()
    }

    @Test
    fun `disconnecting unsubscribes before closing the link`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)

        viewModel.onEvent(DeviceDetailsEvent.Disconnect)

        assertThat(client.notificationChanges)
            .containsExactly(POC_EVENTS_ID to true, POC_EVENTS_ID to false).inOrder()
        assertThat(client.disconnectCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.poc).isNull()
    }

    @Test
    fun `leaving the screen unsubscribes and closes the connection`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)

        viewModel.releaseConnection()

        assertThat(client.notificationChanges.last()).isEqualTo(POC_EVENTS_ID to false)
        assertThat(client.disconnectCount).isEqualTo(1)
    }

    @Test
    fun `a failed unsubscribe does not prevent the disconnect`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)
        client.notificationFailure = BleError.GattOperationTimeout

        viewModel.onEvent(DeviceDetailsEvent.Disconnect)

        assertThat(client.disconnectCount).isEqualTo(1)
    }

    @Test
    fun `reconnecting restores the subscription and keeps the event history`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        client.readValues[POC_STATUS_ID] = "ONLINE".encodeToByteArray()
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)
        client.emitNotification(POC_EVENTS_ID, "TOUCH_ON".encodeToByteArray())

        client.simulateUnexpectedDisconnect()
        assertThat(viewModel.uiState.value.poc).isNull()
        assertThat(viewModel.uiState.value.hardwareEvents.isTouchPressed).isFalse()
        assertThat(viewModel.uiState.value.showsHardwareEvents).isTrue()

        advanceTimeBy(1_500)

        val state = viewModel.uiState.value
        assertThat(state.connection.status).isEqualTo(ConnectionStatus.CONNECTED)
        assertThat(state.connection.hint).isNull()
        assertThat(state.poc?.isEventsNotifying).isTrue()
        assertThat(state.poc?.status).isEqualTo("ONLINE")
        assertThat(client.notificationChanges)
            .containsExactly(POC_EVENTS_ID to true, POC_EVENTS_ID to true).inOrder()
        assertThat(state.hardwareEvents.summary()).containsExactly("TOUCH Not received")

        client.emitNotification(POC_EVENTS_ID, "EVENT 2".encodeToByteArray())
        assertThat(viewModel.uiState.value.hardwareEvents.summary())
            .containsExactly("TOUCH Not received", "EVENT 2").inOrder()
    }

    // --- Reconnection ---------------------------------------------------------------------

    @Test
    fun `an out of range device is retried with back-off and then given up on`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)
        client.connectFailure = BleError.ConnectionTimeout

        client.simulateUnexpectedDisconnect(BleError.ConnectionTimeout)
        advanceTimeBy(1_500) // attempt 1 failed, waiting 2 s
        assertThat(viewModel.uiState.value.connection.statusLabel)
            .isEqualTo("Reconnecting… (attempt 2 of 3)")

        advanceTimeBy(10_000)

        val connection = viewModel.uiState.value.connection
        assertThat(client.connectCount).isEqualTo(1 + 3)
        assertThat(connection.status).isEqualTo(ConnectionStatus.DISCONNECTED)
        assertThat(connection.hint).isEqualTo(
            "Could not reconnect: The device did not answer in time. It may be out of range.",
        )
        assertThat(connection.canConnect).isTrue()

        advanceTimeBy(60_000)
        assertThat(client.connectCount).isEqualTo(1 + 3)
    }

    @Test
    fun `stopping a reconnection cancels any further attempt`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)
        client.simulateUnexpectedDisconnect()

        viewModel.onEvent(DeviceDetailsEvent.Disconnect)
        advanceTimeBy(60_000)

        assertThat(client.connectCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.connection.status)
            .isEqualTo(ConnectionStatus.DISCONNECTED)
        assertThat(viewModel.uiState.value.connection.hint).isNull()
    }

    @Test
    fun `leaving the screen cancels a reconnection`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)
        client.simulateUnexpectedDisconnect()

        viewModel.releaseConnection()
        advanceTimeBy(60_000)

        assertThat(client.connectCount).isEqualTo(1)
    }

    @Test
    fun `a disconnect the user asked for is never reconnected`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)

        viewModel.onEvent(DeviceDetailsEvent.Disconnect)
        advanceTimeBy(60_000)

        assertThat(client.connectCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.connection.status)
            .isEqualTo(ConnectionStatus.DISCONNECTED)
    }

    @Test
    fun `losing the link of another device does not reconnect this one`() = runTest {
        val viewModel = createViewModel()
        client.connect("11:22:33:44:55:66")

        client.simulateUnexpectedDisconnect()
        advanceTimeBy(60_000)

        assertThat(client.connectCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.connection.isReconnecting).isFalse()
    }

    @Test
    fun `bluetooth switched off while connected is reported and not retried`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)

        scanRepository.bluetoothState.value = BluetoothState.DISABLED
        client.simulateUnexpectedDisconnect(BleError.BluetoothDisabled)
        advanceTimeBy(60_000)

        val connection = viewModel.uiState.value.connection
        assertThat(client.connectCount).isEqualTo(1)
        assertThat(connection.message).isEqualTo("Bluetooth is disabled.")
        assertThat(connection.statusLabel).isEqualTo("Bluetooth is off")
        assertThat(connection.hint).isEqualTo("Turn Bluetooth on to connect.")
        assertThat(connection.canConnect).isFalse()
    }

    @Test
    fun `bluetooth switched off during a reconnection stops it`() = runTest {
        val viewModel = createViewModel()
        viewModel.onEvent(DeviceDetailsEvent.Connect)
        client.simulateUnexpectedDisconnect()

        scanRepository.bluetoothState.value = BluetoothState.DISABLED
        advanceTimeBy(60_000)

        assertThat(client.connectCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.connection.isReconnecting).isFalse()

        scanRepository.bluetoothState.value = BluetoothState.ENABLED
        assertThat(viewModel.uiState.value.connection.canConnect).isTrue()
    }

    @Test
    fun `connect is unavailable while bluetooth is off`() = runTest {
        scanRepository.bluetoothState.value = BluetoothState.DISABLED
        val viewModel = createViewModel()

        viewModel.onEvent(DeviceDetailsEvent.Connect)

        assertThat(viewModel.uiState.value.connection.canConnect).isFalse()
        assertThat(client.connectCount).isEqualTo(0)
    }

    @Test
    fun `a failed service discovery is reported and can be retried`() = runTest {
        client.connectFailure = BleError.ServiceDiscoveryFailed
        val viewModel = createViewModel()

        viewModel.onEvent(DeviceDetailsEvent.Connect)

        val connection = viewModel.uiState.value.connection
        assertThat(connection.message).isEqualTo("Could not discover the device's services.")
        assertThat(connection.hint).isEqualTo("Could not discover the device's services.")
        assertThat(connection.canConnect).isTrue()
    }

    @Test
    fun `re-entering the screen shows the link that is already open`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        client.connect(DEVICE_ID)

        val viewModel = createViewModel()

        assertThat(viewModel.uiState.value.connection.status).isEqualTo(ConnectionStatus.CONNECTED)
        assertThat(viewModel.uiState.value.poc).isNotNull()
    }

    @Test
    fun `events of another connected device are not shown here`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        val viewModel = createViewModel()

        client.connect("11:22:33:44:55:66")
        client.emitNotification(POC_EVENTS_ID, "EVENT 1".encodeToByteArray())

        assertThat(viewModel.uiState.value.poc).isNull()
        assertThat(client.notificationChanges).isEmpty()
    }

    private fun TestScope.createViewModel(): DeviceDetailsViewModel = DeviceDetailsViewModel(
        savedStateHandle = SavedStateHandle(mapOf("deviceId" to DEVICE_ID)),
        observeScannedDevice = ObserveScannedDeviceUseCase(sessionStore),
        observeKnownDevice = ObserveKnownDeviceUseCase(knownDevices),
        observeGattConnection = ObserveGattConnectionUseCase(repository),
        observeGattErrors = ObserveGattErrorsUseCase(repository),
        observePocService = ObservePocServiceUseCase(repository),
        observeNotifyingCharacteristics = ObserveNotifyingCharacteristicsUseCase(repository),
        observePocHardwareEvents = ObservePocHardwareEventsUseCase(
            ObservePocServiceUseCase(repository),
            ObservePocEventsUseCase(
                ObservePocServiceUseCase(repository),
                ObserveCharacteristicValuesUseCase(repository),
            ),
        ),
        observeBluetoothState = ObserveBluetoothStateUseCase(scanRepository),
        connectToDevice = ConnectToDeviceUseCase(repository),
        reconnectToDevice = ReconnectToDeviceUseCase(repository),
        disconnectFromDevice = DisconnectFromDeviceUseCase(repository),
        readPocStatus = ReadPocStatusUseCase(repository, ReadCharacteristicUseCase(repository)),
        sendPocCommand = SendPocCommandUseCase(repository, WriteCharacteristicUseCase(repository)),
        setPocEventsNotifications = SetPocEventsNotificationsUseCase(
            repository,
            SetCharacteristicNotificationsUseCase(repository),
        ),
        // Unconfined, so the teardown the ViewModel launches on leaving runs immediately.
        applicationScope = CoroutineScope(mainDispatcherRule.testDispatcher),
    )
}

/** One line per history entry: a touch as `TOUCH <duration or release status>`, others raw. */
private fun HardwareEventsUiState.summary(): List<String> = entries.map {
    when (it) {
        is HardwareEventEntryUi.Touch -> "TOUCH ${it.duration ?: it.releaseStatus}"
        is HardwareEventEntryUi.Single -> it.type
    }
}
