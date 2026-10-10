package io.github.sunjirapath.promptparse

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Vectors from promptparse (`index.test.ts:67-87,102-120,150-176`) and thai-qr-payment (MIT); see NOTICE. */
class TrueMoneyAndBarcodeTest {

    // --- TrueMoney QR -----------------------------------------------------------------------

    @Test
    fun `TrueMoney QR matches both references`() {
        assertEquals(
            "00020101021129390016A000000677010111031514000080111111153037645802TH63047C0F",
            TrueMoney.build("0801111111"),
        )
        assertEquals(
            "00020101021229390016A000000677010111031514000080111111153037645802TH540510.05" +
                "814800480065006C006C006F00200057006F0072006C006400216304F5A2",
            TrueMoney.build("0801111111", BigDecimal("10.05"), "Hello World!"),
        )
    }

    @Test
    fun `every mobile spelling gives the same QR`() {
        val expected = TrueMoney.build("0801111111")
        for (m in listOf("080-111-1111", "+66801111111", "66801111111", "0066801111111")) {
            assertEquals(expected, TrueMoney.build(m), m)
        }
        assertFailsWith<IllegalArgumentException> { TrueMoney.build("12345") }
    }

    @Test
    fun `TrueMoney QR reads back`() {
        val payload = TrueMoney.build("0801111111", BigDecimal("10.05"), "สวัสดี 😀")
        assertEquals(TrueMoneyInfo("0801111111", BigDecimal("10.05"), "สวัสดี 😀"), TrueMoney.parse(payload))
        assertEquals(TrueMoneyInfo("0801111111", null, null), TrueMoney.parse(TrueMoney.build("0801111111", message = "")))
    }

    @Test
    fun `TrueMoney message limits and bad hex`() {
        TrueMoney.build("0801111111", message = "x".repeat(24))
        assertFailsWith<IllegalArgumentException> { TrueMoney.build("0801111111", message = "x".repeat(25)) }
        assertFailsWith<IllegalArgumentException> { TrueMoney.build("0801111111", amount = BigDecimal.ZERO) }
        val base = PromptParse.parse(TrueMoney.build("0801111111"))!!
        for (hex in listOf("004", "00ZZ", "+041", "")) {
            assertEquals(null, TrueMoney.parse(base.with("81", hex).encode())!!.message, hex)
        }
    }

    @Test
    fun `TrueMoney reader rejects what is not TrueMoney`() {
        assertNull(TrueMoney.parse(PromptPay.anyId(AnyIdType.MSISDN, "0812223333")))
        assertNull(TrueMoney.parse(PromptPay.anyId(AnyIdType.EWALLETID, "140000812223333").dropLast(4) + "0000"))
        assertNull(TrueMoney.parse(PromptPay.billPayment("0112233445566", "R1")))
        assertNull(TrueMoney.parse("garbage"))
        val base = PromptParse.parse(TrueMoney.build("0801111111"))!!
        assertNull(TrueMoney.parse(base.with("29", "0016A000000677010199" + "031514000080111111").encode()))
        assertNull(TrueMoney.parse(base.with("54", "x").encode()))
        assertNull(TrueMoney.parse(base.with("29", "0016A0000006770101110315" + "15000080111111").encode())) // prefix 15
        // a plain e-wallet QR is the same shape but without the 14 prefix
        assertNull(TrueMoney.parse(PromptPay.anyId(AnyIdType.EWALLETID, "150000812223333")))
    }

    // --- BOT barcode ------------------------------------------------------------------------

    @Test
    fun `barcode build matches both references`() {
        assertEquals("|099999999999990\r111222333444\r\r0", BotBarcode.build("099999999999990", "111222333444"))
        assertEquals(
            "|099400016550100\r123456789012\r670429\r364922",
            BotBarcode.build("099400016550100", "123456789012", "670429", BigDecimal("3649.22")),
        )
    }

    @Test
    fun `barcode amounts are whole satang`() {
        fun amount(a: String) = BotBarcode.build("099400016550100", "R", null, BigDecimal(a)).substringAfterLast('\r')
        assertEquals("50", amount("0.5"))
        assertEquals("1", amount("0.005"))
        assertEquals("100", amount("1"))
        assertEquals("999999999999", amount("9999999999.99"))
        for (bad in listOf("0", "-1", "0.004", "10000000000")) {
            assertFailsWith<IllegalArgumentException>(bad) { BotBarcode.build("099400016550100", "R", null, BigDecimal(bad)) }
        }
    }

    @Test
    fun `barcode builder pads and validates`() {
        assertEquals("|000000000012345\rR\r\r0", BotBarcode.build("12345", "R"))
        assertEquals("|099400016550100\rR\r\r0", BotBarcode.build("099400016550100", "R", ""))
        for (bad in listOf("", "0994000165501001", "09940001655010x")) {
            assertFailsWith<IllegalArgumentException>(bad) { BotBarcode.build(bad, "R") }
        }
        assertFailsWith<IllegalArgumentException> { BotBarcode.build("099400016550100", "") }
        assertFailsWith<IllegalArgumentException> { BotBarcode.build("099400016550100", "a\rb") }
        assertFailsWith<IllegalArgumentException> { BotBarcode.build("099400016550100", "a", "b\rc") }
    }

    @Test
    fun `barcode parse`() {
        assertEquals(
            BotBarcodeInfo("099999999999990", "111222333444", null, null),
            BotBarcode.parse("|099999999999990\r111222333444\r\r0"),
        )
        assertEquals(
            BotBarcodeInfo("099400016550100", "123456789012", "670429", BigDecimal("3649.22")),
            BotBarcode.parse("|099400016550100\r123456789012\r670429\r364922"),
        )
        assertEquals(null, BotBarcode.parse("|099999999999990\rR\r\r000")!!.amount) // all zeros = none
    }

    @Test
    fun `barcode parse rejects malformed input`() {
        for (bad in listOf(
            "", "|", "099999999999990\r111\r\r0", "|099999999999990\r111\r\r", "|099999999999990\r111\r0",
            "|099999999999990\r111\r\r0\r", "|0999\r111\r\r0", "|099999999999990\r\r\r0", "|099999999999990\r111\r\r12.5",
            "|099999999999990\r111\r\r-5", "|099999999999990\r111\r\r1234567890123", "|099999999999990\r111\r\r٣",
            "00020101021230650016A00000067701011201150994000165501000212123456789012030667042953037645802TH54073649.2263044534",
            "|099400016550100\r123456789012\r670429",
        )) {
            assertNull(BotBarcode.parse(bad), bad.replace("\r", "\\r"))
        }
    }

    @Test
    fun `barcode converts to bill payment like the reference`() {
        assertEquals(
            "00020101021130550016A0000006770101120115099999999999990021211122233344453037645802TH63043EE7",
            BotBarcode.parse("|099999999999990\r111222333444\r\r0")!!.toBillPayment(),
        )
        assertEquals(
            "00020101021230650016A00000067701011201150994000165501000212123456789012030667042953037645802TH54073649.2263044534",
            BotBarcode.parse("|099400016550100\r123456789012\r670429\r364922")!!.toBillPayment(),
        )
        assertTrue(PromptPay.parseBillPayment(BotBarcode.parse("|099400016550100\rR\rS\r100")!!.toBillPayment())!!.amount == BigDecimal("1.00"))
        assertFailsWith<IllegalArgumentException> { BotBarcode.parse("|09940001655010000\rR\r\r0")!!.toBillPayment() }
    }
}
