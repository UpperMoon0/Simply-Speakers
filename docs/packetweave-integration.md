# PacketWeave integration

Simply Speakers uses PacketWeave for receipt-paced audio transfers.
Install the compatible PacketWeave jar declared by your selected loader
build on **both client and server**. When a release changes transfer or
receipt formats, update Speakers and PacketWeave together on all endpoints.
Exact version requirements and release ordering belong in loader metadata
and the versioned changelog.

## Development
Initialize the pinned dependency with `git submodule update --init --recursive`.
Run `python3 tools/prepare_packetweave.py --target <loader-target>`
before direct Gradle game launches. Omit the target to prepare all
configured dependencies before `testAllVersions`. Use the JDK/toolchain
required by the selected build. The verification harness
prepares these dependencies automatically. The API project is compile-only:
do not add it to production shadow configurations.

## Upload contract
The authenticated owner and transaction identify an upload. The server admits
at most five uploads and one per owner, including final validation. The client
has one 4,096-byte chunk outstanding, indexed from zero. Empty, oversized,
duplicate and out-of-order packets are rejected. Chunk packet decode is bounded.

A response with a positive chunk size approves a transfer. A response with
allowed=true, chunk size zero and a cumulative byte count is a write receipt.
The client ignores duplicate/stale/out-of-window receipts and waits on a worker,
never on the game thread. The server writes and closes files on PacketWeave's
dedicated worker; main-thread callbacks only send packets. The final receipt
precedes the terminal audio-validation acknowledgement.

The disk writer retains at most five queued chunk arrays plus the current worker chunk
(24,576 bytes of payload, plus bounded transient packet/copy allocations), rather than a whole audio file.
Slow writes reduce throughput instead of growing queues. File opening, disk
writes, partial-file deletion and expiry cleanup run off the server thread.
The per-chunk acknowledgement naturally limits ordinary gameplay-thread dispatch.
Untrusted connections still need the loader/server's ordinary packet flood policy.

Cancellation on disconnect, disabled-upload policy, invalid data, idle timeout
or shutdown stops admission immediately. Cancellation wins until the worker atomically
claims the terminal commit after validation and duration calculation. A commit already
claimed finishes off-thread even if disconnect/shutdown follows; shutdown drains these
commits without blocking the server. Unpublished files use unique paths and remain owned
by PacketWeave until manifest persistence succeeds. Persistence failure propagates and
cleanup is retried without exposing a manifest entry. File cleanup occurs after any current
filesystem call returns. Final audio validation is serialized on the upload
worker, isolated from the download executor. Uploads and downloads use PacketWeave's
shared receipt-paced scheduler: at most two active sends per authenticated connection
across Screens and Speakers, 32 globally, and one outstanding 4 KiB chunk per send.
There is no waiting send queue. A worker waits for a receiver receipt after each
chunk; game-thread dispatch and keepalives remain free to progress. A stalled
receipt or dispatch times out after ten seconds. This deliberately trades media
throughput for bounded game-connection backlog. Audio-specific cache/playback
state remains here.

## Verification
PacketWeave tests cover 60 MiB receive/content parity, an intentionally blocked
writer, fixed session/owner/window limits, ordering/replay, existing-file safety,
disconnect, expiry and shutdown cleanup. These are JVM/disk regression tests,
not multiplayer tick-time measurements.

The fixture serves an uncached 480,044-byte WAV to both real clients. It contains
120 seconds of nonzero 2 kHz, mono 16-bit PCM, preserving seek and context-reload
coverage while using only 118 receipt windows. At a conservative 100 ms per window,
the transfer needs about 12 seconds; a 60 MiB fixture would need 15,360 windows and
at least 768 seconds even at one 50 ms tick per receipt. Each client must
report `SIMPLYSPEAKERS_DOWNLOAD_PLAY_PASS` with download time and request-to-decoded
OpenAL playback time, within the isolated fixture's 30-second budget. A cached
playback or server-only dispatch timing cannot satisfy this marker. The server waits
for both authenticated fixture connections to report their own initial decoded
playback before advancing pause/seek/restart controls. That readiness barrier has
its own fixed 30-second deadline and never resets either client's latency clock.

The two-player live harness uploads the same bounded, valid WAV using real
request/data/receipt packets while the second player exercises spatial playback.
It requires `SIMPLYSPEAKERS_CLIENT_UPLOAD_PASS` and
`SIMPLYSPEAKERS_SERVER_UPLOAD_PASS`, reporting sampled completed server tick
p50/p95/max before, during and after the upload. Run
`python3 tools/verify.py live --target <loader-target>`.

For constrained disk/network profiling, record the Minecraft version, loader, mod versions,
player count, disk/network limits and a server profiler trace. Upload a 60 MiB
valid WAV with another player moving/interacting. Record tick-time p50/p95/max
for idle baseline, transfer, validation and recovery, and repeat with multiple
uploaders and constrained disk/network. Compare against a previous release in a separate
test world under the same conditions; do not infer live TPS from the regression suite.
