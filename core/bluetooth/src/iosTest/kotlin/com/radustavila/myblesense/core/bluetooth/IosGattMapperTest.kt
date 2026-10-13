package com.radustavila.myblesense.core.bluetooth

import com.radustavila.myblesense.core.bluetooth.mapper.attErrorCodeToBleError
import com.radustavila.myblesense.core.bluetooth.mapper.disconnectionToBleError
import com.radustavila.myblesense.core.bluetooth.mapper.expandBluetoothUuid
import com.radustavila.myblesense.core.bluetooth.mapper.toByteArray
import com.radustavila.myblesense.core.bluetooth.mapper.toDomainProperties
import com.radustavila.myblesense.core.bluetooth.mapper.toDomainState
import com.radustavila.myblesense.core.bluetooth.mapper.toNSData
import com.radustavila.myblesense.core.bluetooth.scanner.toManufacturerData
import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.BluetoothState
import com.radustavila.myblesense.domain.model.GattCharacteristicProperty
import com.radustavila.myblesense.domain.testing.assertThat
import platform.CoreBluetooth.CBManagerStatePoweredOff
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBManagerStateResetting
import platform.CoreBluetooth.CBManagerStateUnauthorized
import platform.CoreBluetooth.CBManagerStateUnknown
import kotlin.test.Test

class IosGattMapperTest {

    @Test
    fun `a 16-bit CoreBluetooth UUID is expanded to the full lower case form`() {
        assertThat(expandBluetoothUuid("180D")).isEqualTo("0000180d-0000-1000-8000-00805f9b34fb")
    }

    @Test
    fun `a 32-bit CoreBluetooth UUID is expanded to the full lower case form`() {
        assertThat(expandBluetoothUuid("1234ABCD")).isEqualTo("1234abcd-0000-1000-8000-00805f9b34fb")
    }

    @Test
    fun `a 128-bit CoreBluetooth UUID is only lower cased`() {
        assertThat(expandBluetoothUuid("12345678-1234-1234-1234-123456789ABC"))
            .isEqualTo("12345678-1234-1234-1234-123456789abc")
    }

    @Test
    fun `characteristic properties are read from the specification bits`() {
        assertThat(0x02uL.toDomainProperties()).containsExactly(GattCharacteristicProperty.READ)
        assertThat((0x04uL or 0x08uL).toDomainProperties()).containsExactly(
            GattCharacteristicProperty.WRITE_NO_RESPONSE,
            GattCharacteristicProperty.WRITE,
        )
        assertThat((0x10uL or 0x20uL).toDomainProperties()).containsExactly(
            GattCharacteristicProperty.NOTIFY,
            GattCharacteristicProperty.INDICATE,
        )
    }

    @Test
    fun `properties the app does not use are ignored`() {
        // Broadcast (0x01) and authenticated signed writes (0x40).
        assertThat((0x01uL or 0x40uL).toDomainProperties()).isEmpty()
    }

    @Test
    fun `only powered on counts as enabled and only powered off as disabled`() {
        assertThat(CBManagerStatePoweredOn.toDomainState()).isEqualTo(BluetoothState.ENABLED)
        assertThat(CBManagerStatePoweredOff.toDomainState()).isEqualTo(BluetoothState.DISABLED)
        assertThat(CBManagerStateUnknown.toDomainState()).isEqualTo(BluetoothState.UNKNOWN)
        assertThat(CBManagerStateResetting.toDomainState()).isEqualTo(BluetoothState.UNKNOWN)
        assertThat(CBManagerStateUnauthorized.toDomainState()).isEqualTo(BluetoothState.UNKNOWN)
    }

    @Test
    fun `not permitted ATT errors mean the operation is unsupported`() {
        listOf(2, 3, 6).forEach { code ->
            assertThat(attErrorCodeToBleError(code) { BleError.CharacteristicReadFailed(it) })
                .isEqualTo(BleError.CharacteristicOperationUnsupported)
        }
    }

    @Test
    fun `security ATT errors keep their code`() {
        assertThat(attErrorCodeToBleError(5) { BleError.CharacteristicReadFailed(it) })
            .isEqualTo(BleError.UnknownGattError(5))
        assertThat(attErrorCodeToBleError(15) { BleError.CharacteristicWriteFailed(it) })
            .isEqualTo(BleError.UnknownGattError(15))
    }

    @Test
    fun `other ATT errors fall back to the failure of the operation`() {
        assertThat(attErrorCodeToBleError(14) { BleError.CharacteristicWriteFailed(it) })
            .isEqualTo(BleError.CharacteristicWriteFailed(14))
    }

    @Test
    fun `a disconnect without an error is a plain disconnect`() {
        assertThat(disconnectionToBleError(errorCode = null, wasConnected = true))
            .isEqualTo(BleError.Disconnected)
    }

    @Test
    fun `a connection timeout is reported as such`() {
        assertThat(disconnectionToBleError(errorCode = 6, wasConnected = true))
            .isEqualTo(BleError.ConnectionTimeout)
    }

    @Test
    fun `losing an established link is a disconnect whatever the code`() {
        assertThat(disconnectionToBleError(errorCode = 7, wasConnected = true))
            .isEqualTo(BleError.Disconnected)
        assertThat(disconnectionToBleError(errorCode = 0, wasConnected = true))
            .isEqualTo(BleError.Disconnected)
    }

    @Test
    fun `failing before the link was established is a connection failure`() {
        assertThat(disconnectionToBleError(errorCode = 10, wasConnected = false))
            .isEqualTo(BleError.ConnectionFailed(10))
    }

    @Test
    fun `manufacturer data is split into the company identifier and its payload`() {
        val data = byteArrayOf(0x4C, 0x00, 0x02, 0x15).toManufacturerData().single()

        assertThat(data.companyId).isEqualTo(0x004C)
        assertThat(data.payload).isEqualTo(byteArrayOf(0x02, 0x15))
    }

    @Test
    fun `manufacturer data too short for a company identifier is dropped`() {
        assertThat(byteArrayOf(0x4C).toManufacturerData()).isEmpty()
    }

    @Test
    fun `bytes survive the round trip through NSData`() {
        val bytes = byteArrayOf(0x00, 0x7F, -0x80, -0x01)

        assertThat(bytes.toNSData().toByteArray()).isEqualTo(bytes)
        assertThat(ByteArray(0).toNSData().toByteArray()).isEmpty()
    }
}
