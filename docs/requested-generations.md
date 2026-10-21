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

OpenUI MC 0.0.15 is required on clients. PacketWeave 0.1.4 is a separate required client/server mod. Neither API is shaded into Simply Speakers. The PacketWeave core source set is compile-only and must come from the pinned checkout. Existing targets keep their existing dependency pins.

For a manual build, check out the pinned repositories as sibling `OpenUI-MC` and `PacketWeave` directories, build their matching target, then run the target wrapper, for example:

```sh
cd fabric-1.16.5
bash gradlew build --no-daemon --no-parallel --max-workers=2
```

Alternative checkout/artifact locations are explicit:

```sh
bash gradlew build \
  -PopenuiJar=/absolute/path/openui-mc-fabric-1.16.5-0.0.15.jar \
  -PpacketweaveJar=/absolute/path/packetweave-fabric-1.16.5-0.1.4.jar \
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
