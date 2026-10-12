# PacketWeave integration

Simply Speakers 0.10.0 requires PacketWeave 0.2.0 or later on both sides.
Install the native PacketWeave jar matching the Minecraft version and loader.
The upload protocol changed; update clients and server together.

## Development
Initialize the pinned dependency with `git submodule update --init --recursive`.
Run `python3 tools/prepare_packetweave.py --target forge-1.20.1` (or the desired
loader) before direct Gradle game launches. Run without a target to prepare all
five dependencies before `testAllVersions`. Use Java 21 to supervise the builds;
Gradle toolchains supply the target Java versions. The verification harness
prepares these dependencies automatically. The API project is compile-only:
do not add it to production shadow configurations.

## Upload contract
The authenticated owner and transaction identify an upload. The server admits
at most five uploads and one per owner, including final validation. The client
has one 32,000-byte chunk outstanding, indexed from zero. Empty, oversized,
duplicate and out-of-order packets are rejected. Chunk packet decode is bounded.

A response with a positive chunk size approves a transfer. A response with
allowed=true, chunk size zero and a cumulative byte count is a write receipt.
The client ignores duplicate/stale/out-of-window receipts and waits on a worker,
never on the game thread. The server writes and closes files on PacketWeave's
dedicated worker; main-thread callbacks only send packets. The final receipt
precedes the terminal audio-validation acknowledgement.

The disk writer retains at most five queued chunk arrays plus the current worker chunk
(192,000 bytes of payload, plus bounded transient packet/copy allocations), rather than a whole audio file.
Slow writes reduce throughput instead of growing queues. File opening, disk
writes, partial-file deletion and expiry cleanup run off the server thread.
The per-chunk acknowledgement naturally limits ordinary gameplay-thread dispatch.
Untrusted connections still need the loader/server's ordinary packet flood policy.

Cancellation on disconnect, disabled-upload policy, invalid data, idle timeout
or shutdown stops admission immediately. File cleanup occurs after any current
filesystem call returns. Final audio validation is serialized on the upload
worker, isolated from the download executor. Downloads use PacketWeave chunking
and bounded game-thread dispatch; audio-specific cache/playback state remains here.

## Verification
PacketWeave tests cover 60 MiB receive/content parity, an intentionally blocked
writer, fixed session/owner/window limits, ordering/replay, existing-file safety,
disconnect, expiry and shutdown cleanup. These are JVM/disk regression tests,
not multiplayer tick-time measurements.

The existing two-player live harness now uploads a valid 60 MiB WAV using real
request/data/receipt packets while the second player exercises spatial playback.
It requires `SIMPLYSPEAKERS_CLIENT_UPLOAD_PASS` and
`SIMPLYSPEAKERS_SERVER_UPLOAD_PASS`, reporting sampled completed server tick
p50/p95/max before, during and after the upload. Run
`python3 tools/verify.py live --target forge-1.20.1` (or another native target).

For constrained disk/network profiling, record the Minecraft version, loader, mod versions,
player count, disk/network limits and a server profiler trace. Upload a 60 MiB
valid WAV with another player moving/interacting. Record tick-time p50/p95/max
for idle baseline, transfer, validation and recovery, and repeat with multiple
uploaders and constrained disk/network. Compare against 0.9.0 in a separate
test world; do not infer live TPS from the regression suite.
