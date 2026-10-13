package com.radustavila.myblesense.domain

import app.cash.turbine.test
import com.radustavila.myblesense.domain.testing.assertThat
import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.GattCharacteristicProperty
import com.radustavila.myblesense.domain.model.GattService
import com.radustavila.myblesense.domain.model.GattWriteType
import com.radustavila.myblesense.domain.model.PocCommand
import com.radustavila.myblesense.domain.model.PocEvent
import com.radustavila.myblesense.domain.model.PocProfile
import com.radustavila.myblesense.domain.model.findPocService
import com.radustavila.myblesense.domain.model.toBleError
import com.radustavila.myblesense.domain.testing.FakeBleGattClient.RecordedWrite
import com.radustavila.myblesense.domain.testing.FakeBleGattRepository
import com.radustavila.myblesense.domain.testing.POC_COMMAND_ID
import com.radustavila.myblesense.domain.testing.POC_EVENTS_ID
import com.radustavila.myblesense.domain.testing.POC_STATUS_ID
import com.radustavila.myblesense.domain.testing.gattCharacteristic
import com.radustavila.myblesense.domain.testing.gattService
import com.radustavila.myblesense.domain.testing.pocGattService
import com.radustavila.myblesense.domain.usecase.ConnectToDeviceUseCase
import com.radustavila.myblesense.domain.usecase.ObserveCharacteristicValuesUseCase
import com.radustavila.myblesense.domain.usecase.ObservePocEventsUseCase
import com.radustavila.myblesense.domain.usecase.ObservePocServiceUseCase
import com.radustavila.myblesense.domain.usecase.ReadCharacteristicUseCase
import com.radustavila.myblesense.domain.usecase.ReadPocStatusUseCase
import com.radustavila.myblesense.domain.usecase.SendPocCommandUseCase
import com.radustavila.myblesense.domain.usecase.SetCharacteristicNotificationsUseCase
import com.radustavila.myblesense.domain.usecase.SetPocEventsNotificationsUseCase
import com.radustavila.myblesense.domain.usecase.WriteCharacteristicUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

private const val DEVICE_ID = "AA:BB:CC:DD:EE:FF"

class PocServiceUseCaseTest {

    private val repository = FakeBleGattRepository()
    private val client = repository.client

    private val connect = ConnectToDeviceUseCase(repository)
    private val observePocService = ObservePocServiceUseCase(repository)
    private val readStatus = ReadPocStatusUseCase(repository, ReadCharacteristicUseCase(repository))
    private val sendCommand =
        SendPocCommandUseCase(repository, WriteCharacteristicUseCase(repository))
    private val setEventsNotifications = SetPocEventsNotificationsUseCase(
        repository,
        SetCharacteristicNotificationsUseCase(repository),
    )
    private val observeEvents = ObservePocEventsUseCase(
        observePocService,
        ObserveCharacteristicValuesUseCase(repository),
    )

    // --- Identification -------------------------------------------------------------------

    @Test
    fun `identifies the service and its three characteristics`() {
        val poc = listOf(gattService(), pocGattService()).findPocService()

        assertThat(poc).isNotNull()
        assertThat(poc!!.status?.canRead).isTrue()
        assertThat(poc.command?.canWrite).isTrue()
        assertThat(poc.events?.canNotify).isTrue()
        assertThat(poc.hasAllCharacteristics).isTrue()
    }

    @Test
    fun `uuids are matched regardless of case`() {
        val upperCase = GattService(
            uuid = PocProfile.SERVICE_UUID.uppercase(),
            characteristics = listOf(
                gattCharacteristic(
                    serviceUuid = PocProfile.SERVICE_UUID.uppercase(),
                    characteristicUuid = PocProfile.STATUS_UUID.uppercase(),
                ),
            ),
        )

        val poc = listOf(upperCase).findPocService()

        assertThat(poc?.status).isNotNull()
        assertThat(poc?.command).isNull()
        assertThat(poc?.hasAllCharacteristics).isFalse()
    }

    @Test
    fun `a device without the service has no poc service`() {
        assertThat(listOf(gattService()).findPocService()).isNull()
    }

    @Test
    fun `the service appears once discovery completes and goes away on disconnect`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())

        observePocService().test {
            assertThat(awaitItem()).isNull()

            connect(DEVICE_ID)
            assertThat(awaitItem()?.status?.id).isEqualTo(POC_STATUS_ID)

            client.simulateUnexpectedDisconnect()
            assertThat(awaitItem()).isNull()
        }
    }

    // --- Status read ----------------------------------------------------------------------

    @Test
    fun `reads the status as text`() = runTest {
        connectToPoc()
        client.readValues[POC_STATUS_ID] = "ONLINE".encodeToByteArray()

        assertThat(readStatus().getOrThrow()).isEqualTo("ONLINE")
    }

    @Test
    fun `a trailing C string terminator is dropped`() = runTest {
        connectToPoc()
        client.readValues[POC_STATUS_ID] = "ONLINE\u0000".encodeToByteArray()

        assertThat(readStatus().getOrThrow()).isEqualTo("ONLINE")
    }

    @Test
    fun `reading while disconnected fails with not connected`() = runTest {
        val error = readStatus().exceptionOrNull()?.toBleError()

        assertThat(error).isEqualTo(BleError.NotConnected)
    }

    @Test
    fun `reading a device without the status characteristic fails`() = runTest {
        client.servicesOnConnect = listOf(gattService())
        connect(DEVICE_ID)

        val error = readStatus().exceptionOrNull()?.toBleError()

        assertThat(error).isEqualTo(BleError.CharacteristicNotFound)
    }

    @Test
    fun `a status characteristic without read is rejected before reaching the stack`() = runTest {
        client.servicesOnConnect = listOf(
            pocGattService(
                characteristics = listOf(
                    gattCharacteristic(
                        serviceUuid = PocProfile.SERVICE_UUID,
                        characteristicUuid = PocProfile.STATUS_UUID,
                        properties = setOf(GattCharacteristicProperty.NOTIFY),
                    ),
                ),
            ),
        )
        connect(DEVICE_ID)

        val error = readStatus().exceptionOrNull()?.toBleError()

        assertThat(error).isEqualTo(BleError.CharacteristicOperationUnsupported)
    }

    @Test
    fun `a gatt read failure is propagated`() = runTest {
        connectToPoc()
        client.readFailure = BleError.CharacteristicReadFailed(status = 2)

        val error = readStatus().exceptionOrNull()?.toBleError()

        assertThat(error).isEqualTo(BleError.CharacteristicReadFailed(status = 2))
    }

    // --- Command write ---------------------------------------------------------------------

    @Test
    fun `ping writes the utf-8 bytes of PING to the command characteristic with response`() =
        runTest {
            connectToPoc()

            assertThat(sendCommand(PocCommand.PING).isSuccess).isTrue()

            assertThat(client.writes).containsExactly(
                RecordedWrite(POC_COMMAND_ID, "PING".encodeToByteArray(), GattWriteType.WITH_RESPONSE),
            )
        }

    @Test
    fun `the ping payload has no terminator`() {
        assertThat(PocCommand.PING.encode())
            .isEqualTo(byteArrayOf(0x50, 0x49, 0x4E, 0x47))
    }

    @Test
    fun `sending while disconnected fails with not connected`() = runTest {
        val error = sendCommand(PocCommand.PING).exceptionOrNull()?.toBleError()

        assertThat(error).isEqualTo(BleError.NotConnected)
        assertThat(client.writes).isEmpty()
    }

    @Test
    fun `sending to a device without the command characteristic fails`() = runTest {
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
        connect(DEVICE_ID)

        val error = sendCommand(PocCommand.PING).exceptionOrNull()?.toBleError()

        assertThat(error).isEqualTo(BleError.CharacteristicNotFound)
    }

    @Test
    fun `a command characteristic without write is rejected before reaching the stack`() =
        runTest {
            client.servicesOnConnect = listOf(
                pocGattService(
                    characteristics = listOf(
                        gattCharacteristic(
                            serviceUuid = PocProfile.SERVICE_UUID,
                            characteristicUuid = PocProfile.COMMAND_UUID,
                            properties = setOf(GattCharacteristicProperty.READ),
                        ),
                    ),
                ),
            )
            connect(DEVICE_ID)

            val error = sendCommand(PocCommand.PING).exceptionOrNull()?.toBleError()

            assertThat(error).isEqualTo(BleError.CharacteristicOperationUnsupported)
            assertThat(client.writes).isEmpty()
        }

    @Test
    fun `a gatt write failure is propagated`() = runTest {
        connectToPoc()
        client.writeFailure = BleError.CharacteristicWriteFailed(status = 3)

        val error = sendCommand(PocCommand.PING).exceptionOrNull()?.toBleError()

        assertThat(error).isEqualTo(BleError.CharacteristicWriteFailed(status = 3))
    }

    @Test
    fun `a gatt timeout is propagated`() = runTest {
        connectToPoc()
        client.writeFailure = BleError.GattOperationTimeout

        val error = sendCommand(PocCommand.PING).exceptionOrNull()?.toBleError()

        assertThat(error).isEqualTo(BleError.GattOperationTimeout)
    }

    // --- Events notifications --------------------------------------------------------------

    @Test
    fun `subscribing enables notifications on the events characteristic`() = runTest {
        connectToPoc()

        assertThat(setEventsNotifications(enabled = true).isSuccess).isTrue()

        assertThat(client.notificationChanges).containsExactly(POC_EVENTS_ID to true)
        assertThat(repository.notifyingCharacteristics().value).containsExactly(POC_EVENTS_ID)
    }

    @Test
    fun `unsubscribing disables notifications on the events characteristic`() = runTest {
        connectToPoc()
        setEventsNotifications(enabled = true)

        assertThat(setEventsNotifications(enabled = false).isSuccess).isTrue()

        assertThat(client.notificationChanges.last()).isEqualTo(POC_EVENTS_ID to false)
        assertThat(repository.notifyingCharacteristics().value).isEmpty()
    }

    @Test
    fun `subscribing while disconnected fails with not connected`() = runTest {
        val error = setEventsNotifications(enabled = true).exceptionOrNull()?.toBleError()

        assertThat(error).isEqualTo(BleError.NotConnected)
        assertThat(client.notificationChanges).isEmpty()
    }

    @Test
    fun `subscribing to a device without the events characteristic fails`() = runTest {
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
        connect(DEVICE_ID)

        val error = setEventsNotifications(enabled = true).exceptionOrNull()?.toBleError()

        assertThat(error).isEqualTo(BleError.CharacteristicNotFound)
    }

    @Test
    fun `an events characteristic without notify is rejected before reaching the stack`() =
        runTest {
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
            connect(DEVICE_ID)

            val error = setEventsNotifications(enabled = true).exceptionOrNull()?.toBleError()

            assertThat(error).isEqualTo(BleError.CharacteristicOperationUnsupported)
            assertThat(client.notificationChanges).isEmpty()
        }

    @Test
    fun `a cccd write failure is propagated`() = runTest {
        connectToPoc()
        client.notificationFailure = BleError.NotificationEnableFailed(status = 5)

        val error = setEventsNotifications(enabled = true).exceptionOrNull()?.toBleError()

        assertThat(error).isEqualTo(BleError.NotificationEnableFailed(status = 5))
    }

    @Test
    fun `events notifications are decoded as utf-8 text in arrival order`() = runTest {
        connectToPoc()

        observeEvents().test {
            client.currentTimeMillis = 1_000
            client.emitNotification(POC_EVENTS_ID, "EVENT 1".encodeToByteArray())
            client.currentTimeMillis = 3_000
            client.emitNotification(POC_EVENTS_ID, "EVENT 2\u0000".encodeToByteArray())

            assertThat(awaitItem()).isEqualTo(PocEvent(text = "EVENT 1", timestampMillis = 1_000))
            assertThat(awaitItem()).isEqualTo(PocEvent(text = "EVENT 2", timestampMillis = 3_000))
        }
    }

    @Test
    fun `non ascii event payloads are decoded as utf-8`() = runTest {
        connectToPoc()

        observeEvents().test {
            client.emitNotification(POC_EVENTS_ID, "Température 23°C".encodeToByteArray())

            assertThat(awaitItem().text).isEqualTo("Température 23°C")
        }
    }

    @Test
    fun `values of other characteristics and reads are not events`() = runTest {
        connectToPoc()
        client.readValues[POC_EVENTS_ID] = "READ".encodeToByteArray()

        observeEvents().test {
            client.emitNotification(POC_STATUS_ID, "ONLINE".encodeToByteArray())
            client.readCharacteristic(POC_EVENTS_ID)
            client.emitNotification(POC_EVENTS_ID, "EVENT 1".encodeToByteArray())

            assertThat(awaitItem().text).isEqualTo("EVENT 1")
            expectNoEvents()
        }
    }

    @Test
    fun `no events are emitted until the poc service is discovered`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())

        observeEvents().test {
            client.emitNotification(POC_EVENTS_ID, "EARLY".encodeToByteArray())
            expectNoEvents()

            connect(DEVICE_ID)
            client.emitNotification(POC_EVENTS_ID, "EVENT 1".encodeToByteArray())

            assertThat(awaitItem().text).isEqualTo("EVENT 1")
        }
    }

    private suspend fun connectToPoc() {
        client.servicesOnConnect = listOf(pocGattService())
        connect(DEVICE_ID)
    }
}
