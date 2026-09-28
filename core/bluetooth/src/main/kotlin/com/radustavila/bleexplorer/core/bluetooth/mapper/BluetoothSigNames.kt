package com.radustavila.bleexplorer.core.bluetooth.mapper

/**
 * Small subset of the Bluetooth SIG "Assigned Numbers" for services and characteristics.
 *
 * Deliberately *not* a full database: the point is to make the common standard entries readable,
 * not to pretend the app knows every UUID. Anything unknown — including every vendor specific
 * 128-bit UUID — has no name and is displayed as its UUID, which is the honest answer.
 *
 * The table is isolated from the GATT implementation: naming is presentation, and the client
 * works purely with UUID strings.
 */
object BluetoothSigNames {

    /** @return the assigned name of a service UUID, or `null` when it is not a known SIG UUID. */
    fun serviceName(uuid: String): String? = SERVICES[uuid.shortenBluetoothUuid()]

    /** @return the assigned name of a characteristic UUID, or `null` when unknown. */
    fun characteristicName(uuid: String): String? = CHARACTERISTICS[uuid.shortenBluetoothUuid()]

    private val SERVICES: Map<String, String> = mapOf(
        "1800" to "Generic Access",
        "1801" to "Generic Attribute",
        "1802" to "Immediate Alert",
        "1803" to "Link Loss",
        "1804" to "Tx Power",
        "1805" to "Current Time",
        "180A" to "Device Information",
        "180D" to "Heart Rate",
        "180F" to "Battery Service",
        "1810" to "Blood Pressure",
        "1812" to "Human Interface Device",
        "1814" to "Running Speed and Cadence",
        "1816" to "Cycling Speed and Cadence",
        "1818" to "Cycling Power",
        "181A" to "Environmental Sensing",
        "181B" to "Body Composition",
        "181C" to "User Data",
        "181D" to "Weight Scale",
        "1826" to "Fitness Machine",
        "FE59" to "Nordic DFU",
    )

    private val CHARACTERISTICS: Map<String, String> = mapOf(
        "2A00" to "Device Name",
        "2A01" to "Appearance",
        "2A04" to "Peripheral Preferred Connection Parameters",
        "2A05" to "Service Changed",
        "2A19" to "Battery Level",
        "2A23" to "System ID",
        "2A24" to "Model Number String",
        "2A25" to "Serial Number String",
        "2A26" to "Firmware Revision String",
        "2A27" to "Hardware Revision String",
        "2A28" to "Software Revision String",
        "2A29" to "Manufacturer Name String",
        "2A2B" to "Current Time",
        "2A37" to "Heart Rate Measurement",
        "2A38" to "Body Sensor Location",
        "2A39" to "Heart Rate Control Point",
        "2A63" to "Cycling Power Measurement",
        "2A6D" to "Pressure",
        "2A6E" to "Temperature",
        "2A6F" to "Humidity",
        "2A98" to "Weight",
        "2AB3" to "Altitude",
    )
}
