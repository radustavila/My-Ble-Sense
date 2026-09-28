package com.radustavila.myblesense.feature.devicedetails

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.myblesense.core.common.di.ApplicationScope
import com.radustavila.myblesense.core.designsystem.text.toUserMessage
import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.BluetoothState
import com.radustavila.myblesense.domain.model.GattConnection
import com.radustavila.myblesense.domain.model.GattConnectionState
import com.radustavila.myblesense.domain.model.HardwareEvent
import com.radustavila.myblesense.domain.model.PocCommand
import com.radustavila.myblesense.domain.model.PocService
import com.radustavila.myblesense.domain.model.ReconnectionStatus
import com.radustavila.myblesense.domain.model.isRecoverableLinkLoss
import com.radustavila.myblesense.domain.model.toBleError
import com.radustavila.myblesense.domain.usecase.ConnectToDeviceUseCase
import com.radustavila.myblesense.domain.usecase.DisconnectFromDeviceUseCase
import com.radustavila.myblesense.domain.usecase.ObserveBluetoothStateUseCase
import com.radustavila.myblesense.domain.usecase.ObserveGattConnectionUseCase
import com.radustavila.myblesense.domain.usecase.ObserveGattErrorsUseCase
import com.radustavila.myblesense.domain.usecase.ObserveKnownDeviceUseCase
import com.radustavila.myblesense.domain.usecase.ObserveNotifyingCharacteristicsUseCase
import com.radustavila.myblesense.domain.usecase.ObservePocHardwareEventsUseCase
import com.radustavila.myblesense.domain.usecase.ObservePocServiceUseCase
import com.radustavila.myblesense.domain.usecase.ObserveScannedDeviceUseCase
import com.radustavila.myblesense.domain.usecase.ReadPocStatusUseCase
import com.radustavila.myblesense.domain.usecase.ReconnectToDeviceUseCase
import com.radustavila.myblesense.domain.usecase.SendPocCommandUseCase
import com.radustavila.myblesense.domain.usecase.SetPocEventsNotificationsUseCase
import com.radustavila.myblesense.feature.devicedetails.navigation.deviceDetailsDeviceId
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

/**
 * Streams a single device out of the current scan session — or, without a scan, the remembered
 * known device — and owns the *lifetime* of the GATT connection it opens to it.
 *
 * The connection itself lives in the app-scoped GATT client so that navigating to the inspector
 * (or rotating the phone) keeps it alive. This ViewModel decides when it ends: it is cleared when
 * the details screen is popped off the back stack — i.e. when the user leaves the connection
 * flow — and closes the link there. A configuration change does not clear a ViewModel, so the
 * connection survives exactly the events it should. A link this screen did not open (the device
 * list connected to the known device) is left open for its owner; only the POC Events
 * subscription this screen made is switched off.
 *
 * **Connection state.** What the screen shows is derived from the client's connection flow, the
 * adapter state and the reconnection in progress, so it always reflects the actual link — also
 * when the screen is re-entered while a link is already up.
 *
 * **Reconnection.** When a link the user opened from this screen is lost unexpectedly (out of
 * range, peripheral reset), a bounded reconnection is started through [ReconnectToDeviceUseCase].
 * It is cancelled by an explicit disconnect, by Bluetooth being switched off and by leaving the
 * screen (it runs in `viewModelScope`). A user-requested disconnect — from here or from the
 * inspector — never triggers it: the client reports only *unexpected* losses as errors.
 *
 * When the connected device turns out to be the ESP32 POC peripheral, its Events characteristic
 * is subscribed to and its Status characteristic read as soon as the service is discovered —
 * again after every reconnection, since a new link starts with a fresh GATT database. Commands
 * are only ever written on an explicit user action.
 *
 * Touch events arrive already paired with their duration and intensity statistics from
 * [ObservePocHardwareEventsUseCase]; this ViewModel only keeps the latest [MAX_HARDWARE_EVENTS] of
 * them, and the live intensity, for the screen.
 */
@HiltViewModel
class DeviceDetailsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeScannedDevice: ObserveScannedDeviceUseCase,
    observeKnownDevice: ObserveKnownDeviceUseCase,
    observeGattConnection: ObserveGattConnectionUseCase,
    observeGattErrors: ObserveGattErrorsUseCase,
    observePocService: ObservePocServiceUseCase,
    observeNotifyingCharacteristics: ObserveNotifyingCharacteristicsUseCase,
    observePocHardwareEvents: ObservePocHardwareEventsUseCase,
    observeBluetoothState: ObserveBluetoothStateUseCase,
    private val connectToDevice: ConnectToDeviceUseCase,
    private val reconnectToDevice: ReconnectToDeviceUseCase,
    private val disconnectFromDevice: DisconnectFromDeviceUseCase,
    private val readPocStatus: ReadPocStatusUseCase,
    private val sendPocCommand: SendPocCommandUseCase,
    private val setPocEventsNotifications: SetPocEventsNotificationsUseCase,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val deviceId: String = savedStateHandle.deviceDetailsDeviceId()

    private val _uiState = MutableStateFlow(DeviceDetailsUiState())
    val uiState: StateFlow<DeviceDetailsUiState> = _uiState.asStateFlow()

    private val gattConnection: StateFlow<GattConnection> = observeGattConnection()

    /** The user tapped Connect and the client has not reported the attempt yet. */
    private val isConnectRequested = MutableStateFlow(false)

    /** Automatic reconnection in progress; `null` when there is none. */
    private val reconnection = MutableStateFlow<ReconnectionStatus?>(null)

    /**
     * Whether the current (or last) link was opened to this device and not closed on purpose.
     * Only such a link is worth reconnecting when it is lost.
     */
    private var hasLinkToThisDevice = false

    /** The link was opened from this screen, so leaving the screen closes it. */
    private var closesLinkOnLeave = false

    private var connectJob: Job? = null
    private var reconnectJob: Job? = null
    private var readStatusJob: Job? = null
    private var sendCommandJob: Job? = null
    private var subscribeEventsJob: Job? = null

    private var hardwareEventSequence = 0L

    init {
        // The scan session has the richer view; the known device keeps the screen usable when
        // it was opened from the device list without a scan.
        combine(observeScannedDevice(deviceId), observeKnownDevice()) { scanned, known ->
            scanned?.toDetailsUi() ?: known?.takeIf { it.id == deviceId }?.toDetailsUi()
        }
            .onEach { device -> _uiState.update { it.copy(isLoading = false, device = device) } }
            .launchIn(viewModelScope)

        gattConnection
            .onEach(::trackLink)
            .launchIn(viewModelScope)

        combine(
            gattConnection,
            isConnectRequested,
            reconnection,
            observeBluetoothState().onStart { emit(BluetoothState.UNKNOWN) },
        ) { connection, requested, reconnecting, bluetooth ->
            ConnectionSnapshot(
                status = connection.toConnectionStatus(deviceId, requested, reconnecting),
                isDiscoveringServices = connection.isDiscoveringServices(deviceId),
                reconnection = reconnecting?.toUiOrNull(),
                // UNKNOWN (no adapter reported yet) is not treated as "off": the connect call
                // itself reports a disabled adapter if that is what it is.
                isBluetoothEnabled = bluetooth != BluetoothState.DISABLED &&
                    bluetooth != BluetoothState.TURNING_OFF,
            )
        }
            .distinctUntilChanged()
            .onEach(::onConnectionSnapshot)
            .launchIn(viewModelScope)

        observeGattErrors()
            .onEach(::onGattError)
            .launchIn(viewModelScope)

        // The single GATT connection may belong to another device, so the POC service only
        // counts when it was discovered on *this* one.
        combine(
            gattConnection,
            observePocService(),
            observeNotifyingCharacteristics(),
        ) { connection, poc, notifying ->
            poc?.takeIf { connection.isConnectedTo(deviceId) }
                ?.let { PocSnapshot(it, isEventsNotifying = it.events?.id in notifying) }
        }
            .distinctUntilChanged()
            .onEach(::onPocServiceChanged)
            .launchIn(viewModelScope)

        observePocHardwareEvents()
            .onEach(::onHardwareEvent)
            .launchIn(viewModelScope)
    }

    fun onEvent(event: DeviceDetailsEvent) {
        when (event) {
            DeviceDetailsEvent.Connect -> connect()
            DeviceDetailsEvent.Disconnect -> disconnect()
            DeviceDetailsEvent.ReadPocStatus -> readStatus()
            DeviceDetailsEvent.SendPocPing -> sendCommand(PocCommand.PING)
            DeviceDetailsEvent.SubscribePocEvents -> subscribeEvents()
            DeviceDetailsEvent.DismissMessage -> updateConnection { it.copy(message = null) }
        }
    }

    // --- Connection -----------------------------------------------------------------------

    private fun connect() {
        if (!_uiState.value.connection.canConnect) return

        isConnectRequested.value = true
        closesLinkOnLeave = true
        updateConnection { it.copy(message = null, problem = null) }
        connectJob = viewModelScope.launch {
            connectToDevice(deviceId).onFailure { failure ->
                val message = failure.toBleError().toUserMessage()
                showMessage(message)
                updateConnection { it.copy(problem = message) }
            }
            // The connection state itself comes from the client's flow.
            isConnectRequested.value = false
        }
    }

    private fun disconnect() {
        if (!_uiState.value.connection.canDisconnect) return

        // Closing on purpose: the loss that follows must not be "recovered".
        hasLinkToThisDevice = false
        closesLinkOnLeave = false
        stopReconnecting()
        updateConnection { it.copy(problem = null) }
        viewModelScope.launch {
            unsubscribeEvents()
            disconnectFromDevice().onFailure { showMessage(it.toBleError().toUserMessage()) }
        }
    }

    private fun trackLink(connection: GattConnection) {
        when {
            connection.isConnectedTo(deviceId) -> {
                hasLinkToThisDevice = true
                updateConnection { it.copy(problem = null) }
            }
            // Another device now owns the single connection, or the link is being closed on
            // purpose (possibly from the inspector): nothing to recover here.
            connection.state == GattConnectionState.DISCONNECTING ||
                (connection.deviceId != null && connection.deviceId != deviceId) -> {
                hasLinkToThisDevice = false
                closesLinkOnLeave = false
            }
        }
    }

    private fun onConnectionSnapshot(snapshot: ConnectionSnapshot) {
        updateConnection {
            it.copy(
                status = snapshot.status,
                isDiscoveringServices = snapshot.isDiscoveringServices,
                reconnection = snapshot.reconnection,
                isBluetoothEnabled = snapshot.isBluetoothEnabled,
            )
        }
        if (!snapshot.isBluetoothEnabled && reconnectJob?.isActive == true) {
            // Every further attempt would fail the same way; the adapter state says why.
            stopReconnecting()
            updateConnection { it.copy(problem = BleError.BluetoothDisabled.toUserMessage()) }
        }
    }

    /**
     * Failures nobody asked for. The client reports a link loss here and never a disconnect the
     * user requested, which is what makes this the trigger for a reconnection.
     */
    private fun onGattError(error: BleError) {
        // Attempts fail loudly on their own; their progress is reported by the reconnection.
        if (reconnectJob?.isActive == true) return

        val message = error.toUserMessage()
        showMessage(message)

        val wasOurLink = hasLinkToThisDevice
        hasLinkToThisDevice = false
        if (!wasOurLink) return

        updateConnection { it.copy(problem = message) }
        if (error.isRecoverableLinkLoss()) startReconnecting()
    }

    private fun startReconnecting() {
        reconnectJob = reconnectToDevice(deviceId)
            .onEach { status ->
                when (status) {
                    is ReconnectionStatus.Waiting, is ReconnectionStatus.Connecting ->
                        reconnection.value = status

                    is ReconnectionStatus.Reconnected -> reconnection.value = null

                    is ReconnectionStatus.GaveUp -> {
                        reconnection.value = null
                        val message = "Could not reconnect: ${status.lastError.toUserMessage()}"
                        showMessage(message)
                        updateConnection { it.copy(problem = message) }
                    }
                }
            }
            .launchIn(viewModelScope)
    }

    /** Cancels the pending delay or connection attempt; the client releases what it opened. */
    private fun stopReconnecting() {
        reconnectJob?.cancel()
        reconnectJob = null
        reconnection.value = null
    }

    // --- ESP32 POC service ----------------------------------------------------------------

    private fun onPocServiceChanged(snapshot: PocSnapshot?) {
        if (snapshot == null) {
            // Disconnected (or never the POC device): nothing stale stays on screen. The
            // hardware events history is kept on purpose, it describes what already happened.
            readStatusJob?.cancel()
            sendCommandJob?.cancel()
            subscribeEventsJob?.cancel()
            _uiState.update {
                it.copy(
                    poc = null,
                    // Whether the sensor is still held cannot be known without the link.
                    hardwareEvents = it.hardwareEvents.endActiveTouch(),
                )
            }
            return
        }
        val isNewlyDiscovered = _uiState.value.poc == null
        _uiState.update {
            it.copy(
                poc = snapshot.service.toUi(
                    previous = it.poc,
                    isEventsNotifying = snapshot.isEventsNotifying,
                ),
            )
        }
        if (isNewlyDiscovered) {
            // Subscribing first: the firmware pushes events on its own, and the CCCD write and
            // the status read are serialized by the GATT queue anyway. This also restores the
            // subscription after a reconnection.
            subscribeEvents()
            readStatus()
        }
    }

    private fun onHardwareEvent(event: HardwareEvent) {
        // Only while the POC device is connected here: a late notification is dropped.
        if (_uiState.value.poc == null) return
        _uiState.update {
            it.copy(hardwareEvents = it.hardwareEvents.plus(event, ++hardwareEventSequence))
        }
    }

    private fun subscribeEvents() {
        val poc = _uiState.value.poc ?: return
        if (!poc.isSubscribeEventsEnabled) return

        updatePoc { it.copy(isSubscribingEvents = true, eventsError = null) }
        subscribeEventsJob = viewModelScope.launch {
            val result = setPocEventsNotifications(enabled = true)
            // A disconnect during the CCCD write already cleared the section and reported it.
            if (_uiState.value.poc == null) return@launch
            // On success the "notifying" flag itself comes from the client's flow.
            val error = result.exceptionOrNull()?.let {
                "Could not subscribe to events: ${it.toBleError().toUserMessage()}"
            }
            updatePoc { it.copy(isSubscribingEvents = false, eventsError = error) }
        }
    }

    /**
     * Switches the Events notifications off before the link is closed, so the peripheral stops
     * pushing even if it keeps the CCCD value across connections. Best effort and bounded: a
     * closing link must not wait on the GATT queue's full timeout, and the disconnect that follows
     * ends the subscription regardless.
     */
    private suspend fun unsubscribeEvents() {
        subscribeEventsJob?.cancel()
        if (_uiState.value.poc?.isEventsNotifying != true) return
        withTimeoutOrNull(UNSUBSCRIBE_TIMEOUT) { setPocEventsNotifications(enabled = false) }
    }

    private fun readStatus() {
        val poc = _uiState.value.poc ?: return
        if (!poc.isReadStatusEnabled) return

        updatePoc { it.copy(isReadingStatus = true) }
        readStatusJob = viewModelScope.launch {
            val result = readPocStatus()
            // A disconnect during the read already cleared the section and reported the error.
            if (_uiState.value.poc == null) return@launch
            result
                .onSuccess { status -> updatePoc { it.copy(status = status) } }
                .onFailure { showMessage(it.toBleError().toUserMessage()) }
            updatePoc { it.copy(isReadingStatus = false) }
        }
    }

    private fun sendCommand(command: PocCommand) {
        val poc = _uiState.value.poc ?: return
        // Also guards against a double tap: the second write would only queue behind the first.
        if (!poc.isSendPingEnabled) return

        updatePoc { it.copy(commandState = PocCommandState.Sending) }
        sendCommandJob = viewModelScope.launch {
            val result = sendPocCommand(command)
            // A disconnect during the write already cleared the section and reported the error.
            if (_uiState.value.poc == null) return@launch
            val outcome = result.fold(
                onSuccess = { PocCommandState.Sent },
                onFailure = {
                    PocCommandState.Failed(
                        "Could not send command: ${it.toBleError().toUserMessage()}",
                    )
                },
            )
            updatePoc { it.copy(commandState = outcome) }
        }
    }

    private fun updatePoc(block: (PocServiceUiState) -> PocServiceUiState) {
        _uiState.update { state -> state.copy(poc = state.poc?.let(block)) }
    }

    private fun showMessage(message: String) = updateConnection { it.copy(message = message) }

    private fun updateConnection(block: (ConnectionUiState) -> ConnectionUiState) {
        _uiState.update { it.copy(connection = block(it.connection)) }
    }

    /**
     * Leaving the details screen ends the connection flow, so the link it opened is closed here.
     *
     * `viewModelScope` is already cancelled at this point — which also cancels a reconnection or
     * a connection attempt in progress — hence the application scope: the teardown is short, must
     * not be cancelled halfway, and releasing a `BluetoothGatt` is exactly the kind of work that
     * must complete.
     */
    override fun onCleared() {
        super.onCleared()
        releaseConnection()
    }

    /** The teardown itself, kept separate so it can be exercised without a ViewModel host. */
    internal fun releaseConnection() {
        hasLinkToThisDevice = false
        stopReconnecting()
        connectJob?.cancel()
        // The client's state, not the screen's: it is the one that knows whether a link is open.
        val connection = gattConnection.value
        val isLinked = connection.deviceId == deviceId &&
            connection.state != GattConnectionState.DISCONNECTED
        if (!isLinked) return
        // Someone else's link (the device list's) stays open, without this screen's subscription.
        val closesLink = closesLinkOnLeave
        closesLinkOnLeave = false
        applicationScope.launch {
            unsubscribeEvents()
            if (closesLink) disconnectFromDevice()
        }
    }
}

/** The POC service of this device together with its Events subscription state. */
private data class PocSnapshot(
    val service: PocService,
    val isEventsNotifying: Boolean,
)

/** Everything the connection part of the screen is derived from. */
private data class ConnectionSnapshot(
    val status: ConnectionStatus,
    val isDiscoveringServices: Boolean,
    val reconnection: ReconnectionUi?,
    val isBluetoothEnabled: Boolean,
)

private val UNSUBSCRIBE_TIMEOUT = 2.seconds
