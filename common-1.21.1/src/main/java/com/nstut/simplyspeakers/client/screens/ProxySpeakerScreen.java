// Language: java
package com.nstut.simplyspeakers.client.screens;

import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.entities.ProxySpeakerBlockEntity;
import com.nstut.simplyspeakers.client.SpeakerGuiConstants;
import com.nstut.simplyspeakers.client.gui.widgets.SettingsSlider;
import com.nstut.simplyspeakers.network.*;
import dev.architectury.networking.NetworkManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

public class ProxySpeakerScreen extends Screen {

    private static final ResourceLocation BACKGROUND_TEXTURE = ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "textures/gui/proxy_speaker.png");

    private static final int SCREEN_WIDTH = SpeakerGuiConstants.SCREEN_WIDTH;
    private static final int SCREEN_HEIGHT = SpeakerGuiConstants.PROXY_SPEAKER_SCREEN_HEIGHT; 
    private static final int SETTINGS_Y_OFFSET = -SpeakerGuiConstants.TAB_BUTTON_HEIGHT;

    private final BlockPos blockEntityPos;
    private ProxySpeakerBlockEntity speaker;

    private class SettingsTabContent {
        EditBox speakerIdField;
        Button saveIdButton;
        SettingsSlider maxVolumeSlider;
        SettingsSlider maxRangeSlider;
        SettingsSlider audioDropoffSlider;
    }

    private final SettingsTabContent settingsTabContent = new SettingsTabContent();

    public ProxySpeakerScreen(BlockPos blockEntityPos) {
        super(Component.translatable("gui.simplyspeakers.proxy_speaker.title"));
        this.blockEntityPos = blockEntityPos;
    }

    @Override
    protected void init() {
        super.init();

        fetchDataFromBlockEntity();

        int guiLeft = (this.width - SCREEN_WIDTH) / 2;
        int guiTop = (this.height - SCREEN_HEIGHT) / 2;

        // Settings tab components
        this.settingsTabContent.speakerIdField = new EditBox(this.font, guiLeft + SpeakerGuiConstants.MARGIN_X, guiTop + SpeakerGuiConstants.SPEAKER_ID_FIELD_Y + SETTINGS_Y_OFFSET, SpeakerGuiConstants.SPEAKER_ID_FIELD_WIDTH, SpeakerGuiConstants.BUTTON_HEIGHT, Component.translatable("gui.simplyspeakers.speaker_id.placeholder"));
        if (this.speaker != null) {
            this.settingsTabContent.speakerIdField.setValue(this.speaker.getSpeakerId());
        }
        this.settingsTabContent.speakerIdField.setTooltip(Tooltip.create(Component.translatable("gui.simplyspeakers.proxy_speaker_id.tooltip")));

        this.settingsTabContent.saveIdButton = Button.builder(Component.translatable("gui.simplyspeakers.save"), button -> {
                    if (this.speaker != null) {
                        String newId = this.settingsTabContent.speakerIdField.getValue();
                        this.speaker.setSpeakerIdClient(newId);
                        NetworkManager.sendToServer(new SetSpeakerIdPacketC2S(this.blockEntityPos, newId));
                    }
                })
                .pos(guiLeft + SpeakerGuiConstants.SAVE_BUTTON_X, guiTop + SpeakerGuiConstants.SPEAKER_ID_FIELD_Y + SETTINGS_Y_OFFSET)
                .size(45, SpeakerGuiConstants.BUTTON_HEIGHT)
                .build();

        if (this.speaker != null) {
            this.settingsTabContent.maxVolumeSlider = new SettingsSlider(
                    guiLeft + SpeakerGuiConstants.MARGIN_X, guiTop + SpeakerGuiConstants.VOLUME_SLIDER_Y + SETTINGS_Y_OFFSET, SCREEN_WIDTH - 20, SpeakerGuiConstants.BUTTON_HEIGHT,
                    Component.translatable("gui.simplyspeakers.max_volume.slider"),
                    this.speaker.getMaxVolume(),
                    0.0, 1.0,
                    value -> Component.translatable("gui.simplyspeakers.max_volume.slider", (int) (value * 100)),
                    value -> {
                        if (this.speaker != null) {
                            this.speaker.setMaxVolumeClient((float) value);
                        }
                        NetworkManager.sendToServer(new UpdateProxyMaxVolumePacketC2S(this.blockEntityPos, (float) value));
                    }
            );
            this.settingsTabContent.maxVolumeSlider.setTooltip(Tooltip.create(Component.translatable("gui.simplyspeakers.proxy_max_volume.tooltip")));

            this.settingsTabContent.maxRangeSlider = new SettingsSlider(
                    guiLeft + SpeakerGuiConstants.MARGIN_X, guiTop + SpeakerGuiConstants.RANGE_SLIDER_Y + SETTINGS_Y_OFFSET, SCREEN_WIDTH - 20, SpeakerGuiConstants.BUTTON_HEIGHT,
                    Component.translatable("gui.simplyspeakers.max_range.slider"),
                    this.speaker.getMaxRange(),
                    1, Config.speakerRange,
                    value -> Component.translatable("gui.simplyspeakers.max_range.slider", (int) value),
                    value -> {
                        if (this.speaker != null) {
                            this.speaker.setMaxRangeClient((int) value);
                        }
                        NetworkManager.sendToServer(new UpdateProxyMaxRangePacketC2S(this.blockEntityPos, (int) value));
                    }
            );
            this.settingsTabContent.maxRangeSlider.setTooltip(Tooltip.create(Component.translatable("gui.simplyspeakers.proxy_max_range.tooltip")));

            this.settingsTabContent.audioDropoffSlider = new SettingsSlider(
                    guiLeft + SpeakerGuiConstants.MARGIN_X, guiTop + SpeakerGuiConstants.DROPOFF_SLIDER_Y + SETTINGS_Y_OFFSET, SCREEN_WIDTH - 20, SpeakerGuiConstants.BUTTON_HEIGHT,
                    Component.translatable("gui.simplyspeakers.audio_dropoff.slider"),
                    this.speaker.getAudioDropoff(),
                    0.0, 1.0,
                    value -> Component.translatable("gui.simplyspeakers.audio_dropoff.slider", (int) (value * 100)),
                    value -> {
                        if (this.speaker != null) {
                            this.speaker.setAudioDropoffClient((float) value);
                        }
                        NetworkManager.sendToServer(new UpdateProxyAudioDropoffPacketC2S(this.blockEntityPos, (float) value));
                    }
            );
            this.settingsTabContent.audioDropoffSlider.setTooltip(Tooltip.create(Component.translatable("gui.simplyspeakers.audio_dropoff.tooltip")));
        }

        // Add all widgets
        this.addRenderableWidget(this.settingsTabContent.speakerIdField);
        this.addRenderableWidget(this.settingsTabContent.saveIdButton);

        if (this.settingsTabContent.maxVolumeSlider != null) {
            this.addRenderableWidget(this.settingsTabContent.maxVolumeSlider);
        }
        if (this.settingsTabContent.maxRangeSlider != null) {
            this.addRenderableWidget(this.settingsTabContent.maxRangeSlider);
        }
        if (this.settingsTabContent.audioDropoffSlider != null) {
            this.addRenderableWidget(this.settingsTabContent.audioDropoffSlider);
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        
        int guiLeft = (this.width - SCREEN_WIDTH) / 2;
        int guiTop = (this.height - SCREEN_HEIGHT) / 2;
        
        Component title = Component.translatable("gui.simplyspeakers.proxy_speaker.title");
        guiGraphics.drawString(this.font, title, guiLeft + (SCREEN_WIDTH - this.font.width(title)) / 2, guiTop + 10, 4210752, false);
        
        guiGraphics.drawString(this.font, Component.translatable("gui.simplyspeakers.speaker_id"), guiLeft + SpeakerGuiConstants.MARGIN_X, guiTop + SpeakerGuiConstants.SPEAKER_ID_LABEL_Y + SETTINGS_Y_OFFSET, 4210752, false);
        if (this.settingsTabContent.maxVolumeSlider != null) {
            guiGraphics.drawString(this.font, Component.translatable("gui.simplyspeakers.max_volume"), guiLeft + SpeakerGuiConstants.MARGIN_X, guiTop + SpeakerGuiConstants.VOLUME_LABEL_Y + SETTINGS_Y_OFFSET, 4210752, false);
        }
        if (this.settingsTabContent.maxRangeSlider != null) {
            guiGraphics.drawString(this.font, Component.translatable("gui.simplyspeakers.max_range", Config.speakerRange), guiLeft + SpeakerGuiConstants.MARGIN_X, guiTop + SpeakerGuiConstants.RANGE_LABEL_Y + SETTINGS_Y_OFFSET, 4210752, false);
        }
        if (this.settingsTabContent.audioDropoffSlider != null) {
            guiGraphics.drawString(this.font, Component.translatable("gui.simplyspeakers.audio_dropoff"), guiLeft + SpeakerGuiConstants.MARGIN_X, guiTop + SpeakerGuiConstants.DROPOFF_LABEL_Y + SETTINGS_Y_OFFSET, 4210752, false);
        }
    }
    
    @Override
    public void renderBackground(GuiGraphics guiGraphics, int i, int j, float f) {
        super.renderBackground(guiGraphics, i, j, f);
        int guiLeft = (this.width - SCREEN_WIDTH) / 2;
        int guiTop = (this.height - SCREEN_HEIGHT) / 2;
        guiGraphics.blit(BACKGROUND_TEXTURE, guiLeft, guiTop, 0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
    }

    private void fetchDataFromBlockEntity() {
        if (Minecraft.getInstance().level == null) {
            this.speaker = null;
            return;
        }
        BlockEntity blockEntity = Minecraft.getInstance().level.getBlockEntity(blockEntityPos);
        if (blockEntity instanceof ProxySpeakerBlockEntity) {
            this.speaker = (ProxySpeakerBlockEntity) blockEntity;
        } else {
            this.speaker = null;
        }
    }
}
