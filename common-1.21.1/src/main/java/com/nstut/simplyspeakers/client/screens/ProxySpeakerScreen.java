// Language: java
package com.nstut.simplyspeakers.client.screens;

import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.entities.ProxySpeakerBlockEntity;
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

    private static final int SCREEN_WIDTH = 162;
    private static final int SCREEN_HEIGHT = 168; 

    private final BlockPos blockEntityPos;
    private ProxySpeakerBlockEntity speaker;
    private Button audioTabButton;
    private Button settingsTabButton;
    private int currentTab = 0; // 0 = audio tab, 1 = settings tab
    
    // Container classes for tab content
    private class AudioTabContent {
        EditBox speakerIdField;
        Button saveIdButton;
        
        void setVisible(boolean visible) {
            if (speakerIdField != null) speakerIdField.visible = visible;
            if (saveIdButton != null) saveIdButton.visible = visible;
        }
    }
    
    private class SettingsTabContent {
        SettingsSlider maxVolumeSlider;
        SettingsSlider maxRangeSlider;
        SettingsSlider audioDropoffSlider;
        
        void setVisible(boolean visible) {
            if (maxVolumeSlider != null) maxVolumeSlider.visible = visible;
            if (maxRangeSlider != null) maxRangeSlider.visible = visible;
            if (audioDropoffSlider != null) audioDropoffSlider.visible = visible;
        }
    }
    
    private AudioTabContent audioTabContent = new AudioTabContent();
    private SettingsTabContent settingsTabContent = new SettingsTabContent();

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

        // Create tab buttons
        this.audioTabButton = Button.builder(Component.translatable("gui.simplyspeakers.tab.audio"), button -> {
                    this.currentTab = 0;
                    updateVisibility();
                })
                .pos(guiLeft + 10, guiTop + 25)
                .size(50, 20)
                .build();

        this.settingsTabButton = Button.builder(Component.translatable("gui.simplyspeakers.tab.settings"), button -> {
                    this.currentTab = 1;
                    updateVisibility();
                })
                .pos(guiLeft + 65, guiTop + 25)
                .size(60, 20)
                .build();

        this.addRenderableWidget(this.audioTabButton);
        this.addRenderableWidget(this.settingsTabButton);

        // Audio tab components
        this.audioTabContent.speakerIdField = new EditBox(this.font, guiLeft + 10, guiTop + 63, SCREEN_WIDTH - 80, 20, Component.translatable("gui.simplyspeakers.speaker_id.placeholder"));
        if (this.speaker != null) {
            this.audioTabContent.speakerIdField.setValue(this.speaker.getSpeakerId());
        }
        this.audioTabContent.speakerIdField.setTooltip(Tooltip.create(Component.translatable("gui.simplyspeakers.proxy_speaker_id.tooltip")));

        this.audioTabContent.saveIdButton = Button.builder(Component.translatable("gui.simplyspeakers.save"), button -> {
                    if (this.speaker != null) {
                        String newId = this.audioTabContent.speakerIdField.getValue();
                        // Optimistically update the client-side speaker entity
                        this.speaker.setSpeakerIdClient(newId);
                        NetworkManager.sendToServer(new SetSpeakerIdPacketC2S(this.blockEntityPos, newId));
                    }
                })
                .pos(guiLeft + SCREEN_WIDTH - 55, guiTop + 63)
                .size(45, 20)
                .build();

        // Settings tab components
        if (this.speaker != null) {
            this.settingsTabContent.maxVolumeSlider = new SettingsSlider(
                    guiLeft + 10, guiTop + 65, SCREEN_WIDTH - 20, 20,
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
                    guiLeft + 10, guiTop + 100, SCREEN_WIDTH - 20, 20,
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
                    guiLeft + 10, guiTop + 135, SCREEN_WIDTH - 20, 20,
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
        this.addRenderableWidget(this.audioTabContent.speakerIdField);
        this.addRenderableWidget(this.audioTabContent.saveIdButton);
        
        if (this.settingsTabContent.maxVolumeSlider != null) {
            this.addRenderableWidget(this.settingsTabContent.maxVolumeSlider);
        }
        if (this.settingsTabContent.maxRangeSlider != null) {
            this.addRenderableWidget(this.settingsTabContent.maxRangeSlider);
        }
        if (this.settingsTabContent.audioDropoffSlider != null) {
            this.addRenderableWidget(this.settingsTabContent.audioDropoffSlider);
        }

        // Set initial visibility
        updateVisibility();
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
        
        int guiLeft = (this.width - SCREEN_WIDTH) / 2;
        int guiTop = (this.height - SCREEN_HEIGHT) / 2;
        
        Component title = Component.translatable("gui.simplyspeakers.proxy_speaker.title");
        guiGraphics.drawString(this.font, title, guiLeft + (SCREEN_WIDTH - this.font.width(title)) / 2, guiTop + 10, 4210752, false);
        
        // Draw tab-specific content
        if (currentTab == 0) {
            // Audio tab labels
            guiGraphics.drawString(this.font, Component.translatable("gui.simplyspeakers.speaker_id"), guiLeft + 10, guiTop + 53, 4210752, false);
        } else if (currentTab == 1) {
            // Settings tab labels
            if (this.settingsTabContent.maxVolumeSlider != null) {
                guiGraphics.drawString(this.font, Component.translatable("gui.simplyspeakers.max_volume"), guiLeft + 10, guiTop + 55, 4210752, false);
            }
            if (this.settingsTabContent.maxRangeSlider != null) {
                guiGraphics.drawString(this.font, Component.translatable("gui.simplyspeakers.max_range", Config.speakerRange), guiLeft + 10, guiTop + 90, 4210752, false);
            }
            if (this.settingsTabContent.audioDropoffSlider != null) {
                guiGraphics.drawString(this.font, Component.translatable("gui.simplyspeakers.audio_dropoff"), guiLeft + 10, guiTop + 125, 4210752, false);
            }
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
    
    private void updateVisibility() {
        boolean isAudioTab = (currentTab == 0);
        boolean isSettingsTab = (currentTab == 1);
        
        // Update visibility of tab content containers
        this.audioTabContent.setVisible(isAudioTab);
        this.settingsTabContent.setVisible(isSettingsTab);
    }
}
