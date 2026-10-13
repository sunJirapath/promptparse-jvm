package io.github.sunjirapath.promptparse

import java.math.BigDecimal
import java.math.BigInteger

private const val BILLER_ID_LENGTH = 15
private const val MAX_SATANG_DIGITS = 12 // 9,999,999,999.99 baht

/**
 * Bank of Thailand 1D bill-payment barcode: `|billerId\rref1\rref2\ramount`, where amount is whole satang
 * (`0` when the cashier keys it in). It is not TLV and has no checksum.
 */
public object BotBarcode {
    /**
     * @param billerId up to 15 digits (tax ID + suffix); shorter values are left-padded with zeros.
     * @param ref1 required customer / invoice reference.
     * @param ref2 optional second reference; `null` or empty is written as nothing.
     * @param amount `null` for a counter-keyed total (`0`); otherwise above 0, rounded `HALF_UP` to satang.
     * @throws IllegalArgumentException on an invalid biller ID, empty or carriage-return-containing reference, or invalid amount.
     */
    @JvmStatic
    @JvmOverloads
    public fun build(
        billerId: String,
        ref1: String,
        ref2: String? = null,
        amount: BigDecimal? = null,
    ): String {
        require(billerId.isNotEmpty() && billerId.length <= BILLER_ID_LENGTH && billerId.all(Tlv::isDigit)) {
            "billerId must be 1 to $BILLER_ID_LENGTH digits"
        }
        require(ref1.isNotEmpty()) { "ref1 must not be empty" }
        require('\r' !in ref1 && '\r' !in (ref2 ?: "")) { "references must not contain a carriage return" }
        val satang = if (amount == null) "0" else Amounts.format(amount).replace(".", "").trimStart('0')
        return "|" + billerId.padStart(BILLER_ID_LENGTH, '0') + "\r" + ref1 + "\r" + (ref2 ?: "") + "\r" + satang
    }

    /**
     * Reads a barcode (leading whitespace and one trailing `\n` or `\r\n` from the scanner are ignored); `null` unless it starts with `|`, has exactly four `\r`-separated fields, a biller ID of at
     * least 15 characters, a non-empty ref1 and an amount that is all digits (whole satang). Never throws.
     * [validateFields] additionally requires the biller ID to be exactly 15 ASCII digits.
     */
    @JvmStatic
    @JvmOverloads
    public fun parse(
        payload: String,
        validateFields: Boolean = false,
    ): BotBarcodeInfo? {
        val text = payload.trimStart().removeSuffix("\r\n").removeSuffix("\n")
        if (!text.startsWith("|")) return null
        val parts = text.substring(1).split('\r')
        if (parts.size != 4) return null
        val billerId = parts[0]
        val ref1 = parts[1]
        val amountField = parts[3]
        if (billerId.length < BILLER_ID_LENGTH || ref1.isEmpty()) return null
        if (validateFields && (billerId.length != BILLER_ID_LENGTH || !billerId.all(Tlv::isDigit))) return null
        val validAmount = amountField.length in 1..MAX_SATANG_DIGITS && amountField.all(Tlv::isDigit)
        if (!validAmount) return null
        val satang = BigInteger(amountField)
        val amount = if (satang.signum() == 0) null else BigDecimal(satang, 2)
        return BotBarcodeInfo(billerId, ref1, parts[2].ifEmpty { null }, amount)
    }
}

/** Contents of a BOT barcode. [ref2] and [amount] are `null` when absent. */
public data class BotBarcodeInfo(
    public val billerId: String,
    public val ref1: String,
    public val ref2: String?,
    public val amount: BigDecimal?,
) {
    /**
     * The equivalent PromptPay Bill Payment (tag 30) QR. Works for billers whose bank accepts it.
     *
     * @throws IllegalArgumentException if [billerId] is not 13 or 15 digits.
     */
    public fun toBillPayment(): String = PromptPay.billPayment(billerId, ref1, ref2, amount)
}
