# Controller casing and item sprites

The controller casing was generated with the built-in imagegen tool, referencing the original generated controller front (`exec-7541bc29-03b6-4508-b173-4cf99f1f30ce.png`). Original outputs remain in `C:/Users/NsTut/.codex/generated_images/01a11238-21af-7811-b1be-9dac7d9bae53/`.

Exact casing prompt:

> Create ONE square flat Minecraft block SIDE/TOP casing texture for Simply Speakers redstone controller. Reference attached existing FRONT texture for exact charcoal dark blue-gray industrial metal palette, silver beveled perimeter and small corner rivets. This is the other face of the same electronics controller, not a speaker: no speaker grille, no knobs, no display, no lettering. Orthographic flat square tile completely fills canvas, no background, no perspective, no rendered cube. Dark charcoal-blue steel inset panel, subtle coarse pixelated tonal variation, thin silver-gray edge frame, four black screw heads at corners, one restrained inset rectangular access-panel seam. Minecraft crisp pixel art aesthetic, simple readable at 32x32 pixels, consistent with reference. Opaque tile.

Generated casing original: `exec-47be117b-37fe-4056-a085-65f0f2d96c96.png`. The shipped casing is downsampled to 16×16 with nearest-neighbor sampling and used for both top and side. The controller front remains its own texture.

The guide book (`exec-e7eda3db-181b-4ead-b56d-197e5ecadb12.png`) and circuit (`exec-ffd6a5f5-900f-4f45-af62-31163d35f588.png`) reuse their original imagegen outputs. Both are resized directly to 64×64 with nearest-neighbor sampling, retaining RGBA transparency. A bounds threshold excludes nearly invisible generation artifacts from determining the crop; original visible pixels are preserved. No 16×16 sprite was enlarged.

Shipped assets live in `common/src/main/resources/assets/simplyspeakers/textures/`: `block/redstone_controller_casing.png`, `item/guide_book.png`, and `item/audio_circuit.png`.

Controller casing: the generated casing is now sampled with nearest-neighbour to 16×16 to match vanilla block faces. Book and Audio Circuit item sprites remain 64×64.
