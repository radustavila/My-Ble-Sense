package com.radustavila.myblesense.core.common.format

/** Formats raw advertising payloads for display, e.g. `02 01 06 1A FF`. */
fun ByteArray.toHexString(separator: String = " "): String =
    joinToString(separator) { byte -> HEX_DIGITS[byte.toInt() and 0xFF] }

/** Formats a Bluetooth SIG company identifier the way the spec writes it, e.g. `0x004C`. */
fun Int.toCompanyIdHex(): String = "0x" + toString(radix = 16).uppercase().padStart(4, '0')

private val HEX_DIGITS: Array<String> =
    Array(256) { it.toString(radix = 16).uppercase().padStart(2, '0') }

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
    val text = decodeToString()
    val isPrintable = text.none { it == '�' || (it.isISOControl() && it != '\n' && it != '\t') }
    return text.takeIf { isPrintable && it.isNotBlank() }
}

private const val HEX_ALPHABET = "0123456789abcdefABCDEF"

/**
 * Formats [value] with exactly [digits] decimals, rounding half away from zero and always using a
 * dot as the separator — the multiplatform stand-in for `String.format(Locale.US, "%.2f", value)`.
 */
fun formatDecimal(value: Double, digits: Int): String {
    require(digits >= 0) { "The number of decimals cannot be negative" }
    var scale = 1L
    repeat(digits) { scale *= 10 }
    val scaled = kotlin.math.floor(kotlin.math.abs(value) * scale + 0.5).toLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    val whole = scaled / scale
    if (digits == 0) return "$sign$whole"
    return "$sign$whole.${(scaled % scale).toString().padStart(digits, '0')}"
}
