# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- `PromptParse.parse` / `validateCrc`: lenient and strict TLV parsing of EMVCo QR payloads, with sub-tags.
- `EmvcoQr` (immutable; `tag`, `value`, `with`, `encode`) and `Tag`.
- `PromptPay.anyId` (mobile / national ID / e-wallet) and `PromptPay.billPayment`, with `BigDecimal` amounts.
- `PromptPay.parseAnyId` / `parseBillPayment` to read generated or scanned payloads.
- Java example module, property and fuzz tests, Kover (90% gate) and API dump checks.

See `docs/SPEC_NOTES.md` for every deliberate difference from the TypeScript reference.
