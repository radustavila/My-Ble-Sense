package com.radustavila.bleexplorer.core.bluetooth.gatt

import com.radustavila.bleexplorer.core.common.log.BleLog
import com.radustavila.bleexplorer.domain.model.BleError
import com.radustavila.bleexplorer.domain.model.BleException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Serializes the GATT operations of one connection.
 *
 * Android's GATT stack accepts a single outstanding request per connection: issuing a read while
 * another read, write or descriptor write is still waiting for its callback makes the second call
 * return `false`, or — worse — silently drops it. The queue makes that impossible by holding a
 * fair [Mutex] for the whole "start the operation, wait for its callback" window.
 *
 * Why this cannot deadlock, which is the usual failure mode of hand-written GATT queues:
 *  - the callback is awaited under [withTimeout], so a peripheral that never answers releases the
 *    lock after [timeout] instead of blocking every later operation;
 *  - the pending continuation is cleared in a `finally`, so a failed, timed out or cancelled
 *    operation never leaves a stale deferred that a later callback could complete;
 *  - [close] terminates the queue on disconnect: the pending operation fails immediately and
 *    everything still queued fails fast instead of being started on a dead `BluetoothGatt`.
 *
 * One queue instance belongs to one connection; reconnecting creates a new one.
 */
internal class GattOperationQueue(
    private val timeout: Duration = DEFAULT_TIMEOUT,
) {
    private val mutex = Mutex()

    /** Written under [mutex], completed from the GATT callback thread, hence `@Volatile`. */
    @Volatile
    private var pending: CompletableDeferred<ByteArray?>? = null

    @Volatile
    private var closedWith: BleError? = null

    /**
     * Runs [start] — the actual platform call, which returns `false`/an error code when the stack
     * rejects it outright — and suspends until the matching callback arrives.
     *
     * @return the payload the callback reported, or `null` for operations that only acknowledge.
     */
    suspend fun execute(label: String, start: () -> Boolean): ByteArray? {
        closedWith?.let { throw BleException(it) }

        return mutex.withLock {
            // The connection may have gone away while this operation waited its turn.
            closedWith?.let { throw BleException(it) }

            val deferred = CompletableDeferred<ByteArray?>()
            pending = deferred
            try {
                BleLog.d("GATT op start: $label")
                if (!start()) {
                    BleLog.w("GATT op rejected by the stack: $label")
                    throw BleException(BleError.GattOperationRejected)
                }
                withTimeout(timeout) { deferred.await() }
            } catch (e: TimeoutCancellationException) {
                BleLog.w("GATT op timed out after $timeout: $label")
                throw BleException(BleError.GattOperationTimeout, e)
            } finally {
                pending = null
            }
        }
    }

    /** Completes the operation in flight. Called from the GATT callback thread. */
    fun complete(value: ByteArray? = null) {
        pending?.complete(value)
    }

    /** Fails the operation in flight without affecting the ones queued behind it. */
    fun fail(error: BleError) {
        pending?.completeExceptionally(BleException(error))
    }

    /**
     * Terminates the queue: the operation in flight fails and every later [execute] fails
     * immediately with [error]. Called when the connection is closed.
     */
    fun close(error: BleError) {
        closedWith = error
        pending?.completeExceptionally(BleException(error))
        pending = null
    }

    companion object {
        /**
         * Generous enough for a slow peripheral to answer, short enough that a device which went
         * out of range does not freeze the UI's action buttons.
         */
        val DEFAULT_TIMEOUT: Duration = 10.seconds
    }
}
