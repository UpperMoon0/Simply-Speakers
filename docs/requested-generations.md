# Requested-generation ports

Requested-generation targets are independent Minecraft loader builds.
They do not reconfigure the existing root build. Each module's own Gradle
files, loader metadata, and source tree declare its supported game/loader
combination, toolchain, dependencies, and the native runtime tasks.
A generated source tree or successful compilation alone does not establish
real audio compatibility.

## Build and verification

Use the selected module's wrapper to build, remap, and package its native
loader jar. The scripts in `tools/` validate dependency artifacts and
source adapters against the module's declared contracts. Native loader
builds and bytecode checks are necessary but do not, by themselves,
establish actual client/server playback.

`python3 tools/requested_live_join.py --target <loader-target>` launches
the dedicated-server and separate-client acceptance attempt. Real
qualification requires the expected server, carrier-client, and
observer-client evidence for download, upload, playback, transport, and
cleanup. The harness never overrides Minecraft's native account or
multiplayer permission checks.

## Integrated-only qualification

An anonymous client may be denied multiplayer by Minecraft's authentication
and social-interaction permissions. In that case, a separate real
integrated-world probe can exercise the loader and audio behavior without
claiming successful dedicated two-client multiplayer. CI permits that
fallback only for specifically classified legacy permission refusals,
with matching exact-commit, passing integrated evidence. Any other
runtime failure, failed dependency download, compilation failure, crash,
missing marker, or mismatched receipt still fails verification.
**A green integrated-only fallback is not a two-client qualification.**
Dedicated coverage requires legitimately authenticated, eligible accounts.

## Dependencies and release artifacts

OpenUI and PacketWeave must match the selected loader's own metadata.
PacketWeave is a separate client/server mod and must not be shaded into
Simply Speakers. Publish the required compatible dependency builds
before publishing Speakers. Upgrade clients and server together whenever
a media packet or receipt protocol changes.

Exact dependency and toolchain versions, pinned source commits, fixture
numbers, and individual CI outcomes belong in versioned release and
acceptance records under [`changelog/`](../changelog/), not in this
evergreen workflow guide.
