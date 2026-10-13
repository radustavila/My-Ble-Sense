package com.radustavila.myblesense.core.bluetooth.gatt

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import com.radustavila.myblesense.core.bluetooth.mapper.connectionStatusToBleError
import com.radustavila.myblesense.core.bluetooth.mapper.gattStatusToBleError
import com.radustavila.myblesense.core.bluetooth.mapper.toDomain
import com.radustavila.myblesense.core.bluetooth.permission.BlePermissionChecker
import com.radustavila.myblesense.core.bluetooth.state.AndroidBluetoothStateMonitor
import com.radustavila.myblesense.core.common.log.BleLog
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
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [BleGattClient] on top of `BluetoothGatt`.
 *
 * Design notes, in the order they matter:
 *
 *  - **One connection at a time.** [active] is the single source of truth; connecting somewhere
 *    else first tears the previous link down. That keeps `close()` reachable on every path, which
 *    is what prevents the classic leak of a `BluetoothGatt` (and of the 32 client interfaces the
 *    stack has in total).
 *  - **Callbacks become flows.** The `BluetoothGattCallback` only feeds state flows, a shared
 *    value flow and the operation queue. Nothing above this class ever sees a callback.
 *  - **Operations are serialized.** Every request goes through [GattOperationQueue]; see the
 *    reasoning there.
 *  - **The connection is guarded by [connectionMutex].** Connect and disconnect can be triggered
 *    from two screens; the mutex makes "connect while disconnecting" well defined instead of
 *    racy.
 *  - **The adapter is watched while connected.** Bluetooth being switched off does not reliably
 *    produce a disconnect callback, so the adapter state is watched explicitly and the link is
 *    torn down as soon as it leaves the enabled state.
 */
class AndroidBleGattClient(
    private val context: Context,
    private val stateMonitor: AndroidBluetoothStateMonitor,
    private val permissionChecker: BlePermissionChecker,
    private val ioDispatcher: CoroutineDispatcher,
) : BleGattClient {

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
        // recent values flowing instead of blocking the binder thread that delivers them.
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * Scope of the adapter watchdog. It belongs to this singleton (the connection outlives every
     * ViewModel by design) and every job it starts is cancelled when the connection is released.
     */
    private val clientScope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val connectionMutex = Mutex()

    @Volatile
    private var active: ConnectionHandle? = null

    override fun errors(): Flow<BleError> = _errors.asSharedFlow()

    override fun characteristicValues(): Flow<GattCharacteristicValue> = _values.asSharedFlow()

    // --- Connection -----------------------------------------------------------------------

    @SuppressLint("MissingPermission") // Guarded by requireConnectPermission().
    override suspend fun connect(deviceId: String): Unit = withContext(ioDispatcher) {
        connectionMutex.withLock {
            requireConnectPermission()
            if (!stateMonitor.currentState().isEnabled) throw BleException(BleError.BluetoothDisabled)

            active?.let { current ->
                if (current.deviceId == deviceId && _connection.value.isConnected) {
                    BleLog.i("GATT connect ignored: already connected")
                    return@withLock
                }
                // Only one link at a time; the previous one is closed before a new one is opened.
                release(current, reportError = null)
            }

            val adapter = stateMonitor.adapter ?: throw BleException(BleError.BluetoothDisabled)
            val device = try {
                adapter.getRemoteDevice(deviceId)
            } catch (e: IllegalArgumentException) {
                throw BleException(BleError.DeviceNotFound, e)
            }

            val handle = ConnectionHandle(deviceId)
            active = handle
            _connection.value = GattConnection(deviceId, GattConnectionState.CONNECTING)
            BleLog.i("GATT connecting to $deviceId")

            try {
                // autoConnect = false: a direct connection attempt that fails fast, instead of a
                // background attempt that would linger and reconnect on its own.
                handle.gatt = device.connectGatt(
                    context,
                    false,
                    handle,
                    BluetoothDevice.TRANSPORT_LE,
                ) ?: throw BleException(BleError.ConnectionFailed())

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
            } catch (e: SecurityException) {
                release(handle, reportError = null)
                throw BleException(BleError.PermissionDenied, e)
            } catch (e: Throwable) {
                // Includes cancellation: leaving connect() must never leave a half-open link.
                release(handle, reportError = null)
                throw e
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun disconnect(): Unit = withContext(ioDispatcher) {
        connectionMutex.withLock {
            val handle = active ?: return@withLock
            BleLog.i("GATT disconnecting from ${handle.deviceId}")
            handle.userRequestedDisconnect = true
            _connection.update { it.copy(state = GattConnectionState.DISCONNECTING) }

            // Pending operations fail here rather than on a GATT object that is about to close.
            handle.queue.close(BleError.Disconnected)
            runCatching { handle.gatt?.disconnect() }
                .onFailure { BleLog.w("GATT disconnect call failed", it) }

            // The callback normally arrives in a few milliseconds; the timeout only guarantees
            // that close() still happens if it never does.
            withTimeoutOrNull(DISCONNECT_TIMEOUT) { handle.disconnected.await() }
            release(handle, reportError = null)
        }
    }

    @SuppressLint("MissingPermission") // Only reached from connect(), after the permission check.
    private suspend fun discoverServices(handle: ConnectionHandle) {
        val gatt = handle.gatt ?: throw BleException(BleError.NotConnected)
        handle.queue.execute("discoverServices") { gatt.discoverServices() }
        BleLog.i("GATT services discovered: ${_services.value.size}")
    }

    /**
     * Turning Bluetooth off invalidates every GATT object. The adapter is watched for as long as
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
     * Closes the platform GATT object and resets every piece of connection state.
     *
     * Always call this — and only this — to end a connection: it is the single place that calls
     * `close()`, cancels the watchdog and clears the flows, so no path can forget one of them.
     */
    @SuppressLint("MissingPermission")
    private fun release(handle: ConnectionHandle, reportError: BleError?) {
        handle.adapterWatchJob?.cancel()
        handle.queue.close(reportError ?: BleError.Disconnected)
        handle.connected.completeExceptionally(
            BleException(reportError ?: BleError.Disconnected),
        )
        handle.disconnected.complete(Unit)

        runCatching { handle.gatt?.close() }
            .onFailure { BleLog.w("GATT close failed", it) }
        handle.gatt = null

        if (active === handle) {
            active = null
            _services.value = emptyList()
            _notifyingCharacteristics.value = emptySet()
            _connection.value = GattConnection.Disconnected
        }
        reportError?.let { _errors.tryEmit(it) }
        BleLog.i("GATT released (${handle.deviceId})")
    }

    // --- Characteristic operations --------------------------------------------------------

    @SuppressLint("MissingPermission")
    override suspend fun readCharacteristic(
        id: GattCharacteristicId,
    ): GattCharacteristicValue = withContext(ioDispatcher) {
        val handle = requireActive()
        val gatt = handle.requireGatt()
        val characteristic = handle.requireCharacteristic(id)

        val bytes = runGattOperation("read ${id.characteristicUuid}") {
            handle.queue.execute("read ${id.characteristicUuid}") {
                gatt.readCharacteristic(characteristic)
            }
        } ?: throw BleException(BleError.CharacteristicReadFailed())

        GattCharacteristicValue(
            id = id,
            value = bytes,
            timestampMillis = System.currentTimeMillis(),
            source = GattValueSource.READ,
        ).also {
            BleLog.d("GATT read ok: ${id.characteristicUuid} (${bytes.size} bytes)")
            _values.tryEmit(it)
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun writeCharacteristic(
        id: GattCharacteristicId,
        value: ByteArray,
        writeType: GattWriteType,
    ): Unit = withContext(ioDispatcher) {
        val handle = requireActive()
        val gatt = handle.requireGatt()
        val characteristic = handle.requireCharacteristic(id)
        val platformWriteType = when (writeType) {
            GattWriteType.WITH_RESPONSE -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            GattWriteType.WITHOUT_RESPONSE -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }

        BleLog.d("GATT write start: ${id.characteristicUuid} (${value.size} bytes, $writeType)")
        runGattOperation("write ${id.characteristicUuid}") {
            handle.queue.execute("write ${id.characteristicUuid}") {
                writeCharacteristicCompat(gatt, characteristic, value, platformWriteType)
            }
        }
        BleLog.d("GATT write ok: ${id.characteristicUuid}")
    }

    @SuppressLint("MissingPermission")
    override suspend fun setNotificationsEnabled(
        id: GattCharacteristicId,
        enabled: Boolean,
    ): Unit = withContext(ioDispatcher) {
        val handle = requireActive()
        val gatt = handle.requireGatt()
        val characteristic = handle.requireCharacteristic(id)

        // Two steps are required and both matter: the local one tells the Android stack to
        // deliver the callbacks, the CCCD write tells the peripheral to send anything at all.
        if (!gatt.setCharacteristicNotification(characteristic, enabled)) {
            throw BleException(BleError.NotificationEnableFailed())
        }
        val cccd = characteristic.getDescriptor(CCCD_UUID)
            ?: throw BleException(BleError.DescriptorNotFound)

        val payload = when {
            !enabled -> BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
            characteristic.supportsNotify() -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }

        BleLog.i("GATT ${if (enabled) "enabling" else "disabling"} notifications on ${id.characteristicUuid}")
        runGattOperation("cccd ${id.characteristicUuid}") {
            handle.queue.execute("cccd ${id.characteristicUuid}") {
                writeDescriptorCompat(gatt, cccd, payload)
            }
        }

        _notifyingCharacteristics.update { current ->
            if (enabled) current + id else current - id
        }
    }

    // --- Platform compatibility -----------------------------------------------------------

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION") // Pre-API 33 path; the modern overloads are used above it.
    private fun writeCharacteristicCompat(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
        writeType: Int,
    ): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeCharacteristic(characteristic, value, writeType) == BluetoothStatusCodes.SUCCESS
    } else {
        characteristic.writeType = writeType
        characteristic.value = value
        gatt.writeCharacteristic(characteristic)
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun writeDescriptorCompat(
        gatt: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray,
    ): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        gatt.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
    } else {
        descriptor.value = value
        gatt.writeDescriptor(descriptor)
    }

    // --- Helpers --------------------------------------------------------------------------

    private fun requireActive(): ConnectionHandle {
        val handle = active
        if (handle == null || !_connection.value.isConnected) {
            throw BleException(BleError.NotConnected)
        }
        return handle
    }

    private fun requireConnectPermission() {
        if (!permissionChecker.hasConnectPermission()) throw BleException(BleError.PermissionDenied)
    }

    /** Normalises the two failure modes the platform can still produce at call time. */
    private inline fun <T> runGattOperation(label: String, block: () -> T): T = try {
        block()
    } catch (e: SecurityException) {
        BleLog.w("GATT $label failed: permission revoked", e)
        throw BleException(BleError.PermissionDenied, e)
    } catch (e: IllegalStateException) {
        BleLog.w("GATT $label failed: invalid stack state", e)
        throw BleException(BleError.GattOperationRejected, e)
    }

    /**
     * Everything that belongs to one connection: the platform object, its operation queue, the
     * callback that feeds them and the handshake signals `connect`/`disconnect` await.
     *
     * Being the `BluetoothGattCallback` itself means the callback and the state it owns cannot
     * drift apart, and that a stale callback can be recognised by comparing it with [active].
     */
    private inner class ConnectionHandle(val deviceId: String) : BluetoothGattCallback() {

        @Volatile
        var gatt: BluetoothGatt? = null

        @Volatile
        var userRequestedDisconnect: Boolean = false

        var adapterWatchJob: Job? = null

        val queue = GattOperationQueue()
        val connected = CompletableDeferred<Unit>()
        val disconnected = CompletableDeferred<Unit>()

        fun requireGatt(): BluetoothGatt = gatt ?: throw BleException(BleError.NotConnected)

        fun requireCharacteristic(id: GattCharacteristicId): BluetoothGattCharacteristic {
            val gatt = requireGatt()
            val characteristic = try {
                gatt.getService(UUID.fromString(id.serviceUuid))
                    ?.getCharacteristic(UUID.fromString(id.characteristicUuid))
            } catch (e: IllegalArgumentException) {
                throw BleException(BleError.CharacteristicNotFound, e)
            }
            return characteristic ?: throw BleException(BleError.CharacteristicNotFound)
        }

        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            val wasConnected = _connection.value.isConnected
            when {
                newState == BluetoothProfile.STATE_CONNECTED &&
                    status == BluetoothGatt.GATT_SUCCESS -> connected.complete(Unit)

                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    val error = connectionStatusToBleError(status, wasConnected)
                    BleLog.i("GATT disconnected (status=$status, error=$error)")
                    if (userRequestedDisconnect) {
                        // release() runs in disconnect(), which is waiting for this signal.
                        disconnected.complete(Unit)
                    } else {
                        // Link lost, peripheral reset, out of range: nobody is waiting for it,
                        // so the teardown and the error report happen here.
                        onUnexpectedDisconnect(this, error)
                    }
                }

                status != BluetoothGatt.GATT_SUCCESS ->
                    connected.completeExceptionally(
                        BleException(connectionStatusToBleError(status, wasConnected)),
                    )
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS || gatt == null) {
                queue.fail(BleError.ServiceDiscoveryFailed)
                return
            }
            _services.value = gatt.services.orEmpty().map { it.toDomain() }
            queue.complete()
        }

        @Suppress("DEPRECATION") // Callback used below API 33; the modern one is right after it.
        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            completeRead(characteristic, characteristic?.value ?: ByteArray(0), status)
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) = completeRead(characteristic, value, status)

        private fun completeRead(
            characteristic: BluetoothGattCharacteristic?,
            value: ByteArray,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                queue.fail(gattStatusToBleError(status) { BleError.CharacteristicReadFailed(it) })
                return
            }
            BleLog.d("GATT onCharacteristicRead ${characteristic?.uuid}: ${value.size} bytes")
            queue.complete(value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                queue.fail(gattStatusToBleError(status) { BleError.CharacteristicWriteFailed(it) })
                return
            }
            queue.complete()
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt?,
            descriptor: BluetoothGattDescriptor?,
            status: Int,
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                queue.fail(gattStatusToBleError(status) { BleError.NotificationEnableFailed(it) })
                return
            }
            queue.complete()
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            characteristic ?: return
            publishNotification(characteristic, characteristic.value ?: ByteArray(0))
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) = publishNotification(characteristic, value)

        /** Notifications are unsolicited: they never touch the operation queue. */
        private fun publishNotification(
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            val serviceUuid = characteristic.service?.uuid?.toString() ?: return
            val id = GattCharacteristicId(serviceUuid, characteristic.uuid.toString())
            BleLog.d("GATT notification ${characteristic.uuid}: ${value.size} bytes")
            _values.tryEmit(
                GattCharacteristicValue(
                    id = id,
                    value = value,
                    timestampMillis = System.currentTimeMillis(),
                    source = GattValueSource.NOTIFICATION,
                ),
            )
        }
    }

    /**
     * Teardown for a disconnect nobody asked for. It runs on the callback thread, so it must not
     * take [connectionMutex] — [release] is safe to call without it because it only touches
     * `@Volatile`/atomic state and is idempotent.
     */
    private fun onUnexpectedDisconnect(handle: ConnectionHandle, error: BleError) {
        if (active !== handle) return
        release(handle, reportError = error)
    }

    private companion object {
        val CONNECT_TIMEOUT: Duration = 15.seconds
        val DISCONNECT_TIMEOUT: Duration = 5.seconds

        const val ERROR_BUFFER = 8
        const val VALUE_BUFFER = 64

        /** Client Characteristic Configuration Descriptor, the switch for notify/indicate. */
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}

private fun BluetoothGattCharacteristic.supportsNotify(): Boolean =
    properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
