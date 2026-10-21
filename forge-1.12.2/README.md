# Forge 1.12.2 native port

This standalone Gradle 5.6.4 / ForgeGradle 3 build targets Forge 14.23.5.2864 and
stable-39 MCP names. Run Gradle with Java 8 and set `JAVA17_HOME` to a JDK 17.
Jabel compiles all owned sources using `--release 8`; newer JVM APIs are rejected.

Pass exact OpenUI and PacketWeave branch artifacts while their port PRs remain
unmerged. No dependency release or Maven publication is assumed:

```sh
JAVA_HOME=/path/to/jdk8 JAVA17_HOME=/path/to/jdk17 ./gradlew build \
  -PopenuiJar=/path/to/openui-mc-forge-1.12.2.jar \
  -PpacketweaveJar=/path/to/packetweave-forge-1.12.2.jar
```

Shared server/audio/security/playlist/portable/UI implementations are generated
from the maintained common sources. Native Forge adapters supply tile persistence,
block/item registration, checked packet dispatch, command registration, lifecycle,
OpenUI screens and LWJGL 2 buffer calls. No Minecraft implementation is vendored.

The audio core transformer suspends playback before vanilla destroys its OpenAL
context and resumes only after the asynchronous sound loader marks it ready.
Forge's early `SoundLoadEvent` is intentionally not used as readiness evidence.
The gate starts suspended and also requires an actually created OpenAL context
matching the current context, so Paulscode's silent fallback cannot resume audio.
Controller unload retires transient inputs without invalidating reusable tiles;
the shared attachment guard checks chunk presence before current tile identity.

Crafting uses gold and quartz in place of copper and amethyst, which do not exist
in this Minecraft version. Planks use the native wildcard-metadata ingredient.
The optional Patchouli recipe and ComputerCraft peripheral are not enabled on this
generation; all speaker functionality and the `/simplyspeakers` (`/ss`) command
tree remain available without them.

## Verification

`python3 -m unittest discover -s ../tools -p 'test_forge_1122_sources.py' -v`
checks packet/security preservation, tile lifecycle, native recipes and escape-key
save behavior. `./gradlew build` also compiles and runs the complete shared test
suite plus native buffer, wire, dedicated-side, coordinate and audio-transformer
regressions on Java 8. The transformer test inspects the actual Minecraft class
from the dependency classpath, never a copied game implementation.

Development verification passed all 435 tests on Java 8 and compiled the full
native source set against official Forge-patched Minecraft 1.12.2, stable-39
mappings and the official Forge access transforms, the pinned PacketWeave API and
OpenUI's native 1.12 adapter. A separate Gradle 5.6.4 offline smoke check verified
the exact local jars resolve as external module dependencies. These checks are
not an in-game client smoke test; full packaging remains subject to native CI.

The opt-in native game harness, exact evidence requirements and current blocked
runtime receipt are documented in [RUNTIME_ACCEPTANCE.md](RUNTIME_ACCEPTANCE.md).
It requires prior Minecraft EULA acceptance. The requested-generation workflow
provides a separate explicit acceptance input for manually dispatched runs;
ordinary pull-request builds do not start this additional 1.12 client/server lane.
