package io.github.sunjirapath.promptparse

import java.math.BigDecimal

private const val TRUEMONEY_PREFIX = "14"

/**
 * TrueMoney Wallet payment QR. It is a PromptPay AnyID (tag 29) QR whose e-wallet sub-tag 03 is `14` plus the
 * mobile number left-padded with zeros to 13 digits. The optional personal message travels in tag 81 and is
 * shown only inside the TrueMoney app; other apps ignore it.
 */
public object TrueMoney {

    /**
     * @param mobile Thai mobile number in any form [PromptPay.anyId] accepts for `MSISDN`.
     * @param amount `null` for a static QR; otherwise above 0, rounded `HALF_UP` to 2 decimals.
     * @param message personal message (tag 81, UTF-16BE hex); `null` or empty for none, at most 24 UTF-16 units.
     * @throws IllegalArgumentException on an invalid mobile, amount or message.
     */
    @JvmStatic
    @JvmOverloads
    public fun build(mobile: String, amount: BigDecimal? = null, message: String? = null): String {
        val local = "0" + PromptPay.normalizeMobile(mobile.filter { it != ' ' && it != '-' }).substring(4)
        val account = Tag.template(
            "29",
            listOf(Tag("00", AID_ANY_ID), Tag("03", TRUEMONEY_PREFIX + local.padStart(13, '0'))),
        )
        val extra = if (message.isNullOrEmpty()) emptyList() else listOf(Tag("81", encodeMessage(message)))
        return PromptPay.merchantQr(account, amount, extra)
    }

    /**
     * Reads a TrueMoney QR. `null` unless it parses strictly (valid CRC), is a `00 = 01` payload with the
     * PromptPay AID in tag 29 and a `14`-prefixed 15-character sub-tag 03. A message that is not valid
     * UTF-16BE hex is ignored. Never throws.
     */
    @JvmStatic
    public fun parse(payload: String): TrueMoneyInfo? {
        val qr = PromptParse.parse(payload, strict = true) ?: return null
        if (qr.value("00") != "01" || qr.value("29", "00") != AID_ANY_ID) return null
        val wire = qr.value("29", "03") ?: return null
        if (wire.length != 15 || !wire.startsWith(TRUEMONEY_PREFIX) || !wire.all(Tlv::isDigit)) return null
        val amount = PromptPay.readAmount(qr) ?: return null
        val mobile = "0" + wire.substring(TRUEMONEY_PREFIX.length).trimStart('0')
        return TrueMoneyInfo(mobile, amount.value, qr.value("81")?.let(::decodeMessage))
    }

    private fun encodeMessage(message: String): String {
        val sb = StringBuilder(message.length * 4)
        for (c in message) sb.append(c.code.toString(16).uppercase().padStart(4, '0'))
        return sb.toString()
    }

    private fun decodeMessage(hex: String): String? {
        if (hex.isEmpty() || hex.length % 4 != 0) return null
        val sb = StringBuilder(hex.length / 4)
        for (i in hex.indices step 4) {
            val unit = hex.substring(i, i + 4).toIntOrNull(16) ?: return null
            if (hex[i] == '+' || hex[i] == '-') return null
            sb.append(unit.toChar())
        }
        return sb.toString()
    }
}

/** Contents of a TrueMoney QR. [mobile] is local form (`0801111111`); [amount] and [message] are `null` when absent. */
public data class TrueMoneyInfo(
    public val mobile: String,
    public val amount: BigDecimal?,
    public val message: String?,
)
