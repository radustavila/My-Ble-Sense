package com.radustavila.myblesense.core.bluetooth

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import com.google.common.truth.Truth.assertThat
import com.radustavila.myblesense.core.bluetooth.mapper.BluetoothSigNames
import com.radustavila.myblesense.core.bluetooth.mapper.connectionStatusToBleError
import com.radustavila.myblesense.core.bluetooth.mapper.gattStatusToBleError
import com.radustavila.myblesense.core.bluetooth.mapper.toDomainProperties
import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.GattCharacteristicProperty
import org.junit.Test

class GattMapperTest {

    @Test
    fun `property bit mask is decoded`() {
        val properties = (
            BluetoothGattCharacteristic.PROPERTY_READ or
                BluetoothGattCharacteristic.PROPERTY_NOTIFY
            ).toDomainProperties()

        assertThat(properties).containsExactly(
            GattCharacteristicProperty.READ,
            GattCharacteristicProperty.NOTIFY,
        )
    }

    @Test
    fun `both write modes are distinguished`() {
        val properties = (
            BluetoothGattCharacteristic.PROPERTY_WRITE or
                BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE
            ).toDomainProperties()

        assertThat(properties).containsExactly(
            GattCharacteristicProperty.WRITE,
            GattCharacteristicProperty.WRITE_NO_RESPONSE,
        )
    }

    @Test
    fun `a characteristic without usable properties maps to an empty set`() {
        assertThat(0.toDomainProperties()).isEmpty()
    }

    @Test
    fun `permission statuses map to unsupported rather than to a generic failure`() {
        val error = gattStatusToBleError(BluetoothGatt.GATT_WRITE_NOT_PERMITTED) {
            BleError.CharacteristicWriteFailed(it)
        }

        assertThat(error).isEqualTo(BleError.CharacteristicOperationUnsupported)
    }

    @Test
    fun `an unknown status falls back to the operation specific error`() {
        val error = gattStatusToBleError(status = 200) { BleError.CharacteristicReadFailed(it) }

        assertThat(error).isEqualTo(BleError.CharacteristicReadFailed(200))
    }

    @Test
    fun `failing to establish a link is a connection failure`() {
        val error = connectionStatusToBleError(status = 133, wasConnected = false)

        assertThat(error).isEqualTo(BleError.ConnectionFailed(133))
    }

    @Test
    fun `losing an established link is a disconnect`() {
        val error = connectionStatusToBleError(status = 133, wasConnected = true)

        assertThat(error).isEqualTo(BleError.Disconnected)
    }

    @Test
    fun `the peripheral going out of range is reported as a timeout`() {
        assertThat(connectionStatusToBleError(status = 8, wasConnected = true))
            .isEqualTo(BleError.ConnectionTimeout)
    }

    @Test
    fun `standard UUIDs are named and unknown ones are not`() {
        assertThat(BluetoothSigNames.serviceName("0000180f-0000-1000-8000-00805f9b34fb"))
            .isEqualTo("Battery Service")
        assertThat(BluetoothSigNames.characteristicName("00002a19-0000-1000-8000-00805f9b34fb"))
            .isEqualTo("Battery Level")
        assertThat(BluetoothSigNames.serviceName("12345678-1234-5678-1234-56789abcdef0")).isNull()
        assertThat(BluetoothSigNames.characteristicName("0000ffe1-0000-1000-8000-00805f9b34fb"))
            .isNull()
    }
}
