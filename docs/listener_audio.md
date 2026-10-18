# Listener audio preferences

Open a speaker or portable speaker, choose **Settings**, then use **Your listening settings**. These choices apply only on your device and do not require permission to manage the speaker. Server configuration synchronization does not override them. Changes save automatically; fade durations apply immediately, while stereo mode applies to newly started playback.

| Setting | Config key | Default | Behavior |
| --- | --- | --- | --- |
| Stereo playback | `listening.stereo` | `false` | `true` preserves the original left/right channels of stereo MP3 and WAV files and HTTP(S) audio streams. |
| Entry fade duration (ms) | `listening.fadeInMillis` | `250` | Time to ramp volume up when entering audible range, from 0 to 5000 ms. Set 0 to disable this timed fade. |
| Exit fade duration (ms) | `listening.fadeOutMillis` | `250` | Time to ramp volume down when leaving audible range, from 0 to 5000 ms. Set 0 to disable this timed fade. |

All loaders save these personal choices in `config/simplyspeakers-ui.properties`, alongside the theme. The same card includes an **Audio cache size** target of 64–4096 MiB (500 MiB by default), stored as `listening.cacheMiB`. Older cached files are removed as downloads finish; the current completed download is kept even when larger than the target. Server originals are unchanged.

On the first launch after upgrading, existing `stereoPlayback`, `rangeFadeInMillis`, and `rangeFadeOutMillis` entries are imported from the old Fabric properties or Forge/NeoForge common TOML file. Those old entries are not used after migration. Use the in-game controls thereafter. If editing the personal file manually, close the client first and relaunch to load it.

## Distance fade (%) and timed entry/exit fades (ms)

These are independent settings:

| Feature | What changes the volume? | Where it is configured |
| --- | --- | --- |
| **Distance fade (%) (existing)** | Your distance from a speaker. Moving farther away lowers the calculated volume. | Each speaker/network or proxy's in-game Distance fade slider. |
| **Entry/exit fade duration (ms) (new)** | Time since entering or leaving audible range. Volume ramps over the configured duration. | Settings > Your listening settings on each listener's device. |

For example, with **Distance fade at 0%** and both durations at **250 ms**, volume takes a quarter-second to ramp up after entry, stays at the configured volume while in range, and takes a quarter-second to ramp down after exit. With Distance fade above 0%, its calculated volume also decreases as you move farther away. Setting a duration to 0 disables that timed transition; the Distance fade slider still works independently.

The existing Distance fade slider does not set an entry or exit duration. Entry ramps up the calculated distance/direction gain. On exit the most recent spatial gain ramps down to silence; movement beyond the boundary may therefore produce a short, bounded tail. The game Master and Records volume controls continue to apply during the tail. The server briefly retains departed listeners only for lifecycle stop notifications; they do not count as subscribers or remote-stream EOF voters. A rapid return reverses the same envelope and reuses the network source when the playback occurrence is unchanged. Re-entry after cleanup joins the current server playback position.

Transport stop, pause, dimension/disconnect cleanup, speaker removal, and network replacement cancel resources immediately through the existing cleanup path. Looping does not restart the entry fade. Missing moving-body positions remain silent.

## Stereo behavior

Positional mono remains the default, including the existing stereo/surround downmix. Stereo mode plays one listener-relative stream per linked network, preserving the recording's left/right mix rather than panning it toward individual speaker locations. Proxies and portable speakers still contribute to distance/direction gain and range eligibility; overlapping emitters use the dominant gain, without duplicating or summing the stereo recording. Mono files work in either mode. Files with more than two channels retain the mono downmix.

Streaming, cache identity, seek and loop positions are unchanged: seek uses decoded frame sizes, including four-byte stereo frames. Downloaded files remain the original encoded audio and can be used in either mode. Stereo preference changes take effect on the next source allocation, not halfway through queued OpenAL buffers.

The range-exit stop packet now includes an explicit reason flag; update server and clients together to 0.10.0.
