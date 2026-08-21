# Simply Speakers → OpenUI MC Migration Plan

## Mission

Replace the legacy textured, coordinate-driven Simply Speakers interfaces with OpenUI MC across every currently supported Minecraft/loader target.

The result must be a compact premium audio-control interface: clean library browsing, obvious currently-playing state, polished settings, reliable upload feedback, responsive layout, keyboard accessibility, and live persisted light/dark themes.

## Repositories reviewed

- Consuming mod: `UpperMoon0/Simply-Speakers`, current `main`.
- OpenUI target: `UpperMoon0/OpenUI-MC`, branch `codex/multiloader-build`.

Current UI implementation files:

```text
common-1.20.1/.../client/screens/SpeakerScreen.java
common-1.20.1/.../client/screens/ProxySpeakerScreen.java
common-1.20.1/.../client/gui/widgets/SpeakerAudioList.java
common-1.20.1/.../client/gui/widgets/SettingsSlider.java

common-1.21.1/.../client/screens/SpeakerScreen.java
common-1.21.1/.../client/screens/ProxySpeakerScreen.java
... corresponding version-specific widgets ...

neoforge-26.1.2/.../client/screens/SpeakerScreen.java
neoforge-26.1.2/.../client/screens/ProxySpeakerScreen.java
... corresponding 26.1.2 widget code ...
```

Current target projects include:

- Fabric 1.20.1
- Forge 1.20.1
- Fabric 1.21.1
- NeoForge 1.21.1
- NeoForge 26.1.2

The screen logic is substantially duplicated across Minecraft version source sets. Do not attempt to compile one OpenUI UI class across incompatible Minecraft versions. Keep composition structurally identical, but compile it in the corresponding version source set.

## Behavior that must survive

Speaker screen:

- request audio list when opened
- search/filter audio
- select audio
- delete audio
- display selected/currently playing audio
- local speaker audio selection update
- upload `.mp3`/`.wav`
- honor `Config.disableUpload`
- speaker ID edit/save
- max volume
- max range
- audio dropoff
- loop toggle
- existing packets and client updates
- status feedback

Proxy speaker:

- speaker ID
- max volume
- max range
- audio dropoff
- current client-side preview/update behavior
- existing proxy packet types

Do not change audio streaming/playback, server file storage, packet serialization or Sable compatibility as part of this UI migration.


## OpenUI target and hard constraints

**Source of truth:** `UpperMoon0/OpenUI-MC`, branch `codex/multiloader-build`.

Do **not** implement against OpenUI `main`. Do **not** copy the old Economy-extracted UI classes into the target mod. The migration target is the completed multi-loader OpenUI branch.

OpenUI version on the reviewed branch: `0.0.1`.

Supported OpenUI module mapping:

| Minecraft | Loader | Java | OpenUI project |
|---|---|---:|---|
| 1.20.1 | Fabric | 17 | `:fabric-1.20.1` |
| 1.20.1 | Forge | 17 | `:forge-1.20.1` |
| 1.21.1 | Fabric | 21 | `:fabric-1.21.1` |
| 1.21.1 | NeoForge | 21 | `:neoforge-1.21.1` |
| 26.1.2 | NeoForge | 25 | `:neoforge-26.1.2` |

Important framework behavior already provided by OpenUI:

- `UiScreen` for ordinary screens.
- `UiContainerScreen<M>` for menu/container screens.
- `UiRuntime` for mount/unmount, layout, rendering, input, focus, overlays and native widget ownership.
- `Ui`, `UIComponent`, `HStack`, `VStack`, `Stack`, `Padding`, `Responsive`, `DynamicGrid`, `VirtualList`.
- Reactive state: `Signal<T>`, `ReadableSignal<T>`, `Computed<T>`, `Signals.batch(...)`, closeable `Subscription`.
- Controls: `ButtonWidget`, `TextField`, `Checkbox`, `SwitchControl`, `Slider`, `Select`, `Tabs`, `Table`, `Card`, `Badge`, `Chip`.
- Feedback: `Dialog`, `Toast`, `Tooltip`, `Popover`, `ContextMenu`, `LoadingOverlay`, `Spinner`, `Skeleton`, `EmptyState`, `ErrorBoundary`.
- Data display: `LineChart`, `AreaChart`, `BarChart`, `Sparkline`, `ProgressBar`.
- Themes: `Theme.dark()`, `Theme.light()`, `Theme.highContrast()`.
- Live theme switching: `uiRuntime().theme(newTheme)`; this invalidates paint without requiring a screen restart.
- Stock controls obtain their colors from the runtime theme.
- Custom components must use `theme().colors()` rather than static dark-only constants.
- `TextField` owns its native Minecraft `EditBox`. Never manually call `addRenderableWidget()` for an OpenUI `TextField`.
- OpenUI handles focus traversal and input dispatch. Do not manually forward mouse/keyboard events unless a genuinely custom component requires it.
- OpenUI's 26.1.2 module uses the Minecraft 26 render-state/extractor API. Do not paste a 1.20.1 custom renderer verbatim into 26.1.2.
- `FadeTransition` is not available on 26.1.2. Use no transition, `SlideTransition`, or `ScaleTransition` when the same behavior must work on all supported targets.

### Dependency rule

The consuming loader must depend on the **matching** OpenUI loader/version module. Never compile against one OpenUI module and ship another.

For a multi-target consuming repository, do not use one global generic composite substitution such as:

```groovy
substitute module('com.nstut:openui-mc') using project(':forge-1.20.1')
```

That only selects one OpenUI target for the entire build and is wrong when several loader projects coexist.

For local development, use loader-specific synthetic coordinates in the consuming root `settings.gradle`:

```groovy
includeBuild('../OpenUI-MC') {
    dependencySubstitution {
        substitute module('com.nstut:openui-mc-fabric-1.20.1') using project(':fabric-1.20.1')
        substitute module('com.nstut:openui-mc-forge-1.20.1') using project(':forge-1.20.1')
        substitute module('com.nstut:openui-mc-fabric-1.21.1') using project(':fabric-1.21.1')
        substitute module('com.nstut:openui-mc-neoforge-1.21.1') using project(':neoforge-1.21.1')
        substitute module('com.nstut:openui-mc-neoforge-26.1.2') using project(':neoforge-26.1.2')
    }
}
```

Each consuming loader project then uses only its matching coordinate, for example:

```groovy
modImplementation 'com.nstut:openui-mc-fabric-1.20.1:0.0.1'
```

or the equivalent dependency configuration for that loader/build system.

If OpenUI is published to a Maven repository before this migration is implemented, replace the synthetic composite setup with the real published loader-specific coordinates. **Do not change the loader-to-loader mapping.**

OpenUI is a required **client-side library mod**. Do not shadow/relocate the OpenUI classes into these mods during the first migration. Add `openui_mc` to each loader's mod metadata as a required client dependency and ship the matching OpenUI jar alongside the consuming mod. Bundling can be evaluated separately after the migration is stable.

### Cross-mod visual language

All three migrations must follow the same suite-level visual rules:

1. Texture-free UI shell. Remove old GUI background PNG use from migrated screens.
2. Use semantic theme colors; no screen-level RGB palette constants.
3. Use OpenUI spacing/radius/theme tokens instead of hand-picked per-screen geometry.
4. Dark and light themes must have feature parity.
5. Every top-level screen must expose the same compact theme toggle in the header.
6. Theme changes must apply immediately to the open screen.
7. Theme choice must persist across Minecraft restarts.
8. Primary action = OpenUI primary button.
9. Secondary/navigation action = secondary/ghost style.
10. Destructive action = danger style and, for irreversible actions, a confirmation dialog.
11. Selection = semantic primary/accent state; do not encode selection only with text color.
12. Status = `Badge`, `Toast`, `EmptyState`, `LoadingOverlay`, or semantic text, not ad-hoc raw draw calls.
13. Use keyboard focus, Tab/Shift+Tab and arrow-key behavior supplied by OpenUI.
14. Hover state must come from OpenUI controls unless a custom data card explicitly implements it.
15. Narrow UI scales must reflow via `Responsive`, not clip.
16. Do not use absolute coordinates to build ordinary control layout.
17. Absolute positioning is allowed only for actual render content that intrinsically needs it, such as a fluid fill or image texture inside a custom component.
18. No UI polling in `render()` when the same data can be represented as a signal and updated by packet/client events.
19. No raw thread sleeps to "refresh later" as a final implementation.
20. No packet/business-rule changes merely to make the UI migration easier.

### Shared theme-toggle implementation pattern

Each mod should expose one persisted client preference:

```text
ui.theme = dark | light
```

Use an enum, not a boolean, so a future `system` or `high_contrast` option can be added without a file-format migration.

Suggested shape:

```java
public enum UiThemeMode {
    DARK,
    LIGHT;

    public Theme toOpenUiTheme() {
        return this == LIGHT ? Theme.light() : Theme.dark();
    }

    public UiThemeMode next() {
        return this == DARK ? LIGHT : DARK;
    }
}
```

Each screen owns a signal seeded from the persisted value:

```java
private final Signal<UiThemeMode> themeMode =
        Signals.of(ClientUiPreferences.getThemeMode());
```

After `super.init()` creates the OpenUI runtime:

```java
@Override
protected void init() {
    super.init();
    uiRuntime().theme(themeMode.get().toOpenUiTheme());
}
```

The header toggle must:

1. Compute `next = themeMode.get().next()`.
2. `themeMode.set(next)`.
3. Persist `next` to the mod's client preference file.
4. Call `uiRuntime().theme(next.toOpenUiTheme())`.
5. Never close/reopen the screen just to update colors.

Use a supplier-backed button label so the same component shows the current mode, for example `"☀ Light"` while dark mode is active if the action means "switch to light", or `"☾ Dark"` while light mode is active if the action means "switch to dark". Include a tooltip such as `Switch to light theme`.

Do not place theme state in a server config and do not sync it over the network.


## Target client architecture

Use the same conceptual architecture in all three Minecraft version source sets:

```text
.../client/ui/
  SimplySpeakersUiThemeMode.java        NEW
  SimplySpeakersUiPreferences.java      NEW
  SimplySpeakersUiScreen.java           NEW base
  SpeakerUiState.java                   NEW or nested state holder
  SpeakerAudioRow.java                  OPTIONAL custom composition, not widget
```

Then rewrite:

```text
SpeakerScreen.java
ProxySpeakerScreen.java
```

After parity, delete the custom GUI-only widgets made obsolete by OpenUI:

```text
SpeakerAudioList.java
SettingsSlider.java
```

Delete GUI textures only after verifying they are not used anywhere else:

```text
textures/gui/speaker.png
textures/gui/proxy_speaker.png
```

### `SimplySpeakersUiPreferences`

Create a tiny client-only properties file:

```text
config/simplyspeakers-ui.properties
```

Property:

```text
ui.theme=dark
```

Requirements:

- enum value `DARK` / `LIGHT`
- invalid/missing -> dark
- synchronized load/save
- preserve unknown future properties
- no server sync
- no dependency on speaker block entity state

Use the loader/platform config directory if there is already a suitable platform service. If not, use a version-appropriate client game/config path. Keep this implementation in each Minecraft-version source set rather than polluting the pure Java `common` module with Minecraft/loader dependencies.

### `SimplySpeakersUiScreen`

Extend OpenUI `UiScreen`.

Responsibilities:

- apply stored theme in `init()` after `super.init()`
- expose the theme mode signal
- expose a consistent theme toggle builder
- optionally provide a common shell/header builder
- no speaker-specific packets

Do not subclass vanilla `Screen` directly for migrated screens.

## Multi-loader dependency wiring

In the Simply Speakers root `settings.gradle`, preserve the existing special logic for targeted `neoforge-26.1.2` invocations.

Add the loader-specific OpenUI composite substitutions. They may live outside the `onlyNeoForge2612` project-inclusion condition because the included OpenUI build resolves its own projects.

Each loader project needs its matching dependency:

```text
fabric-1.20.1  -> com.nstut:openui-mc-fabric-1.20.1:0.0.1
forge-1.20.1   -> com.nstut:openui-mc-forge-1.20.1:0.0.1
fabric-1.21.1  -> com.nstut:openui-mc-fabric-1.21.1:0.0.1
neoforge-1.21.1-> com.nstut:openui-mc-neoforge-1.21.1:0.0.1
neoforge-26.1.2-> com.nstut:openui-mc-neoforge-26.1.2:0.0.1
```

For Architectury Loom projects, use the normal mod dependency configuration (`modImplementation` or the repository's equivalent) so OpenUI is available to dev runs.

For the standalone 26.1.2 ModDevGradle project, use the matching implementation/runtime mod dependency mechanism supported by that project. Do not accidentally pull the 1.21.1 NeoForge OpenUI jar into 26.1.2.

Update every loader metadata file to require `openui_mc` client-side.

Dedicated-server test is mandatory because the common mod must not eagerly load the client OpenUI classes.

## Visual design

### Speaker screen

Target wide layout:

```text
┌──────────────────────────────────────────────────────┐
│ Speaker                                    ◐ Theme   │
│ <current status / currently playing>                 │
├──────────────────────────────────────────────────────┤
│ [ Audio ] [ Settings ]                               │
├──────────────────────────────────────────────────────┤
│                                                      │
│ active tab                                           │
│                                                      │
└──────────────────────────────────────────────────────┘
```

Audio tab:

```text
[ Search audio…                                    ] [Upload]

┌──────────────────────────────────────────────────────┐
│ Song A.mp3                      [Playing]             │
│ Song B.wav                                  [Select] │
│ Song C.mp3                         [Select] [Delete]  │
│ ...                                                  │
└──────────────────────────────────────────────────────┘
```

Settings tab:

```text
Speaker ID
[ living_room _________________________________ ] [Save]

Maximum volume                                      75%
[---------------------------●-----------------------]

Maximum range                                    64 blocks
[-----------------------●---------------------------]

Audio falloff                                      50%
[-------------------●-------------------------------]

Loop playback                                      [ toggle ]
```

Proxy screen:

- same visual shell and theme toggle
- title `Proxy Speaker`
- one Settings card
- no empty fake Audio tab

### Premium interaction rules

- Currently-playing row uses a semantic `Badge` and subtle selected surface, not bright green filename text alone.
- Delete uses danger styling.
- Upload is primary only when it is the main action; otherwise secondary.
- Long filenames must ellipsize or expose tooltip rather than continuously marquee every row.
- If a marquee is retained, use a reusable OpenUI-aware component, not duplicate timing math in screen/list.
- A selected-but-not-playing item and a playing item are distinct states.
- Settings labels show their numeric value beside the slider.
- Sliders must be keyboard adjustable.
- No texture background.
- No vanilla gray buttons.
- No hard-coded pixel palette.
- Keep layout compact; this is an in-world device controller, not a full-screen desktop application.

## Reactive state model

In each `SpeakerScreen`, create signals after the block entity has been resolved:

```java
Signal<SpeakerTab> tab = Signals.of(SpeakerTab.AUDIO);
Signal<String> search = Signals.of("");
Signal<List<AudioFileMetadata>> audioFiles = Signals.of(List.of());
Signal<String> selectedAudioId = Signals.of(initialAudioId);
Signal<String> playingAudioId = Signals.of(initialAudioId);
Signal<String> speakerId = Signals.of(initialSpeakerId);
Signal<Double> maxVolume = Signals.of((double) initialMaxVolume);
Signal<Double> maxRange = Signals.of((double) initialMaxRange);
Signal<Double> audioDropoff = Signals.of((double) initialDropoff);
Signal<Boolean> looping = Signals.of(initialLooping);
Signal<UploadState> uploadState = Signals.of(UploadState.IDLE);
Signal<Component> status = Signals.of(Component.empty());
```

Use a computed list:

```java
Computed<List<AudioFileMetadata>> filteredAudio = Signals.computed(() -> {
    String q = search.get().trim().toLowerCase(Locale.ROOT);
    if (q.isEmpty()) return audioFiles.get();
    return audioFiles.get().stream()
        .filter(a -> a.getOriginalFilename().toLowerCase(Locale.ROOT).contains(q))
        .toList();
});
```

Use stable `VirtualList` keys:

```java
Ui.list(filteredAudio, this::buildAudioRow)
  .key(AudioFileMetadata::getUuid)
  .itemHeight(...)
  .gap(...);
```

Do not reset selection just because the list is filtered unless the old semantic explicitly requires it. The selected ID should remain valid even when hidden by the current search.

## Packet/state bridge

The UI state and packet state must have one direction per event.

User interaction:

```text
OpenUI control -> local signal/client preview -> existing C2S packet
```

Server update:

```text
existing S2C handler -> signal update on client thread -> OpenUI repaints/rebuilds affected component
```

Do not make `render()` repeatedly read the block entity and overwrite signals every frame.

If current S2C handlers only know how to call methods on the open `SpeakerScreen`, retain public handler methods such as:

```java
public void updateAudioList(List<AudioFileMetadata> files)
public void updateSpeakerState(...)
public void setStatusMessage(Component message)
```

but make them write signals. Do not mutate removed vanilla widgets.

## SpeakerScreen migration — exact sequence

### S1 — switch base

Change from vanilla `Screen` to `SimplySpeakersUiScreen`.

Remove:

- `BACKGROUND_TEXTURE`
- fixed texture blit in render
- `audioTabButton` / `settingsTabButton` vanilla button fields
- `AudioTabContent` visibility holder
- `SettingsTabContent` visibility holder
- manual `updateVisibility()`
- direct `addRenderableWidget(...)`
- manual label drawing
- manual search-bar tick
- manual widget event forwarding if any

Keep:

- `blockEntityPos`
- speaker lookup
- packet calls
- file-dialog call
- upload validation
- public methods called by packet handlers

### S2 — build header and Tabs

Enum:

```java
enum SpeakerTab { AUDIO, SETTINGS }
```

Use:

```java
Ui.tabs(tab)
    .tab(SpeakerTab.AUDIO, Component.translatable("gui.simplyspeakers.tab.audio"))
    .tab(SpeakerTab.SETTINGS, Component.translatable("gui.simplyspeakers.tab.settings"));
```

Build content with `Ui.switcher(tab)` or a component that returns the correct tree for each tab.

Header contains:

- title
- currently-playing/status area
- theme toggle

### S3 — replace `SpeakerAudioList`

Do not adapt the old `AbstractWidget`. Replace it.

Use `VirtualList<AudioFileMetadata>`.

Each row should be a `Card`/row composition:

Left:

- compact speaker/music glyph or no icon
- filename
- optional muted metadata if available

Right:

- `Playing` badge when `uuid == playingAudioId`
- `Selected` visual state when selected but not playing
- Select button if selecting this item is meaningful
- Delete danger button

Preserve current action semantics:

- Select:
  - update local speaker audio id/name
  - update `selectedAudioId`
  - send `SelectAudioPacketC2S`
- Delete:
  - open confirmation dialog
  - on confirm send `DeleteAudioPacketC2S`
  - do not immediately fabricate server success unless the existing protocol expects optimistic removal

Empty list:

```text
No audio uploaded
[Upload audio]    (if uploads enabled)
```

Search-no-result:

```text
No audio matches "<query>"
[Clear search]
```

These are different empty states.

### S4 — Upload

Upload button keeps existing `Services.CLIENT.openFileDialog("mp3,wav", ...)`.

Before sending:

- lowercase extension check
- `.mp3` or `.wav` only
- use current server size validation/protocol
- if invalid -> OpenUI toast/error status
- if `Config.disableUpload` -> do not render upload controls at all

Represent progress/state if the current upload callbacks expose it:

```java
enum UploadState { IDLE, PICKING, UPLOADING, SUCCESS, ERROR }
```

If only success/failure status exists, still use `status`/Toast instead of a manual marquee line.

Do not block the render thread.

### S5 — Speaker ID

Use OpenUI `TextField`.

- Signal seeded from current speaker id.
- Save button sends `SetSpeakerIdPacketC2S`.
- Update local speaker exactly as current version expects (`setSpeakerId` / client variant).
- Trim only if current server semantics allow it.
- Keep tooltip/help text.
- Disable Save when no change if straightforward.

### S6 — sliders

Delete `SettingsSlider`.

OpenUI `Slider` binds directly to `Signal<Double>`.

For each slider:

- create signal from current BE value **before** subscribing
- mount a closeable subscription only after initialization
- subscription sends packet on user change
- close subscription when screen unmounts/removed

Volume:

```text
range 0.0 .. 1.0
display rounded percentage
packet float
```

Range:

```text
range 1 .. Config.speakerRange
display integer blocks
packet integer
```

Dropoff:

```text
range 0.0 .. 1.0
display rounded percentage
packet float
```

Because slider signals can change for reasons other than user input, guard programmatic server-sync writes from echoing packets if such sync exists. Use a small `applyingRemoteState` boolean or a one-way update method.

Do not send packets from a render callback.

### S7 — Loop

Use `SwitchControl` bound to `Signal<Boolean>` plus text `Loop playback`.

On local change:

- update speaker client looping state
- send `ToggleLoopPacketC2S`

Again, guard remote/programmatic sync from echoing if S2C state updates write the signal.

### S8 — status text

Remove the screen-specific `getMarqueeOffset` timing code if possible.

Preferred hierarchy:

- Playing state -> Badge + filename in header.
- Upload/result errors -> Toast.
- Persistent operational status -> wrapped/ellipsized text with tooltip.

Only keep marquee behavior if there is a verified requirement for long server status messages.

## ProxySpeakerScreen migration

Use the same themed base.

Signals:

```java
Signal<String> speakerId
Signal<Double> maxVolume
Signal<Double> maxRange
Signal<Double> audioDropoff
```

Use the exact corresponding proxy packets:

- `SetSpeakerIdPacketC2S`
- `UpdateProxyMaxVolumePacketC2S`
- `UpdateProxyMaxRangePacketC2S`
- `UpdateProxyAudioDropoffPacketC2S`

Keep current local preview updates:

- `setSpeakerIdClient`
- `setMaxVolumeClient`
- `setMaxRangeClient`
- `setAudioDropoffClient`

Do not route proxy settings through main-speaker packets.

Visual structure:

```text
Proxy Speaker                                      Theme

Linked Speaker
[ speaker_id __________________________________ ] [Save]

Playback Envelope
Maximum volume       value
slider
Maximum range        value
slider
Audio falloff        value
slider
```

Add muted helper copy explaining that the proxy follows the linked speaker's selected audio, if accurate to the mod's existing behavior.

## Version porting strategy

Do not migrate all versions simultaneously.

### Phase A — 1.20.1

1. Add OpenUI to Fabric 1.20.1 and Forge 1.20.1 build paths.
2. Rewrite `common-1.20.1` SpeakerScreen.
3. Rewrite `common-1.20.1` ProxySpeakerScreen.
4. Remove 1.20.1 legacy audio list/slider only after both loader clients pass.
5. Test Fabric + Forge.

### Phase B — 1.21.1

Copy the **composition structure**, not compiled classes.

1. Add matching 1.21.1 OpenUI dependencies.
2. Port state/view tree to `common-1.21.1`.
3. Keep the 1.21.1 networking/API calls already used there.
4. Test Fabric 1.21.1.
5. Test NeoForge 1.21.1.

### Phase C — 26.1.2

1. Add only `openui-mc-neoforge-26.1.2`.
2. Port the same composition to the standalone 26.1.2 project.
3. Do not copy `GuiGraphics` custom render code from 1.20/1.21.
4. Prefer stock OpenUI components so the framework handles `GuiGraphicsExtractor`.
5. If a custom component remains, implement it using the 26.1.2 OpenUI component/render signature.
6. Do not use `FadeTransition`.
7. Test the dedicated 26.1.2 client and server.

## Localization requirements

Replace hardcoded UI strings with translation keys, including old list literals such as `No audio uploaded`, `Delete`, `Select`.

Add keys for:

- theme switch/light/dark
- playing
- selected
- no audio uploaded
- no search matches
- clear search
- delete confirmation title/body
- upload states/errors
- linked speaker helper text
- max volume/range/dropoff value text

Reuse existing translation keys where they already exist.

## Delete confirmation

Deleting media is destructive. Use an OpenUI `Dialog`.

Dialog:

```text
Delete audio?

"<filename>" will be removed from the server.

[Cancel] [Delete]
```

Delete button = danger.

Do not delete on first click.

If the server currently deliberately allows one-click delete and product behavior must remain exact, the confirmation can be feature-flagged, but premium default should be confirmation.

## Responsive behavior

Recommended breakpoints are conceptual; use `Ui.responsive(...)` and actual available width.

Wide:

- header
- tabs
- search + upload on same row
- settings labels/value aligned

Narrow:

- upload below search
- action buttons may collapse to icon + tooltip
- settings value appears below label if needed
- no horizontal clipping

At minimum test GUI scale 1 through 4 where available.

## Resource cleanup

After each version has migrated, search for:

```text
SpeakerGuiConstants
SpeakerAudioList
SettingsSlider
textures/gui/speaker.png
textures/gui/proxy_speaker.png
```

Delete only members/resources with zero remaining functional references.

`SpeakerGuiConstants` may become almost entirely obsolete. Keep it only if non-UI code uses it; otherwise remove it after all versions are migrated.

## Performance/lifecycle requirements

- `VirtualList` key = audio UUID.
- Do not instantiate vanilla `Button` objects inside every row render.
- Search filtering is computed on signal changes.
- Do not scan the whole block entity every render.
- Close slider/toggle subscriptions.
- Do not retain a closed screen in static packet callbacks.
- Repeated screen open/close must release native OpenUI text fields.
- No custom scroll state for the audio list after migration.
- No per-frame creation of temporary action buttons.
- Theme toggling must request repaint only, not reconstruct network state.

## Test matrix

Required:

| Target | Speaker | Proxy | Dark | Light | Toggle live | Dedicated server |
|---|---:|---:|---:|---:|---:|---:|
| Fabric 1.20.1 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Forge 1.20.1 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| Fabric 1.21.1 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| NeoForge 1.21.1 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| NeoForge 26.1.2 | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |

Interaction cases:

- zero audio files
- one audio file
- hundreds of audio files
- very long filename
- Unicode filename
- search exact/partial/no result
- select
- delete cancel
- delete confirm
- currently playing row
- upload disabled
- invalid extension
- valid mp3
- valid wav
- file dialog cancel
- server/upload failure path
- speaker id save
- slider mouse drag
- slider keyboard arrows
- range limit
- loop toggle
- proxy settings
- resize window while open
- GUI scale change after reopening
- Tab/Shift+Tab
- Escape
- rapid repeated open/close

## Implementation order

1. Wire OpenUI dependencies per loader.
2. Add required mod metadata dependencies.
3. Add theme preference + tests in 1.20.1.
4. Add 1.20.1 themed UiScreen base.
5. Migrate ProxySpeakerScreen 1.20.1 first.
6. Verify both 1.20.1 loaders.
7. Migrate SpeakerScreen shell/tabs.
8. Replace SpeakerAudioList with VirtualList.
9. Replace SettingsSlider.
10. Replace status/marquee UX.
11. Verify packet handler updates.
12. Remove 1.20.1 dead GUI classes/assets.
13. Port theme base/preferences to 1.21.1.
14. Port Proxy then Speaker to 1.21.1.
15. Verify Fabric + NeoForge 1.21.1.
16. Port to 26.1.2.
17. Verify NeoForge 26.1.2 client and server.
18. Run repository-wide legacy UI grep.
19. Polish responsive spacing/animations only after parity.

## Definition of done

- [ ] Every SpeakerScreen is an OpenUI screen.
- [ ] Every ProxySpeakerScreen is an OpenUI screen.
- [ ] All five loader/version artifacts depend on matching OpenUI.
- [ ] Required `openui_mc` runtime metadata is present.
- [ ] Dark/light toggle exists on both screen types.
- [ ] Toggle changes current screen immediately.
- [ ] Theme persists after restart.
- [ ] `SpeakerAudioList` is no longer used.
- [ ] `SettingsSlider` is no longer used.
- [ ] No migrated screen blits `speaker.png` or `proxy_speaker.png`.
- [ ] No vanilla gray UI buttons/edit boxes are manually managed.
- [ ] Audio list is virtualized and keyed by UUID.
- [ ] Search is reactive.
- [ ] Current playing/selected states are distinct and clear.
- [ ] Upload-disabled configuration is honored.
- [ ] All existing speaker/proxy packet semantics are preserved.
- [ ] Slider subscriptions do not echo remote state.
- [ ] No screen leaks subscriptions/native widgets.
- [ ] All supported clients and dedicated servers boot.

## Explicit anti-patterns for the implementation model

Do **not**:

- make OpenUI optional with a fallback vanilla screen
- compile 1.20 OpenUI classes into 1.21/26
- reuse one loader's OpenUI jar for another loader
- create vanilla Buttons inside a list's `render()` method
- keep manual scrollbar math
- keep fixed texture coordinates
- send slider packets from `render()`
- put UI theme in server config
- use a boolean persisted theme value
- silently remove upload/config behavior
- rewrite audio networking/playback
- use `Thread.sleep()` for UI timing
- add decorative animations before behavior is correct
