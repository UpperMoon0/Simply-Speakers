# Native 1.12.2 runtime acceptance

The opt-in probes are inert unless `simplyspeakers.legacyRuntimeAcceptance=true`.
They require an actual Java 8 game process and use the production networking,
server controls, WAV decoder, OpenUI screen, Paulscode sound manager and OpenAL
resources. No API stubs, game source copies or fake success packets are used.

## Development launch

Supply the exact source-pinned dependency artifacts, Java 8 in `JAVA_HOME`,
Java 17 in `JAVA17_HOME` for compilation, and a working X display or `xvfb-run`:

```sh
python3 tools/requested_1122_runtime.py \
  --openui-jar /path/to/openui-mc-forge-1.12.2-0.0.15.jar \
  --packetweave-jar /path/to/packetweave-forge-1.12.2-0.1.4.jar
```

The harness requires an already accepted `eula=true` in
`forge-1.12.2/run/legacy-runtime/server/eula.txt`; it never accepts the Minecraft
EULA automatically. The server binds only `127.0.0.1:25576`, allows one client,
and creates a uniquely named fixture world inside that isolated run directory.

It serially prepares both native task graphs before starting the server and
client. Each game has a 768 MiB heap; each supervising Gradle process has 256 MiB.
It reuses the maintained output pump, failure detection and process cleanup.
This acceptance harness requires POSIX process sessions (the Linux cloud/CI lane).
Every prerequisite invocation owns a bounded process session with unconditional
cleanup, including timed-out or already-exited supervisors and TERM-resistant
descendants.
Logs and the source commit/dirty-state and dependency SHA256 receipt are saved
under `forge-1.12.2/run/legacy-runtime/receipts`.

Required evidence includes:

- Dedicated server discovery, real client join and ordered, sender-checked
  acknowledgements of authoritative play, pause, resume and stop.
- A real decoded OpenAL source with sample-offset progress, not merely a logical
  playing flag. Both paused and playing vanilla sound-manager reloads must
  recreate the context and retire the old audio epoch and worker.
- Explicit vanilla sound unload must suspend playback and destroy the context;
  reload must not resurrect stopped audio.
- Native speaker-screen pointer/key dispatch, text focus and caret editing,
  Tab navigation, settings navigation, clipboard roundtrip, resize disposal
  and Escape-close ownership cleanup.
- Actual disconnect and removal of server subscriptions/client memberships.

Clipboard roundtrip is not an OS paste shortcut test. The null audio backend
proves context/source lifecycle, not physical audible output. These are
**development classes-directory launches**, not production/reobfuscated jar
launches. Portable playback, uploads, multiple listeners and physical output
are outside this bounded 1.12 probe.

## Verification receipt, 2026-10-21

- Full native source compilation passed: 172 main and 83 test source files,
  genuine Forge-patched/access-transformed MCP39 dependencies, Java 8 output.
- All **435 Java 8 tests** passed, zero skipped. This includes the two new
  disabled-probe guards; these are not real-client runtime passes.
- All **10 harness contract tests** passed, including missing-marker/failure
  rejection, explicit prior EULA requirement, loopback fixture, serial
  preparation, and real Python parent/child timeout and exited-parent cleanup.
  Both process regressions prove a TERM-resistant descendant stops executing.
- Genuine CI dependency archives were retrieved and verified:
  OpenUI commit `cafb8c198f74f16a621cb552ccf3e3b117e058d5`, artifact
  `12342887928`; PacketWeave commit
  `b9af8be1c8dae497a852342a0d323f811b80b54a`, artifact `12343495343`.
- Actual game execution **did not start**. One bounded genuine Gradle 5.6.4
  offline preflight stopped because `net.minecraftforge.gradle:ForgeGradle:3.0.197`
  was absent from the local module cache. Separately, the genuine Debian Xvfb
  binary could not open local/unix listening sockets; the one escalation
  attempt failed before execution during sandbox setup. No alternative game
  bootstrap or network/security setting changes were made.

Consequently server discovery, join, UI, OpenAL playback, reload and disconnect
markers remain **unrun**, and no runtime pass or production-artifact pass is
claimed. The prepared harness is ready for a permitted native CI/runtime with
its real dependency cache and display available.
