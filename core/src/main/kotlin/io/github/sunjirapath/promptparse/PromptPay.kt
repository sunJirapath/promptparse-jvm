package io.github.sunjirapath.promptparse

import java.math.BigDecimal

private const val NATID_LENGTH = 13
private const val EWALLET_LENGTH = 15
private const val BILLER_ID_SHORT = 13
private const val BILLER_ID_LONG = 15

/** `0066` + 9 digits. */
private const val MSISDN_WIRE_LENGTH = 13

/** `66` + 9 digits. */
private const val MSISDN_INTERNATIONAL_LENGTH = 11
internal const val AID_ANY_ID = "A000000677010111"
private const val AID_BILL_PAYMENT = "A000000677010112"

/** Generates and reads Thai PromptPay QR payloads. */
public object PromptPay {
    /**
     * PromptPay AnyID (tag 29) QR.
     *
     * [target] by [type]:
     * - `MSISDN`: `0812345678`, `081-234-5678`, `+66812345678`, `66812345678` or `0066812345678`
     *   (written as `0066812345678`).
     * - `NATID`: 13 digits (spaces and dashes are ignored; the check digit is not verified).
     * - `EWALLETID`: 15 digits.
     *
     * A `null` [amount] gives a static QR (tag 01 = `11`); otherwise a dynamic one (`12`) with the
     * amount rounded `HALF_UP` to 2 decimals.
     *
     * @throws IllegalArgumentException if [target] does not fit [type], or [amount] is not above 0 once rounded.
     */
    @JvmStatic
    @JvmOverloads
    public fun anyId(
        type: AnyIdType,
        target: String,
        amount: BigDecimal? = null,
    ): String {
        val account =
            Tag.template(
                "29",
                listOf(Tag("00", AID_ANY_ID), Tag(type.subTagId, normalizeTarget(type, target))),
            )
        return merchantQr(account, amount, emptyList())
    }

    /**
     * PromptPay Bill Payment (tag 30) QR.
     *
     * [billerId] is 13 digits (tax ID) or 15 (tax ID + suffix). [ref1] is required; [ref2] and
     * [ref3] (the reference's "undocumented" tag 62/07) are left out when `null` or empty. Amount as in [anyId].
     *
     * @throws IllegalArgumentException on a malformed [billerId], an empty [ref1], an invalid [amount], or a field too long for its tag.
     */
    @JvmStatic
    @JvmOverloads
    public fun billPayment(
        billerId: String,
        ref1: String,
        ref2: String? = null,
        amount: BigDecimal? = null,
        ref3: String? = null,
    ): String {
        require(isValidBillerId(billerId)) {
            "billerId must be 13 or 15 digits"
        }
        require(ref1.isNotEmpty()) { "ref1 must not be empty" }
        val subTags = mutableListOf(Tag("00", AID_BILL_PAYMENT), Tag("01", billerId), Tag("02", ref1))
        if (!ref2.isNullOrEmpty()) subTags.add(Tag("03", ref2))
        val extra = if (ref3.isNullOrEmpty()) emptyList() else listOf(Tag.template("62", listOf(Tag("07", ref3))))
        return merchantQr(Tag.template("30", subTags), amount, extra)
    }

    /**
     * Reads a PromptPay AnyID QR. Returns `null` unless the payload parses strictly (valid CRC), is
     * a `00 = 01` payload with the AnyID AID in tag 29, and carries a recipient sub-tag (01, 02 or 03).
     * An MSISDN written as `0066xxxxxxxxx` is returned as `0xxxxxxxxx`. Never throws.
     *
     * [validateFields] additionally requires a recipient in its documented wire shape, initiation
     * method 11/12, currency 764, country TH, and a positive amount of at most 2 decimals within
     * the generator's limits when present. National ID check digits and bank registration are not checked.
     */
    @JvmStatic
    @JvmOverloads
    public fun parseAnyId(
        payload: String,
        validateFields: Boolean = false,
    ): AnyIdInfo? {
        val qr = PromptParse.parse(payload, strict = true) ?: return null
        if (qr.value("00") != "01" || qr.value("29", "00") != AID_ANY_ID) return null
        val type = AnyIdType.entries.firstOrNull { qr.tag("29", it.subTagId) != null } ?: return null
        var target = qr.value("29", type.subTagId)!!
        if (validateFields && (!hasValidPaymentFields(qr) || !isValidTarget(type, target))) return null
        if (type == AnyIdType.MSISDN && target.length == MSISDN_WIRE_LENGTH && target.startsWith("0066")) {
            target = "0" + target.substring(4)
        }
        val amount = readAmount(qr) ?: return null
        return AnyIdInfo(type, target, amount.value)
    }

    /**
     * Reads a PromptPay Bill Payment QR. Returns `null` unless the payload parses strictly, has the
     * Bill Payment AID in tag 30 and non-empty billerId (30/01) and ref1 (30/02). Never throws.
     * [validateFields] applies the payment-field checks in [parseAnyId] and requires a 13/15-digit biller ID.
     */
    @JvmStatic
    @JvmOverloads
    public fun parseBillPayment(
        payload: String,
        validateFields: Boolean = false,
    ): BillPaymentInfo? {
        val qr = PromptParse.parse(payload, strict = true) ?: return null
        if (qr.value("00") != "01" || qr.value("30", "00") != AID_BILL_PAYMENT) return null
        val billerId = qr.value("30", "01")?.takeIf { it.isNotEmpty() } ?: return null
        val ref1 = qr.value("30", "02")?.takeIf { it.isNotEmpty() } ?: return null
        if (validateFields && (!hasValidPaymentFields(qr) || !isValidBillerId(billerId))) return null
        val amount = readAmount(qr) ?: return null
        return BillPaymentInfo(billerId, ref1, qr.value("30", "03"), qr.value("62", "07"), amount.value)
    }

    internal class Amount(
        val value: BigDecimal?,
    )

    /** `Amount(null)` when there is no tag 54, `null` when tag 54 is present but unreadable. */
    internal fun readAmount(qr: EmvcoQr): Amount? {
        val text = qr.value("54") ?: return Amount(null)
        return Amounts.parse(text)?.let(::Amount)
    }

    internal fun hasValidPaymentFields(qr: EmvcoQr): Boolean =
        qr.value("01") in listOf("11", "12") && qr.value("53") == "764" && qr.value("58") == "TH" &&
            (qr.value("54")?.let(Amounts::isValid) ?: true)

    private fun isValidTarget(
        type: AnyIdType,
        target: String,
    ): Boolean {
        val length =
            when (type) {
                AnyIdType.MSISDN -> MSISDN_WIRE_LENGTH
                AnyIdType.NATID -> NATID_LENGTH
                AnyIdType.EWALLETID -> EWALLET_LENGTH
            }
        return target.length == length && target.all(Tlv::isDigit) &&
            (type != AnyIdType.MSISDN || target.startsWith("0066"))
    }

    private fun isValidBillerId(billerId: String): Boolean =
        billerId.length in setOf(BILLER_ID_SHORT, BILLER_ID_LONG) && billerId.all(Tlv::isDigit)

    /** Tags 00, 01, [account], 53, 58, [54], then [extra] (the order the reference's golden vectors use). */
    internal fun merchantQr(
        account: Tag,
        amount: BigDecimal?,
        extra: List<Tag>,
    ): String {
        val tags =
            mutableListOf(
                Tag("00", "01"),
                Tag("01", if (amount == null) "11" else "12"),
                account,
                Tag("53", "764"),
                Tag("58", "TH"),
            )
        // The reference puts 54 after 58, and its golden vectors depend on that order.
        if (amount != null) tags.add(Tag("54", Amounts.format(amount)))
        tags.addAll(extra)
        return EmvcoQr(tags).encode()
    }

    private fun normalizeTarget(
        type: AnyIdType,
        target: String,
    ): String {
        val compact = target.filter { it != ' ' && it != '-' }
        return when (type) {
            AnyIdType.MSISDN -> normalizeMobile(compact)
            AnyIdType.NATID -> digits(compact, NATID_LENGTH, "NATID")
            AnyIdType.EWALLETID -> digits(compact, EWALLET_LENGTH, "EWALLETID")
        }
    }

    private fun digits(
        s: String,
        count: Int,
        name: String,
    ): String {
        require(s.length == count && s.all(Tlv::isDigit)) { "$name must be $count digits" }
        return s
    }

    /** `0066` + the 9 national digits, from any accepted spelling of a Thai mobile number. */
    internal fun normalizeMobile(compact: String): String {
        val plus = compact.startsWith("+")
        val s = if (plus) compact.substring(1) else compact
        val national =
            when {
                !s.all(Tlv::isDigit) -> null
                !plus && s.length == 10 && s.startsWith("0") -> s.substring(1)
                s.length == MSISDN_INTERNATIONAL_LENGTH && s.startsWith("66") -> s.substring(2)
                !plus && s.length == MSISDN_WIRE_LENGTH && s.startsWith("0066") -> s.substring(4)
                else -> null
            }
        requireNotNull(national) {
            "MSISDN must be a Thai mobile number such as " +
                "0812345678, 081-234-5678, +66812345678, 66812345678 or 0066812345678"
        }
        return "0066$national"
    }
}

/** Contents of a PromptPay AnyID QR. [amount] is `null` for a static QR. */
public data class AnyIdInfo(
    public val type: AnyIdType,
    public val target: String,
    public val amount: BigDecimal?,
)

/** Contents of a PromptPay Bill Payment QR. [ref2], [ref3] and [amount] are `null` when absent. */
public data class BillPaymentInfo(
    public val billerId: String,
    public val ref1: String,
    public val ref2: String?,
    public val ref3: String?,
    public val amount: BigDecimal?,
)
