package com.radustavila.bleexplorer.domain.model

/**
 * A [PocEvent] interpreted as something that happened on the ESP32's hardware.
 *
 * The firmware reports the *edges* of the capacitive touch sensor (`TOUCH_ON` when it is pressed,
 * `TOUCH_OFF` when it is released) and, in between, a `TOUCH_INTENSITY:<0..100>` sample roughly
 * every 100 ms. How long the sensor was held and how hard it was pressed are derived on the phone,
 * see [TouchTracker]. Anything else the firmware pushes (including a malformed intensity) is kept
 * as [Other] so no event is silently dropped.
 */
sealed interface HardwareEvent {
    /** When the notification reached the app, as stamped by the GATT client. */
    val timestampMillis: Long

    /** The touch sensor started being pressed. */
    data class TouchPressed(override val timestampMillis: Long) : HardwareEvent

    /** A live intensity sample of the touch in progress; [percent] is within `0..100`. */
    data class TouchIntensity(
        override val timestampMillis: Long,
        val percent: Int,
    ) : HardwareEvent

    /**
     * The touch sensor was released.
     *
     * [durationMillis] is how long it was held, or `null` when the matching press was never
     * observed (the app connected or subscribed while the sensor was already being held, or the
     * `TOUCH_ON` notification was lost): an unknown duration is reported as unknown, not guessed.
     *
     * [intensity] summarises the samples received between the press and the release, or is `null`
     * when there were none (or the press was never observed).
     */
    data class TouchReleased(
        override val timestampMillis: Long,
        val durationMillis: Long?,
        val intensity: TouchIntensityStats? = null,
    ) : HardwareEvent

    /** An event this app does not interpret; [text] is the raw payload. */
    data class Other(
        val text: String,
        override val timestampMillis: Long,
    ) : HardwareEvent
}

/** Peak and average intensity of one completed touch, both in percent (`0..100`). */
data class TouchIntensityStats(
    val peakPercent: Int,
    val averagePercent: Int,
    val sampleCount: Int,
)

/** Wire vocabulary of the touch sensor, as written by the firmware. */
object PocTouchEvents {
    const val TOUCH_ON = "TOUCH_ON"
    const val TOUCH_OFF = "TOUCH_OFF"

    /** Followed by an integer percentage, e.g. `TOUCH_INTENSITY:94`. */
    const val TOUCH_INTENSITY_PREFIX = "TOUCH_INTENSITY:"

    val INTENSITY_RANGE: IntRange = 0..100

    /**
     * The percentage carried by a `TOUCH_INTENSITY:<n>` event, or `null` when [text] is not one or
     * its value is not an integer within [INTENSITY_RANGE]: a value the firmware should never send
     * is shown raw rather than clamped into something plausible.
     */
    fun parseIntensity(text: String): Int? {
        if (!text.startsWith(TOUCH_INTENSITY_PREFIX, ignoreCase = true)) return null
        return text.substring(TOUCH_INTENSITY_PREFIX.length).trim().toIntOrNull()
            ?.takeIf { it in INTENSITY_RANGE }
    }
}

/**
 * Running peak / average of the intensity samples of one touch. Only the aggregates are kept, so
 * a touch held for minutes costs no more memory than a short one.
 */
class TouchIntensityAccumulator {

    private var count = 0
    private var sum = 0L
    private var peak = 0

    fun add(percent: Int) {
        count++
        sum += percent
        peak = maxOf(peak, percent)
    }

    /** `null` when no sample was added. The average is rounded half up to a whole percent. */
    fun stats(): TouchIntensityStats? =
        if (count == 0) {
            null
        } else {
            TouchIntensityStats(
                peakPercent = peak,
                averagePercent = ((sum * 2 + count) / (count * 2)).toInt(),
                sampleCount = count,
            )
        }
}

/**
 * Pairs `TOUCH_ON` / `TOUCH_OFF` edges into [HardwareEvent]s carrying the hold duration and the
 * intensity statistics of the samples received in between.
 *
 * Stateful by design — it remembers when the current press started and its samples so far — so
 * one instance belongs to one connection: a press seen before a link loss must never be paired
 * with a release seen after the reconnection. It is not thread safe; it is meant to be driven by a
 * single flow collector.
 */
class TouchTracker {

    private var pressedAtMillis: Long? = null

    /** Non-null exactly while a press has been observed and not released yet. */
    private var samples: TouchIntensityAccumulator? = null

    fun onEvent(event: PocEvent): HardwareEvent {
        val intensity = PocTouchEvents.parseIntensity(event.text)
        return when {
            event.text.equals(PocTouchEvents.TOUCH_ON, ignoreCase = true) -> {
                // A second press without a release in between means a TOUCH_OFF was lost; the most
                // recent press is the one the next release belongs to.
                pressedAtMillis = event.timestampMillis
                samples = TouchIntensityAccumulator()
                HardwareEvent.TouchPressed(event.timestampMillis)
            }

            intensity != null -> {
                // Samples of a touch whose press was not observed are still shown live, but not
                // summarised: that touch's statistics would describe only part of it.
                samples?.add(intensity)
                HardwareEvent.TouchIntensity(event.timestampMillis, intensity)
            }

            event.text.equals(PocTouchEvents.TOUCH_OFF, ignoreCase = true) -> {
                val duration = pressedAtMillis
                    ?.let { event.timestampMillis - it }
                    // Timestamps are wall-clock; a clock adjustment between the two edges must not
                    // produce a negative duration.
                    ?.takeIf { it >= 0 }
                val stats = samples?.stats()
                pressedAtMillis = null
                samples = null
                HardwareEvent.TouchReleased(event.timestampMillis, duration, stats)
            }

            else -> HardwareEvent.Other(event.text, event.timestampMillis)
        }
    }
}
