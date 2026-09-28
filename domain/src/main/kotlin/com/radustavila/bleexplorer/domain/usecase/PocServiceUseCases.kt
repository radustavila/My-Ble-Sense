package com.radustavila.bleexplorer.domain.usecase

import com.radustavila.bleexplorer.domain.model.BleError
import com.radustavila.bleexplorer.domain.model.BleException
import com.radustavila.bleexplorer.domain.model.GattValueSource
import com.radustavila.bleexplorer.domain.model.HardwareEvent
import com.radustavila.bleexplorer.domain.model.PocCommand
import com.radustavila.bleexplorer.domain.model.PocEvent
import com.radustavila.bleexplorer.domain.model.PocService
import com.radustavila.bleexplorer.domain.model.TouchTracker
import com.radustavila.bleexplorer.domain.model.findPocService
import com.radustavila.bleexplorer.domain.repository.BleGattRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Streams the POC service of the connected device; `null` while disconnected, before service
 * discovery completes, or when the device simply does not expose it.
 */
class ObservePocServiceUseCase @Inject constructor(
    private val repository: BleGattRepository,
) {
    operator fun invoke(): Flow<PocService?> =
        repository.services()
            .map { it.findPocService() }
            .distinctUntilChanged()
}

/**
 * Reads the POC Status characteristic and decodes it as text (`"ONLINE"`).
 *
 * The read itself goes through [ReadCharacteristicUseCase], so the connection and `READ` property
 * checks are the same as for any other characteristic.
 */
class ReadPocStatusUseCase @Inject constructor(
    private val repository: BleGattRepository,
    private val readCharacteristic: ReadCharacteristicUseCase,
) {
    suspend operator fun invoke(): Result<String> {
        if (!repository.connection().value.isConnected) {
            return Result.failure(BleException(BleError.NotConnected))
        }
        val status = repository.services().value.findPocService()?.status
            ?: return Result.failure(BleException(BleError.CharacteristicNotFound))

        return readCharacteristic(status.id).map { it.value.decodePocText() }
    }
}

/**
 * Writes a [PocCommand] to the POC Command characteristic.
 *
 * The write itself goes through [WriteCharacteristicUseCase], so the connection and `WRITE`
 * property checks, the write type, the GATT queue and its timeout are the same as for any other
 * characteristic. Success means the peripheral acknowledged the write; what it does with the
 * command is reported separately, on the Events characteristic.
 */
class SendPocCommandUseCase @Inject constructor(
    private val repository: BleGattRepository,
    private val writeCharacteristic: WriteCharacteristicUseCase,
) {
    suspend operator fun invoke(command: PocCommand): Result<Unit> {
        if (!repository.connection().value.isConnected) {
            return Result.failure(BleException(BleError.NotConnected))
        }
        val characteristic = repository.services().value.findPocService()?.command
            ?: return Result.failure(BleException(BleError.CharacteristicNotFound))

        return writeCharacteristic(characteristic.id, command.encode())
    }
}

/**
 * Turns notifications on or off on the POC Events characteristic.
 *
 * The CCCD write itself goes through [SetCharacteristicNotificationsUseCase], so the connection
 * and `NOTIFY` property checks, the GATT queue and its timeout are the same as for any other
 * characteristic. Subscribing is still never implicit: the caller does it because the user
 * connected to the POC device, whose whole point is to be watched.
 */
class SetPocEventsNotificationsUseCase @Inject constructor(
    private val repository: BleGattRepository,
    private val setCharacteristicNotifications: SetCharacteristicNotificationsUseCase,
) {
    suspend operator fun invoke(enabled: Boolean): Result<Unit> {
        if (!repository.connection().value.isConnected) {
            return Result.failure(BleException(BleError.NotConnected))
        }
        val characteristic = repository.services().value.findPocService()?.events
            ?: return Result.failure(BleException(BleError.CharacteristicNotFound))

        return setCharacteristicNotifications(characteristic.id, enabled)
    }
}

/**
 * Streams the events pushed on the POC Events characteristic, decoded as text, in arrival order.
 *
 * The stream follows the connected device's GATT database: it listens to the Events
 * characteristic while one is discovered and emits nothing otherwise. Only notifications count —
 * a value read on demand is not an event the firmware raised.
 */
class ObservePocEventsUseCase @Inject constructor(
    private val observePocService: ObservePocServiceUseCase,
    private val observeCharacteristicValues: ObserveCharacteristicValuesUseCase,
) {
    operator fun invoke(): Flow<PocEvent> =
        observePocService()
            .map { it?.events?.id }
            .distinctUntilChanged()
            .flatMapLatest { id -> if (id == null) emptyFlow() else observeCharacteristicValues(id) }
            .filter { it.source == GattValueSource.NOTIFICATION }
            .map { PocEvent(text = it.value.decodePocText(), timestampMillis = it.timestampMillis) }
}

/**
 * Streams the POC events interpreted as [HardwareEvent]s, with the hold duration of every touch
 * computed from the arrival times of its `TOUCH_ON` and `TOUCH_OFF` edges, and its peak / average
 * intensity from the `TOUCH_INTENSITY` samples received in between.
 *
 * Timing lives here rather than in a ViewModel or a Composable so the rule is written once and
 * tested without Android. A fresh [TouchTracker] is started every time the Events
 * characteristic is (re)discovered: a press seen before a link loss is never paired with a
 * release seen after the reconnection, which would report a hold that never happened (nor are
 * samples of two different touches mixed).
 */
class ObservePocHardwareEventsUseCase @Inject constructor(
    private val observePocService: ObservePocServiceUseCase,
    private val observePocEvents: ObservePocEventsUseCase,
) {
    operator fun invoke(): Flow<HardwareEvent> =
        observePocService()
            .map { it?.events?.id }
            .distinctUntilChanged()
            .flatMapLatest { id ->
                if (id == null) {
                    emptyFlow()
                } else {
                    flow {
                        val tracker = TouchTracker()
                        observePocEvents().collect { emit(tracker.onEvent(it)) }
                    }
                }
            }
}

/**
 * The firmware sends plain UTF-8. C strings are occasionally written with their terminator, so
 * trailing NULs are dropped together with surrounding whitespace.
 */
internal fun ByteArray.decodePocText(): String = decodeToString().trimEnd('\u0000').trim()
