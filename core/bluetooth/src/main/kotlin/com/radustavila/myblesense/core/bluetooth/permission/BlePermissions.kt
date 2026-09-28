package com.radustavila.myblesense.core.bluetooth.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The runtime permissions BLE scanning needs, per API level.
 *
 * - API 31+ : `BLUETOOTH_SCAN` (declared with `neverForLocation`) and `BLUETOOTH_CONNECT` to read
 *   device names. Location is *not* requested.
 * - API 26-30 : the platform only returns scan results when a location permission is held, so
 *   `ACCESS_FINE_LOCATION` is required. The `BLUETOOTH`/`BLUETOOTH_ADMIN` permissions of those
 *   releases are install-time and never requested at runtime.
 */
object BlePermissions {

    /**
     * Connecting to a peripheral needs `BLUETOOTH_CONNECT` from API 31 on. Below that the
     * install-time `BLUETOOTH` permission is enough, so there is nothing to request at runtime.
     */
    val connect: List<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        emptyList()
    }

    val required: List<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_SCAN)
            add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
}

/** Checks whether the permissions required to scan on this API level are currently granted. */
@Singleton
class BlePermissionChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun hasScanPermissions(): Boolean = BlePermissions.required.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /** Whether the app may open a GATT connection on this API level. */
    fun hasConnectPermission(): Boolean = BlePermissions.connect.all { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    fun missingPermissions(): List<String> = BlePermissions.required.filterNot { permission ->
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }
}
