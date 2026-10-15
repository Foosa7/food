package com.food.core

/** EAN-13 and EAN-8, the codes on Swedish groceries. US UPC-A is read as EAN-13 with a leading 0. */
object Barcodes {

    /** The code as stored, or null if it isn't a valid EAN (wrong length or check digit). */
    fun normalize(raw: String): String? {
        val digits = raw.trim().filter { !it.isWhitespace() && it != '-' }
        if (digits.isEmpty() || !digits.all { it.isDigit() }) return null
        val code = if (digits.length == 12) "0$digits" else digits
        return code.takeIf { (it.length == 13 || it.length == 8) && checkDigitOk(it) }
    }

    /** Weights 3 and 1 alternate from the right, not counting the check digit. */
    fun checkDigitOk(code: String): Boolean {
        val body = code.dropLast(1)
        val sum = body.reversed().mapIndexed { i, c -> (c - '0') * if (i % 2 == 0) 3 else 1 }.sum()
        return (10 - sum % 10) % 10 == code.last() - '0'
    }
}
