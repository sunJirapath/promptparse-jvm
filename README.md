# promptparse-jvm

Parse, generate and validate Thai **PromptPay** / **EMVCo** merchant-presented QR payloads on the JVM.
Zero runtime dependencies (Kotlin stdlib only), Java-friendly, immutable and thread-safe.

> **Disclaimer:** this project is not affiliated with, endorsed by, or sponsored by the Bank of Thailand (BOT)
> or any bank. It implements publicly documented formats; always verify generated QR codes with your bank
> before using them in production.

[English](#english) · [ภาษาไทย](#ภาษาไทย)

## English

### Install

Coordinates are placeholders until the first release: `io.github.sunJirapath:promptparse:0.1.0-SNAPSHOT`.
Requires Java 11+.

### Kotlin quick start

```kotlin
import io.github.sunJirapath.promptparse.*
import java.math.BigDecimal

// Generate
val qr = PromptPay.anyId(AnyIdType.MSISDN, "081-222-3333", BigDecimal("30"))
val bill = PromptPay.billPayment("0112233445566", ref1 = "CUSTOMER001", ref2 = "INV001")

// Parse (lenient by default; strict = true also requires a valid CRC and well-formed TLV)
val parsed = PromptParse.parse(qr, strict = true)      // EmvcoQr? - null when invalid
parsed?.value("29", "01")                              // "0066812223333"
parsed?.with("54", "99.00")?.encode()                  // CRC is always recomputed

// Slip verify mini QR (standard + TrueMoney), TrueMoney QR, BOT barcode
SlipVerify.build("002", "0002123123121200011")         // 0040000600000101030020...91049C30
SlipVerify.parse(scanned)                              // SlipVerifyInfo? - null if it is not a slip
SlipVerify.parse(scanned, crcAutoFix = false)          // refuse CRCs shortened by bank apps
TrueMoney.build("0801111111", BigDecimal("10.05"), "Hello")
BotBarcode.parse("|0999999999999901112223334440")?.toBillPayment()

// Read PromptPay payloads back
PromptPay.parseAnyId(qr)                               // AnyIdInfo(type=MSISDN, target=0812223333, amount=30.00)
PromptParse.validateCrc(qr)                            // true
```

### Java quick start

```java
String qr = PromptPay.anyId(AnyIdType.MSISDN, "0812223333", new BigDecimal("30"));
EmvcoQr parsed = PromptParse.parse(qr, true);          // null if invalid
String target = parsed.value("29", "01");
```

A complete, self-checking example lives in [`examples/java`](examples/java) and runs as part of `./gradlew build`.

### Behavior in brief

- Invalid *input to generators* throws `IllegalArgumentException`; invalid *payloads to parsers* return `null` / `false`. Parsers never throw.
- TLV lengths count UTF-8 bytes; the CRC is CRC-16/CCITT-FALSE over UTF-8 bytes, always written in uppercase.
- Amounts are `BigDecimal`, written with 2 decimals (`HALF_UP`); amounts <= 0 are rejected.
- Slip verify parsers repair a CRC whose leading zeros a bank app dropped (1-3 hex digits left) unless `crcAutoFix = false`; builders never shorten it. `PromptParse.fixTruncatedCrc` does the same for tag-63 payment QRs on request.
- Mobile numbers accept `0812345678`, `081-234-5678`, `+66812345678`, `66812345678`, `0066812345678`.
- Every deliberate difference from the TypeScript reference is listed in [`docs/SPEC_NOTES.md`](docs/SPEC_NOTES.md).

### Build

```
./gradlew build        # compile, test, API check, 90% coverage gate, Java example
./gradlew apiDump      # refresh core/api/core.api after intentional API changes
```

### Credits and license

Ported from [maythiwat/promptparse](https://github.com/maythiwat/promptparse) (MIT, © Maythiwat Chomchuen) —
behavior, tag identifiers and golden test vectors come from it. Slip Verify, TrueMoney and BOT barcode behavior
and vectors are cross-checked against and partly derived from
[uunw/thai-qr-payment](https://github.com/uunw/thai-qr-payment) (MIT, © 2026 uunw). See [NOTICE](NOTICE).
This project is licensed under the [Apache License 2.0](LICENSE).

## ภาษาไทย

ไลบรารี Kotlin/JVM สำหรับอ่าน สร้าง และตรวจสอบ QR Payload ของ **PromptPay / EMVCo** (แบบ merchant-presented)
ไม่มี dependency ตอนรันนอกจาก Kotlin stdlib เรียกใช้จาก Java ได้สะดวก โครงสร้างข้อมูลเป็น immutable และ thread-safe

> **ข้อสงวนสิทธิ์:** โปรเจกต์นี้ไม่มีส่วนเกี่ยวข้องกับ ธนาคารแห่งประเทศไทย (ธปท.) หรือธนาคารใด ๆ
> เป็นการ implement ตามรูปแบบที่เปิดเผยต่อสาธารณะเท่านั้น ควรทดสอบ QR ที่สร้างกับธนาคารก่อนใช้งานจริง

### เริ่มต้นใช้งาน (Kotlin)

```kotlin
val qr = PromptPay.anyId(AnyIdType.MSISDN, "081-222-3333", BigDecimal("30"))
val parsed = PromptParse.parse(qr, strict = true)   // null ถ้า payload ไม่ถูกต้อง
PromptPay.parseAnyId(qr)                            // อ่านเบอร์/เลขบัตร/e-wallet และยอดเงินกลับมา
```

### เริ่มต้นใช้งาน (Java)

```java
String qr = PromptPay.anyId(AnyIdType.MSISDN, "0812223333", new BigDecimal("30"));
EmvcoQr parsed = PromptParse.parse(qr, true);
```

- `SlipVerify` (สลิปมาตรฐาน + TrueMoney) แก้ CRC ที่แอปธนาคารตัดเลข 0 นำหน้าให้อัตโนมัติ ปิดได้ด้วย `crcAutoFix = false`
- ข้อมูลที่ส่งเข้า *ตัวสร้าง* ไม่ถูกต้อง จะ throw `IllegalArgumentException`; ส่วน *ตัวอ่าน* จะคืนค่า `null` / `false` และไม่ throw
- ความยาว TLV นับเป็นไบต์ UTF-8 และ CRC เป็น CRC-16/CCITT-FALSE ตัวพิมพ์ใหญ่เสมอ
- ยอดเงินใช้ `BigDecimal` ทศนิยม 2 ตำแหน่ง ปัดแบบ `HALF_UP` และไม่รับค่า <= 0
- รายละเอียดความแตกต่างจากไลบรารีต้นฉบับ (TypeScript) อยู่ใน [`docs/SPEC_NOTES.md`](docs/SPEC_NOTES.md)

### เครดิต

พอร์ตจาก [maythiwat/promptparse](https://github.com/maythiwat/promptparse) (MIT) และอ้างอิง/เทียบพฤติกรรมกับ
[uunw/thai-qr-payment](https://github.com/uunw/thai-qr-payment) (MIT) ดู [NOTICE](NOTICE) —
โปรเจกต์นี้อยู่ภายใต้ [Apache License 2.0](LICENSE)
