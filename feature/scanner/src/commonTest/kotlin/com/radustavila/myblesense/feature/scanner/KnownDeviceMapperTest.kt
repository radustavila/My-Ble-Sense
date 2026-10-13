package com.radustavila.myblesense.feature.scanner

import com.radustavila.myblesense.domain.testing.assertThat
import com.radustavila.myblesense.domain.model.BleCapability
import com.radustavila.myblesense.domain.model.BlePermissionState
import com.radustavila.myblesense.domain.model.BluetoothState
import com.radustavila.myblesense.domain.model.GattConnection
import com.radustavila.myblesense.domain.model.GattConnectionState
import com.radustavila.myblesense.domain.model.KnownDevice
import com.radustavila.myblesense.domain.testing.bleDevice
import kotlin.test.Test

private const val KNOWN_ID = "AA:BB:CC:DD:EE:FF"

class KnownDeviceMapperTest {

    private val ready = ScannerUiState(
        bluetoothState = BluetoothState.ENABLED,
        permissionState = BlePermissionState.GRANTED,
        knownDevice = KnownDevice(KNOWN_ID, "ESP32-BLE-POC"),
    )

    @Test
    fun `there is no entry without a known device or without BLE hardware`() {
        assertThat(ready.copy(knownDevice = null).toKnownDeviceUi()).isNull()
        assertThat(ready.copy(capability = BleCapability.Unsupported).toKnownDeviceUi()).isNull()
    }

    @Test
    fun `forget is offered in every state and nothing else while it runs`() {
        val connected = ready.copy(
            connection = GattConnection(KNOWN_ID, GattConnectionState.CONNECTED, true),
        )
        assertThat(ready.toKnownDeviceUi()?.canForget).isTrue()
        assertThat(connected.toKnownDeviceUi()?.canForget).isTrue()
        assertThat(ready.copy(bluetoothState = BluetoothState.DISABLED).toKnownDeviceUi()?.canForget)
            .isTrue()

        val forgetting = connected.copy(isForgettingKnownDevice = true).toKnownDeviceUi()

        assertThat(forgetting?.isForgetting).isTrue()
        assertThat(forgetting?.canForget).isFalse()
        assertThat(forgetting?.canDisconnect).isFalse()
        assertThat(forgetting?.canConnect).isFalse()
        assertThat(forgetting?.canCancel).isFalse()
    }

    @Test
    fun `a known device without a name is shown as unknown`() {
        val ui = ready.copy(knownDevice = KnownDevice(KNOWN_ID, name = null)).toKnownDeviceUi()

        assertThat(ui?.name).isEqualTo(UNKNOWN_DEVICE)
    }

    @Test
    fun `the scanned name and signal take precedence over the remembered ones`() {
        val ui = ready.copy(
            devices = listOf(bleDevice(id = KNOWN_ID, name = "ESP32 renamed", rssi = -85)),
        ).toKnownDeviceUi()

        assertThat(ui?.name).isEqualTo("ESP32 renamed")
        assertThat(ui?.rssi).isEqualTo(-85)
        assertThat(ui?.status).isEqualTo(KnownDeviceStatus.AVAILABLE)
    }

    @Test
    fun `a link still discovering services is connecting not connected`() {
        val discovering = GattConnection(KNOWN_ID, GattConnectionState.CONNECTED)
        val discovered = discovering.copy(servicesDiscovered = true)

        assertThat(ready.copy(connection = discovering).toKnownDeviceUi()?.status)
            .isEqualTo(KnownDeviceStatus.CONNECTING)
        assertThat(ready.copy(connection = discovered).toKnownDeviceUi()?.status)
            .isEqualTo(KnownDeviceStatus.CONNECTED)
    }

    @Test
    fun `a link to another device leaves the known device disconnected`() {
        val other = GattConnection("11:22:33:44:55:66", GattConnectionState.CONNECTED, true)

        val ui = ready.copy(connection = other).toKnownDeviceUi()

        assertThat(ui?.status).isEqualTo(KnownDeviceStatus.DISCONNECTED)
        assertThat(ui?.hint).isEqualTo(NOT_CHECKED_YET)
    }

    @Test
    fun `a closing link cannot be acted on`() {
        val closing = GattConnection(KNOWN_ID, GattConnectionState.DISCONNECTING)

        val ui = ready.copy(connection = closing).toKnownDeviceUi()!!

        assertThat(ui.status).isEqualTo(KnownDeviceStatus.DISCONNECTING)
        assertThat(ui.canConnect || ui.canDisconnect || ui.canCancel).isFalse()
        assertThat(ui.isBusy).isTrue()
    }

    @Test
    fun `a permission that was not checked yet does not block connecting`() {
        val ui = ready.copy(permissionState = BlePermissionState.UNKNOWN).toKnownDeviceUi()

        assertThat(ui?.status).isEqualTo(KnownDeviceStatus.DISCONNECTED)
    }

    @Test
    fun `a denied permission and Bluetooth off say what to do`() {
        val denied = ready.copy(permissionState = BlePermissionState.PERMANENTLY_DENIED)
            .toKnownDeviceUi()
        val off = ready.copy(bluetoothState = BluetoothState.TURNING_OFF).toKnownDeviceUi()

        assertThat(denied?.status).isEqualTo(KnownDeviceStatus.PERMISSION_REQUIRED)
        assertThat(denied?.hint).isEqualTo("Grant the nearby devices permission to connect.")
        assertThat(off?.status).isEqualTo(KnownDeviceStatus.BLUETOOTH_OFF)
    }
}
