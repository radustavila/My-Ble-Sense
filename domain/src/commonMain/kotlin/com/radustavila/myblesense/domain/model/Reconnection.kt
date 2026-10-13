package com.radustavila.myblesense.domain.model

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * How the app tries to get a lost link back.
 *
 * Deliberately bounded: a peripheral that went out of range or was switched off may never come
 * back, and an endless retry loop would keep the radio busy and the battery draining for nothing.
 * After [maxAttempts] failed attempts the app gives up and leaves reconnecting to the user.
 */
data class ReconnectPolicy(
    val maxAttempts: Int = 3,
    val initialDelay: Duration = 1.seconds,
    val maxDelay: Duration = 8.seconds,
) {
    init {
        require(maxAttempts > 0) { "At least one reconnection attempt is required" }
        require(initialDelay > Duration.ZERO) { "The delay before an attempt must be positive" }
        require(maxDelay >= initialDelay) { "The maximum delay must not be below the initial one" }
    }

    /** Exponential back-off: 1 s, 2 s, 4 s, ... capped at [maxDelay]. [attempt] starts at 1. */
    fun delayBefore(attempt: Int): Duration {
        require(attempt >= 1) { "Attempts are numbered from 1" }
        var delay = initialDelay
        repeat(attempt - 1) { delay = (delay * 2).coerceAtMost(maxDelay) }
        return delay
    }

    companion object {
        val DEFAULT = ReconnectPolicy()
    }
}

/**
 * Whether losing the link with this error is worth an automatic reconnection.
 *
 * Only radio-level losses qualify (out of range, peripheral reset, link timeout). Bluetooth being
 * off or a revoked permission cannot be fixed by retrying — every attempt would fail the same way
 * — so those are left to the user.
 */
fun BleError.isRecoverableLinkLoss(): Boolean = when (this) {
    BleError.Disconnected,
    BleError.ConnectionTimeout,
    BleError.ServiceDiscoveryFailed,
    is BleError.ConnectionFailed,
    is BleError.UnknownGattError,
    -> true

    else -> false
}

/** Progress of an automatic reconnection, as streamed by the reconnect use case. */
sealed interface ReconnectionStatus {
    val attempt: Int
    val maxAttempts: Int

    /** Backing off before [attempt]. */
    data class Waiting(
        override val attempt: Int,
        override val maxAttempts: Int,
        val delay: Duration,
    ) : ReconnectionStatus

    /** [attempt] is connecting (and discovering services) right now. */
    data class Connecting(
        override val attempt: Int,
        override val maxAttempts: Int,
    ) : ReconnectionStatus

    /** The link is back and the GATT database has been discovered again. Terminal. */
    data class Reconnected(
        override val attempt: Int,
        override val maxAttempts: Int,
    ) : ReconnectionStatus

    /** Every attempt failed, or the last failure cannot be fixed by retrying. Terminal. */
    data class GaveUp(
        override val attempt: Int,
        override val maxAttempts: Int,
        val lastError: BleError,
    ) : ReconnectionStatus
}
