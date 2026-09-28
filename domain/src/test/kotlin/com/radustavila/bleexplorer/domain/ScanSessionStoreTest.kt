package com.radustavila.bleexplorer.domain

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.radustavila.bleexplorer.domain.session.InMemoryScanSessionStore
import com.radustavila.bleexplorer.domain.testing.bleDevice
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ScanSessionStoreTest {

    private val store = InMemoryScanSessionStore()

    @Test
    fun `observes a single device by id and follows its updates`() = runTest {
        store.observeDevice("A").test {
            assertThat(awaitItem()).isNull()

            store.publish(listOf(bleDevice(id = "A", rssi = -50)))
            assertThat(awaitItem()?.rssi).isEqualTo(-50)

            store.publish(listOf(bleDevice(id = "A", rssi = -90)))
            assertThat(awaitItem()?.rssi).isEqualTo(-90)

            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clear empties the session`() = runTest {
        store.publish(listOf(bleDevice(id = "A")))

        store.clear()

        assertThat(store.devices.value).isEmpty()
    }
}
