package com.radustavila.myblesense.domain

import com.radustavila.myblesense.domain.testing.assertThat
import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.GattConnection
import com.radustavila.myblesense.domain.model.GattConnectionState
import com.radustavila.myblesense.domain.model.KnownDevice
import com.radustavila.myblesense.domain.model.shouldAutoConnect
import com.radustavila.myblesense.domain.session.InMemoryScanSessionStore
import com.radustavila.myblesense.domain.testing.FakeBleGattRepository
import com.radustavila.myblesense.domain.testing.FakeKnownDeviceRepository
import com.radustavila.myblesense.domain.testing.bleDevice
import com.radustavila.myblesense.domain.model.BleException
import com.radustavila.myblesense.domain.usecase.ConnectToDeviceUseCase
import com.radustavila.myblesense.domain.usecase.ForgetKnownDeviceUseCase
import com.radustavila.myblesense.domain.usecase.TrackKnownDeviceUseCase
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFailsWith
import com.radustavila.myblesense.domain.testing.TestIoException as IOException

private const val DEVICE_ID = "AA:BB:CC:DD:EE:FF"
private const val OTHER_DEVICE_ID = "11:22:33:44:55:66"

class KnownDeviceTest {

    private val gattRepository = FakeBleGattRepository()
    private val client = gattRepository.client
    private val sessionStore = InMemoryScanSessionStore()

    // --- Merging a scan result ------------------------------------------------------------

    @Test
    fun `a scan result updates the remembered name`() {
        val known = KnownDevice(DEVICE_ID, name = "ESP32")

        val updated = known.updatedFrom(bleDevice(id = DEVICE_ID, name = "ESP32-BLE-POC"))

        assertThat(updated).isEqualTo(KnownDevice(DEVICE_ID, name = "ESP32-BLE-POC"))
    }

    @Test
    fun `a scan result without a name keeps the remembered one`() {
        val known = KnownDevice(DEVICE_ID, name = "ESP32-BLE-POC")

        assertThat(known.updatedFrom(bleDevice(id = DEVICE_ID, name = null))).isEqualTo(known)
    }

    @Test
    fun `a scan result of another device is rejected`() {
        assertFailsWith<IllegalArgumentException> {
            KnownDevice(DEVICE_ID, name = null).updatedFrom(bleDevice(id = OTHER_DEVICE_ID))
        }
    }

    // --- Auto-connect policy --------------------------------------------------------------

    @Test
    fun `auto-connects to a known device once Bluetooth is ready and the link is idle`() {
        assertThat(
            shouldAutoConnect(
                knownDevice = KnownDevice(DEVICE_ID, name = null),
                isBluetoothReady = true,
                connection = GattConnection.Disconnected,
                hasAttempted = false,
            ),
        ).isTrue()
    }

    @Test
    fun `never auto-connects without a known device before Bluetooth is ready or twice`() {
        val known = KnownDevice(DEVICE_ID, name = null)
        val idle = GattConnection.Disconnected

        assertThat(shouldAutoConnect(null, true, idle, hasAttempted = false)).isFalse()
        assertThat(shouldAutoConnect(known, false, idle, hasAttempted = false)).isFalse()
        assertThat(shouldAutoConnect(known, true, idle, hasAttempted = true)).isFalse()
    }

    @Test
    fun `never replaces a link that is open or opening`() {
        val known = KnownDevice(DEVICE_ID, name = null)

        GattConnectionState.entries
            .filter { it != GattConnectionState.DISCONNECTED }
            .forEach { state ->
                val connection = GattConnection(OTHER_DEVICE_ID, state)
                assertThat(shouldAutoConnect(known, true, connection, hasAttempted = false))
                    .isFalse()
            }
    }

    // --- Tracking -------------------------------------------------------------------------

    @Test
    fun `a successful connection remembers the device with its scanned name`() = runTest {
        val repository = FakeKnownDeviceRepository()
        sessionStore.publish(listOf(bleDevice(id = DEVICE_ID, name = "ESP32-BLE-POC")))
        val tracking = launch { track(repository) }
        runCurrent()

        ConnectToDeviceUseCase(gattRepository)(DEVICE_ID)
        runCurrent()

        assertThat(repository.current).isEqualTo(KnownDevice(DEVICE_ID, "ESP32-BLE-POC"))
        tracking.cancel()
    }

    @Test
    fun `a failed connection remembers nothing`() = runTest {
        val repository = FakeKnownDeviceRepository()
        client.connectFailure = BleError.ConnectionTimeout
        val tracking = launch { track(repository) }
        runCurrent()

        ConnectToDeviceUseCase(gattRepository)(DEVICE_ID)
        runCurrent()

        assertThat(repository.current).isNull()
        assertThat(repository.saveCount).isEqualTo(0)
        tracking.cancel()
    }

    @Test
    fun `connecting to another device replaces the known one`() = runTest {
        val repository = FakeKnownDeviceRepository(KnownDevice(DEVICE_ID, "ESP32-BLE-POC"))
        sessionStore.publish(listOf(bleDevice(id = OTHER_DEVICE_ID, name = "Venu Sq")))
        val tracking = launch { track(repository) }
        runCurrent()

        ConnectToDeviceUseCase(gattRepository)(OTHER_DEVICE_ID)
        runCurrent()

        assertThat(repository.current).isEqualTo(KnownDevice(OTHER_DEVICE_ID, "Venu Sq"))
        tracking.cancel()
    }

    @Test
    fun `reconnecting without a scan keeps the remembered name`() = runTest {
        val known = KnownDevice(DEVICE_ID, "ESP32-BLE-POC")
        val repository = FakeKnownDeviceRepository(known)
        val tracking = launch { track(repository) }
        runCurrent()

        ConnectToDeviceUseCase(gattRepository)(DEVICE_ID)
        runCurrent()

        assertThat(repository.current).isEqualTo(known)
        assertThat(repository.saveCount).isEqualTo(0)
        tracking.cancel()
    }

    @Test
    fun `a scan that sees the known device updates it instead of adding another`() = runTest {
        val repository = FakeKnownDeviceRepository(KnownDevice(DEVICE_ID, name = null))
        val tracking = launch { track(repository) }
        runCurrent()

        sessionStore.publish(
            listOf(
                bleDevice(id = OTHER_DEVICE_ID, name = "Venu Sq"),
                bleDevice(id = DEVICE_ID, name = "ESP32-BLE-POC"),
            ),
        )
        runCurrent()

        assertThat(repository.current).isEqualTo(KnownDevice(DEVICE_ID, "ESP32-BLE-POC"))
        assertThat(repository.saveCount).isEqualTo(1)
        tracking.cancel()
    }

    @Test
    fun `a storage failure does not stop the tracking`() = runTest {
        val repository = FakeKnownDeviceRepository()
        repository.saveFailure = IOException("disk full")
        val tracking = launch { track(repository) }
        runCurrent()

        ConnectToDeviceUseCase(gattRepository)(DEVICE_ID)
        runCurrent()
        repository.saveFailure = null
        client.disconnect()
        runCurrent()
        ConnectToDeviceUseCase(gattRepository)(DEVICE_ID)
        runCurrent()

        assertThat(tracking.isActive).isTrue()
        assertThat(repository.current).isEqualTo(KnownDevice(DEVICE_ID, name = null))
        tracking.cancel()
    }

    @Test
    fun `a forgotten device is not remembered again by a later scan`() = runTest {
        val repository = FakeKnownDeviceRepository(KnownDevice(DEVICE_ID, name = null))
        val tracking = launch { track(repository) }
        runCurrent()

        repository.clear()
        runCurrent()
        sessionStore.publish(listOf(bleDevice(id = DEVICE_ID, name = "ESP32-BLE-POC")))
        runCurrent()

        assertThat(repository.current).isNull()
        assertThat(repository.saveCount).isEqualTo(0)
        tracking.cancel()
    }

    @Test
    fun `a forgotten device is remembered again once it is connected to again`() = runTest {
        val repository = FakeKnownDeviceRepository(KnownDevice(DEVICE_ID, "ESP32-BLE-POC"))
        val tracking = launch { track(repository) }
        runCurrent()
        forget(repository)
        runCurrent()

        sessionStore.publish(listOf(bleDevice(id = DEVICE_ID, name = "ESP32-BLE-POC")))
        ConnectToDeviceUseCase(gattRepository)(DEVICE_ID)
        runCurrent()

        assertThat(repository.current).isEqualTo(KnownDevice(DEVICE_ID, "ESP32-BLE-POC"))
        tracking.cancel()
    }

    // --- Forgetting -----------------------------------------------------------------------

    @Test
    fun `forgetting a connected known device closes the link before removing it`() = runTest {
        val repository = FakeKnownDeviceRepository(KnownDevice(DEVICE_ID, "ESP32-BLE-POC"))
        ConnectToDeviceUseCase(gattRepository)(DEVICE_ID)

        val result = forget(repository)

        assertThat(result.isSuccess).isTrue()
        assertThat(client.disconnectCount).isEqualTo(1)
        assertThat(client.connection.value).isEqualTo(GattConnection.Disconnected)
        assertThat(repository.current).isNull()
    }

    @Test
    fun `forgetting leaves a link to another device open`() = runTest {
        val repository = FakeKnownDeviceRepository(KnownDevice(DEVICE_ID, "ESP32-BLE-POC"))
        ConnectToDeviceUseCase(gattRepository)(OTHER_DEVICE_ID)

        forget(repository)

        assertThat(client.disconnectCount).isEqualTo(0)
        assertThat(client.connection.value.isConnectedTo(OTHER_DEVICE_ID)).isTrue()
        assertThat(repository.current).isNull()
    }

    @Test
    fun `forgetting without a known device does nothing`() = runTest {
        val repository = FakeKnownDeviceRepository()

        assertThat(forget(repository).isSuccess).isTrue()
        assertThat(repository.clearCount).isEqualTo(0)
    }

    @Test
    fun `a storage failure while forgetting is reported and the device stays known`() = runTest {
        val known = KnownDevice(DEVICE_ID, "ESP32-BLE-POC")
        val repository = FakeKnownDeviceRepository(known)
        repository.clearFailure = IOException("disk full")

        val failure = forget(repository).exceptionOrNull()

        assertThat((failure as BleException).error).isEqualTo(BleError.KnownDeviceNotForgotten)
        assertThat(repository.current).isEqualTo(known)
    }

    private suspend fun forget(repository: FakeKnownDeviceRepository) =
        ForgetKnownDeviceUseCase(gattRepository, repository)()

    private suspend fun track(repository: FakeKnownDeviceRepository) =
        TrackKnownDeviceUseCase(gattRepository, sessionStore, repository)()
}
