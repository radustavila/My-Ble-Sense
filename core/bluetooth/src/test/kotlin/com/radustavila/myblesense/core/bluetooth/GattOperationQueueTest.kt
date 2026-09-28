package com.radustavila.myblesense.core.bluetooth

import com.google.common.truth.Truth.assertThat
import com.radustavila.myblesense.core.bluetooth.gatt.GattOperationQueue
import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.BleException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * The queue is the piece of Phase 2 that is easiest to get subtly wrong, so it is tested on its
 * own: no Android class is involved, only the ordering and the failure handling.
 */
class GattOperationQueueTest {

    private val queue = GattOperationQueue(timeout = 10.seconds)

    @Test
    fun `returns the payload reported by the callback`() = runTest {
        val operation = async { queue.execute("read") { true } }
        runCurrent()

        queue.complete(byteArrayOf(0x4C))

        assertThat(operation.await()).isEqualTo(byteArrayOf(0x4C))
    }

    @Test
    fun `the second operation only starts once the first one completed`() = runTest {
        val started = mutableListOf<String>()

        val first = async { queue.execute("first") { started += "first"; true } }
        runCurrent()
        val second = async { queue.execute("second") { started += "second"; true } }
        runCurrent()

        // The first operation is still waiting for its callback, so the second has not been sent.
        assertThat(started).containsExactly("first")

        queue.complete()
        runCurrent()
        assertThat(started).containsExactly("first", "second").inOrder()

        queue.complete()
        first.await()
        second.await()
    }

    @Test
    fun `a failed operation does not block the ones behind it`() = runTest {
        val first = async { runCatching { queue.execute("first") { true } } }
        runCurrent()
        val second = async { queue.execute("second") { true } }
        runCurrent()

        queue.fail(BleError.CharacteristicReadFailed(status = 2))
        runCurrent()
        queue.complete(byteArrayOf(0x01))

        assertThat(first.await().exceptionOrNull()).isInstanceOf(BleException::class.java)
        assertThat(second.await()).isEqualTo(byteArrayOf(0x01))
    }

    @Test
    fun `an operation the stack rejects fails immediately and releases the queue`() = runTest {
        val rejected = async { runCatching { queue.execute("rejected") { false } } }
        runCurrent()

        val error = (rejected.await().exceptionOrNull() as BleException).error
        assertThat(error).isEqualTo(BleError.GattOperationRejected)

        val next = async { queue.execute("next") { true } }
        runCurrent()
        queue.complete()
        assertThat(next.await()).isNull()
    }

    @Test
    fun `a timed out operation does not deadlock the queue`() = runTest {
        val timedOut = async { runCatching { queue.execute("never answered") { true } } }

        // No callback ever arrives for the first operation.
        advanceTimeBy(11.seconds)

        val error = (timedOut.await().exceptionOrNull() as BleException).error
        assertThat(error).isEqualTo(BleError.GattOperationTimeout)

        val next = async { queue.execute("next") { true } }
        runCurrent()
        queue.complete(byteArrayOf(0x02))
        assertThat(next.await()).isEqualTo(byteArrayOf(0x02))
    }

    @Test
    fun `closing the queue fails the pending operation and every queued one`() = runTest {
        val pending = async { runCatching { queue.execute("pending") { true } } }
        runCurrent()
        var secondStarted = false
        val queued = async { runCatching { queue.execute("queued") { secondStarted = true; true } } }
        runCurrent()

        queue.close(BleError.Disconnected)
        runCurrent()

        assertThat((pending.await().exceptionOrNull() as BleException).error)
            .isEqualTo(BleError.Disconnected)
        assertThat((queued.await().exceptionOrNull() as BleException).error)
            .isEqualTo(BleError.Disconnected)
        // Nothing is sent to a GATT object that is being closed.
        assertThat(secondStarted).isFalse()
    }

    @Test
    fun `operations started after a close fail without touching the stack`() = runTest {
        queue.close(BleError.Disconnected)

        var started = false
        val job = launch {
            runCatching { queue.execute("late") { started = true; true } }
        }
        job.join()

        assertThat(started).isFalse()
    }
}
