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
- Native 26.1.2 build and existing adapter tests passed. Added GPU lifecycle tests
  are awaiting their next run.
- Equivalent-scene particle-versus-shader frame/GPU timing: not measured.
- Actual game screenshots for small/large range, inside/outside, moving portable
  speaker, terrain occlusion, and fallback: not captured.
- Shaderpack and mod compatibility: not verified in a game runtime.

No synthetic image or source-only timing is presented as Minecraft acceptance.

## Hidden-window graphics fixture

`tools/zone_shader_smoke.py` compiles the actual OpenGL renderer and creates an
invisible GLFW window. On this machine it reported NVIDIA GeForce GTX 1050 Ti,
OpenGL 3.3.0, NVIDIA 581.15. It passed program compilation, actual draw output,
restoration of non-default GL state, resource deletion, reload, and forced fallback.

Seven actual GPU captures were inspected: small outside/inside, large
outside/inside, two moving-origin/direction poses, and controlled depth occlusion.
These are **shader-fixture captures, not Minecraft screenshots**. The depth case
uses a controlled scissored depth buffer, not Minecraft terrain. The portable cases
exercise uniform changes, not Minecraft's holder tracking or Sable integration.
They establish shader output and state/resource behavior, not game acceptance or
particle-baseline performance.

![Small outside shader fixture](validation/zone-preview/small-outside.png)
![Small inside shader fixture](validation/zone-preview/small-inside.png)
![Large outside shader fixture](validation/zone-preview/large-outside.png)
![Large inside shader fixture](validation/zone-preview/large-inside.png)
![Changed portable pose fixture](validation/zone-preview/portable-pose-b.png)
![Controlled depth fixture](validation/zone-preview/depth-occlusion-fixture.png)

## Required measurement protocol

Use separate clean game profiles from the baseline and PR head, with identical
game/loader, JVM, window size, world seed, camera poses, view distance, graphics,
and speaker settings. Warm up each scene, measure preview-off and preview-on
frame durations, and report median/p95 plus hardware, sample count and GPU/CPU
timing method. Do not compare an idle scene to an active scene. Preserve both
raw frame samples and screenshots. Include a moving portable speaker and a
stationary camera; repeat with small and maximum ranges, inside/outside views,
terrain crossings, resource reload, disconnect, and HUD-only fallback.
