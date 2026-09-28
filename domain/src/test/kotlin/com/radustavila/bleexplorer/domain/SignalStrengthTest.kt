package com.radustavila.bleexplorer.domain

import com.google.common.truth.Truth.assertThat
import com.radustavila.bleexplorer.domain.model.SignalStrength
import org.junit.Test

class SignalStrengthTest {

    @Test
    fun `buckets rssi values`() {
        assertThat(SignalStrength.fromRssi(-35)).isEqualTo(SignalStrength.STRONG)
        assertThat(SignalStrength.fromRssi(-60)).isEqualTo(SignalStrength.STRONG)
        assertThat(SignalStrength.fromRssi(-61)).isEqualTo(SignalStrength.MEDIUM)
        assertThat(SignalStrength.fromRssi(-80)).isEqualTo(SignalStrength.MEDIUM)
        assertThat(SignalStrength.fromRssi(-81)).isEqualTo(SignalStrength.WEAK)
    }
}
