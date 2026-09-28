package com.radustavila.myblesense.domain.usecase

import com.radustavila.myblesense.domain.model.BleDevice
import com.radustavila.myblesense.domain.session.ScanSessionStore
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Observes a single device from the current scan session, so the details screen keeps showing
 * live RSSI / advertising updates while a scan is running.
 */
class ObserveScannedDeviceUseCase @Inject constructor(
    private val sessionStore: ScanSessionStore,
) {
    operator fun invoke(deviceId: String): Flow<BleDevice?> = sessionStore.observeDevice(deviceId)
}

/** Clears the devices of the previous session, used when a new scan starts. */
class ClearScanSessionUseCase @Inject constructor(
    private val sessionStore: ScanSessionStore,
) {
    operator fun invoke() = sessionStore.clear()
}
