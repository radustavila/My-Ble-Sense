package com.radustavila.myblesense.feature.scanner

import com.google.common.truth.Truth.assertThat
import com.radustavila.myblesense.domain.model.BleCapability
import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.BlePermissionState
import com.radustavila.myblesense.domain.model.BleScanSettings
import com.radustavila.myblesense.domain.model.BluetoothState
import com.radustavila.myblesense.domain.model.DeviceSortOrder
import com.radustavila.myblesense.domain.model.GattConnectionState
import com.radustavila.myblesense.domain.model.KnownDevice
import com.radustavila.myblesense.domain.session.InMemoryScanSessionStore
import com.radustavila.myblesense.domain.testing.FakeBleGattRepository
import com.radustavila.myblesense.domain.testing.FakeBleScanRepository
import com.radustavila.myblesense.domain.testing.FakeKnownDeviceRepository
import com.radustavila.myblesense.domain.testing.MainDispatcherRule
import com.radustavila.myblesense.domain.testing.bleDevice
import com.radustavila.myblesense.domain.usecase.ClearScanSessionUseCase
import com.radustavila.myblesense.domain.usecase.ConnectToDeviceUseCase
import com.radustavila.myblesense.domain.usecase.DisconnectFromDeviceUseCase
import com.radustavila.myblesense.domain.usecase.ForgetKnownDeviceUseCase
import com.radustavila.myblesense.domain.usecase.GetBleCapabilityUseCase
import com.radustavila.myblesense.domain.usecase.ObserveBluetoothStateUseCase
import com.radustavila.myblesense.domain.usecase.ObserveGattConnectionUseCase
import com.radustavila.myblesense.domain.usecase.ObserveKnownDeviceUseCase
import com.radustavila.myblesense.domain.usecase.SortDevicesUseCase
import com.radustavila.myblesense.domain.usecase.StartBleScanUseCase
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.CoroutineScope
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

class ScannerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeBleScanRepository()
    private val sessionStore = InMemoryScanSessionStore()
    private val settings = BleScanSettings(duration = 15.seconds)
    private val gattRepository = FakeBleGattRepository()
    private val client = gattRepository.client

    /** No known device unless a test says otherwise: a first launch. */
    private var knownDevices = FakeKnownDeviceRepository()

    private fun viewModel(): ScannerViewModel = ScannerViewModel(
        startBleScan = StartBleScanUseCase(repository, sessionStore),
        observeBluetoothState = ObserveBluetoothStateUseCase(repository),
        getBleCapability = GetBleCapabilityUseCase(repository),
        clearScanSession = ClearScanSessionUseCase(sessionStore),
        sortDevices = SortDevicesUseCase(),
        scanSettings = settings,
        observeKnownDevice = ObserveKnownDeviceUseCase(knownDevices),
        observeGattConnection = ObserveGattConnectionUseCase(gattRepository),
        connectToDevice = ConnectToDeviceUseCase(gattRepository),
        disconnectFromDevice = DisconnectFromDeviceUseCase(gattRepository),
        forgetKnownDevice = ForgetKnownDeviceUseCase(gattRepository, knownDevices),
        // Unconfined, so the teardown the ViewModel launches on leaving runs immediately.
        applicationScope = CoroutineScope(mainDispatcherRule.testDispatcher),
    )

    private suspend fun ScannerViewModel.grantPermissions() {
        onEvent(ScannerEvent.PermissionsChecked(granted = true))
    }

    @Test
    fun `initial state is idle and ready once permissions are granted`() = runTest(mainDispatcherRule.testScheduler) {
        val viewModel = viewModel()

        assertThat(viewModel.uiState.value.isScanning).isFalse()
        assertThat(viewModel.uiState.value.devices).isEmpty()
        assertThat(viewModel.uiState.value.bluetoothState).isEqualTo(BluetoothState.ENABLED)
        assertThat(viewModel.uiState.value.canScan).isFalse() // permissions unknown yet

        viewModel.grantPermissions()

        assertThat(viewModel.uiState.value.canScan).isTrue()
        assertThat(viewModel.uiState.value.content).isEqualTo(ScannerContent.Empty)
    }

    @Test
    fun `start scan moves the screen into the scanning state`() = runTest(mainDispatcherRule.testScheduler) {
        val viewModel = viewModel().apply { grantPermissions() }

        viewModel.onEvent(ScannerEvent.StartScan)
        runCurrent()

        assertThat(viewModel.uiState.value.isScanning).isTrue()
        assertThat(viewModel.uiState.value.content).isEqualTo(ScannerContent.Scanning)
    }

    @Test
    fun `discovered devices appear in the state`() = runTest(mainDispatcherRule.testScheduler) {
        val viewModel = viewModel().apply { grantPermissions() }
        viewModel.onEvent(ScannerEvent.StartScan)
        runCurrent()

        repository.scanner.emit(bleDevice(id = "A", name = "Watch", rssi = -50))
        runCurrent()

        assertThat(viewModel.uiState.value.devices.map { it.id }).containsExactly("A")
        assertThat(viewModel.uiState.value.deviceCount).isEqualTo(1)
    }

    @Test
    fun `an existing device is updated instead of duplicated`() = runTest(mainDispatcherRule.testScheduler) {
        val viewModel = viewModel().apply { grantPermissions() }
        viewModel.onEvent(ScannerEvent.StartScan)
        runCurrent()

        repository.scanner.emit(bleDevice(id = "A", rssi = -50))
        repository.scanner.emit(bleDevice(id = "A", rssi = -88))
        runCurrent()

        assertThat(viewModel.uiState.value.devices).hasSize(1)
        assertThat(viewModel.uiState.value.devices.single().rssi).isEqualTo(-88)
    }

    @Test
    fun `stop scan cancels the platform scan`() = runTest(mainDispatcherRule.testScheduler) {
        val viewModel = viewModel().apply { grantPermissions() }
        viewModel.onEvent(ScannerEvent.StartScan)
        runCurrent()

        viewModel.onEvent(ScannerEvent.StopScan)
        runCurrent()

        assertThat(viewModel.uiState.value.isScanning).isFalse()
        assertThat(repository.scanner.isScanning).isFalse()
    }

    @Test
    fun `scanning stops on its own when the timeout elapses`() = runTest(mainDispatcherRule.testScheduler) {
        val viewModel = viewModel().apply { grantPermissions() }
        viewModel.onEvent(ScannerEvent.StartScan)
        runCurrent()
        repository.scanner.emit(bleDevice(id = "A"))
        runCurrent()

        advanceTimeBy(settings.duration)
        runCurrent()

        assertThat(viewModel.uiState.value.isScanning).isFalse()
        assertThat(repository.scanner.isScanning).isFalse()
        // Results of the finished scan stay on screen.
        assertThat(viewModel.uiState.value.devices).hasSize(1)
    }

    @Test
    fun `devices keep the selected sort order while scanning`() = runTest(mainDispatcherRule.testScheduler) {
        val viewModel = viewModel().apply { grantPermissions() }
        viewModel.onEvent(ScannerEvent.SortOrderSelected(DeviceSortOrder.NAME))
        viewModel.onEvent(ScannerEvent.StartScan)
        runCurrent()

        repository.scanner.emit(bleDevice(id = "A", name = "Zeta", rssi = -40))
        repository.scanner.emit(bleDevice(id = "B", name = "Alpha", rssi = -90))
        runCurrent()

        assertThat(viewModel.uiState.value.devices.map { it.id })
            .containsExactly("B", "A").inOrder()
    }

    @Test
    fun `sorting an existing list re-orders it without a rescan`() = runTest(mainDispatcherRule.testScheduler) {
        val viewModel = viewModel().apply { grantPermissions() }
        viewModel.onEvent(ScannerEvent.StartScan)
        runCurrent()
        repository.scanner.emit(bleDevice(id = "A", name = "Zeta", rssi = -40))
        repository.scanner.emit(bleDevice(id = "B", name = "Alpha", rssi = -90))
        runCurrent()

        viewModel.onEvent(ScannerEvent.SortOrderSelected(DeviceSortOrder.NAME))

        assertThat(viewModel.uiState.value.devices.map { it.id })
            .containsExactly("B", "A").inOrder()
        assertThat(repository.scanner.scanStartedCount).isEqualTo(1)
    }

    @Test
    fun `turning Bluetooth off during a scan stops it and reports the reason`() = runTest(mainDispatcherRule.testScheduler) {
        val viewModel = viewModel().apply { grantPermissions() }
        viewModel.onEvent(ScannerEvent.StartScan)
        runCurrent()

        repository.bluetoothState.value = BluetoothState.DISABLED
        runCurrent()

        assertThat(viewModel.uiState.value.isScanning).isFalse()
        assertThat(viewModel.uiState.value.error)
            .isEqualTo(BleError.BluetoothTurnedOffDuringScan)
        assertThat(viewModel.uiState.value.content).isEqualTo(ScannerContent.BluetoothDisabled)
    }

    @Test
    fun `Bluetooth coming back does not restart scanning on its own`() = runTest(mainDispatcherRule.testScheduler) {
        val viewModel = viewModel().apply { grantPermissions() }
        viewModel.onEvent(ScannerEvent.StartScan)
        runCurrent()
        repository.bluetoothState.value = BluetoothState.DISABLED
        runCurrent()

        repository.bluetoothState.value = BluetoothState.ENABLED
        runCurrent()

        assertThat(viewModel.uiState.value.isScanning).isFalse()
        assertThat(repository.scanner.scanStartedCount).isEqualTo(1)
    }

    @Test
    fun `starting without permissions reports permission denied instead of scanning`() = runTest(mainDispatcherRule.testScheduler) {
        val viewModel = viewModel()
        viewModel.onEvent(ScannerEvent.PermissionsChecked(granted = false))

        viewModel.onEvent(ScannerEvent.StartScan)
        runCurrent()

        assertThat(viewModel.uiState.value.permissionState).isEqualTo(BlePermissionState.DENIED)
        assertThat(viewModel.uiState.value.error).isEqualTo(BleError.PermissionDenied)
        assertThat(repository.scanner.scanStartedCount).isEqualTo(0)
    }

    @Test
    fun `a missing permission that was never requested is not treated as permanent`() = runTest(mainDispatcherRule.testScheduler) {
        val viewModel = viewModel()

        viewModel.onEvent(ScannerEvent.PermissionsChecked(granted = false))

        assertThat(viewModel.uiState.value.permissionState).isEqualTo(BlePermissionState.DENIED)
    }

    @Test
    fun `a permanent denial survives a later permission re-check`() = runTest(mainDispatcherRule.testScheduler) {
        val viewModel = viewModel()
        viewModel.onEvent(
            ScannerEvent.PermissionRequestCompleted(granted = false, shouldShowRationale = false),
        )

        viewModel.onEvent(ScannerEvent.PermissionsChecked(granted = false))

        assertThat(viewModel.uiState.value.permissionState)
            .isEqualTo(BlePermissionState.PERMANENTLY_DENIED)
    }

    @Test
    fun `revoking permissions while scanning stops the scan`() = runTest(mainDispatcherRule.testScheduler) {
        val viewModel = viewModel().apply { grantPermissions() }
        viewModel.onEvent(ScannerEvent.StartScan)
        runCurrent()

        viewModel.onEvent(ScannerEvent.PermissionsChecked(granted = false))
        runCurrent()

        assertThat(viewModel.uiState.value.isScanning).isFalse()
        assertThat(repository.scanner.isScanning).isFalse()
    }

    @Test
    fun `a denial without rationale is treated as permanent`() = runTest(mainDispatcherRule.testScheduler) {
        val viewModel = viewModel()

        viewModel.onEvent(
            ScannerEvent.PermissionRequestCompleted(granted = false, shouldShowRationale = false),
        )

        assertThat(viewModel.uiState.value.permissionState)
            .isEqualTo(BlePermissionState.PERMANENTLY_DENIED)
        assertThat(viewModel.uiState.value.content)
            .isEqualTo(ScannerContent.PermissionRequired)
    }

    @Test
    fun `unsupported hardware is reported without touching the scanner`() = runTest(mainDispatcherRule.testScheduler) {
        repository.capability = BleCapability.Unsupported
        val viewModel = viewModel().apply { grantPermissions() }

        viewModel.onEvent(ScannerEvent.StartScan)
        runCurrent()

        assertThat(viewModel.uiState.value.content).isEqualTo(ScannerContent.BleUnsupported)
        assertThat(viewModel.uiState.value.error).isEqualTo(BleError.BleUnsupported)
        assertThat(repository.scanner.scanStartedCount).isEqualTo(0)
    }

    @Test
    fun `scanner failures surface as errors and leave the scanning state`() = runTest(mainDispatcherRule.testScheduler) {
        repository.scanner.failWith = BleError.ScanFailed(code = 2)
        val viewModel = viewModel().apply { grantPermissions() }

        viewModel.onEvent(ScannerEvent.StartScan)
        runCurrent()

        assertThat(viewModel.uiState.value.error).isEqualTo(BleError.ScanFailed(code = 2))
        assertThat(viewModel.uiState.value.isScanning).isFalse()
    }

    @Test
    fun `retrying after a failure starts a new scan`() = runTest(mainDispatcherRule.testScheduler) {
        repository.scanner.failWith = BleError.ScanFailed(code = 2)
        val viewModel = viewModel().apply { grantPermissions() }
        viewModel.onEvent(ScannerEvent.StartScan)
        runCurrent()
        viewModel.onEvent(ScannerEvent.DismissError)

        repository.scanner.failWith = null
        viewModel.onEvent(ScannerEvent.StartScan)
        runCurrent()
        repository.scanner.emit(bleDevice(id = "A"))
        runCurrent()

        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(viewModel.uiState.value.isScanning).isTrue()
        assertThat(viewModel.uiState.value.devices).hasSize(1)
    }

    @Test
    fun `a new scan clears the results of the previous one`() = runTest(mainDispatcherRule.testScheduler) {
        val viewModel = viewModel().apply { grantPermissions() }
        viewModel.onEvent(ScannerEvent.StartScan)
        runCurrent()
        repository.scanner.emit(bleDevice(id = "A"))
        runCurrent()
        viewModel.onEvent(ScannerEvent.StopScan)

        viewModel.onEvent(ScannerEvent.StartScan)
        runCurrent()

        assertThat(viewModel.uiState.value.devices).isEmpty()
        assertThat(sessionStore.devices.value).isEmpty()
    }

    // --- Known device ---------------------------------------------------------------------

    private fun rememberPocDevice() {
        knownDevices = FakeKnownDeviceRepository(KnownDevice(KNOWN_ID, "ESP32-BLE-POC"))
    }

    private val ScannerViewModel.known: KnownDeviceUi
        get() = checkNotNull(uiState.value.toKnownDeviceUi()) { "No known device shown" }

    @Test
    fun `the known device is shown at startup without a scan`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()

        val viewModel = viewModel()

        assertThat(viewModel.known.name).isEqualTo("ESP32-BLE-POC")
        assertThat(viewModel.known.status).isEqualTo(KnownDeviceStatus.DISCONNECTED)
        assertThat(viewModel.uiState.value.devices).isEmpty()
        assertThat(repository.scanner.scanStartedCount).isEqualTo(0)
    }

    @Test
    fun `nothing is shown before the first connection`() = runTest(mainDispatcherRule.testScheduler) {
        val viewModel = viewModel().apply { grantPermissions() }

        assertThat(viewModel.uiState.value.toKnownDeviceUi()).isNull()
        assertThat(client.connectCount).isEqualTo(0)
    }

    @Test
    fun `the known device is connected to automatically once Bluetooth is ready`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()
        client.connectDelayMillis = 500
        val viewModel = viewModel()
        assertThat(client.connectCount).isEqualTo(0) // permissions not checked yet

        viewModel.grantPermissions()
        assertThat(viewModel.known.status).isEqualTo(KnownDeviceStatus.CONNECTING)
        assertThat(viewModel.known.canCancel).isTrue()

        advanceTimeBy(600)
        assertThat(client.connectCount).isEqualTo(1)
        assertThat(viewModel.known.status).isEqualTo(KnownDeviceStatus.CONNECTED)
        assertThat(viewModel.known.statusLabel).isEqualTo("Connected")
        assertThat(viewModel.known.canDisconnect).isTrue()
    }

    @Test
    fun `an unreachable known device is tried once, quietly, and shown as unavailable`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()
        client.connectFailure = BleError.ConnectionTimeout
        val viewModel = viewModel()

        viewModel.grantPermissions()
        repository.bluetoothState.value = BluetoothState.DISABLED
        repository.bluetoothState.value = BluetoothState.ENABLED
        viewModel.grantPermissions()
        advanceTimeBy(60_000)

        assertThat(client.connectCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.error).isNull()
        assertThat(viewModel.known.status).isEqualTo(KnownDeviceStatus.UNAVAILABLE)
        assertThat(viewModel.known.hint).contains("out of range")
        assertThat(viewModel.known.canConnect).isTrue()
    }

    @Test
    fun `no automatic connection while Bluetooth is off, one once it is on`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()
        repository.bluetoothState.value = BluetoothState.DISABLED
        val viewModel = viewModel().apply { grantPermissions() }

        assertThat(client.connectCount).isEqualTo(0)
        assertThat(viewModel.known.status).isEqualTo(KnownDeviceStatus.BLUETOOTH_OFF)
        assertThat(viewModel.known.hint).isEqualTo("Turn Bluetooth on to connect.")
        assertThat(viewModel.known.canConnect).isFalse()

        repository.bluetoothState.value = BluetoothState.ENABLED

        assertThat(client.connectCount).isEqualTo(1)
        assertThat(viewModel.known.status).isEqualTo(KnownDeviceStatus.CONNECTED)
    }

    @Test
    fun `no automatic connection without the permission`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()
        val viewModel = viewModel()

        viewModel.onEvent(
            ScannerEvent.PermissionRequestCompleted(granted = false, shouldShowRationale = true),
        )

        assertThat(client.connectCount).isEqualTo(0)
        assertThat(viewModel.known.status).isEqualTo(KnownDeviceStatus.PERMISSION_REQUIRED)
        assertThat(viewModel.known.canConnect).isFalse()
    }

    @Test
    fun `connecting manually after a failure moves through connecting to connected`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()
        client.connectFailure = BleError.ConnectionTimeout
        val viewModel = viewModel().apply { grantPermissions() }
        client.connectFailure = null
        client.connectDelayMillis = 500

        viewModel.onEvent(ScannerEvent.ConnectKnownDevice)
        advanceTimeBy(100)
        assertThat(viewModel.known.status).isEqualTo(KnownDeviceStatus.CONNECTING)
        assertThat(viewModel.known.hint).isNull()

        advanceTimeBy(500)
        assertThat(client.connectCount).isEqualTo(2)
        assertThat(viewModel.known.status).isEqualTo(KnownDeviceStatus.CONNECTED)
    }

    @Test
    fun `a failed manual connection is reported`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()
        client.connectFailure = BleError.ConnectionFailed(status = 133)
        val viewModel = viewModel().apply { grantPermissions() }

        viewModel.onEvent(ScannerEvent.ConnectKnownDevice)

        assertThat(viewModel.uiState.value.error).isEqualTo(BleError.ConnectionFailed(status = 133))
        assertThat(viewModel.known.status).isEqualTo(KnownDeviceStatus.UNAVAILABLE)
        assertThat(viewModel.known.hint).isEqualTo("Could not connect to the device.")
    }

    @Test
    fun `connecting with Bluetooth off is refused with the reason`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()
        repository.bluetoothState.value = BluetoothState.DISABLED
        val viewModel = viewModel().apply { grantPermissions() }

        viewModel.onEvent(ScannerEvent.ConnectKnownDevice)

        assertThat(client.connectCount).isEqualTo(0)
        assertThat(viewModel.uiState.value.error).isEqualTo(BleError.BluetoothDisabled)
    }

    @Test
    fun `a scan that finds the known device marks it available instead of listing it twice`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()
        client.connectFailure = BleError.ConnectionTimeout
        val viewModel = viewModel().apply { grantPermissions() }
        viewModel.onEvent(ScannerEvent.StartScan)
        runCurrent()

        repository.scanner.emitAll(
            bleDevice(id = KNOWN_ID, name = "ESP32-BLE-POC", rssi = -48),
            bleDevice(id = "B", name = "Venu Sq", rssi = -70),
        )
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(state.otherDevices.map { it.id }).containsExactly("B")
        assertThat(state.deviceCount).isEqualTo(2)
        assertThat(state.content).isEqualTo(ScannerContent.Devices)
        assertThat(viewModel.known.status).isEqualTo(KnownDeviceStatus.AVAILABLE)
        assertThat(viewModel.known.statusLabel).isEqualTo("Available")
        assertThat(viewModel.known.rssi).isEqualTo(-48)
        assertThat(viewModel.known.canConnect).isTrue()
    }

    @Test
    fun `a scan that only finds the known device shows no other devices`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()
        client.connectFailure = BleError.ConnectionTimeout
        val viewModel = viewModel().apply { grantPermissions() }
        viewModel.onEvent(ScannerEvent.StartScan)
        runCurrent()

        repository.scanner.emit(bleDevice(id = KNOWN_ID, name = "ESP32-BLE-POC"))
        runCurrent()

        assertThat(viewModel.uiState.value.content).isEqualTo(ScannerContent.Scanning)
        assertThat(viewModel.uiState.value.isKnownDeviceSeen).isTrue()
    }

    @Test
    fun `a finished scan without the known device marks it unavailable`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()
        val viewModel = viewModel().apply { grantPermissions() }
        viewModel.onEvent(ScannerEvent.DisconnectKnownDevice)
        assertThat(viewModel.known.status).isEqualTo(KnownDeviceStatus.DISCONNECTED)

        viewModel.onEvent(ScannerEvent.StartScan)
        runCurrent()
        assertThat(viewModel.known.hint).isEqualTo("Looking for it nearby…")
        repository.scanner.emit(bleDevice(id = "B"))
        advanceTimeBy(16_000)

        assertThat(viewModel.uiState.value.isScanning).isFalse()
        assertThat(viewModel.known.status).isEqualTo(KnownDeviceStatus.UNAVAILABLE)
        assertThat(viewModel.known.hint).isEqualTo(NOT_FOUND_IN_LAST_SCAN)
    }

    @Test
    fun `disconnecting closes the link and it is never reopened on its own`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()
        val viewModel = viewModel().apply { grantPermissions() }

        viewModel.onEvent(ScannerEvent.DisconnectKnownDevice)
        viewModel.grantPermissions()
        advanceTimeBy(60_000)

        assertThat(client.disconnectCount).isEqualTo(1)
        assertThat(client.connectCount).isEqualTo(1)
        assertThat(viewModel.known.status).isEqualTo(KnownDeviceStatus.DISCONNECTED)
    }

    @Test
    fun `cancelling a connection in progress abandons it`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()
        client.connectDelayMillis = 10_000
        val viewModel = viewModel().apply { grantPermissions() }

        viewModel.onEvent(ScannerEvent.DisconnectKnownDevice)
        advanceTimeBy(60_000)

        assertThat(client.connection.value.isConnected).isFalse()
        assertThat(client.connectCount).isEqualTo(1)
        assertThat(viewModel.known.status).isEqualTo(KnownDeviceStatus.DISCONNECTED)
    }

    @Test
    fun `a lost link shows as disconnected and is not reopened here`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()
        val viewModel = viewModel().apply { grantPermissions() }

        client.simulateUnexpectedDisconnect(BleError.Disconnected)
        advanceTimeBy(60_000)

        assertThat(client.connectCount).isEqualTo(1)
        assertThat(viewModel.known.status).isEqualTo(KnownDeviceStatus.DISCONNECTED)
        assertThat(viewModel.known.canConnect).isTrue()
    }

    @Test
    fun `a link opened elsewhere is shown but neither replaced nor closed`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()
        client.connect(KNOWN_ID)
        val viewModel = viewModel().apply { grantPermissions() }

        assertThat(viewModel.known.status).isEqualTo(KnownDeviceStatus.CONNECTED)
        viewModel.releaseConnection()

        assertThat(client.connectCount).isEqualTo(1)
        assertThat(client.disconnectCount).isEqualTo(0)
    }

    @Test
    fun `leaving the app closes the link opened here`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()
        val viewModel = viewModel().apply { grantPermissions() }

        viewModel.releaseConnection()

        assertThat(client.disconnectCount).isEqualTo(1)
    }

    @Test
    fun `a newly remembered device replaces the known device entry`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()
        val viewModel = viewModel()

        knownDevices.save(KnownDevice("11:22:33:44:55:66", "Venu Sq"))

        assertThat(viewModel.known.id).isEqualTo("11:22:33:44:55:66")
        assertThat(viewModel.known.name).isEqualTo("Venu Sq")
    }

    // --- Forget device --------------------------------------------------------------------

    @Test
    fun `forgetting a connected known device disconnects it, then removes it`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()
        val viewModel = viewModel().apply { grantPermissions() }
        assertThat(viewModel.known.status).isEqualTo(KnownDeviceStatus.CONNECTED)
        assertThat(viewModel.known.canForget).isTrue()

        viewModel.onEvent(ScannerEvent.ForgetKnownDevice)
        runCurrent()

        assertThat(client.disconnectCount).isEqualTo(1)
        assertThat(client.connection.value.state).isEqualTo(GattConnectionState.DISCONNECTED)
        assertThat(knownDevices.current).isNull()
        assertThat(viewModel.uiState.value.knownDevice).isNull()
        assertThat(viewModel.uiState.value.toKnownDeviceUi()).isNull()
        assertThat(viewModel.uiState.value.isForgettingKnownDevice).isFalse()
        assertThat(viewModel.uiState.value.error).isNull()
    }

    @Test
    fun `forgetting a disconnected known device only removes it`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()
        val viewModel = viewModel() // permissions unchecked: no automatic connection

        viewModel.onEvent(ScannerEvent.ForgetKnownDevice)
        runCurrent()

        assertThat(client.disconnectCount).isEqualTo(0)
        assertThat(knownDevices.clearCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.toKnownDeviceUi()).isNull()
    }

    @Test
    fun `forgetting while connecting abandons the attempt`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()
        client.connectDelayMillis = 10_000
        val viewModel = viewModel().apply { grantPermissions() }
        assertThat(viewModel.known.status).isEqualTo(KnownDeviceStatus.CONNECTING)

        viewModel.onEvent(ScannerEvent.ForgetKnownDevice)
        advanceTimeBy(60_000)

        assertThat(client.connection.value.isConnected).isFalse()
        assertThat(client.connectCount).isEqualTo(1)
        assertThat(knownDevices.current).isNull()
        assertThat(viewModel.uiState.value.isKnownDeviceConnectRequested).isFalse()
        assertThat(viewModel.uiState.value.toKnownDeviceUi()).isNull()
    }

    @Test
    fun `a forgotten device is listed again by a manual scan and never connected to on its own`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()
        val viewModel = viewModel().apply { grantPermissions() }
        viewModel.onEvent(ScannerEvent.ForgetKnownDevice)
        runCurrent()

        viewModel.grantPermissions()
        viewModel.onEvent(ScannerEvent.StartScan)
        runCurrent()
        repository.scanner.emit(bleDevice(id = KNOWN_ID, name = "ESP32-BLE-POC", rssi = -48))
        runCurrent()

        val state = viewModel.uiState.value
        assertThat(state.otherDevices.map { it.id }).containsExactly(KNOWN_ID)
        assertThat(state.content).isEqualTo(ScannerContent.Devices)
        assertThat(state.isKnownDeviceSeen).isFalse()
        assertThat(state.toKnownDeviceUi()).isNull()
        assertThat(client.connectCount).isEqualTo(1) // only the automatic one before forgetting
    }

    @Test
    fun `a storage failure keeps the known device and says why`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()
        knownDevices.clearFailure = IOException("disk full")
        val viewModel = viewModel()

        viewModel.onEvent(ScannerEvent.ForgetKnownDevice)
        runCurrent()

        assertThat(viewModel.uiState.value.error).isEqualTo(BleError.KnownDeviceNotForgotten)
        assertThat(viewModel.known.id).isEqualTo(KNOWN_ID)
        assertThat(viewModel.known.canForget).isTrue()
    }

    @Test
    fun `while forgetting the entry offers no action and a second tap is ignored`() = runTest(mainDispatcherRule.testScheduler) {
        rememberPocDevice()
        knownDevices.clearDelayMillis = 500
        val viewModel = viewModel().apply { grantPermissions() }

        viewModel.onEvent(ScannerEvent.ForgetKnownDevice)
        runCurrent()
        assertThat(viewModel.known.isForgetting).isTrue()
        assertThat(viewModel.known.canForget).isFalse()
        assertThat(viewModel.known.canConnect).isFalse()
        assertThat(viewModel.known.canDisconnect).isFalse()

        viewModel.onEvent(ScannerEvent.ForgetKnownDevice)
        advanceTimeBy(600)

        assertThat(knownDevices.clearCount).isEqualTo(1)
        assertThat(client.disconnectCount).isEqualTo(1)
        assertThat(viewModel.uiState.value.toKnownDeviceUi()).isNull()
    }
}

private const val KNOWN_ID = "AA:BB:CC:DD:EE:FF"
