package com.radustavila.myblesense.core.common.time

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** Wall-clock time in epoch milliseconds, the timestamp unit used across the domain layer. */
fun currentTimeMillis(): Long = Clock.System.now().toEpochMilliseconds()

/**
 * Formats an epoch timestamp as a wall-clock time in [zone]: `HH:mm:ss`, or `HH:mm:ss.SSS` when
 * [withMillis] is set. Every field is zero padded, so the result has a fixed width.
 */
fun formatClockTime(
    epochMillis: Long,
    zone: TimeZone = TimeZone.currentSystemDefault(),
    withMillis: Boolean = false,
): String {
    val time = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(zone).time
    val seconds = "${time.hour.pad(2)}:${time.minute.pad(2)}:${time.second.pad(2)}"
    return if (withMillis) "$seconds.${(time.nanosecond / NANOS_PER_MILLI).pad(3)}" else seconds
}

private fun Int.pad(length: Int): String = toString().padStart(length, '0')

private const val NANOS_PER_MILLI = 1_000_000
