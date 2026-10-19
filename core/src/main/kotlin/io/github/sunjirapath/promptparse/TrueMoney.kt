package io.github.sunjirapath.promptparse

import java.math.BigDecimal
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

private const val TRUEMONEY_PREFIX = "14"

/** Mobile numbers are zero-padded to this width after the prefix; prefix + number is [WIRE_LENGTH]. */
private const val MOBILE_PAD = 13
private const val WIRE_LENGTH = 15
private const val LOCAL_MOBILE_LENGTH = 10
private const val MAX_MESSAGE_UNITS = 24

/**
 * TrueMoney Wallet payment QR. It is a PromptPay AnyID (tag 29) QR whose e-wallet sub-tag 03 is `14` plus the
 * mobile number left-padded with zeros to 13 digits. The optional personal message travels in tag 81 and is
 * shown only inside the TrueMoney app; other apps ignore it.
 */
public object TrueMoney {
    /**
     * @param mobile Thai mobile number in any form [PromptPay.anyId] accepts for `MSISDN`.
     * @param amount `null` for a static QR; otherwise above 0, rounded `HALF_UP` to 2 decimals.
     * @param message well-formed Unicode message (tag 81, UTF-16BE hex); `null` or empty for none, at most 24 UTF-16 units.
     * @throws IllegalArgumentException on an invalid mobile, amount or message.
     */
    @JvmStatic
    @JvmOverloads
    public fun build(
        mobile: String,
        amount: BigDecimal? = null,
        message: String? = null,
    ): String {
        val local = "0" + PromptPay.normalizeMobile(mobile.filter { it != ' ' && it != '-' }).substring(4)
        val account =
            Tag.template(
                "29",
                listOf(Tag("00", AID_ANY_ID), Tag("03", TRUEMONEY_PREFIX + local.padStart(MOBILE_PAD, '0'))),
            )
        val extra = if (message.isNullOrEmpty()) emptyList() else listOf(Tag("81", encodeMessage(message)))
        return PromptPay.merchantQr(account, amount, extra)
    }

    /**
     * Reads a TrueMoney QR. `null` unless it parses strictly (valid CRC), is a `00 = 01` payload with the
     * PromptPay AID in tag 29 and a `14`-prefixed 15-character sub-tag 03. A message that is not valid
     * UTF-16BE hex is ignored. Never throws.
     * [validateFields] applies the payment-field checks in [PromptPay.parseAnyId] and requires
     * the wallet's fixed padding and local mobile prefix to be zeros.
     */
    @JvmStatic
    @JvmOverloads
    public fun parse(
        payload: String,
        validateFields: Boolean = false,
    ): TrueMoneyInfo? {
        val qr = PromptParse.parse(payload, strict = true) ?: return null
        if (qr.value("00") != "01" || qr.value("29", "00") != AID_ANY_ID) return null
        val wire = qr.value("29", "03") ?: return null
        if (wire.length != WIRE_LENGTH || !wire.startsWith(TRUEMONEY_PREFIX) || !wire.all(Tlv::isDigit)) return null
        if (validateFields && (!PromptPay.hasValidPaymentFields(qr) || !wire.startsWith("140000"))) return null
        val amount = PromptPay.readAmount(qr) ?: return null
        val mobile = wire.takeLast(LOCAL_MOBILE_LENGTH)
        return TrueMoneyInfo(mobile, amount.value, qr.value("81")?.let(::decodeMessage))
    }

    private fun encodeMessage(message: String): String {
        require(message.length <= MAX_MESSAGE_UNITS) { "message must be at most $MAX_MESSAGE_UNITS UTF-16 units" }
        require(Charsets.UTF_16BE.newEncoder().canEncode(message)) { "message must be well-formed UTF-16" }
        val sb = StringBuilder(message.length * 4)
        for (c in message) {
            sb.append(
                c.code
                    .toString(16)
                    .uppercase()
                    .padStart(4, '0'),
            )
        }
        return sb.toString()
    }

    private fun decodeMessage(hex: String): String? {
        if (hex.isEmpty() || hex.length % 4 != 0) return null
        if (hex.any { it !in '0'..'9' && it !in 'a'..'f' && it !in 'A'..'F' }) return null
        val bytes = ByteArray(hex.length / 2) { i -> hex.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
        return try {
            Charsets.UTF_16BE
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            null
        }
    }
}

/** Contents of a TrueMoney QR. [mobile] is local form (`0801111111`); [amount] and [message] are `null` when absent. */
public data class TrueMoneyInfo(
    public val mobile: String,
    public val amount: BigDecimal?,
    public val message: String?,
)
