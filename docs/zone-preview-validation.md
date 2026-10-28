# Shader zone preview — validation record

Issue: https://github.com/UpperMoon0/Simply-Speakers/issues/24

This is an implementation and acceptance work log for a draft PR. It is not a
performance improvement claim. The original particle preview is the comparison
baseline at `fc75a98`.

## Design

Speaker-local immutable ribbon geometry has 640 segments for omnidirectional
speakers and 645 for directional speakers, independent of range. The custom
vertex shader extrudes segments in screen space. Cyan denotes the three maximum
range hoops; gain-colored inner rings use the playback attenuation function;
white lines denote the forward arrow and cone edges. Visible geometry is solid;
terrain-hidden geometry is faint and dashed. Neither pass writes depth.

The GPU buffer is uploaded once per preview; camera-relative translation is
computed in double precision before conversion to shader uniforms. Motion updates
translation/rotation, not the buffer. The existing 30-second lifetime and HUD are
retained. The 1.21.1 Sable resolver remains the source of transformed speaker poses.

## Acceptance status

- Baseline core Gradle suite: passed locally with JDK 21.
- New mesh tests: passed with the existing core suite.
- 1.20.1/1.21.1 adapters and derived legacy/26.x adapters: implemented; native
  build and runtime checks are in progress.
- Requested-generation source suite: 23 tests passed (`python -X utf8`).
- Native 26.1.2 build and GPU cleanup/fallback tests passed. All three version
  adapter suites passed; the additional Sable portable interpolation test passed.
- Fabric 1.20.1 and 1.21.1, Forge 1.20.1 and NeoForge 26.1.2 built locally.
- Actual hidden Minecraft captures and paired particle/shader timings: collected
  on Fabric 1.20.1. See the measured results below.
- Shaderpack and mod compatibility: not verified in a game runtime.

No synthetic image or source-only timing is presented as Minecraft acceptance.

## Hidden-window graphics fixture

`tools/zone_shader_smoke.py` compiles the actual OpenGL renderer and creates an
invisible GLFW window. On this machine it reported NVIDIA GeForce GTX 1050 Ti,
OpenGL 3.3.0, NVIDIA 581.15. It passed program compilation, actual draw output,
restoration of non-default GL state, resource deletion, reload, and forced fallback.

Eight actual GPU captures were inspected: small outside/inside, large
outside/inside, two moving-origin/direction poses, and controlled depth occlusion.
These are **shader-fixture captures, not Minecraft screenshots**. The depth case
uses a controlled scissored depth buffer, not Minecraft terrain. The portable cases
exercise uniform changes, not Minecraft's holder tracking or Sable integration.
They establish shader output and state/resource behavior, not game acceptance or
particle-baseline performance.

The short-far-plane capture is pixel-identical to the long-far-plane fixture.
This checks that diagnostic range boundaries remain readable beyond the terrain
far plane while retaining depth tests and without requesting chunks.

![Small outside shader fixture](validation/zone-preview/small-outside.png)
![Small inside shader fixture](validation/zone-preview/small-inside.png)
![Large outside shader fixture](validation/zone-preview/large-outside.png)
![Large inside shader fixture](validation/zone-preview/large-inside.png)
![Changed portable pose fixture](validation/zone-preview/portable-pose-b.png)
![Controlled depth fixture](validation/zone-preview/depth-occlusion-fixture.png)

## Actual Minecraft comparison

The final paired run used **actual Minecraft 1.20.1/Fabric**, Java 17, an invisible
960x540 window, eight-chunk view distance, VSync off, the same superflat server
seed, fixed camera poses/settings, 100 warm-up ticks and 300 samples per scene.
NVIDIA GTX 1050 Ti, driver 581.15, OpenGL 3.2.0. The comparison worktree retains
the original `fc75a98` preview and geometry byte-for-byte after line-ending
normalization; only identical test instrumentation was added.

CPU measurements cover `GameRenderer.render` submission including HUD; GPU
measurements use `GL_TIME_ELAPSED` over the same renderer scope. Frame intervals
include the surrounding game frame. These are whole-scene measurements, not
preview-exclusive GPU timings. The first zero frame interval is discarded.

| Scene | CPU particle / shader (ms) | CPU change | GPU particle / shader (ms) | Frame particle / shader (ms) |
| --- | --- | --- | --- | --- |
| small outside | 5.936 / 5.035 | -15.2% | 2.293 / 2.235 | 8.717 / 7.545 |
| small inside | 5.697 / 4.871 | -14.5% | 2.041 / 2.300 | 8.381 / 7.347 |
| large outside | 5.622 / 4.357 | -22.5% | 2.032 / 2.089 | 7.707 / 6.329 |
| large inside | 5.675 / 4.729 | -16.7% | 2.133 / 2.074 | 8.251 / 7.146 |
| moving portable | 5.808 / 4.735 | -18.5% | 2.187 / 2.634 | 9.779 / 8.641 |
| terrain | 5.546 / 5.262 | -5.1% | 2.245 / 2.633 | 9.348 / 9.386 |

Values are medians. [Raw samples, p95 summaries, source hashes and settings](validation/zone-preview/game/metadata.json)
and [the timing summary](validation/zone-preview/game/summary.json) are retained.
Median CPU submission improved in this run; **GPU time and p95 did not improve
uniformly**. Terrain median frame time was effectively unchanged/slightly worse.
This is one paired run on a working machine with uncontrolled host load, with
shader first and particles second. It does not certify a general GPU/p95 speedup
or performance on other versions/backends. Performance acceptance remains partial.

The camera is detached from the listener to inspect the zone from inside and
outside without loading distant chunks. The HUD correctly continues to report the
actual player's distance, so it can say "inside" while the camera is outside.
The moving case exercises the actual portable-token/holder preview path. Large
outside boundaries extend beyond the terrain far plane; the original particles
can be clipped there, so the visible work differs even with identical settings.

![Actual shader small outside camera](validation/zone-preview/game/shader-small-outside.png)
![Actual original particle small outside camera](validation/zone-preview/game/particles-small-outside.png)
![Actual shader large outside camera](validation/zone-preview/game/shader-large-outside.png)
![Actual shader inside large zone](validation/zone-preview/game/shader-large-inside.png)
![Actual moving portable preview](validation/zone-preview/game/shader-portable.png)
![Actual terrain occlusion](validation/zone-preview/game/shader-terrain.png)
![Actual HUD-only fallback](validation/zone-preview/game/shader-fallback.png)

All thirteen game captures were inspected. Camera head rotation and far-plane
visibility were corrected after the initial inspection; the retained comparison
uses the corrected fixture. Both client processes completed their scene markers
and screenshot/sample checks, then the isolated server stopped and saved cleanly.

Reproduce after building the pinned dependencies:

```text
python tools/prepare_zone_baseline.py --output ../particle-baseline
python tools/zone_runtime_test.py --baseline ../particle-baseline --java-home <JDK21>
```

The tool uses private development directories and localhost port 25586. It never
controls another application or captures the desktop. Its server profile accepts
the Minecraft EULA for that isolated test world, as the existing live harness does.

## Required measurement protocol

Use separate clean game profiles from the baseline and PR head, with identical
game/loader, JVM, window size, world seed, camera poses, view distance, graphics,
and speaker settings. Warm up each scene, measure preview-off and preview-on
frame durations, and report median/p95 plus hardware, sample count and GPU/CPU
timing method. Do not compare an idle scene to an active scene. Preserve both
raw frame samples and screenshots. Include a moving portable speaker and a
stationary camera; repeat with small and maximum ranges, inside/outside views,
terrain crossings, resource reload, disconnect, and HUD-only fallback.
