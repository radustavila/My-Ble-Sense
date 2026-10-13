package com.radustavila.myblesense.domain

import app.cash.turbine.test
import com.radustavila.myblesense.domain.testing.assertThat
import com.radustavila.myblesense.domain.model.BleCapability
import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.BleException
import com.radustavila.myblesense.domain.model.BleScanSettings
import com.radustavila.myblesense.domain.model.BluetoothState
import com.radustavila.myblesense.domain.session.InMemoryScanSessionStore
import com.radustavila.myblesense.domain.testing.FakeBleScanRepository
import com.radustavila.myblesense.domain.testing.bleDevice
import com.radustavila.myblesense.domain.usecase.StartBleScanUseCase
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.seconds

class StartBleScanUseCaseTest {

    private val repository = FakeBleScanRepository()
    private val sessionStore = InMemoryScanSessionStore()
    private val startScan = StartBleScanUseCase(repository, sessionStore)

    private val settings = BleScanSettings(duration = 15.seconds)

    @Test
    fun `starts the platform scan only while collected and stops afterwards`() = runTest {
        assertThat(repository.scanner.scanStartedCount).isEqualTo(0)

        startScan(settings).test {
            repository.scanner.emit(bleDevice(id = "A"))
            awaitItem()
            assertThat(repository.scanner.isScanning).isTrue()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(repository.scanner.scanStartedCount).isEqualTo(1)
        assertThat(repository.scanner.scanStoppedCount).isEqualTo(1)
    }

    @Test
    fun `emits the accumulated device list on every advertisement`() = runTest {
        startScan(settings).test {
            repository.scanner.emit(bleDevice(id = "A", rssi = -50))
            assertThat(awaitItem().map { it.id }).containsExactly("A")

            repository.scanner.emit(bleDevice(id = "B", rssi = -70))
            assertThat(awaitItem().map { it.id }).containsExactly("A", "B").inOrder()

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `duplicate advertisement updates rssi instead of adding a row`() = runTest {
        startScan(settings).test {
            repository.scanner.emit(bleDevice(id = "A", rssi = -50))
            awaitItem()

            repository.scanner.emit(bleDevice(id = "A", rssi = -85))
            val devices = awaitItem()

            assertThat(devices).hasSize(1)
            assertThat(devices.single().rssi).isEqualTo(-85)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `scan completes and stops the scanner when the timeout elapses`() = runTest {
        startScan(BleScanSettings(duration = 15.seconds)).test {
            repository.scanner.emit(bleDevice(id = "A"))
            awaitItem()

            advanceTimeBy(15.seconds)

            awaitComplete()
        }

        assertThat(repository.scanner.isScanning).isFalse()
    }

    @Test
    fun `results are published to the session store for the details screen`() = runTest {
        startScan(settings).test {
            repository.scanner.emit(bleDevice(id = "A", name = "Sensor"))
            awaitItem()

            assertThat(sessionStore.devices.value.map { it.id }).containsExactly("A")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fails with BluetoothDisabled when the adapter is off`() = runTest {
        repository.bluetoothState.value = BluetoothState.DISABLED

        startScan(settings).test {
            val error = awaitError()
            assertThat((error as BleException).error).isEqualTo(BleError.BluetoothDisabled)
        }
    }

    @Test
    fun `fails with BleUnsupported when the hardware has no BLE`() = runTest {
        repository.capability = BleCapability.Unsupported

        startScan(settings).test {
            val error = awaitError()
            assertThat((error as BleException).error).isEqualTo(BleError.BleUnsupported)
        }
    }

    @Test
    fun `surfaces scanner failures as BleException`() = runTest {
        repository.scanner.failWith = BleError.ScanFailed(code = 2)

        startScan(settings).test {
            val error = awaitError()
            assertThat((error as BleException).error).isEqualTo(BleError.ScanFailed(code = 2))
        }
    }

    @Test
    fun `passes the requested settings down to the scanner`() = runTest {
        val custom = BleScanSettings(duration = 5.seconds)

        startScan(custom).test {
            repository.scanner.emit(bleDevice(id = "A"))
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(repository.scanner.lastSettings).isEqualTo(custom)
    }
}
