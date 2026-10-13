package com.radustavila.myblesense.core.bluetooth.mapper

import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import com.radustavila.myblesense.domain.model.BleError
import com.radustavila.myblesense.domain.model.GattCharacteristic
import com.radustavila.myblesense.domain.model.GattCharacteristicId
import com.radustavila.myblesense.domain.model.GattCharacteristicProperty
import com.radustavila.myblesense.domain.model.GattService

/**
 * Turns the Android GATT objects into domain models.
 *
 * This is the only place where `BluetoothGattService` / `BluetoothGattCharacteristic` are read;
 * past this mapper the app works with plain data classes.
 */
fun BluetoothGattService.toDomain(): GattService = GattService(
    uuid = uuid.toString(),
    characteristics = characteristics.orEmpty().map { it.toDomain(uuid.toString()) },
)

fun BluetoothGattCharacteristic.toDomain(serviceUuid: String): GattCharacteristic =
    GattCharacteristic(
        id = GattCharacteristicId(
            serviceUuid = serviceUuid,
            characteristicUuid = uuid.toString(),
        ),
        properties = properties.toDomainProperties(),
    )

/** Decodes the `PROPERTY_*` bit mask. Bits the app has no action for are simply not modelled. */
internal fun Int.toDomainProperties(): Set<GattCharacteristicProperty> = buildSet {
    if (hasProperty(BluetoothGattCharacteristic.PROPERTY_READ)) {
        add(GattCharacteristicProperty.READ)
    }
    if (hasProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)) {
        add(GattCharacteristicProperty.WRITE)
    }
    if (hasProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) {
        add(GattCharacteristicProperty.WRITE_NO_RESPONSE)
    }
    if (hasProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)) {
        add(GattCharacteristicProperty.NOTIFY)
    }
    if (hasProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE)) {
        add(GattCharacteristicProperty.INDICATE)
    }
}

private fun Int.hasProperty(property: Int): Boolean = this and property != 0

/**
 * Maps a GATT status code onto the domain vocabulary.
 *
 * [onFailure] builds the error for statuses that have no more specific meaning, so the caller can
 * say whether a failed status was a read, a write or a descriptor write.
 */
fun gattStatusToBleError(status: Int, onFailure: (Int) -> BleError): BleError = when (status) {
    BluetoothGatt.GATT_READ_NOT_PERMITTED,
    BluetoothGatt.GATT_WRITE_NOT_PERMITTED,
    BluetoothGatt.GATT_REQUEST_NOT_SUPPORTED,
    -> BleError.CharacteristicOperationUnsupported

    BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION,
    BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION,
    -> BleError.UnknownGattError(status)

    else -> onFailure(status)
}

/**
 * Maps the status reported together with a connection state change.
 *
 * `133` is the infamous catch-all the Android stack returns for "the link could not be
 * established"; it is reported as a plain connection failure rather than pretending to know more.
 */
fun connectionStatusToBleError(status: Int, wasConnected: Boolean): BleError = when {
    status == BluetoothGatt.GATT_SUCCESS -> BleError.Disconnected
    status == GATT_CONNECTION_TIMEOUT -> BleError.ConnectionTimeout
    status == GATT_TERMINATED_BY_PEER -> BleError.Disconnected
    // Losing an established link is a disconnect; failing to establish one is a failure.
    wasConnected -> BleError.Disconnected
    else -> BleError.ConnectionFailed(status)
}

/** `GATT_CONN_TIMEOUT` — the peripheral stopped answering (out of range, powered off). */
private const val GATT_CONNECTION_TIMEOUT = 8

/** `GATT_CONN_TERMINATE_PEER_USER` — the peripheral closed the link itself. */
private const val GATT_TERMINATED_BY_PEER = 19
