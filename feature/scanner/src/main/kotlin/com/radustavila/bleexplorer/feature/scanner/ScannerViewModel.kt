package com.radustavila.bleexplorer.feature.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.radustavila.bleexplorer.core.common.di.ApplicationScope
import com.radustavila.bleexplorer.domain.model.BleDevice
import com.radustavila.bleexplorer.domain.model.BleError
import com.radustavila.bleexplorer.domain.model.BlePermissionState
import com.radustavila.bleexplorer.domain.model.BleScanSettings
import com.radustavila.bleexplorer.domain.model.BluetoothState
import com.radustavila.bleexplorer.domain.model.DeviceSortOrder
import com.radustavila.bleexplorer.domain.model.GattConnection
import com.radustavila.bleexplorer.domain.model.GattConnectionState
import com.radustavila.bleexplorer.domain.model.KnownDevice
import com.radustavila.bleexplorer.domain.model.shouldAutoConnect
import com.radustavila.bleexplorer.domain.model.toBleError
import com.radustavila.bleexplorer.domain.usecase.ClearScanSessionUseCase
import com.radustavila.bleexplorer.domain.usecase.ConnectToDeviceUseCase
import com.radustavila.bleexplorer.domain.usecase.DisconnectFromDeviceUseCase
import com.radustavila.bleexplorer.domain.usecase.GetBleCapabilityUseCase
import com.radustavila.bleexplorer.domain.usecase.ObserveBluetoothStateUseCase
import com.radustavila.bleexplorer.domain.usecase.ObserveGattConnectionUseCase
import com.radustavila.bleexplorer.domain.usecase.ObserveKnownDeviceUseCase
import com.radustavila.bleexplorer.domain.usecase.SortDevicesUseCase
import com.radustavila.bleexplorer.domain.usecase.StartBleScanUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the scanner screen state. All BLE work goes through use cases; the ViewModel never touches
 * an Android Bluetooth API and never holds a scan callback.
 *
 * The scan runs in [viewModelScope], so it is cancelled with the screen (structured concurrency,
 * no `GlobalScope`).
 *
 * **Known device.** The device remembered from an earlier connection is shown as soon as it is
 * read, before any scan, with its state derived from the GATT client's connection (plus the
 * adapter, the permission and what the current scan saw). The list connects to it on its own once
 * per screen lifetime, when Bluetooth is ready and no link is open (see [shouldAutoConnect]); any
 * other attempt is the user's. There is no automatic reconnection here: a lost link just shows as
 * such, and the details screen keeps its own bounded reconnection for links it is showing.
 *
 * A link opened here outlives the details screen — which only closes the links *it* opened — and
 * is closed when this ViewModel is cleared, i.e. when the app's task goes away.
 */
@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val startBleScan: StartBleScanUseCase,
    private val observeBluetoothState: ObserveBluetoothStateUseCase,
    private val getBleCapability: GetBleCapabilityUseCase,
    private val clearScanSession: ClearScanSessionUseCase,
    private val sortDevices: SortDevicesUseCase,
    private val scanSettings: BleScanSettings,
    observeKnownDevice: ObserveKnownDeviceUseCase,
    observeGattConnection: ObserveGattConnectionUseCase,
    private val connectToDevice: ConnectToDeviceUseCase,
    private val disconnectFromDevice: DisconnectFromDeviceUseCase,
    @ApplicationScope private val applicationScope: CoroutineScope,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState(capability = getBleCapability()))
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private var scanJob: Job? = null
    private var connectJob: Job? = null

    private val gattConnection: StateFlow<GattConnection> = observeGattConnection()

    /** Set once the automatic connection ran, or the user connected or disconnected first. */
    private var hasAutoConnectDecision = false

    /** The current link to the known device was opened here and is closed from here. */
    private var ownsKnownDeviceLink = false

    init {
        observeBluetoothState()
            .onEach(::onBluetoothStateChanged)
            .launchIn(viewModelScope)

        observeKnownDevice()
            .onEach(::onKnownDeviceChanged)
            .launchIn(viewModelScope)

        gattConnection
            .onEach(::onConnectionChanged)
            .launchIn(viewModelScope)
    }

    fun onEvent(event: ScannerEvent) = when (event) {
        ScannerEvent.StartScan -> startScan()
        ScannerEvent.StopScan -> stopScan()
        ScannerEvent.DismissError -> _uiState.update { it.copy(error = null) }
        is ScannerEvent.SortOrderSelected -> onSortOrderSelected(event.order)
        ScannerEvent.ConnectKnownDevice -> {
            hasAutoConnectDecision = true
            connectKnownDevice(isAutomatic = false)
        }

        ScannerEvent.DisconnectKnownDevice -> disconnectKnownDevice()
        is ScannerEvent.PermissionsChecked -> onPermissionsChecked(event.granted)
        is ScannerEvent.PermissionRequestCompleted ->
            onPermissionRequestCompleted(event.granted, event.shouldShowRationale)
    }

    private fun startScan() {
        val state = _uiState.value
        when {
            !state.capability.isSupported -> return showError(BleError.BleUnsupported)
            !state.permissionState.isGranted -> return showError(BleError.PermissionDenied)
            !state.bluetoothState.isEnabled -> return showError(BleError.BluetoothDisabled)
            state.isScanning -> return
        }

        clearScanSession()
        _uiState.update {
            it.copy(devices = emptyList(), isScanning = true, hasScannedOnce = true, error = null)
        }

        scanJob = viewModelScope.launch {
            startBleScan(scanSettings)
                .onEach { devices -> publishDevices(devices) }
                .catch { throwable -> showError(throwable.toBleError()) }
                // Reached on the scan timeout, on stop and on error: one place to leave the
                // scanning state, so the UI can never get stuck on "Scanning...".
                .onCompletion { _uiState.update { it.copy(isScanning = false) } }
                .collect()
        }
    }

    private fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _uiState.update { it.copy(isScanning = false) }
    }

    private fun onSortOrderSelected(order: DeviceSortOrder) {
        _uiState.update { state ->
            state.copy(sortOrder = order, devices = sortDevices(state.devices, order))
        }
    }

    /**
     * Re-check on resume. A missing permission here is not necessarily a permanent denial — it
     * may simply never have been requested — so an earlier permanent denial is preserved and
     * nothing is escalated.
     */
    private fun onPermissionsChecked(granted: Boolean) {
        val state = _uiState.value
        val permissionState = when {
            granted -> BlePermissionState.GRANTED
            state.permissionState == BlePermissionState.PERMANENTLY_DENIED ->
                BlePermissionState.PERMANENTLY_DENIED

            else -> BlePermissionState.DENIED
        }
        applyPermissionState(permissionState, granted)
    }

    private fun onPermissionRequestCompleted(granted: Boolean, shouldShowRationale: Boolean) {
        val permissionState = when {
            granted -> BlePermissionState.GRANTED
            // Denied and the system will not show the dialog again: only Settings can fix it.
            !shouldShowRationale -> BlePermissionState.PERMANENTLY_DENIED
            else -> BlePermissionState.DENIED
        }
        applyPermissionState(permissionState, granted)
    }

    // --- Known device ---------------------------------------------------------------------

    private fun onKnownDeviceChanged(device: KnownDevice?) {
        val previous = _uiState.value.knownDevice
        _uiState.update {
            it.copy(
                knownDevice = device,
                // A problem reaching the previous device says nothing about a new one.
                knownDeviceProblem = it.knownDeviceProblem.takeIf { device?.id == previous?.id },
            )
        }
        maybeAutoConnect()
    }

    private fun onConnectionChanged(connection: GattConnection) {
        val knownId = _uiState.value.knownDevice?.id
        if (connection.deviceId != null && connection.deviceId != knownId) {
            // Another device now owns the single connection.
            ownsKnownDeviceLink = false
        }
        _uiState.update {
            it.copy(
                connection = connection,
                knownDeviceProblem = it.knownDeviceProblem
                    .takeUnless { connection.isConnectedTo(knownId.orEmpty()) },
            )
        }
        maybeAutoConnect()
    }

    /** Re-evaluated whenever something it depends on changes; it acts at most once. */
    private fun maybeAutoConnect() {
        val state = _uiState.value
        if (!shouldAutoConnect(
                knownDevice = state.knownDevice,
                isBluetoothReady = state.isBluetoothReady,
                connection = state.connection,
                hasAttempted = hasAutoConnectDecision,
            )
        ) {
            return
        }
        hasAutoConnectDecision = true
        connectKnownDevice(isAutomatic = true)
    }

    /**
     * An automatic attempt fails quietly — the entry turns "unavailable" and says why — while a
     * failure the user asked for is also reported as a message.
     */
    private fun connectKnownDevice(isAutomatic: Boolean) {
        val state = _uiState.value
        val known = state.knownDevice ?: return
        when {
            !state.capability.isSupported -> return showMessage(BleError.BleUnsupported)
            !state.permissionState.isGranted -> return showMessage(BleError.PermissionDenied)
            !state.bluetoothState.isEnabled -> return showMessage(BleError.BluetoothDisabled)
            state.isKnownDeviceConnectRequested -> return
            state.connection.deviceId == known.id &&
                state.connection.state != GattConnectionState.DISCONNECTED -> return
        }

        ownsKnownDeviceLink = true
        _uiState.update { it.copy(isKnownDeviceConnectRequested = true, knownDeviceProblem = null) }
        connectJob = viewModelScope.launch {
            val failure = connectToDevice(known.id).exceptionOrNull()?.toBleError()
            if (failure != null) ownsKnownDeviceLink = false
            _uiState.update {
                it.copy(
                    isKnownDeviceConnectRequested = false,
                    knownDeviceProblem = failure,
                    error = failure.takeUnless { isAutomatic } ?: it.error,
                )
            }
        }
    }

    private fun disconnectKnownDevice() {
        hasAutoConnectDecision = true
        ownsKnownDeviceLink = false
        // Cancelling an attempt in flight makes the client release what it had opened.
        connectJob?.cancel()
        connectJob = null
        _uiState.update { it.copy(isKnownDeviceConnectRequested = false) }
        viewModelScope.launch {
            disconnectFromDevice().onFailure { showMessage(it.toBleError()) }
        }
    }

    /**
     * The app's task is going away: a link this screen opened must not outlive it. The application
     * scope is used because `viewModelScope` is already cancelled here, like in the details screen.
     */
    override fun onCleared() {
        super.onCleared()
        releaseConnection()
    }

    /** The teardown itself, kept separate so it can be exercised without a ViewModel host. */
    internal fun releaseConnection() {
        connectJob?.cancel()
        val knownId = _uiState.value.knownDevice?.id ?: return
        val connection = gattConnection.value
        val isLinked = connection.deviceId == knownId &&
            connection.state != GattConnectionState.DISCONNECTED
        if (ownsKnownDeviceLink && isLinked) {
            ownsKnownDeviceLink = false
            applicationScope.launch { disconnectFromDevice() }
        }
    }

    private fun applyPermissionState(permissionState: BlePermissionState, granted: Boolean) {
        _uiState.update { state ->
            state.copy(
                permissionState = permissionState,
                error = state.error.takeUnless { granted && it == BleError.PermissionDenied },
            )
        }
        // Permissions revoked while a scan was running: stop immediately.
        if (!granted) stopScan()
        maybeAutoConnect()
    }

    private fun onBluetoothStateChanged(state: BluetoothState) {
        val wasEnabled = _uiState.value.bluetoothState.isEnabled
        _uiState.update { it.copy(bluetoothState = state) }

        // Bluetooth went away mid-scan: stop, report, and wait for the user to start again.
        // Scanning is never resumed automatically when it comes back.
        if (wasEnabled && !state.isEnabled && _uiState.value.isScanning) {
            stopScan()
            showError(BleError.BluetoothTurnedOffDuringScan)
        }
        maybeAutoConnect()
    }

    private fun publishDevices(devices: List<BleDevice>) {
        _uiState.update { state ->
            state.copy(devices = sortDevices(devices, state.sortOrder))
        }
    }

    private fun showError(error: BleError) {
        _uiState.update { it.copy(error = error, isScanning = false) }
    }

    /** Reports [error] without touching the scan, which may well keep running. */
    private fun showMessage(error: BleError) {
        _uiState.update { it.copy(error = error) }
    }
}
