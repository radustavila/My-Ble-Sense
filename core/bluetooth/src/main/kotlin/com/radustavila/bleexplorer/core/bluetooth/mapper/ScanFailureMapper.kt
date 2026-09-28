package com.radustavila.bleexplorer.core.bluetooth.mapper

import android.bluetooth.le.ScanCallback
import com.radustavila.bleexplorer.domain.model.BleError

/** Maps the raw `ScanCallback` error codes onto the domain error vocabulary. */
fun scanErrorCodeToBleError(errorCode: Int): BleError = when (errorCode) {
    ScanCallback.SCAN_FAILED_ALREADY_STARTED ->
        BleError.ScanAlreadyStarted

    ScanCallback.SCAN_FAILED_APPLICATION_REGISTRATION_FAILED ->
        BleError.ScanFailed(errorCode, "Application registration failed")

    ScanCallback.SCAN_FAILED_FEATURE_UNSUPPORTED ->
        BleError.ScanFailed(errorCode, "Scan feature unsupported")

    ScanCallback.SCAN_FAILED_INTERNAL_ERROR ->
        BleError.ScanFailed(errorCode, "Internal scanner error")

    SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES ->
        BleError.ScanFailed(errorCode, "Out of hardware resources")

    SCAN_FAILED_SCANNING_TOO_FREQUENTLY ->
        BleError.ScanThrottled

    else -> BleError.ScanFailed(errorCode)
}

// Present in AOSP since API 21 / 30 but only public from API 33+, so they are inlined here to
// keep the mapping exhaustive on every supported API level.
private const val SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES = 5
private const val SCAN_FAILED_SCANNING_TOO_FREQUENTLY = 6
