package com.radustavila.myblesense.domain.repository

import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.GattCharacteristicId
import com.radustavila.myblesense.domain.model.GattCharacteristicValue
import com.radustavila.myblesense.domain.model.GattConnection
import com.radustavila.myblesense.domain.model.GattService
import com.radustavila.myblesense.domain.model.GattWriteType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Repository boundary used by the GATT use cases.
 *
 * It mirrors [BleGattClient] but turns thrown failures into `Result`, so the presentation layer
 * handles errors as values and never has to catch an exception coming out of a use case.
 * Cancellation is *not* captured: a cancelled coroutine still cancels.
 */
interface BleGattRepository {
    fun connection(): StateFlow<GattConnection>

    fun services(): StateFlow<List<GattService>>

    fun notifyingCharacteristics(): StateFlow<Set<GattCharacteristicId>>

    fun errors(): Flow<BleError>

    fun characteristicValues(): Flow<GattCharacteristicValue>

    suspend fun connect(deviceId: String): Result<Unit>

    suspend fun disconnect(): Result<Unit>

    suspend fun readCharacteristic(id: GattCharacteristicId): Result<GattCharacteristicValue>

    suspend fun writeCharacteristic(
        id: GattCharacteristicId,
        value: ByteArray,
        writeType: GattWriteType,
    ): Result<Unit>

    suspend fun setNotificationsEnabled(
        id: GattCharacteristicId,
        enabled: Boolean,
    ): Result<Unit>
}
