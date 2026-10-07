# ComputerCraft API

Main Speaker blocks expose `simply_speaker` on Fabric/Forge 1.20.1 and
Fabric/NeoForge 1.21.1. Proxies and controllers do not expose peripherals.
NeoForge 26.1.2 has no CC integration.

Computers are anonymous: playback requires Public access. Local recordings and
saved playlists come from the network owner's library. Unclaimed networks have
no local library. Claimed network management remains owner/operator-only, even
in Public mode; a computer cannot change ownership, trusted players or access
policy. The public actorless Java `SpeakerApi` follows the same authorization.
The internal server control service is privileged and is not an authorization API.

Mutation methods return `true` when accepted and `false` when denied or invalid.
Lua argument type errors raise ordinary CC errors. Numeric setters reject NaN and
infinity; finite values clamp to their setting's bounds. Slots are **1-based in
Lua** and **0-based in Java**. Getters return snapshots, not mutable server objects.

| Methods | Arguments / result |
| --- | --- |
| `play`, `pause`, `togglePause`, `stop`, `restart`, `next`, `previous` | No arguments; network transport. |
| `seek(seconds)` | Nonnegative position; requires a known-duration recording. |
| `setTrack(audioId)` | Select without starting an idle network. Empty string clears selection. An already playing network switches immediately. |
| `getLibrary()` | Owner recordings as `{id, name, duration}` entries; empty when denied. |
| `getStatus()` | `{playing, paused, position, track, trackId, looping, network, speakerId, repeatMode, shuffle, playlistIndex, canControl, canManage}`. `playlistIndex=0` means none. |
| `setVolume(value)`, `getVolume()` | 0–1. |
| `setRange(blocks)`, `getRange()` | 1 to the server's configured range limit. |
| `setAudioDropoff(value)` | 0–1 distance attenuation. |
| `getSettings()` | `{volume, range, audioDropoff, directionality, coneAngle, rearAttenuation}`. |
| `setRepeatMode(mode)` | `none`, `track` or `playlist`. Invalid strings return false. |
| `setShuffle(enabled)` | Boolean. |
| `setLooping(enabled)`, `isLooping()` | Compatibility alias: true selects track Repeat; false selects none. |
| `getPlaylist()` | Current speaker playback copy as `{slot, id, name}` entries. |
| `addToPlaylist(audioId)`, `removeFromPlaylist(audioId)`, `clearPlaylist()` | Edit the speaker playback copy, not the owner's saved template. |
| `playPlaylist()`, `selectPlaylistTrack(slot)` | Start the speaker playlist / select an entry. |
| `getSavedPlaylists()` | Owner templates as `{id, name, count}` entries; empty when denied. |
| `playSavedPlaylist(id)` | Load and start a validated playback copy; preserve the speaker queue. |
| `queueNext(audioId)`, `queueLast(audioId)` | Prepend / append a temporary request. |
| `getQueue()` | Ordered array of audio IDs. |
| `removeQueued(slot)`, `moveQueued(slot, direction)`, `clearQueue()` | Remove / move by -1 or +1 / clear requests. Out-of-bounds operations return false. |
| `getNetworkName()`, `setNetworkName(name)` | Display name; does not change Speaker ID. Setter requires management rights. |
| `setDirectionality(value)`, `setConeAngle(degrees)`, `setRearAttenuation(value)` | Management rights required; normalized values 0–1, cone 1–350 degrees. |

Personal playlist creation, renaming and deletion stay in the player UI. Relinking,
proxy settings and controller actions also stay in their block screens. Computers
can tune directionality and names only on unclaimed networks; claiming a network
removes those anonymous management rights.

Direct HTTP(S) MP3/WAV URLs are accepted only when remote streaming is enabled
and the address passes stream security checks. Webpages and service links are
unsupported. Each listening client opens the URL; the CC call does not download it.

## Example

Claim the network in its UI, set access to Public and upload a recording first.

```lua
local speaker = assert(peripheral.find("simply_speaker"), "No speaker attached")
local recordings = speaker.getLibrary()
assert(#recordings > 0, "Owner library is empty or access is restricted")
assert(speaker.setTrack(recordings[1].id))
assert(speaker.setVolume(0.5))
assert(speaker.play())
```

Events are `speaker_started`, `speaker_paused`, `speaker_resumed`,
`speaker_stopped`, `speaker_track_changed` and `speaker_finished`.
Arguments: attachment name, audio ID, network display name. Linked peripherals
produce separate attachment events. Finished signals natural exhaustion, not a
manual stop or a transition to another queued/playlist recording.

Methods run through CC's server-thread scheduler and may yield. To listen without
losing events while another coroutine calls methods, use `parallel`:

```lua
parallel.waitForAny(function()
  while true do
    local event, attachment, audioId, network = os.pullEvent()
    if event:sub(1, 8) == "speaker_" then
      print(event, attachment, audioId, network)
    end
  end
end, function()
  while true do
    sleep(5)
    print(speaker.getStatus().position)
  end
end)
```

Java callers use `selectTrack` for selection only; legacy `setTrack` also starts
playback and derives filenames from the validated manifest. Queue, playback-copy,
library, saved-template and audio-setting operations have corresponding methods
on `SpeakerApi`; they retain the same anonymous permission restrictions.

## Automated acceptance

`python tools/verify.py cc` starts isolated dedicated test worlds for all four CC
loaders. Actual placed computers execute the packaged `cc_acceptance.lua` using
CC's Cobalt runtime. The program exercises discovery, Lua types/errors, all access
modes, ownership, stream policy, transport and events, queue ordering, playback-copy
editing, saved templates, detach/reattach and a real computer reboot. This server
fixture does not prove audible output; the separate `live` layer covers decoder
and OpenAL playback.
