package com.radustavila.myblesense.core.bluetooth.gatt

import com.radustavila.myblesense.core.bluetooth.central.IosCentral
import com.radustavila.myblesense.core.bluetooth.central.IosConnectionListener
import com.radustavila.myblesense.core.bluetooth.mapper.attErrorCodeToBleError
import com.radustavila.myblesense.core.bluetooth.mapper.disconnectionToBleError
import com.radustavila.myblesense.core.bluetooth.mapper.toByteArray
import com.radustavila.myblesense.core.bluetooth.mapper.toDomain
import com.radustavila.myblesense.core.bluetooth.mapper.toDomainUuid
import com.radustavila.myblesense.core.bluetooth.mapper.toNSData
import com.radustavila.myblesense.core.bluetooth.permission.BlePermissionChecker
import com.radustavila.myblesense.core.common.log.BleLog
import com.radustavila.myblesense.core.common.time.currentTimeMillis
import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.BleException
import com.radustavila.myblesense.domain.model.GattCharacteristicId
import com.radustavila.myblesense.domain.model.GattCharacteristicValue
import com.radustavila.myblesense.domain.model.GattConnection
import com.radustavila.myblesense.domain.model.GattConnectionState
import com.radustavila.myblesense.domain.model.GattService
import com.radustavila.myblesense.domain.model.GattValueSource
import com.radustavila.myblesense.domain.model.GattWriteType
import com.radustavila.myblesense.domain.repository.BleGattClient
import com.radustavila.myblesense.domain.repository.BluetoothStateMonitor
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import platform.CoreBluetooth.CBATTErrorDomain
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBCharacteristicPropertyIndicate
import platform.CoreBluetooth.CBCharacteristicPropertyNotify
import platform.CoreBluetooth.CBCharacteristicWriteWithResponse
import platform.CoreBluetooth.CBCharacteristicWriteWithoutResponse
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBPeripheralDelegateProtocol
import platform.CoreBluetooth.CBPeripheralStateDisconnected
import platform.CoreBluetooth.CBService
import platform.Foundation.NSError
import platform.darwin.NSObject
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [BleGattClient] on top of CoreBluetooth, with the same contract as the Android client:
 *
 *  - **One connection at a time.** [active] is the single source of truth; connecting somewhere
 *    else first tears the previous link down, and [release] is the only way a connection ends.
 *  - **Callbacks become flows.** The central and peripheral delegates only feed state flows, a
 *    shared value flow and the operation queue.
 *  - **Operations are serialized** through [GattOperationQueue], so timeouts and the "one
 *    operation at a time" rule behave exactly as on Android even though CoreBluetooth queues
 *    requests itself.
 *  - **[errors] only reports losses nobody asked for.** CoreBluetooth calls
 *    `didDisconnectPeripheral` for requested and unexpected disconnects alike, so the client
 *    remembers what it asked for: a disconnect requested by the user, and a connection it
 *    cancelled itself while tearing a link down, never reach [errors].
 *
 * What iOS does not have: hardware addresses (the device id is `CBPeripheral.identifier`, looked
 * up with `retrievePeripherals(withIdentifiers:)`, which is how a known device is reached without
 * scanning), an MTU request, a bonding API, and descriptor writes for the CCCD —
 * `setNotifyValue` does that.
 */
internal class IosBleGattClient(
    private val central: IosCentral,
    private val stateMonitor: BluetoothStateMonitor,
    private val permissionChecker: BlePermissionChecker,
    private val ioDispatcher: CoroutineDispatcher,
) : BleGattClient, IosConnectionListener {

    private val _connection = MutableStateFlow(GattConnection.Disconnected)
    override val connection: StateFlow<GattConnection> = _connection.asStateFlow()

    private val _services = MutableStateFlow<List<GattService>>(emptyList())
    override val services: StateFlow<List<GattService>> = _services.asStateFlow()

    private val _notifyingCharacteristics = MutableStateFlow<Set<GattCharacteristicId>>(emptySet())
    override val notifyingCharacteristics: StateFlow<Set<GattCharacteristicId>> =
        _notifyingCharacteristics.asStateFlow()

    private val _errors = MutableSharedFlow<BleError>(
        extraBufferCapacity = ERROR_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val _values = MutableSharedFlow<GattCharacteristicValue>(
        extraBufferCapacity = VALUE_BUFFER,
        // Notifications can arrive faster than the UI draws; dropping the oldest keeps the most
        // recent values flowing instead of blocking the CoreBluetooth queue.
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Scope of the adapter watchdog; every job it starts ends with the connection. */
    private val clientScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val connectionMutex = Mutex()

    @Volatile
    private var active: ConnectionHandle? = null

    /**
     * Identifiers of peripherals whose connection this client cancelled while releasing a link.
     * Their `didDisconnectPeripheral` is the echo of that cancel, not a link loss, and must not
     * be attributed to a newer connection to the same peripheral.
     */
    private val cancelling = MutableStateFlow<Set<String>>(emptySet())

    init {
        central.connectionListener = this
    }

    override fun errors(): Flow<BleError> = _errors.asSharedFlow()

    override fun characteristicValues(): Flow<GattCharacteristicValue> = _values.asSharedFlow()

    // --- Connection -----------------------------------------------------------------------

    override suspend fun connect(deviceId: String): Unit = withContext(ioDispatcher) {
        connectionMutex.withLock {
            if (!permissionChecker.hasConnectPermission()) throw BleException(BleError.PermissionDenied)
            if (!stateMonitor.currentState().isEnabled) throw BleException(BleError.BluetoothDisabled)

            active?.let { current ->
                if (current.deviceId == deviceId && _connection.value.isConnected) {
                    BleLog.i("GATT connect ignored: already connected")
                    return@withLock
                }
                // Only one link at a time; the previous one is closed before a new one is opened.
                release(current, reportError = null)
            }

            val peripheral = central.retrievePeripheral(deviceId)
                ?: throw BleException(BleError.DeviceNotFound)

            val handle = ConnectionHandle(deviceId, peripheral)
            active = handle
            peripheral.delegate = handle.delegate
            _connection.value = GattConnection(deviceId, GattConnectionState.CONNECTING)
            BleLog.i("GATT connecting to $deviceId")

            try {
                // CoreBluetooth never gives up on a connection request by itself; the timeout
                // is what makes an out-of-range peripheral fail instead of pending forever.
                central.manager.connectPeripheral(peripheral, options = null)
                withTimeout(CONNECT_TIMEOUT) { handle.connected.await() }
                _connection.value = GattConnection(deviceId, GattConnectionState.CONNECTED)
                BleLog.i("GATT connected to $deviceId")

                discoverServices(handle)
                _connection.value = GattConnection(
                    deviceId = deviceId,
                    state = GattConnectionState.CONNECTED,
                    servicesDiscovered = true,
                )
                watchAdapter(handle)
            } catch (e: TimeoutCancellationException) {
                release(handle, reportError = null)
                throw BleException(BleError.ConnectionTimeout, e)
            } catch (e: Throwable) {
                // Includes cancellation: leaving connect() must never leave a half-open link.
                release(handle, reportError = null)
                throw e
            }
        }
    }

    override suspend fun disconnect(): Unit = withContext(ioDispatcher) {
        connectionMutex.withLock {
            val handle = active ?: return@withLock
            BleLog.i("GATT disconnecting from ${handle.deviceId}")
            handle.userRequestedDisconnect = true
            _connection.update { it.copy(state = GattConnectionState.DISCONNECTING) }

            // Pending operations fail here rather than on a peripheral that is about to go away.
            handle.queue.close(BleError.Disconnected)
            if (stateMonitor.currentState().isEnabled) {
                central.manager.cancelPeripheralConnection(handle.peripheral)
            }

            // The callback normally arrives in a few milliseconds; the timeout only guarantees
            // that the teardown still happens if it never does.
            withTimeoutOrNull(DISCONNECT_TIMEOUT) { handle.disconnected.await() }
            release(handle, reportError = null)
        }
    }

    /** Services first, then the characteristics of each of them, as one queued operation. */
    private suspend fun discoverServices(handle: ConnectionHandle) {
        handle.queue.execute("discoverServices") {
            handle.peripheral.discoverServices(null)
            true
        }
        BleLog.i("GATT services discovered: ${_services.value.size}")
    }

    /**
     * Turning Bluetooth off invalidates every peripheral. The adapter is watched for as long as
     * the connection lives so the app reports it instead of waiting for operations to time out.
     */
    private fun watchAdapter(handle: ConnectionHandle) {
        handle.adapterWatchJob = stateMonitor.observeState()
            .onEach { state ->
                if (!state.isEnabled) {
                    BleLog.w("Bluetooth disabled while connected; closing GATT")
                    connectionMutex.withLock {
                        if (active === handle) release(handle, reportError = BleError.BluetoothDisabled)
                    }
                }
            }
            .launchIn(clientScope)
    }

    /**
     * Cancels the platform connection and resets every piece of connection state.
     *
     * Always call this — and only this — to end a connection: it is the single place that
     * cancels the link, detaches the delegate, stops the watchdog and clears the flows.
     */
    private fun release(handle: ConnectionHandle, reportError: BleError?) {
        handle.adapterWatchJob?.cancel()
        handle.queue.close(reportError ?: BleError.Disconnected)
        handle.connected.completeExceptionally(
            BleException(reportError ?: BleError.Disconnected),
        )
        handle.disconnected.complete(Unit)

        val peripheral = handle.peripheral
        // Cancelling on a manager that is not powered on is an API misuse, and pointless.
        if (peripheral.state != CBPeripheralStateDisconnected && stateMonitor.currentState().isEnabled) {
            cancelling.update { it + handle.peripheralId }
            central.manager.cancelPeripheralConnection(peripheral)
        }
        peripheral.delegate = null

        if (active === handle) {
            active = null
            _services.value = emptyList()
            _notifyingCharacteristics.value = emptySet()
            _connection.value = GattConnection.Disconnected
        }
        reportError?.let { _errors.tryEmit(it) }
        BleLog.i("GATT released (${handle.deviceId})")
    }

    // --- Central callbacks (CoreBluetooth queue) ------------------------------------------

    override fun onConnected(peripheral: CBPeripheral) {
        val id = peripheral.identifier.UUIDString
        // A new link is up, so the echo of any earlier cancel has been delivered or never will.
        cancelling.update { it - id }
        handleFor(id)?.connected?.complete(Unit)
    }

    override fun onFailedToConnect(peripheral: CBPeripheral, error: NSError?) {
        val handle = handleFor(peripheral.identifier.UUIDString) ?: return
        BleLog.i("GATT connection failed (code=${error?.code})")
        handle.connected.completeExceptionally(
            BleException(BleError.ConnectionFailed(error?.code?.toInt())),
        )
    }

    override fun onDisconnected(peripheral: CBPeripheral, error: NSError?) {
        val id = peripheral.identifier.UUIDString
        if (wasCancelledHere(id)) {
            BleLog.d("GATT disconnect callback of a released link ignored")
            return
        }
        val handle = handleFor(id) ?: return
        val bleError = disconnectionToBleError(error?.code?.toInt(), _connection.value.isConnected)
        BleLog.i("GATT disconnected (code=${error?.code}, error=$bleError)")

        if (handle.userRequestedDisconnect) {
            // release() runs in disconnect(), which is waiting for this signal.
            handle.disconnected.complete(Unit)
        } else {
            // Link lost, peripheral reset, out of range: nobody is waiting for it, so the
            // teardown and the error report happen here. It runs on the CoreBluetooth queue
            // and must not take connectionMutex; release() is idempotent and safe without it.
            if (active === handle) release(handle, reportError = bleError)
        }
    }

    private fun handleFor(peripheralId: String): ConnectionHandle? =
        active?.takeIf { it.peripheralId == peripheralId }

    private fun wasCancelledHere(peripheralId: String): Boolean {
        var cancelled = false
        cancelling.update { ids ->
            cancelled = peripheralId in ids
            ids - peripheralId
        }
        return cancelled
    }

    // --- Characteristic operations --------------------------------------------------------

    override suspend fun readCharacteristic(
        id: GattCharacteristicId,
    ): GattCharacteristicValue = withContext(ioDispatcher) {
        val handle = requireActive()
        val characteristic = handle.requireCharacteristic(id)

        val bytes = try {
            handle.queue.execute("read ${id.characteristicUuid}") {
                handle.pendingRead = characteristic
                handle.peripheral.readValueForCharacteristic(characteristic)
                true
            }
        } finally {
            handle.pendingRead = null
        } ?: throw BleException(BleError.CharacteristicReadFailed())

        GattCharacteristicValue(
            id = id,
            value = bytes,
            timestampMillis = currentTimeMillis(),
            source = GattValueSource.READ,
        ).also {
            BleLog.d("GATT read ok: ${id.characteristicUuid} (${bytes.size} bytes)")
            _values.tryEmit(it)
        }
    }

    override suspend fun writeCharacteristic(
        id: GattCharacteristicId,
        value: ByteArray,
        writeType: GattWriteType,
    ): Unit = withContext(ioDispatcher) {
        val handle = requireActive()
        val characteristic = handle.requireCharacteristic(id)

        BleLog.d("GATT write start: ${id.characteristicUuid} (${value.size} bytes, $writeType)")
        handle.queue.execute("write ${id.characteristicUuid}") {
            when (writeType) {
                GattWriteType.WITH_RESPONSE -> handle.peripheral.writeValue(
                    value.toNSData(),
                    forCharacteristic = characteristic,
                    type = CBCharacteristicWriteWithResponse,
                )

                GattWriteType.WITHOUT_RESPONSE -> {
                    handle.peripheral.writeValue(
                        value.toNSData(),
                        forCharacteristic = characteristic,
                        type = CBCharacteristicWriteWithoutResponse,
                    )
                    // CoreBluetooth reports nothing for an unacknowledged write.
                    handle.queue.complete()
                }
            }
            true
        }
        BleLog.d("GATT write ok: ${id.characteristicUuid}")
    }

    override suspend fun setNotificationsEnabled(
        id: GattCharacteristicId,
        enabled: Boolean,
    ): Unit = withContext(ioDispatcher) {
        val handle = requireActive()
        val characteristic = handle.requireCharacteristic(id)

        // A characteristic that can neither notify nor indicate has no CCCD to configure.
        if (characteristic.properties and SUBSCRIPTION_PROPERTIES == 0uL) {
            throw BleException(BleError.DescriptorNotFound)
        }

        BleLog.i("GATT ${if (enabled) "enabling" else "disabling"} notifications on ${id.characteristicUuid}")
        // setNotifyValue writes the CCCD (notify or indicate, whichever is declared) itself.
        handle.queue.execute("cccd ${id.characteristicUuid}") {
            handle.peripheral.setNotifyValue(enabled, forCharacteristic = characteristic)
            true
        }

        _notifyingCharacteristics.update { current ->
            if (enabled) current + id else current - id
        }
    }

    // --- Helpers --------------------------------------------------------------------------

    private fun requireActive(): ConnectionHandle {
        val handle = active
        if (handle == null || !_connection.value.isConnected) {
            throw BleException(BleError.NotConnected)
        }
        return handle
    }

    /**
     * Everything that belongs to one connection: the peripheral, its operation queue, the
     * delegate that feeds them and the handshake signals `connect`/`disconnect` await.
     */
    private inner class ConnectionHandle(
        val deviceId: String,
        val peripheral: CBPeripheral,
    ) : PeripheralCallbacks {

        /** As CoreBluetooth spells it, to match callbacks against this connection. */
        val peripheralId: String = peripheral.identifier.UUIDString

        // CBPeripheral keeps its delegate weakly; this reference is what keeps it alive.
        val delegate = PeripheralDelegate(this)

        @Volatile
        var userRequestedDisconnect: Boolean = false

        var adapterWatchJob: Job? = null

        val queue = GattOperationQueue()
        val connected = CompletableDeferred<Unit>()
        val disconnected = CompletableDeferred<Unit>()

        /**
         * CoreBluetooth reports read responses and notifications through the same callback. The
         * characteristic being read is remembered so its next value completes the read.
         */
        @Volatile
        var pendingRead: CBCharacteristic? = null

        /** Only touched on the CoreBluetooth queue. */
        private var servicesAwaitingCharacteristics = 0

        fun requireCharacteristic(id: GattCharacteristicId): CBCharacteristic =
            peripheral.services.orEmpty()
                .filterIsInstance<CBService>()
                .firstOrNull { it.UUID.toDomainUuid().equals(id.serviceUuid, ignoreCase = true) }
                ?.characteristics.orEmpty()
                .filterIsInstance<CBCharacteristic>()
                .firstOrNull { it.UUID.toDomainUuid().equals(id.characteristicUuid, ignoreCase = true) }
                ?: throw BleException(BleError.CharacteristicNotFound)

        override fun onServicesDiscovered(error: NSError?) {
            if (error != null) {
                queue.fail(BleError.ServiceDiscoveryFailed)
                return
            }
            val discovered = peripheral.services.orEmpty().filterIsInstance<CBService>()
            if (discovered.isEmpty()) {
                _services.value = emptyList()
                queue.complete()
                return
            }
            servicesAwaitingCharacteristics = discovered.size
            discovered.forEach { peripheral.discoverCharacteristics(null, forService = it) }
        }

        override fun onCharacteristicsDiscovered(service: CBService, error: NSError?) {
            if (error != null) {
                queue.fail(BleError.ServiceDiscoveryFailed)
                return
            }
            servicesAwaitingCharacteristics--
            if (servicesAwaitingCharacteristics > 0) return
            _services.value = peripheral.services.orEmpty()
                .filterIsInstance<CBService>()
                .map { it.toDomain() }
            queue.complete()
        }

        override fun onValueUpdated(characteristic: CBCharacteristic, error: NSError?) {
            if (pendingRead == characteristic) {
                pendingRead = null
                if (error != null) {
                    queue.fail(error.toBleError { BleError.CharacteristicReadFailed(it) })
                } else {
                    queue.complete(characteristic.value?.toByteArray() ?: ByteArray(0))
                }
                return
            }
            if (error != null) {
                BleLog.w("GATT notification error on ${characteristic.UUID.UUIDString} (code=${error.code})")
                return
            }
            publishNotification(characteristic)
        }

        override fun onValueWritten(characteristic: CBCharacteristic, error: NSError?) {
            if (error != null) {
                queue.fail(error.toBleError { BleError.CharacteristicWriteFailed(it) })
            } else {
                queue.complete()
            }
        }

        override fun onNotificationStateUpdated(characteristic: CBCharacteristic, error: NSError?) {
            if (error != null) {
                queue.fail(error.toBleError { BleError.NotificationEnableFailed(it) })
            } else {
                queue.complete()
            }
        }

        /** Notifications are unsolicited: they never touch the operation queue. */
        private fun publishNotification(characteristic: CBCharacteristic) {
            val serviceUuid = characteristic.service?.UUID?.toDomainUuid() ?: return
            val value = characteristic.value?.toByteArray() ?: ByteArray(0)
            BleLog.d("GATT notification ${characteristic.UUID.UUIDString}: ${value.size} bytes")
            _values.tryEmit(
                GattCharacteristicValue(
                    id = GattCharacteristicId(serviceUuid, characteristic.UUID.toDomainUuid()),
                    value = value,
                    timestampMillis = currentTimeMillis(),
                    source = GattValueSource.NOTIFICATION,
                ),
            )
        }
    }

    private companion object {
        val CONNECT_TIMEOUT: Duration = 15.seconds
        val DISCONNECT_TIMEOUT: Duration = 5.seconds
        const val ERROR_BUFFER = 8
        const val VALUE_BUFFER = 64
        val SUBSCRIPTION_PROPERTIES: ULong = CBCharacteristicPropertyNotify or CBCharacteristicPropertyIndicate
    }
}

private fun NSError.toBleError(onFailure: (Int) -> BleError): BleError {
    val code = code.toInt()
    return if (domain == CBATTErrorDomain) attErrorCodeToBleError(code, onFailure) else onFailure(code)
}

/** The peripheral delegate methods the client uses, without their Objective-C selector names. */
private interface PeripheralCallbacks {
    fun onServicesDiscovered(error: NSError?)

    fun onCharacteristicsDiscovered(service: CBService, error: NSError?)

    fun onValueUpdated(characteristic: CBCharacteristic, error: NSError?)

    fun onValueWritten(characteristic: CBCharacteristic, error: NSError?)

    fun onNotificationStateUpdated(characteristic: CBCharacteristic, error: NSError?)
}

/**
 * `CBPeripheralDelegate` that only forwards. Several of its methods share one Kotlin signature
 * and differ by parameter name alone, hence [ObjCSignatureOverride].
 */
private class PeripheralDelegate(
    private val callbacks: PeripheralCallbacks,
) : NSObject(), CBPeripheralDelegateProtocol {

    override fun peripheral(peripheral: CBPeripheral, didDiscoverServices: NSError?) =
        callbacks.onServicesDiscovered(didDiscoverServices)

    override fun peripheral(
        peripheral: CBPeripheral,
        didDiscoverCharacteristicsForService: CBService,
        error: NSError?,
    ) = callbacks.onCharacteristicsDiscovered(didDiscoverCharacteristicsForService, error)

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didUpdateValueForCharacteristic: CBCharacteristic,
        error: NSError?,
    ) = callbacks.onValueUpdated(didUpdateValueForCharacteristic, error)

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didWriteValueForCharacteristic: CBCharacteristic,
        error: NSError?,
    ) = callbacks.onValueWritten(didWriteValueForCharacteristic, error)

    @ObjCSignatureOverride
    override fun peripheral(
        peripheral: CBPeripheral,
        didUpdateNotificationStateForCharacteristic: CBCharacteristic,
        error: NSError?,
    ) = callbacks.onNotificationStateUpdated(didUpdateNotificationStateForCharacteristic, error)
}
