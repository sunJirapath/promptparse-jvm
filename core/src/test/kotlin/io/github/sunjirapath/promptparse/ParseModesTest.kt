package io.github.sunjirapath.promptparse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParseModesTest {
    private val valid = "00020101021229370016A0000006770101110113006680111111153037645802TH540520.15630442BE"

    @Test
    fun `not TLV at all`() {
        for (s in listOf("", "0", "0002", "AAAA0000", "00x2010", "0002\n", "0002 1")) {
            assertNull(PromptParse.parse(s), "lenient: '${s.replace("\n", "\\n")}'")
            assertNull(PromptParse.parse(s, strict = true))
        }
    }

    @Test
    fun `a valid first value can contain whitespace`() {
        assertNotNull(PromptParse.parse("0001a"))
        assertNotNull(PromptParse.parse("0001 "))
        assertEquals("\r1", PromptParse.parse("0002\r1")!!.value("00"))
    }

    @Test
    fun `lenient ignores a bad CRC, strict rejects it`() {
        val bad = valid.dropLast(4) + "FFFF"
        assertNotNull(PromptParse.parse(bad))
        assertNull(PromptParse.parse(bad, strict = true))
        assertNotNull(PromptParse.parse(valid, strict = true))
    }

    @Test
    fun `strict accepts a lowercase CRC, output is always uppercase`() {
        val lower = valid.dropLast(4) + "42be"
        val qr = assertNotNull(PromptParse.parse(lower, strict = true))
        assertEquals(valid, qr.encode())
    }

    @Test
    fun `lenient keeps the tags before a truncated one, strict rejects`() {
        val truncated = "00020101021229"
        assertEquals(listOf("00", "01"), PromptParse.parse(truncated)!!.tags.map { it.id })
        val cutValue = "0002010105ab"
        assertEquals(listOf("00"), PromptParse.parse(cutValue)!!.tags.map { it.id })
        // A truncated payload can still carry a matching CRC.
        val body = "000201010512"
        assertNull(PromptParse.parse(body + Tlv.checksum(body), strict = true))
    }

    @Test
    fun `lenient stops at a non numeric length or id`() {
        assertEquals(listOf("00"), PromptParse.parse("00020101ABcdef")!!.tags.map { it.id })
        assertEquals(listOf("00"), PromptParse.parse("0002010AB2xy")!!.tags.map { it.id })
        assertEquals(listOf("00"), PromptParse.parse("0002xx02ab")!!.tags.map { it.id })
        assertNull(PromptParse.parse("0002xx02ab", strict = true))
        assertNull(PromptParse.parse("00020101ABcdef", strict = true))
    }

    @Test
    fun `strict rejects trailing bytes even with a matching CRC`() {
        val body = "00020101021" // dangling "1"
        assertNull(PromptParse.parse(body + Tlv.checksum(body), strict = true))
    }

    @Test
    fun `no tag decoded gives null`() {
        assertNull(PromptParse.parse("0005abc"))
        assertNull(PromptParse.parse("00ab01"))
    }

    @Test
    fun `tag 63 is dropped, an empty QR is allowed`() {
        val qr = PromptParse.parse(valid)!!
        assertNull(qr.tag("63"))
        assertTrue(qr.tags.none { it.id == "63" })
        val onlyCrc = assertNotNull(PromptParse.parse("63040000"))
        assertTrue(onlyCrc.tags.isEmpty())
        assertEquals("6304" + Tlv.checksum("6304"), onlyCrc.encode())
    }

    @Test
    fun `sub-tags can be turned off`() {
        val withSub = PromptParse.parse(valid)!!
        val without = PromptParse.parse(valid, subTags = false)!!
        assertEquals("0066801111111", withSub.value("29", "01"))
        assertNull(without.value("29", "01"))
        assertTrue(without.tag("29")!!.subTags.isEmpty())
        assertEquals(withSub.tag("29")!!.value, without.tag("29")!!.value)
        assertEquals(valid, without.encode())
    }

    @Test
    fun `values that only look like TLV do not get sub-tags`() {
        fun subTagsOf(value: String): List<Tag> =
            PromptParse.parse("59%02d%s".format(value.length, value))!!.tag("59")!!.subTags

        assertTrue(subTagsOf("1234 Shop").isEmpty()) // 12 / len 34 runs past the end
        assertTrue(subTagsOf("01000202xy").isEmpty()) // empty sub-tag 01
        assertTrue(subTagsOf("0002ab01").isEmpty()) // dangling bytes
        assertTrue(subTagsOf("0000ab").isEmpty()) // 00/len 00 then "ab" is not a tag
        assertTrue(subTagsOf("1000x").isEmpty()) // 4 digits, then junk
        assertTrue(subTagsOf("20.15").isEmpty())
        assertEquals(listOf("00", "01"), subTagsOf("0002ab0103xyz").map { it.id })
    }

    @Test
    fun `sub-tags are one level deep`() {
        // 62 -> 07 -> "0002ab" looks like TLV but is not decoded again.
        val inner = Tag.of("07", "0002ab")
        assertTrue(inner.subTags.isNotEmpty())
        val qr = PromptParse.parse("6210" + "0706" + "0002ab")!!
        assertEquals("0002ab", qr.value("62", "07"))
        assertTrue(qr.tag("62", "07")!!.subTags.isEmpty())
    }

    @Test
    fun `huge inputs finish`() {
        val big = "0000".repeat(500_000)
        val qr = PromptParse.parse(big)!!
        assertEquals(500_000, qr.tags.size)
        assertNull(PromptParse.parse("9".repeat(2_000_000), strict = true))
    }
}
