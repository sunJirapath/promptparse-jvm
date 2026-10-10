package io.github.sunjirapath.promptparse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EmvcoQrTest {
    private val payload = PromptPay.anyId(AnyIdType.MSISDN, "0812223333")
    private val qr = PromptParse.parse(payload, strict = true)!!

    @Test
    fun `lookup of tags, sub-tags and values`() {
        assertEquals("01", qr.value("00"))
        assertEquals("11", qr.tag("01")?.value)
        assertEquals("A000000677010111", qr.value("29", "00"))
        assertEquals("0066812223333", qr.tag("29", "01")?.value)
        assertEquals(listOf("00", "01"), qr.tag("29")!!.subTags.map { it.id })
        assertNull(qr.tag("99"))
        assertNull(qr.value("29", "99"))
        assertNull(qr.value("99", "00"))
        assertNull(qr.tag("00", "00")) // tag 00 has no sub-tags
    }

    @Test
    fun `with replaces in place and keeps the order`() {
        val changed = qr.with("01", "12")
        assertEquals("12", changed.value("01"))
        assertEquals(qr.tags.map { it.id }, changed.tags.map { it.id })
        assertEquals("11", qr.value("01"), "original is unchanged")
        assertTrue(PromptParse.validateCrc(changed.encode()))
    }

    @Test
    fun `with appends a missing tag and re-derives sub-tags`() {
        val added = qr.with("62", "0703SCB")
        assertEquals("62", added.tags.last().id)
        assertEquals("SCB", added.value("62", "07"))
        assertEquals(added, PromptParse.parse(added.encode(), strict = true))
    }

    @Test
    fun `with rejects the CRC tag, bad ids and long values`() {
        assertFailsWith<IllegalArgumentException> { qr.with("63", "0000") }
        assertFailsWith<IllegalArgumentException> { qr.with("6", "x") }
        assertFailsWith<IllegalArgumentException> { qr.with("ab", "x") }
        assertFailsWith<IllegalArgumentException> { qr.with("54", "9".repeat(100)) }
    }

    @Test
    fun `the constructor rejects tag 63`() {
        assertFailsWith<IllegalArgumentException> { EmvcoQr(listOf(Tag("63", "ABCD"))) }
    }

    @Test
    fun `tags are copied and unmodifiable views`() {
        val source = mutableListOf(Tag("00", "01"))
        val q = EmvcoQr(source)
        source.add(Tag("01", "11"))
        assertEquals(1, q.tags.size)
    }

    @Test
    fun `equality ignores the original CRC and case`() {
        val lower = PromptParse.parse(payload.dropLast(4) + payload.takeLast(4).lowercase())!!
        assertEquals(qr, lower)
        assertEquals(qr.hashCode(), lower.hashCode())
        assertNotEquals(qr, qr.with("01", "12"))
        assertFalse(qr.equals("x"))
        assertTrue(qr.toString().startsWith("EmvcoQr(tags="))
    }

    @Test
    fun `encode of a hand built QR matches the reference tag order`() {
        val built = EmvcoQr(listOf(Tag("00", "01"), Tag("01", "11")))
        val body = "000201" + "010211" + "6304"
        assertEquals(body + Tlv.checksum(body), built.encode())
    }

    @Test
    fun `Tag validation`() {
        assertFailsWith<IllegalArgumentException> { Tag("1", "x") }
        assertFailsWith<IllegalArgumentException> { Tag("1a", "x") }
        assertFailsWith<IllegalArgumentException> { Tag("123", "x") }
        // value and sub-tags must agree
        assertFailsWith<IllegalArgumentException> { Tag("29", "nope", listOf(Tag("00", "a"))) }
        Tag("29", "0001a", listOf(Tag("00", "a")))
    }

    @Test
    fun `Tag factories, equality and toString`() {
        val template = Tag.template("29", listOf(Tag("00", "ab"), Tag("01", "cd")))
        assertEquals("000 2ab010 2cd".replace(" ", ""), template.value)
        assertEquals(template, Tag.of("29", template.value))
        assertEquals(template.hashCode(), Tag.of("29", template.value).hashCode())
        assertNotEquals(template, Tag("29", template.value)) // same text, but no sub-tags
        assertNotEquals(Tag("29", "x"), Tag("29", "y"))
        assertNotEquals(Tag("29", "x"), Tag("30", "x"))
        assertFalse(template.equals(null))
        assertSame(template, template.also { assertTrue(it == template) })
        assertEquals("Tag(id=01, value=11)", Tag("01", "11").toString())
        assertTrue(template.toString().startsWith("Tag(id=29, subTags=["))
        assertTrue(Tag("01", "11").subTags.isEmpty())
    }
}
