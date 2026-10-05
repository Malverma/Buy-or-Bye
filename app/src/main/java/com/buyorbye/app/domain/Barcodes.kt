package com.buyorbye.app.domain

object Barcodes {

    /**
     * Normalizes a scanned retail code to the form product databases index: UPC-E is
     * expanded to UPC-A, and an EAN-13 with a leading 0 (a US code) becomes UPC-A.
     */
    fun normalize(raw: String, isUpcE: Boolean = false): String {
        if (!raw.all(Char::isDigit)) return raw
        return when {
            isUpcE && (raw.length == 6 || raw.length == 8) -> expandUpcE(raw)
            raw.length == 13 && raw.startsWith("0") -> raw.drop(1)
            else -> raw
        }
    }

    /** UPC-E (6 or 8 digits) → 12-digit UPC-A, per the standard zero-suppression rules. */
    fun expandUpcE(code: String): String {
        val numberSystem = if (code.length == 8) code[0] else '0'
        val s = if (code.length == 8) code.substring(1, 7) else code
        val body = when (s[5]) {
            '0', '1', '2' -> "${s[0]}${s[1]}${s[5]}0000${s[2]}${s[3]}${s[4]}"
            '3' -> "${s[0]}${s[1]}${s[2]}00000${s[3]}${s[4]}"
            '4' -> "${s[0]}${s[1]}${s[2]}${s[3]}00000${s[4]}"
            else -> "${s[0]}${s[1]}${s[2]}${s[3]}${s[4]}0000${s[5]}"
        }
        val first11 = "$numberSystem$body"
        val check = if (code.length == 8) code[7] else upcCheckDigit(first11)
        return "$first11$check"
    }

    private fun upcCheckDigit(first11: String): Char {
        val sum = first11.mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 3 else 1 }.sum()
        return '0' + (10 - sum % 10) % 10
    }
}
