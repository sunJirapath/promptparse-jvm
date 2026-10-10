package io.github.sunjirapath.promptparse

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Golden vectors ported from maythiwat/promptparse `src/index.test.ts` (MIT, see NOTICE).
 * Phase 1 only; the comment on each test gives the reference test's line range.
 */
class ReferenceVectorsTest {
    private val goodCrcPayload =
        "00020101021229370016A0000006770101110113006680111111153037645802TH540520.15630442BE"

    @Test // :5-7
    fun `invalid string passed to parser`() {
        assertNull(PromptParse.parse("AAAA0000"))
    }

    @Test // :9-15
    fun `parse TLV, count tags and read one`() {
        val qr = assertNotNull(PromptParse.parse("000411110104222202043333"))
        assertEquals(3, qr.tags.size)
        assertEquals("2222", qr.tag("01")?.value)
    }

    @Test // :17-24
    fun `strict parse with invalid checksum`() {
        assertNull(PromptParse.parse(goodCrcPayload.dropLast(4) + "FFFF", strict = true))
    }

    @Test // :26-33
    fun `strict parse with valid checksum, sub-tag value`() {
        val qr = assertNotNull(PromptParse.parse(goodCrcPayload, strict = true))
        assertEquals("0066801111111", qr.value("29", "01"))
    }

    @Test // :35-44
    fun `generate AnyID`() {
        assertEquals(
            "00020101021129370016A0000006770101110113006681222333353037645802TH63041DCF",
            PromptPay.anyId(AnyIdType.MSISDN, "0812223333"),
        )
    }

    @Test // :46-56
    fun `generate AnyID with amount`() {
        assertEquals(
            "00020101021229370016A0000006770101110113006681222333353037645802TH540530.0063043CAD",
            PromptPay.anyId(AnyIdType.MSISDN, "0812223333", BigDecimal(30)),
        )
    }

    @Test // :89-100
    fun `generate Bill Payment with ref3`() {
        assertEquals(
            "00020101021130620016A000000677010112011301122334455660211CUSTOMER0010306INV00153037645802TH62070703SCB6304780E",
            PromptPay.billPayment("0112233445566", "CUSTOMER001", "INV001", ref3 = "SCB"),
        )
    }

    @Test // :122-129 (reference EMVCoQR.validate('63'))
    fun `validate checksum tag`() {
        assertTrue(PromptParse.validateCrc(goodCrcPayload))
    }

    @Test // :178-182
    fun `read AnyID, MSISDN without amount`() {
        val payload = PromptPay.anyId(AnyIdType.MSISDN, "0812223333")
        assertEquals(AnyIdInfo(AnyIdType.MSISDN, "0812223333", null), PromptPay.parseAnyId(payload))
    }

    @Test // :184-190
    fun `read AnyID, MSISDN with amount`() {
        val payload = PromptPay.anyId(AnyIdType.MSISDN, "0812223333", BigDecimal(30))
        assertEquals(AnyIdInfo(AnyIdType.MSISDN, "0812223333", BigDecimal("30.00")), PromptPay.parseAnyId(payload))
    }

    @Test // :192-196
    fun `read AnyID, NATID`() {
        val payload = PromptPay.anyId(AnyIdType.NATID, "1234567890123")
        assertEquals(AnyIdInfo(AnyIdType.NATID, "1234567890123", null), PromptPay.parseAnyId(payload))
    }

    @Test // :198-204
    fun `read AnyID from a slip verify payload is null`() {
        assertNull(PromptPay.parseAnyId("004000060000010103002021900021231231212000115102TH91049C30"))
    }

    @Test // :206-212
    fun `read Bill Payment without optional fields`() {
        val payload = PromptPay.billPayment("0112233445566", "CUSTOMER001")
        assertEquals(
            BillPaymentInfo("0112233445566", "CUSTOMER001", null, null, null),
            PromptPay.parseBillPayment(payload),
        )
    }

    @Test // :214-232
    fun `read Bill Payment with ref2, ref3 and amount`() {
        val payload = PromptPay.billPayment("0112233445566", "CUSTOMER001", "INV001", BigDecimal(250), "SCB")
        assertEquals(
            BillPaymentInfo("0112233445566", "CUSTOMER001", "INV001", "SCB", BigDecimal("250.00")),
            PromptPay.parseBillPayment(payload),
        )
    }

    @Test // :234-240
    fun `read Bill Payment from an AnyID payload is null`() {
        assertNull(PromptPay.parseBillPayment(PromptPay.anyId(AnyIdType.MSISDN, "0812223333")))
    }

    @Test // :242-250 (reference checksum() == Tlv.checksum on the whole string)
    fun `checksum of UTF-8 text`() {
        assertEquals("8FDF", Tlv.checksum("Café"))
        assertEquals("1EA0", Tlv.checksum("ร้านทดสอบ"))
        assertEquals("BF6B", Tlv.checksum("a😀b"))
    }

    @Test // :248-250
    fun `checksum with unpaired surrogate`() {
        assertEquals("64F6", Tlv.checksum("lone\uD800x"))
    }

    @Test // :252-258
    fun `strict parse of a payload with UTF-8 merchant name`() {
        val payload =
            "00020101021129370016A0000006770101110113006681222333353037645802TH5927ร้านทดสอบ6021กรุงเทพ630488F4"
        val qr = assertNotNull(PromptParse.parse(payload, strict = true))
        // Under UTF-8 byte lengths (SPEC_NOTES Q2) the vector parses as the reference author clearly meant it to.
        assertEquals("ร้านทดสอบ", qr.value("59"))
        assertEquals("กรุงเทพ", qr.value("60"))
        assertEquals(payload, qr.encode())
        assertNull(PromptParse.parse(payload.dropLast(4) + "FFFF", strict = true))
        assertFalse(PromptParse.validateCrc(payload.dropLast(4) + "FFFF"))
    }
}
