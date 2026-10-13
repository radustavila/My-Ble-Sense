package com.radustavila.myblesense.core.bluetooth.scanner

import com.radustavila.myblesense.core.bluetooth.central.IosCentral
import com.radustavila.myblesense.core.bluetooth.central.IosScanListener
import com.radustavila.myblesense.core.bluetooth.mapper.CompanyIdentifiers
import com.radustavila.myblesense.core.bluetooth.mapper.shortenBluetoothUuid
import com.radustavila.myblesense.core.bluetooth.mapper.toByteArray
import com.radustavila.myblesense.core.bluetooth.mapper.toDomainUuid
import com.radustavila.myblesense.core.bluetooth.permission.BlePermissionChecker
import com.radustavila.myblesense.core.common.log.BleLog
import com.radustavila.myblesense.core.common.time.currentTimeMillis
import com.radustavila.myblesense.domain.model.BleDevice
import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.BleException
import com.radustavila.myblesense.domain.model.BleScanSettings
import com.radustavila.myblesense.domain.model.ManufacturerData
import com.radustavila.myblesense.domain.model.ServiceData
import com.radustavila.myblesense.domain.repository.BleScanner
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import platform.CoreBluetooth.CBAdvertisementDataIsConnectable
import platform.CoreBluetooth.CBAdvertisementDataLocalNameKey
import platform.CoreBluetooth.CBAdvertisementDataManufacturerDataKey
import platform.CoreBluetooth.CBAdvertisementDataServiceDataKey
import platform.CoreBluetooth.CBAdvertisementDataServiceUUIDsKey
import platform.CoreBluetooth.CBAdvertisementDataTxPowerLevelKey
import platform.CoreBluetooth.CBCentralManagerScanOptionAllowDuplicatesKey
import platform.CoreBluetooth.CBPeripheral
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.NSNumber

/**
 * [BleScanner] on top of `CBCentralManager.scanForPeripherals`.
 *
 * Same contract as the Android scanner: a cold flow whose collection is the lifetime of the scan.
 * Duplicates are allowed so RSSI keeps refreshing; de-duplication happens in the scan use case.
 *
 * The device id is `CBPeripheral.identifier`: iOS never exposes the hardware address. That UUID
 * is stable on this phone, which is all the known-device feature needs, but it is not the id
 * another phone (or Android) sees for the same peripheral.
 */
internal class IosBleScanner(
    private val central: IosCentral,
    private val permissionChecker: BlePermissionChecker,
) : BleScanner {

    override fun scan(settings: BleScanSettings): Flow<BleDevice> = callbackFlow {
        if (!permissionChecker.hasScanPermissions()) throw BleException(BleError.PermissionDenied)
        if (!central.state.value.isEnabled) throw BleException(BleError.ScannerUnavailable)
        if (central.scanListener != null) throw BleException(BleError.ScanAlreadyStarted)

        central.scanListener = IosScanListener { peripheral, advertisementData, rssi ->
            trySend(mapAdvertisement(peripheral, advertisementData, rssi, currentTimeMillis()))
        }
        central.manager.scanForPeripheralsWithServices(
            serviceUUIDs = settings.serviceUuidFilters
                .map { CBUUID.UUIDWithString(it) }
                .takeIf { it.isNotEmpty() },
            options = mapOf<Any?, Any>(CBCentralManagerScanOptionAllowDuplicatesKey to true),
        )
        BleLog.i("BLE scan started (duration=${settings.duration})")

        awaitClose {
            central.scanListener = null
            // Stopping a scan on a manager that is no longer powered on is an API misuse.
            if (central.state.value.isEnabled) central.manager.stopScan()
            BleLog.i("BLE scan stopped")
        }
    }.buffer(SCAN_BUFFER_CAPACITY) // advertisements arrive faster than the UI consumes them

    private companion object {
        const val SCAN_BUFFER_CAPACITY = 128
    }
}

internal fun mapAdvertisement(
    peripheral: CBPeripheral,
    advertisementData: Map<Any?, *>,
    rssi: Int,
    nowMillis: Long,
): BleDevice {
    val id = peripheral.identifier.UUIDString
    val advertisedName = (advertisementData[CBAdvertisementDataLocalNameKey] as? String)
        ?.takeIf { it.isNotBlank() }
    return BleDevice(
        id = id,
        address = id,
        name = advertisedName ?: peripheral.name?.takeIf { it.isNotBlank() },
        advertisedName = advertisedName,
        rssi = rssi,
        txPowerDbm = (advertisementData[CBAdvertisementDataTxPowerLevelKey] as? NSNumber)?.intValue,
        isConnectable = (advertisementData[CBAdvertisementDataIsConnectable] as? NSNumber)?.boolValue,
        serviceUuids = (advertisementData[CBAdvertisementDataServiceUUIDsKey] as? List<*>).orEmpty()
            .filterIsInstance<CBUUID>()
            .map { it.toDomainUuid().shortenBluetoothUuid() },
        manufacturerData = (advertisementData[CBAdvertisementDataManufacturerDataKey] as? NSData)
            ?.toByteArray()
            ?.toManufacturerData()
            .orEmpty(),
        serviceData = (advertisementData[CBAdvertisementDataServiceDataKey] as? Map<*, *>).orEmpty()
            .mapNotNull { (uuid, payload) ->
                if (uuid !is CBUUID || payload !is NSData) return@mapNotNull null
                ServiceData(uuid.toDomainUuid().shortenBluetoothUuid(), payload.toByteArray())
            },
        firstSeenAtMillis = nowMillis,
        lastSeenAtMillis = nowMillis,
    )
}

/**
 * CoreBluetooth hands over the manufacturer AD structure whole: a little-endian company
 * identifier followed by the payload. Android splits it; this does the same.
 */
internal fun ByteArray.toManufacturerData(): List<ManufacturerData> {
    if (size < COMPANY_ID_SIZE) return emptyList()
    val companyId = (this[0].toInt() and 0xFF) or ((this[1].toInt() and 0xFF) shl 8)
    return listOf(
        ManufacturerData(
            companyId = companyId,
            companyName = CompanyIdentifiers.nameOf(companyId),
            payload = copyOfRange(COMPANY_ID_SIZE, size),
        ),
    )
}

private const val COMPANY_ID_SIZE = 2
