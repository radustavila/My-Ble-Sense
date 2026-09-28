package com.radustavila.myblesense.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for a scan session.
 *
 * A scan always has a bounded [duration]: BLE scanning is expensive and must never run
 * indefinitely.
 */
data class BleScanSettings(
    val duration: Duration = DEFAULT_DURATION,
    val mode: BleScanMode = BleScanMode.BALANCED,
    val serviceUuidFilters: List<String> = emptyList(),
) {
    init {
        require(duration > Duration.ZERO) { "Scan duration must be positive" }
    }

    companion object {
        val DEFAULT_DURATION: Duration = 15.seconds
    }
}

enum class BleScanMode {
    LOW_POWER,
    BALANCED,
    LOW_LATENCY,
}

/** Ordering applied to the discovered device list. */
enum class DeviceSortOrder {
    SIGNAL_STRENGTH,
    NAME,
    FIRST_SEEN,
}
