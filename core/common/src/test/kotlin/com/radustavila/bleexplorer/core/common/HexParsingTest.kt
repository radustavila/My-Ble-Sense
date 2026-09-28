package com.radustavila.bleexplorer.core.common

import com.google.common.truth.Truth.assertThat
import com.radustavila.bleexplorer.core.common.format.parseHexOrNull
import com.radustavila.bleexplorer.core.common.format.toHexString
import com.radustavila.bleexplorer.core.common.format.toPrintableUtf8OrNull
import org.junit.Test

class HexParsingTest {

    @Test
    fun `parses hex with and without separators`() {
        val expected = byteArrayOf(0x01, 0xFF.toByte(), 0x2A)

        assertThat("01FF2A".parseHexOrNull()).isEqualTo(expected)
        assertThat("01 FF 2A".parseHexOrNull()).isEqualTo(expected)
        assertThat("01:ff:2a".parseHexOrNull()).isEqualTo(expected)
        assertThat("0x01-FF-2A".parseHexOrNull()).isEqualTo(expected)
    }

    @Test
    fun `rejects malformed input instead of guessing`() {
        assertThat("".parseHexOrNull()).isNull()
        assertThat("   ".parseHexOrNull()).isNull()
        assertThat("0".parseHexOrNull()).isNull()
        assertThat("01F".parseHexOrNull()).isNull()
        assertThat("ZZ".parseHexOrNull()).isNull()
        assertThat("01 G2".parseHexOrNull()).isNull()
    }

    @Test
    fun `round trips through the hex formatter`() {
        val bytes = byteArrayOf(0x00, 0x7F, 0x80.toByte(), 0xFF.toByte())

        assertThat(bytes.toHexString().parseHexOrNull()).isEqualTo(bytes)
    }

    @Test
    fun `printable text is decoded and binary payloads are not`() {
        assertThat("Venu Sq".toByteArray().toPrintableUtf8OrNull()).isEqualTo("Venu Sq")
        assertThat(byteArrayOf(0x00, 0x01, 0x02).toPrintableUtf8OrNull()).isNull()
        assertThat(byteArrayOf(0xC3.toByte(), 0x28).toPrintableUtf8OrNull()).isNull()
        assertThat(ByteArray(0).toPrintableUtf8OrNull()).isNull()
    }
}
