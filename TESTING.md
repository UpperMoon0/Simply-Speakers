# Verification

Use the repository's verification harness to choose the least expensive
test layer that proves the behavior under review. The Gradle build and
loader metadata define supported Minecraft targets and Java toolchains;
do not copy dependency or game version floors into this guide.

## Choose a verification layer

| Command | Coverage |
| --- | --- |
| `python tools/verify.py core` | Pure behavior, audio decoding, security and harness regression tests without a game launch |
| `python tools/verify.py adapters` | Production adapter logic, packet codecs, state persistence and loader-facing tests |
| `python tools/verify.py build --target <loader-target>` | Compile, remap and package the selected native loader |
| `python tools/verify.py live --target <loader-target>` | Dedicated server and separate clients exercising real packet, decoder and OpenAL paths |
| `python tools/verify.py cc --target <loader-target>` | Real ComputerCraft Lua peripherals where supported |
| `python tools/verify.py full` | Full supported matrix, running cheaper layers first |
| `python tools/verify.py gate` | Verify exact-source passing receipts; does not rerun tests |

Run `python tools/verify.py --help` to discover the current target names
and flags. For generated or standalone ports, use the relevant module wrapper
and the requested-generation acceptance harness described in
[requested-generation methodology](docs/requested-generations.md).

## Evidence standards

A core or adapter pass is not evidence of live client/server playback.
Live acceptance must use actual game processes, decoded audio and OpenAL
resource checks. A two-client qualification requires independently
verified server, carrier and observer activity; server-only markers or
one-client integrated worlds are insufficient. The automated null audio
driver verifies buffer lifecycle, not subjective sound quality.

Native Minecraft account restrictions must not be bypassed. If a
legacy loader refuses anonymous multiplayer, record that refusal and
run the separately qualified integrated-world fallback only when its
narrow eligibility criteria are satisfied. Label its result
**integrated-only** and leave dedicated two-client qualification
unproven. Any unrelated crash, missing receipt, failed dependency
download or test error must fail the gate.

Run UI and rendering probes in disposable worlds with the supported
module-specific flags; inspect screenshots visually in addition to
checking geometry assertions. For upload and download changes, test
wrong-connection receipts, cancellation during final acknowledgement,
timeout and disconnect cleanup, bounded admission, parallel cache
publication, active-decoder leases and slow receivers. For releases,
verify all native artifacts, loader metadata and dependency contracts
against the exact commit being published.

## Historical acceptance evidence

The exact loader versions, toolchain values, fixture counts, commands,
logs, and qualification limits for the recorded acceptance runs are
preserved in the [versioned verification record](changelog/2026-10-verification-record.md). Keep future release-specific
results in their own dated or versioned records, not this guide.
