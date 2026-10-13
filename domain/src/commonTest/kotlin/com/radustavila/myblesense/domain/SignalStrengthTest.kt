package com.radustavila.myblesense.domain

import com.radustavila.myblesense.domain.testing.assertThat
import com.radustavila.myblesense.domain.model.SignalStrength
import kotlin.test.Test

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
