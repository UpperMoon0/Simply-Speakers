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
| `python tools/verify.py cc --target forge-1.20.1` | Real CC computers execute Lua: discovery, calls, events, permissions, catalog/queue changes, detach and reboot | One isolated dedicated server; no client |
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
- real packet encode/decode, including empty snapshots and playback identity;
- named playlist management, inactive editing, explicit playback switching, protected deletion and limits;
- legacy single-playlist JSON migration, named catalog disk reload and maximum catalog/queue packet size.

The Sable tests use real `Pose3d` math with translation and rotation, distinct
logical/render poses, moving bodies and unresolved-body silence. Core tests run
once; NeoForge no longer reruns a copied common suite. Narrow source contracts
remain where they check version/loader wiring, including request authorization.

## What a live pass proves

The opt-in fixture places two linked speaker blocks and saves a generated WAV on
the server. The client must download it, decode nonzero samples and report an
actual OpenAL playing source shared by both emitters. Each acknowledgement then
advances the server through pause, resume, seek, restart, stop and a real block
entity Redstone Controller pulse. Every phase requires separate client and server evidence.
The initial pre-world join guard is also required; it cannot certify playback.

Four targets also load CC:Tweaked and invoke the real peripheral adapter to check
track ownership, access policy and transport. This checks the adapter's Java API;
the separate `cc` layer executes the packaged `cc_acceptance.lua` inside placed
CC advanced computers using the actual Cobalt runtime. It checks Lua argument
errors, ownership and stream policy, all protected access modes, getters without
unrelated events, transport events, numeric bounds, queues, saved playlist copies,
linked peripherals, detach and computer reboot. `SIMPLYSPEAKERS_CC_LUA_PASS` is
required; a Java adapter pass cannot substitute for it. NeoForge 26.1.2 does not
claim ComputerCraft compatibility. Local 1.21.1 profiles load the available sibling
Sable and vehicle mods described in README; CI does not assume sibling checkouts.
The transform tests do not simulate an entire moving Sable physics world.

OpenAL uses its null output driver during automated sessions, so decoded/queued
playback and resource lifecycle are checked without producing sound. Audible
quality, visual screens and full vehicle motion still need their own manual or
focused scenarios. Disk reload tests run in-process; they are not a fresh JVM
restart test.

## Player screen verification

Run `./gradlew :forge-1.20.1:runClient -PuiPreview=true` for the opt-in native
screen audit. It opens the real Speaker, Proxy and Redstone Controller screens
with sample data and no world. Four UI sizes (320×240, 427×240, 640×360,
854×480) and both themes cover 584 cases per Minecraft runtime. Cases include
all four player views, empty/search/long-name states, paused and stream playback,
track menus, delete/clear/timestamp dialogs, hover tooltips, scrolled Settings,
proxies and every controller action.

The probe checks the actual selected view, control bounds, centered transport,
centered timeline endpoints, track-row baselines, truncated title widths, square
icon targets and nonempty tooltips. It drags the timeline and requires the timestamp
dialog to open, reject invalid input, and close after a valid submission. It also
checks that controller help text has enough height for its wrapped lines. Screenshots go in the selected loader's `run/screenshots/` folder;
`SIMPLYSPEAKERS_UI_AUDIT_PASS cases=584` confirms completion. Review those images
visually as well: these assertions alone do not establish visual quality.

Run the same opt-in command against `fabric-1.20.1`, `fabric-1.21.1` and
`neoforge-26.1.2` to cover all three Minecraft render/input adapters. It restores
window/scale settings and exits when finished. Normal launches do not enable the audit.
`-PuiAuditGroup=controller` runs 320 focused cases for controller/proxy forms,
timestamp submission, scrolling and transport hover contrast.
`-PuiAuditGroup=playlists` checks 128 playlist/queue cases, including named-list
selection, management dialogs, long names, a full catalog and destination picking.
`-PuiAuditGroup=playlist_picker` checks 40 focused selector/destination-picker cases.
`-PuiAuditGroup=settings_layout` checks 48 Library/Settings/scrolled Settings and jump-dialog cases.
`-PuiAuditGroup=settings_help` checks 40 speaker-setting hover explanations, including the preview legend and scrolled controls.
`-PuiAuditGroup=interaction` checks 40 timestamp/focus/hover cases;
`-PuiAuditGroup=forms_scrolled` checks the bottom of each controller form in 72 cases. See
[the visual audit report](docs/ui_audit.md) for the recorded scope and findings.

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


## Crafting resource checks

`python -m unittest tools.test_crafting_resources` verifies that every finished
mod recipe consumes the Audio Circuit, that 1.21.1 and 26.1.2 recipe formats
agree on materials/layout/output, that the circuit is craftable from vanilla
materials, and that both item model formats point to its transparent 16x16 sprite.
Build 1.20.1 targets as well to exercise the legacy recipe transformation.

## Controller cooperation and migration

`RedstoneControllerIntegrationTest` runs on all three Minecraft runtimes and drives configured block entities through duplicate/conflicting pulses, OR playback inputs, maximum volume arbitration, permission revocation, removal, retargeting, proxy isolation, missing chunks and proxy reloads. Controller NBT round trips verify held pulse inputs do not retrigger and analog jobs restore their input. Registry serialization verifies temporary volume never replaces saved volume. Legacy speaker modes are exercised as inert inputs, with unpowered network playback still emitting.

The native UI matrix has 584 cases per runtime. Retired native redstone-mode fixtures have been replaced by 72 network mode-picker interactions, plus 16 proxy mode-picker interactions, covering every job in both themes and four window-size requests. Each picker opens through the actual UI input path, checks every choice fits without scrolling, checks hover help, selects it and verifies the form mode. The live dedicated-server/client fixture starts playback through an actual powered Redstone Controller and checks decoded playback independently on client and server.

The `redstone_rework` native group combines every controller form and mode picker with proxy forms, player transport/tooltips, Settings layout/help and timestamp dialogs. The physical live fixture requires `SIMPLYSPEAKERS_CONTROLLER_COOPERATION_PASS`, covering duplicate toggles, Stop priority, OR playback, maximum volume, saved-volume restoration and analog track selection.

Fresh-placement integration coverage assigns a Speaker ID and links a controller before the first block tick. Audio resource coverage requires transport stop and decoder completion to schedule only one cleanup, preventing deletion of reused OpenAL source IDs. Live restart remains gated on decoded, playing client audio.

The `ux_core` native UI audit group covers 176 playlist selection/search, queue order, settings grouping, preview help, and restart-help cases across four viewport sizes and both themes. The live join probe measures every compiled guide page against Patchouli bounds, checks world-preview emission, closes/reopens all nine controller actions through real server packets, and verifies seven speaker settings after authoritative synchronization.


Continuous slider regression: `ServerPlaybackIntegrationTest.continuousSettingsUpdatesPreserveSubscriptionsTransportAndSession` applies 80 gain/controller-gain/cone/range changes and rejects stop packets, session changes, timeline resets or subscription loss. The real client probe uses native screen click/drag/release entry points, checks the drag lifecycle and 24 signal values before release, and requires the identical live decoder resource throughout volume/range/direction updates. `SIMPLYSPEAKERS_CONTINUOUS_DRAG_PASS` is required by the live harness.

## Portable speaker coverage

The shared adapter tests execute actual portable item metadata, inventory reconciliation,
server endpoints, authorization, packet handlers and playback services on all three
Minecraft runtimes. They cover stable identity across slots and stack replacement,
creative-copy isolation, malformed stacks, separate offhand equipment, holder transfers, inventory removal,
logout/death/spectator cleanup, dimension changes, protected links, linked-block
continuity, new listeners and range exits. Three distinct mocked players exercise
carrier/listener separation; these are production-code adapter tests, not a multiplayer
runtime session. Client adapter tests cover tracked and untracked carrier positions,
teleports, stale snapshots, dimension cleanup, dead carriers and transport cleanup.
A real registry save/reload restores settings, playlists and ownership paused while
retaining item metadata. The real versioned codecs are checked for mobile identities, fractional poses,
portable screen routing and malformed input.

Every live target additionally requires seven portable phases: start, movement,
pause, resume, stop, restart and inventory removal. A real registered item in a
non-hotbar inventory slot drives a downloaded, decoded OpenAL stream. The probe
reads the actual OpenAL source position after the server teleports its carrier,
requires the same decoder/source across that movement, and requires source deletion
and pose/membership cleanup on pause, stop and removal. Both server and client phase
markers are mandatory. This automated live fixture has one real player, who is both
carrier and listener; it does not claim a two-player runtime test. OpenAL still uses
the null output driver, so listening quality and visual portable-screen review need
manual or focused verification.
