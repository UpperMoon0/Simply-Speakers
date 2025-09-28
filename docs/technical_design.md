# Simply Speakers - Technical Design Document

## Overview

This document provides a comprehensive technical overview of the Simply Speakers mod, detailing the architecture, components, and interactions of the speaker and audio system in Minecraft.

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
- `isLooping`: Loop playback setting
- `playbackStartTick`: Game tick when playback started

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
- Handles redstone power state changes
- Notifies proxy speakers of state changes
- Manages player listening states for range-based audio

### ProxySpeakerBlockEntity
A synchronized speaker that mirrors a main speaker's playback:
- Links to a main speaker via shared speaker ID
- Maintains its own playing state (can be individually controlled)
- Synchronizes playback position with the main speaker
- Manages player listening states for range-based audio

## Network Communication

The mod uses a packet-based communication system with both client-to-server (C2S) and server-to-client (S2C) packets:

### Client-to-Server Packets
- `LoadAudioCallPacketC2S`: Requests audio loading
- `AudioPathPacketC2S`: Sends audio file path
- `ToggleLoopPacketC2S`: Toggles looping state
- `RequestUploadAudioPacketC2S`: Initiates file upload
- `UploadAudioDataPacketC2S`: Sends file data chunks
- `RequestAudioListPacketC2S`: Requests available audio files
- `SelectAudioPacketC2S`: Selects an audio file for playback
- `RequestAudioFilePacketC2S`: Requests an audio file download
- `StopPlaybackPacketC2S`: Requests playback stop
- `SetSpeakerIdPacketC2S`: Sets speaker ID

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
5. Individual proxy speakers can be controlled via redstone power

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

## Security Considerations

- File uploads are validated for type and size
- Audio files are stored in a dedicated directory
- Client-side file access is restricted to cached files
- Network packets are validated before processing