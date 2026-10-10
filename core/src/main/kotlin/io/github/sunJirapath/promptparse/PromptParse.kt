package io.github.sunJirapath.promptparse

/** Entry point for parsing and checking EMVCo-style QR payloads. */
public object PromptParse {

    /**
     * Parses any EMVCo-compatible QR payload.
     *
     * Never throws. Returns `null` when [payload] is not TLV at all (it must start with 4 digits and
     * have at least 5 chars) or decodes to no tag.
     *
     * - **Lenient** (default): a malformed or truncated tag ends decoding and the tags before it are kept.
     * - **[strict]**: the payload must be well-formed TLV end to end and its last 4 characters must be
     *   the CRC of everything before them (either hex case accepted). Required-tag checks are the
     *   job of [PromptPay.parseAnyId] / [PromptPay.parseBillPayment], as in the reference.
     *
     * With [subTags], values that decode cleanly as TLV (templates such as 29, 30, 62) get [Tag.subTags].
     * Tag 63 is dropped from the result.
     */
    @JvmStatic
    @JvmOverloads
    public fun parse(payload: String, strict: Boolean = false, subTags: Boolean = true): EmvcoQr? {
        if (!Tlv.looksLikeTlv(payload)) return null
        if (strict && !Tlv.crcMatches(payload)) return null
        val decoded = Tlv.decode(payload, strict) ?: return null
        if (decoded.isEmpty()) return null
        val tags = decoded.filter { it.id != CRC_TAG_ID }.map {
            if (subTags) Tag.of(it.id, it.value) else it
        }
        return EmvcoQr(tags)
    }

    /**
     * True if [payload] ends in a `6304` CRC tag whose 4 hex digits (either case) are the
     * CRC-16/CCITT-FALSE of everything before them. Never throws.
     */
    @JvmStatic
    public fun validateCrc(payload: String): Boolean =
        payload.length >= 8 && payload.startsWith("6304", payload.length - 8) && Tlv.crcMatches(payload)
}
