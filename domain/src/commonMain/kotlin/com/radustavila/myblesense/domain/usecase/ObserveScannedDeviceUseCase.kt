package com.radustavila.myblesense.domain.usecase

import com.radustavila.myblesense.domain.model.BleDevice
import com.radustavila.myblesense.domain.session.ScanSessionStore
import kotlinx.coroutines.flow.Flow

/**
 * Observes a single device from the current scan session, so the details screen keeps showing
 * live RSSI / advertising updates while a scan is running.
 */
class ObserveScannedDeviceUseCase(
    private val sessionStore: ScanSessionStore,
) {
    operator fun invoke(deviceId: String): Flow<BleDevice?> = sessionStore.observeDevice(deviceId)
}

/** Clears the devices of the previous session, used when a new scan starts. */
class ClearScanSessionUseCase(
    private val sessionStore: ScanSessionStore,
) {
    operator fun invoke() = sessionStore.clear()
}
