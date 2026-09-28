package com.radustavila.myblesense.domain.usecase

import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.BleException
import com.radustavila.myblesense.domain.model.GattConnectionState
import com.radustavila.myblesense.domain.model.KnownDevice
import com.radustavila.myblesense.domain.repository.BleGattRepository
import com.radustavila.myblesense.domain.repository.KnownDeviceRepository
import com.radustavila.myblesense.domain.session.ScanSessionStore
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Streams the remembered device; `null` until the app has connected to one. */
class ObserveKnownDeviceUseCase @Inject constructor(
    private val repository: KnownDeviceRepository,
) {
    operator fun invoke(): Flow<KnownDevice?> = repository.knownDevice
}

/**
 * Keeps the [KnownDevice] up to date for as long as it is collected (the app's lifetime):
 *
 *  - every successful connection — GATT database discovered, whichever screen opened it and
 *    including an automatic reconnection — remembers that device, replacing the previous one;
 *  - a scan that sees the remembered device updates its entry (a newly learned name) instead of
 *    remembering it a second time.
 *
 * The name comes from the scan session, or from the entry already remembered for the same
 * device when the connection was opened without a scan. Remembering is best effort: a storage
 * failure is reported by the repository and never affects the connection.
 */
class TrackKnownDeviceUseCase @Inject constructor(
    private val gattRepository: BleGattRepository,
    private val sessionStore: ScanSessionStore,
    private val repository: KnownDeviceRepository,
) {
    suspend operator fun invoke() = coroutineScope {
        launch { rememberConnectedDevices() }
        launch { updateFromScans() }
    }

    private suspend fun rememberConnectedDevices() {
        gattRepository.connection()
            .filter { it.isConnected && it.servicesDiscovered }
            .mapNotNull { it.deviceId }
            .collect { deviceId ->
                val current = repository.knownDevice.first()
                val scannedName = sessionStore.devices.value
                    .firstOrNull { it.id == deviceId }
                    ?.displayName
                val connected = KnownDevice(
                    id = deviceId,
                    name = scannedName ?: current?.takeIf { it.id == deviceId }?.name,
                )
                if (connected != current) repository.save(connected)
            }
    }

    private suspend fun updateFromScans() {
        repository.knownDevice
            // Not filtered before combining: a forgotten device must stop matching scan results
            // instead of lingering as the latest value and being remembered again.
            .combine(sessionStore.devices) { known, devices ->
                if (known == null) return@combine null
                devices.firstOrNull { it.id == known.id }
                    ?.let(known::updatedFrom)
                    ?.takeIf { it != known }
            }
            .filterNotNull()
            .collect { repository.save(it) }
    }
}

/**
 * Makes the app forget the [KnownDevice]: the device list no longer shows it, never connects to
 * it on its own and lists it like any other device the next time a scan finds it.
 *
 * A link to it that is open or opening is closed first, so the forgotten device is not left
 * connected — and not remembered again by [TrackKnownDeviceUseCase] once its services are
 * discovered. If that disconnect fails the device stays known, since the link state is then
 * uncertain. Only the app's own record is removed: the system's Bluetooth pairing (bond) is left
 * as it is.
 */
class ForgetKnownDeviceUseCase @Inject constructor(
    private val gattRepository: BleGattRepository,
    private val repository: KnownDeviceRepository,
) {
    suspend operator fun invoke(): Result<Unit> {
        val known = repository.knownDevice.first() ?: return Result.success(Unit)

        val connection = gattRepository.connection().value
        if (connection.deviceId == known.id && connection.state != GattConnectionState.DISCONNECTED) {
            gattRepository.disconnect().onFailure { return Result.failure(it) }
        }

        return repository.clear().fold(
            onSuccess = { Result.success(Unit) },
            onFailure = { Result.failure(BleException(BleError.KnownDeviceNotForgotten, it)) },
        )
    }
}
