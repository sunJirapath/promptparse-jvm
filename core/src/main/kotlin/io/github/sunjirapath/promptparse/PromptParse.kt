package io.github.sunjirapath.promptparse

/** Entry point for parsing and checking EMVCo-style QR payloads. */
public object PromptParse {
    /**
     * Parses any EMVCo-compatible QR payload.
     *
     * Never throws. Surrounding whitespace from a scanner is ignored (strict: spaces, tabs and line breaks on
     * both sides; lenient: leading whitespace and trailing line breaks), so `encode()` and every other
     * result reflect the trimmed payload. Returns `null` when [payload] is not TLV at all (it must start with 4 digits and
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
    public fun parse(
        payload: String,
        strict: Boolean = false,
        subTags: Boolean = true,
    ): EmvcoQr? {
        val text = if (strict) Tlv.trimScan(payload) else Tlv.trimScanLenient(payload)
        if (!Tlv.looksLikeTlv(text)) return null
        if (strict && !Tlv.crcMatches(text)) return null
        val decoded = Tlv.decode(text, strict) ?: return null
        if (decoded.isEmpty()) return null
        val tags =
            decoded.filter { it.id != CRC_TAG_ID }.map {
                if (subTags) Tag.of(it.id, it.value) else it
            }
        return EmvcoQr(tags)
    }

    /**
     * Restores a payment QR whose `63` CRC was shortened to 1-3 hex digits (some bank apps drop leading
     * zeros): returns the payload with the CRC left-padded to 4 digits if that makes it valid, otherwise `null`.
     * A payload whose CRC is already complete also gives `null`. [parse] never repairs; call this first if wanted.
     * Never throws.
     */
    @JvmStatic
    public fun fixTruncatedCrc(payload: String): String? =
        Tlv.splitCrc(Tlv.trimScan(payload), CRC_TAG_ID, autoFix = true)?.takeIf { it.truncated }?.repaired

    /**
     * True if [payload] ends in a `6304` CRC tag whose 4 hex digits (either case) are the
     * CRC-16/CCITT-FALSE of everything before them. Never throws.
     */
    @JvmStatic
    public fun validateCrc(payload: String): Boolean {
        val text = Tlv.trimScan(payload)
        return text.length >= 8 && text.startsWith("6304", text.length - 8) && Tlv.crcMatches(text)
    }
}
