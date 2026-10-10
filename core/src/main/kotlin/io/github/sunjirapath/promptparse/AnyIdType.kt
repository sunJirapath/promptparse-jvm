package io.github.sunjirapath.promptparse

/** Kind of recipient identifier in a PromptPay AnyID (tag 29) QR. */
public enum class AnyIdType(internal val subTagId: String) {
    /** Thai mobile number. */
    MSISDN("01"),

    /** National ID or tax ID (13 digits). */
    NATID("02"),

    /** E-wallet ID (15 digits). */
    EWALLETID("03"),
}
