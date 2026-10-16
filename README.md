# promptparse-jvm — PromptPay Java & Thai QR Kotlin

[![CI](https://github.com/sunJirapath/promptparse-jvm/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/sunJirapath/promptparse-jvm/actions/workflows/ci.yml)
[![CodeQL](https://github.com/sunJirapath/promptparse-jvm/actions/workflows/codeql.yml/badge.svg)](https://github.com/sunJirapath/promptparse-jvm/actions/workflows/codeql.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.sunjirapath.promptparse/promptparse)](https://central.sonatype.com/artifact/io.github.sunjirapath.promptparse/promptparse)
[![Coverage](https://img.shields.io/endpoint?url=https%3A%2F%2Fraw.githubusercontent.com%2FsunJirapath%2Fpromptparse-jvm%2Fbadges%2Fcoverage.json)](https://github.com/sunJirapath/promptparse-jvm/actions/workflows/ci.yml)
[![License](https://img.shields.io/github/license/sunJirapath/promptparse-jvm)](LICENSE)

**PromptPay Java** and **Thai QR Kotlin** library: parse, generate and validate Thai **PromptPay** / **EMVCo**
merchant-presented QR payloads on the JVM, including slip pre-checks (**ตรวจสลิป Java**) before calling a bank API.
Zero runtime dependencies (Kotlin stdlib only), Java-friendly, immutable and thread-safe.

**Features:** PromptPay AnyID (phone / national ID / e-wallet) · PromptPay Bill Payment · TrueMoney QR ·
Slip Verify / Mini QR (standard + TrueMoney) · BOT 1D barcode → QR · generic EMVCo TLV parser with CRC-16 validation.

> **Disclaimer:** this project is not affiliated with, endorsed by, or sponsored by the Bank of Thailand (BOT)
> or any bank. It implements publicly documented formats; always verify generated QR codes with your bank
> before using them in production.

[English](#english) · [ภาษาไทย](#ภาษาไทย)

## English

### Install

Latest version: **0.1.2**, available on Maven Central.

> **Coordinates changed in 0.1.2.** The groupId is now `io.github.sunjirapath.promptparse` (it matches the Java
> package). Versions 0.1.0 and 0.1.1 stay on Maven Central under the old `io.github.sunjirapath:promptparse`;
> change the dependency below when you upgrade. The API is the same.

```kotlin
// Gradle (Kotlin DSL)
dependencies {
    implementation("io.github.sunjirapath.promptparse:promptparse:0.1.2")
}
```

```xml
<!-- Maven -->
<dependency>
  <groupId>io.github.sunjirapath.promptparse</groupId>
  <artifactId>promptparse</artifactId>
  <version>0.1.2</version>
</dependency>
```

**Supported platforms:** Java 11 or newer (bytecode target 11, compile-checked with `-Xjdk-release=11`); CI runs the
tests on JDK 11, 17, 21 and 25 on Linux and on JDK 21 on Windows. The only runtime dependency is the Kotlin stdlib.
**Android (since 0.1.1): API 21 (5.0) and newer.** The main code is checked against the Android API 21 signatures (Animal Sniffer) and
the test vectors, Thai/emoji handling, locale independence and fuzz tests run on emulators for API 21, 24, 26, 30, 34
and 36 in CI (`android-tests`, [workflow](.github/workflows/android.yml)). No core-library desugaring is needed. Not
verified: R8/ProGuard minification of an app that uses the library (it uses no reflection, so none should be needed).

The library produces and reads payload *strings*. Turning a string into a QR image is up to you
(for example ZXing or `qrcode-kotlin`).

### Quick start by use case

**1. Generate a checkout QR**

```kotlin
import io.github.sunjirapath.promptparse.*
import java.math.BigDecimal

// Pay a phone number, with an amount (dynamic QR) or without (static QR, payer types the amount)
val payload = PromptPay.anyId(AnyIdType.MSISDN, "081-222-3333", BigDecimal("250.00"))
val staticQr = PromptPay.anyId(AnyIdType.NATID, "1234567890123")

// Pay a biller: ref1 is usually the order or invoice number
val bill = PromptPay.billPayment("0112233445566", "ORDER-1042", ref2 = "SHOP-A", amount = BigDecimal("250"))
// payload / bill are the strings to encode into the QR image
```

```java
String payload = PromptPay.anyId(AnyIdType.MSISDN, "081-222-3333", new BigDecimal("250.00"));
String bill = PromptPay.billPayment("0112233445566", "ORDER-1042", "SHOP-A", new BigDecimal("250"));
```

Generators throw `IllegalArgumentException` with a clear message for invalid input (bad number, amount <= 0, ...).

**2. Pre-validate a payment slip before calling the bank API**

```kotlin
val slip = SlipVerify.parse(scannedText)          // SlipVerifyInfo? - null if it is not a standard slip
    ?: return reject("not a slip QR")             // also null for a bad CRC, wrong marker, trailing junk
callBankOpenApi(slip.sendingBank, slip.transRef)  // your code
// TrueMoney slips have their own variant:
val tm = SlipVerify.parseTrueMoney(scannedText)
```

```java
SlipVerifyInfo slip = SlipVerify.parse(scannedText);   // null if not a valid standard slip
if (slip == null) { /* reject */ }
```

Some bank apps drop the leading zeros of the slip CRC; the parser repairs that by default.
Pass `crcAutoFix = false` (Java: `SlipVerify.parse(text, false)`) to refuse it.

**3. Parse a scanned QR**

```kotlin
PromptPay.parseAnyId(scanned)?.let { println("${it.type} ${it.target} ${it.amount}") }   // AnyIdInfo?
PromptPay.parseBillPayment(scanned)?.let { println("${it.billerId} ${it.ref1} ${it.amount}") }
TrueMoney.parse(scanned)                                  // TrueMoneyInfo? (mobile, amount, message)

// Any EMVCo payload: lenient by default, strict = true also requires a valid CRC
val qr = PromptParse.parse(scanned, strict = true)        // EmvcoQr? - null when invalid
qr?.value("29", "01")                                     // a value inside a template tag
qr?.with("54", "99.00")?.encode()                         // edit a tag; the CRC is recomputed

BotBarcode.parse(barcodeText)?.toBillPayment()            // BOT 1D barcode -> bill payment QR
```

Since 0.1.1, payment readers optionally validate field shapes:

```kotlin
PromptPay.parseAnyId(scanned, validateFields = true)
PromptPay.parseBillPayment(scanned, validateFields = true)
TrueMoney.parse(scanned, validateFields = true)
SlipVerify.parse(scanned, validateFields = true)           // crcAutoFix remains true
SlipVerify.parseTrueMoney(scanned, crcAutoFix = false, validateFields = true)
BotBarcode.parse(scanned, validateFields = true)
```

Java: `PromptPay.parseAnyId(scanned, true)` (and likewise for the other readers).
This requires initiation method `11`/`12`, currency `764`, country `TH`, the documented recipient/biller shape,
and, when present, an amount > 0 with at most 2 decimals, at most 10 integer digits and at most 13 characters.
TrueMoney also checks fixed zero padding. It does not check national-ID check digits, phone allocation,
bank registration or payment authenticity. Existing one-argument calls retain permissive field extraction.
Payment readers reject duplicate critical tags, duplicate known account/reference fields, mixed AnyID/Bill Payment
templates and multiple recipient types when validation is enabled. Unknown extension fields remain permissive.
Slip readers check 3-digit bank codes or 8-digit dates (shape only), and reject duplicate known fields/root tags.
Barcode validation requires exactly 15 ASCII biller digits. Java slip calls use three arguments:
`SlipVerify.parse(scanned, true, true)` and `SlipVerify.parseTrueMoney(scanned, false, true)`.

```java
AnyIdInfo info = PromptPay.parseAnyId(scanned);           // null if not a PromptPay AnyID QR
EmvcoQr qr = PromptParse.parse(scanned, true);            // null when invalid
String target = qr.value("29", "01");
```

A complete, self-checking Java example that calls every public entry point lives in
[`examples/java`](examples/java) and runs as part of `./gradlew check`.

### Behavior in brief

- Invalid *input to generators* throws `IllegalArgumentException`; invalid *payloads to parsers* return `null` / `false`. Parsers never throw.
- TLV lengths count UTF-8 bytes; the CRC is CRC-16/CCITT-FALSE over UTF-8 bytes, always written in uppercase.
- Amounts are `BigDecimal`, written with 2 decimals (`HALF_UP`); amounts <= 0 are rejected.
- Slip verify parsers repair a CRC whose leading zeros a bank app dropped (1-3 hex digits left) unless `crcAutoFix = false`; builders never shorten it. `PromptParse.fixTruncatedCrc` does the same for tag-63 payment QRs on request.
- Mobile numbers accept `0812345678`, `081-234-5678`, `+66812345678`, `66812345678`, `0066812345678`.
- Every deliberate difference from the TypeScript reference is listed in [`docs/SPEC_NOTES.md`](docs/SPEC_NOTES.md).

Fixes since 0.1.0: getter lists are unmodifiable in Java as well as Kotlin;
`Tag.template` preserves encoded values and derives children using the parser's one-level rule (empty children
leave the value opaque); TrueMoney parsing preserves all ten local-number digits; strict parsing requires a final
CRC tag. These changes are listed under `[Unreleased]` in [CHANGELOG.md](CHANGELOG.md).

### Untrusted input size

The generic parser has no payload-size limit; memory and work grow with the input. Set an input limit in scanner
and API integrations **before parsing**, and cap HTTP/body sizes before decoding them into a string.
For example, an application may choose a 4,096-character limit:

```kotlin
val info = scanned.takeIf { it.length <= 4096 }
    ?.let { PromptPay.parseAnyId(it, validateFields = true) }
    ?: return reject("invalid or oversized payment QR")
```

This is an example application policy, not a QR specification limit. Choose a limit for the formats your
integration accepts; long barcode references and generic TLV data may require a different policy.

### Differences from promptparse

Behavior was ported from [promptparse](https://github.com/maythiwat/promptparse); where this library deliberately differs:

| Area | promptparse (TypeScript) | promptparse-jvm |
|---|---|---|
| TrueMoney slip | rejects only if *both* marker sub-tags are wrong, and may return missing fields as `undefined` | needs **both** markers (`00` and `01` = `01`) **and** event type, transaction id and date, else `null` (the rule `thai-qr-payment` documents) |
| `BotBarcode` amount | `0`/missing means "none", `0` is also accepted when generating | `build` takes `null` for "no amount" and **rejects 0 or negative** amounts with `IllegalArgumentException`; `parse` turns the wire value `0` (or all zeros) into `null` |
| Amounts | JS `number`, `toFixed(2)`; `0` silently makes a static QR | `BigDecimal`, `HALF_UP`, always `> 0` after rounding |
| Mobile numbers | only `0xxxxxxxxx` / `66xxxxxxxxx` work | also `+66`, dashes/spaces and `0066...`; anything else throws |
| Lengths and CRC | UTF-16 units for lengths, UTF-8 bytes for the CRC | UTF-8 bytes for both |
| Invalid input | generators do not validate; parsers return `null` | generators throw `IllegalArgumentException`; parsers return `null`/`false` and never throw |
| Slip CRC | repaired if shortened; `lastIndexOf("9104")` can misplace the CRC | repaired (1-3 digits, `crcAutoFix = false` disables); the CRC is located from the end |
| Strict payment CRC envelope (since 0.1.1) | checks the last 4 characters only | requires exactly one CRC tag `63`, length `04`, as the last decoded tag |
| Field validation (since 0.1.1) | permissive extraction | permissive by default; `validateFields = true` checks payment/slip/barcode field shapes and rejects ambiguous critical fields |
| Whitespace | not handled | surrounding spaces, tabs and line breaks from scanners are ignored (see below) |

Whitespace: strict parsing, slip/TrueMoney readers, `validateCrc` and `fixTruncatedCrc` trim spaces, tabs, `\r` and `\n` on both sides;
lenient `PromptParse.parse` trims leading whitespace and trailing line breaks only (a last value may end in a space);
`BotBarcode.parse` ignores leading whitespace and one trailing `\n` or `\r\n`. Anything returned (for example `EmvcoQr.encode()`) is built from the trimmed text.

### Build and contribute

```
./gradlew check        # format, detekt, tests, API, 90% line/branch coverage, examples, Maven artifact consumer
./gradlew spotlessApply  # fix formatting
./gradlew apiDump      # refresh core/api/core.api after intentional API changes
```

See [CONTRIBUTING.md](CONTRIBUTING.md). Security reports: [SECURITY.md](SECURITY.md).

`artifact-tests` is a standalone Java build that resolves the generated publication's JAR and POM from a
temporary repository under `core/build`. It reuses the Java example, verifies that the library was loaded
from a JAR and that the POM supplies the Kotlin stdlib, and runs on the selected test JDK. It does not use
project dependencies, change your local Maven repository, sign artifacts, or publish a release.

### Credits and license

Ported from [maythiwat/promptparse](https://github.com/maythiwat/promptparse) (MIT, © Maythiwat Chomchuen) —
behavior, tag identifiers and golden test vectors come from it. Slip Verify, TrueMoney and BOT barcode behavior
and vectors are cross-checked against and partly derived from
[uunw/thai-qr-payment](https://github.com/uunw/thai-qr-payment) (MIT, © 2026 uunw). See [NOTICE](NOTICE).
This project is licensed under the [Apache License 2.0](LICENSE).

## ภาษาไทย

**PromptPay Java / Thai QR Kotlin** — ไลบรารี Kotlin/JVM สำหรับอ่าน สร้าง และตรวจสอบ QR Payload ของ **PromptPay / EMVCo** (แบบ merchant-presented)
สร้าง QR พร้อมเพย์ด้วย Java/Kotlin และ**ตรวจสลิป Java** (ตรวจ QR บนสลิป Mini QR ก่อนเรียก API ตรวจสอบสลิปของธนาคาร)
รองรับ AnyID (เบอร์โทร/เลขบัตรประชาชน/e-wallet), Bill Payment, TrueMoney, Slip Verify และ BOT Barcode
ไม่มี dependency ตอนรันนอกจาก Kotlin stdlib เรียกใช้จาก Java ได้สะดวก โครงสร้างข้อมูลเป็น immutable และ thread-safe

> **ข้อสงวนสิทธิ์:** โปรเจกต์นี้ไม่มีส่วนเกี่ยวข้องกับ ธนาคารแห่งประเทศไทย (ธปท.) หรือธนาคารใด ๆ
> เป็นการ implement ตามรูปแบบที่เปิดเผยต่อสาธารณะเท่านั้น ควรทดสอบ QR ที่สร้างกับธนาคารก่อนใช้งานจริง

### ติดตั้ง

เวอร์ชันล่าสุด **0.1.2** บน Maven Central: `io.github.sunjirapath.promptparse:promptparse:0.1.2`
(ตั้งแต่ 0.1.2 groupId เปลี่ยนเป็น `io.github.sunjirapath.promptparse`; 0.1.0 และ 0.1.1 ยังอยู่ที่ `io.github.sunjirapath:promptparse`)

**รองรับ:** Java 11 ขึ้นไป (ทดสอบบน JDK 11, 17, 21, 25 และ Windows) และ Android API 21 ขึ้นไป (เริ่มใน `0.1.1`; ทดสอบบน emulator API 21, 24, 26, 30, 34, 36)

### เริ่มต้นใช้งาน (Kotlin) — Thai QR Kotlin

```kotlin
val qr = PromptPay.anyId(AnyIdType.MSISDN, "081-222-3333", BigDecimal("30"))
val parsed = PromptParse.parse(qr, strict = true)   // null ถ้า payload ไม่ถูกต้อง
PromptPay.parseAnyId(qr)                            // อ่านเบอร์/เลขบัตร/e-wallet และยอดเงินกลับมา
SlipVerify.parse(scanned)                           // ตรวจ QR บนสลิปก่อนเรียก API ธนาคาร (null ถ้าไม่ใช่สลิป)
```

### เริ่มต้นใช้งาน (Java) — PromptPay Java / ตรวจสลิป Java

```java
String qr = PromptPay.anyId(AnyIdType.MSISDN, "0812223333", new BigDecimal("30"));
EmvcoQr parsed = PromptParse.parse(qr, true);
SlipVerifyInfo slip = SlipVerify.parse(scanned);
```

- `SlipVerify` (สลิปมาตรฐาน + TrueMoney) แก้ CRC ที่แอปธนาคารตัดเลข 0 นำหน้าให้อัตโนมัติ ปิดได้ด้วย `crcAutoFix = false`
- ข้อมูลที่ส่งเข้า *ตัวสร้าง* ไม่ถูกต้อง จะ throw `IllegalArgumentException`; ส่วน *ตัวอ่าน* จะคืนค่า `null` / `false` และไม่ throw
- ตัวอ่านจะตัดช่องว่างและขึ้นบรรทัดใหม่ที่เครื่องสแกนต่อท้ายให้
- ความยาว TLV นับเป็นไบต์ UTF-8 และ CRC เป็น CRC-16/CCITT-FALSE ตัวพิมพ์ใหญ่เสมอ
- ยอดเงินใช้ `BigDecimal` ทศนิยม 2 ตำแหน่ง ปัดแบบ `HALF_UP` และไม่รับค่า <= 0
- ตั้งแต่ `0.1.1` ตัวอ่าน AnyID/Bill Payment/TrueMoney/สลิป/บาร์โค้ด รับ `validateFields = true` เพื่อตรวจรูปแบบผู้รับ สกุลเงิน `764` ประเทศ `TH` และยอดเงิน
- รายละเอียดความแตกต่างจากไลบรารีต้นฉบับ (TypeScript) อยู่ใน [`docs/SPEC_NOTES.md`](docs/SPEC_NOTES.md)

### เครดิตและสัญญาอนุญาต

พอร์ตจาก [maythiwat/promptparse](https://github.com/maythiwat/promptparse) (MIT) และอ้างอิง/เทียบพฤติกรรมกับ
[uunw/thai-qr-payment](https://github.com/uunw/thai-qr-payment) (MIT) ดู [NOTICE](NOTICE) —
โปรเจกต์นี้อยู่ภายใต้ [Apache License 2.0](LICENSE)
