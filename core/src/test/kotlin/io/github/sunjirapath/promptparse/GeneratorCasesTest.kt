@file:Suppress("ktlint:standard:max-line-length") // test vectors are long payload strings

package io.github.sunjirapath.promptparse

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Table-driven edge cases for every generator: lengths, amounts, normalization forms. */
class GeneratorCasesTest {
    private fun amountOf(payload: String) = PromptParse.parse(payload, strict = true)!!.value("54")

    // --- amounts, across every generator that takes one --------------------------------------

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "0.01,0.01",
        "0.005,0.01",
        "0.0049999,0.00",
        "0.015,0.02",
        "0.994999,0.99",
        "0.995,1.00",
        "1,1.00",
        "1.5,1.50",
        "100,100.00",
        "1E+2,100.00",
        "123456.785,123456.79",
        "9999999999.99,9999999999.99",
        "9999999999.994,9999999999.99",
    )
    fun `amounts are rounded HALF_UP to 2 decimals or rejected when they become zero`(
        input: String,
        expected: String,
    ) {
        val amount = BigDecimal(input)
        if (expected == "0.00") {
            assertFailsWith<IllegalArgumentException> { PromptPay.anyId(AnyIdType.MSISDN, "0812223333", amount) }
            assertFailsWith<IllegalArgumentException> { PromptPay.billPayment("0112233445566", "R", amount = amount) }
            assertFailsWith<IllegalArgumentException> { TrueMoney.build("0801111111", amount) }
            assertFailsWith<IllegalArgumentException> { BotBarcode.build("099400016550100", "R", null, amount) }
            return
        }
        assertEquals(expected, amountOf(PromptPay.anyId(AnyIdType.NATID, "1234567890123", amount)))
        assertEquals(expected, amountOf(PromptPay.billPayment("0112233445566", "R", amount = amount)))
        assertEquals(expected, amountOf(TrueMoney.build("0801111111", amount)))
        val satang = BotBarcode.build("099400016550100", "R", null, amount).substringAfterLast('\r')
        assertEquals(expected.replace(".", "").trimStart('0'), satang)
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            "0", "0.00", "-0.00", "-0.01", "-1", "-9999999999.99", "0.0049", "0E+5", "0E-5",
            "10000000000", "10000000000.00", "9999999999.995", "99999999999", "1E+10", "1E+999999999",
            "1E-999999999", "-1E-999999999", "-1E+999999999", "0.001", "0.0001",
        ],
    )
    fun `zero, negative, tiny and oversized amounts are rejected by every generator`(input: String) {
        val amount = BigDecimal(input)
        assertFailsWith<IllegalArgumentException> { PromptPay.anyId(AnyIdType.MSISDN, "0812223333", amount) }
        assertFailsWith<IllegalArgumentException> { PromptPay.billPayment("0112233445566", "R", amount = amount) }
        assertFailsWith<IllegalArgumentException> { TrueMoney.build("0801111111", amount) }
        assertFailsWith<IllegalArgumentException> { BotBarcode.build("099400016550100", "R", null, amount) }
    }

    @ParameterizedTest
    @ValueSource(strings = ["0812223333", "1234567890123", "140000812223333"])
    fun `a null amount gives a static QR for every AnyID type`(target: String) {
        val type =
            when (target.length) {
                10 -> AnyIdType.MSISDN
                13 -> AnyIdType.NATID
                else -> AnyIdType.EWALLETID
            }
        val qr = PromptParse.parse(PromptPay.anyId(type, target, null), strict = true)!!
        assertEquals("11", qr.value("01"))
        assertNull(qr.tag("54"))
    }

    // --- AnyID normalization ----------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = [
            "0812223333", "081-222-3333", "081 222 3333", "08-1222-3333", "+66812223333", "+66 81 222 3333",
            "+66-81-222-3333", "66812223333", "66-81-222-3333", "0066812223333", "0066-81-222-3333",
            "0612223333", "0912223333",
        ],
    )
    fun `every accepted mobile spelling normalizes to 0066 plus nine digits`(input: String) {
        val national = input.filter { it.isDigit() }.takeLast(9)
        val payload = PromptPay.anyId(AnyIdType.MSISDN, input)
        assertEquals("0066$national", PromptParse.parse(payload, strict = true)!!.value("29", "01"))
        assertEquals("0$national", PromptPay.parseAnyId(payload)!!.target)
    }

    @ParameterizedTest(name = "case {index}")
    @ValueSource(
        strings = [
            "", " ", "0", "08122233", "081222333", "08122233334", "812223333", "+812223333", "+0812223333",
            "++66812223333", "+66+812223333", "0812-2233-33x", "66 8122233", "006681222333", "00668122233334",
            "(081) 222-3333", "081.222.3333", "٠٨١٢٢٢٣٣٣٣", "０８１２２２３３３３", "0812223333\n",
            "tel:0812223333",
        ],
    )
    fun `anything else is rejected for MSISDN with a clear message`(input: String) {
        val e = assertFailsWith<IllegalArgumentException> { PromptPay.anyId(AnyIdType.MSISDN, input) }
        assertTrue(e.message!!.startsWith("MSISDN must be a Thai mobile number"), e.message)
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        "NATID,1234567890123,true",
        "NATID,1-2345-67890-12-3,true",
        "NATID,1 2345 67890 12 3,true",
        "NATID,123456789012,false",
        "NATID,12345678901234,false",
        "NATID,123456789012a,false",
        "NATID,0000000000000,true",
        "EWALLETID,140000812223333,true",
        "EWALLETID,14-0000-8122-2333-3,true",
        "EWALLETID,14000081222333,false",
        "EWALLETID,1400008122233334,false",
        "EWALLETID,14000081222333x,false",
        "EWALLETID,1234567890123,false",
    )
    fun `national and e-wallet IDs have fixed lengths`(
        type: AnyIdType,
        target: String,
        valid: Boolean,
    ) {
        if (valid) {
            val payload = PromptPay.anyId(type, target)
            assertEquals(target.filter { it.isDigit() }, PromptPay.parseAnyId(payload)!!.target)
            assertEquals(type, PromptPay.parseAnyId(payload)!!.type)
        } else {
            assertFailsWith<IllegalArgumentException> { PromptPay.anyId(type, target) }
        }
    }

    // --- Bill Payment limits ----------------------------------------------------------------

    // tag 30 value = AID (4 + 16) + biller (4 + n) + ref1 (4 + a) [+ ref2 (4 + b)] and may hold 99 bytes at most
    @ParameterizedTest(name = "biller {0}")
    @CsvSource("0112233445566,58", "010753600031508,56")
    fun `ref1 can fill tag 30 exactly and not by one byte more`(
        biller: String,
        maxRef1: Int,
    ) {
        val ok = PromptPay.billPayment(biller, "R".repeat(maxRef1))
        assertEquals("R".repeat(maxRef1), PromptPay.parseBillPayment(ok)!!.ref1)
        assertFailsWith<IllegalArgumentException> { PromptPay.billPayment(biller, "R".repeat(maxRef1 + 1)) }
    }

    @ParameterizedTest(name = "ref1 of {0}")
    @CsvSource("1,53", "10,44", "30,24")
    fun `ref2 fills what ref1 leaves`(
        ref1Length: Int,
        maxRef2: Int,
    ) {
        val ref1 = "R".repeat(ref1Length)
        val ok = PromptPay.billPayment("0112233445566", ref1, "S".repeat(maxRef2))
        assertEquals("S".repeat(maxRef2), PromptPay.parseBillPayment(ok)!!.ref2)
        assertFailsWith<IllegalArgumentException> {
            PromptPay.billPayment(
                "0112233445566",
                ref1,
                "S".repeat(maxRef2 + 1),
            )
        }
    }

    @ParameterizedTest(name = "{0} chars of {1} bytes")
    @CsvSource("19,ก,57", "20,ก,60", "14,é,28", "1,😀,4")
    fun `reference limits count UTF-8 bytes`(
        count: Int,
        char: String,
        bytes: Int,
    ) {
        val ref = char.repeat(count)
        assertEquals(bytes, Tlv.utf8Length(ref))
        if (bytes <= 58) {
            assertEquals(ref, PromptPay.parseBillPayment(PromptPay.billPayment("0112233445566", ref))!!.ref1)
        } else {
            assertFailsWith<IllegalArgumentException> { PromptPay.billPayment("0112233445566", ref) }
        }
    }

    @ParameterizedTest(name = "case {index}")
    @ValueSource(
        strings = ["", "0", "01122334455", "011223344556", "01122334455667", "0112233445566789", "011223344556x", "01122334 5566"],
    )
    fun `billerId must be 13 or 15 digits`(biller: String) {
        assertFailsWith<IllegalArgumentException> { PromptPay.billPayment(biller, "R") }
    }

    // --- slip / barcode / TrueMoney limits ---------------------------------------------

    // root tag 00: markers (4 + 6) + bank (4 + 3) + ref (4 + n) = 21 + n
    @ParameterizedTest
    @CsvSource("78,true", "79,false", "1,true")
    fun `slip transRef length limit`(
        length: Int,
        ok: Boolean,
    ) {
        val ref = "T".repeat(length)
        if (ok) {
            assertEquals(ref, SlipVerify.parse(SlipVerify.build("002", ref))!!.transRef)
        } else {
            assertFailsWith<IllegalArgumentException> { SlipVerify.build("002", ref) }
        }
    }

    // TrueMoney root: markers 12 + date 12 + event (4 + e) + transaction (4 + t) = 32 + e + t
    @ParameterizedTest
    @CsvSource("1,66,true", "33,34,true", "34,34,false", "60,8,false", "1,1,true")
    fun `TrueMoney slip field length limit`(
        event: Int,
        txn: Int,
        ok: Boolean,
    ) {
        val call = { SlipVerify.buildTrueMoney("E".repeat(event), "T".repeat(txn), "01012025") }
        if (ok) {
            assertEquals("E".repeat(event), SlipVerify.parseTrueMoney(call())!!.eventType)
        } else {
            assertFailsWith<IllegalArgumentException> { call() }
        }
    }

    @ParameterizedTest
    @CsvSource(
        "1,000000000000001",
        "12345,000000000012345",
        "099400016550100,099400016550100",
        "999999999999999,999999999999999",
    )
    fun `barcode biller ids are padded to 15 digits`(
        input: String,
        padded: String,
    ) {
        val text = BotBarcode.build(input, "R")
        assertEquals("|$padded\rR\r\r0", text)
        assertEquals(15, BotBarcode.parse(text)!!.billerId.length)
    }

    @ParameterizedTest
    @ValueSource(strings = ["TrueMoney message", "สวัสดี", "😀😀", "a", "x\ty", "line1 line2"])
    fun `TrueMoney message survives the round trip`(message: String) {
        assertEquals(message, TrueMoney.parse(TrueMoney.build("0801111111", null, message))!!.message)
    }
}
