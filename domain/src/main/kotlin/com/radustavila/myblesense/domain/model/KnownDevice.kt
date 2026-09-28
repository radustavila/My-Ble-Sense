package com.radustavila.myblesense.domain.model

/**
 * The last device the app successfully connected to, remembered across launches so it can be
 * shown — and connected to again — without a new scan.
 *
 * Only the minimal stable information is kept: the identifier the GATT client connects to (the
 * hardware address) and the name last seen for it. No platform object is ever persisted, and
 * nothing about the connection itself: whether the device is nearby is only known at runtime.
 */
data class KnownDevice(
    val id: String,
    val name: String?,
) {
    /**
     * Merges what a scan reports for this same device. A name the scan learned replaces the
     * remembered one; a scan without a name never erases it.
     */
    fun updatedFrom(scanned: BleDevice): KnownDevice {
        require(scanned.id == id) { "A scan result of another device cannot update this one" }
        return copy(name = scanned.displayName ?: name)
    }
}

/**
 * Whether the app should open the link to [knownDevice] on its own, as the device list appears.
 *
 * At most once per screen lifetime ([hasAttempted]): a known device that is out of range must not
 * be retried in a loop, and a user who disconnected or connected elsewhere has taken over. It is
 * only appropriate while Bluetooth is usable ([isBluetoothReady]: supported, enabled, permitted)
 * and the single GATT connection is idle — an open or opening link is never replaced by it.
 */
fun shouldAutoConnect(
    knownDevice: KnownDevice?,
    isBluetoothReady: Boolean,
    connection: GattConnection,
    hasAttempted: Boolean,
): Boolean = knownDevice != null &&
    isBluetoothReady &&
    !hasAttempted &&
    connection.state == GattConnectionState.DISCONNECTED
