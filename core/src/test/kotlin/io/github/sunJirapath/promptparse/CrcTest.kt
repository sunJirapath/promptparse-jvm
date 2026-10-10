package io.github.sunJirapath.promptparse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CrcTest {
    private val valid = "00020101021229370016A0000006770101110113006680111111153037645802TH540520.15630442BE"

    @Test
    fun `CCITT-FALSE check value`() {
        // The standard check value for CRC-16/CCITT-FALSE (poly 1021, init FFFF).
        assertEquals("29B1", Tlv.checksum("123456789"))
        assertEquals("FFFF", Tlv.checksum(""))
    }

    @Test
    fun `output is uppercase and zero padded`() {
        // Find a payload whose CRC starts with 0 to prove the padding.
        val padded = (0..5000).map { Tlv.checksum("a$it") }.first { it.startsWith("0") }
        assertEquals(4, padded.length)
        assertEquals(padded.uppercase(), padded)
    }

    @Test
    fun `valid payload passes, either hex case`() {
        assertTrue(PromptParse.validateCrc(valid))
        assertTrue(PromptParse.validateCrc(valid.dropLast(4) + "42be"))
    }

    @Test
    fun `changing any single character is detected`() {
        for (i in valid.indices) {
            val c = valid[i]
            val replacement = if (c == 'X') 'Y' else 'X'
            val tampered = valid.substring(0, i) + replacement + valid.substring(i + 1)
            assertFalse(PromptParse.validateCrc(tampered), "tamper at $i")
            assertNull(PromptParse.parse(tampered, strict = true), "strict tamper at $i")
        }
    }

    @Test
    fun `swapping or dropping characters is detected`() {
        assertFalse(PromptParse.validateCrc(valid.removeRange(10, 11)))
        assertFalse(PromptParse.validateCrc(valid.substring(0, 20) + valid.substring(21, 22) + valid[20] + valid.substring(22)))
    }

    @Test
    fun `non hex and control characters in the CRC are rejected`() {
        // 0x10 | 0x20 == '0': a sloppy case-folding comparison would accept this.
        val crc = Tlv.checksum(valid.dropLast(4))
        val bad = crc.replace('0', '\u0010')
        if (bad != crc) assertFalse(PromptParse.validateCrc(valid.dropLast(4) + bad))
        assertFalse(PromptParse.validateCrc(valid.dropLast(4) + "ＦＦＦＦ"))
    }

    @Test
    fun `validateCrc rejects short, tagless and empty input`() {
        assertFalse(PromptParse.validateCrc(""))
        assertFalse(PromptParse.validateCrc("6304"))
        assertFalse(PromptParse.validateCrc("12345678"))
        assertFalse(PromptParse.validateCrc("6304FFFF"))
        assertTrue(PromptParse.validateCrc("6304" + Tlv.checksum("6304")))
    }

    @Test
    fun `encode always recomputes the CRC`() {
        val qr = PromptParse.parse(valid.dropLast(4) + "0000")!!
        assertEquals(valid, qr.encode())
    }
}
