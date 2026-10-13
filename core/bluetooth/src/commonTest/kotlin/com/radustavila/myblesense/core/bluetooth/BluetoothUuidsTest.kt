package com.radustavila.myblesense.core.bluetooth

import com.radustavila.myblesense.domain.testing.assertThat
import com.radustavila.myblesense.core.bluetooth.mapper.CompanyIdentifiers
import com.radustavila.myblesense.core.bluetooth.mapper.shortenBluetoothUuid
import kotlin.test.Test

class BluetoothUuidsTest {

    @Test
    fun `shortens 16 bit sig uuids`() {
        assertThat("0000180d-0000-1000-8000-00805f9b34fb".shortenBluetoothUuid())
            .isEqualTo("180D")
    }

    @Test
    fun `shortens 32 bit sig uuids`() {
        assertThat("1234abcd-0000-1000-8000-00805f9b34fb".shortenBluetoothUuid())
            .isEqualTo("1234ABCD")
    }

    @Test
    fun `keeps vendor specific uuids intact`() {
        val vendorUuid = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"

        assertThat(vendorUuid.shortenBluetoothUuid()).isEqualTo(vendorUuid.uppercase())
    }

    @Test
    fun `resolves known company identifiers only`() {
        assertThat(CompanyIdentifiers.nameOf(0x004C)).isEqualTo("Apple")
        assertThat(CompanyIdentifiers.nameOf(0x0087)).isEqualTo("Garmin International")
        assertThat(CompanyIdentifiers.nameOf(0xFFFF)).isNull()
    }
}
