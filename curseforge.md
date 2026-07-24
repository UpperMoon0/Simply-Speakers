Simply Speakers adds functional speaker blocks to Minecraft that can play your own music files! Build a speaker network, synchronize audio across multiple locations, and control playback with redstone.

## Features

### Block Types
- **Speaker**: The main controller that stores and plays audio files. Place it, right-click to open the configuration screen, and start playing music.
- **Proxy Speaker**: Links to a main Speaker by ID, playing the same audio at the same position in a different location. Perfect for multiple rooms or large areas.

### Audio Playback
- Supports **MP3** and **WAV** files
- Upload audio files directly through the in-game UI
- Redstone control - power to play, unpower to stop
- Loop playback with toggle support
- Range-based audio - volume fades with distance from the speaker

### Audio Settings (per speaker)
- **Max Volume** (0-100%): How loud the speaker can be at the source
- **Max Range** (1-512 blocks): How far the audio reaches
- **Audio Dropoff** (0-100%): Controls how volume decreases over distance - 0% means uniform volume, 100% means linear fade

### Manual Audio Installation
Audio files can also be placed manually:
1. Drop .mp3/.wav files into `{world}/simply_speakers_audios/`
2. Add entries to `audio_manifest.json` with a UUID key and original filename
3. Files appear automatically in the speaker GUI

## Supported Platforms
- Fabric 1.20.1
- Forge 1.20.1  
- Fabric 1.21.1
- NeoForge 1.21.1
- NeoForge 26.1.2

## Dependencies
- Architectury API (corresponding version for your Minecraft/loader)

## Getting Started
1. Craft a **Speaker** block
2. Place it in the world
3. Right-click to open the GUI
4. Upload audio files or add them manually
5. Select an audio file and press Play
6. Optionally craft **Proxy Speakers** and set the same Speaker ID to sync audio across locations

## Configuration
Edit the mod config file to adjust:
- `speakerRange`: Default range for new speakers (1-512, default: 64)
- `disableUpload`: Disable the upload feature entirely
- `maxUploadSize`: Maximum file size for uploads (bytes)
- `debugLogging`: Enable verbose logging for troubleshooting
