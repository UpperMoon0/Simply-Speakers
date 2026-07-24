# Simply Speakers

CurseForge: https://www.curseforge.com/minecraft/mc-mods/simply-speakers

Simply Speakers is a Minecraft mod that allows players to play custom audio files in-game using speaker blocks.

## Features

* **Speaker Block**: The main controller block that stores and plays audio files.
* **Proxy Speaker Block**: Sync audio playback across multiple locations by linking to a main Speaker.
* **Custom Audio**: Upload or manually add .mp3 and .wav files.
* **Per-Speaker Audio Settings**: Fine-tune max volume (0-100%), max range (1-512 blocks), and audio dropoff (0-100%) per speaker.
* **Redstone Control**: Power a speaker to play, unpower to stop.
* **Range-based Audio**: Volume fades with distance; players entering/leaving range automatically start/stop hearing audio.
* **Loop Playback**: Toggle looping from the speaker GUI.
* **Cross-Platform**: Supports Fabric, Forge, and NeoForge across multiple Minecraft versions.

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
4. **Redstone Control**: Both Speaker types respond to redstone signals — powered plays, unpowered stops.
5. **Range & Dropoff**: Audio fades with distance based on each speaker's configurable max range and dropoff curve.

## Audio Settings

Each speaker and proxy speaker has independent settings:

| Setting        | Range      | Description                                               |
|----------------|------------|-----------------------------------------------------------|
| Max Volume     | 0% – 100%  | How loud the speaker is at the source                     |
| Max Range      | 1 – 512    | Maximum distance (in blocks) the audio can be heard       |
| Audio Dropoff  | 0% – 100%  | 0% = uniform volume at all distances; 100% = linear fade  |

## Adding Audio Files

### In-Game Upload
Right-click a Speaker, click the upload button, and select an audio file. Files are validated and saved automatically.

### Manual Installation
Audio files are stored in `simply_speakers_audios/` inside your world's save folder.

1. Generate a UUID (e.g. from [uuidgenerator.net](https://www.uuidgenerator.net/)).
2. Rename your `.mp3` or `.wav` file to `<your-uuid>.mp3` and place it in the `simply_speakers_audios/` folder.
3. Add an entry to `audio_manifest.json`:
```json
{
  "your-uuid": {
    "uuid": "your-uuid",
    "originalFilename": "your-song.mp3"
  }
}
```

## Configuration

Edit the mod config file to adjust:
- `speakerRange`: Default range for new speakers (1–512, default: 64)
- `disableUpload`: Disable the in-game upload feature
- `maxUploadSize`: Maximum file size for uploads in bytes
- `debugLogging`: Enable verbose logging for troubleshooting

## Dependencies

- **Architectury API** (matching your Minecraft version and loader)
- **Fabric API** (for Fabric versions)

## Building from Source

1. Clone the repository.
2. Run `gradlew.bat build` (or `./gradlew build` on Linux/Mac).
3. JAR files are located in each subproject's `build/libs/` directory.

To target a specific module:
```bash
gradlew.bat :neoforge-1.21.1:build
gradlew.bat :fabric-1.20.1:build
```

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
| `shared-1.21plus/`  | Shared code between 1.21.1 and 26.1.2 NeoForge       |
| `shared-neoforge/`  | Shared NeoForge platform code                        |
| `fabric-1.20.1/`   | Fabric 1.20.1 loader entry point                     |
| `fabric-1.21.1/`   | Fabric 1.21.1 loader entry point                     |
| `forge-1.20.1/`    | Forge 1.20.1 loader entry point                      |
| `neoforge-1.21.1/` | NeoForge 1.21.1 loader entry point                   |
| `neoforge-26.1.2/` | NeoForge 26.1.2 loader entry point (standalone)      |
| `tools/`            | Automated test scripts                               |

## Changelog

See [changelog/changelog.txt](changelog/changelog.txt) for the full version history.

## Contributing

Contributions are welcome! Please feel free to submit a pull request or open an issue.
