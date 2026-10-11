# Security policy

## Reporting a vulnerability

Please **do not open a public issue** for security problems.

Report privately through GitHub Security Advisories:
**Security → Advisories → [Report a vulnerability](https://github.com/sunJirapath/promptparse-jvm/security/advisories/new)**
on this repository. Include the payload or input that triggers it, the affected version and the impact you see.

You should get an acknowledgement within a few days. Fixes are developed in a private advisory fork, released,
and then disclosed with credit to the reporter unless you prefer otherwise.

## What counts

This library parses untrusted strings (scanned QR codes), so the interesting classes of bug are:

- a parser that throws, hangs or uses unbounded memory on crafted input (parsers are documented to never throw);
- accepting a payload with a wrong or forged CRC where the documented rules say it should be rejected;
- a generator that produces a payload different from the one requested (for example a wrong amount or recipient).

## Supported versions

Only the latest released version receives fixes. The library is pre-1.0.

## Scope notes

The library does not talk to banks, store data, or handle credentials. A valid CRC proves only that a payload was
not corrupted, not that it is authentic: always verify payments with your bank or payment provider.
