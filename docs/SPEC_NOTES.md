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

Status after Phase 1: all Phase 1 vectors above are ported and green (`ReferenceVectorsTest`). Decisions Q1–Q8 were accepted as written.
D10 (found while implementing): sub-tag/top-level ids must be 2 ASCII **digits**; the reference accepts any 2 characters (`tlv.ts:30`). EMVCo ids are numeric, so we stop/ reject earlier than the reference on non-numeric ids.

---

# Part 2 — second reference: `thai-qr-payment` (gap analysis, no code changed yet)

## 6. Sources and license gate

| Item | Value |
|---|---|
| Repo | <https://github.com/uunw/thai-qr-payment>, commit `ef874fd`, npm `thai-qr-payment@1.2.0` (`npm view`: repository `git+https://github.com/uunw/thai-qr-payment.git`, license `MIT`) |
| License file | `LICENSE`: "MIT License, Copyright (c) 2026 uunw" — confirmed by reading the file, not just npm metadata |
| Gate result | **MIT → logic and test vectors may be ported**, with attribution. Action when implementing: add the MIT text + copyright line to `NOTICE`, credit in README, mention in CHANGELOG. |
| Docs read | Every page under `docs/src/content/docs/{guide,reference}` (payload, slip-verify, barcode, spec, qr, render, assets, react, cli, sizes, edge, cdn); the live page <https://thai-qr-payment.js.org/guide/slip-verify/> matches the repo source. qr/render/assets/react/cli/sizes/edge/cdn describe QR *rendering* and packaging — out of scope for a payload library. |
| Code read | `packages/payload/src/{tlv,crc,tags,recipient,amount,builder,parser,slip-verify,barcode,message}.ts` and `slip-verify.test.ts` |
| Probe | I executed that package's own `builder`/`slip-verify`/`barcode`/`crc` code (copied to a scratch dir, imports renamed `.js→.ts`, run with Node's type stripping) to get outputs rather than guessing. |
| Also cloned | promptparse is now at `/tmp/ref/promptparse` for comparison (same commit `7487a97`). |

Conflict rule used below (yours): **BOT spec wins when either reference cites it; otherwise lenient on parse, strict on generate.** Neither reference quotes the BOT/EMVCo text for the points in dispute (tqp says it follows a BOT supplement "not publicly indexed", `docs/.../spec.md`), so where I cannot verify a spec claim I say so and use the second half of the rule.

## 7. Comparison table

`tqp` = thai-qr-payment (`packages/payload/src/…`), `pp` = promptparse (`src/…`), ours = this repo (`core/src/main/kotlin/io/github/sunJirapath/promptparse/…`).

| Feature / field | Ours today | pp | tqp | Proposed action | Source |
|---|---|---|---|---|---|
| **TLV decode, malformed input** | lenient: stop at first bad tag, keep earlier ones; strict: `null`. Never throws | never fails; `NaN` lengths re-parse garbage | **throws** `SyntaxError` on truncated header / non-numeric length / value past end | keep ours (null, never throws). Lenient-on-parse ✔ | ours `Tlv.kt:117-141`; pp `tlv.ts:23-39`; tqp `tlv.ts:50-72` |
| **TLV length unit, non-ASCII** | UTF-8 **bytes** (Q2) | code: UTF-16 units; its Thai vector: bytes | code: UTF-16 units (`value.length`), but comment/docs say "byte length"/"99 bytes"; **no non-ASCII vector exists** | keep ours. Both codes agree with us for ASCII (all PromptPay/slip data); tqp's *documented* intent is bytes. Not spec-verifiable → record only | ours `Tlv.kt:32-60`; pp `tlv.ts:32,126`; tqp `tlv.ts:18-19`, `tlv.test.ts:18-25` |
| **Value > 99** | `IllegalArgumentException` | silently wraps (`100→"00"`) | throws `RangeError` | keep ours (strict on generate ✔, agrees with tqp) | ours `Tag.kt` init; pp `tlv.ts:52`; tqp `tlv.ts:19-23` |
| **Empty values on generate** | kept as given (`ref2=""` omitted by `billPayment` only) | `if (ref2)` skips falsy | `encodeFields` drops null **and** empty for every field | keep; document | ours `PromptPay.kt:55`; pp `BillPayment.ts:38`; tqp `tlv.ts:37` |
| **CRC algorithm** | CCITT-FALSE, over **UTF-8 bytes** | CCITT-FALSE, UTF-8 bytes (fixed upstream in `cd749f7`) | CCITT-FALSE, but each char `& 0xFF` (**Latin-1 truncation**, not UTF-8) | keep ours. Byte-identical for ASCII (**verified**: `123456789→29B1`); for non-ASCII tqp differs (**verified**: `Café` tqp `1050` vs ours/pp `8FDF`; Thai `6605` vs `1EA0`). tqp has no non-ASCII CRC vector, and its doc says "ASCII-encoded payload" → its truncation is an unexamined edge, ours follows pp's tested vector | ours `Tlv.kt:150-160`; pp `checksum.ts:46-105`; tqp `crc.ts:28-35` |
| **CRC case on parse** | strict accepts either case | `toUpperCase()` on received → either case | main parser: uppercase only (`===`); slip: standard uppercase only; TrueMoney either | keep ours (lenient on parse). **Differs from tqp's standard-slip parser**, which rejects a lowercase CRC (**verified**: `null`) | ours `Tlv.kt:163-171`; pp `parser.ts:19`; tqp `slip-verify.ts:220-223`, `parser.ts:325` |
| **CRC case on generate** | uppercase (TrueMoney slip lowercase planned) | uppercase; TrueMoney slip lowercase | uppercase; TrueMoney slip lowercase | keep; TrueMoney slip lowercase (both agree) | pp `TrueMoneySlipVerify.ts:43`; tqp `slip-verify.ts:157` |
| **Tag order on generate** | `00,01,29/30,53,58,[54],[62]` (pp order; 4 pp golden vectors depend on it) | same as ours (54 **after** 58) | **ascending by id** (`…53,54,58…`); comment: "spec mandates ascending order" (unverified) | **DECISION NEEDED (breaking output, see §8 P5)**. **Verified**: static QRs are byte-identical, dynamic ones differ: `…TH540530.0063043CAD` (pp/ours) vs `…5303764540530.005802TH6304F9BA` (tqp) | ours `PromptPay.kt:101-113`; pp `AnyID.ts:43-53`; tqp `builder.ts:426-428` |
| **AnyID mobile normalization** | accepts `0812345678`, `081-234-5678`, `081 234 5678`, `+66…`, `66…`, `0066…`; else `IllegalArgumentException` | only `^0→66` + left-pad: garbage for `+66…`, `081-…`, `0066…` | strips every non-digit, then `^0→66`, pad 13. `+66…`, `081-…`, `66…` OK; **`0066…` with explicit mobile → garbage** (**verified**: wire `0066066812345678…`) | keep ours (strictest and correct for all 5 forms; `0066` form is only handled by us). All three agree on `0812345678`, `66812345678`, `+66812345678` → `0066812345678` (**verified**) | ours `PromptPay.kt:129-143`; pp `AnyID.ts:35`; tqp `recipient.ts:34-49` |
| **AnyID type inference** | explicit `AnyIdType` only | explicit | inferred from digit count (≥15 e-wallet, ≥13 national ID, else mobile), explicit override | keep explicit (no guessing on generate). Optional additive helper not proposed | tqp `recipient.ts:67-68` |
| **NATID / e-wallet validation** | 13 / 15 digits, else IAE | none | 13 / 15 digits (`RangeError`) | agree ✔ no change | tqp `recipient.ts:74-81` |
| **PromptPay bank account (sub 04) and OTA (sub 05, AID `A000000677010114`)** | not supported (D6) | enum has `BANKACC` reserved; read-only, no generate | generate + parse: sub 04 = 3-digit bank code + account (≤43), OTA code 10 chars flips AID | **optional additive (P4)**; needs a decision, source is tqp only and cites the BoT supplement | tqp `recipient.ts:96-111`, `builder.ts:182-201`, `tags.ts:74` |
| **Amount** | `BigDecimal`, `HALF_UP`, `<=0` → IAE, ≤13 chars | `Number.toFixed`, `!amount` → static; negative accepted | integer-math `Math.round`, `<=0` → **static silently**, `>9999999999.99` throws, `NaN` throws | keep ours (strict on generate). Verified agreement: `30→30.00`, `10.05`; JS rounding differs from `HALF_UP` (`1.005→1.00`, `2.675→2.68`(tqp)/`2.67`(pp), ours `1.01`/`2.68`) | ours `Amounts.kt:17-31`; pp `AnyID.ts:51-53`; tqp `amount.ts:21-48` |
| **Bill Payment (tag 30)** | AID `…0112`, 01 biller (13 or 15 digits), 02 ref1 required, 03 ref2, `ref3`→62/07; IAE on bad input | no validation; `ref3`→62/07 | no validation (billerId, refs optional); refs dropped if empty; **cross-border AID `A000000677012006`**; ref3-style data goes in tag 62 sub-fields (9 slots) | keep ours. **Verified byte-identical** for billerId+ref1+ref2 and for ref3 vs tqp `additionalData({terminalLabel})` (both `…62070703SCB6304780E`). Cross-border AID + 62 sub-fields = optional additive (P4) | ours `PromptPay.kt:45-60`; pp `BillPayment.ts:25-59`; tqp `builder.ts:251-269,303-359`, `tags.ts:75-79` |
| **Read PromptPay payloads** | `parseAnyId` / `parseBillPayment`: strict CRC, `null` unless AID + required sub-tags; `0066`+9→`0`+9 only for 13-char form | `validate.anyId/billPayment`, same checks | `parsePayload`: **throws** on bad CRC; unknown AID → `merchant:null`; mobile wire: strip leading zeros, `66…→0…` (works for any length) | keep null-not-throw. Optional behavior change P6: make our mobile read as lenient as tqp | ours `PromptPay.kt:67-94`; pp `validate/promptpay/AnyID.ts:10-45`; tqp `parser.ts:176-183,318-364,372-462` |
| **Slip Verify (standard): envelope** | **missing** | root `00`{`00=000001`,`01` bank,`02` ref}, `51=TH`, CRC tag `91` uppercase | identical layout | **P1 additive**: `SlipVerify.build/parse` | pp `SlipVerify.ts:18-32`; tqp `slip-verify.ts:21-30,83-92` |
| **Slip: golden vector** | — | `004000060000010103002021900021231231212000115102TH91049C30` (`index.test.ts:58-65`) | same string (`slip-verify.test.ts:13`) | port; both references agree byte-for-byte (**verified** by running tqp builder) | — |
| **Slip: root tag / API marker checks** | — | requires `00/00=="000001"`, non-empty `00/01`, `00/02`; does not look at tag `51` | same checks; also does not check `51`; strict TLV of the body (extra bytes → `null`) | **P1**: require marker `000001`, bank, ref → else `null`; do not require `51` (neither does; lenient). Strict body TLV (trailing bytes → `null`) as tqp (`parseFields` throws → `null`, by reading `slip-verify.ts:108-113`) | pp `validate/promptpay/SlipVerify.ts:21-29`; tqp `slip-verify.ts:104-131` |
| **Slip: CRC tag location** | — | `lastIndexOf("9104")` | `lastIndexOf("9104")` | **P2**: do not use `lastIndexOf` (see next row) | pp `SlipVerify.ts:12`; tqp `slip-verify.ts:212` |
| **Truncated-CRC auto-fix (slip)** | — | on by default, `crcAutoFix=true` flag; only when 1–3 chars follow the last `9104` | on by default, **no flag**; accepts 1–4 char tail; pads with `0` to 4 | **P2 additive**: on by default, `crcAutoFix` flag to disable, **never on generate**. Candidates tried from the *end* (tail length 4,3,2,1 before the final `9104`+tail) so a CRC that itself contains `9104` (e.g. `…91049104`) is still found — both references' `lastIndexOf` mis-handle that case (by reading the code, not executed: tqp sees an empty tail → `null`; pp skips its fix and relies on the generic strict check). Rejects empty or >4-char tail | pp `validate/promptpay/SlipVerify.ts:11-19`; tqp `slip-verify.ts:211-227` |
| **Truncated-CRC auto-fix (payment QR, tag 63)** | — (lenient parse ignores CRC; strict requires full CRC) | none | main parser tries 1–3 dropped chars unless `strict:true`; reports `crc.truncated` | **P3 optional additive**: `crcAutoFix` for `PromptParse.parse(..., strict=true)` and the PromptPay readers, default **off** there (strict means strict; tqp also disables it in strict). Needs your OK | tqp `parser.ts:336-353`, `parser.test.ts` |
| **Slip: TrueMoney variant** | — | `00{00=01,01=01,02 eventType,03 txnId,04 date}`, **no tag 51**, CRC `91` **lowercase** on generate | identical layout and lowercase; date must be 8 chars (`RangeError`) | **P1 additive**: `SlipVerify.buildTrueMoney/parseTrueMoney`; date = 8 ASCII digits (`DDMMYYYY`, shape only, not calendar-checked) on generate | pp `TrueMoneySlipVerify.ts:25-44`; tqp `slip-verify.ts:142-158` |
| **Slip: TrueMoney discriminator** | — | returns `null` only if **both** `00` and `01` ≠ `"01"` (`&&`, looks like a bug); returns `eventType`/`txnId`/`date` even if missing (`undefined`) | **both** markers must be `01` and all three fields present, else `null` | follow **tqp** (stricter and documented: "marker pair is the discriminator"). Standard→TrueMoney parser and vice versa return `null` (documented in tqp; asserted by its tests `slip-verify.test.ts:123,231`) | pp `validate/truemoney/TrueMoneySlipVerify.ts:16-21`; tqp `slip-verify.ts:191-196`, guide `slip-verify.md` |
| **Slip: TrueMoney CRC compare** | — | goes through generic strict `parse` → either case | either case, via lower-casing both sides | accept either case | pp `parser.ts:19`; tqp `slip-verify.ts:220-222` |
| **Slip: golden vectors** | — | none for TM slip | `00480002010102010203P2P0313TXN00012345670408250120249104b425` and CRC-drop cases (`T00000086→0539`, `T00000178→00C3`, `T00003342→0004`, `TXN0000000005→0549`) | port all (MIT). They exercise 1/2/3-char truncation exactly as your test list asks | tqp `slip-verify.test.ts:14,71-96,215-226` |
| **TrueMoney payment QR + tag 81** | — (Phase 2 item) | tag 29/03 = `"14000"+mobileNo`; tag 81 = UTF-16BE hex; no parse | `"14"+digits.padStart(13,"0")` (≤13 digits); tag 81; **parse**: `14`-prefix on sub 03 ⇒ TrueMoney, decodes message | **P3 additive**: `TrueMoney.build(mobile, amount?, message?)` (+ read). **Verified** identical for `0801111111` static (`…63047C0F`); tag 81 hex identical; with amount they differ only by tag order (P5) | pp `TrueMoney.ts:24-47`, `encoder.ts:11-17`; tqp `builder.ts:213-237`, `message.ts:12-36`, `parser.ts:185-192,245-253` |
| **BOT 1D barcode** | — (Phase 2 item) | `|biller\rref1\rref2\ramountSatang`; parse: no validation, `parseInt` amount | same format; **build** pads billerId to 15 with `0`, rejects CR/empty ref1/negative/over max; **parse** requires biller ≥15, ref1 non-empty, amount digits only (decimal → `null`) | **P3 additive** `BotBarcode.build/parse/toBillPayment`. BOT standard is cited by both (pp README, tqp docs) → follow the **stricter, spec-shaped** rules of tqp for generate and for the shape of parse; amount via `BigDecimal` satang. **Verified** both emit `|099400016550100\r123456789012\r670429\r364922` | pp `BOTBarcode.ts:21-62`; tqp `barcode.ts:69-85,108-133,136-203` |
| **Other tags (tqp only)** | readable generically via `EmvcoQr.tag/value`; not generated | — | builder: 52 MCC, 59 name (truncated 25), 60 city (15), 61 postal, 55–57 tip, 62 sub-fields 01–09, 80 VAT TQRC, `pointOfInitiation` override | **not proposed** (outside the PromptPay-library brief; each needs spec we can't verify). Reading them already works. Ask if wanted | tqp `builder.ts:289-359`, `tags.ts:12-93` |
| **Null vs exception** | generators: `IllegalArgumentException`; parsers: `null`/`false`, never throw | parsers `null`; generators don't validate | **mixed**: `parsePayload` throws (`Error`/`SyntaxError`); slip + barcode parsers return `null`; builders throw `TypeError`/`RangeError` | keep ours (strict-throw on generate, null on parse) — consistent with the rule and with tqp's slip/barcode parsers | — |

## 8. Proposed changes, by priority

| # | Change | Kind | Notes |
|---|---|---|---|
| **P1** | `SlipVerify` object: `build(sendingBank, transRef)`, `parse(payload, crcAutoFix = true)` → `SlipVerifyInfo?`; `buildTrueMoney(eventType, transactionId, date)`, `parseTrueMoney(payload, crcAutoFix = true)` → `TrueMoneySlipVerifyInfo?` (immutable result classes). Parsers return `null` for: non-slip payloads, wrong root/API marker, bad CRC, trailing bytes, other variant. Generators throw `IllegalArgumentException` (empty bank/ref, date not 8 digits). | **additive** | Internals use `Tlv` directly with CRC tag `91` (no change to `EmvcoQr`, which stays 63-only). |
| **P2** | Truncated-CRC auto-fix inside the two slip parsers: default on, `crcAutoFix = false` disables, never applied by `build`. Candidate search from the end (fixes the `lastIndexOf("9104")` edge in both references). | **additive** | Shipped together with P1. |
| **P3** | `TrueMoney.build(mobile, amount?, message?)` + read; `BotBarcode.parse/build/toBillPayment`; optional `crcAutoFix` for tag-63 strict parsing (default off). | **additive** | The first two were your original Phase 2 list. The tag-63 auto-fix is the only item here I'd skip unless you want it. |
| **P4** | Bank account (04) / OTA (05, AID `…0114`) / cross-border bill AID (`…012006`) / tag 62 sub-fields. | **additive** (adds `AnyIdType.BANKACC`) | Single-source (tqp), cites an unpublished BoT supplement. I recommend **not** doing it now. |
| **P5** | Emit tags in **ascending id order** (`…53, 54, 58`) instead of pp's `…53, 58, 54`. | **BREAKING (generated bytes)** | Changes every dynamic QR and the pp golden vectors `…540530.0063043CAD`, `…54073649.2263044534`, bill payment with amount, TrueMoney with amount (all would need new CRCs). tqp claims the spec mandates ascending order; I could not verify it (the BoT PDF is not fetchable here) and pp does not mention order. Both orders scan in practice. **Recommendation: leave as is until you confirm the BoT text; if it requires ascending order, switch and re-derive the vectors (they would then match tqp byte-for-byte).** |
| **P6** | `parseAnyId` mobile read as lenient as tqp (strip leading zeros, `66…`→`0…` for any length). | **behavior change** (no signature change) | Today `006681222333` (12 chars) is returned as written, and two of my tests pin that. Low value; skip unless you want it. |

## 9. Cross-check results (both references vs ours, byte-for-byte)

Executed tqp's code; promptparse outputs are its own vectors (already green in our suite).

| Case | pp | tqp | Ours | Agree? |
|---|---|---|---|---|
| AnyID mobile `0812223333`, static | `…63041DCF` | `…63041DCF` | `…63041DCF` | yes (all three) |
| Bill payment `0112233445566/CUSTOMER001/INV001` (+ `SCB` as 62/07) | `…780E` | `…780E` | `…780E` | yes |
| TrueMoney `0801111111`, static | `…63047C0F` | `…63047C0F` | (P3) | yes |
| Standard slip `002` / `0002123123121200011` | `…91049C30` | `…91049C30` | (P1) | yes |
| TrueMoney slip `P2P/TXN0001234567/25012024` | CRC lowercase (same layout) | `…9104b425` | (P1) | yes (layout + lowercase) |
| BOT barcode `099400016550100/123456789012/670429/3649.22` | `…364922` | `…364922` | (P3) | yes |
| AnyID `0812223333` + 30 THB | `…TH540530.00…3CAD` | `…540530.005802TH…F9BA` | pp's | **no — tag order (P5)** |
| Amount `1.005` / `2.675` | `1.00` / `2.67` | `1.00` / `2.68` | `1.01` / `2.68` | no — pp/tqp disagree with each other (double vs rounding); ours is the documented HALF_UP |
| CRC of non-ASCII (`Café`) | `8FDF` | `1050` | `8FDF` | no — tqp truncates to Latin-1 |
| Mobile `0066812345678` (explicit mobile) | garbage | garbage | `0066812345678` | no — ours is the only correct one |
| `amount <= 0` | static / negative accepted | static | IAE | no — deliberate (strict on generate) |

## 10. Test plan for Part 2 (will be added with the code)

Slip verify: valid standard and TrueMoney (both vectors above); truncated CRC of 1, 2 and 3 chars for both variants (tqp's `T00000086`, `T00000178`, `T00003342`, `TXN0000000005` cases); payload whose CRC itself contains `9104`; wrong root tag, wrong API marker, TrueMoney marker pair missing, standard-vs-TrueMoney cross-rejection, tag-63 PromptPay payload, empty/garbage, bad CRC, trailing bytes, >4-char tail, empty tail → `null`; `crcAutoFix = false` → `null` for each truncated case; lowercase/uppercase CRC accepted on parse; generators never truncate and throw `IllegalArgumentException` on bad input; round-trip property test (random bank/ref/date → `parse(build(x)) == x`) and fuzz (never throws); Java example extended; cross-check test asserting our output equals tqp's recorded vectors where both references agree.

## 11. Part 2 decisions and result

Approved: P1, P2, P3. Skipped: P4 (bank account / OTA / cross-border / tag 62 sub-fields) and P6 (lenient mobile read). **Tag order unchanged** (P5 declined: 54 stays after 58, as in promptparse).
API shape: one `SlipVerify` object with `build` / `parse` (standard) and `buildTrueMoney` / `parseTrueMoney`.
Implemented differences from either reference, on purpose: standard slip CRC accepted in either case (tqp: uppercase only); bank code must be 3 digits and TrueMoney date 8 digits on generate; TrueMoney slip needs both markers and all three fields (tqp's rule, not promptparse's); CRC repair searches from the end; `BotBarcode.build` rejects amounts <= 0 (tqp writes `0`, meaning "none", for 0 — pass `null` instead).
