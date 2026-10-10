# SPEC_NOTES — promptparse-jvm

Source of truth: <https://github.com/maythiwat/promptparse> (MIT), commit `7487a97`
(package.json version 1.7.0). All `file:line` references below are relative to `src/` in that commit.
Probe results marked **[verified]** were obtained by evaluating the reference's exact expressions in Node.

Status legend: ✅ port as-is · ⚠️ port with a deliberate change (see §3) · ❓ ambiguous, needs a decision (see §4)

## 1. Tags, sub-tags, AIDs, formats (Phase 1)

| Item | Value | Reference | |
|---|---|---|---|
| TLV layout | 2-char ID + 2-digit length + value | `lib/tlv.ts:30-32` (decode), `:51-59` (encode) | ❓ length unit (Q2) |
| Length padding | `('00'+len).slice(-2)` | `lib/tlv.ts:52` | ⚠️ D9 |
| Sub-tags | any tag whose value matches `^\d{4}.+` is re-decoded; kept only if *every* sub-tag has `length>0` and `length === value.length` | `lib/parser.ts:30-43` | ✅ |
| CRC tag ID / length | `63`, `04` | `generate/promptpay/AnyID.ts:55`, `lib/tlv.ts:92-93` | ✅ |
| CRC algorithm | poly `0x1021`, init `0xFFFF`, MSB-first, no xorout, table-driven | `utils/checksum.ts:4-34,96-105`; init at `lib/tlv.ts:73` | ✅ (function is *named* "xmodem" but init=0xFFFF makes it CCITT-FALSE, D7) |
| CRC input | the payload **including** `"6304"`, as **UTF-8 bytes** (unpaired surrogate → U+FFFD) | `lib/tlv.ts:92-94`, `utils/checksum.ts:46-85` | ✅ |
| CRC output | 4 hex digits, left-padded with `0`, uppercase by default | `lib/tlv.ts:72-77` | ✅ |
| `00` payload format indicator | `01` | `generate/promptpay/AnyID.ts:44` | ✅ |
| `01` point of initiation | `11` static / `12` dynamic (amount present) | `AnyID.ts:45`, `BillPayment.ts:44` | ⚠️ D2 (what counts as "present") |
| `29` AnyID template | sub `00` = AID `A000000677010111` | `AnyID.ts:39` | ✅ |
| `29` sub `01` / `02` / `03` / `04` | MSISDN / NATID / EWALLETID / BANKACC(reserved) | `AnyID.ts:3-15` | ⚠️ D6 (BANKACC omitted) |
| MSISDN wire form | 13 digits: `0066` + 9 digits | `AnyID.ts:35`; decode back `validate/promptpay/AnyID.ts:33-35` | ⚠️ D1 |
| NATID wire form | passed through unchanged (no validation) | `AnyID.ts:40` | ⚠️ D1 (we require 13 digits) |
| EWALLETID wire form | passed through unchanged (no validation) | `AnyID.ts:40` | ⚠️ D1 (we require 15 digits) |
| `30` Bill Payment template | sub `00` = AID `A000000677010112` | `BillPayment.ts:33` | ✅ |
| `30` sub `01` / `02` / `03` | billerId / ref1 / ref2 (ref2 omitted when empty) | `BillPayment.ts:34-40` | ✅ |
| `62` / sub `07` | "(Undocumented) Reference 3", emitted after `54` | `BillPayment.ts:16-17,54-56` | ⚠️ D5 (not in brief; needed for a reference vector) |
| `53` currency | `764` | `AnyID.ts:47`, `BillPayment.ts:46` | ✅ |
| `58` country | `TH` | `AnyID.ts:48`, `BillPayment.ts:47` | ✅ |
| `54` amount | `Number(amount).toFixed(2)`, appended **after** 58 (and before 62) | `AnyID.ts:51-53`, `BillPayment.ts:50-52` | ⚠️ D2 |
| Tag emission order (must match golden vectors) | 00, 01, 29\|30, 53, 58, [54], [62], 63 | `AnyID.ts:43-55`, `BillPayment.ts:42-58` | ✅ |
| `parse` accept regex | `^\d{4}.+` (≥ 5 chars; first 4 are digits; 5th is not a JS line terminator) | `lib/parser.ts:14` | ✅ **[verified]** |
| `parse` strict | last 4 chars (uppercased) must equal `checksum(payload minus last 4)`; **nothing else** is checked | `lib/parser.ts:18-22` | ⚠️ D3, D8 |
| `parse` empty result | `null` if zero tags decoded | `lib/parser.ts:26-28` | ✅ |
| `EmvcoQR.validate(crcTagId)` | re-encodes tags except CRC tag, recomputes CRC, compares to the original payload **case-sensitively** | `lib/EMVCoQR.ts:29-33` | ✅ |
| Extract AnyID | strict parse; requires `00=="01"`, `29/00==AID`, one of `29/01..04` present; MSISDN `0066xxxxxxxxx`→`0xxxxxxxxx`; amount via `parseFloat` | `validate/promptpay/AnyID.ts:10-45` | ⚠️ D4 (amount → BigDecimal) |
| Extract Bill Payment | strict parse; requires `00=="01"`, `30/00==AID`, non-empty `30/01`, `30/02`; optional `30/03`, `62/07`, `54` | `validate/promptpay/BillPayment.ts:9-39` | ⚠️ D4 |

## 2. Phase 2 inventory (not implemented until you ask)

| Item | Reference |
|---|---|
| Slip Verify (mini QR) generate: `00{ 00=000001, 01=bank, 02=transRef }`, `51=TH`, CRC tag **`91`** | `generate/promptpay/SlipVerify.ts:18-32` |
| Slip Verify validate, incl. "crcAutoFix" (left-pads a short CRC after the last `9104`) | `validate/promptpay/SlipVerify.ts:10-35` |
| TrueMoney QR: tag 29, sub `03` = `"14000"+mobileNo`, optional tag `81` = UTF-16BE hex of message | `generate/truemoney/TrueMoney.ts:24-47`, `utils/encoder.ts:11-17` |
| TrueMoney slip verify: `00{00=01,01=01,02=eventType,03=txnId,04=DDMMYYYY}`, CRC tag `91`, **lowercase** CRC | `generate/truemoney/TrueMoneySlipVerify.ts:25-44`; validate `validate/truemoney/TrueMoneySlipVerify.ts:9-28` |
| BOT Barcode `\|biller\rref1\rref2\ramountSatang`; `toQrTag30()` | `lib/BOTBarcode.ts:21-62` |
| *(not in brief)* BCEL OneProof validate | `validate/bcel/BCELOneProof.ts` — skipped unless you want it |

Phase 2 needs `crcTagId` (91) and lower-case CRC support in the model — keep that in mind for the Phase 1 design (Q4).

## 3. Differences between the brief and the reference (resolved as shown)

| # | Brief says | Reference does | Resolution |
|---|---|---|---|
| D1 | Normalise `0812345678`, `081-234-5678`, `+66812345678`, `66812345678` → `0066812345678`; validate NATID=13, e-wallet=15 | Only `replace(/^0/,'66')` then left-pad to 13 (`AnyID.ts:35`). **[verified]** `0812345678`✓ `66812345678`✓ but `081-234-5678`→`6681-234-5678` ✗, `+66812345678`→`0+66812345678` ✗, `0066812345678`→`6066812345678` ✗, `08123`→`0000000668123` ✗. No NATID/EWALLET validation. | Follow the **brief** (the reference's intended outputs match; its garbage outputs are not "behavior" worth porting). Strip spaces/`-`; accept `0xxxxxxxxx`, `66xxxxxxxxx`, `+66xxxxxxxxx`, `0066xxxxxxxxx` (9 digits after the country code); anything else → `IllegalArgumentException`. NATID: 13 digits (dashes/spaces stripped, **no** mod-11 checksum — the reference has none). E-wallet: 15 digits. |
| D2 | Amount is `BigDecimal`, `HALF_UP`, 2 dp, reject `<= 0`; `01=12` when amount present | `!amount ? '11':'12'`, `Number(x).toFixed(2)` (`AnyID.ts:45,52`). `0`/`NaN` → silently static; negative → `12` + `-5.00`; binary-double rounding: **[verified]** `1.005→"1.00"`, `2.675→"2.67"` (BigDecimal HALF_UP gives `1.01`, `2.68`). | Follow the **brief**: `null` → static (`11`, no tag 54); non-null `<= 0` **or rounding to 0.00** → `IllegalArgumentException`. Tests assert both the golden vectors (`30`→`30.00`, `10.05`, `3649.22`) and the HALF_UP cases, noting they intentionally differ from JS. No upper bound enforced (EMVCo caps tag 54 at 13 chars; we throw if the formatted amount exceeds 13 chars — flagged Q8). |
| D3 | `strict = true` enforces CRC **and required tags** | `parse(strict)` checks CRC only (`parser.ts:18-22`); "required tag" checks live in the `validate.*` functions (AID, billerId, ref1). | Follow the **reference** for `PromptParse.parse` (CRC + well-formedness only; a generic EMVCo parser can't require tag 00/01 because Slip Verify QRs start with a template at tag `00`). Required-tag enforcement lives in the PromptPay readers (D4). See Q6. |
| D4 | API has `parse`, `validateCrc`, `anyId`, `billPayment` | Also has `validate.anyId` / `validate.billPayment` that **read** a PromptPay QR back into fields (`validate/promptpay/*`), and 7 of the reference's test vectors exercise them (`index.test.ts:178-240`). | Add `PromptPay.parseAnyId(payload): AnyIdInfo?` and `PromptPay.parseBillPayment(payload): BillPaymentInfo?` (immutable classes; `amount: BigDecimal?`). Needed to port the vectors; Q5. |
| D5 | `billPayment(billerId, ref1, ref2, amount)` | Also `ref3` → tag `62/07`; one golden vector uses it (`index.test.ts:89-100`). | Add `ref3: String? = null` as the **last** parameter (`@JvmOverloads`), so brief-shaped calls still compile in Kotlin. Java sees extra overloads. Q5. |
| D6 | `AnyIdType { MSISDN, NATID, EWALLETID }` | `ProxyType` also has `BANKACC='04'` ("Reserved") (`AnyID.ts:13-14`). | Omit `BANKACC` (no behavior to port, nothing validated). Adding an enum constant later is source-compatible for Kotlin, but changes `.api`; say if you want it now. |
| D7 | CRC-16/CCITT-FALSE | Function is called `crc16xmodem` but is called with init `0xFFFF` (`tlv.ts:73`) — that *is* CCITT-FALSE (true XMODEM is init 0). | Brief is right; just a misleading name in the reference. Check value: `CRC("123456789") = 0x29B1`. |
| D8 | `encode()` always recomputes CRC; uppercase | Strict parse compares **case-insensitively** (`parser.ts:19`: `.toUpperCase()` on the received CRC); `EmvcoQR.validate` compares case-sensitively (`EMVCoQR.ts:32`). Real-world scanners sometimes emit lowercase. | `parse(strict=true)` and `validateCrc` accept either case (as reference `parse`); output is always uppercase. |
| D9 | — | `('00'+len).slice(-2)`: **[verified]** length 100 → `"00"`, 105 → `"05"` — silently produces corrupt payloads. | We throw `IllegalArgumentException` if a value exceeds 99 units. |

## 4. Ambiguities / decisions needed (recommended default in **bold**)

**Q1 — Where does this project live?** `E:\promptparse` is a checkout of the *TypeScript* repo itself (same HEAD `7487a97`, root has `README.md`, `LICENSE.md` (MIT), `package.json`, `src/`, `.github/workflows/publish.yml` (npm publish)). The requested layout would put `README.md`/`LICENSE`/`NOTICE`/`ci.yml` next to — and overwrite — the TS ones. **Default: build in a new sibling directory/repo (e.g. `E:\promptparse-jvm`), leave the TS checkout untouched** (this SPEC_NOTES.md is the only file I've written here, and I'll move it).

**Q2 — TLV length unit for non-ASCII values.** The reference is internally inconsistent **[verified]**:
- `decode` slices by JS string index (`tlv.ts:32`) and `tag()` sets `length = value.length` (`tlv.ts:126`) → **UTF-16 code units**.
- CRC is over **UTF-8 bytes** (`checksum.ts:46-85`).
- Its own non-ASCII vector (`index.test.ts:252-257`) declares `5927` for `ร้านทดสอบ` (9 chars × 3 bytes = 27) and `6021` for `กรุงเทพ` (7 × 3 = 21) → **UTF-8 bytes**. With the reference's `decode`, that payload mis-parses: tag `59` swallows `…6021กรุงเทพ630488F` and a bogus tag `"4"` is left over. The reference test only asserts `parse(...)` is truthy and the CRC check, so it never notices.

So "match the reference exactly" has no single answer. Options:
- (A) UTF-16 units: matches reference *code* and `String.length`; the reference's *own vector* then mis-parses in our port too.
- (B) **UTF-8 bytes: consistent with the CRC and with the reference's non-ASCII vector; deviates from reference code only for non-ASCII.** ASCII payloads (all PromptPay/AnyID/Bill Payment cases) are identical under A and B. Emoji (astral) is the only place A and B differ from "code points".
Default **B**, with tests for Thai, Latin-1 (`Café`), astral (`😀`) and a length-boundary case, plus a documented test showing what A would produce.

**Q3 — Lenient parsing of malformed TLV.** Reference never fails: a non-digit length yields `NaN` and re-parses garbage (`tlv.ts:31`, **[verified]**: `"0002ABxxxx"` → tag `xx` with value `xxxx`); a truncated tag is kept with a clamped value but its stored `length` still says the declared size (`tlv.ts:32-34`). **Default: lenient = stop at the first malformed (non-numeric length) or truncated tag and keep the tags before it (null if none); strict = any malformation, including trailing bytes or truncation, → null.** Never throws.

**Q4 — Is the CRC tag part of `EmvcoQr`?** Brief wants `parse(encode(qr)) == qr`, and `encode()` always recomputes CRC. That only holds if the CRC tag isn't stored (otherwise `qr` from a hand-built list has no `63` but the re-parsed one does). **Default: the final tag is dropped from `EmvcoQr.tags` when its ID is `63` and length `04` (the CRC is derived, not data); `qr.tag("63")` returns `null`.** Reference's `getTag('63')` is not exercised by any reference test. Phase 2 will add a `crcTagId` parameter for `91`.
`with(id, value)`: replace the first tag with that ID in place, else append; sub-tags re-derived with the parse rule; `with("63", …)` → `IllegalArgumentException`.

**Q5 — API additions beyond the brief:** `PromptPay.parseAnyId` / `parseBillPayment` (D4) and `ref3` (D5). **Default: add both.**

**Q6 — "Required tags" under `strict`:** see D3. **Default: CRC + well-formed TLV only in `PromptParse.parse`; required tags enforced by `PromptPay.parseAnyId/parseBillPayment` (which always parse strictly, like the reference).**

**Q7 — Toolchain.** `jvmToolchain(17)` but this machine has JDK 21 on `PATH` and a Gradle-provisioned JDK 25; no JDK 17. Gradle 9.6.1 is cached (I'll generate the wrapper from it). **Default: add the `foojay-resolver-convention` settings plugin so Gradle auto-downloads JDK 17 (build-time only, not a runtime dependency)**; alternative is install JDK 17 yourself. CI uses `actions/setup-java` 17.

**Q8 — Extra length guards.** EMVCo caps tag 54 at 13 chars and several fields at 99; reference enforces none. **Default: enforce only the hard TLV limit (value ≤ 99 units, D9) and tag 54 ≤ 13 chars.**

Also noted, no decision needed:
- `parse` first-5-chars rule `^\d{4}.+`: JS `.` rejects `\n \r \u2028 \u2029`; we hand-code the same set (Java's `.` additionally rejects `\u0085`).
- Sub-tag detection runs on **every** tag whose value matches `^\d{4}.+` (e.g. tag 59 `"1234 Shop"` is attempted; accepted only if it decodes cleanly), exactly as the reference does. Ported with explicit false-positive tests.
- Template IDs are not hard-coded in the parser (29, 30, 62 are discovered by the shape rule above).
- Apache-2.0 for our code is compatible with porting MIT code **provided** the MIT notice travels with it → full MIT text in `NOTICE`.

## 5. Reference test vectors to port (`index.test.ts`)

| Vector | Ref line | Phase |
|---|---|---|
| parse `AAAA0000` → null | 5-7 | 1 |
| 3 tags from `000411110104222202043333`; `getTag('01')`=`2222` | 9-15 | 1 |
| strict: bad CRC `FFFF` → null; good CRC `42BE`, `29/01`=`0066801111111` | 17-33 | 1 |
| anyId MSISDN `0812223333` → `…63041DCF`; with amount 30 → `…540530.0063043CAD` | 35-56 | 1 |
| Bill Payment with ref1/ref2/ref3 → `…780E` | 89-100 | 1 |
| `validate('63')` on good payload → true | 122-129 | 1 (→ `validateCrc`) |
| validate AnyID: MSISDN, MSISDN+amount, NATID; slip payload → null | 178-204 | 1 |
| validate Bill Payment: minimal, full, AnyID payload → null | 206-240 | 1 |
| checksum UTF-8: `Café`=8FDF, `ร้านทดสอบ`=1EA0, `a😀b`=BF6B, lone surrogate `64F6` | 242-250 | 1 (crc is `internal`; tested through it) |
| strict parse of UTF-8 merchant payload (`…88F4`), and tampered `FFFF` → null | 252-258 | 1 (**see Q2**) |
| Slip Verify gen/validate (3 vectors) | 58-65, 131-148 | 2 |
| TrueMoney gen (2 vectors) | 67-87 | 2 |
| BOT Barcode gen/convert (6 vectors) | 102-120, 150-176 | 2 |

Added by us (not in reference): CRC check value `"123456789"→29B1`; property/fuzz tests; D1/D2/D9 edge cases.
