package io.github.sunJirapath.promptparse

/**
 * An EMVCo merchant-presented QR payload as an ordered list of top-level [tags].
 *
 * The CRC tag (63) is not data: it is never kept here and [encode] always recomputes it. For any
 * `qr` made by [PromptParse.parse] or built from [Tag.of] / [Tag.template] tags,
 * `PromptParse.parse(qr.encode()) == qr`.
 *
 * Immutable and thread-safe.
 *
 * @throws IllegalArgumentException if [tags] contains tag 63.
 */
public class EmvcoQr(tags: List<Tag>) {
    public val tags: List<Tag> = tags.toList()

    init {
        require(this.tags.none { it.id == CRC_TAG_ID }) {
            "Tag $CRC_TAG_ID is the CRC and is computed by encode(); it cannot be part of the tags"
        }
    }

    /** First top-level tag with [id], or the first sub-tag [subId] of it when given; `null` if absent. */
    @JvmOverloads
    public fun tag(id: String, subId: String? = null): Tag? {
        val top = tags.firstOrNull { it.id == id } ?: return null
        return if (subId == null) top else top.subTags.firstOrNull { it.id == subId }
    }

    /** Value of [tag], or `null` if absent. */
    @JvmOverloads
    public fun value(id: String, subId: String? = null): String? = tag(id, subId)?.value

    /**
     * A copy with the first tag [id] given the new [value] (sub-tags are re-derived from it), or
     * the tag appended if there is none.
     *
     * @throws IllegalArgumentException if [id] is not 2 digits, is the CRC tag, or [value] is longer than 99 UTF-8 bytes.
     */
    public fun with(id: String, value: String): EmvcoQr {
        require(id != CRC_TAG_ID) { "Tag $CRC_TAG_ID is the CRC and is computed by encode()" }
        val replacement = Tag.of(id, value)
        val i = tags.indexOfFirst { it.id == id }
        return EmvcoQr(if (i < 0) tags + replacement else tags.toMutableList().also { it[i] = replacement })
    }

    /** The payload string: all [tags] in order, then the CRC tag `63` with a freshly computed uppercase checksum. */
    public fun encode(): String {
        val body = Tlv.encode(tags) + CRC_TAG_ID + "04"
        return body + Tlv.checksum(body)
    }

    override fun equals(other: Any?): Boolean = this === other || (other is EmvcoQr && tags == other.tags)

    override fun hashCode(): Int = tags.hashCode()

    override fun toString(): String = "EmvcoQr(tags=$tags)"
}
