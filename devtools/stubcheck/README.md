# Offline stub check

`check.sh` compiles `src/main` and runs the unit tests **without** the Minecraft or Fabric jars, for
environments that cannot reach the Mojang/Fabric Maven repos. A real `./gradlew build` against Minecraft 26.2
is still the source of truth; this is a fast correctness gate.

## Why `skyblock-miner-1.0.0.jar` is in the repo root

`StubGen` reads that jar to discover every Minecraft/Fabric API member the released mod referenced, then
generates minimal stub classes for them so the current source compiles against the right shapes.

**Do not delete it.** It looks like a stale build artifact, but it is the API reference the offline check
depends on — removing it breaks `check.sh` (and the CI "Offline stub check" step). It is deliberately kept at
the repo root because `check.sh` resolves it there. If it is ever regenerated, it should be the jar whose
`MANIFEST.MF` records the 26.2 build the stubs are meant to match.

## Adding API members the 1.0.0 jar did not use

New code sometimes calls vanilla members the released jar never referenced, so `StubGen` does not know about
them. List those in `extra.txt` (and hierarchy/generics hints in `hints.txt`). Entries there are **unverified**
against 26.2 by the offline check — the CI Gradle build, which compiles against the real game, is what confirms
they exist. Keep `extra.txt` to genuine long-standing vanilla names.

## Files

- `check.sh` — the entry point (fetches a few Maven libs, generates stubs, compiles, runs JUnit).
- `StubGen.java` — reads the reference jar and emits stub sources.
- `patch.py` — applies `hints.txt` / `extra.txt` to the generated stubs.
- `handwritten/` — hand-written stubs for APIs StubGen cannot derive (Brigadier, Mixin annotations).
