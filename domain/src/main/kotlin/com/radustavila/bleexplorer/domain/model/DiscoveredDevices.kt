package com.radustavila.bleexplorer.domain.model

/**
 * Immutable index of the devices discovered so far, keyed by their stable id.
 *
 * BLE peripherals advertise several times per second, so the same device is reported over and
 * over. [upsert] merges a new advertisement into the existing entry (updating RSSI and any
 * advertising data that changed) instead of appending a new one, and keeps insertion order so
 * the UI list stays stable.
 */
class DiscoveredDevices private constructor(
    private val byId: Map<String, BleDevice>,
) {
    val devices: List<BleDevice> get() = byId.values.toList()

    val size: Int get() = byId.size

    fun upsert(device: BleDevice): DiscoveredDevices {
        val existing = byId[device.id]
        val merged = if (existing == null) {
            device.copy(seenCount = 1)
        } else {
            device.copy(
                // Names are only present in some advertisements; never lose a known name.
                name = device.name ?: existing.name,
                advertisedName = device.advertisedName ?: existing.advertisedName,
                serviceUuids = device.serviceUuids.ifEmpty { existing.serviceUuids },
                manufacturerData = device.manufacturerData.ifEmpty { existing.manufacturerData },
                serviceData = device.serviceData.ifEmpty { existing.serviceData },
                txPowerDbm = device.txPowerDbm ?: existing.txPowerDbm,
                isConnectable = device.isConnectable ?: existing.isConnectable,
                firstSeenAtMillis = existing.firstSeenAtMillis,
                seenCount = existing.seenCount + 1,
            )
        }
        // LinkedHashMap keeps discovery order; re-putting an existing key does not move it.
        return DiscoveredDevices(LinkedHashMap(byId).apply { put(merged.id, merged) })
    }

    fun sortedBy(order: DeviceSortOrder): List<BleDevice> = devices.sortedWith(order.comparator())

    companion object {
        val EMPTY = DiscoveredDevices(emptyMap())

        fun of(vararg devices: BleDevice): DiscoveredDevices =
            devices.fold(EMPTY) { acc, device -> acc.upsert(device) }
    }
}

/** Sorting lives in the domain layer, never inside a Composable. */
fun DeviceSortOrder.comparator(): Comparator<BleDevice> = when (this) {
    // Higher (less negative) RSSI first.
    DeviceSortOrder.SIGNAL_STRENGTH -> compareByDescending<BleDevice> { it.rssi }.thenBy { it.id }

    // Named devices first, alphabetically; unnamed devices last, ordered by id.
    DeviceSortOrder.NAME -> compareBy<BleDevice> { it.displayName == null }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.displayName.orEmpty() }
        .thenBy { it.id }

    DeviceSortOrder.FIRST_SEEN -> compareBy<BleDevice> { it.firstSeenAtMillis }.thenBy { it.id }
}
