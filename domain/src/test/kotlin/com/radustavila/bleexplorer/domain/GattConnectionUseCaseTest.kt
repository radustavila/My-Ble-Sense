package com.radustavila.bleexplorer.domain

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.radustavila.bleexplorer.domain.model.BleError
import com.radustavila.bleexplorer.domain.model.BleException
import com.radustavila.bleexplorer.domain.model.GattConnectionState
import com.radustavila.bleexplorer.domain.testing.BATTERY_SERVICE_UUID
import com.radustavila.bleexplorer.domain.testing.CUSTOM_SERVICE_UUID
import com.radustavila.bleexplorer.domain.testing.FakeBleGattRepository
import com.radustavila.bleexplorer.domain.testing.gattService
import com.radustavila.bleexplorer.domain.usecase.ConnectToDeviceUseCase
import com.radustavila.bleexplorer.domain.usecase.DisconnectFromDeviceUseCase
import com.radustavila.bleexplorer.domain.usecase.ObserveGattConnectionUseCase
import com.radustavila.bleexplorer.domain.usecase.ObserveGattErrorsUseCase
import com.radustavila.bleexplorer.domain.usecase.ObserveGattServicesUseCase
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import org.junit.Test

private const val DEVICE_ID = "AA:BB:CC:DD:EE:FF"

class GattConnectionUseCaseTest {

    private val repository = FakeBleGattRepository()
    private val client = repository.client

    private val connect = ConnectToDeviceUseCase(repository)
    private val disconnect = DisconnectFromDeviceUseCase(repository)
    private val observeConnection = ObserveGattConnectionUseCase(repository)
    private val observeServices = ObserveGattServicesUseCase(repository)
    private val observeErrors = ObserveGattErrorsUseCase(repository)

    @Test
    fun `starts disconnected`() {
        val connection = observeConnection().value

        assertThat(connection.state).isEqualTo(GattConnectionState.DISCONNECTED)
        assertThat(connection.deviceId).isNull()
        assertThat(observeServices().value).isEmpty()
    }

    @Test
    fun `connect reports connecting before connected`() = runTest {
        client.connectDelayMillis = 500
        client.servicesOnConnect = listOf(gattService())

        val job = launch { connect(DEVICE_ID) }

        advanceTimeBy(100)
        assertThat(observeConnection().value.state).isEqualTo(GattConnectionState.CONNECTING)

        advanceTimeBy(500)
        job.join()
        val connection = observeConnection().value
        assertThat(connection.state).isEqualTo(GattConnectionState.CONNECTED)
        assertThat(connection.deviceId).isEqualTo(DEVICE_ID)
        assertThat(connection.servicesDiscovered).isTrue()
    }

    @Test
    fun `connect publishes the discovered services`() = runTest {
        client.servicesOnConnect = listOf(gattService(), gattService(uuid = CUSTOM_SERVICE_UUID))

        connect(DEVICE_ID)

        assertThat(observeServices().value.map { it.uuid })
            .containsExactly(BATTERY_SERVICE_UUID, CUSTOM_SERVICE_UUID)
            .inOrder()
    }

    @Test
    fun `a device without services is not an error`() = runTest {
        client.servicesOnConnect = emptyList()

        val result = connect(DEVICE_ID)

        assertThat(result.isSuccess).isTrue()
        assertThat(observeServices().value).isEmpty()
    }

    @Test
    fun `connection failure is reported and leaves the state disconnected`() = runTest {
        client.connectFailure = BleError.ConnectionFailed(status = 133)

        val result = connect(DEVICE_ID)

        assertThat(result.bleError()).isEqualTo(BleError.ConnectionFailed(status = 133))
        assertThat(observeConnection().value.state).isEqualTo(GattConnectionState.DISCONNECTED)
    }

    @Test
    fun `connection timeout is reported as its own error`() = runTest {
        client.connectFailure = BleError.ConnectionTimeout

        val result = connect(DEVICE_ID)

        assertThat(result.bleError()).isEqualTo(BleError.ConnectionTimeout)
    }

    @Test
    fun `disconnect clears the connection and the services`() = runTest {
        client.servicesOnConnect = listOf(gattService())
        connect(DEVICE_ID)

        val result = disconnect()

        assertThat(result.isSuccess).isTrue()
        assertThat(observeConnection().value.state).isEqualTo(GattConnectionState.DISCONNECTED)
        assertThat(observeServices().value).isEmpty()
        assertThat(client.disconnectCount).isEqualTo(1)
    }

    @Test
    fun `an unexpected disconnect is reported on the error stream`() = runTest {
        connect(DEVICE_ID)

        observeErrors().test {
            client.simulateUnexpectedDisconnect(BleError.Disconnected)

            assertThat(awaitItem()).isEqualTo(BleError.Disconnected)
            assertThat(observeConnection().value.state).isEqualTo(GattConnectionState.DISCONNECTED)
        }
    }
}

/** The error carried by a failed GATT `Result`, which is always a [BleException]. */
internal fun Result<*>.bleError(): BleError? =
    (exceptionOrNull() as? BleException)?.error
