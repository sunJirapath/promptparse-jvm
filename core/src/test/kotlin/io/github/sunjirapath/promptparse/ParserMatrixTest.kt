@file:Suppress("ktlint:standard:max-line-length") // test vectors are long payload strings

package io.github.sunjirapath.promptparse

import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.math.BigDecimal
import java.time.Duration
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The same hostile inputs thrown at every public parser: valid, wrong CRC, shortened CRC, lowercase CRC,
 * broken length fields, truncation, garbage, empty, huge. Parsers must return `null` and never throw or hang.
 */
class ParserMatrixTest {
    class Parser(
        val name: String,
        val valid: String,
        /** CRC tag id, or `null` for formats without a CRC. */
        val crcTag: String?,
        val lowerCaseCrc: Boolean,
        val parse: (String) -> Any?,
    ) {
        override fun toString() = name
    }

    companion object {
        private val anyId = PromptPay.anyId(AnyIdType.MSISDN, "0812223333", BigDecimal("30"))
        private val bill = PromptPay.billPayment("0112233445566", "CUSTOMER001", "INV001", BigDecimal("250"), "SCB")
        private val trueMoney = TrueMoney.build("0801111111", BigDecimal("10.05"), "Hello World!")
        private val slip = SlipVerify.build("002", "0002123123121200011")
        private val trueMoneySlip = SlipVerify.buildTrueMoney("P2P", "TXN0001234567", "25012024")
        private const val BARCODE = "|099400016550100\r123456789012\r670429\r364922"

        @JvmStatic
        fun parsers(): List<Arguments> =
            listOf(
                Parser("PromptParse.parse(strict)", anyId, "63", false) { PromptParse.parse(it, strict = true) },
                Parser("PromptPay.parseAnyId", anyId, "63", false, PromptPay::parseAnyId),
                Parser("PromptPay.parseBillPayment", bill, "63", false, PromptPay::parseBillPayment),
                Parser("TrueMoney.parse", trueMoney, "63", false, TrueMoney::parse),
                Parser("SlipVerify.parse", slip, "91", false) { SlipVerify.parse(it) },
                Parser("SlipVerify.parseTrueMoney", trueMoneySlip, "91", true) { SlipVerify.parseTrueMoney(it) },
                Parser("BotBarcode.parse", BARCODE, null, false, BotBarcode::parse),
            ).map { Arguments.of(it) }

        /** [body] + CRC tag header + CRC, in the case the format uses. */
        private fun sealed(
            body: String,
            tag: String,
            lower: Boolean,
        ): String {
            val seed = "$body${tag}04"
            val crc = Tlv.checksum(seed)
            return seed + if (lower) crc.lowercase() else crc
        }

        private fun sealedBody(
            body: String,
            tag: String = "63",
            lower: Boolean = false,
        ): String = sealed(body, tag, lower)

        @JvmStatic
        fun duplicates(): List<Arguments> {
            val anyIdBody = PromptPay.anyId(AnyIdType.MSISDN, "0812223333", BigDecimal("30")).dropLast(8)
            val billBody = PromptPay.billPayment("0112233445566", "FIRST").dropLast(8)
            val slipRoot = "0006000001" + "0103002" + "0203REF" + "0103099"
            val slipBody = "00%02d%s5102TH".format(slipRoot.length, slipRoot)
            return listOf(
                Arguments.of("amount", sealedBody(anyIdBody + "540599.00", "63", false), BigDecimal("30.00")),
                Arguments.of("slip bank", sealedBody(slipBody, "91", false), "002"),
                Arguments.of(
                    "bill ref1",
                    sealedBody(billBody + tag("30", "0016A000000677010112" + "0201X"), "63", false),
                    "FIRST",
                ),
            )
        }

        private const val AID1 = "0016A000000677010111"
        private const val AID2 = "0016A000000677010112"

        private fun tag(
            id: String,
            value: String,
        ) = "%s%02d%s".format(id, value.length, value)

        private const val HEAD = "000201" + "010211"
        private const val TAIL = "5303764" + "5802TH"

        @JvmStatic
        fun missingRequired(): List<Arguments> {
            val anyIdReader: (String) -> Any? = PromptPay::parseAnyId
            val billReader: (String) -> Any? = PromptPay::parseBillPayment
            val tmReader: (String) -> Any? = TrueMoney::parse
            val slipReader: (String) -> Any? = { SlipVerify.parse(it) }
            val tmSlipReader: (String) -> Any? = { SlipVerify.parseTrueMoney(it) }
            return listOf(
                Arguments.of(
                    "anyId: no tag 00",
                    sealedBody("010211" + tag("29", AID1 + "0113" + "0066812223333") + TAIL),
                    anyIdReader,
                ),
                Arguments.of("anyId: no tag 29", sealedBody(HEAD + TAIL), anyIdReader),
                Arguments.of(
                    "anyId: no AID",
                    sealedBody(HEAD + tag("29", "0113" + "0066812223333") + TAIL),
                    anyIdReader,
                ),
                Arguments.of("anyId: no recipient", sealedBody(HEAD + tag("29", AID1) + TAIL), anyIdReader),
                Arguments.of(
                    "anyId: wrong payload format",
                    sealedBody("000202" + "010211" + tag("29", AID1 + "0113" + "0066812223333") + TAIL),
                    anyIdReader,
                ),
                Arguments.of("bill: no biller", sealedBody(HEAD + tag("30", AID2 + "0205REF01") + TAIL), billReader),
                Arguments.of(
                    "bill: no ref1",
                    sealedBody(HEAD + tag("30", AID2 + "0113" + "0112233445566") + TAIL),
                    billReader,
                ),
                Arguments.of("bill: no tag 30", sealedBody(HEAD + TAIL), billReader),
                Arguments.of("trueMoney: no wallet sub-tag", sealedBody(HEAD + tag("29", AID1) + TAIL), tmReader),
                Arguments.of("trueMoney: no tag 29", sealedBody(HEAD + TAIL), tmReader),
                Arguments.of("slip: no root tag", sealedBody("5102TH", "91"), slipReader),
                Arguments.of(
                    "slip: no sending bank",
                    sealedBody(tag("00", "0006000001" + "0203REF") + "5102TH", "91"),
                    slipReader,
                ),
                Arguments.of(
                    "slip: no reference",
                    sealedBody(tag("00", "0006000001" + "0103002") + "5102TH", "91"),
                    slipReader,
                ),
                Arguments.of(
                    "slip: no marker",
                    sealedBody(tag("00", "0103002" + "0203REF") + "5102TH", "91"),
                    slipReader,
                ),
                Arguments.of(
                    "trueMoney slip: no date",
                    sealedBody(
                        tag(
                            "00",
                            "00020101" + "020201" + "0203P2P" + "0303T01",
                        ),
                        "91",
                        true,
                    ),
                    tmSlipReader,
                ),
                Arguments.of(
                    "trueMoney slip: one marker",
                    sealedBody(
                        tag(
                            "00",
                            "00020101" + "0203P2P" + "0303T01" + "040801012025",
                        ),
                        "91",
                        true,
                    ),
                    tmSlipReader,
                ),
            )
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parsers")
    fun `the valid sample parses`(p: Parser) {
        assertNotNull(p.parse(p.valid))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parsers")
    fun `empty, blank and garbage input give null`(p: Parser) {
        for (bad in listOf(
            "",
            " ",
            "\n",
            "0",
            "|",
            "Z".repeat(60),
            "0".repeat(60),
            "\u0000",
            "\uD800",
            "ก".repeat(30),
        )) {
            assertNull(p.parse(bad), "'$bad'")
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parsers")
    fun `no prefix of a valid payload throws, and shorter-than-CRC prefixes are rejected`(p: Parser) {
        for (n in 0 until p.valid.length) {
            val prefix = p.valid.substring(0, n)
            val result = p.parse(prefix) // must not throw
            if (p.crcTag != null && n < p.valid.length - 4) assertNull(result, "prefix of $n chars")
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parsers")
    fun `a wrong CRC is rejected`(p: Parser) {
        if (p.crcTag == null) return
        val body = p.valid.dropLast(4)
        for (bad in listOf("0000", "FFFF", "ffff", "XXXX", "1234", "", "12345")) {
            if (bad == p.valid.takeLast(4)) continue
            assertNull(p.parse(body + bad), "crc '$bad'")
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parsers")
    fun `CRC hex in lowercase is accepted`(p: Parser) {
        if (p.crcTag == null) return
        val lower = p.valid.dropLast(4) + p.valid.takeLast(4).lowercase()
        assertNotNull(p.parse(lower))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parsers")
    fun `a length field that overflows, underflows or is not a number is rejected even with a correct CRC`(p: Parser) {
        val crcTag = p.crcTag ?: return
        val body = p.valid.dropLast(8)
        val declared = body.substring(2, 4).toInt()
        val lengths = listOf((declared + 1).toString().padStart(2, '0'), (declared - 1).toString().padStart(2, '0'))
        for (len in lengths + listOf("ab", "-1", "+5", "9x", "  ", "٣٣")) {
            val mutated = body.substring(0, 2) + len + body.substring(4)
            assertNull(p.parse(sealed(mutated, crcTag, p.lowerCaseCrc)), "length '$len'")
        }
        assertNull(p.parse(sealed(body.substring(0, 2) + "99" + body.substring(4), crcTag, p.lowerCaseCrc)))
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parsers")
    fun `huge input finishes quickly`(p: Parser) {
        val inputs = listOf("0".repeat(3_000_000), "0004abcd".repeat(400_000), "|" + "\r".repeat(2_000_000))
        assertTimeoutPreemptively(Duration.ofSeconds(20)) {
            for (input in inputs) assertNull(p.parse(input))
            assertNull(p.parse(p.valid + "0".repeat(3_000_000)))
            assertNull(p.parse("0".repeat(3_000_000) + p.valid))
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parsers")
    fun `deeply repeated valid payloads are not a hang or a false positive`(p: Parser) {
        assertTimeoutPreemptively(Duration.ofSeconds(20)) {
            assertNull(p.parse(p.valid.repeat(50_000)))
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parsers")
    fun `swapping the characters of a payload does not throw`(p: Parser) {
        val chars = p.valid.toCharArray()
        for (i in 0 until chars.size - 1) {
            val c = chars.copyOf()
            c[i] = chars[i + 1]
            c[i + 1] = chars[i]
            p.parse(String(c)) // must not throw
        }
    }

    // --- parser-specific structure ----------------------------------------------------------

    @ParameterizedTest
    @MethodSource("duplicates")
    fun `with duplicate tags the first occurrence wins`(
        name: String,
        payload: String,
        first: Any?,
    ) {
        assertEquals(
            first,
            when (name) {
                "amount" -> PromptPay.parseAnyId(payload)!!.amount
                "slip bank" -> SlipVerify.parse(payload)!!.sendingBank
                "bill ref1" -> PromptPay.parseBillPayment(payload)!!.ref1
                else -> error(name)
            },
        )
    }

    @ParameterizedTest
    @MethodSource("missingRequired")
    fun `a payload without a required tag gives null`(
        label: String,
        payload: String,
        reader: (String) -> Any?,
    ) {
        assertNull(reader(payload), label)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("parsers")
    fun `non-ASCII junk inside a payload is rejected, not thrown on`(p: Parser) {
        for (i in listOf(0, 1, 5, p.valid.length / 2, p.valid.length - 1)) {
            val mutated = p.valid.substring(0, i) + "ก😀" + p.valid.substring(i)
            p.parse(mutated)
        }
        if (p.crcTag != null) assertNull(p.parse(p.valid.dropLast(1) + "ก"))
    }
}
