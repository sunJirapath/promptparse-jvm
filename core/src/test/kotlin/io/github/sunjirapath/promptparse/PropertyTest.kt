package io.github.sunjirapath.promptparse

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PropertyTest {
    // --- generators -------------------------------------------------------------------------

    private fun ids(): Arbitrary<String> =
        Arbitraries
            .integers()
            .between(0, 99)
            .filter { it != 63 }
            .map { "%02d".format(it) }

    /** Up to 24 chars of anything (at most 96 UTF-8 bytes, so it always fits a tag). */
    private fun anyText(): Arbitrary<String> = Arbitraries.strings().all().ofMaxLength(24)

    private fun tlvish(): Arbitrary<String> =
        Arbitraries.strings().withChars(*"0123456789ABCDEFabกé😀".toCharArray()).ofMaxLength(80)

    private fun plainTag(): Arbitrary<Tag> = ids().flatMap { id -> anyText().map { Tag.of(id, it) } }

    private fun templateTag(): Arbitrary<Tag> =
        ids().flatMap { id ->
            val sub =
                ids().flatMap { sid ->
                    Arbitraries
                        .strings()
                        .alpha()
                        .ofMinLength(1)
                        .ofMaxLength(8)
                        .map { Tag(sid, it) }
                }
            sub
                .list()
                .ofMinSize(1)
                .ofMaxSize(3)
                .map { Tag.template(id, it) }
        }

    @Provide
    fun qrs(): Arbitrary<EmvcoQr> =
        Arbitraries
            .oneOf(plainTag(), templateTag())
            .list()
            .ofMaxSize(8)
            .map { EmvcoQr(it) }

    @Provide
    fun fuzz(): Arbitrary<String> = Arbitraries.oneOf(Arbitraries.strings().all().ofMaxLength(120), tlvish())

    // --- round trip -------------------------------------------------------------------------

    @Property(tries = 2000)
    fun `parse(encode(qr)) equals qr`(
        @ForAll("qrs") qr: EmvcoQr,
    ) {
        val payload = qr.encode()
        assertEquals(qr, PromptParse.parse(payload, strict = true))
        assertEquals(qr, PromptParse.parse(payload, strict = false))
        assertTrue(PromptParse.validateCrc(payload))
        assertEquals(payload, PromptParse.parse(payload)!!.encode())
    }

    @Property(tries = 500)
    fun `generated PromptPay payloads read back`(
        @ForAll("mobiles") mobile: String,
        @ForAll("amounts") amount: BigDecimal?,
    ) {
        val payload = PromptPay.anyId(AnyIdType.MSISDN, mobile, amount)
        val info = PromptPay.parseAnyId(payload)!!
        assertEquals(mobile, info.target)
        assertEquals(amount?.setScale(2, java.math.RoundingMode.HALF_UP), info.amount)
        assertEquals(payload, PromptParse.parse(payload, strict = true)!!.encode())
    }

    @Provide
    fun mobiles(): Arbitrary<String> =
        Arbitraries
            .strings()
            .numeric()
            .ofLength(9)
            .map { "0$it" }

    @Provide
    fun amounts(): Arbitrary<BigDecimal?> =
        Arbitraries
            .bigDecimals()
            .between(BigDecimal("0.005"), BigDecimal("9999999999.99"))
            .ofScale(3)
            .injectNull(0.2)

    // --- fuzz -------------------------------------------------------------------------------

    @Property(tries = 5000)
    fun `random input never throws`(
        @ForAll("fuzz") s: String,
        @ForAll strict: Boolean,
        @ForAll subTags: Boolean,
    ) {
        PromptParse.parse(s, strict, subTags)
        PromptParse.validateCrc(s)
        PromptPay.parseAnyId(s)
        PromptPay.parseBillPayment(s)
    }

    @Property(tries = 2000)
    fun `mutated valid payloads never throw, and strict results always have a good CRC`(
        @ForAll("qrs") qr: EmvcoQr,
        @ForAll("fuzz") junk: String,
        @ForAll position: Int,
    ) {
        val payload = qr.encode()
        val at = Math.floorMod(position, payload.length + 1)
        val mutated = payload.substring(0, at) + junk.take(3) + payload.substring(minOf(at + 1, payload.length))
        val strict = PromptParse.parse(mutated, strict = true)
        if (strict != null) assertTrue(Tlv.crcMatches(Tlv.trimScan(mutated)))
        PromptParse.parse(mutated)
        PromptPay.parseAnyId(mutated)
    }

    @Property(tries = 1000)
    fun `invalid AnyID targets throw only IllegalArgumentException`(
        @ForAll("fuzz") target: String,
        @ForAll type: AnyIdType,
    ) {
        try {
            val payload = PromptPay.anyId(type, target)
            assertTrue(PromptParse.validateCrc(payload))
        } catch (expected: IllegalArgumentException) {
            // the documented failure mode
        }
    }
}
