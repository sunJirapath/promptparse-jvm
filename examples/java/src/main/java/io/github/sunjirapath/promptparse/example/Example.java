package io.github.sunjirapath.promptparse.example;

import io.github.sunjirapath.promptparse.AnyIdInfo;
import io.github.sunjirapath.promptparse.AnyIdType;
import io.github.sunjirapath.promptparse.BillPaymentInfo;
import io.github.sunjirapath.promptparse.BotBarcode;
import io.github.sunjirapath.promptparse.BotBarcodeInfo;
import io.github.sunjirapath.promptparse.EmvcoQr;
import io.github.sunjirapath.promptparse.PromptParse;
import io.github.sunjirapath.promptparse.PromptPay;
import io.github.sunjirapath.promptparse.SlipVerify;
import io.github.sunjirapath.promptparse.SlipVerifyInfo;
import io.github.sunjirapath.promptparse.Tag;
import io.github.sunjirapath.promptparse.TrueMoney;
import io.github.sunjirapath.promptparse.TrueMoneyInfo;
import io.github.sunjirapath.promptparse.TrueMoneySlipVerifyInfo;
import java.math.BigDecimal;
import java.util.Arrays;

/**
 * Plain Java usage of promptparse-jvm. Fails with an AssertionError if any output is unexpected.
 */
public final class Example {
  private Example() {}

  public static void main(String[] args) {
    // Static QR for a mobile number (no amount).
    String staticQr = PromptPay.anyId(AnyIdType.MSISDN, "081-222-3333");
    check(staticQr, "00020101021129370016A0000006770101110113006681222333353037645802TH63041DCF");

    // Dynamic QR with an amount: BigDecimal in, 2 decimals out.
    String dynamicQr = PromptPay.anyId(AnyIdType.MSISDN, "0812223333", new BigDecimal("30"));
    check(
        dynamicQr,
        "00020101021229370016A0000006770101110113006681222333353037645802TH540530.0063043CAD");

    // Bill payment, calling the @JvmOverloads variant with ref1, ref2 and amount.
    String bill =
        PromptPay.billPayment("0112233445566", "CUSTOMER001", "INV001", new BigDecimal("250.5"));
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
    check(
        PromptParse.parse(staticQr.substring(0, staticQr.length() - 1) + "0", true) == null, true);

    // Slip verify: parse returns null for anything that is not a slip.
    String slip = SlipVerify.build("002", "0002123123121200011");
    check(slip, "004000060000010103002021900021231231212000115102TH91049C30");
    SlipVerifyInfo slipInfo = SlipVerify.parse(slip);
    check(slipInfo.getTransRef(), "0002123123121200011");
    check(SlipVerify.parse(staticQr) == null, true);

    // A bank app dropped the leading zero of the CRC (0539 -> 539): repaired unless auto-fix is
    // disabled.
    String full = SlipVerify.build("014", "T00000086");
    String truncated = full.substring(0, full.length() - 4) + "539";
    check(SlipVerify.parse(truncated).getTransRef(), "T00000086");
    check(SlipVerify.parse(truncated, false) == null, true);

    // TrueMoney QR and BOT barcode.
    check(
        TrueMoney.build("0801111111"),
        "00020101021129390016A000000677010111031514000080111111153037645802TH63047C0F");
    check(
        BotBarcode.parse("|099999999999990\r111222333444\r\r0").toBillPayment(),
        "00020101021130550016A0000006770101120115099999999999990021211122233344453037645802TH63043EE7");

    coverRemainingEntryPoints(staticQr);

    System.out.println("OK: " + staticQr);
  }

  /**
   * Calls every public entry point not already used in main, so a signature break shows up here.
   */
  private static void coverRemainingEntryPoints(String staticQr) {
    // PromptParse: validateCrc, lenient parse with sub-tags off, fixTruncatedCrc.
    check(PromptParse.validateCrc(staticQr), true);
    check(PromptParse.parse(staticQr, false, false).tag("29").getSubTags().isEmpty(), true);
    check(PromptParse.fixTruncatedCrc(staticQr) == null, true); // already complete

    // Tag and EmvcoQr, built by hand.
    Tag sub = new Tag("00", "ab");
    Tag viaOf = Tag.of("59", "Shop");
    Tag template = Tag.template("29", Arrays.asList(sub, new Tag("01", "cd")));
    EmvcoQr qr = new EmvcoQr(Arrays.asList(new Tag("00", "01"), viaOf, template));
    check(qr.getTags().size(), 3);
    check(qr.tag("29", "01").getValue(), "cd");
    check(qr.tag("59").getId(), "59");
    check(qr.value("59"), "Shop");
    check(qr.value("29", "00"), "ab");
    check(PromptParse.parse(qr.encode(), true).equals(qr), true);

    // PromptPay: every overload family and the readers.
    check(AnyIdType.values().length, 3);
    String eWallet = PromptPay.anyId(AnyIdType.EWALLETID, "140000812223333");
    check(PromptPay.parseAnyId(eWallet).getType(), AnyIdType.EWALLETID);
    String bill = PromptPay.billPayment("0112233445566", "REF1");
    String billFull =
        PromptPay.billPayment("0112233445566", "REF1", "REF2", new BigDecimal("1.005"), "SCB");
    BillPaymentInfo info = PromptPay.parseBillPayment(billFull);
    check(info.getRef2(), "REF2");
    check(info.getRef3(), "SCB");
    check(info.getAmount().toPlainString(), "1.01");
    check(PromptPay.parseBillPayment(bill).getAmount() == null, true);

    // SlipVerify, TrueMoney variant.
    String tmSlip = SlipVerify.buildTrueMoney("P2P", "TXN0001234567", "25012024");
    check(tmSlip, "00480002010102010203P2P0313TXN00012345670408250120249104b425");
    TrueMoneySlipVerifyInfo tmInfo = SlipVerify.parseTrueMoney(tmSlip);
    check(tmInfo.getTransactionId(), "TXN0001234567");
    check(tmInfo.getDate(), "25012024");
    check(SlipVerify.parseTrueMoney(tmSlip, false).getEventType(), "P2P");
    check(SlipVerify.parse("not a slip", true) == null, true);

    // TrueMoney QR with every argument, and its reader.
    String trueMoney = TrueMoney.build("0801111111", new BigDecimal("10.05"), "Hello World!");
    TrueMoneyInfo tmQr = TrueMoney.parse(trueMoney);
    check(tmQr.getMobile(), "0801111111");
    check(tmQr.getMessage(), "Hello World!");
    check(tmQr.getAmount().toPlainString(), "10.05");

    // BotBarcode: build with all arguments, parse, convert.
    String barcode =
        BotBarcode.build("099400016550100", "123456789012", "670429", new BigDecimal("3649.22"));
    check(barcode, "|099400016550100\r123456789012\r670429\r364922");
    check(BotBarcode.build("099400016550100", "123456789012").endsWith("\r\r0"), true);
    BotBarcodeInfo barcodeInfo = BotBarcode.parse(barcode);
    check(barcodeInfo.getBillerId(), "099400016550100");
    check(barcodeInfo.getRef1(), "123456789012");
    check(barcodeInfo.getRef2(), "670429");
    check(barcodeInfo.getAmount().toPlainString(), "3649.22");
    check(
        barcodeInfo.toBillPayment(),
        PromptPay.billPayment(
            "099400016550100", "123456789012", "670429", new BigDecimal("3649.22")));
  }

  private static void check(Object actual, Object expected) {
    if (!expected.equals(actual)) {
      throw new AssertionError("expected <" + expected + "> but was <" + actual + ">");
    }
  }
}
