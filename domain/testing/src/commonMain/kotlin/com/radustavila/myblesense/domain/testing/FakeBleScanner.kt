package com.radustavila.myblesense.domain.testing

import com.radustavila.myblesense.domain.model.BleCapability
import com.radustavila.myblesense.domain.model.BleDevice
import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.BleException
import com.radustavila.myblesense.domain.model.BleScanSettings
import com.radustavila.myblesense.domain.model.BluetoothState
import com.radustavila.myblesense.domain.repository.BleScanRepository
import com.radustavila.myblesense.domain.repository.BleScanner
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/**
 * Deterministic [BleScanner] used by unit tests: advertisements are pushed by the test instead of
 * coming from real hardware.
 */
class FakeBleScanner : BleScanner {
    private val advertisements = MutableSharedFlow<BleDevice>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    var scanStartedCount: Int = 0
        private set
    var scanStoppedCount: Int = 0
        private set
    var lastSettings: BleScanSettings? = null
        private set

    /** Error thrown as soon as the scan starts, when set. */
    var failWith: BleError? = null

    val isScanning: Boolean get() = scanStartedCount > scanStoppedCount

    override fun scan(settings: BleScanSettings): Flow<BleDevice> = flow {
        lastSettings = settings
        scanStartedCount++
        failWith?.let { throw BleException(it) }
        try {
            advertisements.collect { emit(it) }
        } finally {
            scanStoppedCount++
        }
    }

    /** Emits an advertisement to the (single) active collector. */
    suspend fun emit(device: BleDevice) {
        advertisements.emit(device)
    }

    suspend fun emitAll(vararg devices: BleDevice) {
        devices.forEach { emit(it) }
    }
}

/** [BleScanRepository] backed by [FakeBleScanner], with controllable adapter state. */
class FakeBleScanRepository(
    val scanner: FakeBleScanner = FakeBleScanner(),
    var capability: BleCapability = BleCapability(hasBluetooth = true, hasBluetoothLowEnergy = true),
) : BleScanRepository {
    val bluetoothState = MutableStateFlow(BluetoothState.ENABLED)

    override fun scan(settings: BleScanSettings): Flow<BleDevice> = flow {
        when {
            !capability.isSupported -> throw BleException(BleError.BleUnsupported)
            !bluetoothState.value.isEnabled -> throw BleException(BleError.BluetoothDisabled)
        }
        scanner.scan(settings).collect { emit(it) }
    }

    override fun capability(): BleCapability = capability

    override fun bluetoothState(): Flow<BluetoothState> = bluetoothState

    override fun currentBluetoothState(): BluetoothState = bluetoothState.value
}

/** Builds a device with sensible defaults so tests only state what matters to them. */
fun bleDevice(
    id: String = "AA:BB:CC:DD:EE:FF",
    name: String? = null,
    rssi: Int = -60,
    txPowerDbm: Int? = null,
    serviceUuids: List<String> = emptyList(),
    firstSeenAtMillis: Long = 0L,
    lastSeenAtMillis: Long = 0L,
): BleDevice = BleDevice(
    id = id,
    address = id,
    name = name,
    rssi = rssi,
    txPowerDbm = txPowerDbm,
    isConnectable = true,
    serviceUuids = serviceUuids,
    firstSeenAtMillis = firstSeenAtMillis,
    lastSeenAtMillis = lastSeenAtMillis,
)
