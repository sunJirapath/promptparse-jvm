# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project aims to follow
[Semantic Versioning](https://semver.org/) once it reaches 1.0.

## [Unreleased]

### Fixed

- `EmvcoQr.tags` and `Tag.subTags` are defensive, unmodifiable copies, including for Java consumers.
- Strict payment parsing requires exactly one final CRC tag `63` with length `04`; a checksum inside another
  tag's value or a duplicate CRC tag is rejected. Lenient parsing is unchanged.
- `Tag.template` derives children using the same one-level rule as parsing, preserving round-trip equality
  for nested templates and templates containing empty children. The encoded value is preserved.
- TrueMoney parsing removes only fixed padding, preserving all ten local-number digits including leading zeros.
- All generated JARs include `META-INF/LICENSE` and `META-INF/NOTICE`; `check` verifies binary, sources and documentation JARs.
- Installation examples use the published Maven Central version `0.1.0`.

### Added

- Optional `validateFields` on AnyID, Bill Payment and TrueMoney readers checks recipient/biller shape,
  initiation method, Thai baht currency, country, and positive amount limits. Existing one-argument methods
  remain available and keep permissive field extraction. This is format validation, not payment verification.

## [0.1.0] - 2026-10-11

### Added

- `PromptParse`: `parse` (lenient or strict, with sub-tags) and `validateCrc` for EMVCo-style QR payloads;
  `fixTruncatedCrc` for tag-63 payment QRs whose CRC lost leading zeros.
- `EmvcoQr` and `Tag`: immutable model with `tag`, `value`, `with` and `encode` (the CRC is always recomputed).
- `PromptPay`: `anyId` (mobile, national ID, e-wallet), `billPayment` (with optional `ref2`, `ref3`, amount) and
  the readers `parseAnyId` and `parseBillPayment`. Amounts are `BigDecimal` (`HALF_UP`, 2 decimals, > 0).
- `SlipVerify`: standard (Bank of Thailand) and TrueMoney slip-verify mini QR, `build` / `parse`.
  Parsers return `null` for non-slip payloads, wrong marker, bad CRC, trailing bytes or the other variant.
- Truncated-CRC auto-fix for slip parsing (on by default, `crcAutoFix = false` disables, never applied when
  building). The CRC is located from the end, so a CRC that itself contains `9104` still parses.
- `TrueMoney` QR (`build` / `parse`, personal message in tag 81) and `BotBarcode` (`build` / `parse` /
  `toBillPayment`).
- Parsers ignore the surrounding spaces, tabs and line breaks that scanners leave around a payload.
- Java example module that calls every public entry point and asserts its own output.
- Build: Spotless (ktlint, google-java-format), detekt, Kover (90% line coverage gate), binary-compatibility
  validator (`core/api/core.api`), `-Xjdk-release=11`, Dokka javadoc and sources jars.
- CI on JDK 11, 17, 21, 25 (Linux) and 21 (Windows), CodeQL, Dependabot, tag-triggered release workflow.
- Tests: reference vectors, a parser matrix of hostile inputs, generator edge cases, round-trip property tests and
  fuzzing (jqwik), one regression test per bug fixed.

### Changed

- The package and Maven groupId are lowercase: `io.github.sunjirapath` (nothing was published under the old name).

### Notes

- Behavior and test vectors are ported from [promptparse](https://github.com/maythiwat/promptparse) (MIT) and
  cross-checked against [thai-qr-payment](https://github.com/uunw/thai-qr-payment) (MIT); see `NOTICE`.
  Every deliberate difference is in `docs/SPEC_NOTES.md` and the README table. Tag order is unchanged from
  promptparse (tag 54 follows tag 58).

[Unreleased]: https://github.com/sunJirapath/promptparse-jvm/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/sunJirapath/promptparse-jvm/releases/tag/v0.1.0
