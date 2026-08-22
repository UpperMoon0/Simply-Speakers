package com.nstut.simplyspeakers.client.screens;

import com.nstut.openui.api.Ui;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.api.VStack;
import com.nstut.openui.api.HStack;
import com.nstut.openui.controls.Card;
import com.nstut.openui.controls.EmptyState;
import com.nstut.openui.controls.Slider;
import com.nstut.openui.controls.TextField;
import com.nstut.openui.layout.Alignment;
import com.nstut.openui.layout.Justification;
import com.nstut.openui.state.Signal;
import com.nstut.openui.state.Signals;
import com.nstut.openui.state.Subscription;
import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.blocks.entities.ProxySpeakerBlockEntity;
import com.nstut.simplyspeakers.client.ui.SimplySpeakersUiScreen;
import dev.architectury.networking.NetworkManager;
import com.nstut.simplyspeakers.network.SetSpeakerIdPacketC2S;
import com.nstut.simplyspeakers.network.UpdateProxyAudioDropoffPacketC2S;
import com.nstut.simplyspeakers.network.UpdateProxyMaxRangePacketC2S;
import com.nstut.simplyspeakers.network.UpdateProxyMaxVolumePacketC2S;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Proxy Speaker configuration screen, migrated to OpenUI.
 *
 * <p>Preserves all existing proxy packet semantics: speaker id, max volume, max range and
 * audio dropoff are previewed locally on the client BE and forwarded through the dedicated
 * proxy packets. No audio list is shown (the proxy follows its linked speaker's selection).</p>
 */
public class ProxySpeakerScreen extends SimplySpeakersUiScreen {
    private static final int PANEL_WIDTH = 256;

    private final BlockPos blockEntityPos;
    private ProxySpeakerBlockEntity speaker;

    private final Signal<String> speakerId = Signals.of("");
    private final Signal<Double> maxVolume = Signals.of(1.0);
    private final Signal<Double> maxRange = Signals.of(16.0);
    private final Signal<Double> audioDropoff = Signals.of(1.0);

    private final List<Subscription> sliderSubs = new ArrayList<>();

    public ProxySpeakerScreen(BlockPos blockEntityPos) {
        super(Component.translatable("gui.simplyspeakers.proxy_speaker.title"));
        this.blockEntityPos = blockEntityPos;
    }

    @Override
    protected void init() {
        fetchDataFromBlockEntity();
        if (speaker != null) {
            speakerId.set(speaker.getSpeakerId());
            maxVolume.set((double) speaker.getMaxVolume());
            maxRange.set((double) speaker.getMaxRange());
            audioDropoff.set((double) speaker.getAudioDropoff());
        }
        super.init();
    }

    @Override
    protected UIComponent buildUI() {
        VStack panel = Ui.column(
                buildHeader(),
                speaker != null ? buildSettingsCard() : buildNotFound()
        ).gap(10);
        panel.width(PANEL_WIDTH);
        return Ui.padding(16, Ui.stack(panel).align(Alignment.CENTER, Alignment.CENTER));
    }

    private UIComponent buildHeader() {
        return Ui.row(
                Ui.heading(Component.translatable("gui.simplyspeakers.proxy_speaker.title")),
                buildThemeToggle()
        ).justify(Justification.SPACE_BETWEEN);
    }

    private UIComponent buildNotFound() {
        return Ui.card(
                Ui.emptyState(Component.translatable("gui.simplyspeakers.proxy_speaker.not_found"))
        ).outlined(true).padding(16);
    }

    private UIComponent buildSettingsCard() {
        Card card = Ui.card().outlined(true).padding(12);
        card.addChild(Ui.column(
                buildSpeakerIdRow(),
                Ui.divider(),
                sliderRow(
                        Component.translatable("gui.simplyspeakers.max_volume"),
                        () -> Component.translatable("gui.simplyspeakers.max_volume.slider", (int) (maxVolume.get() * 100)),
                        maxVolume, 0.0, 1.0,
                        v -> sendProxyVolume(v)
                ),
                sliderRow(
                        Component.translatable("gui.simplyspeakers.max_range"),
                        () -> Component.translatable("gui.simplyspeakers.max_range.slider", (int) (double) maxRange.get()),
                        maxRange, 1.0, Config.speakerRange,
                        v -> sendProxyRange(v)
                ),
                sliderRow(
                        Component.translatable("gui.simplyspeakers.audio_dropoff"),
                        () -> Component.translatable("gui.simplyspeakers.audio_dropoff.slider", (int) (audioDropoff.get() * 100)),
                        audioDropoff, 0.0, 1.0,
                        v -> sendProxyDropoff(v)
                ),
                Ui.text(Component.translatable("gui.simplyspeakers.proxy.helper"))
        ).gap(10));
        return card;
    }

    private UIComponent buildSpeakerIdRow() {
        return Ui.row(
                Ui.textField(speakerId)
                        .placeholder(Component.translatable("gui.simplyspeakers.speaker_id.placeholder").getString())
                        .flex(),
                Ui.button(Component.translatable("gui.simplyspeakers.save"), () -> {
                    if (speaker != null) {
                        String newId = speakerId.get();
                        speaker.setSpeakerIdClient(newId);
                        NetworkManager.sendToServer(new SetSpeakerIdPacketC2S(blockEntityPos, newId));
                    }
                }).primary()
        ).gap(6);
    }

    private UIComponent sliderRow(Component label, Supplier<Component> valueSupplier,
                                   Signal<Double> signal, double min, double max, Consumer<Double> packetSender) {
        Slider slider = Ui.slider(signal, min, max);
        slider.fillWidth();
        sliderSubs.add(signal.subscribe(packetSender::accept));
        return Ui.column(
                Ui.row(Ui.text(label), Ui.text(valueSupplier)).justify(Justification.SPACE_BETWEEN),
                slider
        ).gap(4);
    }

    private void sendProxyVolume(double v) {
        float f = (float) v;
        if (speaker != null) speaker.setMaxVolumeClient(f);
        NetworkManager.sendToServer(new UpdateProxyMaxVolumePacketC2S(blockEntityPos, f));
    }

    private void sendProxyRange(double v) {
        int i = (int) Math.round(v);
        if (speaker != null) speaker.setMaxRangeClient(i);
        NetworkManager.sendToServer(new UpdateProxyMaxRangePacketC2S(blockEntityPos, i));
    }

    private void sendProxyDropoff(double v) {
        float f = (float) v;
        if (speaker != null) speaker.setAudioDropoffClient(f);
        NetworkManager.sendToServer(new UpdateProxyAudioDropoffPacketC2S(blockEntityPos, f));
    }

    private void fetchDataFromBlockEntity() {
        if (Minecraft.getInstance().level == null) {
            this.speaker = null;
            return;
        }
        BlockEntity blockEntity = Minecraft.getInstance().level.getBlockEntity(blockEntityPos);
        if (blockEntity instanceof ProxySpeakerBlockEntity proxy) {
            this.speaker = proxy;
        } else {
            this.speaker = null;
        }
    }

    @Override
    public void removed() {
        for (Subscription s : sliderSubs) s.close();
        sliderSubs.clear();
        super.removed();
    }
}
