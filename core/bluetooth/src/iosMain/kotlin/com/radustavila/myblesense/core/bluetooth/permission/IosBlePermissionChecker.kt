package com.radustavila.myblesense.core.bluetooth.permission

import platform.CoreBluetooth.CBManager
import platform.CoreBluetooth.CBManagerAuthorizationDenied
import platform.CoreBluetooth.CBManagerAuthorizationRestricted

/**
 * iOS has a single Bluetooth authorization covering scanning and connecting. While it is still
 * undetermined the system prompt is pending, which is not a denial.
 */
internal class IosBlePermissionChecker : BlePermissionChecker {

    override fun hasScanPermissions(): Boolean = isAuthorized()

    override fun hasConnectPermission(): Boolean = isAuthorized()

    private fun isAuthorized(): Boolean = when (CBManager.authorization) {
        CBManagerAuthorizationDenied, CBManagerAuthorizationRestricted -> false
        else -> true
    }
}
