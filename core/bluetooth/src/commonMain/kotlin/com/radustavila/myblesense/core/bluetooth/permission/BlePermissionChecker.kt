package com.radustavila.myblesense.core.bluetooth.permission

/**
 * Whether the app is currently allowed to use Bluetooth. Android answers from its runtime
 * permissions, iOS from the CoreBluetooth authorization status.
 */
interface BlePermissionChecker {
    fun hasScanPermissions(): Boolean

    fun hasConnectPermission(): Boolean
}
