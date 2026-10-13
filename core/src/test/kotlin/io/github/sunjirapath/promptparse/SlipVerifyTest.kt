@file:Suppress("ktlint:standard:max-line-length") // test vectors are long payload strings

package io.github.sunjirapath.promptparse

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Vectors from maythiwat/promptparse (`index.test.ts:58-65,131-148`) and uunw/thai-qr-payment
 * (`slip-verify.test.ts`), both MIT; see NOTICE.
 */
class SlipVerifyTest {
    private val std = "004000060000010103002021900021231231212000115102TH91049C30"
    private val stdInfo = SlipVerifyInfo("002", "0002123123121200011")
    private val tm = "00480002010102010203P2P0313TXN00012345670408250120249104b425"
    private val tmInfo = TrueMoneySlipVerifyInfo("P2P", "TXN0001234567", "25012024")

    // --- standard ---------------------------------------------------------------------------

    @Test
    fun `standard build matches both references byte for byte`() {
        assertEquals(std, SlipVerify.build("002", "0002123123121200011"))
    }

    @Test
    fun `standard parse of both references' vectors`() {
        assertEquals(stdInfo, SlipVerify.parse(std))
        assertEquals(
            SlipVerifyInfo("014", "00111222233344ABCD12"),
            SlipVerify.parse("004100060000010103014022000111222233344ABCD125102TH910417DF"),
        )
    }

    @Test
    fun `standard CRC is accepted in either case`() {
        assertEquals(stdInfo, SlipVerify.parse(std.dropLast(4) + "9c30"))
        val upper = SlipVerify.build("014", "REF-001")
        assertTrue(Regex("9104[0-9A-F]{4}$").containsMatchIn(upper))
    }

    @Test
    fun `standard rejects everything that is not a valid standard slip`() {
        assertNull(SlipVerify.parse(""))
        assertNull(SlipVerify.parse("Z".repeat(60)))
        assertNull(SlipVerify.parse(std.dropLast(4) + "0000")) // bad CRC
        assertNull(SlipVerify.parse(std + "00")) // tail longer than 4
        assertNull(SlipVerify.parse(std.dropLast(4))) // no CRC digits at all
        assertNull(SlipVerify.parse(tm)) // other variant
        assertNull(SlipVerify.parse(PromptPay.anyId(AnyIdType.MSISDN, "0812223333", java.math.BigDecimal(50)))) // tag 63 payload
        assertNull(
            SlipVerify.parse("00020101021229370016A0000006770101110113006680111111153037645802TH540520.15630442BE"),
        )
    }

    @Test
    fun `wrong API marker, missing fields and wrong root tag are null`() {
        fun slip(
            root: String,
            tagId: String = "00",
        ): String {
            val seed = "$tagId%02d%s5102TH9104".format(root.length, root)
            return seed + Tlv.checksum(seed)
        }
        assertEquals(stdInfo, SlipVerify.parse(slip("0006000001010300202190002123123121200011")))
        assertNull(SlipVerify.parse(slip("0006000002010300202190002123123121200011"))) // marker 000002
        assertNull(SlipVerify.parse(slip("010300202190002123123121200011"))) // no marker
        assertNull(SlipVerify.parse(slip("00060000010219000212312312120001"))) // no bank
        assertNull(SlipVerify.parse(slip("0006000001010300202"))) // no ref
        assertNull(SlipVerify.parse(slip("00060000010100" + "0200"))) // empty bank and ref
        assertNull(SlipVerify.parse(slip("0006000001010300202190002123123121200011", tagId = "01"))) // root is not tag 00
        assertNull(SlipVerify.parse(slip("0006000001010300205190002123123121200011"))) // sub-tag runs past the end
    }

    @Test
    fun `trailing bytes after a valid body are rejected`() {
        val body = std.dropLast(8) + "99" // junk after tag 51
        val seed = body + "9104"
        assertNull(SlipVerify.parse(seed + Tlv.checksum(seed)))
    }

    @Test
    fun `standard builder validates and never emits a short CRC`() {
        assertFailsWith<IllegalArgumentException> { SlipVerify.build("02", "R") }
        assertFailsWith<IllegalArgumentException> { SlipVerify.build("0021", "R") }
        assertFailsWith<IllegalArgumentException> { SlipVerify.build("00a", "R") }
        assertFailsWith<IllegalArgumentException> { SlipVerify.build("002", "") }
        assertFailsWith<IllegalArgumentException> { SlipVerify.build("002", "R".repeat(100)) }
        for (n in 0 until 400) {
            assertEquals(
                4,
                SlipVerify.build("014", "T%08d".format(n)).substringAfterLast("9104").length,
            )
        }
    }

    // --- truncated CRC ----------------------------------------------------------------------

    @Test
    fun `standard truncated CRC of 1, 2 and 3 digits is repaired`() {
        // transRef -> canonical CRC with leading zeros (from thai-qr-payment's tests)
        for ((ref, crc) in listOf("T00000086" to "0539", "T00000178" to "00C3", "T00003342" to "0004")) {
            val wire = SlipVerify.build("014", ref)
            assertEquals(crc, wire.takeLast(4), ref)
            // a bank app drops leading zeros: 0539 -> 539, 00C3 -> 0C3 / C3, 0004 -> 004 / 04 / 4
            for (dropped in 1..(4 - crc.trimStart('0').length)) {
                val truncated = wire.dropLast(4) + crc.substring(dropped)
                assertEquals(SlipVerifyInfo("014", ref), SlipVerify.parse(truncated), "$ref minus $dropped")
                assertNull(SlipVerify.parse(truncated, crcAutoFix = false), "$ref minus $dropped, fix disabled")
            }
        }
    }

    @Test
    fun `a truncated CRC that does not match is rejected`() {
        val wire = SlipVerify.build("014", "T00000086") // ...0539
        assertNull(SlipVerify.parse(wire.dropLast(4) + "9")) // 9 padded is 0009, not 0539
        assertNull(SlipVerify.parse(wire.dropLast(4)))
        assertNull(SlipVerify.parse(wire.dropLast(2), crcAutoFix = false))
        assertEquals(SlipVerifyInfo("014", "T00000086"), SlipVerify.parse(wire, crcAutoFix = false))
    }

    @Test
    fun `a CRC whose digits spell the tag header is still found`() {
        val wire = CRC_HEADER_COLLISION_SLIP
        assertEquals(wire, SlipVerify.build("014", "R5328"))
        assertEquals(SlipVerifyInfo("014", "R5328"), SlipVerify.parse(wire))
        assertEquals(SlipVerifyInfo("014", "R5328"), SlipVerify.parse(wire, crcAutoFix = false))
    }

    // --- TrueMoney --------------------------------------------------------------------------

    @Test
    fun `TrueMoney build matches thai-qr-payment byte for byte, lowercase CRC`() {
        assertEquals(tm, SlipVerify.buildTrueMoney("P2P", "TXN0001234567", "25012024"))
        assertTrue(
            Regex("9104[0-9a-f]{4}$").containsMatchIn(SlipVerify.buildTrueMoney("TOPUP", "A1B2C3D4", "01012025")),
        )
        assertTrue(tm.startsWith("0048"))
        assertTrue(!tm.contains("5102TH"))
    }

    @Test
    fun `TrueMoney parse accepts either CRC case`() {
        assertEquals(tmInfo, SlipVerify.parseTrueMoney(tm))
        assertEquals(tmInfo, SlipVerify.parseTrueMoney(tm.dropLast(4) + tm.takeLast(4).uppercase()))
    }

    @Test
    fun `TrueMoney rejects the standard slip, tag 63 payloads and bad input`() {
        assertNull(SlipVerify.parseTrueMoney(std))
        assertNull(SlipVerify.parseTrueMoney(""))
        assertNull(SlipVerify.parseTrueMoney(PromptPay.anyId(AnyIdType.MSISDN, "0812223333")))
        assertNull(SlipVerify.parseTrueMoney(tm.dropLast(4) + "0000"))
        assertNull(SlipVerify.parseTrueMoney(tm + "0"))
    }

    @Test
    fun `TrueMoney needs both markers and all three fields`() {
        fun slip(root: String): String {
            val seed = "00%02d%s9104".format(root.length, root)
            return seed + Tlv.checksum(seed).lowercase()
        }
        val full = "0002010102010203P2P0313TXN00012345670408250120" + "24"
        assertEquals(tmInfo, SlipVerify.parseTrueMoney(slip(full)))
        assertNull(SlipVerify.parseTrueMoney(slip("00020101020" + "00203P2P0313TXN00012345670408250120" + "24"))) // marker B is 00
        assertNull(SlipVerify.parseTrueMoney(slip("0102010203P2P0313TXN00012345670408250120" + "24"))) // no marker A
        assertNull(SlipVerify.parseTrueMoney(slip("000201010201" + "0313TXN00012345670408250120" + "24"))) // no event
        assertNull(SlipVerify.parseTrueMoney(slip("0002010102010203P2P0408250120" + "24"))) // no transaction id
        assertNull(SlipVerify.parseTrueMoney(slip("0002010102010203P2P0313TXN0001234567"))) // no date
        assertNull(SlipVerify.parseTrueMoney(slip("0002010102010200" + "0313TXN00012345670408250120" + "24"))) // empty event
    }

    @Test
    fun `TrueMoney truncated CRC`() {
        // TXN0000000005 -> canonical CRC 0549 (thai-qr-payment's test)
        val wire = SlipVerify.buildTrueMoney("P2P", "TXN0000000005", "25012024")
        assertEquals("0549", wire.takeLast(4))
        val info = TrueMoneySlipVerifyInfo("P2P", "TXN0000000005", "25012024")
        assertEquals(info, SlipVerify.parseTrueMoney(wire.dropLast(4) + "549"))
        assertNull(SlipVerify.parseTrueMoney(wire.dropLast(4) + "549", crcAutoFix = false))
        assertEquals(info, SlipVerify.parseTrueMoney(wire, crcAutoFix = false))
    }

    @Test
    fun `TrueMoney builder validates`() {
        assertFailsWith<IllegalArgumentException> { SlipVerify.buildTrueMoney("", "T", "25012024") }
        assertFailsWith<IllegalArgumentException> { SlipVerify.buildTrueMoney("P2P", "", "25012024") }
        for (bad in listOf("", "2025", "250120240", "2501202a", "25-01-24")) {
            assertFailsWith<IllegalArgumentException>(bad) { SlipVerify.buildTrueMoney("P2P", "T", bad) }
        }
    }

    // --- tag-63 helper ----------------------------------------------------------------------

    @Test
    fun `fixTruncatedCrc repairs a payment QR`() {
        val full = LEADING_ZERO_CRC_PAYMENT
        assertEquals(full, PromptPay.anyId(AnyIdType.MSISDN, "0812223333", java.math.BigDecimal("9")))
        val shortened = full.dropLast(4) + full.takeLast(3)
        assertEquals(full, PromptParse.fixTruncatedCrc(shortened))
        assertNull(PromptParse.parse(shortened, strict = true), "parse itself never repairs")
        assertNotNull(PromptParse.parse(PromptParse.fixTruncatedCrc(shortened)!!, strict = true))
        assertNull(PromptParse.fixTruncatedCrc(full)) // already complete
        assertNull(PromptParse.fixTruncatedCrc(full.dropLast(4) + "FFF")) // wrong
        assertNull(PromptParse.fixTruncatedCrc(""))
    }
}
