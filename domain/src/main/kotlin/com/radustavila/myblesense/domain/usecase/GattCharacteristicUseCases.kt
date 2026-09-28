package com.radustavila.myblesense.domain.usecase

import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.BleException
import com.radustavila.myblesense.domain.model.GattCharacteristic
import com.radustavila.myblesense.domain.model.GattCharacteristicId
import com.radustavila.myblesense.domain.model.GattCharacteristicValue
import com.radustavila.myblesense.domain.model.GattWriteType
import com.radustavila.myblesense.domain.repository.BleGattRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import javax.inject.Inject

/**
 * Reads a characteristic after checking that the connected device actually exposes it and
 * declares the `READ` property.
 *
 * Validating here rather than in the ViewModel keeps the rule in one place and makes it testable
 * without any Android or Compose dependency.
 */
class ReadCharacteristicUseCase @Inject constructor(
    private val repository: BleGattRepository,
) {
    suspend operator fun invoke(id: GattCharacteristicId): Result<GattCharacteristicValue> {
        val characteristic = repository.requireCharacteristic(id)
            .getOrElse { return Result.failure(it) }

        if (!characteristic.canRead) {
            return failure(BleError.CharacteristicOperationUnsupported)
        }
        return repository.readCharacteristic(id)
    }
}

/**
 * Writes raw bytes to a characteristic.
 *
 * The write type is derived from the declared properties — `WRITE` acknowledges, `WRITE_NO_RESPONSE`
 * does not — instead of being chosen by the caller, so the UI can never request a mode the
 * peripheral does not support.
 */
class WriteCharacteristicUseCase @Inject constructor(
    private val repository: BleGattRepository,
) {
    suspend operator fun invoke(
        id: GattCharacteristicId,
        value: ByteArray,
    ): Result<Unit> {
        val characteristic = repository.requireCharacteristic(id)
            .getOrElse { return Result.failure(it) }

        val writeType = when {
            characteristic.canWriteWithResponse -> GattWriteType.WITH_RESPONSE
            characteristic.canWriteWithoutResponse -> GattWriteType.WITHOUT_RESPONSE
            else -> return failure(BleError.CharacteristicOperationUnsupported)
        }
        return repository.writeCharacteristic(id, value, writeType)
    }
}

/**
 * Enables or disables notifications/indications on a characteristic by writing its CCCD.
 *
 * Subscriptions are never enabled implicitly: a peripheral pushing values the user did not ask
 * for wastes both devices' radio time.
 */
class SetCharacteristicNotificationsUseCase @Inject constructor(
    private val repository: BleGattRepository,
) {
    suspend operator fun invoke(id: GattCharacteristicId, enabled: Boolean): Result<Unit> {
        val characteristic = repository.requireCharacteristic(id)
            .getOrElse { return Result.failure(it) }

        if (!characteristic.supportsSubscription) {
            return failure(BleError.CharacteristicOperationUnsupported)
        }
        return repository.setNotificationsEnabled(id, enabled)
    }
}

/** Values pushed by the peripheral for one characteristic, in arrival order. */
class ObserveCharacteristicValuesUseCase @Inject constructor(
    private val repository: BleGattRepository,
) {
    operator fun invoke(id: GattCharacteristicId): Flow<GattCharacteristicValue> =
        repository.characteristicValues().filter { it.id == id }

    /** Un-filtered stream, used by the inspector to update every characteristic row at once. */
    operator fun invoke(): Flow<GattCharacteristicValue> = repository.characteristicValues()
}

/** Characteristics the app is currently subscribed to. */
class ObserveNotifyingCharacteristicsUseCase @Inject constructor(
    private val repository: BleGattRepository,
) {
    operator fun invoke(): StateFlow<Set<GattCharacteristicId>> =
        repository.notifyingCharacteristics()
}

/**
 * Looks a characteristic up in the discovered database.
 *
 * Failing here (rather than in the infrastructure layer) means an operation on a stale UI row —
 * one that was rendered before a disconnect — is rejected before it ever reaches the BLE stack.
 */
private fun BleGattRepository.requireCharacteristic(
    id: GattCharacteristicId,
): Result<GattCharacteristic> {
    if (!connection().value.isConnected) return failure(BleError.NotConnected)

    val characteristic = services().value
        .firstOrNull { it.uuid == id.serviceUuid }
        ?.characteristics
        ?.firstOrNull { it.uuid == id.characteristicUuid }
        ?: return failure(BleError.CharacteristicNotFound)

    return Result.success(characteristic)
}

private fun <T> failure(error: BleError): Result<T> = Result.failure(BleException(error))
