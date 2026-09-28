package com.radustavila.myblesense.feature.scanner.permission

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.radustavila.myblesense.core.bluetooth.permission.BlePermissions

/**
 * Launcher for the BLE runtime permissions of the current API level.
 *
 * [onResult] reports whether everything was granted and whether the system would still show a
 * rationale — `false` after a denial means "don't ask again", which is how the app knows to send
 * the user to the system settings instead of re-prompting forever.
 */
@Composable
fun rememberBlePermissionLauncher(
    onResult: (granted: Boolean, shouldShowRationale: Boolean) -> Unit,
): ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>> {
    val context = LocalContext.current
    return rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        val granted = result.values.all { it } && result.isNotEmpty()
        onResult(granted, context.shouldShowBlePermissionRationale())
    }
}

/** Launcher for the system "turn Bluetooth on?" dialog. Declining it is not an error. */
@Composable
fun rememberEnableBluetoothLauncher(
    onResult: (enabled: Boolean) -> Unit,
): ManagedActivityResultLauncher<Intent, androidx.activity.result.ActivityResult> =
    rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onResult(result.resultCode == Activity.RESULT_OK)
    }

@Composable
fun rememberEnableBluetoothIntent(): Intent =
    remember { Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE) }

fun Context.hasBlePermissions(): Boolean = BlePermissions.required.all { permission ->
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

fun Context.shouldShowBlePermissionRationale(): Boolean {
    val activity = findActivity() ?: return false
    return BlePermissions.required.any {
        ActivityCompat.shouldShowRequestPermissionRationale(activity, it)
    }
}

/** Opens this app's page in the system settings, the only path out of a permanent denial. */
fun Context.openAppSettings() {
    startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
