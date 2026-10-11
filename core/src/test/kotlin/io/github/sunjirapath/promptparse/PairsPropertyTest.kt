@file:Suppress("ktlint:standard:max-line-length") // test vectors are long payload strings

package io.github.sunjirapath.promptparse

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Round trips for every public generator/parser pair that [PropertyTest] and [SlipPropertyTest] do not cover. */
class PairsPropertyTest {
    @Provide
    fun amounts(): Arbitrary<BigDecimal?> =
        Arbitraries
            .bigDecimals()
            .between(BigDecimal("0.005"), BigDecimal("9999999999.99"))
            .ofScale(3)
            .injectNull(0.2)

    @Provide
    fun digits13(): Arbitrary<String> = Arbitraries.strings().numeric().ofLength(13)

    @Provide
    fun digits15(): Arbitrary<String> = Arbitraries.strings().numeric().ofLength(15)

    @Provide
    fun mobiles(): Arbitrary<String> = Arbitraries.strings().numeric().ofLength(9)

    @Provide
    fun refs(): Arbitrary<String?> =
        Arbitraries
            .strings()
            .withChars(*"AZaz09-ก".toCharArray())
            .ofMinLength(1)
            .ofMaxLength(12)
            .injectNull(0.3)

    private fun rounded(a: BigDecimal?) = a?.setScale(2, RoundingMode.HALF_UP)

    @Property(tries = 500)
    fun `national ID and e-wallet ID round trip`(
        @ForAll("digits13") nat: String,
        @ForAll("digits15") wallet: String,
        @ForAll("amounts") amount: BigDecimal?,
    ) {
        assertEquals(
            AnyIdInfo(AnyIdType.NATID, nat, rounded(amount)),
            PromptPay.parseAnyId(PromptPay.anyId(AnyIdType.NATID, nat, amount)),
        )
        assertEquals(
            AnyIdInfo(AnyIdType.EWALLETID, wallet, rounded(amount)),
            PromptPay.parseAnyId(PromptPay.anyId(AnyIdType.EWALLETID, wallet, amount)),
        )
    }

    @Property(tries = 500)
    fun `every mobile spelling round trips to the local form`(
        @ForAll("mobiles") national: String,
    ) {
        for (spelling in listOf(
            "0$national",
            "66$national",
            "+66$national",
            "0066$national",
            "0${national.take(2)}-${national.drop(2)}",
        )) {
            assertEquals(
                "0$national",
                PromptPay.parseAnyId(PromptPay.anyId(AnyIdType.MSISDN, spelling))!!.target,
                spelling,
            )
        }
    }

    @Property(tries = 500)
    fun `bill payment round trips`(
        @ForAll("digits13") biller: String,
        @ForAll("refs") ref1: String?,
        @ForAll("refs") ref2: String?,
        @ForAll("refs") ref3: String?,
        @ForAll("amounts") amount: BigDecimal?,
    ) {
        val r1 = ref1 ?: "R1"
        val payload = PromptPay.billPayment(biller, r1, ref2, amount, ref3)
        assertEquals(BillPaymentInfo(biller, r1, ref2, ref3, rounded(amount)), PromptPay.parseBillPayment(payload))
        assertEquals(payload, PromptParse.parse(payload, strict = true)!!.encode())
        assertTrue(PromptParse.validateCrc(payload))
    }

    @Property(tries = 500)
    fun `TrueMoney round trips`(
        @ForAll("mobiles") national: String,
        @ForAll("amounts") amount: BigDecimal?,
        @ForAll("refs") message: String?,
    ) {
        val info = TrueMoney.parse(TrueMoney.build("0$national", amount, message))!!
        assertEquals("0" + national.trimStart('0'), info.mobile.takeIf { national.isNotEmpty() } ?: "")
        assertEquals(rounded(amount), info.amount)
        assertEquals(message, info.message)
    }

    @Property(tries = 500)
    fun `a barcode converts to a bill payment that reads back the same`(
        @ForAll("digits15") biller: String,
        @ForAll("refs") ref1: String?,
        @ForAll("refs") ref2: String?,
        @ForAll("amounts") amount: BigDecimal?,
    ) {
        val barcode = BotBarcode.parse(BotBarcode.build(biller, ref1 ?: "R1", ref2, amount))!!
        val bill = PromptPay.parseBillPayment(barcode.toBillPayment())!!
        assertEquals(barcode.billerId, bill.billerId)
        assertEquals(barcode.ref1, bill.ref1)
        assertEquals(barcode.ref2, bill.ref2)
        assertEquals(barcode.amount, bill.amount)
    }

    @Property(tries = 300)
    fun `generated payloads are not accepted by the wrong parser`(
        @ForAll("mobiles") national: String,
    ) {
        val anyId = PromptPay.anyId(AnyIdType.MSISDN, "0$national")
        val bill = PromptPay.billPayment("0112233445566", "R1")
        val trueMoney = TrueMoney.build("0$national")
        assertNull(PromptPay.parseBillPayment(anyId))
        assertNull(PromptPay.parseAnyId(bill))
        assertNull(TrueMoney.parse(anyId))
        assertNull(TrueMoney.parse(bill))
        assertNull(SlipVerify.parse(anyId))
        assertNull(SlipVerify.parseTrueMoney(trueMoney))
        assertEquals(AnyIdType.EWALLETID, PromptPay.parseAnyId(trueMoney)!!.type) // a TrueMoney QR is an e-wallet AnyID
    }
}
