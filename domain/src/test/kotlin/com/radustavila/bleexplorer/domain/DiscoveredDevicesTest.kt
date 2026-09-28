package com.radustavila.bleexplorer.domain

import com.google.common.truth.Truth.assertThat
import com.radustavila.bleexplorer.domain.model.DiscoveredDevices
import com.radustavila.bleexplorer.domain.testing.bleDevice
import org.junit.Test

class DiscoveredDevicesTest {

    @Test
    fun `new device is inserted`() {
        val result = DiscoveredDevices.EMPTY.upsert(bleDevice(id = "A", name = "Watch"))

        assertThat(result.size).isEqualTo(1)
        assertThat(result.devices.single().displayName).isEqualTo("Watch")
    }

    @Test
    fun `same device advertised twice does not create a second entry`() {
        val result = DiscoveredDevices.of(
            bleDevice(id = "A", rssi = -50),
            bleDevice(id = "A", rssi = -70),
        )

        assertThat(result.size).isEqualTo(1)
    }

    @Test
    fun `rssi is updated when a known device is seen again`() {
        val result = DiscoveredDevices.of(
            bleDevice(id = "A", rssi = -50),
            bleDevice(id = "A", rssi = -77),
        )

        assertThat(result.devices.single().rssi).isEqualTo(-77)
    }

    @Test
    fun `devices with the same name but different ids are kept apart`() {
        val result = DiscoveredDevices.of(
            bleDevice(id = "A", name = "Sensor"),
            bleDevice(id = "B", name = "Sensor"),
        )

        assertThat(result.size).isEqualTo(2)
    }

    @Test
    fun `known name is kept when a later advertisement has none`() {
        val result = DiscoveredDevices.of(
            bleDevice(id = "A", name = "Venu Sq"),
            bleDevice(id = "A", name = null, rssi = -80),
        )

        val device = result.devices.single()
        assertThat(device.name).isEqualTo("Venu Sq")
        assertThat(device.rssi).isEqualTo(-80)
    }

    @Test
    fun `advertising data is refreshed when it changes and kept when absent`() {
        val result = DiscoveredDevices.of(
            bleDevice(id = "A", serviceUuids = listOf("1800"), txPowerDbm = 4),
            bleDevice(id = "A", serviceUuids = listOf("180d", "180f")),
        )

        val device = result.devices.single()
        assertThat(device.serviceUuids).containsExactly("180d", "180f").inOrder()
        assertThat(device.txPowerDbm).isEqualTo(4)
    }

    @Test
    fun `first seen timestamp and seen count survive updates`() {
        val result = DiscoveredDevices.of(
            bleDevice(id = "A", firstSeenAtMillis = 100, lastSeenAtMillis = 100),
            bleDevice(id = "A", firstSeenAtMillis = 500, lastSeenAtMillis = 500),
        )

        val device = result.devices.single()
        assertThat(device.firstSeenAtMillis).isEqualTo(100)
        assertThat(device.lastSeenAtMillis).isEqualTo(500)
        assertThat(device.seenCount).isEqualTo(2)
    }

    @Test
    fun `discovery order is preserved across updates`() {
        val result = DiscoveredDevices.of(
            bleDevice(id = "A"),
            bleDevice(id = "B"),
            bleDevice(id = "A", rssi = -30),
        )

        assertThat(result.devices.map { it.id }).containsExactly("A", "B").inOrder()
    }
}
