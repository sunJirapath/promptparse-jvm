package io.github.sunjirapath.promptparse

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SemanticValidationTest {
    private val anyId = PromptPay.anyId(AnyIdType.MSISDN, "0812223333", BigDecimal("30"))
    private val bill = PromptPay.billPayment("0112233445566", "R1", amount = BigDecimal("30"))
    private val trueMoney = TrueMoney.build("0801111111", BigDecimal("30"))

    @Test
    fun `generated payloads pass validation for every payment reader`() {
        for (amount in listOf(null, BigDecimal("0.01"), BigDecimal("9999999999.99"))) {
            for ((type, target) in listOf(
                AnyIdType.MSISDN to "0812223333",
                AnyIdType.NATID to "1234567890123",
                AnyIdType.EWALLETID to "140000812223333",
            )) {
                val payload = PromptPay.anyId(type, target, amount)
                assertEquals(PromptPay.parseAnyId(payload), PromptPay.parseAnyId(payload, true))
            }
            val billPayload = PromptPay.billPayment("0112233445566", "R1", amount = amount)
            assertEquals(PromptPay.parseBillPayment(billPayload), PromptPay.parseBillPayment(billPayload, true))
            val tmPayload = TrueMoney.build("0801111111", amount)
            assertEquals(TrueMoney.parse(tmPayload), TrueMoney.parse(tmPayload, true))
        }
    }

    @ParameterizedTest
    @CsvSource("53,840", "53,''", "58,US", "58,''", "01,99", "01,''")
    fun `country, currency and initiation method are validated only on request`(
        id: String,
        value: String,
    ) {
        checkPaymentField(id, value)
    }

    @ParameterizedTest
    @ValueSource(strings = ["0", "0.00", "0.001", "1.234", "10000000000", "99999999999.99", "00000000000001"])
    fun `amount limits are validated only on request`(value: String) {
        checkPaymentField("54", value)
    }

    private fun checkPaymentField(
        id: String,
        value: String,
    ) {
        val anyPayload = PromptParse.parse(anyId)!!.with(id, value).encode()
        assertNotNull(PromptPay.parseAnyId(anyPayload))
        assertNull(PromptPay.parseAnyId(anyPayload, true))
        val billPayload = PromptParse.parse(bill)!!.with(id, value).encode()
        assertNotNull(PromptPay.parseBillPayment(billPayload))
        assertNull(PromptPay.parseBillPayment(billPayload, true))
        val tmPayload = PromptParse.parse(trueMoney)!!.with(id, value).encode()
        assertNotNull(TrueMoney.parse(tmPayload))
        assertNull(TrueMoney.parse(tmPayload, true))
    }

    @ParameterizedTest
    @CsvSource("01,abcde", "01,006681222333", "01,006681222333x", "01,9966812223333", "02,123", "03,123")
    fun `AnyID recipient shapes are validated only on request`(
        subId: String,
        target: String,
    ) {
        val account = Tag.template("29", listOf(Tag("00", AID_ANY_ID), Tag(subId, target)))
        val payload = PromptParse.parse(anyId)!!.with("29", account.value).encode()
        assertNotNull(PromptPay.parseAnyId(payload))
        assertNull(PromptPay.parseAnyId(payload, true))
    }

    @ParameterizedTest
    @ValueSource(strings = ["123", "011223344556x", "01122334455667"])
    fun `biller ID shapes are validated only on request`(billerId: String) {
        val account = Tag.template("30", listOf(Tag("00", "A000000677010112"), Tag("01", billerId), Tag("02", "R1")))
        val payload = PromptParse.parse(bill)!!.with("30", account.value).encode()
        assertNotNull(PromptPay.parseBillPayment(payload))
        assertNull(PromptPay.parseBillPayment(payload, true))
    }

    @Test
    fun `missing common payment fields are rejected when validation is enabled`() {
        for (id in listOf("01", "53", "58")) {
            val payload = EmvcoQr(PromptParse.parse(anyId)!!.tags.filter { it.id != id }).encode()
            assertNotNull(PromptPay.parseAnyId(payload))
            assertNull(PromptPay.parseAnyId(payload, true))
        }
    }

    @Test
    fun `TrueMoney fixed padding is validated only on request`() {
        val account = Tag.template("29", listOf(Tag("00", AID_ANY_ID), Tag("03", "141230801111111")))
        val payload = PromptParse.parse(trueMoney)!!.with("29", account.value).encode()
        assertNotNull(TrueMoney.parse(payload))
        assertNull(TrueMoney.parse(payload, true))
    }

    @ParameterizedTest
    @ValueSource(strings = ["0.01", "1", "1.2", "1.23", "0001.00", "9999999999.99"])
    fun `valid wire amounts need no normalization to pass`(amount: String) {
        val payload = PromptParse.parse(anyId)!!.with("54", amount).encode()
        assertEquals(BigDecimal(amount), PromptPay.parseAnyId(payload, true)!!.amount)
    }

    @Test
    fun `duplicate critical top-level and account fields are rejected by each payment reader`() {
        val fullBill = PromptPay.billPayment("0112233445566", "R1", "R2", BigDecimal("30"), "R3")
        val readers =
            listOf<Pair<String, (String, Boolean) -> Any?>>(
                anyId to PromptPay::parseAnyId,
                fullBill to PromptPay::parseBillPayment,
                trueMoney to TrueMoney::parse,
            )
        for ((original, read) in readers) {
            val qr = PromptParse.parse(original)!!
            for (id in listOf("00", "01", "29", "30", "53", "54", "58", "62")) {
                val tag = qr.tag(id) ?: continue
                val duplicate = EmvcoQr(qr.tags + tag).encode()
                assertNotNull(read(duplicate, false), id)
                assertNull(read(duplicate, true), id)
            }
            val account = qr.tag("29") ?: qr.tag("30")!!
            for (sub in account.subTags) {
                val value = Tag.template(account.id, account.subTags + sub).value
                val duplicate = qr.with(account.id, value).encode()
                assertNotNull(read(duplicate, false), sub.id)
                assertNull(read(duplicate, true), sub.id)
            }
        }
    }

    @Test
    fun `multiple recipient types and account templates are ambiguous`() {
        val qr = PromptParse.parse(anyId)!!
        val account = qr.tag("29")!!
        for (extra in listOf(Tag("02", "1234567890123"), Tag("04", "1234567890"))) {
            val payload = qr.with("29", Tag.template("29", account.subTags + extra).value).encode()
            assertNotNull(PromptPay.parseAnyId(payload))
            assertNull(PromptPay.parseAnyId(payload, true))
        }
        val billAccount = PromptParse.parse(bill)!!.tag("30")!!
        val mixed = EmvcoQr(qr.tags + billAccount).encode()
        assertNotNull(PromptPay.parseAnyId(mixed))
        assertNotNull(PromptPay.parseBillPayment(mixed))
        assertNull(PromptPay.parseAnyId(mixed, true))
        assertNull(PromptPay.parseBillPayment(mixed, true))
        val tm = PromptParse.parse(trueMoney)!!
        assertNull(TrueMoney.parse(EmvcoQr(tm.tags + billAccount).encode(), true))
    }

    @Test
    fun `additional reference duplicates including empty values are rejected`() {
        val qr = PromptParse.parse(bill)!!
        for (value in listOf("R3", "")) {
            val extra = Tag.template("62", listOf(Tag("07", "FIRST"), Tag("07", value)))
            val payload = qr.with("62", extra.value).encode()
            assertNotNull(PromptPay.parseBillPayment(payload))
            assertNull(PromptPay.parseBillPayment(payload, true))
        }
        assertNull(PromptPay.parseBillPayment(qr.with("62", "junk").encode(), true))
    }

    @Test
    fun `unknown extensions retain their permissive interpretation`() {
        val qr = PromptParse.parse(anyId)!!
        val extended = EmvcoQr(qr.tags + listOf(Tag("99", "A"), Tag("99", "B"))).encode()
        assertNotNull(PromptPay.parseAnyId(extended, true))
    }
}
