package com.radustavila.bleexplorer.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.radustavila.bleexplorer.domain.model.KnownDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Runs against a real DataStore file in a temporary folder, as the app does on the device. */
class DataStoreKnownDeviceRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val file: File by lazy { File(tmp.root, "known_device.preferences_pb") }

    private fun TestScope.dataStore(scope: CoroutineScope = backgroundScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })

    private fun TestScope.repository() = DataStoreKnownDeviceRepository(dataStore())

    @Test
    fun `there is no known device before the first connection`() = runTest {
        assertThat(repository().knownDevice.first()).isNull()
    }

    @Test
    fun `a saved device is read back`() = runTest {
        val repository = repository()

        val result = repository.save(KnownDevice("AA:BB:CC:DD:EE:FF", "ESP32-BLE-POC"))

        assertThat(result.isSuccess).isTrue()
        assertThat(repository.knownDevice.first())
            .isEqualTo(KnownDevice("AA:BB:CC:DD:EE:FF", "ESP32-BLE-POC"))
    }

    @Test
    fun `saving another device replaces the previous one, name included`() = runTest {
        val repository = repository()
        repository.save(KnownDevice("AA:BB:CC:DD:EE:FF", "ESP32-BLE-POC"))

        repository.save(KnownDevice("11:22:33:44:55:66", name = null))

        assertThat(repository.knownDevice.first())
            .isEqualTo(KnownDevice("11:22:33:44:55:66", name = null))
    }

    @Test
    fun `the known device survives an app restart`() = runTest {
        val firstLaunch = Job()
        DataStoreKnownDeviceRepository(dataStore(CoroutineScope(coroutineContext + firstLaunch)))
            .save(KnownDevice("AA:BB:CC:DD:EE:FF", "ESP32-BLE-POC"))
        // Only one DataStore may be active per file: the first "process" has to be gone.
        firstLaunch.cancelAndJoin()

        val secondLaunch = repository()

        assertThat(secondLaunch.knownDevice.first())
            .isEqualTo(KnownDevice("AA:BB:CC:DD:EE:FF", "ESP32-BLE-POC"))
    }

    @Test
    fun `observers see the device being replaced`() = runTest {
        val repository = repository()

        repository.knownDevice.test {
            assertThat(awaitItem()).isNull()
            repository.save(KnownDevice("AA:BB:CC:DD:EE:FF", "ESP32-BLE-POC"))
            assertThat(awaitItem()?.id).isEqualTo("AA:BB:CC:DD:EE:FF")
            repository.save(KnownDevice("11:22:33:44:55:66", "Venu Sq"))
            assertThat(awaitItem()?.id).isEqualTo("11:22:33:44:55:66")
        }
    }

    @Test
    fun `an unreadable file means no known device instead of a crash`() = runTest {
        file.writeText("definitely not a preferences protobuf")

        assertThat(repository().knownDevice.first()).isNull()
    }
}
