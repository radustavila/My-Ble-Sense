package com.radustavila.myblesense.core.bluetooth.repository

import com.radustavila.myblesense.core.bluetooth.permission.BlePermissionChecker
import com.radustavila.myblesense.core.common.di.IoDispatcher
import com.radustavila.myblesense.core.common.log.BleLog
import com.radustavila.myblesense.domain.model.BleCapability
import com.radustavila.myblesense.domain.model.BleDevice
import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.BleException
import com.radustavila.myblesense.domain.model.BleScanSettings
import com.radustavila.myblesense.domain.model.BluetoothState
import com.radustavila.myblesense.domain.repository.BleScanRepository
import com.radustavila.myblesense.domain.repository.BleScanner
import com.radustavila.myblesense.domain.repository.BluetoothStateMonitor
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Validates the preconditions of a scan, delegates to the platform scanner and normalises every
 * failure into a [BleException].
 *
 * It also watches the adapter: if Bluetooth is switched off mid-scan the flow fails with
 * [BleError.BluetoothTurnedOffDuringScan] instead of silently going quiet.
 */
@Singleton
class AndroidBleScanRepository @Inject constructor(
    private val scanner: BleScanner,
    private val stateMonitor: BluetoothStateMonitor,
    private val permissionChecker: BlePermissionChecker,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : BleScanRepository {

    override fun scan(settings: BleScanSettings): Flow<BleDevice> = flow {
        checkPreconditions()
        emitAll(merge(scanner.scan(settings), adapterTurnedOffSignal()))
    }
        .onEach { BleLog.d("BLE device discovered: ${it.id} rssi=${it.rssi}") }
        .catch { throwable ->
            throw when (throwable) {
                is BleException -> throwable
                is SecurityException -> BleException(BleError.PermissionDenied, throwable)
                else -> BleException(BleError.Unknown(throwable.message), throwable)
            }
        }
        .flowOn(ioDispatcher)

    /**
     * Never emits a device: it fails the scan as soon as the adapter leaves the enabled state,
     * which cancels the platform scan through the merged flow's structured concurrency.
     */
    private fun adapterTurnedOffSignal(): Flow<BleDevice> = stateMonitor.observeState()
        .filter { it == BluetoothState.DISABLED || it == BluetoothState.TURNING_OFF }
        .map { throw BleException(BleError.BluetoothTurnedOffDuringScan) }

    private fun checkPreconditions() {
        if (!capability().isSupported) throw BleException(BleError.BleUnsupported)
        if (!stateMonitor.currentState().isEnabled) throw BleException(BleError.BluetoothDisabled)
        if (!permissionChecker.hasScanPermissions()) throw BleException(BleError.PermissionDenied)
    }

    override fun capability(): BleCapability = stateMonitor.capability()

    override fun bluetoothState(): Flow<BluetoothState> = stateMonitor.observeState()

    override fun currentBluetoothState(): BluetoothState = stateMonitor.currentState()
}
