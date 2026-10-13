package com.radustavila.myblesense.core.bluetooth.state

import com.radustavila.myblesense.core.bluetooth.central.IosCentral
import com.radustavila.myblesense.domain.model.BleCapability
import com.radustavila.myblesense.domain.model.BluetoothState
import com.radustavila.myblesense.domain.repository.BluetoothStateMonitor
import kotlinx.coroutines.flow.Flow
import platform.CoreBluetooth.CBManagerStateUnsupported

/**
 * Adapter state from `centralManagerDidUpdateState`. Unlike Android the state is asynchronous:
 * it is [BluetoothState.UNKNOWN] until CoreBluetooth has reported once.
 */
internal class IosBluetoothStateMonitor(
    private val central: IosCentral,
) : BluetoothStateMonitor {

    override fun capability(): BleCapability {
        // Every device that runs iOS has Bluetooth LE; only the simulator reports "unsupported".
        val supported = central.managerState != CBManagerStateUnsupported
        return BleCapability(hasBluetooth = supported, hasBluetoothLowEnergy = supported)
    }

    override fun currentState(): BluetoothState = central.state.value

    override fun observeState(): Flow<BluetoothState> = central.state
}
