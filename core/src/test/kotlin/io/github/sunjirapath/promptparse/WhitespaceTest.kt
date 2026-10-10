package io.github.sunjirapath.promptparse

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Scanners often append a newline (or CRLF) and copy-paste adds spaces: every parser ignores them. */
class WhitespaceTest {
    private val anyId = PromptPay.anyId(AnyIdType.MSISDN, "0812223333", BigDecimal("30"))
    private val bill = PromptPay.billPayment("0112233445566", "CUSTOMER001", "INV001")
    private val slip = SlipVerify.build("002", "0002123123121200011")
    private val trueMoneySlip = SlipVerify.buildTrueMoney("P2P", "TXN0001234567", "25012024")
    private val trueMoney = TrueMoney.build("0801111111", BigDecimal("10.05"), "Hi")
    private val barcode = "|099400016550100\r123456789012\r670429\r364922"

    @ParameterizedTest
    @ValueSource(strings = ["\n", "\r\n", "\r", " ", "\t", " \r\n", "\n\n", "  \t\r\n  "])
    fun `strict readers ignore surrounding whitespace`(ws: String) {
        assertEquals(PromptParse.parse(anyId, strict = true), PromptParse.parse(ws + anyId + ws, strict = true))
        assertEquals(anyId, PromptParse.parse(ws + anyId + ws, strict = true)!!.encode())
        assertEquals("0812223333", PromptPay.parseAnyId(ws + anyId + ws)!!.target)
        assertEquals("CUSTOMER001", PromptPay.parseBillPayment(ws + bill + ws)!!.ref1)
        assertEquals("0801111111", TrueMoney.parse(ws + trueMoney + ws)!!.mobile)
        assertEquals("002", SlipVerify.parse(ws + slip + ws)!!.sendingBank)
        assertEquals("P2P", SlipVerify.parseTrueMoney(ws + trueMoneySlip + ws)!!.eventType)
        assertEquals(true, PromptParse.validateCrc(ws + anyId + ws))
    }

    @ParameterizedTest
    @ValueSource(strings = ["\n", "\r\n", "\r", " ", "\t"])
    fun `a shortened CRC is still repaired when whitespace surrounds it`(ws: String) {
        val full = SlipVerify.build("014", "T00000086") // CRC 0539
        val short = full.dropLast(4) + "539"
        assertEquals("T00000086", SlipVerify.parse(ws + short + ws)!!.transRef)
        assertNull(SlipVerify.parse(ws + short + ws, crcAutoFix = false))
        val payment =
            (1..400)
                .map { PromptPay.anyId(AnyIdType.MSISDN, "0812223333", BigDecimal(it)) }
                .first { it.takeLast(4).startsWith("0") }
        assertEquals(payment, PromptParse.fixTruncatedCrc(ws + payment.dropLast(4) + payment.takeLast(3) + ws))
    }

    @ParameterizedTest
    @ValueSource(strings = ["\n", "\r\n"])
    fun `lenient parse drops a trailing line break and leading whitespace and returns the trimmed payload`(nl: String) {
        val body = "000201" + "0104" + "1234" // no CRC
        val qr = assertNotNull(PromptParse.parse("  $body$nl"))
        assertEquals(listOf("00", "01"), qr.tags.map { it.id })
        assertEquals("1234", qr.value("01"))
        assertEquals(PromptParse.parse(body), qr)
    }

    @ParameterizedTest
    @ValueSource(strings = ["\n", "\r\n"])
    fun `a barcode may end in the scanner's newline`(nl: String) {
        assertEquals("670429", BotBarcode.parse(barcode + nl)!!.ref2)
        assertEquals("670429", BotBarcode.parse("  $barcode$nl")!!.ref2)
        assertEquals(BotBarcode.parse(barcode), BotBarcode.parse(barcode + nl))
    }

    @ParameterizedTest
    @ValueSource(strings = ["", " ", "\n", "\r\n", "  \t \r\n"])
    fun `only whitespace is not a payload`(ws: String) {
        assertNull(PromptParse.parse(ws))
        assertNull(PromptParse.parse(ws, strict = true))
        assertNull(SlipVerify.parse(ws))
        assertNull(SlipVerify.parseTrueMoney(ws))
        assertNull(TrueMoney.parse(ws))
        assertNull(PromptPay.parseAnyId(ws))
        assertNull(BotBarcode.parse(ws))
        assertNull(PromptParse.fixTruncatedCrc(ws))
        assertEquals(false, PromptParse.validateCrc(ws))
    }

    @ParameterizedTest
    @ValueSource(strings = ["0 ", " ", "​", "　", "\u000b", "\u000c", "\u0085"])
    fun `other whitespace-like characters are not trimmed`(extra: String) {
        assertNull(PromptParse.parse(anyId + extra, strict = true))
        assertNull(SlipVerify.parse(slip + extra))
        assertNull(PromptParse.parse(extra + anyId, strict = true))
    }

    @ParameterizedTest
    @ValueSource(strings = ["X", "ab", "0"])
    fun `whitespace inside a payload is data, not trimmed`(mid: String) {
        assertNull(PromptParse.parse(anyId.substring(0, 10) + " $mid " + anyId.substring(10), strict = true))
    }
}
