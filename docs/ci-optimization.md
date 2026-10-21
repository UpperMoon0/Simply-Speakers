# Layered CI optimization evidence

## Scope and invariant coverage

This change removes only the unnecessary adapter-to-native scheduling edge in
Layered verification. Both branches still depend on successful core/harness and
OpenUI preparation; the final verification gate still requires successful policy,
core, adapter and required live jobs plus exact-commit/source receipts.

The baseline is main `359527ab18ca48069d687d2d7fb03c4f74426c07`.
Production code, existing test sources, `verify.py`, `live_join_test.py`,
`cc_lua_test.py`, target selection and release workflow are unchanged.
`tools/ci_coverage_manifest.json` explicitly captures each target's complete
marker set and layer assignment. The regression test compares this contract to
the real harness, rather than inferring coverage from a single final pass marker.
An independent validation extracted all four original harness/policy modules with
`git show 359527ab:tools/<module>` into a temporary directory and loaded those
baseline modules. All target/layer/marker lists, adapter tasks and live timeout
matched the manifest; all four module bytes also match the baseline exactly.

- Same five layered targets: Fabric/Forge 1.20.1, Fabric/NeoForge 1.21.1,
  NeoForge 26.1.2.
- Same three version-adapter suites, one shared pure-core suite and harness suite.
- Same five live sessions, each with dedicated server, carrier and observer.
- Same four real ComputerCraft Lua sessions; no CC claim on NeoForge 26.1.2.
- Same 14 carrier phases, nine independent observer phases, four audio-reload
  cases and all upload, guide, continuous-drag, controller and peripheral gates.
- Same crash handling, deadlines, isolated worlds, loopback-only fixture sessions,
  cleanup, process ownership, receipt invalidation and source identity checks.
- No sharding, test removal, timeout reduction, native-launch increase or release change.

## Measured baseline, not an after-result

[PR #25 Layered verification run 39493912116](https://github.com/UpperMoon0/Simply-Speakers/actions/runs/39493912116)
ran against `2b556e09bab518f11f96ecc064eab5c1c9b91915`, subsequently merged in the
baseline main. Timestamps below are UTC on 2026-10-20, from actual job logs.

| Job | Start | Finish | Elapsed | Cache state |
| --- | --- | --- | --- | --- |
| [Core](https://github.com/UpperMoon0/Simply-Speakers/actions/runs/39493912116/job/118873879590) | 20:40:04 | 20:40:42 | 38 s | Existing Gradle setup |
| [OpenUI preparation](https://github.com/UpperMoon0/Simply-Speakers/actions/runs/39493912116/job/118874249631) | 20:40:49 | 20:40:58 | 9 s | Exact Maven cache hit; build skipped |
| [Adapters](https://github.com/UpperMoon0/Simply-Speakers/actions/runs/39493912116/job/118874377259) | 20:41:04 | 20:45:14 | 4 min 10 s | Existing warm dependency path |
| [Forge 1.20.1 native job](https://github.com/UpperMoon0/Simply-Speakers/actions/runs/39493912116/job/118876502356) | 20:45:26 | 20:56:09 | 10 min 43 s | Exact Maven cache hit |

Within the Forge job, build began 20:45:36 and live began 20:49:51
(about 4 min 15 s). CC began 20:54:55, so the live layer took about 5 min 4 s.
The real Lua pass arrived 20:55:52 (about 57 s after CC began).

Removing the adapter dependency allows the existing 4 min 10 s adapter stage to
overlap native work. That is a scheduling opportunity, **not a measured 4 min 10 s
end-to-end saving**: runner availability, dependency downloads, cache state and
other jobs can change the actual critical path.

## Resource and cache tradeoffs

Maximum simultaneous native jobs remains five; maximum layered jobs after
preparation becomes six including adapters. A full run still launches 19 game
processes in total: 15 in the five live sessions and four CC servers. It does not
increase per-target process or heap budgets. Native work may proceed even when
adapters later fail, unlike the previous cheap-first serial edge. No reduction in
aggregate runner minutes or billed cost is claimed.

The CI dependency namespace is deliberately new and separate from release caches.
The first run must build OpenUI cold and create verified provenance; that run must
not be compared directly with the warm nine-second baseline preparation as a
regression or speedup. Later exact-key hits skip the build but always validate
source/version/toolchain/input identity, complete target artifacts, SHA-256s and
embedded JAR/POM versions. The actual selected JDK release file supplies Java
version, runtime build, vendor and architecture; a wrong vendor/major is rejected
and changed runtime builds generate distinct keys. Consumers install the producer
job's resolved exact Java version, preventing image-rollout drift between floating
Java 21 selections. A corrupt hit fails closed. A cache miss in a consumer
uses only this run's already-verified preparation artifact and revalidates it.

## Validation status

- 89 Python tests pass, including real Fabric dependency metadata/JDK checks and
  17 new cache/workflow regression tests.
- `actionlint` 1.7.11 accepts the layered workflow; workflow/action YAML parse.
- Cache fixture tests cover cold creation, repeated warm validation, missing
  manifest or target file, changed hashes, wrong source/version/toolchain/input
  metadata, extra files, invalid JARs and symlinks. These are synthetic dependency
  fixtures, **not a timed real OpenUI cold build**.
- Local Gradle core was attempted but stopped resolving Foojay resolver 0.9.0
  from the configured plugin repositories. No local Gradle/native pass is claimed.
- The first exact-head hosted run passed all layers, with actual cold-build and
  native timings recorded below. No full warm-producer run has been measured;
  current-head results must still be checked before claiming final readiness.

## First hosted after-run: all coverage passed, cold cache

[Run 39641776128](https://github.com/UpperMoon0/Simply-Speakers/actions/runs/39641776128)
at `e0c45de4a7e1ae4706020333eea25e1036400823` passed every job, including all five
real two-listener sessions, four real CC Lua sessions and the exact-source gate.
The separate OpenUI client-dependency workflow also passed. Its initial dependency
preparation was a verified **cold miss**; the six consumers restored that new
cache successfully and validated its provenance before running.

The intervals below use the first and last timestamped log entries for each job.
They include job setup/cleanup, not just test command runtime. Summed job elapsed
is not billed minutes, CPU utilization or peak memory, none of which were measured.

| Job | Baseline seconds (warm OpenUI) | After seconds (cold producer) |
| --- | ---: | ---: |
| Core/harness | 38.075 | 36.548 |
| Policy | 1.959 | 2.500 |
| OpenUI preparation | 8.913 | 396.944 |
| Adapters | 250.371 | 289.109 |
| NeoForge 26.1.2 native | 516.880 | 493.352 |
| Fabric 1.21.1 native | 568.750 | 601.386 |
| Forge 1.20.1 native | 643.039 | 648.539 |
| Fabric 1.20.1 native | 632.179 | 597.756 |
| NeoForge 1.21.1 native | 662.241 | 706.901 |
| Exact-source gate | 6.799 | 4.594 |
| Sum of job elapsed | 3329.206 | 3777.629 |
| First job start to final gate end | 992.746 | 1165.641 |

Observed total wall time was **19 min 25.641 s after**, versus **16 min 32.746 s
before**. This first cold run was therefore 2 min 52.895 s slower overall, not a
speedup. OpenUI preparation added 6 min 28.031 s compared with the old warm cache.
The actual cold Gradle build reported 6 min 19 s; manifest creation and verification
both succeeded, and only then were cache/artifact outputs published.

The independently measurable interval **after OpenUI preparation finished through
the final gate** decreased from 15 min 38.986 s to 12 min 5.703 s (3 min 33.283 s,
about 22.7%). In the after-run, adapters started 6.837 s after preparation and all
five native jobs started 6.476–8.119 s after preparation. Previously natives had
waited until adapters ended, then another 7.652–11.727 s. These are observed
scheduling gaps, not GitHub's authoritative queue metrics. Native job duration
variability and runner availability still prevent attributing the entire interval
difference to a single change or predicting an exact future warm-run saving.

This run predates the review fix that propagates the producer's resolved exact
JDK version to consumers. That follow-up keeps the same coverage and validation,
and its exact-head CI must be checked independently. The helper/workflow input key
changes, so its first producer run is another cold cache generation, not a warm
benchmark. Current-head verification and later measurements belong in the PR's
validation summary; this section preserves the original measured evidence.
