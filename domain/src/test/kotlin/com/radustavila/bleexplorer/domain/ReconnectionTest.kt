package com.radustavila.bleexplorer.domain

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.radustavila.bleexplorer.domain.model.BleError
import com.radustavila.bleexplorer.domain.model.ReconnectPolicy
import com.radustavila.bleexplorer.domain.model.ReconnectionStatus
import com.radustavila.bleexplorer.domain.model.isRecoverableLinkLoss
import com.radustavila.bleexplorer.domain.testing.FakeBleGattRepository
import com.radustavila.bleexplorer.domain.usecase.ReconnectToDeviceUseCase
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

private const val DEVICE_ID = "AA:BB:CC:DD:EE:FF"

class ReconnectionTest {

    private val repository = FakeBleGattRepository()
    private val client = repository.client
    private val reconnect = ReconnectToDeviceUseCase(repository)

    // --- Policy ---------------------------------------------------------------------------

    @Test
    fun `the delay doubles between attempts and is capped`() {
        val policy = ReconnectPolicy(maxAttempts = 5, initialDelay = 1.seconds, maxDelay = 4.seconds)

        assertThat((1..5).map(policy::delayBefore))
            .containsExactly(1.seconds, 2.seconds, 4.seconds, 4.seconds, 4.seconds).inOrder()
    }

    @Test(expected = IllegalArgumentException::class)
    fun `a policy without attempts is rejected`() {
        ReconnectPolicy(maxAttempts = 0)
    }

    @Test
    fun `only radio-level losses are worth reconnecting`() {
        assertThat(BleError.Disconnected.isRecoverableLinkLoss()).isTrue()
        assertThat(BleError.ConnectionTimeout.isRecoverableLinkLoss()).isTrue()
        assertThat(BleError.ConnectionFailed(133).isRecoverableLinkLoss()).isTrue()
        assertThat(BleError.ServiceDiscoveryFailed.isRecoverableLinkLoss()).isTrue()

        assertThat(BleError.BluetoothDisabled.isRecoverableLinkLoss()).isFalse()
        assertThat(BleError.PermissionDenied.isRecoverableLinkLoss()).isFalse()
        assertThat(BleError.DeviceNotFound.isRecoverableLinkLoss()).isFalse()
    }

    // --- Use case -------------------------------------------------------------------------

    @Test
    fun `reconnects after backing off and rediscovers services`() = runTest {
        reconnect(DEVICE_ID).test {
            assertThat(awaitItem()).isEqualTo(ReconnectionStatus.Waiting(1, 3, 1.seconds))
            assertThat(awaitItem()).isEqualTo(ReconnectionStatus.Connecting(1, 3))
            assertThat(awaitItem()).isEqualTo(ReconnectionStatus.Reconnected(1, 3))
            awaitComplete()
        }
        assertThat(client.connection.value.isConnectedTo(DEVICE_ID)).isTrue()
        assertThat(client.connection.value.servicesDiscovered).isTrue()
    }

    @Test
    fun `a failed attempt is retried with a longer delay`() = runTest {
        client.queuedConnectFailures += BleError.ConnectionTimeout

        reconnect(DEVICE_ID).test {
            skipItems(2) // waiting + connecting, attempt 1
            assertThat(awaitItem()).isEqualTo(ReconnectionStatus.Waiting(2, 3, 2.seconds))
            assertThat(awaitItem()).isEqualTo(ReconnectionStatus.Connecting(2, 3))
            assertThat(awaitItem()).isEqualTo(ReconnectionStatus.Reconnected(2, 3))
            awaitComplete()
        }
        assertThat(client.connectCount).isEqualTo(2)
    }

    @Test
    fun `gives up after the last attempt instead of retrying forever`() = runTest {
        client.connectFailure = BleError.ConnectionTimeout

        reconnect(DEVICE_ID).test {
            skipItems(6)
            assertThat(awaitItem())
                .isEqualTo(ReconnectionStatus.GaveUp(3, 3, BleError.ConnectionTimeout))
            awaitComplete()
        }
        assertThat(client.connectCount).isEqualTo(3)
    }

    @Test
    fun `gives up at once on a failure retrying cannot fix`() = runTest {
        client.connectFailure = BleError.BluetoothDisabled

        reconnect(DEVICE_ID).test {
            skipItems(2)
            assertThat(awaitItem())
                .isEqualTo(ReconnectionStatus.GaveUp(1, 3, BleError.BluetoothDisabled))
            awaitComplete()
        }
        assertThat(client.connectCount).isEqualTo(1)
    }

    @Test
    fun `cancelling the collection stops any further attempt`() = runTest {
        client.connectFailure = BleError.ConnectionTimeout
        val job = reconnect(DEVICE_ID).launchIn(backgroundScope)

        advanceTimeBy(1_500) // first attempt done, waiting 2 s before the second
        job.cancel()
        advanceTimeBy(60_000)

        assertThat(client.connectCount).isEqualTo(1)
    }
}
