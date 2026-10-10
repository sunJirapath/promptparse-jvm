package io.github.sunjirapath.promptparse

/**
 * One TLV element: a 2-digit [id], its [value] and, for template tags such as 29 and 30, the
 * [subTags] the value decodes to.
 *
 * Immutable. When [subTags] is not empty, [value] must be exactly their encoding, so the two can
 * never disagree. The TLV length is not stored: it is the UTF-8 byte length of [value] and is at
 * most 99.
 *
 * @throws IllegalArgumentException if [id] is not 2 ASCII digits, [value] is longer than 99 UTF-8
 *   bytes, or [subTags] do not encode to [value].
 */
public class Tag @JvmOverloads public constructor(
    public val id: String,
    public val value: String,
    subTags: List<Tag> = emptyList(),
) {
    /** Sub-tags of a template tag, or an empty list. Unmodifiable. */
    public val subTags: List<Tag> = if (subTags.isEmpty()) emptyList() else subTags.toList()

    init {
        require(Tlv.isTagId(id)) { "Tag id must be 2 ASCII digits" }
        val length = Tlv.utf8Length(value)
        require(length <= MAX_VALUE_LENGTH) {
            "Value of tag $id is $length UTF-8 bytes; a TLV length holds at most $MAX_VALUE_LENGTH"
        }
        require(this.subTags.isEmpty() || Tlv.encode(this.subTags) == value) {
            "Value of tag $id does not match the encoding of its sub-tags"
        }
    }

    override fun equals(other: Any?): Boolean =
        this === other || (other is Tag && id == other.id && value == other.value && subTags == other.subTags)

    override fun hashCode(): Int = (id.hashCode() * 31 + value.hashCode()) * 31 + subTags.hashCode()

    override fun toString(): String =
        if (subTags.isEmpty()) "Tag(id=$id, value=$value)" else "Tag(id=$id, subTags=$subTags)"

    public companion object {
        /** A tag whose sub-tags are derived from [value] by the same rule [PromptParse.parse] uses. */
        @JvmStatic
        public fun of(id: String, value: String): Tag = Tag(id, value, Tlv.deriveSubTags(value))

        /** A template tag whose value is the encoding of [subTags]. */
        @JvmStatic
        public fun template(id: String, subTags: List<Tag>): Tag = Tag(id, Tlv.encode(subTags), subTags)
    }
}
