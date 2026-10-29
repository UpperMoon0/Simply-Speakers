# Requested Minecraft generation ports

These targets are independent builds. They deliberately do not load the existing root Architectury/Loom build, so the old 1.20.1, 1.21.1 and 26.1.2 targets retain their toolchains and dependency contracts.

| Target | Game / loader | Runtime | Build |
| --- | --- | --- | --- |
| `forge-1.12.2` | Forge 14.23.5.2864, MCP stable 39 | Java 8 | Gradle 5.6.4 on Java 8; Java 17 Jabel compiler |
| `fabric-1.16.5` | Fabric 0.19.5 / Fabric API 0.42.0+1.16 / Architectury 1.32.68 | Java 8 | Gradle 9.5.1 on Java 25; Java 17 compiler |
| `forge-1.16.5` | Forge 36.2.42 / Architectury 1.32.68 | Java 8 | Gradle 8.8 on Java 17 |
| `fabric-26.2` | Fabric 0.19.5 / Fabric API 0.160.0+26.2 / Architectury 21.1.12 | Java 25 | Loom 1.17.9 / Gradle 9.5.1 |
| `neoforge-26.2` | NeoForge 26.2.0.93 / Architectury 21.1.12 | Java 25 | ModDev 2.0.148 / Gradle 9.5.1 |

There is no official Fabric target for 1.12.2. No unofficial Legacy Fabric distribution is included.

## Reproducible, unmerged dependencies

The complete commit/version contract is in [`requested-port-dependencies.json`](requested-port-dependencies.json). The requested-generation CI checks out those exact commits and verifies them before building matching native OpenUI and PacketWeave artifacts. It does not assume unpublished versions already exist in a package registry.

OpenUI MC 0.0.15 is required on clients. PacketWeave 0.1.5 is a separate required client/server mod. Neither API is shaded into Simply Speakers. The PacketWeave core source set is compile-only and must come from the pinned checkout. All targets now require the receipt-driven PacketWeave 0.1.5 API.

For a manual build, check out the pinned repositories as sibling `OpenUI-MC` and `PacketWeave` directories, build their matching target, then run the target wrapper, for example:

```sh
cd fabric-1.16.5
bash gradlew build --no-daemon --no-parallel --max-workers=2
```

Alternative checkout/artifact locations are explicit:

```sh
bash gradlew build \
  -PopenuiJar=/absolute/path/openui-mc-fabric-1.16.5-0.0.15.jar \
  -PpacketweaveJar=/absolute/path/packetweave-fabric-1.16.5-0.1.5.jar \
  -PpacketweaveSource=/absolute/path/PacketWeave/core/src/main/java
```

Forge 1.12.2 additionally needs `JAVA_HOME` pointing to Java 8 and `JAVA17_HOME` pointing to Java 17. The generators transform only this repository's owned mod sources. They clean stale generated files and preserve record value semantics while compiling with `--release 8`; modern syntax is lowered using Jabel rather than pretending Java 17 bytecode runs on Java 8.

## Behavior and native adaptations

- Full playback, positional/directional audio, upload/download, bounded packet codecs, playlists and saved playlists, player library/ownership, linked networks, portable endpoints, redstone controls, command authorization, and OpenUI screens reuse the maintained behavior.
- 1.16.5 uses real legacy block-entity tick, NBT and update-packet hooks. Attached physical blocks read native block state; inventory-backed portable routing tokens never look up a fake world block.
- Legacy controller coordination discards unloaded or replaced native tile instances without loading chunks, then requires a fresh signal observation before replaying pulse actions.
- Legacy packet dispatch preserves Architectury UUID channel IDs and validates direction before decoding; malformed wrong-side packets cannot initialize client handlers on a dedicated server.
- Audio library list codecs retain the native VarInt-length wire layout and reject negative, oversized or truncated counts before allocating.
- Legacy recipes use gold/quartz in place of copper/amethyst, which did not exist in those game generations. Optional Patchouli guidebook recipes are omitted in legacy builds rather than referencing absent item serializers. Core crafting and localization remain available without Patchouli.
- 26.2 accesses screens through the native GUI owner and delegates UI drawing to OpenUI's Blaze3D implementation. No raw OpenGL rendering calls are introduced.
- Optional ComputerCraft peripherals are not currently supplied for the requested lanes. The existing supported targets' optional integrations remain unchanged.

## API reference and evidence boundaries

API adaptations were checked against the private `UpperMoon0/MC-Modding-Src` canonical `1.12.2/`, `1.16.5/` and `26.2/` source trees at commit `01087a3908d6e14c43de4ecc39cf1d4f5025a8bb`, then compiled against official game artifacts and native loader APIs. The private reference and official game binaries are not copied into this repository or published with it. Source coverage audits alone are not runtime evidence.

The read-only `Requested generation ports` workflow builds actual native loader jars and tests, then checks bytecode versions, required behavior classes and absence of embedded dependency APIs. It has no release/publish/merge/deploy step. Existing release workflows are unchanged and do not silently publish these additional lanes.

Local direct compiler results validate API compatibility, not final loader remapping, graphics-device startup, or multiplayer playback. Those stronger claims require successful native builds and recorded client/server runs. See the PR's current verification results and limitations before treating any target as release-ready.

## Shared behavior and runtime acceptance

The modern source generator derives the complete 26.2 runtime from maintained
26.1.2 and shared sources; native GUI and screenshot-target ownership are adapted. Both modern
loaders consume that same generated output. The generator removes stale files,
rejects duplicate source identities, and is checked for deterministic regeneration.
Legacy targets likewise derive maintained behavior and share the non-loading
controller attachment predicate, literal-safe identifier mapping, lazy client-packet
handler lowering, and executable dedicated-side isolation fixture. All file-dialog
adapters share a client-executor callback boundary. Loader-specific world/audio
hooks remain adapters, and both Forge lanes use one exact-artifact resolver that
keeps ForgeGradle's synthetic mapped repository available.

`python3 tools/requested_live_join.py --target fabric-1.16.5` (or another listed
1.16.5/26.2 loader) runs one real dedicated server and two real clients. It requires
native packet delivery, upload/download, OpenUI settings interactions, positional
and portable OpenAL sources, deletion, and four sound-engine destruction/reload
cases. It records the actual game JVM separately from Gradle. Both 1.16.5 game
and test processes remain Java 8; Java 17 compiles `--release 8`, while Fabric's
Gradle driver runs on Java 25. A Java 17 game-runtime claim is not made.

The harness uses the null-output OpenAL backend. Success proves actual native
contexts/sources and lifecycle handling, not audible output on physical speakers.
Unsupported optional Patchouli/ComputerCraft integrations are explicitly excluded,
not fabricated as passing checks. These are development-loader acceptance runs;
production-jar checks are separate, and packaged-jar gameplay still needs its own
receipt. A configured harness is not itself evidence that a run has passed.

## Review regression receipts

The 2026-10-21 native Java 8 regression pass includes 435 passing Forge 1.12.2
tests and 417 passing Forge 1.16.5 tests, with no skips. These exercise actual
Forge-patched game classes, native split-key UUID NBT persistence, canonical
registry migration, controller unload/replacement, OpenAL readiness failure seams,
and strict dedicated-side S2C initialization. The file-dialog tests use a real
worker and prove callbacks cannot execute before the client executor drains.
These are compiler/integration-test receipts, not a substitute for a loaded game.

At exact PR head `7d01dea665d148e2edb4da2ce3831281c56ae5a3`, both Fabric and
NeoForge 26.2 completed native packaging and the full real Java 25 server/two-client
audio acceptance in [run 39616326814](https://github.com/UpperMoon0/Simply-Speakers/actions/runs/39616326814).
This includes large audio transfer/decode, native OpenAL playing/paused reload,
UI controls and directional-preview framebuffer evidence. The null OpenAL backend
still does not prove physical audible output or packaged-jar gameplay.

Both 1.16.5 clients loaded on Java 8 but reported native multiplayer eligibility
false after the loading overlay closed. The dedicated servers loaded successfully;
joining is explicitly blocked and unqualified. The harness does not change native
eligibility, authentication, service responses, or connection behavior.

The bounded Forge 1.12.2 development probe is described in
[`RUNTIME_ACCEPTANCE.md`](../forge-1.12.2/RUNTIME_ACCEPTANCE.md). Its local game
launch did not start: the genuine Gradle wrapper could not resolve an uncached
ForgeGradle build dependency offline, and the display server could not bind its
socket in the sandbox. The native compile and 435 tests passed, but no game
acceptance markers are claimed. The workflow enables this additional
client/server lane only for the specifically approved, same-repository PR 26
port branch targeting main, or a manual run on that same branch with explicit
`minecraft_eula_accepted=true`. A fail-closed event validator rejects other PRs,
forks, branches and event types before writing the isolated server's EULA file.
This does not grant standing consent to unrelated future servers or treat a
skipped runtime lane as passing.


The later native Forge 1.12 CI job at PR head
`2af04c684d418acfaf4c54f884ce969e81942dc2` passed packaging and launched the real
Java 8 dedicated server, including mod discovery and isolated-world readiness.
Its real client failed in LWJGL 2 display discovery before graphics-context
creation or join. This supersedes only the earlier **local** unrun status, not
the outstanding client/audio/UI acceptance requirements. The exact job and
same-display diagnostic boundary are recorded in `RUNTIME_ACCEPTANCE.md`.

## Separate Java 8 integrated-world acceptance

The separately named integrated lane runs only after a current, exact-target and
exact-commit receipt records native multiplayer refusal. Missing receipts, other
failures, stale receipts and successful dedicated runs cannot enter this route.
Dedicated/two-client qualification remains explicitly **blocked**, not passed, if
Minecraft refuses multiplayer eligibility. For anonymous CI, a precisely
classified 1.16.5 permission refusal can complete the native *attempt* step
without claiming multiplayer qualification **only when** a separate,
exact-commit passing integrated-world receipt satisfies the final CI coverage
gate. Missing evidence, runtime crashes, build errors, timeouts, unexpected
permission states, and failed integrated acceptance still fail CI. Manual
dedicated acceptance remains strict by default. This distinction does not
replace a genuine dedicated/two-client gameplay qualification for release.

A fresh isolated run directory opens the ordinary vanilla title screen and clicks
only the actual enabled, visible Singleplayer and Create New World controls via
native pointer dispatch. Disabled controls and unexpected screens fail closed.
No lower-level world-creation or connection shortcut, credentials, account/service
flag changes, or LAN publishing are used. The driver records actual integrated
server identity before the audio fixture can qualify.

Inside that permitted world, a single client uses production transport packets
for play, pause, resume, seek, restart and stop. Separately observed logical-server
state, decoded sample progress, native OpenAL source/context creation, playing and
paused resource reload, stale-epoch rejection, and real OpenUI pointer/keyboard,
settings roundtrip, tabs, resize and close/reopen are required. This is explicitly
one-client integrated development-runtime evidence, not dedicated multiplayer,
two-client networking, physical audibility, or production-jar boot evidence.

`tools/requested_integrated_acceptance.py` reuses the maintained process supervisor,
bounded preparation runner and isolated display ownership. Java 8 is selected for
the actual game, independently of the Gradle/compiler JVM. Its receipt and markers
are separate from the dedicated lane. Native compilation, six Java 8 driver/inertness
tests per loader, and harness regressions pass; the first actual integrated CI run
is still pending. The dependency lock now pins OpenUI's reviewed LWJGL 2 buffer
correction, which must also be exercised by the next Forge 1.12 UI/audio run.

## Complete release publication

The release workflow builds the five retained targets and all five requested
standalone targets before either publisher runs. Requested builds use the same
`tools/build_requested_release.py` path as native CI, including reviewed dependency
commit/version checks, all native build tests and production JAR verification.
Their published OpenUI 0.0.15 and PacketWeave 0.1.5 releases must contain every
requested native artifact and have the same source trees as the reviewed pins.
The retained targets keep their build path and also require PacketWeave 0.1.5.

A ten-target bundle gate rejects missing or unexpected JARs, wrong native IDs,
versions, Minecraft metadata, embedded dependency classes or incompatible bytecode.
It emits SHA256SUMS. Both CurseForge and GitHub consume only that verified bundle.
CurseForge relations follow each target's native metadata: Forge 1.12.2 does not
require Architectury, Fabric targets retain Fabric API, OpenUI and PacketWeave are
required, and Patchouli is optional only where declared by the native target.

Publication builds do not represent extra native gameplay acceptance. In
particular, the separately failed 1.16.5 dedicated-client eligibility checks are
not replaced by integrated-world results or waived by this release configuration.
Existing retained-target Layered verification remains a release prerequisite.
