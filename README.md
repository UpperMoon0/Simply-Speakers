# Simply Speakers

CurseForge: https://www.curseforge.com/minecraft/mc-mods/simply-speakers

Simply Speakers is a Minecraft mod that allows players to play custom audio files in-game using placed and portable speakers.

## Features

* **Portable Speaker**: Carry custom spatial audio in your inventory using the same Library, Playlist, Queue, transport, Settings and access controls. Nearby players hear the moving source. See [portable speaker behavior](docs/portable_speaker.md).
* **Speaker Block**: Plays custom audio with configurable range, volume and direction.
* **Proxy Speaker Block**: Sync audio playback across multiple locations by linking to a main Speaker.
* **Custom Audio**: Upload or manually add .mp3 and .wav files.
* **Per-Speaker Audio Settings**: Fine-tune max volume (0-100%), max range (1-512 blocks), and audio dropoff (0-100%) per speaker.
* **Speaker Controllers**: Link dedicated controllers by Speaker ID for buttons, announcements, lever playback, and analog volume. Each controller has one job and accepts input from any side.
* **In-Game Guide**: A Patchouli book in the Simply Speakers creative tab documents setup, playback, automation, permissions, and integrations.
* **Range-based Audio**: Volume fades with distance; players entering/leaving range automatically start/stop hearing audio.
* **Repeat Playback**: Use the player bar to repeat the current track or the active playlist. Queued requests play once and take priority.
* **Moving Speakers (1.21.1)**: With Sable installed, speakers on physics bodies emit audio from their moving and rotating world positions.
* **Cross-Platform**: Supports Fabric, Forge, and NeoForge across multiple Minecraft versions.
* **Playlists & Queues**: Named saved playlists with create, rename, duplicate and delete controls, shuffle, repeat, and a one-shot play-next queue.
* **Transport Controls**: Pause/resume (position-preserving), seek, restart, next/previous from GUI, redstone, commands, or code.
* **CC:Tweaked Integration** (optional): Main Speakers expose playback, queues, saved playlists, audio settings and events. See the [Lua API reference](docs/cc_api.md).
* **Named Networks & Commands**: Name your speaker networks and drive them with `/simplyspeakers` (`/ss`) or the public Java API.
* **Library Organization**: Display names, categories, and tags for your audio library.
* **Ownership & Access**: Public, trusted, owner-only, or operators-only control per network.
* **Directional Audio**: Focus sound into a cone in front of the speaker with adjustable directionality, angle, and rear attenuation.
* **Internet Streams**: Play direct `http(s)` MP3/WAV stream URLs live.

## Supported Platforms

| Platform  | Minecraft   |
|-----------|-------------|
| Fabric    | 1.20.1      |
| Forge     | 1.20.1      |
| Fabric    | 1.21.1      |
| NeoForge  | 1.21.1      |
| NeoForge  | 26.1.2      |

## How it works

1. **Main Speaker**: Controls audio playback state (play, pause, stop) and stores the selected audio file and settings.
2. **Proxy Speakers**: Place anywhere and link to a main Speaker by setting the same Speaker ID in their configuration interface.
3. **Synchronization**: When the main Speaker starts playback, all linked Proxy Speakers begin playing the same audio at the same position.
4. **Redstone Control**: Link Speaker Controllers by Speaker ID and choose one job on each. Speakers and proxies ignore direct redstone input.
5. **Range & Dropoff**: Audio fades with distance based on each speaker's configurable max range and dropoff curve.

## Audio Settings

Each speaker network and individual proxy has its own settings:

| Setting        | Range      | Description                                               |
|----------------|------------|-----------------------------------------------------------|
| Max Volume     | 0% – 100%  | How loud the speaker is at the source                     |
| Max Range      | 1 – 512    | Maximum distance (in blocks) the audio can be heard       |
| Audio Dropoff  | 0% – 100%  | 0% = uniform volume at all distances; 100% = linear fade  |

## Access and streams

Settings groups ownership, control access and trusted players together. Owners and operators
can claim or transfer ownership, choose Public/Trusted/Owner/Operators access, and manage
trusted players by online name or UUID. These restrictions affect control, not hearing.
Trusted playback access does not grant permission to change ownership, access or linking.

Library's **Add stream** opens a URL dialog with Play, Queue and playlist destinations.
Use a direct public HTTP(S) MP3/WAV URL, up to 256 characters. Remote streams must be enabled
by the server; each listener connects directly to the URL. Upload permission is independent.

## Adding Audio Files

### In-Game Upload
Right-click a Speaker, click the upload button, and select an audio file. Use MP3 or WAV files within the server upload limit.

### Manual Installation
Audio files are stored in `simply_speakers_audios/` inside your world's save folder.

1. Generate a UUID (e.g. from [uuidgenerator.net](https://www.uuidgenerator.net/)).
2. Rename your `.mp3` or `.wav` file to `<your-uuid>.mp3` and place it in the `simply_speakers_audios/` folder.
3. Add an entry to `audio_manifest.json`:
```json
{
  "your-uuid": {
    "uuid": "your-uuid",
    "originalFilename": "your-song.mp3",
    "ownerUUID": "intended-player-uuid",
    "durationSeconds": 120.0
  }
}
```

## Configuration

Edit the mod config file to adjust:
- `speakerRange`: Server ceiling for per-speaker range (1–512, default: 64)
- `disableUpload`: Disable the in-game upload feature
- `allowRemoteStreams`: Whether clients may stream direct `http(s)` MP3/WAV URLs through this server (independent of `disableUpload`, default: off)
- `maxUploadSize`: Maximum file size for uploads in bytes
- `debugLogging`: Enable verbose logging for troubleshooting

## Dependencies

- **Architectury API** (matching your Minecraft version and loader)
- **Fabric API** (for Fabric versions)
- **OpenUI MC 0.0.12 or newer** (matching your Minecraft version and loader)
- **Patchouli** (matching your Minecraft version and loader; required on clients and servers, installed automatically in every development environment)

## Speaker Controllers and Guide

Craft a Speaker Controller with an Audio Circuit, iron ingots, redstone, and a comparator. Give your main speaker a Speaker ID, then enter that ID in the controller. Speakers and proxies ignore direct redstone input; existing native modes are retired. Select one job: play/pause, next, previous, stop, restart, announcement, powered playback, volume, or analog track selection. Place multiple controllers for separate controls without assigning settings to block faces.

Each controller has one job and a fixed input rule. Pulse jobs fire once per rising edge; Play While Powered resumes with power and pauses on release; Volume maps strength 0–15 to a chosen ceiling. Playback and volume may target a network or one loaded proxy by coordinates. Links stay within one dimension and recheck the configuring player's current network rights.

Controllers sharing the same target combine playback inputs with OR and use the highest requested volume. Duplicate pulse commands in one tick execute once. Conflicting pulses use the priority Stop > Announcement > Restart > Toggle > Select track > Next > Previous, with the lowest packed block position resolving equal-priority sources. Different target IDs and individual proxy outputs remain independent. Powered playback applies when the combined input changes; holding power does not restart finished tracks or undo a later Stop pulse or manual transport.

Unloading, removal, retargeting and revoked permissions release continuous inputs. Controller volume overrides are transient and preserve saved manual volume across restart; removing the last volume controller restores it. Removing the last proxy playback controller enables that output. Removing a network playback controller leaves its current transport state. Loading a powered pulse controller does not generate a fresh pulse. Manual player transport remains available.

Announcement plays an owned local clip once, even when the network loops or has a playlist. Choose whether another pulse restarts the same active announcement or is ignored. It replaces current playback and stops at the end; it does not resume the previous sound. Internet streams cannot be announcements because their duration is unknown.

Find **Simply Speakers Guide** in the mod creative tab, or craft it from a book and an Audio Circuit. Its 23 chapters cover every player-facing feature, with controller examples, administration details, and troubleshooting.

OpenUI MC is distributed as a separate mod and is not bundled into Simply Speakers. Install the matching OpenUI jar alongside Simply Speakers. Fabric metadata cannot scope a required dependency to the client, so Fabric dedicated servers must also have OpenUI installed; Forge and NeoForge declare it as client-only.

### Optional integrations

- **Sable 2.0.5** (Minecraft 1.21.1): Enables correct spatial audio for speakers mounted on Sable physics bodies. Simply Speakers works normally without Sable.
- **Create Aeronautics** (NeoForge 1.21.1): Required only if you want Aeronautics' blocks and physics-body gameplay. It is not required for Simply Speakers or basic Sable compatibility.

Sable Companion is bundled with Simply Speakers, and Sable's official jar includes its physics backend. Do not install either component separately.

## Building from Source

1. Clone the repository.
2. Run `gradlew.bat build` (or `./gradlew build` on Linux/Mac).
3. JAR files are located in each subproject's `build/libs/` directory.

To target a specific module:
```bash
gradlew.bat :neoforge-1.21.1:build
gradlew.bat :fabric-1.20.1:build
```

Development clients and servers automatically load CC:Tweaked on Fabric/Forge
1.20.1 and Fabric/NeoForge 1.21.1. These runtime dependencies are development-only
and are not bundled in release jars or published as required dependencies.

For moving-speaker testing, both 1.21.1 loaders load Sable 2.0.5 when its matching
jar exists under `../sable/<loader>/build/libs/`. Fabric also exposes Sable's
nested physics backend, Veil, and Forge Config API Port to the dev runtime.
NeoForge additionally loads Create and its dependencies, and the Simulated,
Aeronautics, and Offroad 1.3.1 component jars when all three are built under
`../Simulated-Project/<component>/neoforge/build/libs/`.
Build those sibling projects before starting the dev run to enable these integrations.
The 26.1.2 module has no CC:Tweaked or Sable integration.

To run all version-independent tests:
```bash
gradlew.bat testAllVersions
```

## Mod Structure

The project is a multi-loader, multi-version project:

| Module              | Description                                          |
|---------------------|------------------------------------------------------|
| `common/`           | Pure Java logic — config, audio ownership, state     |
| `common-1.20.1/`   | Shared Minecraft code for 1.20.1 (Fabric + Forge)    |
| `common-1.21.1/`   | Shared Minecraft code for 1.21.1 (Fabric + NeoForge) |
| `shared/minecraft-1.21plus/` | Shared code between 1.21.1 and 26.1.2 NeoForge |
| `shared/loader-neoforge/`    | Shared NeoForge platform code                  |
| `fabric-1.20.1/`   | Fabric 1.20.1 loader entry point                     |
| `fabric-1.21.1/`   | Fabric 1.21.1 loader entry point                     |
| `forge-1.20.1/`    | Forge 1.20.1 loader entry point                      |
| `neoforge-1.21.1/` | NeoForge 1.21.1 loader entry point                   |
| `neoforge-26.1.2/` | NeoForge 26.1.2 loader entry point (standalone)      |
| `tools/`            | Automated test scripts                               |

## Changelog

See the versioned release notes in the [changelog directory](changelog/).

## Contributing

Contributions are welcome! Please feel free to submit a pull request or open an issue.

### Player, playlists, and queue

Saved playlists belong to each player and are available at any speaker they can control.
Speaker networks keep independent playback copies and temporary queues. Browsing or editing
another list leaves playback running; playing a list or entry preserves queued requests.

Queued requests play once before the saved source resumes at its remembered position, even
when Repeat Current Sound is enabled. Play Next inserts at the front; Add to Queue appends.
Clearing the queue keeps saved lists. Deleting the playing list stops its source but keeps
requests; every saved list can be deleted without deleting recordings.

Each player can save 16 lists, 256 entries per list and 512 entries total. Names must be
unique and contain 1–64 characters. Legacy network lists migrate to their owner.

Display name is a label for listings and commands. Speaker ID links speakers, proxies and
controllers within one dimension; a blank ID makes a speaker standalone.

Volume, range, distance fade and directional controls apply live. The eight-second area
preview marks boundaries in teal, louder areas in green and quieter areas in amber.
Proxies have independent volume, range and fade; directional settings follow the linked
speaker and use the proxy's own facing.

### Shared crafting ingredient

Craft an **Audio Circuit** with two copper ingots, two redstone dust and one
amethyst shard to make four circuits. Place redstone above and below the shard,
and copper to its left and right. Speakers, proxy speakers, redstone controllers
and the guide book each consume a circuit, giving their recipes a mod-specific
ingredient. The circuit is also in the Simply Speakers creative tab.
The guide's first chapter shows its crafting recipe.
