# Contributing

Thanks for helping. This is a small, dependency-free library, so changes should stay small and well tested.

## Setup

You need a JDK 21 to run Gradle (any JDK 17+ works for compiling; the build downloads a JDK 17 toolchain
if it is missing). Then:

```bash
git clone https://github.com/sunJirapath/promptparse-jvm
cd promptparse-jvm
./gradlew check
```

`./gradlew check` is the single entry point, locally and in CI. It runs, in order of cost:

| Task | What it does |
|---|---|
| `spotlessCheck` | ktlint (Kotlin, Gradle KTS) and google-java-format (Java example) |
| `detekt` | static analysis, config in `config/detekt/detekt.yml` |
| `test` | JUnit 5 + jqwik property tests |
| `apiCheck` | fails if the public API differs from `core/api/core.api` |
| `koverVerify` | fails if line or branch coverage of `core` drops under 90% |
| `runExample` | runs the Java example, which asserts its own output |
| `verifyJarNotices` | checks that binary, sources and documentation JARs contain the complete `LICENSE` and `NOTICE` |
| `artifactSmokeTest` | compiles/runs a standalone Java consumer of the generated Maven JAR/POM, with transitive stdlib resolution |

Useful extras:

```bash
./gradlew spotlessApply          # fix formatting
./gradlew apiDump                # accept an intentional public API change
./gradlew :core:test -PtestJdk=11   # run the tests on another JDK (11, 17, 21, 25)
./gradlew :core:koverHtmlReport  # coverage report in core/build/reports/kover
```

Android: `./gradlew :core:animalsnifferMain` checks the main code against the Android API 21 signatures and is part of
`check`. The instrumented tests live in `android-tests/`, a separate Gradle build that needs an Android SDK and an
emulator or device, so they run in CI (`android.yml`) rather than in `check`:

```bash
./gradlew -p android-tests connectedDebugAndroidTest
```

Rules that CI enforces: formatting, zero detekt findings (no baseline), zero compiler warnings in main sources,
no public API change without an updated `core.api` (review that diff!), and no runtime dependency besides the
Kotlin stdlib. Tooling dependencies are build-time only.

`artifactSmokeTest` stages the real JAR and generated POM in `core/build/artifact-smoke-repository` and runs
the separate `artifact-tests` build. It reuses the Java example without any library project dependency.
`./gradlew :core:artifactSmokeTest -PtestJdk=11` runs it alone; no Maven CLI, signing credentials or publishing
access is needed. CRC regression vectors are fixed literals in `CrcFixtures.kt`; avoid runtime searches for them.

## Commit style

[Conventional Commits](https://www.conventionalcommits.org/): `feat:`, `fix:`, `docs:`, `test:`, `build:`, `ci:`,
`refactor:`, `style:`. Add `!` for a breaking public API change. Keep formatting-only changes in their own
commit. One logical change per commit and per pull request.

## Adding a test vector

Test vectors are payload strings with a known-good answer. Prefer a real or reference-derived one over a value
produced by this library (that would only test the code against itself).

1. Pick the right file: `ReferenceVectorsTest` (vectors from promptparse), `SlipVerifyTest` /
   `TrueMoneyAndBarcodeTest` (slip, TrueMoney, barcode), `ParserMatrixTest` (a hostile-input case that every
   parser should survive), `GeneratorCasesTest` (generator edge cases), `RegressionTest` (a bug fix).
2. Add the payload as a string literal plus the expected result. Note where it came from in a comment
   (reference file and line, bank sample, issue number). Never use real customer data, account numbers
   or secrets; use made-up numbers.
3. Prefer a table (`@ParameterizedTest` with `@CsvSource`/`@ValueSource`) over copy-pasted tests.
4. For a bug fix, add a test in `RegressionTest` whose name states the bug, and watch it fail before the fix.
5. If the expected result differs from the TypeScript references, say so in `docs/SPEC_NOTES.md` and in the
   README "Differences" table.

## Reporting a bug

Use the bug report template and include the QR payload string (anonymised), what you expected and what you got.
For security problems see [SECURITY.md](SECURITY.md) and do not open a public issue.

## Releasing (maintainers)

1. Set `VERSION_NAME` in `gradle.properties` to the release version (no `-SNAPSHOT`).
2. Add a `## [x.y.z] - date` section to `CHANGELOG.md` (the GitHub Release text is taken from it).
3. Merge to `main`, then push the tag `vX.Y.Z`. The `Release` workflow verifies the tag, runs `check`, waits for
   approval of the `release` environment, publishes to Maven Central and creates the GitHub Release.
4. Set `VERSION_NAME` to the next `-SNAPSHOT`.

## License

By contributing you agree that your contribution is licensed under the Apache License 2.0.
