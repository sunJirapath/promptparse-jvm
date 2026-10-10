package io.github.sunJirapath.promptparse.example;

import io.github.sunJirapath.promptparse.AnyIdInfo;
import io.github.sunJirapath.promptparse.AnyIdType;
import io.github.sunJirapath.promptparse.EmvcoQr;
import io.github.sunJirapath.promptparse.PromptParse;
import io.github.sunJirapath.promptparse.PromptPay;
import java.math.BigDecimal;

/** Plain Java usage of promptparse-jvm. Fails with an AssertionError if any output is unexpected. */
public final class Example {
    private Example() {}

    public static void main(String[] args) {
        // Static QR for a mobile number (no amount).
        String staticQr = PromptPay.anyId(AnyIdType.MSISDN, "081-222-3333");
        check(staticQr, "00020101021129370016A0000006770101110113006681222333353037645802TH63041DCF");

        // Dynamic QR with an amount: BigDecimal in, 2 decimals out.
        String dynamicQr = PromptPay.anyId(AnyIdType.MSISDN, "0812223333", new BigDecimal("30"));
        check(dynamicQr, "00020101021229370016A0000006770101110113006681222333353037645802TH540530.0063043CAD");

        // Bill payment, calling the @JvmOverloads variant with ref1, ref2 and amount.
        String bill = PromptPay.billPayment("0112233445566", "CUSTOMER001", "INV001", new BigDecimal("250.5"));
        check(PromptPay.parseBillPayment(bill).getAmount().toPlainString(), "250.50");

        // Parse, read tags, change the amount, re-encode (the CRC is recomputed).
        EmvcoQr qr = PromptParse.parse(dynamicQr, true);
        check(qr.value("29", "01"), "0066812223333");
        check(qr.with("54", "99.00").encode().contains("540599.00"), true);

        AnyIdInfo info = PromptPay.parseAnyId(dynamicQr);
        check(info.getType(), AnyIdType.MSISDN);
        check(info.getTarget(), "0812223333");

        // Validation failures are IllegalArgumentExceptions.
        try {
            PromptPay.anyId(AnyIdType.NATID, "123");
            throw new AssertionError("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // ok
        }

        check(PromptParse.validateCrc(staticQr), true);
        check(PromptParse.parse(staticQr.substring(0, staticQr.length() - 1) + "0", true) == null, true);

        System.out.println("OK: " + staticQr);
    }

    private static void check(Object actual, Object expected) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }
}
