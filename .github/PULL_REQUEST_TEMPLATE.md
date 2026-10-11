## What and why

<!-- One or two sentences. Link the issue if there is one. -->

## Checklist

- [ ] `./gradlew check` passes locally (format, detekt, tests, apiCheck, coverage gate, Java example)
- [ ] New or changed behavior has tests; a bug fix has a test in `RegressionTest` that failed before the fix
- [ ] Test vectors come from a public source and contain no real personal or account data
- [ ] Public API change? `core/api/core.api` is updated and the change is listed under `[Unreleased]` in `CHANGELOG.md`
- [ ] Behavior differs from promptparse / thai-qr-payment? Noted in `docs/SPEC_NOTES.md` and the README table
- [ ] No new runtime dependency (the Kotlin stdlib is the only one)
- [ ] Commits follow Conventional Commits; formatting-only changes are in their own commit
