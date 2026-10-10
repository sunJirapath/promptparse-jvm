# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- `SlipVerify`: standard (BOT) and TrueMoney slip-verify mini QR, `build` / `parse`. Parsers return `null` for non-slip payloads, wrong marker, bad CRC, trailing bytes or the other variant.
- Truncated-CRC auto-fix for slip parsing (on by default, `crcAutoFix = false` disables; never applied when building). Candidates are searched from the end, so a CRC that itself contains `9104` is handled.
- `PromptParse.fixTruncatedCrc` for tag-63 payment QRs (explicit, never done by `parse`).
- `TrueMoney` QR (`build` / `parse`, tag 81 personal message) and `BotBarcode` (`build` / `parse` / `toBillPayment`).
- `PromptParse.parse` / `validateCrc`: lenient and strict TLV parsing of EMVCo QR payloads, with sub-tags.
- `EmvcoQr` (immutable; `tag`, `value`, `with`, `encode`) and `Tag`.
- `PromptPay.anyId` (mobile / national ID / e-wallet) and `PromptPay.billPayment`, with `BigDecimal` amounts.
- `PromptPay.parseAnyId` / `parseBillPayment` to read generated or scanned payloads.
- Java example module, property and fuzz tests, Kover (90% gate) and API dump checks.

Credits: behavior and vectors cross-checked with, and partly derived from, uunw/thai-qr-payment (MIT); see `NOTICE`.
The API change is purely additive (`core.api` has no removed lines). Tag order is unchanged (54 follows 58, as in promptparse).

See `docs/SPEC_NOTES.md` for every deliberate difference from the TypeScript reference.
