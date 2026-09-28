package com.radustavila.bleexplorer.domain.session

import com.radustavila.bleexplorer.domain.model.BleDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory store of the devices discovered during the current scan session.
 *
 * It is the single source of truth shared by the scanner screen and the details screen, so the
 * details screen keeps updating (RSSI, new advertising data) while a scan is running.
 */
interface ScanSessionStore {
    val devices: StateFlow<List<BleDevice>>

    fun observeDevice(deviceId: String): Flow<BleDevice?>

    fun publish(devices: List<BleDevice>)

    fun clear()
}

@Singleton
class InMemoryScanSessionStore @Inject constructor() : ScanSessionStore {
    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())

    override val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()

    override fun observeDevice(deviceId: String): Flow<BleDevice?> =
        _devices.map { list -> list.firstOrNull { it.id == deviceId } }

    override fun publish(devices: List<BleDevice>) {
        _devices.value = devices
    }

    override fun clear() {
        _devices.value = emptyList()
    }
}
