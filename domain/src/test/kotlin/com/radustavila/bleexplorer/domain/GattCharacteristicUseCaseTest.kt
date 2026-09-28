package com.radustavila.bleexplorer.domain

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.radustavila.bleexplorer.domain.model.BleError
import com.radustavila.bleexplorer.domain.model.GattCharacteristicId
import com.radustavila.bleexplorer.domain.model.GattCharacteristicProperty
import com.radustavila.bleexplorer.domain.model.GattValueSource
import com.radustavila.bleexplorer.domain.model.GattWriteType
import com.radustavila.bleexplorer.domain.testing.BATTERY_LEVEL_UUID
import com.radustavila.bleexplorer.domain.testing.BATTERY_SERVICE_UUID
import com.radustavila.bleexplorer.domain.testing.CUSTOM_CHARACTERISTIC_UUID
import com.radustavila.bleexplorer.domain.testing.CUSTOM_SERVICE_UUID
import com.radustavila.bleexplorer.domain.testing.FakeBleGattRepository
import com.radustavila.bleexplorer.domain.testing.gattCharacteristic
import com.radustavila.bleexplorer.domain.testing.gattService
import com.radustavila.bleexplorer.domain.usecase.ConnectToDeviceUseCase
import com.radustavila.bleexplorer.domain.usecase.ObserveCharacteristicValuesUseCase
import com.radustavila.bleexplorer.domain.usecase.ObserveNotifyingCharacteristicsUseCase
import com.radustavila.bleexplorer.domain.usecase.ReadCharacteristicUseCase
import com.radustavila.bleexplorer.domain.usecase.SetCharacteristicNotificationsUseCase
import com.radustavila.bleexplorer.domain.usecase.WriteCharacteristicUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val DEVICE_ID = "AA:BB:CC:DD:EE:FF"

class GattCharacteristicUseCaseTest {

    private val repository = FakeBleGattRepository()
    private val client = repository.client

    private val connect = ConnectToDeviceUseCase(repository)
    private val read = ReadCharacteristicUseCase(repository)
    private val write = WriteCharacteristicUseCase(repository)
    private val setNotifications = SetCharacteristicNotificationsUseCase(repository)
    private val observeValues = ObserveCharacteristicValuesUseCase(repository)
    private val observeNotifying = ObserveNotifyingCharacteristicsUseCase(repository)

    private val batteryLevel = GattCharacteristicId(BATTERY_SERVICE_UUID, BATTERY_LEVEL_UUID)
    private val customCharacteristic =
        GattCharacteristicId(CUSTOM_SERVICE_UUID, CUSTOM_CHARACTERISTIC_UUID)

    // --- Read -----------------------------------------------------------------------------

    @Test
    fun `read returns the raw bytes`() = runTest {
        connectWithBatteryService()
        client.readValues[batteryLevel] = byteArrayOf(0x4C)

        val value = read(batteryLevel).getOrThrow()

        assertThat(value.value).isEqualTo(byteArrayOf(0x4C))
        assertThat(value.source).isEqualTo(GattValueSource.READ)
    }

    @Test
    fun `read failure is reported as a domain error`() = runTest {
        connectWithBatteryService()
        client.readFailure = BleError.CharacteristicReadFailed(status = 2)

        val result = read(batteryLevel)

        assertThat(result.bleError()).isEqualTo(BleError.CharacteristicReadFailed(status = 2))
    }

    @Test
    fun `a GATT timeout surfaces as a timeout, not as a generic failure`() = runTest {
        connectWithBatteryService()
        client.readFailure = BleError.GattOperationTimeout

        assertThat(read(batteryLevel).bleError()).isEqualTo(BleError.GattOperationTimeout)
    }

    @Test
    fun `read is rejected while disconnected`() = runTest {
        assertThat(read(batteryLevel).bleError()).isEqualTo(BleError.NotConnected)
    }

    @Test
    fun `read is rejected for a characteristic the device does not expose`() = runTest {
        connectWithBatteryService()

        assertThat(read(customCharacteristic).bleError())
            .isEqualTo(BleError.CharacteristicNotFound)
    }

    @Test
    fun `read is rejected when the characteristic is not readable`() = runTest {
        client.servicesOnConnect = listOf(
            gattService(
                uuid = CUSTOM_SERVICE_UUID,
                characteristics = listOf(
                    gattCharacteristic(
                        serviceUuid = CUSTOM_SERVICE_UUID,
                        characteristicUuid = CUSTOM_CHARACTERISTIC_UUID,
                        properties = setOf(GattCharacteristicProperty.WRITE),
                    ),
                ),
            ),
        )
        connect(DEVICE_ID)

        assertThat(read(customCharacteristic).bleError())
            .isEqualTo(BleError.CharacteristicOperationUnsupported)
    }

    // --- Write ----------------------------------------------------------------------------

    @Test
    fun `a write characteristic is written with response`() = runTest {
        connectWithCustomCharacteristic(GattCharacteristicProperty.WRITE)

        val result = write(customCharacteristic, byteArrayOf(0x01, 0xFF.toByte()))

        assertThat(result.isSuccess).isTrue()
        assertThat(client.writes).hasSize(1)
        assertThat(client.writes.single().writeType).isEqualTo(GattWriteType.WITH_RESPONSE)
        assertThat(client.writes.single().value).isEqualTo(byteArrayOf(0x01, 0xFF.toByte()))
    }

    @Test
    fun `a write-without-response characteristic uses the unacknowledged mode`() = runTest {
        connectWithCustomCharacteristic(GattCharacteristicProperty.WRITE_NO_RESPONSE)

        write(customCharacteristic, byteArrayOf(0x2A)).getOrThrow()

        assertThat(client.writes.single().writeType).isEqualTo(GattWriteType.WITHOUT_RESPONSE)
    }

    @Test
    fun `writing a read-only characteristic is rejected before it reaches the stack`() = runTest {
        connectWithBatteryService()

        val result = write(batteryLevel, byteArrayOf(0x01))

        assertThat(result.bleError()).isEqualTo(BleError.CharacteristicOperationUnsupported)
        assertThat(client.writes).isEmpty()
    }

    @Test
    fun `write failure is reported`() = runTest {
        connectWithCustomCharacteristic(GattCharacteristicProperty.WRITE)
        client.writeFailure = BleError.CharacteristicWriteFailed(status = 3)

        assertThat(write(customCharacteristic, byteArrayOf(0x01)).bleError())
            .isEqualTo(BleError.CharacteristicWriteFailed(status = 3))
    }

    // --- Notifications --------------------------------------------------------------------

    @Test
    fun `enabling and disabling notifications updates the subscription set`() = runTest {
        connectWithCustomCharacteristic(GattCharacteristicProperty.NOTIFY)

        setNotifications(customCharacteristic, enabled = true).getOrThrow()
        assertThat(observeNotifying().value).containsExactly(customCharacteristic)

        setNotifications(customCharacteristic, enabled = false).getOrThrow()
        assertThat(observeNotifying().value).isEmpty()
    }

    @Test
    fun `indicate characteristics can be subscribed to as well`() = runTest {
        connectWithCustomCharacteristic(GattCharacteristicProperty.INDICATE)

        assertThat(setNotifications(customCharacteristic, enabled = true).isSuccess).isTrue()
    }

    @Test
    fun `subscribing to a characteristic without notify or indicate is rejected`() = runTest {
        connectWithBatteryService()

        assertThat(setNotifications(batteryLevel, enabled = true).bleError())
            .isEqualTo(BleError.CharacteristicOperationUnsupported)
    }

    @Test
    fun `a failing CCCD write is reported`() = runTest {
        connectWithCustomCharacteristic(GattCharacteristicProperty.NOTIFY)
        client.notificationFailure = BleError.NotificationEnableFailed()

        assertThat(setNotifications(customCharacteristic, enabled = true).bleError())
            .isEqualTo(BleError.NotificationEnableFailed())
    }

    @Test
    fun `incoming notifications are streamed for the subscribed characteristic only`() = runTest {
        connectWithCustomCharacteristic(GattCharacteristicProperty.NOTIFY)

        observeValues(customCharacteristic).test {
            client.emitNotification(batteryLevel, byteArrayOf(0x64))
            client.emitNotification(customCharacteristic, byteArrayOf(0x48, 0x00))
            client.emitNotification(customCharacteristic, byteArrayOf(0x4A, 0x00))

            assertThat(awaitItem().value).isEqualTo(byteArrayOf(0x48, 0x00))
            assertThat(awaitItem().value).isEqualTo(byteArrayOf(0x4A, 0x00))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `losing the link while observing stops the operations and clears the subscriptions`() =
        runTest {
            connectWithCustomCharacteristic(GattCharacteristicProperty.NOTIFY)
            setNotifications(customCharacteristic, enabled = true).getOrThrow()

            client.simulateUnexpectedDisconnect()

            assertThat(observeNotifying().value).isEmpty()
            assertThat(read(batteryLevel).bleError()).isEqualTo(BleError.NotConnected)
        }

    private suspend fun connectWithBatteryService() {
        client.servicesOnConnect = listOf(gattService())
        connect(DEVICE_ID)
    }

    private suspend fun connectWithCustomCharacteristic(
        property: GattCharacteristicProperty,
    ) {
        client.servicesOnConnect = listOf(
            gattService(),
            gattService(
                uuid = CUSTOM_SERVICE_UUID,
                characteristics = listOf(
                    gattCharacteristic(
                        serviceUuid = CUSTOM_SERVICE_UUID,
                        characteristicUuid = CUSTOM_CHARACTERISTIC_UUID,
                        properties = setOf(property),
                    ),
                ),
            ),
        )
        connect(DEVICE_ID)
    }
}
