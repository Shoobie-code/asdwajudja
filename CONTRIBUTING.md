# Contributing

## Setup

- JDK 25 (CI uses Temurin 25) and Gradle.
- Clone the repo and run `gradle build` for the full build, or `bash devtools/stubcheck/check.sh` for an offline compile and test run (no Fabric or Mojang Maven access needed).

## Layout

- `src/main/java/com/skyblockminer/`: the mod. Feature packages: `macro` (macros and shared engine), `pathing`, `rotation`, `market`, `helpers`, `gui`, `config`, `script` (userscripts), `update` (self-update).
- `src/test/java/...`: JUnit 5 tests that mirror the main packages.
- `devtools/stubcheck/`: the offline build used when Maven is unreachable.

## Before you open a PR

1. Run `bash devtools/stubcheck/check.sh` and make sure all tests pass.
2. Add or update tests for any logic you change.
3. Settings are declared in `config/MacroSettings.java`. Clamp new numeric values in `MinerConfig.sanitize()`.
4. Keep changes to one feature per PR, and describe what you tested in the PR body.

## Commits

Short imperative subject line, then a body explaining why when it isn't obvious.
