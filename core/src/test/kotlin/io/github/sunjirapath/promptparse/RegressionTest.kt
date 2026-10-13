@file:Suppress("ktlint:standard:max-line-length") // test vectors are long payload strings

package io.github.sunjirapath.promptparse

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** One test per bug found and fixed while building this library. The name says what went wrong. */
class RegressionTest {
    @Test
    fun `tiny and huge BigDecimal exponents throw IllegalArgumentException, not ArithmeticException`() {
        // setScale(2) on 1E-999999999 overflowed BigInteger before the magnitude pre-check existed
        for (a in listOf("1E-999999999", "1E+999999999", "-1E+999999999", "0E+999999999")) {
            assertFailsWith<IllegalArgumentException>(
                a,
            ) { PromptPay.anyId(AnyIdType.MSISDN, "0812223333", BigDecimal(a)) }
        }
    }

    @Test
    fun `a bill payment ref3 of exactly 95 bytes fits tag 62 and 96 does not`() {
        // the first version of the test used 95 as the failing case; tag 62 holds "07" + "95" + 95 = 99 bytes
        PromptPay.billPayment("0112233445566", "X", ref3 = "X".repeat(95))
        assertFailsWith<IllegalArgumentException> { PromptPay.billPayment("0112233445566", "X", ref3 = "X".repeat(96)) }
    }

    @Test
    fun `a bank app drops the LEADING zeros of the CRC, not the trailing digits`() {
        val wire = SlipVerify.build("014", "T00000086") // CRC 0539
        assertEquals("0539", wire.takeLast(4))
        assertNotNull(SlipVerify.parse(wire.dropLast(4) + "539"))
        assertNull(SlipVerify.parse(wire.dropLast(1))) // "053" would mean 0053: wrong digits, must not repair
    }

    @Test
    fun `a CRC that spells the tag header is found, lastIndexOf-style search got it wrong`() {
        assertEquals(CRC_HEADER_COLLISION_SLIP, SlipVerify.build("014", "R5328"))
        assertEquals("R5328", SlipVerify.parse(CRC_HEADER_COLLISION_SLIP)!!.transRef)
    }

    @Test
    fun `an all-zero CRC cannot be shortened to nothing`() {
        // the property test once dropped all four digits of 0000 and expected a repair
        assertEquals(ZERO_CRC_SLIP, SlipVerify.build("014", "Z15389"))
        val wire = ZERO_CRC_SLIP
        assertNotNull(SlipVerify.parse(wire.dropLast(1)))
        assertNotNull(SlipVerify.parse(wire.dropLast(3)))
        assertNull(SlipVerify.parse(wire.dropLast(4)))
    }

    @Test
    fun `control characters are not case-folded into hex digits in the CRC compare`() {
        // 0x10 or 0x20 equals '0' (0x30): a sloppy "or 0x20" comparison accepted this
        val valid = PromptPay.anyId(AnyIdType.MSISDN, "0812223333", BigDecimal(30))
        val crc = valid.takeLast(4)
        val forged = crc.map { if (it == '0') '\u0010' else it }.joinToString("")
        if (forged != crc) assertNull(PromptParse.parse(valid.dropLast(4) + forged, strict = true))
    }

    @Test
    fun `lowercase hex digits a-f are accepted in the CRC but g-z and fullwidth letters are not`() {
        val valid = PromptPay.anyId(AnyIdType.MSISDN, "0812223333", BigDecimal(30))
        assertNotNull(PromptParse.parse(valid.dropLast(4) + valid.takeLast(4).lowercase(), strict = true))
        assertNull(PromptParse.parse(valid.dropLast(4) + "ＦＦＦＦ", strict = true))
    }

    @Test
    fun `a value of exactly 99 bytes is accepted and 100 is rejected, the reference wrapped it to 00`() {
        Tag("59", "a".repeat(99))
        assertFailsWith<IllegalArgumentException> { Tag("59", "a".repeat(100)) }
    }

    @Test
    fun `the reference's Thai merchant-name vector parses with byte lengths`() {
        val payload =
            "00020101021129370016A0000006770101110113006681222333353037645802TH5927ร้านทดสอบ6021กรุงเทพ630488F4"
        val qr = assertNotNull(PromptParse.parse(payload, strict = true))
        assertEquals("ร้านทดสอบ", qr.value("59"))
        assertEquals("กรุงเทพ", qr.value("60"))
    }

    @Test
    fun `a truncated lone surrogate keeps the CRC consistent`() {
        // an unpaired surrogate is U+FFFD (3 bytes) for both the TLV length and the CRC
        val qr = EmvcoQr(listOf(Tag.of("59", "x\uD800y")))
        assertEquals(qr, PromptParse.parse(qr.encode(), strict = true))
    }

    @Test
    fun `tag order puts 54 after 58 like the reference's vectors`() {
        val ids =
            PromptParse
                .parse(
                    PromptPay.anyId(AnyIdType.MSISDN, "0812223333", BigDecimal(30)),
                )!!
                .tags
                .map { it.id }
        assertEquals(listOf("00", "01", "29", "53", "58", "54"), ids)
    }

    @Test
    fun `a whitespace-only lenient trim does not eat a last value that ends with a space`() {
        val qr = assertNotNull(PromptParse.parse("0001 "))
        assertEquals(" ", qr.value("00"))
    }

    @Test
    fun `the 0066 mobile spelling is normalized, the JS reference turned it into 6066812345678`() {
        val payload = PromptPay.anyId(AnyIdType.MSISDN, "0066812345678")
        assertEquals("0066812345678", PromptParse.parse(payload)!!.value("29", "01"))
    }

    @Test
    fun `a BOT barcode ending in CRLF is read, a plain trim of newline alone left a stray CR`() {
        assertNotNull(BotBarcode.parse("|099400016550100\r123456789012\r670429\r364922\r\n"))
        assertNotNull(BotBarcode.parse("|099400016550100\r123456789012\r670429\r364922\n"))
    }

    @Test
    fun `Windows line endings in a pasted payload are ignored`() {
        val payload = PromptPay.anyId(AnyIdType.NATID, "1234567890123")
        assertNotNull(PromptPay.parseAnyId("$payload\r\n"))
    }

    @Test
    fun `strict parsing requires the CRC to be its own final tag`() {
        val body = PromptPay.anyId(AnyIdType.MSISDN, "0812223333").dropLast(8)
        for (suffix in listOf("5904", "5905X", "5910XX6304", "6310XX6304", "6300" + "6304")) {
            val seed = body + suffix
            val payload = seed + Tlv.checksum(seed)
            assertNull(PromptParse.parse(payload, strict = true), suffix)
            assertNull(PromptPay.parseAnyId(payload), suffix)
        }
    }

    @Test
    fun `empty and nested template children have the same representation after parsing`() {
        val templates =
            listOf(
                Tag.template("29", listOf(Tag("00", "AID"), Tag("01", ""))),
                Tag.template("62", listOf(Tag.template("07", listOf(Tag("00", "ab"))))),
                Tag.template("62", emptyList()),
            )
        for (template in templates) {
            val qr = EmvcoQr(listOf(template))
            assertEquals(qr, PromptParse.parse(qr.encode(), strict = true))
        }
        assertEquals("0003AID0100", templates[0].value)
        assertEquals(emptyList(), templates[0].subTags)
        assertEquals(emptyList(), templates[1].subTags.single().subTags)
    }

    @Test
    fun `TrueMoney removes only the fixed padding, preserving every local digit`() {
        for (mobile in listOf("0012345678", "0000000000", "0801111111")) {
            val payload = TrueMoney.build(mobile)
            assertEquals(mobile, TrueMoney.parse(payload)!!.mobile)
            assertEquals(mobile, TrueMoney.parse(payload, validateFields = true)!!.mobile)
        }
    }

    @Test
    fun `field validation rejects an ambiguous amount while default extraction keeps the first`() {
        val qr = PromptParse.parse(PromptPay.anyId(AnyIdType.MSISDN, "0812223333", BigDecimal("30")))!!
        val payload = EmvcoQr(qr.tags + Tag("54", "99.00")).encode()
        assertEquals(BigDecimal("30.00"), PromptPay.parseAnyId(payload)!!.amount)
        assertNull(PromptPay.parseAnyId(payload, validateFields = true))
    }
}
