package io.github.sunJirapath.promptparse

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PromptPayTest {
    private val aidAnyId = "A000000677010111"
    private val aidBill = "A000000677010112"
    private val mobile = "00020101021129370016A0000006770101110113006681222333353037645802TH63041DCF"

    // --- AnyID: MSISDN ---------------------------------------------------------------------

    @Test
    fun `every accepted MSISDN spelling gives the same payload`() {
        for (input in listOf(
            "0812223333", "081-222-3333", "081 222 3333", " 0812223333".trim(), "+66812223333", "+66 81 222 3333",
            "66812223333", "0066812223333", "0066-81-222-3333",
        )) {
            assertEquals(mobile, PromptPay.anyId(AnyIdType.MSISDN, input), input)
        }
    }

    @Test
    fun `invalid MSISDNs are rejected with a clear message`() {
        for (input in listOf(
            "", "0", "0812", "081222333", "08122233334", "1812223333", "abc", "08122x3333", "+0812223333",
            "+660812223333", "+66812223333 ext", "+0066812223333", "6681222333", "668122233334",
            "00668122233", "0066812223333 4", "++66812223333", "٠٨١٢٢٢٣٣٣٣", "081.222.3333",
        )) {
            val e = assertFailsWith<IllegalArgumentException>(input) { PromptPay.anyId(AnyIdType.MSISDN, input) }
            assertTrue(e.message!!.startsWith("MSISDN must be"), e.message)
        }
    }

    // --- AnyID: NATID / EWALLETID -----------------------------------------------------------

    @Test
    fun `national ID needs 13 digits, separators are ignored`() {
        val expected = PromptPay.anyId(AnyIdType.NATID, "1234567890123")
        assertEquals(expected, PromptPay.anyId(AnyIdType.NATID, "1-2345-67890-12-3"))
        assertEquals("1234567890123", PromptPay.parseAnyId(expected)!!.target)
        for (bad in listOf("", "123456789012", "12345678901234", "123456789012a", "0812223333")) {
            assertFailsWith<IllegalArgumentException>(bad) { PromptPay.anyId(AnyIdType.NATID, bad) }
        }
    }

    @Test
    fun `e-wallet ID needs 15 digits`() {
        val payload = PromptPay.anyId(AnyIdType.EWALLETID, "140000812223333")
        assertTrue(payload.contains("0315140000812223333"))
        assertEquals(AnyIdInfo(AnyIdType.EWALLETID, "140000812223333", null), PromptPay.parseAnyId(payload))
        for (bad in listOf("", "14000081222333", "1400008122233334", "14000081222333x")) {
            assertFailsWith<IllegalArgumentException>(bad) { PromptPay.anyId(AnyIdType.EWALLETID, bad) }
        }
    }

    // --- amounts and point of initiation ----------------------------------------------------

    @Test
    fun `static versus dynamic`() {
        val static = PromptParse.parse(PromptPay.anyId(AnyIdType.MSISDN, "0812223333"))!!
        assertEquals("11", static.value("01"))
        assertNull(static.tag("54"))
        val dynamic = PromptParse.parse(PromptPay.anyId(AnyIdType.MSISDN, "0812223333", BigDecimal("0.01")))!!
        assertEquals("12", dynamic.value("01"))
        assertEquals("0.01", dynamic.value("54"))
    }

    @Test
    fun `tags 53 and 58 are fixed and 54 follows 58`() {
        val qr = PromptParse.parse(PromptPay.anyId(AnyIdType.MSISDN, "0812223333", BigDecimal.TEN))!!
        assertEquals("764", qr.value("53"))
        assertEquals("TH", qr.value("58"))
        assertEquals(listOf("00", "01", "29", "53", "58", "54"), qr.tags.map { it.id })
    }

    @Test
    fun `amount is formatted with 2 decimals, HALF_UP`() {
        fun amountTag(a: String) =
            PromptParse.parse(PromptPay.anyId(AnyIdType.MSISDN, "0812223333", BigDecimal(a)))!!.value("54")

        assertEquals("30.00", amountTag("30"))
        assertEquals("30.00", amountTag("30.0"))
        assertEquals("10.05", amountTag("10.05"))
        assertEquals("3649.22", amountTag("3649.22"))
        assertEquals("1.01", amountTag("1.005")) // the JS reference gives 1.00 (binary double)
        assertEquals("2.68", amountTag("2.675")) // ... and 2.67
        assertEquals("0.13", amountTag("0.125"))
        assertEquals("0.01", amountTag("0.005"))
        assertEquals("1.00", amountTag("0.995"))
        assertEquals("1.00", amountTag("1.004999"))
        assertEquals("1200000.00", amountTag("1.2E+6"))
        assertEquals("9999999999.99", amountTag("9999999999.99"))
    }

    @Test
    fun `amounts that are zero, negative, tiny or too large are rejected`() {
        for (a in listOf("0", "0.00", "-1", "-0.01", "0.004", "1E-999999999", "-1E+999999999", "0E+999999999")) {
            assertFailsWith<IllegalArgumentException>(a) { PromptPay.anyId(AnyIdType.MSISDN, "0812223333", BigDecimal(a)) }
        }
        for (a in listOf("10000000000", "1E+999999999", "9999999999.995", "99999999999.99")) {
            assertFailsWith<IllegalArgumentException>(a) { PromptPay.anyId(AnyIdType.MSISDN, "0812223333", BigDecimal(a)) }
        }
    }

    // --- Bill Payment -----------------------------------------------------------------------

    @Test
    fun `bill payment tags`() {
        val payload = PromptPay.billPayment("010753600031508", "CUST1", "INV2", BigDecimal("12.5"), "SCB")
        val qr = PromptParse.parse(payload, strict = true)!!
        assertEquals(listOf("00", "01", "30", "53", "58", "54", "62"), qr.tags.map { it.id })
        assertEquals(aidBill, qr.value("30", "00"))
        assertEquals("010753600031508", qr.value("30", "01"))
        assertEquals("CUST1", qr.value("30", "02"))
        assertEquals("INV2", qr.value("30", "03"))
        assertEquals("12.50", qr.value("54"))
        assertEquals("SCB", qr.value("62", "07"))
        assertEquals("12", qr.value("01"))
    }

    @Test
    fun `empty or null ref2 and ref3 are left out`() {
        val bare = PromptPay.billPayment("0112233445566", "R1")
        assertEquals(bare, PromptPay.billPayment("0112233445566", "R1", "", null, ""))
        assertNull(PromptParse.parse(bare)!!.tag("30", "03"))
        assertNull(PromptParse.parse(bare)!!.tag("62"))
        assertEquals("11", PromptParse.parse(bare)!!.value("01"))
    }

    @Test
    fun `invalid bill payment input`() {
        assertFailsWith<IllegalArgumentException> { PromptPay.billPayment("", "R1") }
        assertFailsWith<IllegalArgumentException> { PromptPay.billPayment("011223344556", "R1") }
        assertFailsWith<IllegalArgumentException> { PromptPay.billPayment("01122334455678", "R1") }
        assertFailsWith<IllegalArgumentException> { PromptPay.billPayment("011223344556x", "R1") }
        assertFailsWith<IllegalArgumentException> { PromptPay.billPayment("0112233445566", "") }
        assertFailsWith<IllegalArgumentException> { PromptPay.billPayment("0112233445566", "R1", amount = BigDecimal.ZERO) }
        // tag 30 would exceed 99 bytes; the message names the tag
        val e = assertFailsWith<IllegalArgumentException> { PromptPay.billPayment("0112233445566", "R".repeat(60), "S".repeat(20)) }
        assertTrue(e.message!!.contains("tag 30"), e.message)
        PromptPay.billPayment("0112233445566", "X", ref3 = "X".repeat(95)) // tag 62 is exactly 99 bytes
        assertFailsWith<IllegalArgumentException> { PromptPay.billPayment("0112233445566", "R1", ref3 = "X".repeat(96)) }
    }

    @Test
    fun `non ASCII references use byte lengths`() {
        val payload = PromptPay.billPayment("0112233445566", "ลูกค้า1", "ใบแจ้งหนี้")
        val info = PromptPay.parseBillPayment(payload)
        assertEquals(BillPaymentInfo("0112233445566", "ลูกค้า1", "ใบแจ้งหนี้", null, null), info)
    }

    // --- reading ----------------------------------------------------------------------------

    @Test
    fun `reading rejects bad CRC, wrong AID, wrong 00 and bad amount`() {
        assertNotNull(PromptPay.parseAnyId(mobile))
        assertNull(PromptPay.parseAnyId(mobile.dropLast(4) + "0000"))
        assertNull(PromptPay.parseAnyId("garbage"))
        assertNull(PromptPay.parseAnyId(""))

        val qr = PromptParse.parse(mobile)!!
        assertNull(PromptPay.parseAnyId(qr.with("00", "02").encode()))
        assertNull(PromptPay.parseAnyId(qr.with("29", "0016A000000677010199").encode()))
        assertNull(PromptPay.parseAnyId(qr.with("29", "0016${aidAnyId}0404ab12").encode())) // only reserved sub-tag 04
        for (amount in listOf("1e5", "-1", "1.2.3", ".5", "5.", "", "٣٠", "NaN")) {
            assertNull(PromptPay.parseAnyId(qr.with("54", amount).encode()), amount)
        }
        assertEquals(BigDecimal("5"), PromptPay.parseAnyId(qr.with("54", "5").encode())!!.amount)
        assertEquals(BigDecimal("0.50"), PromptPay.parseAnyId(qr.with("54", "0.50").encode())!!.amount)
    }

    @Test
    fun `an MSISDN not in 0066 form is returned as written`() {
        val qr = PromptParse.parse(mobile)!!
        val odd = qr.with("29", "0016${aidAnyId}0105abcde").encode()
        assertEquals("abcde", PromptPay.parseAnyId(odd)!!.target)
        val twelve = qr.with("29", "0016${aidAnyId}0112006681222333").encode()
        assertEquals("006681222333", PromptPay.parseAnyId(twelve)!!.target)
    }

    @Test
    fun `reading bill payments requires biller and ref1`() {
        val ok = PromptPay.billPayment("0112233445566", "R1")
        assertNotNull(PromptPay.parseBillPayment(ok))
        val qr = PromptParse.parse(ok)!!
        fun withTag30(vararg sub: Tag) = qr.with("30", Tag.template("30", sub.toList()).value).encode()

        assertNull(PromptPay.parseBillPayment(withTag30(Tag("00", aidBill), Tag("01", "0112233445566"))))
        assertNull(PromptPay.parseBillPayment(withTag30(Tag("00", aidBill), Tag("01", "0112233445566"), Tag("02", ""))))
        assertNull(PromptPay.parseBillPayment(withTag30(Tag("00", aidBill), Tag("02", "R1"))))
        assertNull(PromptPay.parseBillPayment(withTag30(Tag("00", aidBill), Tag("01", ""), Tag("02", "R1"))))
        assertNull(PromptPay.parseBillPayment(withTag30(Tag("00", aidAnyId), Tag("01", "0112233445566"), Tag("02", "R1"))))
        assertNull(PromptPay.parseBillPayment(qr.with("00", "02").encode()))
        assertNull(PromptPay.parseBillPayment(qr.with("54", "x").encode()))
        assertNull(PromptPay.parseBillPayment(ok.dropLast(4) + "0000"))
    }
}
