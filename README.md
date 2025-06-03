# Simply Speakers

Simply Speakers is a Minecraft mod that allows players to play custom audio from a URL in-game using a speaker block.

## Features

* **Speaker Block**: A new block that can be placed in the world.
* **Custom Audio**: Play audio from any direct .mp3 or .wav URL. **Note: Only local file URLs are supported. Streaming from internet URLs is not supported.**
* **Cross-Platform**: Supports both Fabric and Forge mod loaders.

## Building from Source

1.Clone the repository.
2.Run `./gradlew build` (or `gradlew.bat build` on Windows) to build the mod.
    * The compiled JAR files will be located in the `build/libs` directory of the forge and fabric subprojects.

## Mod Structure

The project is a multi-loader project structured as follows:

* `common/`: Contains the core logic of the mod, shared between Fabric and Forge.
* `fabric/`: Contains Fabric-specific implementation details and the Fabric mod entry point (`SimplySpeakersFabric.java`).
* `forge/`: Contains Forge-specific implementation details and the Forge mod entry point (`SimplySpeakersForge.java`).

## Dependencies

* Minecraft Version: 1.20.1
* Architectury API
* Fabric Loader (for Fabric version)
* Fabric API (for Fabric version)
* Forge (for Forge version)

## Contributing

Contributions are welcome! Please feel free to submit a pull request or open an issue.
