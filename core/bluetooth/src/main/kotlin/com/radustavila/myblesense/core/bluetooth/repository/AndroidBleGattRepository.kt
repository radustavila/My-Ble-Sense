package com.radustavila.myblesense.core.bluetooth.repository

import com.radustavila.myblesense.core.common.log.BleLog
import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.BleException
import com.radustavila.myblesense.domain.model.GattCharacteristicId
import com.radustavila.myblesense.domain.model.GattCharacteristicValue
import com.radustavila.myblesense.domain.model.GattConnection
import com.radustavila.myblesense.domain.model.GattService
import com.radustavila.myblesense.domain.model.GattWriteType
import com.radustavila.myblesense.domain.model.toBleError
import com.radustavila.myblesense.domain.repository.BleGattClient
import com.radustavila.myblesense.domain.repository.BleGattRepository
import com.radustavila.myblesense.domain.repository.BluetoothStateMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connection-time counterpart of
 * [com.radustavila.myblesense.core.bluetooth.repository.AndroidBleScanRepository]: it validates
 * the preconditions of every operation, delegates to [BleGattClient] and converts thrown failures
 * into `Result` values carrying a [BleException].
 *
 * `CancellationException` is deliberately re-thrown: swallowing it into a `Result` would break
 * structured concurrency, which is how a ViewModel leaving the screen cancels an in-flight read.
 */
@Singleton
class AndroidBleGattRepository @Inject constructor(
    private val client: BleGattClient,
    private val stateMonitor: BluetoothStateMonitor,
) : BleGattRepository {

    override fun connection(): StateFlow<GattConnection> = client.connection

    override fun services(): StateFlow<List<GattService>> = client.services

    override fun notifyingCharacteristics(): StateFlow<Set<GattCharacteristicId>> =
        client.notifyingCharacteristics

    override fun errors(): Flow<BleError> = client.errors()

    override fun characteristicValues(): Flow<GattCharacteristicValue> = client.characteristicValues()

    override suspend fun connect(deviceId: String): Result<Unit> = gattCall("connect") {
        if (!stateMonitor.capability().isSupported) throw BleException(BleError.BleUnsupported)
        if (!stateMonitor.currentState().isEnabled) throw BleException(BleError.BluetoothDisabled)
        client.connect(deviceId)
    }

    override suspend fun disconnect(): Result<Unit> = gattCall("disconnect") {
        client.disconnect()
    }

    override suspend fun readCharacteristic(
        id: GattCharacteristicId,
    ): Result<GattCharacteristicValue> = gattCall("read") {
        client.readCharacteristic(id)
    }

    override suspend fun writeCharacteristic(
        id: GattCharacteristicId,
        value: ByteArray,
        writeType: GattWriteType,
    ): Result<Unit> = gattCall("write") {
        client.writeCharacteristic(id, value, writeType)
    }

    override suspend fun setNotificationsEnabled(
        id: GattCharacteristicId,
        enabled: Boolean,
    ): Result<Unit> = gattCall("setNotifications") {
        client.setNotificationsEnabled(id, enabled)
    }

    private suspend inline fun <T> gattCall(label: String, block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        val error = e.toBleError()
        BleLog.w("GATT $label failed: $error", e)
        Result.failure(if (e is BleException) e else BleException(error, e))
    }
}
