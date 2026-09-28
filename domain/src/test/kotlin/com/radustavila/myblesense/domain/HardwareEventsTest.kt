package com.radustavila.myblesense.domain

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.radustavila.myblesense.domain.model.HardwareEvent
import com.radustavila.myblesense.domain.model.PocEvent
import com.radustavila.myblesense.domain.model.PocTouchEvents
import com.radustavila.myblesense.domain.model.TouchIntensityAccumulator
import com.radustavila.myblesense.domain.model.TouchIntensityStats
import com.radustavila.myblesense.domain.model.TouchTracker
import com.radustavila.myblesense.domain.testing.FakeBleGattRepository
import com.radustavila.myblesense.domain.testing.POC_EVENTS_ID
import com.radustavila.myblesense.domain.testing.pocGattService
import com.radustavila.myblesense.domain.usecase.ConnectToDeviceUseCase
import com.radustavila.myblesense.domain.usecase.ObserveCharacteristicValuesUseCase
import com.radustavila.myblesense.domain.usecase.ObservePocEventsUseCase
import com.radustavila.myblesense.domain.usecase.ObservePocHardwareEventsUseCase
import com.radustavila.myblesense.domain.usecase.ObservePocServiceUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Test

private const val DEVICE_ID = "AA:BB:CC:DD:EE:FF"

class HardwareEventsTest {

    // --- Intensity parsing ----------------------------------------------------------------

    @Test
    fun `intensity samples are parsed as a percentage`() {
        assertThat(PocTouchEvents.parseIntensity("TOUCH_INTENSITY:94")).isEqualTo(94)
        assertThat(PocTouchEvents.parseIntensity("TOUCH_INTENSITY:0")).isEqualTo(0)
        assertThat(PocTouchEvents.parseIntensity("TOUCH_INTENSITY:100")).isEqualTo(100)
        assertThat(PocTouchEvents.parseIntensity("touch_intensity: 7")).isEqualTo(7)
    }

    @Test
    fun `malformed or out of range intensities are not parsed`() {
        assertThat(PocTouchEvents.parseIntensity("TOUCH_INTENSITY:")).isNull()
        assertThat(PocTouchEvents.parseIntensity("TOUCH_INTENSITY:abc")).isNull()
        assertThat(PocTouchEvents.parseIntensity("TOUCH_INTENSITY:12.5")).isNull()
        assertThat(PocTouchEvents.parseIntensity("TOUCH_INTENSITY:-1")).isNull()
        assertThat(PocTouchEvents.parseIntensity("TOUCH_INTENSITY:101")).isNull()
        assertThat(PocTouchEvents.parseIntensity("TOUCH_ON")).isNull()
        assertThat(PocTouchEvents.parseIntensity("INTENSITY:50")).isNull()
    }

    // --- Intensity statistics -------------------------------------------------------------

    @Test
    fun `no samples means no statistics`() {
        assertThat(TouchIntensityAccumulator().stats()).isNull()
    }

    @Test
    fun `statistics give the peak and the average of the samples`() {
        val accumulator = TouchIntensityAccumulator()
        listOf(60, 94, 88, 82).forEach(accumulator::add)

        assertThat(accumulator.stats())
            .isEqualTo(TouchIntensityStats(peakPercent = 94, averagePercent = 81, sampleCount = 4))
    }

    @Test
    fun `the average is rounded to the nearest whole percent`() {
        val halfUp = TouchIntensityAccumulator().apply { add(50); add(51) }
        val down = TouchIntensityAccumulator().apply { add(10); add(10); add(11) }

        assertThat(halfUp.stats()?.averagePercent).isEqualTo(51)
        assertThat(down.stats()?.averagePercent).isEqualTo(10)
    }

    @Test
    fun `a single sample is both the peak and the average`() {
        val accumulator = TouchIntensityAccumulator().apply { add(0) }

        assertThat(accumulator.stats()).isEqualTo(TouchIntensityStats(0, 0, 1))
    }

    // --- TouchTracker ---------------------------------------------------------------------

    private val tracker = TouchTracker()

    @Test
    fun `a press is reported as touch pressed`() {
        assertThat(tracker.onEvent(PocEvent("TOUCH_ON", timestampMillis = 1_000)))
            .isEqualTo(HardwareEvent.TouchPressed(timestampMillis = 1_000))
    }

    @Test
    fun `a release carries the time elapsed since the press`() {
        tracker.onEvent(PocEvent("TOUCH_ON", timestampMillis = 1_000))

        val released = tracker.onEvent(PocEvent("TOUCH_OFF", timestampMillis = 2_800))

        assertThat(released)
            .isEqualTo(HardwareEvent.TouchReleased(timestampMillis = 2_800, durationMillis = 1_800))
    }

    @Test
    fun `a release without an observed press has an unknown duration`() {
        val released = tracker.onEvent(PocEvent("TOUCH_OFF", timestampMillis = 2_800))

        assertThat(released)
            .isEqualTo(HardwareEvent.TouchReleased(timestampMillis = 2_800, durationMillis = null))
    }

    @Test
    fun `each release is paired with its own press only`() {
        tracker.onEvent(PocEvent("TOUCH_ON", timestampMillis = 0))
        tracker.onEvent(PocEvent("TOUCH_OFF", timestampMillis = 500))

        val second = tracker.onEvent(PocEvent("TOUCH_OFF", timestampMillis = 900))

        assertThat((second as HardwareEvent.TouchReleased).durationMillis).isNull()
    }

    @Test
    fun `a repeated press restarts the measurement`() {
        tracker.onEvent(PocEvent("TOUCH_ON", timestampMillis = 0))
        tracker.onEvent(PocEvent("TOUCH_ON", timestampMillis = 1_000))

        val released = tracker.onEvent(PocEvent("TOUCH_OFF", timestampMillis = 1_300))

        assertThat((released as HardwareEvent.TouchReleased).durationMillis).isEqualTo(300)
    }

    @Test
    fun `a wall clock going backwards never yields a negative duration`() {
        tracker.onEvent(PocEvent("TOUCH_ON", timestampMillis = 5_000))

        val released = tracker.onEvent(PocEvent("TOUCH_OFF", timestampMillis = 4_000))

        assertThat((released as HardwareEvent.TouchReleased).durationMillis).isNull()
    }

    @Test
    fun `touch edges are matched case-insensitively`() {
        assertThat(tracker.onEvent(PocEvent("touch_on", timestampMillis = 0)))
            .isInstanceOf(HardwareEvent.TouchPressed::class.java)
    }

    @Test
    fun `other events are kept with their raw text and do not affect the touch`() {
        tracker.onEvent(PocEvent("TOUCH_ON", timestampMillis = 0))

        val other = tracker.onEvent(PocEvent("EVENT 7", timestampMillis = 100))
        val released = tracker.onEvent(PocEvent("TOUCH_OFF", timestampMillis = 400))

        assertThat(other).isEqualTo(HardwareEvent.Other("EVENT 7", timestampMillis = 100))
        assertThat((released as HardwareEvent.TouchReleased).durationMillis).isEqualTo(400)
    }

    @Test
    fun `intensity samples are reported live`() {
        tracker.onEvent(PocEvent("TOUCH_ON", timestampMillis = 0))

        assertThat(tracker.onEvent(PocEvent("TOUCH_INTENSITY:42", timestampMillis = 100)))
            .isEqualTo(HardwareEvent.TouchIntensity(timestampMillis = 100, percent = 42))
    }

    @Test
    fun `a release summarises the intensity samples of its touch`() {
        tracker.onEvent(PocEvent("TOUCH_ON", timestampMillis = 0))
        listOf(60, 94, 88, 82).forEachIndexed { i, percent ->
            tracker.onEvent(PocEvent("TOUCH_INTENSITY:$percent", timestampMillis = (i + 1) * 100L))
        }

        val released = tracker.onEvent(PocEvent("TOUCH_OFF", timestampMillis = 2_400))

        assertThat(released).isEqualTo(
            HardwareEvent.TouchReleased(
                timestampMillis = 2_400,
                durationMillis = 2_400,
                intensity = TouchIntensityStats(peakPercent = 94, averagePercent = 81, sampleCount = 4),
            ),
        )
    }

    @Test
    fun `a touch without samples has no intensity statistics`() {
        tracker.onEvent(PocEvent("TOUCH_ON", timestampMillis = 0))

        val released = tracker.onEvent(PocEvent("TOUCH_OFF", timestampMillis = 300))

        assertThat((released as HardwareEvent.TouchReleased).intensity).isNull()
    }

    @Test
    fun `samples of one touch are not carried into the next`() {
        tracker.onEvent(PocEvent("TOUCH_ON", timestampMillis = 0))
        tracker.onEvent(PocEvent("TOUCH_INTENSITY:100", timestampMillis = 100))
        tracker.onEvent(PocEvent("TOUCH_OFF", timestampMillis = 200))
        tracker.onEvent(PocEvent("TOUCH_ON", timestampMillis = 1_000))
        tracker.onEvent(PocEvent("TOUCH_INTENSITY:20", timestampMillis = 1_100))

        val released = tracker.onEvent(PocEvent("TOUCH_OFF", timestampMillis = 1_200))

        assertThat((released as HardwareEvent.TouchReleased).intensity)
            .isEqualTo(TouchIntensityStats(peakPercent = 20, averagePercent = 20, sampleCount = 1))
    }

    @Test
    fun `a repeated press discards the samples of the lost touch`() {
        tracker.onEvent(PocEvent("TOUCH_ON", timestampMillis = 0))
        tracker.onEvent(PocEvent("TOUCH_INTENSITY:100", timestampMillis = 100))
        tracker.onEvent(PocEvent("TOUCH_ON", timestampMillis = 1_000))
        tracker.onEvent(PocEvent("TOUCH_INTENSITY:30", timestampMillis = 1_100))

        val released = tracker.onEvent(PocEvent("TOUCH_OFF", timestampMillis = 1_200))

        assertThat((released as HardwareEvent.TouchReleased).intensity?.peakPercent).isEqualTo(30)
    }

    @Test
    fun `samples without an observed press are shown but not summarised`() {
        val sample = tracker.onEvent(PocEvent("TOUCH_INTENSITY:70", timestampMillis = 100))

        val released = tracker.onEvent(PocEvent("TOUCH_OFF", timestampMillis = 200))

        assertThat(sample).isEqualTo(HardwareEvent.TouchIntensity(100, 70))
        assertThat(released).isEqualTo(HardwareEvent.TouchReleased(200, null, null))
    }

    @Test
    fun `a malformed intensity is kept raw and does not affect the statistics`() {
        tracker.onEvent(PocEvent("TOUCH_ON", timestampMillis = 0))
        tracker.onEvent(PocEvent("TOUCH_INTENSITY:50", timestampMillis = 100))

        val malformed = tracker.onEvent(PocEvent("TOUCH_INTENSITY:250", timestampMillis = 200))
        val released = tracker.onEvent(PocEvent("TOUCH_OFF", timestampMillis = 300))

        assertThat(malformed).isEqualTo(HardwareEvent.Other("TOUCH_INTENSITY:250", 200))
        assertThat((released as HardwareEvent.TouchReleased).intensity)
            .isEqualTo(TouchIntensityStats(50, 50, 1))
    }

    // --- ObservePocHardwareEventsUseCase --------------------------------------------------

    private val repository = FakeBleGattRepository()
    private val client = repository.client
    private val observePocService = ObservePocServiceUseCase(repository)
    private val observeHardwareEvents = ObservePocHardwareEventsUseCase(
        observePocService,
        ObservePocEventsUseCase(observePocService, ObserveCharacteristicValuesUseCase(repository)),
    )

    @Test
    fun `touch notifications are streamed with their duration and intensity`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        ConnectToDeviceUseCase(repository)(DEVICE_ID)

        observeHardwareEvents().test {
            client.currentTimeMillis = 10_000
            client.emitNotification(POC_EVENTS_ID, "TOUCH_ON".encodeToByteArray())
            client.currentTimeMillis = 10_100
            client.emitNotification(POC_EVENTS_ID, "TOUCH_INTENSITY:80\u0000".encodeToByteArray())
            client.currentTimeMillis = 11_800
            client.emitNotification(POC_EVENTS_ID, "TOUCH_OFF\u0000".encodeToByteArray())

            assertThat(awaitItem()).isEqualTo(HardwareEvent.TouchPressed(10_000))
            assertThat(awaitItem()).isEqualTo(HardwareEvent.TouchIntensity(10_100, 80))
            assertThat(awaitItem()).isEqualTo(
                HardwareEvent.TouchReleased(11_800, 1_800, TouchIntensityStats(80, 80, 1)),
            )
        }
    }

    @Test
    fun `a press before a link loss is not paired with a release after reconnecting`() = runTest {
        client.servicesOnConnect = listOf(pocGattService())
        val connect = ConnectToDeviceUseCase(repository)
        connect(DEVICE_ID)

        observeHardwareEvents().test {
            client.emitNotification(POC_EVENTS_ID, "TOUCH_ON".encodeToByteArray())
            client.emitNotification(POC_EVENTS_ID, "TOUCH_INTENSITY:90".encodeToByteArray())
            assertThat(awaitItem()).isInstanceOf(HardwareEvent.TouchPressed::class.java)
            assertThat(awaitItem()).isInstanceOf(HardwareEvent.TouchIntensity::class.java)

            client.simulateUnexpectedDisconnect()
            connect(DEVICE_ID)
            client.currentTimeMillis = 3_000
            client.emitNotification(POC_EVENTS_ID, "TOUCH_OFF".encodeToByteArray())

            assertThat(awaitItem()).isEqualTo(HardwareEvent.TouchReleased(3_000, null))
        }
    }
}
