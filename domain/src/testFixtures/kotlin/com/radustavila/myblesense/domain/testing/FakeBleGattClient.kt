package com.radustavila.myblesense.domain.testing

import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.BleException
import com.radustavila.myblesense.domain.model.GattCharacteristic
import com.radustavila.myblesense.domain.model.GattCharacteristicId
import com.radustavila.myblesense.domain.model.GattCharacteristicProperty
import com.radustavila.myblesense.domain.model.GattCharacteristicValue
import com.radustavila.myblesense.domain.model.GattConnection
import com.radustavila.myblesense.domain.model.GattConnectionState
import com.radustavila.myblesense.domain.model.GattService
import com.radustavila.myblesense.domain.model.GattValueSource
import com.radustavila.myblesense.domain.model.GattWriteType
import com.radustavila.myblesense.domain.model.PocProfile
import com.radustavila.myblesense.domain.repository.BleGattClient
import com.radustavila.myblesense.domain.repository.BleGattRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Deterministic [BleGattClient] for tests: connections, reads, writes, notifications and failures
 * are driven by the test instead of by a peripheral. No test in this project needs BLE hardware.
 */
class FakeBleGattClient : BleGattClient {

    private val _connection = MutableStateFlow(GattConnection.Disconnected)
    override val connection: StateFlow<GattConnection> = _connection.asStateFlow()

    private val _services = MutableStateFlow<List<GattService>>(emptyList())
    override val services: StateFlow<List<GattService>> = _services.asStateFlow()

    private val _notifying = MutableStateFlow<Set<GattCharacteristicId>>(emptySet())
    override val notifyingCharacteristics: StateFlow<Set<GattCharacteristicId>> =
        _notifying.asStateFlow()

    private val _errors = MutableSharedFlow<BleError>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _values = MutableSharedFlow<GattCharacteristicValue>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    // --- Programmable behaviour -----------------------------------------------------------

    /** GATT database reported once [connect] succeeds. */
    var servicesOnConnect: List<GattService> = emptyList()

    /** Payload returned by [readCharacteristic], per characteristic. */
    val readValues: MutableMap<GattCharacteristicId, ByteArray> = mutableMapOf()

    var connectFailure: BleError? = null

    /** Failures consumed one per [connect] call, before [connectFailure] applies. */
    val queuedConnectFailures: ArrayDeque<BleError> = ArrayDeque()
    var readFailure: BleError? = null
    var writeFailure: BleError? = null
    var notificationFailure: BleError? = null

    /** Makes [connect] take virtual time, so "connecting" is observable in tests. */
    var connectDelayMillis: Long = 0

    /** Makes [writeCharacteristic] take virtual time, so "sending" is observable in tests. */
    var writeDelayMillis: Long = 0

    /** Deterministic clock used for value timestamps. */
    var currentTimeMillis: Long = 0

    // --- Recorded interactions ------------------------------------------------------------

    var connectCount: Int = 0
        private set
    var disconnectCount: Int = 0
        private set

    val writes: MutableList<RecordedWrite> = mutableListOf()

    /** Every CCCD change that reached the stack, in order: characteristic to "enabled". */
    val notificationChanges: MutableList<Pair<GattCharacteristicId, Boolean>> = mutableListOf()

    data class RecordedWrite(
        val id: GattCharacteristicId,
        val value: ByteArray,
        val writeType: GattWriteType,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is RecordedWrite) return false
            return id == other.id && writeType == other.writeType &&
                value.contentEquals(other.value)
        }

        override fun hashCode(): Int =
            31 * (31 * id.hashCode() + value.contentHashCode()) + writeType.hashCode()
    }

    // --- BleGattClient --------------------------------------------------------------------

    override fun errors(): Flow<BleError> = _errors.asSharedFlow()

    override fun characteristicValues(): Flow<GattCharacteristicValue> = _values.asSharedFlow()

    override suspend fun connect(deviceId: String) {
        connectCount++
        _connection.value = GattConnection(deviceId, GattConnectionState.CONNECTING)
        try {
            if (connectDelayMillis > 0) delay(connectDelayMillis)
        } catch (e: CancellationException) {
            _connection.value = GattConnection.Disconnected
            throw e
        }
        (queuedConnectFailures.removeFirstOrNull() ?: connectFailure)?.let { error ->
            _connection.value = GattConnection.Disconnected
            throw BleException(error)
        }
        _services.value = servicesOnConnect
        _connection.value = GattConnection(
            deviceId = deviceId,
            state = GattConnectionState.CONNECTED,
            servicesDiscovered = true,
        )
    }

    override suspend fun disconnect() {
        disconnectCount++
        reset()
    }

    override suspend fun readCharacteristic(id: GattCharacteristicId): GattCharacteristicValue {
        requireConnected()
        readFailure?.let { throw BleException(it) }
        val value = GattCharacteristicValue(
            id = id,
            value = readValues[id] ?: ByteArray(0),
            timestampMillis = currentTimeMillis,
            source = GattValueSource.READ,
        )
        _values.tryEmit(value)
        return value
    }

    override suspend fun writeCharacteristic(
        id: GattCharacteristicId,
        value: ByteArray,
        writeType: GattWriteType,
    ) {
        requireConnected()
        if (writeDelayMillis > 0) delay(writeDelayMillis)
        writeFailure?.let { throw BleException(it) }
        writes += RecordedWrite(id, value, writeType)
    }

    override suspend fun setNotificationsEnabled(id: GattCharacteristicId, enabled: Boolean) {
        requireConnected()
        notificationFailure?.let { throw BleException(it) }
        notificationChanges += id to enabled
        _notifying.update { if (enabled) it + id else it - id }
    }

    // --- Test driving ---------------------------------------------------------------------

    /** Pushes a notification, as a subscribed peripheral would. */
    fun emitNotification(id: GattCharacteristicId, value: ByteArray) {
        _values.tryEmit(
            GattCharacteristicValue(
                id = id,
                value = value,
                timestampMillis = currentTimeMillis,
                source = GattValueSource.NOTIFICATION,
            ),
        )
    }

    /** Simulates a link the app did not close itself: out of range, peripheral reset, adapter off. */
    fun simulateUnexpectedDisconnect(error: BleError = BleError.Disconnected) {
        reset()
        _errors.tryEmit(error)
    }

    private fun reset() {
        _connection.value = GattConnection.Disconnected
        _services.value = emptyList()
        _notifying.value = emptySet()
    }

    private fun requireConnected() {
        if (!_connection.value.isConnected) throw BleException(BleError.NotConnected)
    }
}

/**
 * [BleGattRepository] over [FakeBleGattClient], converting failures to `Result` exactly like the
 * Android implementation does, so use case and ViewModel tests exercise the real contract.
 */
class FakeBleGattRepository(
    val client: FakeBleGattClient = FakeBleGattClient(),
) : BleGattRepository {

    override fun connection(): StateFlow<GattConnection> = client.connection

    override fun services(): StateFlow<List<GattService>> = client.services

    override fun notifyingCharacteristics(): StateFlow<Set<GattCharacteristicId>> =
        client.notifyingCharacteristics

    override fun errors(): Flow<BleError> = client.errors()

    override fun characteristicValues(): Flow<GattCharacteristicValue> = client.characteristicValues()

    override suspend fun connect(deviceId: String): Result<Unit> =
        gattCall { client.connect(deviceId) }

    override suspend fun disconnect(): Result<Unit> = gattCall { client.disconnect() }

    override suspend fun readCharacteristic(
        id: GattCharacteristicId,
    ): Result<GattCharacteristicValue> = gattCall { client.readCharacteristic(id) }

    override suspend fun writeCharacteristic(
        id: GattCharacteristicId,
        value: ByteArray,
        writeType: GattWriteType,
    ): Result<Unit> = gattCall { client.writeCharacteristic(id, value, writeType) }

    override suspend fun setNotificationsEnabled(
        id: GattCharacteristicId,
        enabled: Boolean,
    ): Result<Unit> = gattCall { client.setNotificationsEnabled(id, enabled) }

    private inline fun <T> gattCall(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }
}

/** Builds a characteristic with the given properties; the defaults keep tests to the point. */
fun gattCharacteristic(
    serviceUuid: String = BATTERY_SERVICE_UUID,
    characteristicUuid: String = BATTERY_LEVEL_UUID,
    properties: Set<GattCharacteristicProperty> = setOf(GattCharacteristicProperty.READ),
): GattCharacteristic = GattCharacteristic(
    id = GattCharacteristicId(serviceUuid, characteristicUuid),
    properties = properties,
)

fun gattService(
    uuid: String = BATTERY_SERVICE_UUID,
    characteristics: List<GattCharacteristic> = listOf(gattCharacteristic(serviceUuid = uuid)),
): GattService = GattService(uuid = uuid, characteristics = characteristics)

const val BATTERY_SERVICE_UUID = "0000180f-0000-1000-8000-00805f9b34fb"
const val BATTERY_LEVEL_UUID = "00002a19-0000-1000-8000-00805f9b34fb"
const val CUSTOM_SERVICE_UUID = "12345678-1234-5678-1234-56789abcdef0"
const val CUSTOM_CHARACTERISTIC_UUID = "12345678-1234-5678-1234-56789abcdef1"

/** The ESP32 POC service exactly as the firmware declares it: Status (READ), Command (WRITE), Events (NOTIFY). */
fun pocGattService(
    characteristics: List<GattCharacteristic> = listOf(
        gattCharacteristic(
            serviceUuid = PocProfile.SERVICE_UUID,
            characteristicUuid = PocProfile.STATUS_UUID,
            properties = setOf(GattCharacteristicProperty.READ),
        ),
        gattCharacteristic(
            serviceUuid = PocProfile.SERVICE_UUID,
            characteristicUuid = PocProfile.COMMAND_UUID,
            properties = setOf(GattCharacteristicProperty.WRITE),
        ),
        gattCharacteristic(
            serviceUuid = PocProfile.SERVICE_UUID,
            characteristicUuid = PocProfile.EVENTS_UUID,
            properties = setOf(GattCharacteristicProperty.NOTIFY),
        ),
    ),
): GattService = GattService(uuid = PocProfile.SERVICE_UUID, characteristics = characteristics)

val POC_STATUS_ID = GattCharacteristicId(PocProfile.SERVICE_UUID, PocProfile.STATUS_UUID)

val POC_COMMAND_ID = GattCharacteristicId(PocProfile.SERVICE_UUID, PocProfile.COMMAND_UUID)

val POC_EVENTS_ID = GattCharacteristicId(PocProfile.SERVICE_UUID, PocProfile.EVENTS_UUID)
