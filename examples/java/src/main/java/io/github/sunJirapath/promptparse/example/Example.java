package io.github.sunJirapath.promptparse.example;

import io.github.sunJirapath.promptparse.AnyIdInfo;
import io.github.sunJirapath.promptparse.AnyIdType;
import io.github.sunJirapath.promptparse.EmvcoQr;
import io.github.sunJirapath.promptparse.PromptParse;
import io.github.sunJirapath.promptparse.BotBarcode;
import io.github.sunJirapath.promptparse.PromptPay;
import io.github.sunJirapath.promptparse.SlipVerify;
import io.github.sunJirapath.promptparse.SlipVerifyInfo;
import io.github.sunJirapath.promptparse.TrueMoney;
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

        // Slip verify: parse returns null for anything that is not a slip.
        String slip = SlipVerify.build("002", "0002123123121200011");
        check(slip, "004000060000010103002021900021231231212000115102TH91049C30");
        SlipVerifyInfo slipInfo = SlipVerify.parse(slip);
        check(slipInfo.getTransRef(), "0002123123121200011");
        check(SlipVerify.parse(staticQr) == null, true);

        // A bank app dropped the leading zero of the CRC (0539 -> 539): repaired unless auto-fix is disabled.
        String full = SlipVerify.build("014", "T00000086");
        String truncated = full.substring(0, full.length() - 4) + "539";
        check(SlipVerify.parse(truncated).getTransRef(), "T00000086");
        check(SlipVerify.parse(truncated, false) == null, true);

        // TrueMoney QR and BOT barcode.
        check(TrueMoney.build("0801111111"),
                "00020101021129390016A000000677010111031514000080111111153037645802TH63047C0F");
        check(BotBarcode.parse("|099999999999990\r111222333444\r\r0").toBillPayment(),
                "00020101021130550016A0000006770101120115099999999999990021211122233344453037645802TH63043EE7");

        System.out.println("OK: " + staticQr);
    }

    private static void check(Object actual, Object expected) {
        if (!expected.equals(actual)) {
            throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
        }
    }
}
