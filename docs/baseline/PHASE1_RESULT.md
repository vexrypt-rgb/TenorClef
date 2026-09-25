# Phase 1 Result — Make the existing JUnit tests runnable (Class D)

Date: 2026-09-24. Approved change from [`BASELINE.md`](BASELINE.md) §20. Not committed.

## Change

`build.gradle` only (applies to every version module):

```groovy
// dependencies { … }
testImplementation(platform('org.junit:junit-bom:5.10.2'))
testImplementation 'org.junit.jupiter:junit-jupiter'
testRuntimeOnly 'org.junit.platform:junit-platform-launcher'

test {
    useJUnitPlatform()
}
```

No source, test, CI, or other build-logic edits. CRLF line endings preserved.

## How it was verified

The working tree cannot run tests yet: every non-1.16.1 compile fails on the untracked
`mixins/AutoWorldCreateMixin.java` (BASELINE D2). To avoid touching that file, verification ran on a
**clean `git archive` export of HEAD `873e4d79`** in the session scratchpad, with the identical edit
applied. JDK 21.0.12, Gradle 9.4.1.

| Step | Command | Exit | Result |
|---|---|---|---|
| Before (HEAD `build.gradle`) | `:1.21.1:compileTestJava` | 1 | `package org.junit.jupiter.api does not exist` (first error `AgentJsonTest.java:3`) |
| After | `:1.21.1:test --tests adris.altoclef.tasksystem.RecoveryManagerTest` | 0 | **9/9 pass** |
| After | `:1.21.1:test --continue` | 1 | **116 tests, 20 suites: 115 pass, 1 fail, 0 errors, 0 skipped** |
| Working tree (edited) | `:1.16.1:dependencies --configuration testRuntimeClasspath` | 0 | resolves `junit-jupiter 5.10.2`, `junit-platform-launcher 1.10.2` (before: 0 junit artifacts) |
| Working tree (edited) | `:1.16.1:compileJava` | 0 | `UP-TO-DATE` — main compile inputs unchanged |

`TungstenJarPresenceTest` produces no suite (no `@Test` methods).

## Deviation from the planned verification

BASELINE §21 named `:1.16.1:test`. That is **not achievable at HEAD**, because of evidence found during
Phase 1: `:1.16.1:compileTestJava` depends on `compileJava` of every preprocess ancestor
(`1.16.5 → 1.17.1 → 1.18 → 1.18.2 → 1.19.4 → …`), and at clean HEAD `:1.19.4:compileJava` fails on committed
`tasks/speedrun/testrun2/dj/DjPlayer.java:138` (`SoundEvent` constructor). Verification used `:1.21.1:test`
instead: the main project and a required CI gate. `RecoveryManager` is plain Java in the shared `src/`, so
the code under test is identical for 1.16.1.

## New evidence (recorded, not fixed)

1. **Failing live-path test.** `TaskPropagationTest.parentAbsorbsChildFailure` expects `FAILURE`, gets
   `RETRY`. A child calls `fail(NO_PATH, …, recoverable=false)`; `Task.absorbChildOutcome`
   (`Task.java:272–279`) re-applies `RecoveryManager` from retry 0 → `ALTERNATE_PATH` → `RETRY`, overriding
   the child's non-recoverable flag. `absorbChildOutcome` runs on every live `Task.tick()` (`Task.java:68`).
   Test from Phase 4 (`a66f4adf`); absorb logic from Phase 6 (`aed2573b`). Whether the test or the code is
   wrong is **Unknown** — owner decision.
2. **Per-module compile at clean HEAD** (Verified; resolves BASELINE §2 "Unknown"):
   pass — `1.16.1, 1.16.5, 1.17.1, 1.18, 1.18.2, 1.21, 1.21.1`;
   fail — `1.19.4, 1.20.1, 1.20.2, 1.20.4, 1.20.5, 1.20.6` (not CI-gated; first error `DjPlayer.java:138`).
   `1.21.11` not run at HEAD.
3. `docs/BENCHMARKS.md:16` ("run offline unit tests") becomes accurate for `:1.21.1:test` with this change.

## What did not change

Runtime behavior (build-config only; main `compileJava` UP-TO-DATE). CI still runs no tests.
