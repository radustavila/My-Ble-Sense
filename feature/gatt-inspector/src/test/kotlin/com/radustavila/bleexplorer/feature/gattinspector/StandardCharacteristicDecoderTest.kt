package com.radustavila.bleexplorer.feature.gattinspector

import com.google.common.truth.Truth.assertThat
import org.junit.Test

private const val BATTERY_LEVEL = "00002a19-0000-1000-8000-00805f9b34fb"
private const val HEART_RATE = "00002a37-0000-1000-8000-00805f9b34fb"
private const val MANUFACTURER_NAME = "00002a29-0000-1000-8000-00805f9b34fb"
private const val VENDOR_SPECIFIC = "12345678-1234-5678-1234-56789abcdef0"

class StandardCharacteristicDecoderTest {

    @Test
    fun `battery level is shown as a percentage`() {
        assertThat(decode(BATTERY_LEVEL, byteArrayOf(0x4C))).isEqualTo("76%")
    }

    @Test
    fun `a battery level of the wrong length is not decoded`() {
        assertThat(decode(BATTERY_LEVEL, byteArrayOf(0x4C, 0x00))).isNull()
    }

    @Test
    fun `heart rate is decoded for both measurement widths`() {
        // Flags = 0x00: 8-bit measurement.
        assertThat(decode(HEART_RATE, byteArrayOf(0x00, 0x48))).isEqualTo("72 bpm")
        // Flags = 0x01: 16-bit measurement, little endian.
        assertThat(decode(HEART_RATE, byteArrayOf(0x01, 0x48, 0x01))).isEqualTo("328 bpm")
    }

    @Test
    fun `a truncated heart rate payload is not decoded`() {
        assertThat(decode(HEART_RATE, byteArrayOf(0x01, 0x48))).isNull()
    }

    @Test
    fun `string characteristics are decoded as text`() {
        assertThat(decode(MANUFACTURER_NAME, "Garmin".toByteArray())).isEqualTo("Garmin")
    }

    @Test
    fun `vendor specific characteristics are never interpreted`() {
        assertThat(decode(VENDOR_SPECIFIC, byteArrayOf(0x4C))).isNull()
    }

    @Test
    fun `an empty payload is never interpreted`() {
        assertThat(decode(BATTERY_LEVEL, ByteArray(0))).isNull()
    }

    private fun decode(uuid: String, value: ByteArray): String? =
        StandardCharacteristicDecoder.decode(uuid, value)
}
