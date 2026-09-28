package com.radustavila.myblesense.core.bluetooth.mapper

import android.annotation.SuppressLint
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import androidx.core.util.forEach
import com.radustavila.myblesense.domain.model.BleDevice
import com.radustavila.myblesense.domain.model.ManufacturerData
import com.radustavila.myblesense.domain.model.ServiceData
import javax.inject.Inject

/**
 * Translates an Android [ScanResult] into the platform independent [BleDevice].
 *
 * This is the only place that understands `ScanResult`/`ScanRecord`; nothing above this layer
 * ever sees an Android Bluetooth type. Advertising data is parsed here, not in Compose.
 */
class ScanResultMapper @Inject constructor() {

    @SuppressLint("MissingPermission") // The scanner only runs once permissions are granted.
    fun map(result: ScanResult, nowMillis: Long): BleDevice {
        val record: ScanRecord? = result.scanRecord
        val address = result.device.address

        return BleDevice(
            id = address,
            address = address,
            // The name from the advertisement is available without BLUETOOTH_CONNECT; the
            // adapter-cached name is only a fallback and may throw if permission was revoked.
            name = record?.deviceName?.takeIf { it.isNotBlank() } ?: result.device.safeName(),
            advertisedName = record?.deviceName?.takeIf { it.isNotBlank() },
            rssi = result.rssi,
            txPowerDbm = result.txPowerDbm() ?: record?.txPowerLevel?.takeIf { it != NO_TX_POWER },
            isConnectable = result.connectable(),
            serviceUuids = record?.serviceUuids
                ?.map { it.uuid.toString().shortenBluetoothUuid() }
                .orEmpty(),
            manufacturerData = record?.toManufacturerData().orEmpty(),
            serviceData = record?.toServiceData().orEmpty(),
            firstSeenAtMillis = nowMillis,
            lastSeenAtMillis = nowMillis,
        )
    }

    private fun ScanResult.txPowerDbm(): Int? =
        txPower.takeIf { it != ScanResult.TX_POWER_NOT_PRESENT }

    private fun ScanResult.connectable(): Boolean = isConnectable

    @SuppressLint("MissingPermission")
    private fun android.bluetooth.BluetoothDevice.safeName(): String? = try {
        name?.takeIf { it.isNotBlank() }
    } catch (_: SecurityException) {
        // BLUETOOTH_CONNECT revoked while scanning: the advertisement name is enough.
        null
    }

    private fun ScanRecord.toManufacturerData(): List<ManufacturerData> {
        val data = manufacturerSpecificData ?: return emptyList()
        return buildList {
            data.forEach { companyId, payload ->
                add(
                    ManufacturerData(
                        companyId = companyId,
                        companyName = CompanyIdentifiers.nameOf(companyId),
                        payload = payload ?: ByteArray(0),
                    ),
                )
            }
        }
    }

    private fun ScanRecord.toServiceData(): List<ServiceData> =
        serviceData.orEmpty().map { (uuid, payload) ->
            ServiceData(
                uuid = uuid.uuid.toString().shortenBluetoothUuid(),
                payload = payload ?: ByteArray(0),
            )
        }

    private companion object {
        // ScanRecord returns Integer.MIN_VALUE when the AD structure is absent.
        const val NO_TX_POWER = Int.MIN_VALUE
    }
}
