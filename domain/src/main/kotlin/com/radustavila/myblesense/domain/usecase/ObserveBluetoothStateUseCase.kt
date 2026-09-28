package com.radustavila.myblesense.domain.usecase

import com.radustavila.myblesense.domain.model.BleCapability
import com.radustavila.myblesense.domain.model.BluetoothState
import com.radustavila.myblesense.domain.repository.BleScanRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Streams adapter state changes (user turning Bluetooth on/off, airplane mode, ...). */
class ObserveBluetoothStateUseCase @Inject constructor(
    private val repository: BleScanRepository,
) {
    operator fun invoke(): Flow<BluetoothState> = repository.bluetoothState()
}

/** One-shot hardware capability check (does this device have BLE at all?). */
class GetBleCapabilityUseCase @Inject constructor(
    private val repository: BleScanRepository,
) {
    operator fun invoke(): BleCapability = repository.capability()
}
