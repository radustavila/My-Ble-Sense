package com.radustavila.bleexplorer.domain.repository

import com.radustavila.bleexplorer.domain.model.BleCapability
import com.radustavila.bleexplorer.domain.model.BleDevice
import com.radustavila.bleexplorer.domain.model.BleScanSettings
import com.radustavila.bleexplorer.domain.model.BluetoothState
import kotlinx.coroutines.flow.Flow

/**
 * Low level scanning abstraction implemented by the Android infrastructure layer.
 *
 * [scan] returns a *cold* flow: collecting it starts a platform scan, cancelling the collection
 * stops it and releases the scan callback. There is no `stopScan()` because structured
 * concurrency already expresses the lifetime of the scan, which makes callback leaks impossible.
 */
interface BleScanner {
    fun scan(settings: BleScanSettings): Flow<BleDevice>
}

/** Access to adapter capabilities and state, without exposing any Android Bluetooth type. */
interface BluetoothStateMonitor {
    fun capability(): BleCapability

    fun currentState(): BluetoothState

    fun observeState(): Flow<BluetoothState>
}
