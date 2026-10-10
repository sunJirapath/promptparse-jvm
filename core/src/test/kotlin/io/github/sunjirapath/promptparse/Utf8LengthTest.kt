package io.github.sunjirapath.promptparse

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** TLV lengths count UTF-8 bytes (SPEC_NOTES Q2). */
class Utf8LengthTest {
    private val thai = "ร้านทดสอบ" // 9 chars, 27 bytes
    private val emoji = "😀" // 1 code point, 2 UTF-16 units, 4 bytes

    @Test
    fun `byte lengths of 1, 2, 3 and 4 byte characters and a lone surrogate`() {
        assertEquals(1, Tlv.utf8Length("a"))
        assertEquals(5, Tlv.utf8Length("Café"))
        assertEquals(27, Tlv.utf8Length(thai))
        assertEquals(4, Tlv.utf8Length(emoji))
        assertEquals(3, Tlv.utf8Length("\uD800")) // U+FFFD, as the reference's CRC does
        assertEquals(3, Tlv.utf8Length("\uDC00"))
        assertEquals(3 + 1, Tlv.utf8Length("\uD800a"))
    }

    @Test
    fun `utf8Bytes agrees with the JDK for well-formed text`() {
        for (s in listOf("", "abc", "Café", thai, emoji, "a${emoji}b", "\u0000\u007F\u0080߿ࠀ￿")) {
            assertContentEquals(s.toByteArray(Charsets.UTF_8), Tlv.utf8Bytes(s), s)
        }
        assertContentEquals(byteArrayOf(0xEF.toByte(), 0xBF.toByte(), 0xBD.toByte()), Tlv.utf8Bytes("\uD800"))
    }

    @Test
    fun `encode writes the byte length`() {
        assertEquals("5927$thai", Tlv.encode(listOf(Tag("59", "$thai"))))
        assertEquals("590" + "5" + "Café", Tlv.encode(listOf(Tag("59", "Café"))))
        assertEquals("5906a${emoji}b", Tlv.encode(listOf(Tag("59", "a${emoji}b"))))
    }

    @Test
    fun `decode reads the byte length`() {
        val tags = Tlv.decode("5927${thai}6021กรุงเทพ", strict = true)!!
        assertEquals(listOf("59" to thai, "60" to "กรุงเทพ"), tags.map { it.id to it.value })
    }

    @Test
    fun `99 bytes fit, 100 do not`() {
        Tag("59", "a".repeat(99))
        Tag("59", "ก".repeat(33)) // 99 bytes, 33 chars
        assertFailsWith<IllegalArgumentException> { Tag("59", "a".repeat(100)) }
        assertFailsWith<IllegalArgumentException> { Tag("59", "ก".repeat(34)) } // 102 bytes
        assertFailsWith<IllegalArgumentException> { Tag("59", emoji.repeat(25)) } // 100 bytes, 50 UTF-16 units
        Tag("59", emoji.repeat(24)) // 96 bytes
    }

    @Test
    fun `a length that ends inside a character is malformed`() {
        val payload = "5902ก" // 2 of the 3 bytes of ก
        assertNull(Tlv.decode(payload, strict = true))
        assertNull(PromptParse.parse("0002015902ก", strict = true))
        assertEquals(listOf("00"), PromptParse.parse("0002015902ก")!!.tags.map { it.id })
    }

    @Test
    fun `a length that ends inside a surrogate pair is malformed`() {
        assertNull(Tlv.decode("5902$emoji", strict = true))
        assertEquals(emoji, Tlv.decode("5904$emoji", strict = true)!!.single().value)
    }

    @Test
    fun `lone surrogates round trip`() {
        val qr = EmvcoQr(listOf(Tag.of("59", "x\uD800y"), Tag.of("60", "\uDC00")))
        assertEquals(qr, PromptParse.parse(qr.encode(), strict = true))
    }

    /**
     * Documents what the alternative (UTF-16 code unit lengths, the reference's decode loop) would do
     * to the reference's own Thai vector, which is why we count UTF-8 bytes instead.
     */
    @Test
    fun `counting UTF-16 units would mis-parse the reference's own Thai vector`() {
        val payload = "5927${thai}6021กรุงเทพ630488F4"

        // Verbatim port of the reference decode(): slice by UTF-16 index.
        val referenceStyle = ArrayList<Pair<String, String>>()
        var idx = 0
        while (idx < payload.length) {
            val id = payload.substring(idx, minOf(idx + 2, payload.length))
            val length = payload.substring(minOf(idx + 2, payload.length), minOf(idx + 4, payload.length)).toIntOrNull() ?: break
            referenceStyle.add(id to payload.substring(minOf(idx + 4, payload.length), minOf(idx + 4 + length, payload.length)))
            idx += length + 4
        }
        assertNotEquals(thai, referenceStyle.first().second)
        assertTrue(referenceStyle.first().second.contains("6021"))

        // Byte lengths read it correctly.
        val tags = Tlv.decode(payload, strict = true)!!
        assertEquals(listOf("59", "60", "63"), tags.map { it.id })
        assertEquals(thai, tags[0].value)
    }
}
