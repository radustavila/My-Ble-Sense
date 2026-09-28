package com.radustavila.myblesense.domain.usecase

import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.GattConnection
import com.radustavila.myblesense.domain.model.GattService
import com.radustavila.myblesense.domain.model.ReconnectPolicy
import com.radustavila.myblesense.domain.model.ReconnectionStatus
import com.radustavila.myblesense.domain.model.isRecoverableLinkLoss
import com.radustavila.myblesense.domain.model.toBleError
import com.radustavila.myblesense.domain.repository.BleGattRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * Connects to a peripheral and waits until its GATT database has been discovered.
 *
 * A first connection is an explicit user action, with one exception: a single attempt to the
 * remembered [com.radustavila.myblesense.domain.model.KnownDevice] as the device list appears
 * (see [com.radustavila.myblesense.domain.model.shouldAutoConnect]). The only other automatic
 * attempts are the bounded ones of [ReconnectToDeviceUseCase], after a link the user opened was
 * lost.
 */
class ConnectToDeviceUseCase @Inject constructor(
    private val repository: BleGattRepository,
) {
    suspend operator fun invoke(deviceId: String): Result<Unit> = repository.connect(deviceId)
}

/**
 * Tries to get a lost link back, following a bounded [ReconnectPolicy].
 *
 * The returned flow is cold and drives the whole attempt: it backs off, connects (which also
 * rediscovers the GATT database, see [ConnectToDeviceUseCase]) and completes after a terminal
 * [ReconnectionStatus.Reconnected] or [ReconnectionStatus.GaveUp]. Cancelling the collection —
 * the user disconnecting or leaving the screen — cancels the pending delay or the in-flight
 * connection attempt, and the GATT client releases whatever that attempt had opened.
 *
 * It gives up early on a failure retrying cannot fix (Bluetooth off, permission revoked), so the
 * user is told right away instead of after every attempt has failed the same way.
 */
class ReconnectToDeviceUseCase @Inject constructor(
    private val repository: BleGattRepository,
) {
    operator fun invoke(
        deviceId: String,
        policy: ReconnectPolicy = ReconnectPolicy.DEFAULT,
    ): Flow<ReconnectionStatus> = flow {
        val max = policy.maxAttempts
        var lastError: BleError = BleError.Disconnected
        for (attempt in 1..max) {
            val wait = policy.delayBefore(attempt)
            emit(ReconnectionStatus.Waiting(attempt, max, wait))
            delay(wait)

            emit(ReconnectionStatus.Connecting(attempt, max))
            val failure = repository.connect(deviceId).exceptionOrNull()
            if (failure == null) {
                emit(ReconnectionStatus.Reconnected(attempt, max))
                return@flow
            }
            lastError = failure.toBleError()
            if (!lastError.isRecoverableLinkLoss()) {
                emit(ReconnectionStatus.GaveUp(attempt, max, lastError))
                return@flow
            }
        }
        emit(ReconnectionStatus.GaveUp(max, max, lastError))
    }
}

/** Closes the connection and releases the platform GATT object. */
class DisconnectFromDeviceUseCase @Inject constructor(
    private val repository: BleGattRepository,
) {
    suspend operator fun invoke(): Result<Unit> = repository.disconnect()
}

/** Streams the connection lifecycle: disconnected → connecting → connected → disconnecting. */
class ObserveGattConnectionUseCase @Inject constructor(
    private val repository: BleGattRepository,
) {
    operator fun invoke(): StateFlow<GattConnection> = repository.connection()
}

/** Streams the discovered services; empty while disconnected or before discovery completes. */
class ObserveGattServicesUseCase @Inject constructor(
    private val repository: BleGattRepository,
) {
    operator fun invoke(): StateFlow<List<GattService>> = repository.services()
}

/**
 * Streams failures that were not caused by a user action — link lost, Bluetooth switched off,
 * permission revoked — so the UI can report them without inventing a connection state for them.
 */
class ObserveGattErrorsUseCase @Inject constructor(
    private val repository: BleGattRepository,
) {
    operator fun invoke(): Flow<BleError> = repository.errors()
}
