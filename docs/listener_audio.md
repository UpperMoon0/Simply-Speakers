# Listener audio preferences

These are local settings on each listener's client. Server configuration synchronization does not override them. Restart the client after editing the config file; stereo mode applies to newly started playback.

| Setting | Default | Behavior |
| --- | --- | --- |
| `stereoPlayback` | `false` | `true` preserves the original left/right channels of stereo MP3 and WAV files and HTTP(S) audio streams. |
| `rangeFadeInMillis` | `250` | Range-entry fade duration, from 0 to 5000 milliseconds. Set 0 to disable. |
| `rangeFadeOutMillis` | `250` | Range-exit fade duration, from 0 to 5000 milliseconds. Set 0 to disable. |

Fabric reads `config/simplyspeakers.properties`. Forge and NeoForge read `config/simplyspeakers-common.toml`. Existing files may omit the new entries: the defaults above apply until entries are added.

## Boundary fades

Timed fades are separate from the speaker's **Distance fade** slider. Entry ramps up the calculated distance/direction gain. On exit the most recent spatial gain ramps down to silence; movement beyond the boundary may therefore produce a short, bounded tail. The game Master and Records volume controls continue to apply during the tail. A rapid return reverses the same envelope and reuses the network source when the playback occurrence is unchanged. Re-entry after cleanup joins the current server playback position.

Transport stop, pause, dimension/disconnect cleanup, speaker removal, and network replacement cancel resources immediately through the existing cleanup path. Looping does not restart the entry fade. Missing moving-body positions remain silent.

## Stereo behavior

Positional mono remains the default, including the existing stereo/surround downmix. Stereo mode plays one listener-relative stream per linked network, preserving the recording's left/right mix rather than panning it toward individual speaker locations. Proxies and portable speakers still contribute to distance/direction gain and range eligibility; overlapping emitters use the dominant gain, without duplicating or summing the stereo recording. Mono files work in either mode. Files with more than two channels retain the mono downmix.

Streaming, cache identity, seek and loop positions are unchanged: seek uses decoded frame sizes, including four-byte stereo frames. Downloaded files remain the original encoded audio and can be used in either mode. Stereo preference changes take effect on the next source allocation, not halfway through queued OpenAL buffers.

The range-exit stop packet now includes an explicit reason flag; update server and clients together to 0.10.0.
