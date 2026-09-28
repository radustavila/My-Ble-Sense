package com.radustavila.bleexplorer.core.common.format

/** Formats raw advertising payloads for display, e.g. `02 01 06 1A FF`. */
fun ByteArray.toHexString(separator: String = " "): String =
    joinToString(separator) { byte -> HEX_DIGITS[byte.toInt() and 0xFF] }

/** Formats a Bluetooth SIG company identifier the way the spec writes it, e.g. `0x004C`. */
fun Int.toCompanyIdHex(): String = "0x%04X".format(this)

private val HEX_DIGITS: Array<String> = Array(256) { "%02X".format(it) }

/**
 * Parses user typed hexadecimal into raw bytes, e.g. `01 FF 2A`, `01-ff-2a` or `01FF2A`.
 *
 * Separators (spaces, colons, dashes) and an optional `0x` prefix are accepted because BLE values
 * are copied from datasheets in all of those shapes. Anything else — an odd number of digits, a
 * non-hex character, an empty string — returns `null` rather than a partially parsed value: a
 * write is sent to real hardware and must never guess what the user meant.
 */
fun String.parseHexOrNull(): ByteArray? {
    val digits = filterNot { it.isWhitespace() || it == ':' || it == '-' }
        .removePrefix("0x")
        .removePrefix("0X")

    if (digits.isEmpty() || digits.length % 2 != 0) return null
    if (digits.any { it !in HEX_ALPHABET }) return null

    return ByteArray(digits.length / 2) { index ->
        digits.substring(index * 2, index * 2 + 2).toInt(radix = 16).toByte()
    }
}

/**
 * Best-effort UTF-8 rendering of a raw payload, or `null` when the bytes are not printable text.
 *
 * Arbitrary binary data decodes into replacement characters and control codes, so it is rejected
 * instead of shown: an interpretation that looks like text but is not would be worse than none.
 */
fun ByteArray.toPrintableUtf8OrNull(): String? {
    if (isEmpty()) return null
    val text = toString(Charsets.UTF_8)
    val isPrintable = text.none { it == '�' || (it.isISOControl() && it != '\n' && it != '\t') }
    return text.takeIf { isPrintable && it.isNotBlank() }
}

private const val HEX_ALPHABET = "0123456789abcdefABCDEF"
