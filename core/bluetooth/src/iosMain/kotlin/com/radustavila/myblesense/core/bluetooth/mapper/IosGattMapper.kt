package com.radustavila.myblesense.core.bluetooth.mapper

import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.BluetoothState
import com.radustavila.myblesense.domain.model.GattCharacteristic
import com.radustavila.myblesense.domain.model.GattCharacteristicId
import com.radustavila.myblesense.domain.model.GattCharacteristicProperty
import com.radustavila.myblesense.domain.model.GattService
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.CoreBluetooth.CBCharacteristic
import platform.CoreBluetooth.CBManagerState
import platform.CoreBluetooth.CBManagerStatePoweredOff
import platform.CoreBluetooth.CBManagerStatePoweredOn
import platform.CoreBluetooth.CBService
import platform.CoreBluetooth.CBUUID
import platform.Foundation.NSData
import platform.Foundation.create
import platform.posix.memcpy

/**
 * `unauthorized`, `resetting` and the initial `unknown` are all "not usable right now" without
 * being "off": authorization is reported through the permission checker, and the other two are
 * transient.
 */
internal fun CBManagerState.toDomainState(): BluetoothState = when (this) {
    CBManagerStatePoweredOn -> BluetoothState.ENABLED
    CBManagerStatePoweredOff -> BluetoothState.DISABLED
    else -> BluetoothState.UNKNOWN
}

/**
 * CoreBluetooth prints SIG-assigned UUIDs in their short form (`180D`) and custom ones in upper
 * case; the domain layer uses the full 128-bit lower case form on every platform.
 */
internal fun CBUUID.toDomainUuid(): String = expandBluetoothUuid(UUIDString)

internal fun expandBluetoothUuid(uuid: String): String {
    val lower = uuid.lowercase()
    return when (lower.length) {
        SHORT_UUID_LENGTH -> "0000$lower$BASE_UUID_SUFFIX"
        MEDIUM_UUID_LENGTH -> "$lower$BASE_UUID_SUFFIX"
        else -> lower
    }
}

internal fun CBService.toDomain(): GattService {
    val serviceUuid = UUID.toDomainUuid()
    return GattService(
        uuid = serviceUuid,
        characteristics = characteristics.orEmpty()
            .filterIsInstance<CBCharacteristic>()
            .map { it.toDomain(serviceUuid) },
    )
}

internal fun CBCharacteristic.toDomain(serviceUuid: String): GattCharacteristic = GattCharacteristic(
    id = GattCharacteristicId(serviceUuid = serviceUuid, characteristicUuid = UUID.toDomainUuid()),
    properties = properties.toDomainProperties(),
)

/** `CBCharacteristicProperties` uses the bit values of the Bluetooth specification. */
internal fun ULong.toDomainProperties(): Set<GattCharacteristicProperty> = buildSet {
    if (hasProperty(PROPERTY_READ)) add(GattCharacteristicProperty.READ)
    if (hasProperty(PROPERTY_WRITE)) add(GattCharacteristicProperty.WRITE)
    if (hasProperty(PROPERTY_WRITE_NO_RESPONSE)) add(GattCharacteristicProperty.WRITE_NO_RESPONSE)
    if (hasProperty(PROPERTY_NOTIFY)) add(GattCharacteristicProperty.NOTIFY)
    if (hasProperty(PROPERTY_INDICATE)) add(GattCharacteristicProperty.INDICATE)
}

private fun ULong.hasProperty(property: ULong): Boolean = this and property != 0uL

/** Maps the code of a `CBATTErrorDomain` error; mirrors the Android GATT status mapping. */
internal fun attErrorCodeToBleError(code: Int, onFailure: (Int) -> BleError): BleError = when (code) {
    ATT_READ_NOT_PERMITTED,
    ATT_WRITE_NOT_PERMITTED,
    ATT_REQUEST_NOT_SUPPORTED,
    -> BleError.CharacteristicOperationUnsupported

    ATT_INSUFFICIENT_AUTHENTICATION,
    ATT_INSUFFICIENT_ENCRYPTION,
    -> BleError.UnknownGattError(code)

    else -> onFailure(code)
}

/**
 * Maps how a link ended. [errorCode] is the `CBErrorDomain` code CoreBluetooth reported, `null`
 * when it reported none (a disconnect the app asked for).
 */
internal fun disconnectionToBleError(errorCode: Int?, wasConnected: Boolean): BleError = when {
    errorCode == null -> BleError.Disconnected
    errorCode == CB_ERROR_CONNECTION_TIMEOUT -> BleError.ConnectionTimeout
    errorCode == CB_ERROR_PERIPHERAL_DISCONNECTED -> BleError.Disconnected
    // Losing an established link is a disconnect; failing to establish one is a failure.
    wasConnected -> BleError.Disconnected
    else -> BleError.ConnectionFailed(errorCode)
}

@OptIn(ExperimentalForeignApi::class)
internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}

@OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }

private const val BASE_UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb"
private const val SHORT_UUID_LENGTH = 4
private const val MEDIUM_UUID_LENGTH = 8

private const val PROPERTY_READ = 0x02uL
private const val PROPERTY_WRITE_NO_RESPONSE = 0x04uL
private const val PROPERTY_WRITE = 0x08uL
private const val PROPERTY_NOTIFY = 0x10uL
private const val PROPERTY_INDICATE = 0x20uL

private const val ATT_READ_NOT_PERMITTED = 2
private const val ATT_WRITE_NOT_PERMITTED = 3
private const val ATT_INSUFFICIENT_AUTHENTICATION = 5
private const val ATT_REQUEST_NOT_SUPPORTED = 6
private const val ATT_INSUFFICIENT_ENCRYPTION = 15

private const val CB_ERROR_CONNECTION_TIMEOUT = 6
private const val CB_ERROR_PERIPHERAL_DISCONNECTED = 7
