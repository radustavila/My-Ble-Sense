package com.radustavila.bleexplorer.domain.repository

import com.radustavila.bleexplorer.domain.model.KnownDevice
import kotlinx.coroutines.flow.Flow

/**
 * Persistent storage of the [KnownDevice], implemented outside the domain layer.
 *
 * There is at most one: remembering a device replaces the previous one.
 */
interface KnownDeviceRepository {
    /** The remembered device, `null` when there is none; emits again whenever it changes. */
    val knownDevice: Flow<KnownDevice?>

    /** Replaces the remembered device. Fails, as a value, when the storage cannot be written. */
    suspend fun save(device: KnownDevice): Result<Unit>
}
