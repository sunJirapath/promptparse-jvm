package io.github.sunjirapath.promptparse

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import net.jqwik.api.constraints.IntRange
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SlipPropertyTest {
    @Provide
    fun refs(): Arbitrary<String> =
        Arbitraries
            .strings()
            .withChars(*"AZaz09-ก😀".toCharArray())
            .ofMinLength(1)
            .ofMaxLength(8)

    @Provide
    fun fuzz(): Arbitrary<String> =
        Arbitraries.oneOf(
            Arbitraries.strings().all().ofMaxLength(120),
            Arbitraries.strings().withChars(*"0123456789ABCDEFab\r|ก".toCharArray()).ofMaxLength(80),
        )

    @Provide
    fun amounts(): Arbitrary<BigDecimal?> =
        Arbitraries
            .bigDecimals()
            .between(BigDecimal("0.005"), BigDecimal("9999999999.99"))
            .ofScale(3)
            .injectNull(0.2)

    @Property(tries = 1000)
    fun `slip verify round trips, also with leading zeros dropped from the CRC`(
        @ForAll("refs") ref: String,
        @ForAll("refs") txn: String,
        @ForAll @IntRange(min = 0, max = 999) bank: Int,
    ) {
        val code = "%03d".format(bank)
        val std = SlipVerify.build(code, ref)
        val tm = SlipVerify.buildTrueMoney(txn, ref, "01012025")
        assertEquals(SlipVerifyInfo(code, ref), SlipVerify.parse(std))
        assertEquals(TrueMoneySlipVerifyInfo(txn, ref, "01012025"), SlipVerify.parseTrueMoney(tm))

        val stdCrc = std.takeLast(4)
        for (dropped in 1..minOf(3, 4 - stdCrc.trimStart('0').length)) {
            val short = std.dropLast(4) + stdCrc.substring(dropped)
            assertEquals(SlipVerifyInfo(code, ref), SlipVerify.parse(short))
            assertNull(SlipVerify.parse(short, crcAutoFix = false))
        }
        val tmCrc = tm.takeLast(4)
        for (dropped in 1..minOf(3, 4 - tmCrc.trimStart('0').length)) {
            val short = tm.dropLast(4) + tmCrc.substring(dropped)
            assertEquals(TrueMoneySlipVerifyInfo(txn, ref, "01012025"), SlipVerify.parseTrueMoney(short))
            assertNull(SlipVerify.parseTrueMoney(short, crcAutoFix = false))
        }
    }

    @Property(tries = 3000)
    fun `new parsers never throw unexpectedly`(
        @ForAll("fuzz") s: String,
    ) {
        SlipVerify.parse(s)
        SlipVerify.parse(s, crcAutoFix = false)
        SlipVerify.parseTrueMoney(s)
        TrueMoney.parse(s)
        TrueMoney.parse(s, validateFields = true)
        PromptParse.fixTruncatedCrc(s)
        val barcode = BotBarcode.parse(s)
        if (barcode != null) {
            try {
                barcode.toBillPayment()
            } catch (expected: IllegalArgumentException) {
                // biller ID that is not 13/15 digits
            }
        }
    }

    @Property(tries = 500)
    fun `barcode round trips`(
        @ForAll("refs") ref1: String,
        @ForAll("amounts") amount: BigDecimal?,
    ) {
        val text = BotBarcode.build("099400016550100", ref1, null, amount)
        val info = BotBarcode.parse(text)!!
        assertEquals(amount?.setScale(2, RoundingMode.HALF_UP), info.amount)
        assertEquals(text, BotBarcode.build(info.billerId, info.ref1, info.ref2, info.amount))
        assertTrue(text.startsWith("|"))
    }
}
