package io.github.sunjirapath.promptparse

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TrueMoneyMessageTest {
    @ParameterizedTest
    @ValueSource(strings = ["００４１", "٠٠٤١", "00４1", "D800", "DC00", "D8000041", "0041DC00", "D800D800", "DC00D800"])
    fun `malformed message is ignored without discarding payment fields`(hex: String) {
        val base = PromptParse.parse(TrueMoney.build("0801111111"))!!
        val payload = base.with("81", hex).encode()
        for (validateFields in listOf(false, true)) {
            val info = assertNotNull(TrueMoney.parse(payload, validateFields))
            assertEquals("0801111111", info.mobile)
            assertNull(info.message, hex)
        }
    }

    @ParameterizedTest
    @ValueSource(strings = ["\uD800", "\uDC00", "\uD800A", "A\uDC00", "\uD800\uD800", "\uDC00\uD800"])
    fun `builder rejects malformed UTF16 instead of emitting unreadable messages`(message: String) {
        assertFailsWith<IllegalArgumentException> { TrueMoney.build("0801111111", message = message) }
    }

    @Test
    fun `ASCII Thai emoji and UTF16 boundary messages survive lowercase hex`() {
        for (message in listOf("Hello World!", "สวัสดี 😀", "\uFEFF\uFFFD\uD800\uDC00", "😀".repeat(12))) {
            val payload = TrueMoney.build("0801111111", message = message)
            val qr = PromptParse.parse(payload)!!
            val lowercase = qr.with("81", qr.value("81")!!.lowercase()).encode()
            assertEquals(message, TrueMoney.parse(payload)!!.message)
            assertEquals(message, TrueMoney.parse(lowercase, validateFields = true)!!.message)
        }
        assertFailsWith<IllegalArgumentException> { TrueMoney.build("0801111111", message = "😀".repeat(13)) }
    }
}
