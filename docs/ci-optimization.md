# Layered CI verification

Run pure-core, harness and policy checks before expensive adapter and
native game jobs. Preserve the exact source-identity and receipt gates,
all mandatory loader targets, real client/server probes, and independent
consumer dependency checks. Optimizing scheduling must not silently
reduce coverage or treat skipped jobs as passes.

Benchmark changes against a pinned baseline with explicit runner,
cache, job-start and end times. Keep separate evidence for cold and
warm caches; do not generalize a single hosted run to all workloads.
Every release still requires exact-commit native artifacts and the
applicable runtime qualification receipts.

The recorded baseline, measured CI timings, coverage matrix and
rollout details are archived in the
[dated CI optimization record](../changelog/2026-10-ci-optimization.md).
