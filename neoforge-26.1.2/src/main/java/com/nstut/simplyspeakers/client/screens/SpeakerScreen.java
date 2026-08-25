package com.nstut.simplyspeakers.client.screens;

import com.nstut.openui.api.ButtonWidget;
import com.nstut.openui.api.HStack;
import com.nstut.openui.api.Ui;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.VStack;
import com.nstut.openui.controls.Badge;
import com.nstut.openui.controls.Card;
import com.nstut.openui.controls.Dialog;
import com.nstut.openui.controls.EmptyState;
import com.nstut.openui.controls.Slider;
import com.nstut.openui.controls.TextField;
import com.nstut.openui.controls.Toast;
import com.nstut.openui.layout.Justification;
import com.nstut.openui.overlay.OverlayHandle;
import com.nstut.openui.state.Computed;
import com.nstut.openui.state.Signal;
import com.nstut.openui.state.Signals;
import com.nstut.openui.state.Subscription;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.platform.Services;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.client.ui.SimplySpeakersUiScreen;
import com.nstut.simplyspeakers.network.DeleteAudioPacketC2S;
import dev.architectury.networking.NetworkManager;
import com.nstut.simplyspeakers.network.RequestUploadAudioPacketC2S;
import com.nstut.simplyspeakers.network.SelectAudioPacketC2S;
import com.nstut.simplyspeakers.network.SetSpeakerIdPacketC2S;
import com.nstut.simplyspeakers.network.PlaylistSyncPacketS2C;
import com.nstut.simplyspeakers.network.PlaylistControlPacketC2S;
import com.nstut.simplyspeakers.network.RequestPlaylistPacketC2S;
import com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S;
import com.nstut.simplyspeakers.network.TransportControlPacketC2S;
import com.nstut.simplyspeakers.RedstoneMode;
import com.nstut.simplyspeakers.playlist.RepeatMode;
import com.nstut.simplyspeakers.network.ToggleLoopPacketC2S;
import com.nstut.simplyspeakers.network.UpdateAudioDropoffPacketC2S;
import com.nstut.simplyspeakers.network.UpdateMaxRangePacketC2S;
import com.nstut.simplyspeakers.network.UpdateMaxVolumePacketC2S;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Main Speaker screen, migrated to OpenUI.
 *
 * <p>Preserves all existing behaviour: requesting the audio list on open, search/filter,
 * selection, delete (with confirmation), upload, speaker id, volume/range/dropoff sliders and
 * loop toggle. Network packets and client update entry points ({@link #updateAudioList},
 * {@link #refreshFromState}, {@link #setStatusMessage}) are unchanged in signature and now write
 * to reactive signals rather than vanilla widgets.</p>
 */
public class SpeakerScreen extends SimplySpeakersUiScreen {
    private static final int PANEL_WIDTH = 320;

    private enum SpeakerTab { AUDIO, SETTINGS }
    private enum AudioViewState { EMPTY, NO_MATCHES, RESULTS }
    private record AudioRowModel(AudioFileMetadata audio, boolean selected, boolean playing) { }

    private final BlockPos blockEntityPos;
    private SpeakerBlockEntity speaker;

    private final Signal<SpeakerTab> tab = Signals.of(SpeakerTab.AUDIO);
    private final Signal<String> search = Signals.of("");
    private final Signal<List<AudioFileMetadata>> audioFiles = Signals.of(List.of());
    private final Signal<String> selectedAudioId = Signals.of("");
    private final Signal<String> playingAudioId = Signals.of("");
    private final Signal<String> speakerId = Signals.of("");
    private final Signal<Double> maxVolume = Signals.of(1.0);
    private final Signal<Double> maxRange = Signals.of(16.0);
    private final Signal<Double> audioDropoff = Signals.of(1.0);
    private final Signal<Boolean> looping = Signals.of(false);
    private final Signal<Boolean> paused = Signals.of(false);
    private final Signal<String> networkName = Signals.of("");
    private final Signal<Integer> redstoneModeIndex = Signals.of(RedstoneMode.DEFAULT.ordinal());

    private final Signal<List<String>> playlistIds = Signals.of(List.of());
    private final Signal<List<String>> playlistNames = Signals.of(List.of());
    private final Signal<Integer> playlistCursor = Signals.of(-1);
    private final Signal<Boolean> playlistShuffle = Signals.of(false);
    private final Signal<Integer> playlistRepeat = Signals.of(RepeatMode.DEFAULT.ordinal());
    private final Signal<Component> status = Signals.of(Component.empty());
    private boolean applyingRemoteState;

    private final Computed<List<AudioRowModel>> filteredAudio = Signals.computed(() -> {
        String q = search.get().trim().toLowerCase(Locale.ROOT);
        String playing = playingAudioId.get();
        String selected = selectedAudioId.get();
        return audioFiles.get().stream()
                .filter(a -> a.getOriginalFilename().toLowerCase(Locale.ROOT).contains(q))
                .map(a -> new AudioRowModel(a, a.getUuid().equals(selected), a.getUuid().equals(playing)))
                .toList();
    });
    private final Computed<AudioViewState> audioViewState = Signals.computed(() -> {
        if (audioFiles.get().isEmpty()) return AudioViewState.EMPTY;
        return filteredAudio.get().isEmpty() ? AudioViewState.NO_MATCHES : AudioViewState.RESULTS;
    });

    private final java.util.List<Subscription> subs = new java.util.ArrayList<>();

    public SpeakerScreen(BlockPos blockEntityPos) {
        super(Component.translatable("gui.simplyspeakers.speaker.title"));
        this.blockEntityPos = blockEntityPos;
    }

    @Override
    protected void init() {
        closeControlSubscriptions();
        fetchDataFromBlockEntity();
        if (speaker != null) {
            speakerId.set(speaker.getSpeakerId());
            selectedAudioId.set(speaker.getAudioId());
            playingAudioId.set(speaker.getAudioId());
            maxVolume.set((double) speaker.getMaxVolume());
            maxRange.set((double) speaker.getMaxRange());
            audioDropoff.set((double) speaker.getAudioDropoff());
            looping.set(speaker.isLooping());
            networkName.set(speaker.getNetworkName());
            redstoneModeIndex.set(speaker.getRedstoneMode().ordinal());
        }
        super.init();
        wireControlSubscriptions();
        guiReady = true;
        NetworkManager.sendToServer(new com.nstut.simplyspeakers.network.RequestAudioListPacketC2S(blockEntityPos));
        NetworkManager.sendToServer(new RequestPlaylistPacketC2S(blockEntityPos));
    }

    @Override
    protected UIComponent buildUI() {
        VStack panel = Ui.column(
                buildHeader(),
                Ui.tabs(tab)
                        .tab(SpeakerTab.AUDIO, Component.translatable("gui.simplyspeakers.tab.audio"))
                        .tab(SpeakerTab.SETTINGS, Component.translatable("gui.simplyspeakers.tab.settings")),
                // Flex so the active view absorbs exactly the leftover shell space;
                // the audio list then scrolls internally instead of pushing content
                // past the top and bottom of the window.
                Ui.switcher(tab)
                        .when(SpeakerTab.AUDIO, this::buildAudioView)
                        .when(SpeakerTab.SETTINGS, this::buildSettingsView)
                        .flex(),
                Ui.text((Supplier<Component>) status::get).marquee()
        ).gap(6);
        // Pin the requested width: measured widths of tab views differ (the
        // audio list reports a small preferred width), which would otherwise
        // resize the whole window when switching tabs.
        panel.width(PANEL_WIDTH);
        return buildWindow(panel, PANEL_WIDTH);
    }

    private UIComponent buildHeader() {
        return Ui.column(
                Ui.row(
                        Ui.heading(Component.translatable("gui.simplyspeakers.speaker.title")),
                        buildThemeToggle()
                ).justify(Justification.SPACE_BETWEEN),
                Ui.text(() -> {
                    String id = playingAudioId.get();
                    if (id == null || id.isEmpty()) return Component.empty();
                    return Component.translatable("gui.simplyspeakers.now_playing", filenameOf(id));
                })
        ).gap(4);
    }

    private UIComponent buildAudioView() {
        return Ui.switcher(audioViewState)
                .when(AudioViewState.EMPTY, this::buildEmptyAudioView)
                .when(AudioViewState.NO_MATCHES, this::buildNoMatchesView)
                .when(AudioViewState.RESULTS, this::buildAudioResultsView);
    }

    private UIComponent buildEmptyAudioView() {
        EmptyState empty = Ui.emptyState(Component.translatable("gui.simplyspeakers.no_audio"));
        if (!Config.disableUpload) empty.action(Component.translatable("gui.simplyspeakers.upload"), this::openUpload);
        return empty;
    }

    private UIComponent buildNoMatchesView() {
        return Ui.column(
                buildAudioToolbar(),
                Ui.emptyState(Component.translatable("gui.simplyspeakers.no_search_matches", search.get())),
                Ui.button(Component.translatable("gui.simplyspeakers.clear_search"), () -> search.set("")).ghost().small()
        ).gap(8);
    }

    private UIComponent buildAudioResultsView() {
        return Ui.column(
                buildAudioToolbar(),
                Ui.list(filteredAudio, this::buildAudioRow)
                        .key(row -> row.audio().getUuid())
                        .itemHeight(36)
                        .gap(6)
                        // Flex so the list takes exactly the space left inside the
                        // shell instead of a guessed fixed height that overflows
                        // small viewports; it scrolls internally past that.
                        .flex()
                        .minHeight(56)
                ),
                buildPlaylistCard()
        ).gap(6);
    }

    private UIComponent buildAudioToolbar() {
        HStack toolbar = Ui.row(
                Ui.textField(search)
                        .placeholder(Component.translatable("gui.simplyspeakers.search.placeholder").getString())
                        .tooltip(Component.translatable("gui.simplyspeakers.search.tooltip"))
                        .flex()
        ).gap(6);
        if (!Config.disableUpload) {
            toolbar.child(Ui.button(Component.translatable("gui.simplyspeakers.upload"), this::openUpload).primary().small());
        }
        return toolbar;
    }

    private UIComponent buildAudioRow(AudioRowModel row) {
        AudioFileMetadata audio = row.audio();
        boolean playing = row.playing();
        boolean selected = row.selected();

        Card card = Ui.card().outlined(true).padding(8).selected(selected);
        VStack left = Ui.column(
                // Marquee: long filenames ping-pong inside the row like the
                // pre-migration audio list did.
                Ui.text(Component.literal(audio.getOriginalFilename())).marquee(),
                audio.getDurationSeconds() > 0
                        ? Ui.text(Component.translatable("gui.simplyspeakers.duration", formatDuration(audio.getDurationSeconds())))
                        : Ui.text(Component.empty())
        ).gap(2);

        HStack right = Ui.row().gap(6);
        if (playing) {
            right.child(Ui.badge(Component.translatable("gui.simplyspeakers.playing"), Badge.Variant.SUCCESS));
        } else if (selected) {
            right.child(Ui.badge(Component.translatable("gui.simplyspeakers.selected"), Badge.Variant.NEUTRAL));
        }
        if (!selected) {
            right.child(Ui.button(Component.translatable("gui.simplyspeakers.select"), () -> selectAudio(audio)).secondary().small());
        }
        right.child(Ui.button(Component.translatable("gui.simplyspeakers.delete"), () -> confirmDelete(audio)).danger().small());

        card.addChild(Ui.row(left.flex(), right).gap(8));
        return card;
    }

    private UIComponent buildSettingsView() {
        if (speaker == null) {
            return Ui.card(Ui.emptyState(Component.translatable("gui.simplyspeakers.proxy_speaker.not_found"))).outlined(true).padding(16);
        }
        Card card = Ui.card().outlined(true).padding(12);
        // The card itself stays fully visible and fills the tab area; only the
        // column of inputs inside it scrolls when the viewport is too short.
        card.addChild(Ui.scroll(Ui.column(
                buildSpeakerIdRow(),
                Ui.divider(),
                sliderRow(
                        Component.translatable("gui.simplyspeakers.max_volume"),
                        () -> Component.translatable("gui.simplyspeakers.max_volume.slider", (int) (maxVolume.get() * 100)),
                        maxVolume, 0.0, 1.0,
                        Component.translatable("gui.simplyspeakers.max_volume.tooltip")
                ),
                sliderRow(
                        Component.translatable("gui.simplyspeakers.max_range", (int) Config.speakerRange),
                        () -> Component.translatable("gui.simplyspeakers.max_range.slider", (int) (double) maxRange.get()),
                        maxRange, 1.0, Config.speakerRange,
                        Component.translatable("gui.simplyspeakers.max_range.tooltip")
                ),
                sliderRow(
                        Component.translatable("gui.simplyspeakers.audio_dropoff"),
                        () -> Component.translatable("gui.simplyspeakers.audio_dropoff.slider", (int) (audioDropoff.get() * 100)),
                        audioDropoff, 0.0, 1.0,
                        Component.translatable("gui.simplyspeakers.audio_dropoff.tooltip")
                ),
                Ui.row(
                        Ui.text(Component.translatable("gui.simplyspeakers.loop")),
                        Ui.toggle(looping)
                ).gap(8).tooltip(Component.translatable("gui.simplyspeakers.loop.tooltip")),
                Ui.divider(),
                buildPolicyCard(),
                buildTransportCard()
        ).gap(10)).fillHeight());
        return card;
    }

    private UIComponent buildSpeakerIdRow() {
        return Ui.row(
                Ui.textField(speakerId)
                        .placeholder(Component.translatable("gui.simplyspeakers.speaker_id.placeholder").getString())
                        .tooltip(Component.translatable("gui.simplyspeakers.speaker_id.tooltip"))
                        .flex(),
                Ui.button(Component.translatable("gui.simplyspeakers.save"), () -> {
                    if (speaker != null) {
                        String newId = speakerId.get();
                        speaker.setSpeakerId(newId);
                        NetworkManager.sendToServer(new SetSpeakerIdPacketC2S(blockEntityPos, newId));
                    }
                }).primary()
        ).gap(6);
    }

    private UIComponent sliderRow(Component label, Supplier<Component> valueSupplier,
                                   Signal<Double> signal, double min, double max, Component tooltip) {
        Slider slider = Ui.slider(signal, min, max);
        slider.fillWidth();
        UIComponent row = Ui.column(
                Ui.row(Ui.text(label), Ui.text(valueSupplier)).justify(Justification.SPACE_BETWEEN),
                slider
        ).gap(4);
        if (tooltip != null) row.tooltip(tooltip);
        return row;
    }


    // ==================================================================
    // 0.8.x transport / playlist / policy UI
    // ==================================================================

    private record PlaylistRow(int index, String audioId, String filename, boolean current) {}

    private final Computed<List<PlaylistRow>> playlistRows = Signals.computed(() -> {
        List<String> ids = playlistIds.get();
        List<String> names = playlistNames.get();
        int cursor = playlistCursor.get();
        List<PlaylistRow> rows = new java.util.ArrayList<>(ids.size());
        for (int i = 0; i < ids.size(); i++) {
            rows.add(new PlaylistRow(i, ids.get(i), i < names.size() ? names.get(i) : "", i == cursor));
        }
        return rows;
    });

    /** Guards policy packets until the screen finished initialising. */
    private boolean guiReady;
    /** Local estimate of the playback position in seconds for seek buttons. */
    private volatile float estimatedPositionSeconds;

    private void sendToServer(Object packet) {
        NetworkManager.sendToServer(packet);
    }

    private UIComponent buildTransportCard() {
        return Ui.card().outlined(true).padding(10).addChild(Ui.column(
                Ui.text(Component.translatable("gui.simplyspeakers.transport.title")),
                Ui.row(
                        transportButton("gui.simplyspeakers.transport.previous",
                                () -> sendTransport(TransportControlPacketC2S.ACTION_PREVIOUS)),
                        playPauseButton(),
                        transportButton("gui.simplyspeakers.transport.stop",
                                () -> sendTransport(TransportControlPacketC2S.ACTION_STOP)),
                        transportButton("gui.simplyspeakers.transport.restart",
                                () -> sendTransport(TransportControlPacketC2S.ACTION_RESTART)),
                        transportButton("gui.simplyspeakers.transport.next",
                                () -> sendTransport(TransportControlPacketC2S.ACTION_NEXT))
                ).gap(4),
                Ui.row(
                        Ui.button(Component.translatable("gui.simplyspeakers.transport.back30"), () -> {
                            estimatedPositionSeconds = Math.max(0.0f, estimatedPositionSeconds - 30.0f);
                            sendTransport(TransportControlPacketC2S.ACTION_SEEK, estimatedPositionSeconds);
                        }).small(),
                        Ui.button(Component.translatable("gui.simplyspeakers.transport.fwd30"), () -> {
                            estimatedPositionSeconds += 30.0f;
                            sendTransport(TransportControlPacketC2S.ACTION_SEEK, estimatedPositionSeconds);
                        }).small()
                ).gap(6)
        ).gap(6));
    }

    private ButtonWidget transportButton(String key, Runnable action) {
        return Ui.button(Component.translatable(key), action);
    }

    private ButtonWidget playPauseButton() {
        boolean isPaused = Boolean.TRUE.equals(paused.get());
        return Ui.button(isPaused
                        ? Component.translatable("gui.simplyspeakers.transport.play")
                        : Component.translatable("gui.simplyspeakers.transport.pause"),
                () -> sendTransport(TransportControlPacketC2S.ACTION_TOGGLE)).primary();
    }

    private void sendTransport(byte action) {
        sendToServer(new TransportControlPacketC2S(blockEntityPos, action));
    }

    private void sendTransport(byte action, float seekSeconds) {
        sendToServer(new TransportControlPacketC2S(blockEntityPos, action, seekSeconds));
    }

    private UIComponent buildPlaylistCard() {
        HStack header = Ui.row(
                Ui.text(Component.translatable("gui.simplyspeakers.playlist.title")).flex(),
                Ui.button(Component.translatable("gui.simplyspeakers.playlist.add_selected"),
                        this::addSelectedToPlaylist).small(),
                shuffleButton(),
                repeatCycleButton(),
                Ui.button(Component.translatable("gui.simplyspeakers.playlist.clear"),
                        () -> sendPlaylistOp(PlaylistControlPacketC2S.OP_CLEAR, -1, false, "", "")).danger().small()
        ).gap(4);

        return Ui.card().outlined(true).padding(10).addChild(Ui.column(
                header,
                playlistRows.get().isEmpty()
                        ? Ui.text(Component.translatable("gui.simplyspeakers.playlist.empty"))
                        : Ui.list(playlistRows, this::buildPlaylistRow)
                                .key(row -> row.audioId() + ":" + row.index())
                                .itemHeight(24)
                                .flex()
                                .minHeight(40)
        ).gap(6));
    }

    private ButtonWidget shuffleButton() {
        return Ui.button((Supplier<Component>) () -> Component.translatable(
                        playlistShuffle.get() ? "gui.simplyspeakers.playlist.shuffle_on"
                                : "gui.simplyspeakers.playlist.shuffle_off"),
                () -> {
                    boolean newValue = !playlistShuffle.get();
                    playlistShuffle.set(newValue);
                    sendToServer(new PlaylistControlPacketC2S(blockEntityPos,
                            PlaylistControlPacketC2S.OP_SET_SHUFFLE, -1, newValue, "", ""));
                });
    }

    private ButtonWidget repeatCycleButton() {
        return Ui.button((Supplier<Component>) () -> {
                    RepeatMode mode = RepeatMode.fromIndex(playlistRepeat.get());
                    return Component.translatable("gui.simplyspeakers.playlist.repeat", mode.id());
                },
                () -> {
                    int next = (playlistRepeat.get() + 1) % RepeatMode.values().length;
                    playlistRepeat.set(next);
                    sendToServer(new PlaylistControlPacketC2S(blockEntityPos,
                            PlaylistControlPacketC2S.OP_SET_REPEAT, next, false, "", ""));
                });
    }

    private void addSelectedToPlaylist() {
        String id = selectedAudioId.get();
        if (id.isEmpty()) {
            status.set(Component.translatable("gui.simplyspeakers.playlist.no_selection"));
            return;
        }
        sendToServer(new PlaylistControlPacketC2S(blockEntityPos,
                PlaylistControlPacketC2S.OP_ADD, -1, false, id, filenameOf(id)));
        status.set(Component.translatable("gui.simplyspeakers.playlist.added", filenameOf(id)));
    }

    private void sendPlaylistOp(byte op, int index, boolean flag, String audioId, String filename) {
        sendToServer(new PlaylistControlPacketC2S(blockEntityPos, op, index, flag, audioId, filename));
    }

    private UIComponent buildPlaylistRow(PlaylistRow row) {
        HStack right = Ui.row().gap(2);
        if (!row.current()) {
            right.child(Ui.button(Component.translatable("gui.simplyspeakers.playlist.play_here"),
                    () -> sendPlaylistOp(PlaylistControlPacketC2S.OP_SELECT_INDEX, row.index(), true,
                            row.audioId(), row.filename())).small());
        }
        right.child(Ui.button(Component.translatable("gui.simplyspeakers.playlist.queue"),
                () -> sendPlaylistOp(PlaylistControlPacketC2S.OP_QUEUE_NEXT, -1, false, row.audioId(), "")).small());
        right.child(Ui.button(Component.translatable("gui.simplyspeakers.playlist.up"),
                () -> sendPlaylistOp(PlaylistControlPacketC2S.OP_MOVE_UP, row.index(), false, "", "")).small());
        right.child(Ui.button(Component.translatable("gui.simplyspeakers.playlist.down"),
                () -> sendPlaylistOp(PlaylistControlPacketC2S.OP_MOVE_DOWN, row.index(), false, "", "")).small());
        right.child(Ui.button(Component.translatable("gui.simplyspeakers.playlist.remove"),
                () -> sendPlaylistOp(PlaylistControlPacketC2S.OP_REMOVE_AUDIO, -1, false, row.audioId(), "")).danger().small());

        Card card = Ui.card().outlined(true).padding(6);
        card.addChild(Ui.row(
                Ui.text(Component.literal((row.index() + 1) + ". " + row.filename())).marquee().flex(),
                right
        ).gap(6));
        return card;
    }

    private UIComponent buildPolicyCard() {
        return Ui.card().outlined(true).padding(10).addChild(Ui.column(
                Ui.row(
                        Ui.textField(networkName)
                                .placeholder(Component.translatable("gui.simplyspeakers.network_name.placeholder").getString())
                                .flex(),
                        Ui.button(Component.translatable("gui.simplyspeakers.save"), () ->
                                sendToServer(SpeakerPolicyPacketC2S.networkName(blockEntityPos, networkName.get()))).primary()
                ).gap(6),
                Ui.row(
                        Ui.text(Component.translatable("gui.simplyspeakers.redstone")).flex(),
                        Ui.button((Supplier<Component>) () -> {
                                    RedstoneMode mode = RedstoneMode.fromIndex(redstoneModeIndex.get());
                                    return Component.translatable("gui.simplyspeakers.redstone.mode", mode.id());
                                },
                                () -> {
                                    int next = (redstoneModeIndex.get() + 1) % RedstoneMode.values().length;
                                    redstoneModeIndex.set(next);
                                    sendToServer(SpeakerPolicyPacketC2S.redstoneMode(blockEntityPos, RedstoneMode.fromIndex(next)));
                                })
                ).gap(6),
                directionalSliderRow(Component.translatable("gui.simplyspeakers.directionality"), directionality, 0.0, 1.0,
                        () -> sendToServer(SpeakerPolicyPacketC2S.directionality(blockEntityPos, directionality.get().floatValue()))),
                directionalSliderRow(Component.translatable("gui.simplyspeakers.cone_angle"), coneAngle, 5.0, 355.0,
                        () -> sendToServer(SpeakerPolicyPacketC2S.coneAngle(blockEntityPos, (int) Math.round(coneAngle.get())))),
                directionalSliderRow(Component.translatable("gui.simplyspeakers.rear_attenuation"), rearAttenuation, 0.0, 1.0,
                        () -> sendToServer(SpeakerPolicyPacketC2S.rearAttenuation(blockEntityPos, rearAttenuation.get().floatValue())))
        ).gap(8));
    }

    private final Signal<Double> directionality = Signals.of(0.0);
    private final Signal<Double> coneAngle = Signals.of(90.0);
    private final Signal<Double> rearAttenuation = Signals.of(0.9);

    private final java.util.List<Subscription> guiSubs = new java.util.ArrayList<>();

    private UIComponent directionalSliderRow(Component label, Signal<Double> signal,
                                             double min, double max, Runnable commit) {
        Slider slider = Ui.slider(signal, min, max);
        slider.fillWidth();
        UIComponent column = Ui.column(
                Ui.row(
                        Ui.text((Supplier<Component>) () -> {
                            double v = signal.get();
                            String value = (max - min > 1.5)
                                    ? String.format(java.util.Locale.ROOT, "%.0f", v)
                                    : String.format(java.util.Locale.ROOT, "%.0f%%", v * 100.0);
                            return label.copy().append(": ").append(value);
                        }),
                        slider
                ).justify(Justification.SPACE_BETWEEN)
        ).gap(2);
        guiSubs.add(signal.subscribe(v -> {
            if (!guiReady || applyingRemoteState) return;
            commit.run();
        }));
        return column;
    }

    /** Called by {@link PlaylistSyncPacketS2C} handlers on every authoritative change. */
    public void updatePlaylistModel(PlaylistSyncPacketS2C packet) {
        playlistIds.set(List.copyOf(packet.getAudioIds()));
        playlistNames.set(List.copyOf(packet.getFilenames()));
        playlistCursor.set(packet.getCurrentIndex());
        playlistShuffle.set(packet.isShuffle());
        playlistRepeat.set(Math.max(0, packet.getRepeatOrdinal()));
        paused.set(packet.isPaused());
    }

    private void wireControlSubscriptions() {
        subs.add(maxVolume.subscribe(v -> NetworkManager.sendToServer(
                new UpdateMaxVolumePacketC2S(blockEntityPos, v.floatValue()))));
        subs.add(maxRange.subscribe(v -> NetworkManager.sendToServer(
                new UpdateMaxRangePacketC2S(blockEntityPos, (int) Math.round(v)))));
        subs.add(audioDropoff.subscribe(v -> NetworkManager.sendToServer(
                new UpdateAudioDropoffPacketC2S(blockEntityPos, v.floatValue()))));
        subs.add(looping.subscribe(v -> {
            if (applyingRemoteState) return;
            if (speaker != null) speaker.setLoopingClient(v);
            NetworkManager.sendToServer(new ToggleLoopPacketC2S(blockEntityPos, v));
        }));
    }

    private void closeControlSubscriptions() {
        for (Subscription subscription : subs) subscription.close();
        subs.clear();
    }

    private void selectAudio(AudioFileMetadata audio) {
        if (speaker != null) speaker.setAudioIdClient(audio.getUuid(), audio.getOriginalFilename());
        selectedAudioId.set(audio.getUuid());
        NetworkManager.sendToServer(new SelectAudioPacketC2S(blockEntityPos, audio.getUuid(), audio.getOriginalFilename()));
    }

    private void confirmDelete(AudioFileMetadata audio) {
        Card card = Ui.card().elevated(true).outlined(true).padding(14);
        OverlayHandle[] handle = { null };
        ButtonWidget cancelBtn = Ui.button(Component.translatable("gui.simplyspeakers.cancel"),
                () -> { if (handle[0] != null) handle[0].close(); }).ghost();
        ButtonWidget deleteBtn = Ui.button(Component.translatable("gui.simplyspeakers.delete"), () -> {
            if (handle[0] != null) handle[0].close();
            NetworkManager.sendToServer(new DeleteAudioPacketC2S(audio.getUuid()));
        }).danger();
        card.addChild(Ui.column(
                Ui.heading(Component.translatable("gui.simplyspeakers.delete_confirm.title")),
                Ui.text(Component.translatable("gui.simplyspeakers.delete_confirm.body", audio.getOriginalFilename())),
                Ui.row(cancelBtn, deleteBtn).gap(6)
        ).gap(10));
        card.width(240).minHeight(90);
        handle[0] = Dialog.show(uiRuntime().overlays(), card);
    }

    private void openUpload() {
        Services.CLIENT.openFileDialog("mp3,wav", file -> {
            if (file == null) return;
            String name = file.getName().toLowerCase(Locale.ROOT);
            if (!name.endsWith(".mp3") && !name.endsWith(".wav")) {
                setStatusMessage(Component.translatable("gui.simplyspeakers.upload.invalid_type"));
                Toast.show(uiRuntime().overlays(),
                        Toast.error(Component.translatable("gui.simplyspeakers.upload").getString(),
                                Component.translatable("gui.simplyspeakers.upload.invalid_type").getString()));
                return;
            }
            UUID transactionId = ClientAudioPlayer.startUpload(file);
            NetworkManager.sendToServer(
                    new RequestUploadAudioPacketC2S(blockEntityPos, transactionId, file.getName(), file.length()));
        });
    }

    private String filenameOf(String uuid) {
        for (AudioFileMetadata a : audioFiles.get()) {
            if (a.getUuid().equals(uuid)) return a.getOriginalFilename();
        }
        return uuid;
    }

    private String formatDuration(float seconds) {
        int total = Math.max(0, Math.round(seconds));
        int m = total / 60;
        int s = total % 60;
        return m + ":" + (s < 10 ? "0" : "") + s;
    }

    // ---- Public bridge methods called by network handlers / upload callbacks ----

    public void updateAudioList(List<AudioFileMetadata> audioList) {
        ClientAudioPlayer.setAudioList(audioList);
        audioFiles.set(List.copyOf(audioList));
    }

    public void refreshFromState(String audioId, String filename, boolean looping) {
        applyingRemoteState = true;
        try {
            playingAudioId.set(audioId == null ? "" : audioId);
            this.looping.set(looping);
            if (speaker != null) {
                speaker.setAudioIdClient(audioId, filename);
                speaker.setLoopingClient(looping);
            }
        } finally {
            applyingRemoteState = false;
        }
    }

    public void setStatusMessage(Component statusMessage) {
        status.set(statusMessage);
    }

    public BlockPos getBlockEntityPos() {
        return blockEntityPos;
    }

    public String getSpeakerId() {
        return speaker != null ? speaker.getSpeakerId() : "";
    }

    private void fetchDataFromBlockEntity() {
        if (Minecraft.getInstance().level == null) {
            this.speaker = null;
            return;
        }
        BlockEntity blockEntity = Minecraft.getInstance().level.getBlockEntity(blockEntityPos);
        if (blockEntity instanceof SpeakerBlockEntity s) {
            this.speaker = s;
        } else {
            this.speaker = null;
        }
    }

    @Override
    public void removed() {
        guiSubs.forEach(Subscription::close);
        guiSubs.clear();
        guiReady = false;
        closeControlSubscriptions();
        audioViewState.close();
        filteredAudio.close();
        super.removed();
    }
}
