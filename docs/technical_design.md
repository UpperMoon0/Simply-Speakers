# Simply Speakers - Technical Design Document

## Overview

This document provides a comprehensive technical overview of the Simply Speakers mod, detailing the architecture, components, and interactions of the speaker and audio system in Minecraft.

## Linked Controllers and Guide

`RedstoneControllerBlockEntity` stores a dimension-local Speaker ID, one `ControllerAction`, optional proxy coordinates, and the configuring player's UUID. It samples the strongest input from any face. Pulse jobs act only on a zero-to-positive transition; enabled and volume jobs reconcile continuous input. Saved signal state prevents a held input from becoming a new pulse after reload. Configuration validates distance, interaction protection, controller ownership, loaded proxy identity, and network control permission. Every action rechecks network permission. Lookup never creates an unknown network or loads a proxy chunk.

Network transport uses `ServerSpeakerControlService`. Announcements require an owned local file with known duration and mark only that playback occurrence as one-shot: packets suppress looping and completion stops instead of consuming a playlist or queue. Normal playback starts clear the flag. Volume changes resynchronize active listeners. Speakers and proxies ignore local redstone input. Proxies default to enabled, and loaded controllers apply transient output intent. Unloading or removing the last proxy playback controller restores enabled output; volume overrides preserve and restore configured volume.

The Patchouli book definition is in `data/simplyspeakers/patchouli_books/guide/book.json`; its 23 chapters and four categories are client resources under `assets/simplyspeakers/patchouli_books/guide/en_us`. Patchouli adds the book to `simplyspeakers:tab`, using the generated transparent inventory texture. Minecraft 1.20.1 uses Patchouli's shaped book recipe serializer; 1.21.1 and 26.1.2 use the book data component, with 26.1.2 item definitions and string ingredient syntax. All development runtimes include the matching Patchouli artifact, and all loader metadata declares it required. Live verification compiles the actual book contents and exercises the registered controller block.

## System Architecture

The Simply Speakers mod implements a distributed audio system using a central registry pattern with speaker entities that can be synchronized across multiple locations. The system consists of several key components:

1. **Speaker Blocks** - Primary audio controllers
2. **Proxy Speaker Blocks** - Synchronized audio players
3. **Audio Management System** - File handling and caching
4. **Network Layer** - Communication between client and server
5. **Registry System** - Centralized state management

## Core Components

### SpeakerState
The `SpeakerState` class represents the state of a speaker network and holds all information needed to manage speaker playback:
- `audioId`: UUID of the selected audio file
- `audioFilename`: Original filename of the audio file
- `isPlaying`: Current playback status
- `isLooping`: Legacy compatibility alias for active playlist Repeat Track; old flags migrate once
- `playbackStartTick`: Game tick when playback started
- `maxVolume`: Maximum volume level (0.0 to 1.0)
- `maxRange`: Maximum range for audio playback (1 to Config.MAX_RANGE)
- `audioDropoff`: Audio dropoff factor (0.0 to 1.0)

### SpeakerRegistry
The `SpeakerRegistry` implements a centralized registry system for tracking speakers by their IDs and managing their state:
- Maps speaker IDs to sets of speaker positions
- Maps speaker IDs to sets of proxy speaker positions
- Maps levels to position-to-speaker ID mappings
- Manages centralized speaker state storage
- Handles persistence through JSON serialization

### AudioFileManager
The `AudioFileManager` handles all audio file operations:
- Manages the audio directory structure
- Maintains an audio manifest (JSON file)
- Validates and saves uploaded audio files
- Handles chunked file transfers
- Provides audio file metadata

### ClientAudioPlayer
The `ClientAudioPlayer` manages client-side audio playback:
- Uses OpenAL for audio streaming
- Implements buffered streaming for continuous playback
- Handles MP3/WAV decoding
- Manages audio caching
- Controls volume based on player distance

## Block Entities

### SpeakerBlockEntity
The main speaker block entity that controls audio playback:
- Manages the speaker state through the registry
- Emits network playback without local redstone input; linked Speaker Controllers handle automation
- Notifies proxy speakers of state changes
- Manages player listening states for range-based audio

### ProxySpeakerBlockEntity
A synchronized speaker that mirrors a main speaker's playback:
- Links to a main speaker via shared speaker ID
- Maintains its own playing state (can be individually controlled)
- Synchronizes playback position with the main speaker
- Manages player listening states for range-based audio
- Supports configurable audio settings (maxVolume, maxRange, audioDropoff)

## Network Communication

The mod uses a packet-based communication system with both client-to-server (C2S) and server-to-client (S2C) packets:

### Client-to-Server Packets
- `LoadAudioCallPacketC2S`: Requests audio loading
- `AudioPathPacketC2S`: Sends audio file path
- `ToggleLoopPacketC2S`: Compatibility packet mapping to Repeat Track/None
- `RequestUploadAudioPacketC2S`: Initiates file upload
- `UploadAudioDataPacketC2S`: Sends file data chunks
- `RequestAudioListPacketC2S`: Requests available audio files
- `SelectAudioPacketC2S`: Selects an audio file for playback
- `RequestAudioFilePacketC2S`: Requests an audio file download
- `StopPlaybackPacketC2S`: Requests playback stop
- `SetSpeakerIdPacketC2S`: Sets speaker ID
- `UpdateMaxVolumePacketC2S`: Updates maximum volume setting
- `UpdateMaxRangePacketC2S`: Updates maximum range setting
- `UpdateAudioDropoffPacketC2S`: Updates audio dropoff setting
- `UpdateProxyMaxVolumePacketC2S`: Updates proxy speaker maximum volume setting
- `UpdateProxyMaxRangePacketC2S`: Updates proxy speaker maximum range setting
- `UpdateProxyAudioDropoffPacketC2S`: Updates proxy speaker audio dropoff setting

### Server-to-Client Packets
- `StopAudioPacketS2C`: Stops audio playback
- `PlayAudioPacketS2C`: Starts audio playback
- `SpeakerBlockEntityPacketS2C`: Updates speaker block entity
- `RespondUploadAudioPacketS2C`: Responds to upload requests
- `AcknowledgeUploadPacketS2C`: Acknowledges file upload
- `SendAudioListPacketS2C`: Sends audio file list
- `SendAudioFilePacketS2C`: Sends audio file data
- `SpeakerStateUpdatePacketS2C`: Updates speaker state

## Audio System Implementation

### File Management
1. Audio files are stored in a world-specific directory: `simply_speakers_audios`
2. Each file is renamed with a UUID for internal reference
3. A manifest file (`audio_manifest.json`) tracks file metadata
4. Files are validated for MP3/WAV format support

### Client-Side Playback
1. Audio files are cached in a client directory: `simply_speakers_cache`
2. OpenAL is used for audio streaming with buffered playback
3. MP3 files are decoded using the JLayer library
4. WAV files are processed through Java's AudioSystem
5. Audio is converted to PCM format for OpenAL compatibility
6. Volume is adjusted based on player distance from speakers
7. Volume is further adjusted based on speaker settings (maxVolume, maxRange, audioDropoff)

### Synchronization System
1. Speakers and proxy speakers are linked via shared speaker IDs
2. The main speaker controls the playback state
3. Proxy speakers mirror the main speaker's playback with position synchronization
4. State changes are broadcast to all linked proxy speakers
5. Redstone power controls individual speaker playback

## Data Flow

### Audio Upload Process
1. Client requests upload through UI
2. Server validates file size and type
3. Server approves upload and specifies chunk size
4. Client sends file data in chunks
5. Server reassembles and saves the file
6. Server updates the audio manifest
7. Server notifies client of successful upload

### Audio Playback Process
1. User selects audio file in speaker UI
2. Server updates speaker state with selected audio
3. Server notifies all linked proxy speakers
4. When playback starts, server sends play packets to nearby players
5. Clients receive play packets and start streaming audio
6. Clients manage volume based on player position

### Proxy Speaker Synchronization
1. Proxy speaker is linked to main speaker via speaker ID
2. When main speaker starts playback, it notifies all linked proxy speakers
3. Proxy speakers receive state updates and begin playback at the correct position
4. When main speaker stops, all proxy speakers stop
5. Individual proxy speakers can be enabled or muted by linked Speaker Controllers; direct block power is ignored

## Performance Considerations

### Memory Management
- Streaming audio uses buffered playback to minimize memory usage
- Audio files are cached on the client to reduce server requests
- Registry data is persisted to disk to survive server restarts
- Player listening states are tracked to minimize packet sending

### Network Optimization
- Audio files are transferred in chunks to prevent network congestion
- State updates are only sent to relevant players (within range)
- Registry updates are batched to reduce network overhead
- Redundant packet sending is minimized through state tracking

### Audio Streaming
- OpenAL sources are managed in separate threads to prevent blocking
- Buffer underruns are handled gracefully with automatic restart
- Audio decoding is optimized for real-time streaming
- Volume updates are batched to reduce OpenAL calls

## Error Handling

### File Operations
- Invalid file types are rejected during upload
- File size limits prevent server overload
- Missing files are handled gracefully
- Corrupted audio files are detected during playback

### Network Issues
- Disconnected clients have their resources cleaned up
- Failed packet deliveries are logged but don't crash the system
- Timeout mechanisms prevent hanging operations
- Partial transfers are resumed when possible

### Audio Playback
- Unsupported audio formats are detected and reported
- OpenAL errors are caught and logged
- Buffer allocation failures are handled gracefully
- Streaming thread crashes don't affect other speakers

## Extensibility

### Adding New Audio Formats
1. Extend the `AudioFileManager` validation
2. Update the `ClientAudioPlayer` decoding logic
3. Add format-specific handling in the streaming system

### Adding New Speaker Types
1. Create new block and block entity classes
2. Register the new block in `BlockRegistries`
3. Add new packet types if needed for special behavior
4. Update the registry system if new linking mechanisms are needed

## Configuration

The system supports several configurable parameters:
- `speakerRange`: Distance at which audio can be heard
- `maxUploadSize`: Maximum file size for uploads
- `disableUpload`: Disables the upload feature entirely
- `allowRemoteStreams`: Whether clients may stream direct HTTP(S) audio URLs through this server; independent of `disableUpload`, and off by default

### Speaker Settings
Each speaker now supports additional configurable parameters:
- `maxVolume`: Controls the maximum volume level (0% to 100%)
- `maxRange`: Controls the maximum range at which audio can be heard (1 to 512 blocks)
- `audioDropoff`: Controls how audio volume decreases with distance (0% = no dropoff, 100% = linear dropoff)

### Proxy Speaker Settings
Proxy speakers now support the same configurable parameters as main speakers:
- `maxVolume`: Controls the maximum volume level (0% to 100%)
- `maxRange`: Controls the maximum range at which audio can be heard (1 to 512 blocks)
- `audioDropoff`: Controls how audio volume decreases with distance (0% = no dropoff, 100% = linear dropoff)

## Security Considerations

- File uploads are validated for type and size
- Audio files are stored in a dedicated directory
- Client-side file access is restricted to cached files
- Network packets are validated before processing

## Named playlist library

PlayerPlaylistStore persists each player's catalog in the world's player_playlists.json,
using atomic writes. Names are unique without case sensitivity, 1–64 characters, with
limits of 16 lists, 256 entries per list and 512 entries total. Every list is deletable.
Legacy catalogs migrate once to the network owner without copying transient queues.

Explicit Play copies a personal source into SpeakerState, retaining its owner identity.
Transport and temporary requests remain network-specific; browsing is local to the screen.
Catalog edits validate the actor's ownership and target control rights. Deleting a playing
source stops affected playback without deleting recordings or queued requests.

Catalog and runtime snapshots are sent separately to stay within packet limits. Catalog
responses are personalized per recipient; runtime playback is shared with listeners.
Clients and servers must use matching mod versions.

## Controller arbitration

Controller inputs resolve once per server tick before the listener scan. Continuous playback combines powered sources with OR; continuous volume takes the highest requested level. Volume overrides are transient and do not overwrite saved/manual settings. Unloading, removal, retargeting or permission revocation releases contributions. Pulses coalesce to one command per target per tick with Stop > Announcement > Restart > Toggle > Select track > Next > Previous; packed source position breaks ties. Source permissions and exact proxy network membership are revalidated without loading target chunks. Speakers retain inert legacy blockstate/NBT fields for save compatibility.

## Player consistency and area preview

Repeat is authoritative in the active saved playlist. Legacy Loop flags migrate once to Repeat Track; transient decoder-loop flags do not overwrite saved repeat preferences. Natural completion respects Track repeat, while manual Next bypasses it and queued requests interrupt it. Playlist changes resynchronize active decoders when loop behavior changes.

Controller configuration is validated server-side. Speaker block update tags include network name, directionality, cone angle, rear attenuation, and exact Repeat mode, and screen initialization hydrates these values. The area preview uses the same distance/directional gain calculation as playback, emits bounded local colored samples for eight seconds, and clears on world/dimension changes.

## CC:Tweaked reference

See [the ComputerCraft API reference](cc_api.md) for Lua methods, return values,
events, permissions and examples. Main speakers expose `simply_speaker` on
Fabric/Forge 1.20.1 and Fabric/NeoForge 1.21.1. NeoForge 26.1.2 has no CC integration.

## Review corrections after 8537ecde

IPv4-mapped IPv6 stream hosts are checked after hexadecimal expansion, so
compressed, expanded and dotted encodings use the same IPv4 restrictions. The
same policy validates literal URLs and DNS answers at connection time.

Queued playback preserves its canonical cursor when tracks are removed or
swapped. Whole-list replacement remaps the canonical track by key; if removed,
continuation starts after its nearest surviving predecessor. An active queued
request remains distinct from the canonical playlist cursor.

State updates carry an authoritative settings snapshot: display name, legacy
redstone mode, configured and effective controller volume, range, dropoff and
cone settings. Clients apply it to their state replicas. Active audio gains are
updated through the existing same-stream refresh path, preserving decoders and
playback identity. Native redstone settings remain retired in favor of controllers.

ComputerCraft getters use CC's main-thread scheduler, including its completion
wakeup, on all four adapters. The real Lua acceptance program verifies that
queries finish without requiring unrelated events.

Operator commands enforce access policies even on unowned networks. A player setting access claims an unowned network without replacing an existing owner. Console commands can restrict an unowned network to operators; owner/trusted modes require an owner. Operator transport commands use the authenticated command path rather than anonymous automation.

- Preserve legacy audio manifests when duration recovery encounters entries without library metadata. Refresh Play/Pause icons and transport state immediately from authoritative playback updates.

Deleting a recording purges every occurrence from saved player catalogs, runtime playlists and temporary queues. Current playback stops only if it uses the deleted file. Rebuilt remote streams receive a fresh EOF identity, and legacy queues normalize to a bounded list with constant-time head removal. Existing registry settings take precedence over stale block NBT.

Main-speaker emitter eligibility is independent of network transport state, so retained snapshots can resume after chunk unload. Proxy eligibility still follows its local enable state. Control-service operations reject keys outside the supplied level, including fully qualified keys. Audio renames accept at most 256 characters; library getters bound legacy persisted labels to the corresponding packet limits.
