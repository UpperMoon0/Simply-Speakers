# Settings ownership audit

Audited all fields in Config and every Fabric/Forge/NeoForge config writer.

| Setting | Owner and interface |
| --- | --- |
| Stereo playback | Personal; Settings > Your listening settings. Applies to new playback. |
| Entry/exit fade durations | Personal; same card. Apply immediately, 0 disables that timed transition. |
| Audio cache budget | Personal; same card, 64-4096 MiB. Enforced at download/cache cleanup; the completed track is protected even when larger than the target. |
| Theme | Personal; existing header theme toggle. |
| Volume, range, Distance fade, directionality, cone and rear attenuation | Speaker settings; existing controls and server authority. |
| Linking, ownership and trusted players | Network settings; existing management UI and server authority. |
| speakerRange | Server ceiling for the per-speaker range slider. Administrator configuration. |
| disableUpload, allowRemoteStreams, maxUploadSize | Server upload/network policy. Administrator configuration. |
| debugLogging | Troubleshooting diagnostic; remains administrator configuration. |

Personal listening choices are stored with the existing local UI preferences. They are never
sent as speaker changes and cannot be overwritten by server configuration packets or reloads.
Fabric/Forge/NeoForge no longer generate playback choices in mod configuration. Existing
values are imported once, before Forge/NeoForge removes obsolete config entries. Explicit
personal preferences win over legacy values. Theme and unknown preference keys are preserved.
Read-only users can change their own preferences without management or playback permissions.
