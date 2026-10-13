package com.radustavila.myblesense.domain.testing

import com.radustavila.myblesense.domain.model.KnownDevice
import com.radustavila.myblesense.domain.repository.KnownDeviceRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** In-memory [KnownDeviceRepository]; what a previous launch remembered is [initial]. */
class FakeKnownDeviceRepository(initial: KnownDevice? = null) : KnownDeviceRepository {

    private val _knownDevice = MutableStateFlow(initial)
    override val knownDevice: Flow<KnownDevice?> = _knownDevice.asStateFlow()

    /** The device currently remembered. */
    val current: KnownDevice? get() = _knownDevice.value

    /** Makes [save] fail with this, as a storage that cannot be written would. */
    var saveFailure: Throwable? = null

    var saveCount: Int = 0
        private set

    override suspend fun save(device: KnownDevice): Result<Unit> {
        saveCount++
        saveFailure?.let { return Result.failure(it) }
        _knownDevice.value = device
        return Result.success(Unit)
    }

    /** Makes [clear] fail with this, as a storage that cannot be written would. */
    var clearFailure: Throwable? = null

    /** Makes [clear] take virtual time, so "forgetting" is observable in tests. */
    var clearDelayMillis: Long = 0

    var clearCount: Int = 0
        private set

    override suspend fun clear(): Result<Unit> {
        clearCount++
        if (clearDelayMillis > 0) delay(clearDelayMillis)
        clearFailure?.let { return Result.failure(it) }
        _knownDevice.value = null
        return Result.success(Unit)
    }
}
