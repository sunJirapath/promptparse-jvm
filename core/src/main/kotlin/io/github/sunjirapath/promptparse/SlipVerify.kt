package io.github.sunjirapath.promptparse

private const val SLIP_CRC_TAG = "91"
private const val SLIP_API_TYPE = "000001"
private const val TRUEMONEY_MARKER = "01"

/**
 * Slip Verify "mini QR": the small QR printed on a transfer slip, used to look a transaction up through
 * a bank's Open API. It is not a payment QR: root tag `00` holds the data, the CRC lives in tag `91`.
 *
 * Two variants exist and each parser returns `null` for the other:
 * - standard (Bank of Thailand): `00{00=000001, 01=sending bank, 02=transaction ref}`, `51=TH`, CRC uppercase;
 * - TrueMoney: `00{00=01, 01=01, 02=event type, 03=transaction id, 04=DDMMYYYY}`, CRC lowercase.
 *
 * Parsers never throw and ignore surrounding whitespace. By default they repair a CRC that a bank app shortened by dropping leading zeros
 * (1-3 hex digits left); pass `crcAutoFix = false` to refuse that. Builders never shorten the CRC.
 */
public object SlipVerify {

    /**
     * Standard slip verify QR.
     *
     * @param sendingBank 3-digit Bank of Thailand bank code, e.g. `002`.
     * @param transRef transaction reference printed on the slip.
     * @throws IllegalArgumentException if [sendingBank] is not 3 digits, [transRef] is empty, or a field is too long.
     */
    @JvmStatic
    public fun build(sendingBank: String, transRef: String): String {
        require(sendingBank.length == 3 && sendingBank.all(Tlv::isDigit)) { "sendingBank must be a 3-digit bank code" }
        require(transRef.isNotEmpty()) { "transRef must not be empty" }
        val root = Tag.template("00", listOf(Tag("00", SLIP_API_TYPE), Tag("01", sendingBank), Tag("02", transRef)))
        return withCrc(Tlv.encode(listOf(root, Tag("51", "TH"))), uppercase = true)
    }

    /**
     * Reads a standard slip verify QR; `null` unless it has a valid tag-91 CRC, well-formed TLV, and root tag
     * `00` with API marker `000001`, a sending bank and a transaction reference.
     * A TrueMoney slip, a PromptPay payment QR or any other payload gives `null`.
     */
    @JvmStatic
    @JvmOverloads
    public fun parse(payload: String, crcAutoFix: Boolean = true): SlipVerifyInfo? {
        val sub = rootFields(payload, crcAutoFix) ?: return null
        if (sub["00"] != SLIP_API_TYPE) return null
        val bank = sub["01"]?.takeIf { it.isNotEmpty() } ?: return null
        val ref = sub["02"]?.takeIf { it.isNotEmpty() } ?: return null
        return SlipVerifyInfo(bank, ref)
    }

    /**
     * TrueMoney slip verify QR (lowercase CRC, no tag 51).
     *
     * @param date `DDMMYYYY`, exactly 8 digits (the shape is checked, not the calendar date).
     * @throws IllegalArgumentException if [eventType] or [transactionId] is empty, [date] is malformed, or a field is too long.
     */
    @JvmStatic
    public fun buildTrueMoney(eventType: String, transactionId: String, date: String): String {
        require(eventType.isNotEmpty()) { "eventType must not be empty" }
        require(transactionId.isNotEmpty()) { "transactionId must not be empty" }
        require(date.length == 8 && date.all(Tlv::isDigit)) { "date must be 8 digits (DDMMYYYY)" }
        val root = Tag.template(
            "00",
            listOf(
                Tag("00", TRUEMONEY_MARKER), Tag("01", TRUEMONEY_MARKER),
                Tag("02", eventType), Tag("03", transactionId), Tag("04", date),
            ),
        )
        return withCrc(Tlv.encode(listOf(root)), uppercase = false)
    }

    /**
     * Reads a TrueMoney slip verify QR (CRC in either case); `null` unless both marker sub-tags (00 and 01)
     * are `01` and event type, transaction id and date are present. A standard slip gives `null`.
     */
    @JvmStatic
    @JvmOverloads
    public fun parseTrueMoney(payload: String, crcAutoFix: Boolean = true): TrueMoneySlipVerifyInfo? {
        val sub = rootFields(payload, crcAutoFix) ?: return null
        if (sub["00"] != TRUEMONEY_MARKER || sub["01"] != TRUEMONEY_MARKER) return null
        val eventType = sub["02"]?.takeIf { it.isNotEmpty() } ?: return null
        val transactionId = sub["03"]?.takeIf { it.isNotEmpty() } ?: return null
        val date = sub["04"]?.takeIf { it.isNotEmpty() } ?: return null
        return TrueMoneySlipVerifyInfo(eventType, transactionId, date)
    }

    private fun withCrc(body: String, uppercase: Boolean): String {
        val seed = body + SLIP_CRC_TAG + "04"
        val crc = Tlv.checksum(seed)
        return seed + if (uppercase) crc else crc.lowercase()
    }

    /** First value per sub-tag id of root tag `00`, after checking the CRC and that the whole body is well-formed TLV. */
    private fun rootFields(payload: String, crcAutoFix: Boolean): Map<String, String>? {
        val body = Tlv.splitCrc(Tlv.trimScan(payload), SLIP_CRC_TAG, crcAutoFix)?.body ?: return null
        val root = Tlv.decode(body, strict = true)?.firstOrNull { it.id == "00" } ?: return null
        val subTags = Tlv.decode(root.value, strict = true) ?: return null
        val fields = HashMap<String, String>()
        for (t in subTags) fields.putIfAbsent(t.id, t.value)
        return fields
    }
}

/** Contents of a standard slip verify QR. */
public data class SlipVerifyInfo(
    public val sendingBank: String,
    public val transRef: String,
)

/** Contents of a TrueMoney slip verify QR; [date] is `DDMMYYYY`. */
public data class TrueMoneySlipVerifyInfo(
    public val eventType: String,
    public val transactionId: String,
    public val date: String,
)
