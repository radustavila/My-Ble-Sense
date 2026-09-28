package com.radustavila.bleexplorer.core.bluetooth.scanner

import android.annotation.SuppressLint
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import com.radustavila.bleexplorer.core.bluetooth.mapper.ScanResultMapper
import com.radustavila.bleexplorer.core.bluetooth.mapper.scanErrorCodeToBleError
import com.radustavila.bleexplorer.core.bluetooth.permission.BlePermissionChecker
import com.radustavila.bleexplorer.core.bluetooth.state.AndroidBluetoothStateMonitor
import com.radustavila.bleexplorer.core.common.log.BleLog
import com.radustavila.bleexplorer.domain.model.BleDevice
import com.radustavila.bleexplorer.domain.model.BleError
import com.radustavila.bleexplorer.domain.model.BleException
import com.radustavila.bleexplorer.domain.model.BleScanMode
import com.radustavila.bleexplorer.domain.model.BleScanSettings
import com.radustavila.bleexplorer.domain.repository.BleScanner
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [BleScanner] on top of [BluetoothLeScanner].
 *
 * The callback based platform API is bridged to a cold [Flow] with `callbackFlow`: starting the
 * scan happens on collection, and `awaitClose` stops it whenever the collector is cancelled
 * (user pressed stop, timeout elapsed, screen went away, adapter turned off). That is what makes
 * a leaked `ScanCallback` — the classic BLE bug — structurally impossible here.
 */
@Singleton
class AndroidBleScanner @Inject constructor(
    private val stateMonitor: AndroidBluetoothStateMonitor,
    private val permissionChecker: BlePermissionChecker,
    private val mapper: ScanResultMapper,
) : BleScanner {

    @SuppressLint("MissingPermission") // Guarded by permissionChecker right above the call.
    override fun scan(settings: BleScanSettings): Flow<BleDevice> = callbackFlow {
        if (!permissionChecker.hasScanPermissions()) {
            throw BleException(BleError.PermissionDenied)
        }
        val scanner: BluetoothLeScanner = stateMonitor.adapter?.bluetoothLeScanner
            ?: throw BleException(BleError.ScannerUnavailable)

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result ?: return
                trySend(mapper.map(result, System.currentTimeMillis()))
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                val now = System.currentTimeMillis()
                results?.forEach { trySend(mapper.map(it, now)) }
            }

            override fun onScanFailed(errorCode: Int) {
                val error = scanErrorCodeToBleError(errorCode)
                BleLog.w("BLE scan failed: $error")
                close(BleException(error))
            }
        }

        try {
            scanner.startScan(settings.toScanFilters(), settings.toScanSettings(), callback)
            BleLog.i("BLE scan started (duration=${settings.duration}, mode=${settings.mode})")
        } catch (e: SecurityException) {
            throw BleException(BleError.PermissionDenied, e)
        } catch (e: IllegalStateException) {
            // Thrown when the adapter is turned off between the check and the call.
            throw BleException(BleError.ScannerUnavailable, e)
        }

        awaitClose {
            try {
                scanner.stopScan(callback)
                BleLog.i("BLE scan stopped")
            } catch (e: SecurityException) {
                BleLog.w("Could not stop BLE scan: permission revoked", e)
            } catch (e: IllegalStateException) {
                BleLog.w("Could not stop BLE scan: adapter unavailable", e)
            }
        }
    }.buffer(SCAN_BUFFER_CAPACITY) // advertisements arrive faster than the UI consumes them

    private fun BleScanSettings.toScanSettings(): ScanSettings = ScanSettings.Builder()
        .setScanMode(
            when (mode) {
                BleScanMode.LOW_POWER -> ScanSettings.SCAN_MODE_LOW_POWER
                BleScanMode.BALANCED -> ScanSettings.SCAN_MODE_BALANCED
                BleScanMode.LOW_LATENCY -> ScanSettings.SCAN_MODE_LOW_LATENCY
            },
        )
        // Report every advertisement so RSSI keeps refreshing; de-duplication is our job.
        .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        .setReportDelay(0L)
        .build()

    private fun BleScanSettings.toScanFilters(): List<ScanFilter> =
        serviceUuidFilters.map { uuid ->
            ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(uuid)).build()
        }

    private companion object {
        const val SCAN_BUFFER_CAPACITY = 128
    }
}
