package com.radustavila.myblesense.core.bluetooth.central

import com.radustavila.myblesense.core.bluetooth.mapper.toDomainState
import com.radustavila.myblesense.core.common.log.BleLog
import com.radustavila.myblesense.domain.model.BluetoothState
import kotlinx.cinterop.ObjCSignatureOverride
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.CoreBluetooth.CBCentralManager
import platform.CoreBluetooth.CBCentralManagerDelegateProtocol
import platform.CoreBluetooth.CBCentralManagerOptionShowPowerAlertKey
import platform.CoreBluetooth.CBManagerState
import platform.CoreBluetooth.CBPeripheral
import platform.Foundation.NSError
import platform.Foundation.NSNumber
import platform.Foundation.NSUUID
import platform.darwin.NSObject
import platform.darwin.dispatch_queue_create
import kotlin.concurrent.Volatile

/** Receives the advertisements of the scan in progress. Called on the CoreBluetooth queue. */
internal fun interface IosScanListener {
    fun onDiscovered(peripheral: CBPeripheral, advertisementData: Map<Any?, *>, rssi: Int)
}

/** Receives link level events of every peripheral. Called on the CoreBluetooth queue. */
internal interface IosConnectionListener {
    fun onConnected(peripheral: CBPeripheral)

    fun onFailedToConnect(peripheral: CBPeripheral, error: NSError?)

    fun onDisconnected(peripheral: CBPeripheral, error: NSError?)
}

/**
 * The app's single `CBCentralManager`, shared by the state monitor, the scanner and the GATT
 * client — the CoreBluetooth counterpart of Android's `BluetoothAdapter`.
 *
 * Creating it is what makes iOS show the Bluetooth permission prompt (once) and, with
 * `ShowPowerAlert`, the "turn Bluetooth on" alert. Nothing may be issued before the manager
 * reports `poweredOn`, which is why [state] starts at [BluetoothState.UNKNOWN] and every caller
 * checks it first.
 *
 * Delegate callbacks arrive on a private serial queue, never on the main thread, and are only
 * forwarded: to [state], to the [scanListener] and to the [connectionListener].
 */
internal class IosCentral {

    private val _state = MutableStateFlow(BluetoothState.UNKNOWN)
    val state: StateFlow<BluetoothState> = _state.asStateFlow()

    /** Raw manager state, needed to tell "unsupported" apart from "off". */
    val managerState: CBManagerState get() = manager.state

    @Volatile
    var scanListener: IosScanListener? = null

    @Volatile
    var connectionListener: IosConnectionListener? = null

    // CoreBluetooth keeps its delegate weakly; this reference is what keeps it alive.
    private val delegate = Delegate()

    val manager: CBCentralManager = CBCentralManager(
        delegate = delegate,
        queue = dispatch_queue_create("com.radustavila.myblesense.bluetooth", null),
        options = mapOf<Any?, Any>(CBCentralManagerOptionShowPowerAlertKey to true),
    )

    /**
     * Looks a peripheral up by the identifier iOS assigned to it — the way to reach a known
     * device without scanning. Returns `null` when [deviceId] is not a UUID or iOS has never
     * seen that peripheral.
     */
    fun retrievePeripheral(deviceId: String): CBPeripheral? {
        if (!UUID_PATTERN.matches(deviceId)) return null
        return manager.retrievePeripheralsWithIdentifiers(listOf(NSUUID(uUIDString = deviceId)))
            .firstOrNull() as? CBPeripheral
    }

    private inner class Delegate : NSObject(), CBCentralManagerDelegateProtocol {

        override fun centralManagerDidUpdateState(central: CBCentralManager) {
            val state = central.state.toDomainState()
            BleLog.i("Bluetooth state changed: $state")
            _state.value = state
        }

        override fun centralManager(
            central: CBCentralManager,
            didDiscoverPeripheral: CBPeripheral,
            advertisementData: Map<Any?, *>,
            RSSI: NSNumber,
        ) {
            scanListener?.onDiscovered(didDiscoverPeripheral, advertisementData, RSSI.intValue)
        }

        override fun centralManager(central: CBCentralManager, didConnectPeripheral: CBPeripheral) {
            connectionListener?.onConnected(didConnectPeripheral)
        }

        @ObjCSignatureOverride
        override fun centralManager(
            central: CBCentralManager,
            didFailToConnectPeripheral: CBPeripheral,
            error: NSError?,
        ) {
            connectionListener?.onFailedToConnect(didFailToConnectPeripheral, error)
        }

        @ObjCSignatureOverride
        override fun centralManager(
            central: CBCentralManager,
            didDisconnectPeripheral: CBPeripheral,
            error: NSError?,
        ) {
            connectionListener?.onDisconnected(didDisconnectPeripheral, error)
        }
    }

    private companion object {
        val UUID_PATTERN = Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
    }
}
