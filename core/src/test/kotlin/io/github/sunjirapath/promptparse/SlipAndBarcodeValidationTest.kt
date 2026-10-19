package io.github.sunjirapath.promptparse

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SlipAndBarcodeValidationTest {
    private fun slip(
        subTags: List<Tag>,
        extra: List<Tag> = emptyList(),
    ): String {
        val seed = Tlv.encode(listOf(Tag.template("00", subTags)) + extra) + "9104"
        return seed + Tlv.checksum(seed)
    }

    private fun standard(bank: String) = listOf(Tag("00", "000001"), Tag("01", bank), Tag("02", "REF"))

    private fun trueMoney(date: String) =
        listOf(Tag("00", "01"), Tag("01", "01"), Tag("02", "P2P"), Tag("03", "TXN"), Tag("04", date))

    @ParameterizedTest
    @ValueSource(strings = ["12", "1234", "ABC", "１２３"])
    fun `slip bank code validation is opt-in`(bank: String) {
        val payload = slip(standard(bank))
        assertNotNull(SlipVerify.parse(payload))
        assertNull(SlipVerify.parse(payload, validateFields = true))
    }

    @ParameterizedTest
    @ValueSource(strings = ["date", "0101202", "010120250", "0101202X", "０１０１２０２５"])
    fun `TrueMoney slip date validation is opt-in`(date: String) {
        val payload = slip(trueMoney(date))
        assertNotNull(SlipVerify.parseTrueMoney(payload))
        assertNull(SlipVerify.parseTrueMoney(payload, validateFields = true))
    }

    @Test
    fun `validated slips preserve CRC repair and date-shape-only behavior`() {
        val wire = SlipVerify.build("014", "T00000086")
        assertEquals(SlipVerify.parse(wire), SlipVerify.parse(wire, true, true))
        val short = wire.dropLast(4) + "539"
        assertNotNull(SlipVerify.parse(short, validateFields = true))
        assertNull(SlipVerify.parse(short, crcAutoFix = false, validateFields = true))
        val tm = SlipVerify.buildTrueMoney("P2P", "TXN", "32139999")
        assertEquals(SlipVerify.parseTrueMoney(tm), SlipVerify.parseTrueMoney(tm, true, true))
        val zeroCrc = ZERO_CRC_SLIP
        assertNotNull(SlipVerify.parse(zeroCrc, false, true))
    }

    @Test
    fun `duplicate slip root and known fields are rejected only when requested`() {
        val fields = standard("002")
        for (field in fields) {
            val payload = slip(fields + field)
            assertNotNull(SlipVerify.parse(payload))
            assertNull(SlipVerify.parse(payload, validateFields = true))
        }
        val duplicateRoot = slip(fields, listOf(Tag.template("00", fields)))
        assertNotNull(SlipVerify.parse(duplicateRoot))
        assertNull(SlipVerify.parse(duplicateRoot, validateFields = true))
        val duplicateCountry = slip(fields, listOf(Tag("51", "TH"), Tag("51", "TH")))
        assertNull(SlipVerify.parse(duplicateCountry, validateFields = true))
        val tmFields = trueMoney("01012025")
        val duplicateDate = slip(tmFields + Tag("04", "02012025"))
        assertNotNull(SlipVerify.parseTrueMoney(duplicateDate))
        assertNull(SlipVerify.parseTrueMoney(duplicateDate, validateFields = true))
    }

    @ParameterizedTest
    @ValueSource(strings = ["ABCD", "xxxx", ""])
    fun `standard slip rejects an extra CRC tag in every parsing mode`(extraCrc: String) {
        val payload = slip(standard("002"), listOf(Tag("91", extraCrc)))
        for (crcAutoFix in listOf(false, true)) {
            for (validateFields in listOf(false, true)) {
                assertNull(SlipVerify.parse(payload, crcAutoFix, validateFields))
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["ABCD", "xxxx", ""])
    fun `TrueMoney slip rejects an extra CRC tag in every parsing mode`(extraCrc: String) {
        val payload = slip(trueMoney("01012025"), listOf(Tag("91", extraCrc)))
        for (crcAutoFix in listOf(false, true)) {
            for (validateFields in listOf(false, true)) {
                assertNull(SlipVerify.parseTrueMoney(payload, crcAutoFix, validateFields))
            }
        }
    }

    @Test
    fun `CRC tag text and nested extension fields are not duplicate root checksums`() {
        val standard = slip(standard("002") + Tag("91", "9104ABCD"), listOf(Tag("99", "9104ABCD")))
        assertNotNull(SlipVerify.parse(standard, validateFields = true))
        val trueMoney = slip(trueMoney("01012025") + Tag("91", "9104ABCD"), listOf(Tag("99", "9104ABCD")))
        assertNotNull(SlipVerify.parseTrueMoney(trueMoney, validateFields = true))
    }

    @ParameterizedTest
    @ValueSource(strings = ["ABCDEFGHIJKLMNO", "0994000165501000", "０９９４０００１６５５０１００"])
    fun `barcode biller validation is opt-in`(biller: String) {
        val payload = "|$biller\rR1\r\r0"
        assertNotNull(BotBarcode.parse(payload))
        assertNull(BotBarcode.parse(payload, validateFields = true))
    }

    @Test
    fun `generated barcodes pass field validation and scanner whitespace still works`() {
        val payload = BotBarcode.build("099400016550100", "R1")
        assertEquals(BotBarcode.parse(payload), BotBarcode.parse(payload, true))
        assertEquals(BotBarcode.parse(payload), BotBarcode.parse("  $payload\r\n", true))
    }
}
