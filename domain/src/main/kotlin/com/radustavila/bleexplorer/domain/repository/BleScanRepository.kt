package com.radustavila.bleexplorer.domain.repository

import com.radustavila.bleexplorer.domain.model.BleCapability
import com.radustavila.bleexplorer.domain.model.BleDevice
import com.radustavila.bleexplorer.domain.model.BleScanSettings
import com.radustavila.bleexplorer.domain.model.BluetoothState
import kotlinx.coroutines.flow.Flow

/**
 * Repository boundary used by the use cases. The implementation validates preconditions
 * (BLE support, adapter state, permissions), delegates to [BleScanner] and maps infrastructure
 * failures to [com.radustavila.bleexplorer.domain.model.BleError].
 */
interface BleScanRepository {
    /**
     * Emits every advertisement observed while collected. The same peripheral is reported many
     * times; de-duplication happens upstream of the UI, in the scan use case.
     *
     * The flow fails with [com.radustavila.bleexplorer.domain.model.BleException].
     */
    fun scan(settings: BleScanSettings): Flow<BleDevice>

    fun capability(): BleCapability

    fun bluetoothState(): Flow<BluetoothState>

    fun currentBluetoothState(): BluetoothState
}
