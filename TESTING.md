# Verification

Run Gradle with JDK 21. Its toolchains run Minecraft 1.20.1 on Java 17,
1.21.1 on Java 21, and NeoForge 26.1.2 on Java 25.

## Choose the cheapest useful layer

| Command | Coverage | Cost |
| --- | --- | --- |
| `python tools/verify.py core` | Pure behavior, WAV decode/seek, cross-version contracts, verification harness regressions | No Minecraft configuration or game launch |
| `python tools/verify.py adapters` | Actual playback manager, control service, registry disk reload, packet codecs on all three versions; logical/render Sable transforms on 1.21.1 | Minecraft classes and supported NeoForge JUnit loader; no client/server process |
| `python tools/verify.py build --target forge-1.20.1` | Loader compilation, resource processing, remapping and packaging | Only the requested loader/version is configured |
| `python tools/verify.py live --target forge-1.20.1` | Actual dedicated server and client, network transfer, decoder and OpenAL playback, linked emitters, controls, redstone and peripheral adapter | One server/client launch |
| `python tools/verify.py full` | All layers, all five targets | Cheap checks first; local game sessions run sequentially |
| `python tools/verify.py gate` | Checks successful receipts match current commit and source contents | Does not rerun tests |

Repeat `--target` to select several loaders. Omitting it selects all five.
`python tools/verify.py gate --release` additionally requires clean sources.
Local edits are supported for normal verification. Editing sources while a layer
runs invalidates its result. Generated logs, Python caches and build outputs are ignored.

## Behavior rather than a second implementation

The shared integration suite invokes the production manager, control service and
registry on 1.20.1, 1.21.1 and 26.1.2. It substitutes world/player objects, packet
delivery and physics lookup at a small package-private boundary. It checks:

- listener entry, range hysteresis, exit/re-entry and exact packet counts;
- pause/resume, seek/restart/stop, non-finite seek rejection and occurrence identity;
- EOF validation and playlist advancement after quit, dimension, range and emitter churn;
- empty playlist snapshots, event signals, duration expiry and looping offsets;
- actual persistent registry reload, policy/playlist preservation and audience reset;
- real packet encode/decode, including empty snapshots and playback identity.

The Sable tests use real `Pose3d` math with translation and rotation, distinct
logical/render poses, moving bodies and unresolved-body silence. Core tests run
once; NeoForge no longer reruns a copied common suite. Narrow source contracts
remain where they check version/loader wiring, including request authorization.

## What a live pass proves

The opt-in fixture places two linked speaker blocks and saves a generated WAV on
the server. The client must download it, decode nonzero samples and report an
actual OpenAL playing source shared by both emitters. Each acknowledgement then
advances the server through pause, resume, seek, restart, stop and a real block
entity redstone pulse. Every phase requires separate client and server evidence.
The initial pre-world join guard is also required; it cannot certify playback.

Four targets also load CC:Tweaked and invoke the real peripheral adapter to check
track ownership, access policy and transport. This checks the adapter's Java API;
it does not execute a Lua program through a computer. NeoForge 26.1.2 does not
claim ComputerCraft compatibility. Local 1.21.1 profiles load the available sibling
Sable and vehicle mods described in README; CI does not assume sibling checkouts.
The transform tests do not simulate an entire moving Sable physics world.

OpenAL uses its null output driver during automated sessions, so decoded/queued
playback and resource lifecycle are checked without producing sound. Audible
quality, visual screens and full vehicle motion still need their own manual or
focused scenarios. Disk reload tests run in-process; they are not a fresh JVM
restart test.

## Evidence and CI

Receipts and combined logs are written to `build/verification/`. A failed run
invalidates previous success before starting. The gate rejects missing, failed,
wrong-layer or stale receipts; a run cannot pass using only a final marker.
The harness fails on game/loader crashes, missing phases, early exit and timeout,
and cleans up its owned processes. A checkout lock prevents overlapping fixtures.

Pull requests always run core/harness and version adapter checks. Only explicitly
recognized prose-only changes skip game launches; root Gradle changes, unknown
paths, manual runs and releases require the full matrix. CI runs the five targets
in parallel after cheap checks succeed and retains receipts and failure logs.
Both release publication jobs depend on the complete reusable verification gate.
