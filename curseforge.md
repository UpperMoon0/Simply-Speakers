# Simply Speakers

Simply Speakers adds configurable speaker blocks that play your own MP3 and WAV audio in Minecraft. Upload files in game, build synchronized speaker networks across large areas, and control playback with redstone.

![Simply Speakers speaker block](https://media.forgecdn.net/attachments/1856/970/thumbnail-png.png)

## Features

- **Speaker block:** Select audio, configure playback, and control a linked network from one main speaker.
- **Proxy speakers:** Link multiple locations with the same Speaker ID and keep playback synchronized.
- **MP3 and WAV support:** Upload local audio files directly through the speaker interface or install them manually in the world save.
- **Per-speaker controls:** Configure volume, audible range, distance dropoff, and looping.
- **Redstone control:** Power speakers to play and remove power to stop.
- **Multiplayer synchronization:** Nearby players hear the same playback position while the server remains authoritative.
- **Moving-body audio (compatible builds):** When Sable is installed, speakers mounted on physics bodies follow their actual moving and rotating positions.
- **Internet streams:** When the server enables `allowRemoteStreams`, direct `http(s)` MP3/WAV URLs can be selected as tracks and are streamed live by clients without caching. Private/internal network addresses are always rejected.

## Getting Started

1. Craft and place a **Speaker**.
2. Right-click it to open the interface.
3. Upload or select an audio file.
4. Configure its volume, range, dropoff, looping, and Speaker ID.
5. Add **Proxy Speakers** with the same Speaker ID wherever you want synchronized playback.
6. Use the interface or redstone to control playback.

## Optional In-Game Guide

Install **Patchouli** for your Minecraft version and loader to enable the Simply Speakers guide and its crafting recipe. Patchouli is optional on clients and servers; audio features work without it.

## Optional Sable Integration

On builds compatible with **Sable**, installing the matching Sable release enables spatial audio for speakers placed on moving physics bodies. Audio follows moving, rotating, and scaled bodies without restarting playback. Networks can span moving bodies and ordinary world speakers.

Sable is optional. Simply Speakers works normally without it; only moving-body spatial compatibility is unavailable.

**Create Aeronautics is not required by Simply Speakers.** Install Aeronautics and its dependencies only on compatible builds if you want its physics-body blocks and gameplay. Consult each mod release for supported loaders.

Sable Companion is already bundled with Simply Speakers, and Sable's official jar includes its physics backend. Users should not install those components separately.

## Manual Audio Installation

Audio files are stored in `simply_speakers_audios` inside the world's save directory. To add one manually:

1. Generate a UUID, such as with [uuidgenerator.net](https://www.uuidgenerator.net/).
2. Rename the file to `<your-uuid>.mp3` or `<your-uuid>.wav` and place it in `simply_speakers_audios`.
3. Add an entry to `audio_manifest.json`:

```json
{
  "your-uuid": {
    "uuid": "your-uuid",
    "originalFilename": "your-song.mp3"
  }
}
```

## Supported Platforms

Simply Speakers provides **Fabric, Forge and NeoForge** builds. Use the
CurseForge file filters and loader metadata to choose a file for your
Minecraft version and loader. Optional integrations such as Sable work
only on compatible builds.

## Required Dependencies

- **Architectury API** matching your Minecraft version and loader
- **OpenUI MC**, at the compatible version declared by the selected mod jar, required on clients and on Fabric servers when their metadata requires it
- **PacketWeave**, at the compatible version declared by the selected mod jar, on **both client and server**
- **Fabric API** on Fabric

## Configuration

- `speakerRange`: Default range for new speakers, from 1 to 512 blocks (default: 64)
- `disableUpload`: Disable in-game uploads
- `allowRemoteStreams`: Whether clients may stream direct `http(s)` MP3/WAV URLs through this server (independent of `disableUpload`, default: off)
- `maxUploadSize`: Maximum upload size in bytes
- `debugLogging`: Enable verbose troubleshooting logs

## Support

- [Join the Discord community](https://discord.gg/4vD9WuT2As)
- [Report bugs on GitHub](https://github.com/UpperMoon0/Simply-Speakers/issues)

## Updating an Installation

Check your selected release's loader metadata and versioned changelog for
exact minimum OpenUI and PacketWeave requirements. Install matching mod
jars for your Minecraft version and loader. When media packet or receipt
formats change, update clients and server together; mixed protocol
generations may be incompatible. Existing libraries and completed audio
caches remain usable unless the release notes state otherwise. Fabric
server metadata may also require OpenUI; Forge and NeoForge generally
require it only on clients.
