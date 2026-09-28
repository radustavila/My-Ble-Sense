package com.radustavila.bleexplorer.domain.model

/**
 * GATT profile of the ESP32 proof-of-concept peripheral (`ESP32-BLE-POC`).
 *
 * The profile is knowledge about *what* the peripheral exposes, not about *how* it is reached, so
 * it lives in the domain layer: the generic GATT client discovers and reads it like any other
 * device, and only the use cases know which UUIDs matter.
 */
object PocProfile {
    const val DEVICE_NAME = "ESP32-BLE-POC"

    const val SERVICE_UUID = "12345678-1234-1234-1234-123456789abc"

    /** `READ` — reports the peripheral's state, `"ONLINE"` once it is up. */
    const val STATUS_UUID = "12345678-1234-1234-1234-123456789abd"

    /** `WRITE` — accepts commands. */
    const val COMMAND_UUID = "12345678-1234-1234-1234-123456789abe"

    /** `NOTIFY` — pushes events. */
    const val EVENTS_UUID = "12345678-1234-1234-1234-123456789abf"
}

/**
 * Commands the firmware understands on the Command characteristic.
 *
 * The wire format is the plain UTF-8 text of [text], without a terminator: the firmware reads the
 * written value as-is.
 */
enum class PocCommand(val text: String) {
    PING("PING"),
    ;

    fun encode(): ByteArray = text.encodeToByteArray()
}

/**
 * One event pushed by the firmware on the Events characteristic (`"EVENT 1"`, `"EVENT 2"`, ...).
 *
 * [text] is the notification payload decoded as UTF-8; [timestampMillis] is when the value
 * reached the app, as stamped by the GATT client.
 */
data class PocEvent(
    val text: String,
    val timestampMillis: Long,
)

/**
 * The POC service as found in a connected device's GATT database.
 *
 * Each characteristic is nullable: a firmware under development may well expose the service with
 * only some of them, and the UI must be able to say which one is missing instead of failing as a
 * whole. The [GattCharacteristic.id]s are the ones reported by discovery, so they can be handed
 * straight to the generic read/write/notify use cases.
 */
data class PocService(
    val service: GattService,
    val status: GattCharacteristic?,
    val command: GattCharacteristic?,
    val events: GattCharacteristic?,
) {
    val hasAllCharacteristics: Boolean
        get() = status != null && command != null && events != null
}

/**
 * Looks the POC service up in a discovered GATT database, or returns `null` when the connected
 * device does not expose it.
 *
 * UUIDs are compared case-insensitively: the platform reports them in lower case, but nothing in
 * the Bluetooth spec requires it, and a case mismatch would silently hide the service.
 */
fun List<GattService>.findPocService(): PocService? {
    val service = firstOrNull { it.uuid.equals(PocProfile.SERVICE_UUID, ignoreCase = true) }
        ?: return null

    fun characteristic(uuid: String): GattCharacteristic? =
        service.characteristics.firstOrNull { it.uuid.equals(uuid, ignoreCase = true) }

    return PocService(
        service = service,
        status = characteristic(PocProfile.STATUS_UUID),
        command = characteristic(PocProfile.COMMAND_UUID),
        events = characteristic(PocProfile.EVENTS_UUID),
    )
}
