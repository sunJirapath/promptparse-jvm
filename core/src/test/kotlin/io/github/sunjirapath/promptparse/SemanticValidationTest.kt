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
}
