package com.radustavila.myblesense.feature.gattinspector

import androidx.lifecycle.SavedStateHandle
import com.radustavila.myblesense.domain.testing.assertThat
import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.GattCharacteristicId
import com.radustavila.myblesense.domain.model.GattCharacteristicProperty
import com.radustavila.myblesense.domain.model.GattConnectionState
import com.radustavila.myblesense.domain.session.InMemoryScanSessionStore
import com.radustavila.myblesense.domain.testing.BATTERY_LEVEL_UUID
import com.radustavila.myblesense.domain.testing.BATTERY_SERVICE_UUID
import com.radustavila.myblesense.domain.testing.CUSTOM_CHARACTERISTIC_UUID
import com.radustavila.myblesense.domain.testing.CUSTOM_SERVICE_UUID
import com.radustavila.myblesense.domain.testing.FakeBleGattRepository
import com.radustavila.myblesense.domain.testing.MainDispatcherRule
import com.radustavila.myblesense.domain.testing.bleDevice
import com.radustavila.myblesense.domain.testing.gattCharacteristic
import com.radustavila.myblesense.domain.testing.gattService
import com.radustavila.myblesense.domain.usecase.ConnectToDeviceUseCase
import com.radustavila.myblesense.domain.usecase.DisconnectFromDeviceUseCase
import com.radustavila.myblesense.domain.usecase.ObserveCharacteristicValuesUseCase
import com.radustavila.myblesense.domain.usecase.ObserveGattConnectionUseCase
import com.radustavila.myblesense.domain.usecase.ObserveGattErrorsUseCase
import com.radustavila.myblesense.domain.usecase.ObserveGattServicesUseCase
import com.radustavila.myblesense.domain.usecase.ObserveNotifyingCharacteristicsUseCase
import com.radustavila.myblesense.domain.usecase.ObserveScannedDeviceUseCase
import com.radustavila.myblesense.domain.usecase.ReadCharacteristicUseCase
import com.radustavila.myblesense.domain.usecase.SetCharacteristicNotificationsUseCase
import com.radustavila.myblesense.domain.usecase.WriteCharacteristicUseCase
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

private const val DEVICE_ID = "AA:BB:CC:DD:EE:FF"

class GattInspectorViewModelTest {

    private val mainDispatcherRule = MainDispatcherRule()

    @BeforeTest
    fun installMainDispatcher() = mainDispatcherRule.install()

    @AfterTest
    fun resetMainDispatcher() = mainDispatcherRule.reset()

    private val repository = FakeBleGattRepository()
    private val client = repository.client
    private val sessionStore = InMemoryScanSessionStore()

    private val batteryLevel = GattCharacteristicId(BATTERY_SERVICE_UUID, BATTERY_LEVEL_UUID)
    private val writable = GattCharacteristicId(CUSTOM_SERVICE_UUID, CUSTOM_CHARACTERISTIC_UUID)

    @Test
    fun `starts disconnected with no services`() = runTest {
        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertThat(state.connectionState).isEqualTo(GattConnectionState.DISCONNECTED)
        assertThat(state.isConnected).isFalse()
        assertThat(state.services).isEmpty()
    }

    @Test
    fun `shows the scanned device name when the session still knows it`() = runTest {
        sessionStore.publish(listOf(bleDevice(id = DEVICE_ID, name = "Venu Sq")))

        val viewModel = createViewModel()

        assertThat(viewModel.uiState.value.deviceName).isEqualTo("Venu Sq")
    }

    @Test
    fun `falls back to the device id for an unnamed device`() = runTest {
        val viewModel = createViewModel()

        assertThat(viewModel.uiState.value.deviceName).isEqualTo(DEVICE_ID)
    }

    @Test
    fun `discovered services are named when the UUID is a standard one`() = runTest {
        val viewModel = createViewModel()
        connect()

        val services = viewModel.uiState.value.services
        assertThat(services.map { it.name })
            .containsExactly("Battery Service", null)
            .inOrder()
        assertThat(services.first().shortUuid).isEqualTo("180F")
        assertThat(services.first().characteristics.single().name).isEqualTo("Battery Level")
        assertThat(services.last().title).isEqualTo("Unknown service")
    }

    @Test
    fun `services expand and collapse`() = runTest {
        val viewModel = createViewModel()
        connect()

        viewModel.onEvent(GattInspectorEvent.ToggleService(BATTERY_SERVICE_UUID))
        assertThat(viewModel.uiState.value.services.first().isExpanded).isTrue()

        viewModel.onEvent(GattInspectorEvent.ToggleService(BATTERY_SERVICE_UUID))
        assertThat(viewModel.uiState.value.services.first().isExpanded).isFalse()
    }

    @Test
    fun `reading shows the raw value its size and the standard interpretation`() = runTest {
        val viewModel = createViewModel()
        connect()
        client.readValues[batteryLevel] = byteArrayOf(0x4C)

        viewModel.onEvent(GattInspectorEvent.Read(batteryLevel))

        val value = viewModel.characteristic(batteryLevel).latestValue
        assertThat(value?.hex).isEqualTo("4C")
        assertThat(value?.byteCount).isEqualTo(1)
        assertThat(value?.decoded).isEqualTo("76%")
        assertThat(value?.fromNotification).isFalse()
    }

    @Test
    fun `a failed read is reported without a value`() = runTest {
        val viewModel = createViewModel()
        connect()
        client.readFailure = BleError.GattOperationTimeout

        viewModel.onEvent(GattInspectorEvent.Read(batteryLevel))

        assertThat(viewModel.characteristic(batteryLevel).latestValue).isNull()
        assertThat(viewModel.uiState.value.message?.isError).isTrue()
        assertThat(viewModel.uiState.value.message?.text).contains("did not answer in time")
    }

    @Test
    fun `a read leaves no operation in flight`() = runTest {
        val viewModel = createViewModel()
        connect()

        viewModel.onEvent(GattInspectorEvent.Read(batteryLevel))

        assertThat(viewModel.characteristic(batteryLevel).operation)
            .isEqualTo(CharacteristicOperation.IDLE)
    }

    @Test
    fun `valid hex is written to the device`() = runTest {
        val viewModel = createViewModel()
        connect()

        viewModel.onEvent(GattInspectorEvent.WriteInputChanged(writable, "01 FF 2A"))
        viewModel.onEvent(GattInspectorEvent.Write(writable))

        assertThat(client.writes.single().value).isEqualTo(byteArrayOf(0x01, 0xFF.toByte(), 0x2A))
        assertThat(viewModel.uiState.value.message?.text).isEqualTo("Wrote 3 byte(s).")
    }

    @Test
    fun `invalid hex is rejected before anything is sent`() = runTest {
        val viewModel = createViewModel()
        connect()

        viewModel.onEvent(GattInspectorEvent.WriteInputChanged(writable, "0G"))
        viewModel.onEvent(GattInspectorEvent.Write(writable))

        assertThat(client.writes).isEmpty()
        assertThat(viewModel.characteristic(writable).writeError).isNotNull()
    }

    @Test
    fun `editing the value clears the previous validation error`() = runTest {
        val viewModel = createViewModel()
        connect()
        viewModel.onEvent(GattInspectorEvent.WriteInputChanged(writable, "0G"))
        viewModel.onEvent(GattInspectorEvent.Write(writable))

        viewModel.onEvent(GattInspectorEvent.WriteInputChanged(writable, "0A"))

        assertThat(viewModel.characteristic(writable).writeError).isNull()
    }

    @Test
    fun `a failed write is reported`() = runTest {
        val viewModel = createViewModel()
        connect()
        client.writeFailure = BleError.CharacteristicWriteFailed()

        viewModel.onEvent(GattInspectorEvent.WriteInputChanged(writable, "01"))
        viewModel.onEvent(GattInspectorEvent.Write(writable))

        assertThat(viewModel.uiState.value.message?.isError).isTrue()
    }

    @Test
    fun `notifications are enabled and disabled on request`() = runTest {
        val viewModel = createViewModel()
        connect()
        val notifying = GattCharacteristicId(CUSTOM_SERVICE_UUID, NOTIFY_CHARACTERISTIC_UUID)

        viewModel.onEvent(GattInspectorEvent.SetNotifications(notifying, enabled = true))
        assertThat(viewModel.characteristic(notifying).isNotifying).isTrue()

        viewModel.onEvent(GattInspectorEvent.SetNotifications(notifying, enabled = false))
        assertThat(viewModel.characteristic(notifying).isNotifying).isFalse()
    }

    @Test
    fun `incoming notifications are listed newest first`() = runTest {
        val viewModel = createViewModel()
        connect()
        val notifying = GattCharacteristicId(CUSTOM_SERVICE_UUID, NOTIFY_CHARACTERISTIC_UUID)
        viewModel.onEvent(GattInspectorEvent.SetNotifications(notifying, enabled = true))

        client.emitNotification(notifying, byteArrayOf(0x48, 0x00))
        client.emitNotification(notifying, byteArrayOf(0x4A, 0x00))

        val notifications = viewModel.characteristic(notifying).notifications
        assertThat(notifications.map { it.hex }).containsExactly("4A 00", "48 00").inOrder()
        assertThat(viewModel.characteristic(notifying).latestValue?.fromNotification).isTrue()
    }

    @Test
    fun `a failing subscription is reported and leaves notifications off`() = runTest {
        val viewModel = createViewModel()
        connect()
        client.notificationFailure = BleError.DescriptorNotFound
        val notifying = GattCharacteristicId(CUSTOM_SERVICE_UUID, NOTIFY_CHARACTERISTIC_UUID)

        viewModel.onEvent(GattInspectorEvent.SetNotifications(notifying, enabled = true))

        assertThat(viewModel.characteristic(notifying).isNotifying).isFalse()
        assertThat(viewModel.uiState.value.message?.isError).isTrue()
    }

    @Test
    fun `disconnecting clears the services and the subscriptions`() = runTest {
        val viewModel = createViewModel()
        connect()
        val notifying = GattCharacteristicId(CUSTOM_SERVICE_UUID, NOTIFY_CHARACTERISTIC_UUID)
        viewModel.onEvent(GattInspectorEvent.SetNotifications(notifying, enabled = true))

        viewModel.onEvent(GattInspectorEvent.Disconnect)

        val state = viewModel.uiState.value
        assertThat(state.connectionState).isEqualTo(GattConnectionState.DISCONNECTED)
        assertThat(state.services).isEmpty()
        assertThat(client.disconnectCount).isEqualTo(1)
    }

    @Test
    fun `losing the link while subscribed reports it and stops the stream`() = runTest {
        val viewModel = createViewModel()
        connect()

        client.simulateUnexpectedDisconnect(BleError.Disconnected)

        assertThat(viewModel.uiState.value.isConnected).isFalse()
        assertThat(viewModel.uiState.value.message?.text).contains("disconnected")
    }

    private suspend fun connect() {
        client.servicesOnConnect = listOf(
            gattService(),
            gattService(
                uuid = CUSTOM_SERVICE_UUID,
                characteristics = listOf(
                    gattCharacteristic(
                        serviceUuid = CUSTOM_SERVICE_UUID,
                        characteristicUuid = CUSTOM_CHARACTERISTIC_UUID,
                        properties = setOf(GattCharacteristicProperty.WRITE),
                    ),
                    gattCharacteristic(
                        serviceUuid = CUSTOM_SERVICE_UUID,
                        characteristicUuid = NOTIFY_CHARACTERISTIC_UUID,
                        properties = setOf(GattCharacteristicProperty.NOTIFY),
                    ),
                ),
            ),
        )
        ConnectToDeviceUseCase(repository).invoke(DEVICE_ID)
    }

    private fun GattInspectorViewModel.characteristic(
        id: GattCharacteristicId,
    ): GattCharacteristicUi = uiState.value.services
        .first { it.uuid == id.serviceUuid }
        .characteristics
        .first { it.uuid == id.characteristicUuid }

    /**
     * The UI state is a `WhileSubscribed` flow, exactly as the screen consumes it, so the test
     * keeps a collector alive for the duration of the test.
     */
    private fun TestScope.createViewModel(): GattInspectorViewModel = GattInspectorViewModel(
        savedStateHandle = SavedStateHandle(mapOf("deviceId" to DEVICE_ID)),
        observeScannedDevice = ObserveScannedDeviceUseCase(sessionStore),
        observeConnection = ObserveGattConnectionUseCase(repository),
        observeServices = ObserveGattServicesUseCase(repository),
        observeNotifying = ObserveNotifyingCharacteristicsUseCase(repository),
        observeCharacteristicValues = ObserveCharacteristicValuesUseCase(repository),
        observeGattErrors = ObserveGattErrorsUseCase(repository),
        readCharacteristic = ReadCharacteristicUseCase(repository),
        writeCharacteristic = WriteCharacteristicUseCase(repository),
        setCharacteristicNotifications = SetCharacteristicNotificationsUseCase(repository),
        disconnectFromDevice = DisconnectFromDeviceUseCase(repository),
    ).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { }
        }
    }

    private companion object {
        const val NOTIFY_CHARACTERISTIC_UUID = "12345678-1234-5678-1234-56789abcdef3"
    }
}
