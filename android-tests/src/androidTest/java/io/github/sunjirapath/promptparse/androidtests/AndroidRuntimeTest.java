package io.github.sunjirapath.promptparse.androidtests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.os.Build;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import io.github.sunjirapath.promptparse.AnyIdInfo;
import io.github.sunjirapath.promptparse.AnyIdType;
import io.github.sunjirapath.promptparse.BillPaymentInfo;
import io.github.sunjirapath.promptparse.BotBarcode;
import io.github.sunjirapath.promptparse.EmvcoQr;
import io.github.sunjirapath.promptparse.PromptParse;
import io.github.sunjirapath.promptparse.PromptPay;
import io.github.sunjirapath.promptparse.SlipVerify;
import io.github.sunjirapath.promptparse.Tag;
import io.github.sunjirapath.promptparse.TrueMoney;
import io.github.sunjirapath.promptparse.TrueMoneyInfo;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * The library's golden vectors and edge cases, run on a real Android runtime. The vectors are the
 * ones asserted by the JVM test suite (ported from promptparse and thai-qr-payment), so a
 * difference here is an Android problem.
 */
@RunWith(AndroidJUnit4.class)
public class AndroidRuntimeTest {
  private static final String STATIC_QR =
      "00020101021129370016A0000006770101110113006681222333353037645802TH63041DCF";
  private static final String DYNAMIC_QR =
      "00020101021229370016A0000006770101110113006681222333353037645802TH540530.0063043CAD";
  private static final String BILL_REF3 =
      "00020101021130620016A000000677010112011301122334455660211CUSTOMER0010306INV00153037645802TH62070703SCB6304780E";
  private static final String SLIP = "004000060000010103002021900021231231212000115102TH91049C30";
  private static final String TRUEMONEY_SLIP =
      "00480002010102010203P2P0313TXN00012345670408250120249104b425";
  private static final String TRUEMONEY_QR =
      "00020101021129390016A000000677010111031514000080111111153037645802TH63047C0F";
  private static final String TRUEMONEY_QR_FULL =
      "00020101021229390016A000000677010111031514000080111111153037645802TH540510.05"
          + "814800480065006C006C006F00200057006F0072006C006400216304F5A2";
  private static final String THAI_MERCHANT =
      "00020101021129370016A0000006770101110113006681222333353037645802TH5927"
          + "ร้านทดสอบ"
          + "6021กรุงเทพ630488F4";
  private static final Locale ORIGINAL_LOCALE = Locale.getDefault();

  @After
  public void restoreLocale() {
    Locale.setDefault(ORIGINAL_LOCALE);
  }

  @Test
  public void reportsTheRuntimeItRunsOn() {
    Log.i(
        "promptparse",
        "running on API " + Build.VERSION.SDK_INT + " " + System.getProperty("java.vm.name"));
    assertTrue(Build.VERSION.SDK_INT >= 21);
  }

  @Test
  public void generatesPromptPayVectors() {
    assertEquals(STATIC_QR, PromptPay.anyId(AnyIdType.MSISDN, "0812223333"));
    assertEquals(DYNAMIC_QR, PromptPay.anyId(AnyIdType.MSISDN, "0812223333", new BigDecimal("30")));
    assertEquals(
        BILL_REF3, PromptPay.billPayment("0112233445566", "CUSTOMER001", "INV001", null, "SCB"));
  }

  @Test
  public void mobileSpellingsAreNormalized() {
    for (String spelling :
        Arrays.asList(
            "0812223333", "081-222-3333", "+66812223333", "66812223333", "0066812223333")) {
      assertEquals(spelling, STATIC_QR, PromptPay.anyId(AnyIdType.MSISDN, spelling));
    }
  }

  @Test
  public void readsPromptPayBack() {
    AnyIdInfo info = PromptPay.parseAnyId(DYNAMIC_QR);
    assertNotNull(info);
    assertEquals(AnyIdType.MSISDN, info.getType());
    assertEquals("0812223333", info.getTarget());
    assertEquals(new BigDecimal("30.00"), info.getAmount());
    BillPaymentInfo bill = PromptPay.parseBillPayment(BILL_REF3);
    assertNotNull(bill);
    assertEquals("CUSTOMER001", bill.getRef1());
    assertEquals("INV001", bill.getRef2());
    assertEquals("SCB", bill.getRef3());
    assertNull(PromptPay.parseAnyId(BILL_REF3));
  }

  @Test
  public void amountsUseBigDecimalHalfUp() {
    assertTrue(
        PromptPay.anyId(AnyIdType.NATID, "1234567890123", new BigDecimal("1.005"))
            .contains("54041.01"));
    assertTrue(
        PromptPay.anyId(AnyIdType.NATID, "1234567890123", new BigDecimal("9999999999.99"))
            .contains("541399999999999.99"));
    for (String bad : Arrays.asList("0", "-1", "0.004", "1E+999999999", "1E-999999999")) {
      try {
        PromptPay.anyId(AnyIdType.MSISDN, "0812223333", new BigDecimal(bad));
        fail("expected IllegalArgumentException for " + bad);
      } catch (IllegalArgumentException expected) {
        // ok
      }
    }
  }

  @Test
  public void slipVerifyStandardAndTrueMoney() {
    assertEquals(SLIP, SlipVerify.build("002", "0002123123121200011"));
    assertEquals("0002123123121200011", SlipVerify.parse(SLIP).getTransRef());
    assertEquals(TRUEMONEY_SLIP, SlipVerify.buildTrueMoney("P2P", "TXN0001234567", "25012024"));
    assertEquals("P2P", SlipVerify.parseTrueMoney(TRUEMONEY_SLIP).getEventType());
    assertNull(SlipVerify.parse(TRUEMONEY_SLIP));
    assertNull(SlipVerify.parseTrueMoney(SLIP));
    assertNull(SlipVerify.parse(STATIC_QR));
  }

  @Test
  public void shortenedSlipCrcIsRepairedUnlessDisabled() {
    String full = SlipVerify.build("014", "T00000086"); // CRC 0539
    assertTrue(full.endsWith("0539"));
    String truncated = full.substring(0, full.length() - 4) + "539";
    assertEquals("T00000086", SlipVerify.parse(truncated).getTransRef());
    assertNull(SlipVerify.parse(truncated, false));
    assertNull(SlipVerify.parse(full.substring(0, full.length() - 1)));
  }

  @Test
  public void trueMoneyQrAndBarcode() {
    assertEquals(TRUEMONEY_QR, TrueMoney.build("0801111111"));
    assertEquals(
        TRUEMONEY_QR_FULL, TrueMoney.build("0801111111", new BigDecimal("10.05"), "Hello World!"));
    TrueMoneyInfo info = TrueMoney.parse(TRUEMONEY_QR_FULL);
    assertEquals("0801111111", info.getMobile());
    assertEquals("Hello World!", info.getMessage());

    String barcode =
        BotBarcode.build("099400016550100", "123456789012", "670429", new BigDecimal("3649.22"));
    assertEquals("|099400016550100\r123456789012\r670429\r364922", barcode);
    assertEquals(
        "00020101021230650016A00000067701011201150994000165501000212123456789012030667042953037645802TH"
            + "54073649.2263044534",
        BotBarcode.parse(barcode).toBillPayment());
  }

  @Test
  public void thaiTextUsesUtf8ByteLengthsAndCrc() {
    EmvcoQr qr = PromptParse.parse(THAI_MERCHANT, true);
    assertNotNull("strict parse of the Thai vector", qr);
    assertEquals("ร้านทดสอบ", qr.value("59"));
    assertEquals("กรุงเทพ", qr.value("60"));
    assertEquals(THAI_MERCHANT, qr.encode());
  }

  @Test
  public void emojiAndLoneSurrogatesRoundTrip() {
    String emoji = "a😀b";
    EmvcoQr qr = new EmvcoQr(Arrays.asList(Tag.of("59", emoji), Tag.of("60", "x\uD800y")));
    String payload = qr.encode();
    assertTrue(payload.contains("5906" + emoji));
    assertEquals(qr, PromptParse.parse(payload, true));
  }

  @Test
  public void editingATagRecomputesTheCrc() {
    EmvcoQr qr = PromptParse.parse(DYNAMIC_QR, true);
    String changed = qr.with("54", "99.00").encode();
    assertTrue(changed.contains("540599.00"));
    assertTrue(PromptParse.validateCrc(changed));
    assertFalse(PromptParse.validateCrc(changed.substring(0, changed.length() - 1) + "0"));
  }

  @Test
  public void scannerWhitespaceIsIgnored() {
    assertNotNull(PromptPay.parseAnyId("  " + DYNAMIC_QR + "\r\n"));
    assertNotNull(SlipVerify.parse(SLIP + "\n"));
    assertNotNull(BotBarcode.parse("|099400016550100\r123456789012\r670429\r364922\r\n"));
  }

  @Test
  public void resultsDoNotDependOnTheDeviceLocale() {
    // Some devices format digits with Arabic-Indic or Thai numerals; the payload must stay ASCII.
    for (Locale locale :
        Arrays.asList(
            Locale.forLanguageTag("ar-EG-u-nu-arab"),
            Locale.forLanguageTag("th-TH-u-nu-thai"),
            Locale.forLanguageTag("fa-IR"),
            new Locale("tr", "TR"),
            Locale.ROOT)) {
      Locale.setDefault(locale);
      assertEquals(
          locale.toString(),
          DYNAMIC_QR,
          PromptPay.anyId(AnyIdType.MSISDN, "0812223333", new BigDecimal("30")));
      assertEquals(locale.toString(), SLIP, SlipVerify.build("002", "0002123123121200011"));
      assertEquals(
          locale.toString(),
          TRUEMONEY_SLIP,
          SlipVerify.buildTrueMoney("P2P", "TXN0001234567", "25012024"));
      assertNotNull(locale.toString(), SlipVerify.parseTrueMoney(TRUEMONEY_SLIP));
      assertNotNull(locale.toString(), PromptParse.parse(THAI_MERCHANT, true));
    }
  }

  @Test
  public void randomInputNeverThrows() {
    Random random = new Random(42);
    String alphabet = "0123456789ABCDEFabcdef|\r\n ก😀\uD800";
    for (int i = 0; i < 20000; i++) {
      StringBuilder sb = new StringBuilder();
      int length = random.nextInt(80);
      for (int j = 0; j < length; j++) {
        sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
      }
      String s = sb.toString();
      PromptParse.parse(s);
      PromptParse.parse(s, true, false);
      PromptParse.validateCrc(s);
      PromptParse.fixTruncatedCrc(s);
      PromptPay.parseAnyId(s);
      PromptPay.parseBillPayment(s);
      SlipVerify.parse(s);
      SlipVerify.parseTrueMoney(s, false);
      TrueMoney.parse(s);
      BotBarcode.parse(s);
    }
  }

  @Test
  public void hugeInputFinishesQuickly() {
    char[] zeros = new char[2_000_000];
    Arrays.fill(zeros, '0');
    String big = new String(zeros);
    long start = System.nanoTime();
    assertNull(PromptParse.parse(big, true));
    assertNull(SlipVerify.parse(big));
    PromptParse.parse(big);
    long millis = (System.nanoTime() - start) / 1_000_000;
    assertTrue("took " + millis + " ms", millis < 20_000);
  }
}
