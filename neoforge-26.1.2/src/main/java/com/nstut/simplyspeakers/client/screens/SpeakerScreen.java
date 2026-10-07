package com.nstut.simplyspeakers.client.screens;

import com.nstut.openui.api.ButtonWidget;
import com.nstut.openui.api.HStack;
import com.nstut.openui.api.Ui;
import com.nstut.openui.layout.Alignment;
import com.nstut.simplyspeakers.client.ui.SpeakerIconButton;
import com.nstut.simplyspeakers.client.ui.SpeakerIconButton.Icon;
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
import com.nstut.simplyspeakers.client.ui.SpeakerButtonWidget;
import com.nstut.simplyspeakers.client.ui.SpeakerMarqueeButton;
import com.nstut.simplyspeakers.network.DeleteAudioPacketC2S;
import dev.architectury.networking.NetworkManager;
import com.nstut.simplyspeakers.network.RequestUploadAudioPacketC2S;
import com.nstut.simplyspeakers.network.SelectAudioPacketC2S;
import com.nstut.simplyspeakers.network.SetSpeakerIdPacketC2S;
import com.nstut.simplyspeakers.network.PlaylistSyncPacketS2C;
import com.nstut.simplyspeakers.network.SpeakerStateUpdatePacketS2C;
import com.nstut.simplyspeakers.network.PlaylistControlPacketC2S;
import com.nstut.simplyspeakers.network.RequestPlaylistPacketC2S;
import com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S;
import com.nstut.simplyspeakers.network.TransportControlPacketC2S;
import com.nstut.simplyspeakers.playlist.RepeatMode;
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
    private static final int PANEL_WIDTH = 560;

    private enum SpeakerTab { AUDIO, PLAYLIST, QUEUE, SETTINGS }
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
    private final Signal<Boolean> paused = Signals.of(false);
    private final Signal<String> networkName = Signals.of("");

    private final Signal<List<String>> playlistIds = Signals.of(List.of());
    private final Signal<List<String>> playlistNames = Signals.of(List.of());
    private final Signal<Integer> playlistCursor = Signals.of(-1);
    private final Signal<Boolean> playlistShuffle = Signals.of(false);
    private final Signal<Integer> playlistRepeat = Signals.of(RepeatMode.DEFAULT.ordinal());
    private final Signal<Component> status = Signals.of(Component.empty());
    private final Computed<Boolean> hasStatus = Signals.computed(() -> !status.get().getString().isEmpty());
    private boolean applyingRemoteState;
    private final Signal<List<String>> queueIds = Signals.of(List.of());
    private final Signal<List<Integer>> upcomingIndices = Signals.of(List.of());
    private final Signal<Boolean> playing = Signals.of(false);
    private final Signal<String> playingFilename = Signals.of("");
    private final Signal<Double> position = Signals.of(0.0);
    private final Signal<Double> duration = Signals.of(0.0);
    private final Signal<Double> seekFraction = Signals.of(0.0);
    private final Signal<String> jumpTimestamp = Signals.of("");
    private final Signal<Boolean> seekable = Signals.of(false);
    private boolean scrubbing;
    private double snapshotPosition;
    private long snapshotTick;
    private int contentWidth;
    private long lastSnapshotRequest;
    private Slider timeline;
    private UIComponent playerControls;
    private ButtonWidget playPause;
    private ButtonWidget shuffleControl;
    private ButtonWidget repeatControl;
    private ButtonWidget jumpControl;

    private final Computed<List<AudioRowModel>> filteredAudio = Signals.computed(() -> {
        String q = search.get().trim().toLowerCase(Locale.ROOT);
        String currentAudio = this.playing.get() ? playingAudioId.get() : "";
        String selected = selectedAudioId.get();
        return audioFiles.get().stream()
                .filter(a -> q.isEmpty() || a.hasTag(q))
                .map(a -> new AudioRowModel(a, a.getUuid().equals(selected), a.getUuid().equals(currentAudio)))
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
        guiSubs.forEach(Subscription::close);
        guiSubs.clear();
        guiReady = false;
        scrubbing = false;
        fetchDataFromBlockEntity();
        if (speaker != null) {
            speakerId.set(speaker.getSpeakerId());
            selectedAudioId.set(speaker.getAudioId());
            playingAudioId.set(speaker.getAudioId());
            maxVolume.set((double) speaker.getMaxVolume());
            maxRange.set((double) speaker.getMaxRange());
            audioDropoff.set((double) speaker.getAudioDropoff());
            networkName.set(speaker.getNetworkName());
            var policy=speaker.getSpeakerState();
            if(policy!=null) {
                directionality.set((double)policy.getDirectionality());
                coneAngle.set((double)policy.getConeAngleDegrees());
                rearAttenuation.set((double)policy.getRearAttenuation());
            }
        }
        super.init();
        wireControlSubscriptions();
        guiReady = true;
        sendToServer(new com.nstut.simplyspeakers.network.RequestAudioListPacketC2S(blockEntityPos));
        sendToServer(new RequestPlaylistPacketC2S(blockEntityPos));
    }

    @Override
    protected UIComponent buildUI() {
        contentWidth = Math.min(PANEL_WIDTH, Math.max(76, width - 40));
        UIComponent views = Ui.switcher(tab)
                .when(SpeakerTab.AUDIO, this::buildAudioView)
                .when(SpeakerTab.PLAYLIST, this::buildPlaylistView)
                .when(SpeakerTab.QUEUE, this::buildQueueView)
                .when(SpeakerTab.SETTINGS, this::buildSettingsView).flex();
        UIComponent browser;
        if (contentWidth >= 440) {
            browser = Ui.row(buildNavigation().width(92), Ui.divider().width(1).fillHeight().key("browser.separator"), views).gap(10).flex();
        } else {
            browser = Ui.column(Ui.tabs(tab)
                    .tab(SpeakerTab.AUDIO, tr("library"))
                    .tab(SpeakerTab.PLAYLIST, tr("playlist"))
                    .tab(SpeakerTab.QUEUE, tr("queue"))
                    .tab(SpeakerTab.SETTINGS, tr("settings")), views).gap(6).flex();
        }
        VStack panel = Ui.column(buildHeader(), Ui.divider(), browser, Ui.divider(),
                buildPlayerBar(), Ui.switcher(hasStatus).when(true, () -> Ui.text((Supplier<Component>) status::get).nowrap().marquee())
                        .when(false, () -> Ui.spacer().height(0))).gap(3);
        panel.width(contentWidth);
        return buildWindow(panel, contentWidth);
    }

    private Component tr(String key, Object... args) {
        return Component.translatable("gui.simplyspeakers.player." + key, args);
    }

    private VStack buildNavigation() {
        VStack nav = Ui.column().gap(4);
        for (SpeakerTab destination : SpeakerTab.values()) {
            String key = destination == SpeakerTab.AUDIO ? "library" : destination.name().toLowerCase(Locale.ROOT);
            ButtonWidget button = SpeakerButtonWidget.button(tr(key), () -> tab.set(destination)).ghost().small().alignLeft().activeIndicator();
            button.setActive(tab.get() == destination);
            guiSubs.add(tab.subscribe(value -> button.setActive(value == destination)));
            nav.child(button);
        }
        return nav;
    }

    private UIComponent buildHeader() {
        return Ui.row(Ui.heading(Component.translatable("gui.simplyspeakers.speaker.title")),
                Ui.spacer().flex(), buildThemeToggle()).align(Alignment.CENTER).gap(8);
    }

    private UIComponent buildAudioView() {
        return Ui.switcher(audioViewState)
                .when(AudioViewState.EMPTY, this::buildEmptyAudioView)
                .when(AudioViewState.NO_MATCHES, this::buildNoMatchesView)
                .when(AudioViewState.RESULTS, this::buildAudioResultsView);
    }

    private UIComponent buildEmptyAudioView() {
        return Ui.column(buildAudioToolbar(),Ui.emptyState(Component.translatable("gui.simplyspeakers.no_audio")).flex()).gap(6);
    }

    private UIComponent buildNoMatchesView() {
        return Ui.column(
                buildAudioToolbar(),
                Ui.emptyState(Component.translatable("gui.simplyspeakers.no_search_matches", search.get())).flex(),
                SpeakerButtonWidget.button(Component.translatable("gui.simplyspeakers.clear_search"), () -> search.set("")).ghost().small()
        ).gap(8);
    }

    private UIComponent buildAudioResultsView() {
        return Ui.column(buildAudioToolbar(), Ui.list(filteredAudio, this::buildAudioRow)
                .key(row -> row.audio().getUuid()).itemHeight(24).gap(2).flex().minHeight(28)).gap(6);
    }

    private UIComponent buildAudioToolbar() {
        HStack toolbar = Ui.row(
                Ui.textField(search)
                        .placeholder(Component.translatable("gui.simplyspeakers.search.placeholder").getString())
                        .tooltip(Component.translatable("gui.simplyspeakers.search.tooltip"))
                        .flex()
        ).gap(6);
        if (!Config.disableUpload) {
            toolbar.child(SpeakerButtonWidget.button(Component.translatable("gui.simplyspeakers.upload"), this::openUpload).primary().small());
        }
        toolbar.child(new SpeakerIconButton(Icon.PLUS,streamText("add"),this::addStreamDialog).key("library.add_stream"));
        return toolbar;
    }

    private UIComponent buildAudioRow(AudioRowModel row) {
        AudioFileMetadata audio = row.audio();
        ButtonWidget title = new SpeakerMarqueeButton(() -> Component.literal(audio.effectiveDisplayName()),6).onPress(() -> selectedAudioId.set(audio.getUuid())).ghost().small().alignLeft();
        title.setActive(row.selected());
        title.key("library.title");
        title.flex().tooltip(Component.literal(audio.effectiveDisplayName()));
        return Ui.row(
                new SpeakerIconButton(Icon.PLAY, tr("play_now"), () -> playAudio(audio)),
                title,
                Ui.text(Component.literal(audio.getDurationSeconds() > 0
                        ? formatDuration(audio.getDurationSeconds()) : "—")).nowrap().centered().width(40).key("library.duration"),
                new SpeakerIconButton(Icon.MORE, tr("track_actions"), () -> openAudioMenu(audio))
        ).align(Alignment.CENTER).gap(4);
    }

    private void playAudio(AudioFileMetadata audio) {
        sendPlaylistOp(PlaylistControlPacketC2S.OP_PLAY_AUDIO, -1, true, audio.getUuid(), "");
    }

    private com.nstut.openui.controls.ContextMenu trackMenu() {
        var mouse = Minecraft.getInstance().mouseHandler;
        var window = Minecraft.getInstance().getWindow();
        return Ui.contextMenu((int) (mouse.xpos() * width / window.getScreenWidth()),
                (int) (mouse.ypos() * height / window.getScreenHeight()));
    }

    private void openAudioMenu(AudioFileMetadata audio) {
        trackMenu().item(tr("play_now"), () -> playAudio(audio))
                .item(tr("play_next"), () -> sendPlaylistOp(PlaylistControlPacketC2S.OP_QUEUE_NEXT, -1, false, audio.getUuid(), ""))
                .item(tr("add_queue"), () -> sendPlaylistOp(PlaylistControlPacketC2S.OP_QUEUE_LAST, -1, false, audio.getUuid(), ""))
                .item(tr("add_playlist"), () -> chooseSavedPlaylist(audio))
                .separator().item(tr("delete_server"), () -> confirmDelete(audio)).show(uiRuntime().overlays());
    }

    private UIComponent buildSettingsView() {
        if (speaker == null) {
            return Ui.emptyState(Component.translatable("gui.simplyspeakers.proxy_speaker.not_found"));
        }
        return Ui.scroll(Ui.column(
                buildDisplayNameRow(),
                buildSpeakerIdRow(),
                Ui.divider(),
                sliderRow(
                        Component.translatable("gui.simplyspeakers.max_range", (int) Config.speakerRange),
                        () -> Component.literal(Math.round(maxRange.get())+" blocks"),
                        maxRange, 1.0, Config.speakerRange,
                        Component.translatable("gui.simplyspeakers.max_range.tooltip")
                ),
                sliderRow(
                        Component.translatable("gui.simplyspeakers.audio_dropoff"),
                        () -> Component.literal(Math.round(audioDropoff.get()*100)+"%"),
                        audioDropoff, 0.0, 1.0,
                        Component.translatable("gui.simplyspeakers.audio_dropoff.tooltip")
                ),
                Ui.divider(),
                buildAccessGroup(),
                Ui.divider(),
                buildPolicyCard()
        ).gap(10)).fillHeight().key("settings.content");
    }

    private UIComponent buildDisplayNameRow() {
        return Ui.row(
                        Ui.textField(networkName)
                                .placeholder(Component.translatable("gui.simplyspeakers.network_name.placeholder").getString())
                                .tooltip(Component.translatable("gui.simplyspeakers.network_name.tooltip")).key("settings.network_name").flex(),
                        SpeakerButtonWidget.button(Component.translatable("gui.simplyspeakers.save"), () ->
                                sendToServer(SpeakerPolicyPacketC2S.networkName(blockEntityPos, networkName.get()))).primary().enabledWhen(() -> accessView.get().canManage())

                ).gap(6);
    }

    private UIComponent buildSpeakerIdRow() {
        return Ui.row(
                Ui.textField(speakerId)
                        .placeholder(Component.translatable("gui.simplyspeakers.speaker_id.placeholder").getString())
                        .tooltip(Component.translatable("gui.simplyspeakers.speaker_id.tooltip"))
                        .flex(),
                SpeakerButtonWidget.button(Component.translatable("gui.simplyspeakers.save"), () -> {
                    if (speaker != null) {
                        String newId = speakerId.get();
                        speaker.setSpeakerId(newId);
                        sendToServer(new SetSpeakerIdPacketC2S(blockEntityPos, newId));
                    }
                }).primary().enabledWhen(() -> accessView.get().canManage())
        ).gap(6);
    }

    private UIComponent sliderRow(Component label, Supplier<Component> valueSupplier,
                                   Signal<Double> signal, double min, double max, Component tooltip) {
        Slider slider = Ui.slider(signal, min, max);
        slider.fillWidth();
        UIComponent row = Ui.column(
                Ui.row(Ui.text(label).nowrap().marquee().flex().tooltip(tooltip), Ui.text(valueSupplier).nowrap().tooltip(tooltip)).align(Alignment.CENTER).justify(Justification.SPACE_BETWEEN),
                Ui.switcher(Signals.computed(() -> accessView.get().canControl())).when(true,() -> slider).when(false,() -> Ui.spacer().height(0))
        ).gap(4);
        if (tooltip != null) { row.tooltip(tooltip); slider.tooltip(tooltip); }
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

    private void sendToServer(net.minecraft.network.protocol.common.custom.CustomPacketPayload packet) {
        if (Minecraft.getInstance().getConnection() == null) return;
        NetworkManager.sendToServer(packet);
    }

    private UIComponent buildPlayerBar() {
        timeline = Ui.slider(seekFraction, 0.0, 1.0);
        timeline.flex().height(14).tooltip(tr("seek_help"));
        timeline.on(com.nstut.openui.input.EventType.MOUSE_DOWN, event -> { if (event instanceof com.nstut.openui.input.PointerEvent pointer && pointer.button() == 0) scrubbing = true; });
        timeline.on(com.nstut.openui.input.EventType.MOUSE_UP, event -> {
            if (!scrubbing || !(event instanceof com.nstut.openui.input.PointerEvent pointer) || pointer.button() != 0) return;
            scrubbing = false;
            seekTo(seekFraction.get() * duration.get());
        });
        guiSubs.add(seekFraction.subscribe(value -> {
            if (applyingRemoteState || !guiReady) return;
            if (scrubbing) position.set(value * duration.get());
            else seekTo(value * duration.get());
        }));
        playPause = playPauseButton();
        shuffleControl = shuffleButton().ghost().small();
        repeatControl = repeatCycleButton().ghost().small();
        shuffleControl.setActive(playlistShuffle.get()); repeatControl.setActive(playlistRepeat.get() != 0);
        guiSubs.add(playlistShuffle.subscribe(shuffleControl::setActive));
        guiSubs.add(playlistRepeat.subscribe(value -> repeatControl.setActive(value != 0)));
        Slider volume = Ui.slider(maxVolume, 0, 1);
        volume.width(contentWidth >= 440 ? 70 : 48); volume.height(14); volume.key("player.volume");
        UIComponent heading = Ui.row(
                Ui.text((Supplier<Component>) () -> playingAudioId.get().isEmpty() ? tr("nothing_playing")
                        : Component.literal(playingFilename.get().isBlank() ? filenameOf(playingAudioId.get()) : playingFilename.get())).nowrap().marquee().flex(),
                Ui.switcher(Signals.computed(() -> accessView.get().canControl())).when(true,() -> volume.tooltip(Component.translatable("gui.simplyspeakers.max_volume.tooltip"))).when(false,() -> Ui.spacer().width(0)),
                Ui.text(() -> Component.literal(Math.round(maxVolume.get() * 100) + "%")).nowrap().tooltip(Component.translatable("gui.simplyspeakers.max_volume.tooltip"))).align(Alignment.CENTER).gap(6);
        UIComponent progress = Ui.row(
                Ui.text(() -> Component.literal(formatDuration(position.get().floatValue()))).nowrap().centered().width(40).key("library.duration"),
                Ui.switcher(seekable).when(true, this::buildTimeline)
                        .when(false, () -> Ui.text(tr(!accessView.get().canControl()?"read_only":duration.get() <= 0 && playing.get() ? "live" : "seek_unavailable"))).flex(),
                Ui.text(() -> Component.literal(duration.get() > 0 ? formatDuration(duration.get().floatValue()) : "—")).nowrap().centered().width(40)).align(Alignment.CENTER).gap(6);
        jumpControl = new SpeakerIconButton(Icon.JUMP, tr("jump"), this::jumpToTimestamp);
        var jumpMenu = new SpeakerIconButton(Icon.JUMP, tr("jump"), () -> {
            OverlayHandle[] handle={null};
            Signal<Component> error=Signals.of(Component.empty());
            Card dialog=Ui.card(Ui.column(Ui.heading(tr("jump")),
                    Ui.text(tr("jump_help")).wrap(),
                    Ui.card(Ui.row(Ui.textField(jumpTimestamp).placeholder("m:ss").flex(),
                            new SpeakerIconButton(Icon.JUMP,tr("jump"),() -> {
                                status.set(Component.empty());jumpToTimestamp();
                                if(status.get().getString().isBlank()) handle[0].close(); else error.set(status.get());
                            }).enabled(seekable.get())).align(Alignment.CENTER).gap(6)).outlined(true).padding(6).key("player.jump.group"),
                    Ui.text((Supplier<Component>)error::get).wrap())
                    .gap(8)).padding(12);
            dialog.width(Math.min(240,width-40)); handle[0]=Dialog.show(uiRuntime().overlays(),dialog);
        });
        jumpControl=jumpMenu;
        UIComponent controls = Ui.row(shuffleControl,
                iconTransport("PREVIOUS", "previous", TransportControlPacketC2S.ACTION_PREVIOUS),
                iconTransport("BACK", "back30", TransportControlPacketC2S.ACTION_SEEK_RELATIVE, -30),
                playPause,
                iconTransport("FORWARD", "fwd30", TransportControlPacketC2S.ACTION_SEEK_RELATIVE, 30),
                iconTransport("NEXT", "next", TransportControlPacketC2S.ACTION_NEXT), repeatControl,
                iconTransport("RESTART", "restart", TransportControlPacketC2S.ACTION_RESTART),
                iconTransport("STOP", "stop", TransportControlPacketC2S.ACTION_STOP),jumpMenu)
                .align(Alignment.CENTER).justify(Justification.CENTER).gap(4).key("player.transport");
        playerControls=controls;
        return Ui.column(heading.key("player.heading"), progress.key("player.progress"), controls).gap(6).key("player.bar");
    }

    private UIComponent iconTransport(String label, String key, byte action) {
        return new SpeakerIconButton(Icon.valueOf(label), Component.translatable("gui.simplyspeakers.transport." + key), () -> sendTransport(action));
    }

    private UIComponent iconTransport(String label, String key, byte action, float seconds) {
        return new SpeakerIconButton(Icon.valueOf(label), Component.translatable("gui.simplyspeakers.transport." + key), () -> sendTransport(action, seconds));
    }

    private UIComponent buildTimeline() {
        UIComponent progress = new UIComponent() {
            @Override public int preferredWidth(net.minecraft.client.gui.Font font) { return 0; }
            @Override public int preferredHeight(net.minecraft.client.gui.Font font) { return 14; }
            @Override public UIComponent hitTest(int x, int y) { return null; }
            @Override public void render(net.minecraft.client.gui.GuiGraphicsExtractor g, net.minecraft.client.gui.Font font, int mx, int my, float pt) {
                int end = x + (int) Math.round(Math.max(0, Math.min(1, seekFraction.get())) * Math.max(0, width - 6)) + 3;
                int mid = y + height / 2;
                g.fill(x + 3, mid - 1, x + width - 3, mid + 1, colors().border());
                g.fill(x + 3, mid - 1, end, mid + 1, colors().primary());
                com.nstut.openui.api.UiRender.roundedRect(g, end - 3, mid - 3, 6, 6, 3, colors().primary());
            }
        };
        return Ui.stack(timeline, progress).height(14);
    }

    private void seekTo(double seconds) {
        if (!seekable.get() || !Double.isFinite(seconds)) return;
        double target = Math.max(0, Math.min(duration.get(), seconds));
        snapshotPosition = target;
        snapshotTick = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        applyingRemoteState = true;
        try { position.set(target); seekFraction.set(target / duration.get()); }
        finally { applyingRemoteState = false; }
        sendTransport(TransportControlPacketC2S.ACTION_SEEK, (float) target);
    }

    private void jumpToTimestamp() {
        String text = jumpTimestamp.get().trim();
        if (!text.matches("\\d+:[0-5]\\d")) { status.set(tr("invalid_timestamp")); return; }
        String[] parts = text.split(":");
        try { seekTo(Double.parseDouble(parts[0]) * 60 + Double.parseDouble(parts[1])); }
        catch (NumberFormatException ignored) { status.set(tr("invalid_timestamp")); }
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void tick() {
        super.tick();
        long tick = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
        if (!scrubbing) {
            double elapsed = snapshotPosition + (playing.get() && !paused.get() ? Math.max(0, tick - snapshotTick) / 20.0 : 0);
            if (duration.get() > 0) elapsed = Math.min(duration.get(), elapsed);
            applyingRemoteState = true;
            try { position.set(elapsed); seekFraction.set(duration.get() > 0 ? elapsed / duration.get() : 0); }
            finally { applyingRemoteState = false; }
        }
        // Correct interpolation for server pauses, EOF, and edits by other controllers.
        if (tick - lastSnapshotRequest >= 40) {
            lastSnapshotRequest = tick;
            sendToServer(new RequestPlaylistPacketC2S(blockEntityPos));
        }
        if (playerControls != null) for(var child:playerControls.children()) if(child instanceof ButtonWidget button)
            button.enabled(accessView.get().canControl() && (button!=jumpControl || seekable.get()));
    }



    private ButtonWidget playPauseButton() {
        return new SpeakerIconButton(() -> playing.get() && !paused.get() ? Icon.PAUSE : Icon.PLAY,
                () -> tr(playing.get() && !paused.get() ? "pause" : "play"),
                () -> sendTransport(TransportControlPacketC2S.ACTION_TOGGLE)).primary();
    }

    private void sendTransport(byte action) {
        sendToServer(new TransportControlPacketC2S(blockEntityPos, action));
    }

    private void sendTransport(byte action, float seekSeconds) {
        sendToServer(new TransportControlPacketC2S(blockEntityPos, action, seekSeconds));
    }

    private final Signal<String> selectedPlaylistId=Signals.of("");
    private final Signal<String> activePlaylistId=Signals.of("");
    private final Signal<List<com.nstut.simplyspeakers.playlist.PlaylistLibrarySnapshot.Entry>> savedPlaylists=Signals.of(List.of());
    private final Signal<List<String>> activePlaylistIds=Signals.of(List.of()),activePlaylistNames=Signals.of(List.of());
    private String pendingPlaylistName="";
    private final Signal<String> playlistSearch=Signals.of("");
    private PlaylistSyncPacketS2C latestPlaylistSnapshot;
    private String selectedPlaylistName() {
        return savedPlaylists.get().stream().filter(p -> p.id().equals(selectedPlaylistId.get())).map(p -> p.name()).findFirst().orElse(tr("no_playlists").getString());
    }
    private UIComponent buildPlaylistView() {
        ButtonWidget selector=new SpeakerMarqueeButton(() -> Component.literal(savedPlaylists.get().isEmpty()?tr("no_playlists").getString():playlistLabel(selectedPlaylistName(),playlistIds.get().size())),24) {
            @Override public void render(net.minecraft.client.gui.GuiGraphicsExtractor g,net.minecraft.client.gui.Font font,int mx,int my,float pt) {
                super.render(g,font,mx,my,pt);
                for(int row=0;row<4;row++)g.fill(getX()+getWidth()-17+row,getY()+getHeight()/2-2+row,getX()+getWidth()-10-row,getY()+getHeight()/2-1+row,colors().onSurface());
            }
        }.onPress(() -> chooseSavedPlaylist(null)).ghost().small();selector.height(24);selector.flex();
        return Ui.column(Ui.row(selector,
                new SpeakerIconButton(Icon.PLAY,tr("play_playlist"),() -> sendPlaylistOp(PlaylistControlPacketC2S.OP_PLAY_PLAYLIST,-1,true,"","")).enabledWhen(() -> !playlistIds.get().isEmpty()),
                new SpeakerIconButton(Icon.PLUS,tr("create_playlist"),() -> playlistNameDialog(PlaylistControlPacketC2S.OP_CREATE_PLAYLIST)),
                new SpeakerIconButton(Icon.MORE,tr("playlist_actions"),() -> trackMenu()
                    .item(tr("rename_playlist"),() -> playlistNameDialog(PlaylistControlPacketC2S.OP_RENAME_PLAYLIST))
                    .item(tr("duplicate_playlist"),() -> playlistNameDialog(PlaylistControlPacketC2S.OP_DUPLICATE_PLAYLIST))
                    .separator().item(tr("clear_playlist"),this::confirmClearPlaylist)
                    .item(tr("delete_playlist"),this::confirmDeletePlaylist).show(uiRuntime().overlays())).enabledWhen(() -> !selectedPlaylistId.get().isEmpty()))
                    .align(Alignment.CENTER).gap(4).key("playlist.toolbar"),
                Ui.switcher(playlistEmpty).when(true,() -> Ui.emptyState(tr("playlist_empty")))
                    .when(false,() -> Ui.list(playlistRows,this::buildPlaylistRow).key(row -> row.audioId()+":"+row.index()).itemHeight(24).gap(2).flex()).flex()).gap(6);
    }
    /** Selecting here changes only this screen's editor, not the network playback source. */
    private void chooseSavedPlaylist(AudioFileMetadata addTrack) {
        OverlayHandle[] handle={null};
        Signal<String> query=playlistSearch; query.set("");
        var matches=Signals.computed(() -> savedPlaylists.get().stream().filter(entry ->
            entry.name().toLowerCase(java.util.Locale.ROOT).contains(query.get().trim().toLowerCase(java.util.Locale.ROOT))).toList());
        var list=Ui.list(matches,entry -> {
            var row=new SpeakerMarqueeButton(() -> Component.literal(playlistLabel(entry.name(),entry.audioIds().size())),6).onPress(() -> {
                if(addTrack==null) {selectedPlaylistId.set(entry.id());refreshSelectedPlaylist();}
                else sendToServer(new PlaylistControlPacketC2S(blockEntityPos,PlaylistControlPacketC2S.OP_ADD,-1,false,addTrack.getUuid(),"",entry.id()));
                handle[0].close();
            }).ghost().small();row.tooltip(Component.literal(playlistLabel(entry.name(),entry.audioIds().size())));row.setActive(entry.id().equals(selectedPlaylistId.get()));return row;
        }).key(e -> e.id()).itemHeight(24).gap(3).height(Math.max(48,Math.min(savedPlaylists.get().size()*27,Math.min(120,height-130))));
        Card dialog=Ui.card(Ui.column(Ui.heading(tr(addTrack==null?"choose_playlist":"add_playlist")),
            Ui.textField(query).placeholder(tr("search_playlists").getString()).fillWidth().key("playlist.search"),
            Ui.switcher(Signals.computed(() -> matches.get().isEmpty())).when(true,() -> Ui.text(tr("no_playlists_found"))).when(false,() -> Ui.spacer().height(0)),list,
            SpeakerButtonWidget.button(Component.translatable("gui.simplyspeakers.cancel"),() -> handle[0].close()).ghost()).gap(8)).padding(12);
        dialog.width(Math.min(280,width-40));handle[0]=Dialog.show(uiRuntime().overlays(),dialog);
    }
    private String playlistLabel(String name,int count) {
        return Component.translatable("gui.simplyspeakers.player.playlist_count",name,count,
            tr(count==1?"track_singular":"track_plural")).getString();
    }
    private void playlistNameDialog(byte op) {
        String targetId=selectedPlaylistId.get();
        boolean create=op==PlaylistControlPacketC2S.OP_CREATE_PLAYLIST,duplicate=op==PlaylistControlPacketC2S.OP_DUPLICATE_PLAYLIST;
        String key=create?"create_playlist":duplicate?"duplicate_playlist":"rename_playlist";
        Signal<String> name=Signals.of(create?"":duplicate?selectedPlaylistName().substring(0,Math.min(58,selectedPlaylistName().length()))+" copy":selectedPlaylistName());
        Signal<Component> error=Signals.of(Component.empty());OverlayHandle[] handle={null};
        Card dialog=Ui.card(Ui.column(Ui.heading(tr(key)),Ui.textField(name).placeholder(tr("playlist_name").getString()).fillWidth(),
            Ui.text((Supplier<Component>)error::get).wrap(),
            Ui.row(SpeakerButtonWidget.button(Component.translatable("gui.simplyspeakers.cancel"),() -> handle[0].close()).ghost(),
                SpeakerButtonWidget.button(tr(key),() -> {
                    String value=name.get().trim();
                    if(!com.nstut.simplyspeakers.SpeakerState.validPlaylistName(value) || savedPlaylists.get().stream().anyMatch(p -> p.name().equalsIgnoreCase(value) && (create || duplicate || !p.id().equals(targetId)))) {error.set(tr("invalid_playlist_name"));return;}
                    if((create || duplicate) && savedPlaylists.get().size()>=com.nstut.simplyspeakers.SpeakerState.MAX_SAVED_PLAYLISTS) {error.set(tr("playlist_limit"));return;}
                    if(create || duplicate)pendingPlaylistName=value;
                    sendToServer(new PlaylistControlPacketC2S(blockEntityPos,op,-1,false,"",value,create?"":targetId));handle[0].close();
                }).primary()).justify(Justification.CENTER).gap(6)).gap(8)).padding(12);
        dialog.width(Math.min(280,width-40));handle[0]=Dialog.show(uiRuntime().overlays(),dialog);
    }
    private void confirmDeletePlaylist() {
        String target=selectedPlaylistId.get();OverlayHandle[] handle={null};
        Card dialog=Ui.card(Ui.column(Ui.heading(tr("delete_playlist")),
            Ui.text(Component.translatable("gui.simplyspeakers.player.delete_playlist_confirm",selectedPlaylistName())).wrap(),
            Ui.row(SpeakerButtonWidget.button(Component.translatable("gui.simplyspeakers.cancel"),() -> handle[0].close()).ghost(),
                SpeakerButtonWidget.button(tr("delete_playlist"),() -> {sendToServer(new PlaylistControlPacketC2S(blockEntityPos,PlaylistControlPacketC2S.OP_DELETE_PLAYLIST,-1,false,"","",target));handle[0].close();}).danger()
                    .enabled(!selectedPlaylistId.get().isEmpty())).justify(Justification.CENTER).gap(6)).gap(8)).padding(12);
        dialog.width(Math.min(280,width-40));handle[0]=Dialog.show(uiRuntime().overlays(),dialog);
    }
    private void refreshSelectedPlaylist() {
        var selected=savedPlaylists.get().stream().filter(p -> p.id().equals(selectedPlaylistId.get())).findFirst().orElse(null);
        if(selected==null){playlistIds.set(List.of());playlistNames.set(List.of());playlistCursor.set(-1);return;}
        playlistIds.set(selected.audioIds());playlistNames.set(selected.filenames());
        playlistCursor.set(latestPlaylistSnapshot!=null && selected.id().equals(activePlaylistId.get())?latestPlaylistSnapshot.getPlayingIndex():-1);
    }

    private void confirmClearPlaylist() {
        String target=selectedPlaylistId.get();
        OverlayHandle[] handle = { null };
        Card dialog = Ui.card(Ui.column(Ui.heading(tr("clear_playlist")), Ui.text(tr("clear_playlist_confirm")),
                Ui.row(SpeakerButtonWidget.button(Component.translatable("gui.simplyspeakers.cancel"), () -> handle[0].close()).ghost(),
                        SpeakerButtonWidget.button(tr("clear_playlist"), () -> {
                            handle[0].close(); sendToServer(new PlaylistControlPacketC2S(blockEntityPos,PlaylistControlPacketC2S.OP_CLEAR,-1,false,"","",target));
                        }).danger()).gap(6)).gap(8)).padding(12);
        dialog.width(Math.min(280, width - 40));
        handle[0] = Dialog.show(uiRuntime().overlays(), dialog);
    }

    private record QueueRow(int section, int index, String audioId, String title) { }
    private final Computed<Boolean> playlistEmpty = Signals.computed(() -> playlistIds.get().isEmpty());
    // One viewport scrolls both groups, so short windows never compress rows below their height.
    private final Computed<List<QueueRow>> queueRows = Signals.computed(() -> {
        List<QueueRow> rows = new java.util.ArrayList<>();
        List<String> queued = queueIds.get();
        for (int i = 0; i < queued.size(); i++) rows.add(new QueueRow(1, i, queued.get(i), ""));
        if (queued.isEmpty() && upcomingIndices.get().isEmpty()) rows.add(new QueueRow(0, -2, "", "nothing_queued"));
        for (int index : upcomingIndices.get()) {
            if (index >= 0 && index < activePlaylistIds.get().size()) rows.add(new QueueRow(2, index,
                    activePlaylistIds.get().get(index), index < activePlaylistNames.get().size() ? activePlaylistNames.get().get(index) : ""));
        }
        return rows;
    });

    private UIComponent buildQueueView() {
        return Ui.column(Ui.row(Ui.heading(tr("queue")).flex().tooltip(tr("queue_order_hint")),
                SpeakerButtonWidget.button(tr("clear_queue"), () -> sendPlaylistOp(PlaylistControlPacketC2S.OP_CLEAR_QUEUE, -1, false, "", "")).ghost().small()).align(Alignment.CENTER).gap(6),
                Ui.list(queueRows, this::buildQueueRow).key(row -> row.section() + ":" + row.index() + ":" + row.audioId())
                        .itemHeight(24).gap(2).flex()).gap(6);
    }

    private UIComponent buildQueueRow(QueueRow row) {
        if (row.section() == 0) return Ui.row(Ui.text(tr(row.title())).nowrap().marquee().flex()).align(Alignment.CENTER);
        if (row.section() == 2) return Ui.row(Ui.text(Component.literal((queueIds.get().size()+upcomingIndices.get().indexOf(row.index())+1)+".")).width(22),
            Ui.text(Component.literal(row.title())).nowrap().marquee().flex(),
            Ui.text(tr("source_playlist")).nowrap().tooltip(tr("queue_order_hint")),Ui.spacer().width(24)).align(Alignment.CENTER).gap(4);
        return Ui.row(Ui.text(Component.literal((row.index()+1)+".")).width(22),Ui.text(Component.literal(filenameOf(row.audioId()))).nowrap().marquee().flex(),
                Ui.text(tr("source_request")).nowrap().tooltip(tr("queue_order_hint")),
                new SpeakerIconButton(Icon.MORE, tr("track_actions"), () -> trackMenu()
                        .item(tr("move_up"), () -> sendPlaylistOp(PlaylistControlPacketC2S.OP_QUEUE_UP, row.index(), false, "", ""))
                        .item(tr("move_down"), () -> sendPlaylistOp(PlaylistControlPacketC2S.OP_QUEUE_DOWN, row.index(), false, "", ""))
                        .item(tr("remove_queue"), () -> sendPlaylistOp(PlaylistControlPacketC2S.OP_REMOVE_QUEUED, row.index(), false, "", ""))
                        .show(uiRuntime().overlays())).ghost().small().width(24)).align(Alignment.CENTER).gap(4);
    }

    private ButtonWidget shuffleButton() {
        return new SpeakerIconButton(() -> Icon.SHUFFLE,
                () -> Component.translatable(playlistShuffle.get() ? "gui.simplyspeakers.player.shuffle_on" : "gui.simplyspeakers.player.shuffle_off"),
                () -> {
                    boolean newValue = !playlistShuffle.get();
                    playlistShuffle.set(newValue);
                    sendToServer(new PlaylistControlPacketC2S(blockEntityPos,
                            PlaylistControlPacketC2S.OP_SET_SHUFFLE, -1, newValue, "", ""));
                });
    }

    private ButtonWidget repeatCycleButton() {
        return new SpeakerIconButton(() -> Icon.REPEAT,
                () -> tr("repeat_" + RepeatMode.fromIndex(playlistRepeat.get()).id()),
                () -> {
                    int next = (playlistRepeat.get() + 1) % RepeatMode.values().length;
                    playlistRepeat.set(next);
                    sendToServer(new PlaylistControlPacketC2S(blockEntityPos,
                            PlaylistControlPacketC2S.OP_SET_REPEAT, next, false, "", ""));
                });
    }



    private void sendPlaylistOp(byte op, int index, boolean flag, String audioId, String filename) {
        sendToServer(new PlaylistControlPacketC2S(blockEntityPos, op, index, flag, audioId, filename,selectedPlaylistId.get()));
    }

    private UIComponent buildPlaylistRow(PlaylistRow row) {
        return Ui.row(new SpeakerIconButton(Icon.PLAY, tr("play_now"), () -> sendPlaylistOp(
                        PlaylistControlPacketC2S.OP_SELECT_INDEX, row.index(), true, "", "")),
                Ui.text(Component.literal(row.filename())).nowrap().marquee().flex(),
                new SpeakerIconButton(Icon.MORE, tr("track_actions"), () -> trackMenu()
                        .item(tr("play_next"), () -> sendPlaylistOp(PlaylistControlPacketC2S.OP_QUEUE_NEXT, -1, false, row.audioId(), ""))
                        .item(tr("add_queue"), () -> sendPlaylistOp(PlaylistControlPacketC2S.OP_QUEUE_LAST, -1, false, row.audioId(), ""))
                        .item(tr("move_up"), () -> sendPlaylistOp(PlaylistControlPacketC2S.OP_MOVE_UP, row.index(), false, "", ""))
                        .item(tr("move_down"), () -> sendPlaylistOp(PlaylistControlPacketC2S.OP_MOVE_DOWN, row.index(), false, "", ""))
                        .separator().item(tr("remove_playlist"), () -> sendPlaylistOp(
                                PlaylistControlPacketC2S.OP_REMOVE_INDEX, row.index(), false, row.audioId(), ""))
                        .show(uiRuntime().overlays())).ghost().small().tooltip(tr("track_actions")).width(24)).align(Alignment.CENTER).gap(4);
    }


    private final Signal<com.nstut.simplyspeakers.permissions.AccessViewSnapshot> accessView =
            Signals.of(com.nstut.simplyspeakers.permissions.AccessViewSnapshot.EMPTY);
    private Component accessText(String key) { return Component.translatable("gui.simplyspeakers.access."+key); }
    private Component streamText(String key) { return Component.translatable("gui.simplyspeakers.stream."+key); }
    public void updateAccessModel(com.nstut.simplyspeakers.permissions.AccessViewSnapshot policy) { accessView.set(policy); }

    private Component ownerDisplayName() {
        var owner = accessView.get().owner();
        var client = Minecraft.getInstance();
        if (client.player != null && owner.equals(client.player.getUUID())) return accessText("you");
        var info = client.getConnection() == null ? null : client.getConnection().getPlayerInfo(owner);
        return Component.literal(info == null ? accessView.get().ownerName() : info.getProfile().name());
    }

    private UIComponent buildAccessGroup() {
        return Ui.column(Ui.heading(accessText("title")),
            Ui.row(Ui.text(accessText("owner")).nowrap(),
                Ui.text(() -> accessView.get().owner()==null?accessText("unclaimed"):ownerDisplayName()).nowrap().marquee().flex(),
                Ui.switcher(Signals.computed(() -> accessView.get().owner()==null))
                    .when(true,() -> SpeakerButtonWidget.button(accessText("claim"),() -> sendToServer(new SpeakerPolicyPacketC2S(blockEntityPos,SpeakerPolicyPacketC2S.OP_CLAIM_OWNER,0,false,"")))
                        .small().enabledWhen(() -> accessView.get().canManage()))
                    .when(false,() -> new SpeakerIconButton(Icon.MORE,accessText("change_owner"),() -> playerPolicyDialog(true)).enabledWhen(() -> accessView.get().canManage()))
            ).align(Alignment.CENTER).gap(6),
            Ui.row(Ui.text(accessText("mode")).nowrap().marquee().flex().tooltip(Component.translatable("gui.simplyspeakers.access.tooltip")),
                new SpeakerMarqueeButton(() -> accessText("mode."+accessView.get().access().id()),6)
                    .reserveLabels(java.util.Arrays.stream(com.nstut.simplyspeakers.SpeakerAccess.values())
                        .map(mode -> accessText("mode."+mode.id())).toArray(Component[]::new))
                    .onPress(this::chooseAccessMode).ghost().small().enabledWhen(() -> accessView.get().canManage())
                    .flex().key("settings.access_mode")
            ).align(Alignment.CENTER).gap(6),
            Ui.row(Ui.text(() -> Component.translatable("gui.simplyspeakers.access.trusted_count",accessView.get().trusted().size())).nowrap().marquee().flex(),
                SpeakerButtonWidget.button(accessText("manage"),this::manageTrustedPlayers).ghost().small()
                    .enabledWhen(() -> accessView.get().canManage()).key("settings.trusted")
            ).align(Alignment.CENTER).gap(6)
        ).gap(8).key("settings.access");
    }
    private void chooseAccessMode() {
        var menu=trackMenu();
        for(var mode:com.nstut.simplyspeakers.SpeakerAccess.values())
            menu.item(accessText("mode."+mode.id()),() -> sendToServer(SpeakerPolicyPacketC2S.accessMode(blockEntityPos,mode)));
        menu.show(uiRuntime().overlays());
    }
    private void playerPolicyDialog(boolean transfer) {
        OverlayHandle[] handle={null}; Signal<String> player=Signals.of(""); Signal<Component> error=Signals.of(Component.empty());
        Card dialog=Ui.card(Ui.column(Ui.heading(accessText(transfer?"change_owner":"add_trusted")),
            Ui.text(accessText(transfer?"transfer_help":"player_help")).wrap(),
            Ui.textField(player).maxLength(36).placeholder(accessText("player_placeholder").getString()).fillWidth().key("access.player"),
            Ui.text((Supplier<Component>)error::get).wrap(),
            Ui.row(SpeakerButtonWidget.button(Component.translatable("gui.simplyspeakers.cancel"),() -> handle[0].close()).ghost().small(),
                SpeakerButtonWidget.button(accessText(transfer?"transfer":"add"),() -> {
                    String value=player.get().trim();
                    if(!validPlayerInput(value)){error.set(accessText("invalid_player"));return;}
                    sendToServer(new SpeakerPolicyPacketC2S(blockEntityPos,transfer?SpeakerPolicyPacketC2S.OP_TRANSFER_OWNER:SpeakerPolicyPacketC2S.OP_TRUST_CHANGE,0,true,value));
                    handle[0].close();
                }).primary().small().enabledWhen(() -> accessView.get().canManage())
            ).align(Alignment.CENTER).justify(Justification.CENTER).gap(6)
        ).gap(8)).padding(12);dialog.width(Math.min(320,width-40));handle[0]=Dialog.show(uiRuntime().overlays(),dialog);
    }
    private static boolean validPlayerInput(String value) {
        try { return java.util.UUID.fromString(value).toString().equalsIgnoreCase(value); }
        catch(IllegalArgumentException e){return value.matches("[A-Za-z0-9_]{1,16}");}
    }
    private void manageTrustedPlayers() {
        OverlayHandle[] handle={null};
        Card dialog=buildTrustedPlayersDialog(() -> handle[0].close(),
            () -> {handle[0].close();playerPolicyDialog(false);});
        handle[0]=Dialog.show(uiRuntime().overlays(),dialog);
    }
    private Card buildTrustedPlayersDialog(Runnable close, Runnable addPlayer) {
        var members=Signals.computed(() -> accessView.get().trusted());
        var list=Ui.list(members,player -> Ui.row(
            Ui.text(Component.literal(player.name())).nowrap().marquee().flex().tooltip(Component.literal(player.uuid().toString())),
            new SpeakerIconButton(Icon.TRASH,accessText("remove_trusted"),() -> sendToServer(SpeakerPolicyPacketC2S.trust(blockEntityPos,player.uuid(),false)))
                .enabledWhen(() -> accessView.get().canManage())
        ).align(Alignment.CENTER).gap(6)).key(p -> p.uuid()).itemHeight(24).gap(3).height(Math.max(24,Math.min(Math.min(120,Math.max(24,height-180)),members.get().size()*27-3)));
        Card dialog=Ui.card(Ui.column(Ui.heading(accessText("manage_trusted")),
            Ui.text(accessText("trusted_help")).wrap(),
            Ui.switcher(Signals.computed(() -> members.get().isEmpty()))
                .when(true,() -> Ui.text(accessText("none_trusted")).wrap())
                .when(false,() -> list),
            Ui.row(SpeakerButtonWidget.button(accessText("add_trusted"),addPlayer).primary().small()
                .enabledWhen(() -> accessView.get().canManage() && members.get().size()<com.nstut.simplyspeakers.permissions.AccessViewSnapshot.MAX_TRUSTED),
                SpeakerButtonWidget.button(Component.translatable("gui.simplyspeakers.close"),close).ghost().small()
            ).justify(Justification.CENTER).gap(6)
        ).gap(8)).padding(12);dialog.width(Math.min(320,width-40));return dialog;
    }
    private void addStreamDialog() {
        OverlayHandle[] handle={null};Signal<String> url=Signals.of("");Signal<Component> error=Signals.of(Component.empty());
        java.util.function.Consumer<Byte> submit=op -> {
            String value=url.get().trim();
            if(!accessView.get().streamsAllowed()){error.set(streamText("disabled"));return;}
            if(value.length()>256 || !com.nstut.simplyspeakers.audio.StreamTracks.hasSupportedExtension(value)
                    || !com.nstut.simplyspeakers.audio.StreamTracks.isRemoteStreamUrlAllowed(value,false)) {error.set(streamText("invalid"));return;}
            if(!accessView.get().canControl()){error.set(accessText("no_control"));return;}
            handle[0].close();
            if(op==PlaylistControlPacketC2S.OP_ADD) chooseSavedPlaylist(new AudioFileMetadata(value,value));
            else sendPlaylistOp(op,-1,true,value,"");
        };
        Card dialog=Ui.card(Ui.column(Ui.heading(streamText("add")),Ui.text(streamText("help")).wrap(),
            Ui.textField(url).maxLength(2048).placeholder("https://example.com/audio.mp3").fillWidth().key("stream.url"),
            Ui.switcher(Signals.computed(() -> !accessView.get().streamsAllowed())).when(true,() -> Ui.text(streamText("disabled")).wrap()).when(false,() -> Ui.spacer().height(0)),
            Ui.text((Supplier<Component>)error::get).wrap(),
            Ui.row(new SpeakerIconButton(Icon.PLAY,tr("play_now"),() -> submit.accept(PlaylistControlPacketC2S.OP_PLAY_AUDIO)).enabledWhen(() -> accessView.get().streamsAllowed() && accessView.get().canControl()).primary(),
                new SpeakerIconButton(Icon.PLUS,tr("add_queue"),() -> submit.accept(PlaylistControlPacketC2S.OP_QUEUE_LAST)).enabledWhen(() -> accessView.get().streamsAllowed() && accessView.get().canControl()),
                SpeakerButtonWidget.button(streamText("playlist"),() -> submit.accept(PlaylistControlPacketC2S.OP_ADD)).ghost().small().enabledWhen(() -> accessView.get().streamsAllowed() && accessView.get().canControl() && !savedPlaylists.get().isEmpty())
            ).justify(Justification.CENTER).gap(6),
            SpeakerButtonWidget.button(Component.translatable("gui.simplyspeakers.cancel"),() -> handle[0].close()).ghost().small()
        ).gap(8)).padding(12);dialog.width(Math.min(360,width-40));handle[0]=Dialog.show(uiRuntime().overlays(),dialog);
    }

    private UIComponent buildPolicyCard() {
        return Ui.column(
                Ui.row(Ui.heading(Component.translatable("gui.simplyspeakers.directional.title")).flex(),
                    SpeakerButtonWidget.button(Component.translatable("gui.simplyspeakers.directional.preview"),() -> {
                        com.nstut.simplyspeakers.client.DirectionalPreview.show(blockEntityPos,maxRange.get(),audioDropoff.get(),directionality.get(),coneAngle.get(),rearAttenuation.get());
                        onClose();
                    }).small().tooltip(Component.translatable("gui.simplyspeakers.directional.preview.tooltip")).key("settings.directional_preview")).gap(6),
                directionalSliderRow(Component.translatable("gui.simplyspeakers.directionality"), directionality, 0.0, 1.0,
                        () -> sendToServer(SpeakerPolicyPacketC2S.directionality(blockEntityPos, directionality.get().floatValue())), "directionality"),
                directionalSliderRow(Component.translatable("gui.simplyspeakers.cone_angle"), coneAngle, 5.0, 350.0,
                        () -> sendToServer(SpeakerPolicyPacketC2S.coneAngle(blockEntityPos, (int) Math.round(coneAngle.get()))), "cone_angle"),
                directionalSliderRow(Component.translatable("gui.simplyspeakers.rear_attenuation"), rearAttenuation, 0.0, 1.0,
                        () -> sendToServer(SpeakerPolicyPacketC2S.rearAttenuation(blockEntityPos, rearAttenuation.get().floatValue())), "rear_attenuation")
        ).gap(8);
    }

    private final Signal<Double> directionality = Signals.of(0.0);
    private final Signal<Double> coneAngle = Signals.of(90.0);
    private final Signal<Double> rearAttenuation = Signals.of(0.9);

    private final java.util.List<Subscription> guiSubs = new java.util.ArrayList<>();

    private UIComponent directionalSliderRow(Component label, Signal<Double> signal,
                                             double min, double max, Runnable commit, String setting) {
        Component help = Component.translatable("gui.simplyspeakers." + setting + ".tooltip");
        Slider slider = Ui.slider(signal, min, max);
        slider.fillWidth(); slider.tooltip(help); slider.key("settings." + setting);
        UIComponent column = Ui.column(
                Ui.row(Ui.text(label).nowrap().marquee().tooltip(help),
                        Ui.text(() -> Component.literal(max - min > 1.5 ? Math.round(signal.get()) + "°" : Math.round(signal.get()*100) + "%"))
                                .nowrap().tooltip(help).key("settings." + setting + ".value")).justify(Justification.SPACE_BETWEEN).align(Alignment.CENTER).gap(6).fillWidth(), Ui.switcher(Signals.computed(() -> accessView.get().canManage())).when(true,() -> slider).when(false,() -> Ui.spacer().height(0))).gap(4).tooltip(help);
        guiSubs.add(signal.subscribe(v -> {
            if (!guiReady || applyingRemoteState || !accessView.get().canManage()) return;
            commit.run();
        }));
        return column;
    }

    /** Called by {@link PlaylistSyncPacketS2C} handlers on every authoritative change. */
    public void updatePlaylistCatalog(com.nstut.simplyspeakers.playlist.PlaylistLibrarySnapshot library) {
            savedPlaylists.set(library.entries());activePlaylistId.set(library.activeId());
            var created=savedPlaylists.get().stream().filter(p -> p.name().equals(pendingPlaylistName)).findFirst().orElse(null);
            if(created!=null) {selectedPlaylistId.set(created.id());pendingPlaylistName="";}
            if(savedPlaylists.get().stream().noneMatch(p -> p.id().equals(selectedPlaylistId.get())))selectedPlaylistId.set(savedPlaylists.get().stream().anyMatch(p -> p.id().equals(activePlaylistId.get()))?activePlaylistId.get():savedPlaylists.get().isEmpty()?"":savedPlaylists.get().get(0).id());
        refreshSelectedPlaylist();
    }
    public void updatePlaylistModel(PlaylistSyncPacketS2C packet) {
        applyingRemoteState = true;
        try {
            latestPlaylistSnapshot=packet;
            activePlaylistIds.set(List.copyOf(packet.getAudioIds()));activePlaylistNames.set(List.copyOf(packet.getFilenames()));
            if(packet.hasLibrary())updatePlaylistCatalog(packet.getLibrary());
            refreshSelectedPlaylist();
            playlistShuffle.set(packet.isShuffle());
            playlistRepeat.set(Math.max(0, packet.getRepeatOrdinal()));
            paused.set(packet.isPaused());
            var view = packet.getPlayerView();
            if (!playingAudioId.get().equals(view.audioId())) scrubbing = false;
            queueIds.set(view.queue()); upcomingIndices.set(view.upcoming());
            playing.set(view.playing()); playingAudioId.set(view.audioId()); playingFilename.set(view.filename());
            double length = view.durationSeconds();
            if (length <= 0) for (var audio : audioFiles.get()) if (audio.getUuid().equals(view.audioId())) length = audio.getDurationSeconds();
            duration.set(length); seekable.set(view.playing() && length > 0 && !view.audioId().startsWith("http") && accessView.get().canControl());
            snapshotPosition = view.positionSeconds();
            snapshotTick = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
            if (!scrubbing) { position.set(snapshotPosition); seekFraction.set(length > 0 ? snapshotPosition / length : 0); }
        } finally { applyingRemoteState = false; }
    }

    private void wireControlSubscriptions() {
        subs.add(maxVolume.subscribe(v -> { if (!applyingRemoteState && accessView.get().canControl()) {
            if (speaker != null) speaker.setMaxVolumeClient(v.floatValue());
            sendToServer(new UpdateMaxVolumePacketC2S(blockEntityPos, v.floatValue()));
        } }));
        subs.add(maxRange.subscribe(v -> { if (!applyingRemoteState && accessView.get().canControl()) {
            if (speaker != null) speaker.setMaxRangeClient((int) Math.round(v));
            sendToServer(new UpdateMaxRangePacketC2S(blockEntityPos, (int) Math.round(v)));
        } }));
        subs.add(audioDropoff.subscribe(v -> { if (!applyingRemoteState && accessView.get().canControl()) {
            if (speaker != null) speaker.setAudioDropoffClient(v.floatValue());
            sendToServer(new UpdateAudioDropoffPacketC2S(blockEntityPos, v.floatValue()));
        } }));

    }

    private void closeControlSubscriptions() {
        for (Subscription subscription : subs) subscription.close();
        subs.clear();
    }



    private void confirmDelete(AudioFileMetadata audio) {
        Card card = Ui.card().elevated(true).outlined(true).padding(14);
        OverlayHandle[] handle = { null };
        ButtonWidget cancelBtn = SpeakerButtonWidget.button(Component.translatable("gui.simplyspeakers.cancel"),
                () -> { if (handle[0] != null) handle[0].close(); }).ghost();
        ButtonWidget deleteBtn = SpeakerButtonWidget.button(Component.translatable("gui.simplyspeakers.delete"), () -> {
            if (handle[0] != null) handle[0].close();
            sendToServer(new DeleteAudioPacketC2S(audio.getUuid()));
        }).danger();
        card.addChild(Ui.column(
                Ui.heading(Component.translatable("gui.simplyspeakers.delete_confirm.title")),
                Ui.text(Component.translatable("gui.simplyspeakers.delete_confirm.body", audio.getOriginalFilename())),
                Ui.row(cancelBtn, deleteBtn).gap(6)
        ).gap(10));
        card.width(Math.min(280, width - 40)).minHeight(90);
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
            sendToServer(
                    new RequestUploadAudioPacketC2S(blockEntityPos, transactionId, file.getName(), file.length()));
        });
    }

    private String filenameOf(String uuid) {
        for (AudioFileMetadata a : audioFiles.get()) {
            if (a.getUuid().equals(uuid)) return a.effectiveDisplayName();
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

    public void refreshFromState(SpeakerStateUpdatePacketS2C packet) {
        applyingRemoteState = true;
        try {
            String audioId = packet.getAudioId();
            playingAudioId.set(audioId);
            playingFilename.set(packet.getAudioFilename());
            if (speaker != null) speaker.setAudioIdClient(audioId, packet.getAudioFilename());
            String action = packet.getAction();
            if ("play".equals(action) || "pause".equals(action) || "stop".equals(action)) {
                boolean stopped = "stop".equals(action);
                playing.set(!stopped);
                paused.set("pause".equals(action));
                snapshotTick = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime();
                // Keep the displayed position until the next full snapshot supplies the seek/pause offset.
                snapshotPosition = stopped ? 0 : position.get();
                if (!scrubbing) {
                    position.set(snapshotPosition);
                    seekFraction.set(duration.get() > 0 ? snapshotPosition / duration.get() : 0);
                }
                seekable.set(!stopped && duration.get() > 0 && !audioId.startsWith("http") && accessView.get().canControl());
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

    /** Dimension-qualified registry key of this screen's speaker; used to match network-wide sync packets. */
    public String getFullStateKey() {
        return speaker != null ? speaker.getFullStateKey() : "";
    }

    protected void fetchDataFromBlockEntity() {
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
        hasStatus.close();
        playlistRows.close(); queueRows.close();
        playlistEmpty.close();
        super.removed();
    }
}
