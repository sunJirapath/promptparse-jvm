package io.github.sunjirapath.promptparse

/** Id of the CRC tag. It is derived data: never stored in [EmvcoQr], always recomputed by `encode()`. */
internal const val CRC_TAG_ID: String = "63"

/** Largest value a 2-digit TLV length field can describe. */
internal const val MAX_VALUE_LENGTH: Int = 99

/**
 * TLV plumbing shared by [Tag], [EmvcoQr] and [PromptParse].
 *
 * Lengths are counted in UTF-8 bytes (SPEC_NOTES Q2): that is what the CRC covers and what the
 * reference's non-ASCII vector declares. For ASCII text it is identical to `String.length`.
 */
internal object Tlv {

    /** Mirrors the reference's `^\d{4}.+`: 4 ASCII digits followed by at least one non-line-terminator. */
    fun looksLikeTlv(s: String): Boolean {
        if (s.length < 5) return false
        for (i in 0 until 4) if (!isDigit(s[i])) return false
        return !isLineTerminator(s[4])
    }

    private fun isScannerWhitespace(c: Char): Boolean = c == ' ' || c == '\t' || c == '\r' || c == '\n'

    /** Drops the spaces, tabs and line breaks a scanner or a copy-paste leaves around a payload. */
    fun trimScan(s: String): String = s.trim(::isScannerWhitespace)

    /**
     * Like [trimScan] at the start, but at the end only line breaks: a payload without a CRC may end in a
     * value that legitimately ends with a space.
     */
    fun trimScanLenient(s: String): String = s.trimStart(::isScannerWhitespace).trimEnd('\r', '\n')

    fun isDigit(c: Char): Boolean = c in '0'..'9'

    private fun isLineTerminator(c: Char): Boolean =
        c == '\n' || c == '\r' || c == '\u2028' || c == '\u2029'

    fun isTagId(id: String): Boolean = id.length == 2 && isDigit(id[0]) && isDigit(id[1])

    /** UTF-8 size of the code point starting at [i]. A lone surrogate counts as U+FFFD (3 bytes), like the reference. */
    private fun utf8Size(s: String, i: Int): Int {
        val c = s[i]
        return when {
            c.code < 0x80 -> 1
            c.code < 0x800 -> 2
            c.isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate() -> 4
            else -> 3
        }
    }

    fun utf8Length(s: String): Int {
        var n = 0
        var i = 0
        while (i < s.length) {
            val size = utf8Size(s, i)
            n += size
            i += if (size == 4) 2 else 1
        }
        return n
    }

    fun utf8Bytes(s: String): ByteArray {
        val out = ByteArray(utf8Length(s))
        var o = 0
        var i = 0
        while (i < s.length) {
            val size = utf8Size(s, i)
            var cp = s[i].code
            if (size == 4) cp = Character.toCodePoint(s[i], s[i + 1])
            else if (s[i].isSurrogate()) cp = 0xFFFD
            when (size) {
                1 -> out[o++] = cp.toByte()
                2 -> {
                    out[o++] = (0xC0 or (cp shr 6)).toByte()
                    out[o++] = (0x80 or (cp and 0x3F)).toByte()
                }
                3 -> {
                    out[o++] = (0xE0 or (cp shr 12)).toByte()
                    out[o++] = (0x80 or ((cp shr 6) and 0x3F)).toByte()
                    out[o++] = (0x80 or (cp and 0x3F)).toByte()
                }
                else -> {
                    out[o++] = (0xF0 or (cp shr 18)).toByte()
                    out[o++] = (0x80 or ((cp shr 12) and 0x3F)).toByte()
                    out[o++] = (0x80 or ((cp shr 6) and 0x3F)).toByte()
                    out[o++] = (0x80 or (cp and 0x3F)).toByte()
                }
            }
            i += if (size == 4) 2 else 1
        }
        return out
    }

    /** Index just past [byteLen] UTF-8 bytes of [s] starting at [from], or -1 if that runs past the end or splits a character. */
    private fun advance(s: String, from: Int, byteLen: Int): Int {
        var i = from
        var consumed = 0
        while (consumed < byteLen) {
            if (i >= s.length) return -1
            val size = utf8Size(s, i)
            consumed += size
            i += if (size == 4) 2 else 1
        }
        return if (consumed == byteLen) i else -1
    }

    fun encode(tags: List<Tag>): String {
        val sb = StringBuilder()
        for (t in tags) {
            sb.append(t.id)
            val len = utf8Length(t.value)
            if (len < 10) sb.append('0')
            sb.append(len)
            sb.append(t.value)
        }
        return sb.toString()
    }

    /**
     * Splits [payload] into tags (no sub-tags).
     *
     * A tag is malformed when its id is not 2 digits, its length is not 2 digits, or its value is
     * truncated / ends inside a multi-byte character. [strict] turns that into `null`; otherwise
     * decoding stops there and the tags read so far are returned.
     */
    fun decode(payload: String, strict: Boolean): List<Tag>? {
        val tags = ArrayList<Tag>()
        var idx = 0
        while (idx < payload.length) {
            val end = readTag(payload, idx, tags)
            if (end < 0) return if (strict) null else tags
            idx = end
        }
        return tags
    }

    /** Reads one tag at [idx] into [out] and returns the index after it, or -1 if malformed. */
    private fun readTag(s: String, idx: Int, out: MutableList<Tag>): Int {
        if (idx + 4 > s.length) return -1
        if (!isDigit(s[idx]) || !isDigit(s[idx + 1]) || !isDigit(s[idx + 2]) || !isDigit(s[idx + 3])) return -1
        val len = (s[idx + 2] - '0') * 10 + (s[idx + 3] - '0')
        val end = advance(s, idx + 4, len)
        if (end < 0) return -1
        out.add(Tag(s.substring(idx, idx + 2), s.substring(idx + 4, end), emptyList()))
        return end
    }

    /**
     * Sub-tags of [value], by the reference's rule (`parser.ts:30-43`): the value must look like TLV
     * and decode cleanly over its whole length with no empty sub-tag. One level only.
     */
    fun deriveSubTags(value: String): List<Tag> {
        if (!looksLikeTlv(value)) return emptyList()
        val subs = decode(value, strict = true) ?: return emptyList()
        return if (subs.isNotEmpty() && subs.all { it.value.isNotEmpty() }) subs else emptyList()
    }

    /** CRC-16/CCITT-FALSE over the UTF-8 bytes of [payload] (which must already end in `6304`), as 4 uppercase hex digits. */
    fun checksum(payload: String): String {
        var crc = 0xFFFF
        for (b in utf8Bytes(payload)) {
            crc = crc xor ((b.toInt() and 0xFF) shl 8)
            repeat(8) {
                crc = if (crc and 0x8000 != 0) (crc shl 1) xor 0x1021 else crc shl 1
            }
            crc = crc and 0xFFFF
        }
        return crc.toString(16).uppercase().padStart(4, '0')
    }

    /** True if the last 4 chars of [payload] are the CRC of everything before them (hex digits compared ASCII-case-insensitively). */
    fun crcMatches(payload: String): Boolean {
        if (payload.length < 4) return false
        return hexEquals(payload.substring(payload.length - 4), checksum(payload.substring(0, payload.length - 4)))
    }

    /** [given] equals the uppercase [expected] hex, ignoring ASCII case in [given]. */
    private fun hexEquals(given: String, expected: String): Boolean {
        if (given.length != expected.length) return false
        for (i in given.indices) {
            val g = given[i]
            if ((if (g in 'a'..'f') g - 32 else g) != expected[i]) return false
        }
        return true
    }

    /** A payload split at its trailing CRC tag. [body] is everything before the tag; [repaired] is the payload with a full 4-digit CRC. */
    class CrcSplit(val body: String, val repaired: String, val truncated: Boolean)

    /**
     * Finds the CRC tag [tagId] at the very end of [payload] and checks its checksum.
     *
     * With [autoFix], a CRC with 1-3 hex digits (some bank apps drop leading zeros) is left-padded with
     * `0` and accepted if it then matches. Candidates are tried by position from the end, so a CRC whose
     * own digits spell the tag header (e.g. `9104` + `9104`) is still found.
     */
    fun splitCrc(payload: String, tagId: String, autoFix: Boolean): CrcSplit? {
        val header = tagId + "04"
        for (tail in 4 downTo (if (autoFix) 1 else 4)) {
            val start = payload.length - tail - header.length
            if (start < 0 || !payload.startsWith(header, start)) continue
            val seed = payload.substring(0, start + header.length)
            val padded = payload.substring(seed.length).padStart(4, '0')
            if (hexEquals(padded, checksum(seed))) return CrcSplit(payload.substring(0, start), seed + padded, tail < 4)
        }
        return null
    }
}
