package io.github.sunjirapath.promptparse

import java.math.BigDecimal
import java.math.RoundingMode

/** Tag 54 holds at most 13 characters (EMVCo); "dddddddddd.dd" -> 10 integer digits. */
private const val MAX_AMOUNT_LENGTH = 13
private const val MAX_AMOUNT_INTEGER_DIGITS = 10

/** Below 10^-3 (magnitude -3 and under) an amount rounds to 0.00 at 2 decimals. */
private const val ROUNDS_TO_ZERO_MAGNITUDE = -3

internal object Amounts {

    /**
     * Amount as written in tag 54: 2 decimals, `HALF_UP`.
     *
     * @throws IllegalArgumentException if the amount is not greater than 0 after rounding, or has more than 10 integer digits.
     */
    fun format(amount: BigDecimal): String {
        require(amount.signum() > 0) { "amount must be greater than 0" }
        // Checked before rounding so extreme exponents (1E+999999999, 1E-999999999) cannot make setScale blow up.
        val magnitude = amount.precision() - amount.scale() // value < 10^magnitude
        require(magnitude <= MAX_AMOUNT_INTEGER_DIGITS) {
            "amount must have at most $MAX_AMOUNT_INTEGER_DIGITS integer digits"
        }
        require(magnitude > ROUNDS_TO_ZERO_MAGNITUDE) { "amount must be greater than 0 after rounding to 2 decimals" }
        val rounded = amount.setScale(2, RoundingMode.HALF_UP)
        require(rounded.signum() > 0) { "amount must be greater than 0 after rounding to 2 decimals" }
        val text = rounded.toPlainString()
        require(text.length <= MAX_AMOUNT_LENGTH) { "amount must fit in $MAX_AMOUNT_LENGTH characters after rounding" }
        return text
    }

    /** Parses tag 54 content (`digits[.digits]`, ASCII only); `null` for anything else. */
    fun parse(text: String): BigDecimal? {
        if (text.isEmpty()) return null
        var dots = 0
        for (c in text) {
            if (c == '.') dots++ else if (!Tlv.isDigit(c)) return null
        }
        if (dots > 1 || text.startsWith('.') || text.endsWith('.')) return null
        return BigDecimal(text)
    }
}
