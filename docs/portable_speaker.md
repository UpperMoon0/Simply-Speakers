# Portable Speaker

The Portable Speaker is a non-stackable inventory item. Use it from either hand
to open the normal four-view player. Playback continues while the item is in a
player's main inventory, hotbar or off hand; holding it is not required. Its
original supplied 16×16 transparent sprite is used without redrawing or scaling.

## Audio and controls

- Library uploads and owned recordings, direct MP3/WAV URLs, library search and
  metadata work through the existing audio library and server streaming policy.
- Named playlists, the personal playlist catalog, the temporary queue, shuffle,
  repeat, next/previous, pause/resume, restart and seeking use the normal player.
- Volume, range, dropoff, directionality, cone angle and rear attenuation use the
  same settings and validation as placed speakers. Defaults remain 100% volume,
  16-block range, 100% dropoff and omnidirectional sound.
- The source follows its carrier at approximately torso height. Directional
  sound follows the carrier's horizontal facing. Every nearby listener receives
  spatial audio, including listeners outside the carrier's entity-tracking range.
- Network names, ownership, access modes, trusted players and ownership transfer
  remain available. Possessing the item is required to use its screen controls;
  possession does not bypass its saved network permissions.

The carrier hears the same spatial source as other listeners. This is not a
private headphones mode. Server range limits and remote-stream restrictions
still apply. Matching mod versions are required on clients and server.

## Identity, transfer and persistence

Each item gets a server-assigned persistent identity, separate from its current
inventory slot and position. Moving between slots cannot create another source.
Simultaneously carried duplicate identities are re-keyed to prevent shared or
ghost emitters. State, playlists and permissions are saved in the world registry;
the item retains its identity and link metadata.

Dropping the item, putting it in a chest, dying, leaving the server or becoming a
spectator removes the carried emitter. Standalone playback pauses so it can be
resumed later. A transfer to another player removes the previous holder's source
and invalidates their screen's control target. Pick the item up and use Play to
resume as permitted by its saved access rules.

Dimension changes remove the old source. Named networks are dimension-scoped:
crossing a portal clears the carried item's link instead of silently joining a
same-named destination network. The item's independent saved state is retained.

## Links and block-only features

Within one dimension, a Speaker ID can link the item to the same networks as
placed Speakers and Proxies, subject to manage rights on both networks. Linked
transport is shared: pausing it affects that network. Removing one linked
portable emitter does not stop a network with another main speaker.

The item itself has no redstone sides, comparator output or adjacent
ComputerCraft peripheral. Use placed Speaker Controllers and a placed Speaker
on the same network for those block interfaces. Network commands and the public
Java API continue to address the shared state. Physical block-position commands
still describe blocks rather than inventory slots.

## Crafting

The recipe uses one iron ingot above an Audio Circuit, redstone on both sides of
the circuit, and one note block below it. It is also in the Simply Speakers
creative tab. The in-game guide includes the recipe and a short usage entry.

## Verification

See [TESTING.md](../TESTING.md) for the core, production-adapter and real
dedicated-server/two-client layers. The runtime gate requires a separate stationary
listener to observe the carried source moving, decreasing in gain, leaving range,
and re-entering at the current playback position without duplicates. Pause, stop
and removal require both clients to delete their OpenAL sources. The carrier also
reloads Minecraft's actual sound engine during normal and portable playback and
while paused, requiring recovery at an advancing offset or continued silence.
This checks OpenAL context recreation; physical output-device switching still
needs a manual headphones-to-monitor check. The automated
driver is silent, so a pass establishes decoding, moving source behavior and cleanup,
not subjective audible quality. Manual multiplayer checks should additionally
cover sound quality, UI appearance, handoff, portal travel and listening beyond
entity-tracking distance.
