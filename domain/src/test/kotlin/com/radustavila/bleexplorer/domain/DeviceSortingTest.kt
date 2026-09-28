package com.radustavila.bleexplorer.domain

import com.google.common.truth.Truth.assertThat
import com.radustavila.bleexplorer.domain.model.DeviceSortOrder
import com.radustavila.bleexplorer.domain.testing.bleDevice
import com.radustavila.bleexplorer.domain.usecase.SortDevicesUseCase
import org.junit.Test

class DeviceSortingTest {

    private val sortDevices = SortDevicesUseCase()

    private val devices = listOf(
        bleDevice(id = "A", name = "zebra sensor", rssi = -90, firstSeenAtMillis = 30),
        bleDevice(id = "B", name = null, rssi = -40, firstSeenAtMillis = 20),
        bleDevice(id = "C", name = "Alpha", rssi = -70, firstSeenAtMillis = 10),
    )

    @Test
    fun `sorts by strongest signal first`() {
        val sorted = sortDevices(devices, DeviceSortOrder.SIGNAL_STRENGTH)

        assertThat(sorted.map { it.id }).containsExactly("B", "C", "A").inOrder()
    }

    @Test
    fun `sorts by name case insensitively with unnamed devices last`() {
        val sorted = sortDevices(devices, DeviceSortOrder.NAME)

        assertThat(sorted.map { it.id }).containsExactly("C", "A", "B").inOrder()
    }

    @Test
    fun `sorts by discovery time`() {
        val sorted = sortDevices(devices, DeviceSortOrder.FIRST_SEEN)

        assertThat(sorted.map { it.id }).containsExactly("C", "B", "A").inOrder()
    }

    @Test
    fun `sorting is stable for equal keys`() {
        val equal = listOf(bleDevice(id = "B", rssi = -50), bleDevice(id = "A", rssi = -50))

        val sorted = sortDevices(equal, DeviceSortOrder.SIGNAL_STRENGTH)

        assertThat(sorted.map { it.id }).containsExactly("A", "B").inOrder()
    }
}
