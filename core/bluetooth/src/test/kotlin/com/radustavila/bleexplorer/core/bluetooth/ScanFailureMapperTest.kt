package com.radustavila.bleexplorer.core.bluetooth

import com.google.common.truth.Truth.assertThat
import com.radustavila.bleexplorer.core.bluetooth.mapper.scanErrorCodeToBleError
import com.radustavila.bleexplorer.domain.model.BleError
import org.junit.Test

class ScanFailureMapperTest {

    @Test
    fun `maps already started`() {
        assertThat(scanErrorCodeToBleError(1)).isEqualTo(BleError.ScanAlreadyStarted)
    }

    @Test
    fun `maps registration failure`() {
        assertThat(scanErrorCodeToBleError(2))
            .isEqualTo(BleError.ScanFailed(2, "Application registration failed"))
    }

    @Test
    fun `maps unsupported feature`() {
        assertThat(scanErrorCodeToBleError(4))
            .isEqualTo(BleError.ScanFailed(4, "Scan feature unsupported"))
    }

    @Test
    fun `maps internal error`() {
        assertThat(scanErrorCodeToBleError(3))
            .isEqualTo(BleError.ScanFailed(3, "Internal scanner error"))
    }

    @Test
    fun `maps hardware exhaustion and throttling`() {
        assertThat(scanErrorCodeToBleError(5))
            .isEqualTo(BleError.ScanFailed(5, "Out of hardware resources"))
        assertThat(scanErrorCodeToBleError(6)).isEqualTo(BleError.ScanThrottled)
    }

    @Test
    fun `keeps unknown codes as ScanFailed`() {
        assertThat(scanErrorCodeToBleError(42)).isEqualTo(BleError.ScanFailed(42))
    }
}
