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
 * Low level GATT abstraction implemented by the Android infrastructure layer, the connection-time
 * counterpart of [BleScanner].
 *
 * The app maintains **at most one** connection at a time: BLE peripherals accept very few
 * concurrent links and a single connection keeps the operation queue, the CCCD state and the
 * teardown path unambiguous.
 *
 * Every suspending operation fails with
 * [com.radustavila.myblesense.domain.model.BleException]; they are serialized by the
 * implementation, so callers never have to coordinate them.
 */
interface BleGattClient {
    /** Current connection state. Never completes; starts at [GattConnection.Disconnected]. */
    val connection: StateFlow<GattConnection>

    /** GATT database of the connected device; empty until service discovery completes. */
    val services: StateFlow<List<GattService>>

    /** Characteristics whose CCCD is currently configured for notifications/indications. */
    val notifyingCharacteristics: StateFlow<Set<GattCharacteristicId>>

    /**
     * Failures that nobody asked for: link lost, adapter switched off, permission revoked.
     * Errors of an explicit operation are thrown by that operation instead.
     */
    fun errors(): Flow<BleError>

    /** Every value observed on any characteristic, whether read or pushed as a notification. */
    fun characteristicValues(): Flow<GattCharacteristicValue>

    /** Connects and discovers services; returns once the GATT database is available. */
    suspend fun connect(deviceId: String)

    /** Closes the connection and releases the platform GATT object. Safe to call when idle. */
    suspend fun disconnect()

    suspend fun readCharacteristic(id: GattCharacteristicId): GattCharacteristicValue

    suspend fun writeCharacteristic(
        id: GattCharacteristicId,
        value: ByteArray,
        writeType: GattWriteType,
    )

    /** Writes the CCCD of [id] so the peripheral starts or stops pushing values. */
    suspend fun setNotificationsEnabled(id: GattCharacteristicId, enabled: Boolean)
}
