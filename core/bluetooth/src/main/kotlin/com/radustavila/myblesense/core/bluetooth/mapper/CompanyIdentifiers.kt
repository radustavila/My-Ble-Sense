package com.radustavila.myblesense.core.bluetooth.mapper

/**
 * Subset of the Bluetooth SIG "Company Identifiers" assigned numbers.
 *
 * Only the identifier is decoded — the payload that follows it is proprietary and is never
 * interpreted, just displayed as hex.
 */
object CompanyIdentifiers {

    fun nameOf(companyId: Int): String? = NAMES[companyId]

    private val NAMES: Map<Int, String> = mapOf(
        0x0001 to "Nokia Mobile Phones",
        0x0002 to "Intel",
        0x0006 to "Microsoft",
        0x000F to "Broadcom",
        0x0075 to "Samsung Electronics",
        0x0078 to "Nike",
        0x0087 to "Garmin International",
        0x009E to "Bose",
        0x00C4 to "LG Electronics",
        0x00D2 to "Dialog Semiconductor",
        0x00E0 to "Google",
        0x0107 to "Polar Electro",
        0x0118 to "Wahoo Fitness",
        0x0131 to "Cypress Semiconductor",
        0x0157 to "Huawei",
        0x0171 to "Amazon",
        0x0180 to "Xiaomi",
        0x01D7 to "Sonos",
        0x0201 to "Tile",
        0x0211 to "Espressif",
        0x0224 to "Anker",
        0x02E5 to "Espressif Systems",
        0x0499 to "Ruuvi Innovations",
        0x004C to "Apple",
        0x0059 to "Nordic Semiconductor",
        0x000D to "Texas Instruments",
        0x0030 to "ST Microelectronics",
        0x038F to "Xiaomi Communications",
        0x05A7 to "Sonos",
        0x0A95 to "Fitbit",
    )
}
