package com.radustavila.bleexplorer.core.common

import com.google.common.truth.Truth.assertThat
import com.radustavila.bleexplorer.core.common.format.toCompanyIdHex
import com.radustavila.bleexplorer.core.common.format.toHexString
import org.junit.Test

class FormattingTest {

    @Test
    fun `formats payload bytes as upper case hex`() {
        val payload = byteArrayOf(0x02, 0x01, 0x06, 0xFF.toByte(), 0x00)

        assertThat(payload.toHexString()).isEqualTo("02 01 06 FF 00")
    }

    @Test
    fun `formats company ids the way the bluetooth spec writes them`() {
        assertThat(0x4C.toCompanyIdHex()).isEqualTo("0x004C")
    }
}
