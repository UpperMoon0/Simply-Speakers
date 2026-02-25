// Language: java
package com.nstut.simplyspeakers.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import com.nstut.simplyspeakers.client.gui.widgets.SpeakerAudioList;
import com.nstut.simplyspeakers.client.gui.widgets.SettingsSlider;
import com.nstut.simplyspeakers.network.*;
import com.nstut.simplyspeakers.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class SpeakerScreen extends Screen {

    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation(SimplySpeakers.MOD_ID, "textures/gui/speaker.png");

    private static final int SCREEN_WIDTH = 162;
    private static final int SCREEN_HEIGHT = 224;

    private final BlockPos blockEntityPos;
    private SpeakerBlockEntity speaker;
    private SpeakerAudioList audioListWidget;
    private Button audioTabButton;
    private Button settingsTabButton;
    private Component statusMessage;
    private int currentTab = 0; // 0 = audio tab, 1 = settings tab
    
    // Container classes for tab content
    private class AudioTabContent {
        EditBox speakerIdField;
        Button saveIdButton;
        EditBox searchBar;
        SpeakerAudioList audioListWidget;
        Button uploadButton;
        
        void setVisible(boolean visible) {
            if (speakerIdField != null) speakerIdField.visible = visible;
            if (saveIdButton != null) saveIdButton.visible = visible;
            if (searchBar != null) searchBar.visible = visible;
            if (audioListWidget != null) audioListWidget.visible = visible;
            if (uploadButton != null) uploadButton.visible = visible && !Config.disableUpload;
        }
    }
    
    private class SettingsTabContent {
        SettingsSlider maxVolumeSlider;
        SettingsSlider maxRangeSlider;
        SettingsSlider audioDropoffSlider;
        Button loopToggleButton;
        
        void setVisible(boolean visible) {
            if (maxVolumeSlider != null) maxVolumeSlider.visible = visible;
            if (maxRangeSlider != null) maxRangeSlider.visible = visible;
            if (audioDropoffSlider != null) audioDropoffSlider.visible = visible;
            if (loopToggleButton != null) loopToggleButton.visible = visible;
        }
    }
    
    private AudioTabContent audioTabContent = new AudioTabContent();
    private SettingsTabContent settingsTabContent = new SettingsTabContent();

    public SpeakerScreen(BlockPos blockEntityPos) {
        super(Component.translatable("gui.simplyspeakers.speaker.title"));
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
        this.audioTabContent.speakerIdField.setTooltip(Tooltip.create(Component.translatable("gui.simplyspeakers.speaker_id.tooltip")));

        this.audioTabContent.saveIdButton = Button.builder(Component.translatable("gui.simplyspeakers.save"), button -> {
                    if (this.speaker != null) {
                        String newId = this.audioTabContent.speakerIdField.getValue();
                        // Optimistically update the client-side speaker entity
                        this.speaker.setSpeakerId(newId);
                        PacketRegistries.CHANNEL.sendToServer(new SetSpeakerIdPacketC2S(this.blockEntityPos, newId));
                    }
                })
                .pos(guiLeft + SCREEN_WIDTH - 55, guiTop + 63)
                .size(45, 20)
                .build();

        this.audioTabContent.audioListWidget = new SpeakerAudioList(guiLeft + 10, guiTop + 130, SCREEN_WIDTH - 20, 60, Component.empty(), (audio) -> {
            if (this.speaker != null) {
                this.speaker.setAudioIdClient(audio.getUuid(), audio.getOriginalFilename());
            }
            PacketRegistries.CHANNEL.sendToServer(new SelectAudioPacketC2S(this.blockEntityPos, audio.getUuid(), audio.getOriginalFilename()));
        });

        this.audioTabContent.searchBar = new EditBox(this.font, guiLeft + 10, guiTop + 100, SCREEN_WIDTH - 20, 20, Component.translatable("gui.simplyspeakers.search.placeholder"));
        this.audioTabContent.searchBar.setResponder(this.audioTabContent.audioListWidget::filter);
        this.audioTabContent.searchBar.setTooltip(Tooltip.create(Component.translatable("gui.simplyspeakers.search.tooltip")));

        this.audioTabContent.uploadButton = Button.builder(Component.translatable("gui.simplyspeakers.upload"), button -> {
                    SimplySpeakers.LOGGER.info("Upload button clicked");
                    Services.CLIENT.openFileDialog("mp3,wav", (file) -> {
                        if (file != null) {
                            SimplySpeakers.LOGGER.info("File selected: " + file.getName());
                            // Validate file extension before starting upload
                            String fileName = file.getName().toLowerCase();
                            if (!fileName.endsWith(".mp3") && !fileName.endsWith(".wav")) {
                                SimplySpeakers.LOGGER.warn("Invalid file type selected: " + file.getName());
                                setStatusMessage(Component.translatable("gui.simplyspeakers.upload.invalid_type"));
                                return;
                            }
                            var transactionId = ClientAudioPlayer.startUpload(file);
                            PacketRegistries.CHANNEL.sendToServer(new RequestUploadAudioPacketC2S(this.blockEntityPos, transactionId, file.getName(), file.length()));
                        } else {
                            SimplySpeakers.LOGGER.info("No file selected");
                        }
                    });
                })
                .pos(guiLeft + (SCREEN_WIDTH - 100) / 2, guiTop + 195)
                .size(100, 20)
                .build();
        this.audioTabContent.uploadButton.visible = !Config.disableUpload;

        // Settings tab components
        if (this.speaker != null) {
            this.settingsTabContent.maxVolumeSlider = new SettingsSlider(
                    guiLeft + 10, guiTop + 65, SCREEN_WIDTH - 20, 20,
                    Component.translatable("gui.simplyspeakers.max_volume.slider"),
                    this.speaker.getMaxVolume(),
                    0.0, 1.0,
                    value -> Component.translatable("gui.simplyspeakers.max_volume.slider", (int) (value * 100)),
                    value -> PacketRegistries.CHANNEL.sendToServer(new UpdateMaxVolumePacketC2S(this.blockEntityPos, (float) value))
            );
            this.settingsTabContent.maxVolumeSlider.setTooltip(Tooltip.create(Component.translatable("gui.simplyspeakers.max_volume.tooltip")));

            this.settingsTabContent.maxRangeSlider = new SettingsSlider(
                    guiLeft + 10, guiTop + 100, SCREEN_WIDTH - 20, 20,
                    Component.translatable("gui.simplyspeakers.max_range.slider"),
                    this.speaker.getMaxRange(),
                    1, Config.speakerRange,
                    value -> Component.translatable("gui.simplyspeakers.max_range.slider", (int) value),
                    value -> PacketRegistries.CHANNEL.sendToServer(new UpdateMaxRangePacketC2S(this.blockEntityPos, (int) value))
            );
            this.settingsTabContent.maxRangeSlider.setTooltip(Tooltip.create(Component.translatable("gui.simplyspeakers.max_range.tooltip")));

            this.settingsTabContent.audioDropoffSlider = new SettingsSlider(
                    guiLeft + 10, guiTop + 135, SCREEN_WIDTH - 20, 20,
                    Component.translatable("gui.simplyspeakers.audio_dropoff.slider"),
                    this.speaker.getAudioDropoff(),
                    0.0, 1.0,
                    value -> Component.translatable("gui.simplyspeakers.audio_dropoff.slider", (int) (value * 100)),
                    value -> PacketRegistries.CHANNEL.sendToServer(new UpdateAudioDropoffPacketC2S(this.blockEntityPos, (float) value))
            );
            this.settingsTabContent.audioDropoffSlider.setTooltip(Tooltip.create(Component.translatable("gui.simplyspeakers.audio_dropoff.tooltip")));
            
            this.settingsTabContent.loopToggleButton = Button.builder(getLoopButtonTextComponent(), button -> {
                        if (this.speaker == null) return;
                        boolean newLoopState = !this.speaker.isLooping();
                        this.speaker.setLoopingClient(newLoopState);
                        button.setMessage(getLoopButtonTextComponent());
                        PacketRegistries.CHANNEL.sendToServer(new ToggleLoopPacketC2S(this.blockEntityPos, newLoopState));
                    })
                    .pos(guiLeft + 10, guiTop + 165)
                    .size(80, 20)
                    .build();
            this.settingsTabContent.loopToggleButton.setTooltip(Tooltip.create(Component.translatable("gui.simplyspeakers.loop.tooltip")));
        }

        // Add all widgets
        this.addRenderableWidget(this.audioTabContent.speakerIdField);
        this.addRenderableWidget(this.audioTabContent.saveIdButton);
        this.addRenderableWidget(this.audioTabContent.searchBar);
        this.addRenderableWidget(this.audioTabContent.audioListWidget);
        this.addRenderableWidget(this.audioTabContent.uploadButton);
        
        if (this.settingsTabContent.maxVolumeSlider != null) {
            this.addRenderableWidget(this.settingsTabContent.maxVolumeSlider);
        }
        if (this.settingsTabContent.maxRangeSlider != null) {
            this.addRenderableWidget(this.settingsTabContent.maxRangeSlider);
        }
        if (this.settingsTabContent.audioDropoffSlider != null) {
            this.addRenderableWidget(this.settingsTabContent.audioDropoffSlider);
        }
        if (this.settingsTabContent.loopToggleButton != null) {
            this.addRenderableWidget(this.settingsTabContent.loopToggleButton);
        }

        PacketRegistries.CHANNEL.sendToServer(new RequestAudioListPacketC2S(this.blockEntityPos));
        
        // Set initial visibility
        updateVisibility();
    }

    private Component getLoopButtonTextComponent() {
        boolean looping = (this.speaker != null) && this.speaker.isLooping();
        return Component.translatable(looping ? "gui.simplyspeakers.loop.on" : "gui.simplyspeakers.loop.off");
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        RenderSystem.setShaderTexture(0, BACKGROUND_TEXTURE);
        int guiLeft = (this.width - SCREEN_WIDTH) / 2;
        int guiTop = (this.height - SCREEN_HEIGHT) / 2;
        guiGraphics.blit(BACKGROUND_TEXTURE, guiLeft, guiTop, 0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
        Component title = Component.translatable("gui.simplyspeakers.speaker.title");
        guiGraphics.drawString(this.font, title, guiLeft + (SCREEN_WIDTH - this.font.width(title)) / 2, guiTop + 10, 4210752, false);
        
        // Draw tab-specific content
        if (currentTab == 0) {
            // Audio tab labels
            guiGraphics.drawString(this.font, Component.translatable("gui.simplyspeakers.speaker_id"), guiLeft + 10, guiTop + 53, 4210752, false);
            guiGraphics.drawString(this.font, Component.translatable("gui.simplyspeakers.search"), guiLeft + 10, guiTop + 90, 4210752, false);
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

        if (this.speaker != null && this.audioTabContent.audioListWidget != null) {
            this.audioTabContent.audioListWidget.setPlayingAudioId(this.speaker.getAudioId());
            this.audioTabContent.audioListWidget.setSelectedAudioId(this.speaker.getAudioId());
        }

        if (statusMessage != null) {
            guiGraphics.drawString(this.font, statusMessage, guiLeft + (SCREEN_WIDTH - this.font.width(statusMessage)) / 2, guiTop + 230, 16777215, false);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    private void fetchDataFromBlockEntity() {
        if (Minecraft.getInstance().level == null) {
            this.speaker = null;
            return;
        }
        BlockEntity blockEntity = Minecraft.getInstance().level.getBlockEntity(blockEntityPos);
        if (blockEntity instanceof SpeakerBlockEntity) {
            this.speaker = (SpeakerBlockEntity) blockEntity;
        } else {
            this.speaker = null;
        }
    }

    public SpeakerAudioList getAudioListWidget() {
        return audioListWidget;
    }

    public void setStatusMessage(Component statusMessage) {
        this.statusMessage = statusMessage;
    }

    public void updateAudioList(List<AudioFileMetadata> audioList) {
        ClientAudioPlayer.setAudioList(audioList);
        if (this.audioTabContent.audioListWidget != null) {
            this.audioTabContent.audioListWidget.setAudioList(audioList);
        
            // Set the selected audio in the list based on the speaker's current audio
            if (this.speaker != null) {
                this.audioTabContent.audioListWidget.setSelectedAudioId(this.speaker.getAudioId());
            }
        }
    }
    
    private void updateVisibility() {
        boolean isAudioTab = (currentTab == 0);
        boolean isSettingsTab = (currentTab == 1);
        
        // Update visibility of tab content containers
        this.audioTabContent.setVisible(isAudioTab);
        this.settingsTabContent.setVisible(isSettingsTab);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (currentTab == 0 && this.audioTabContent.audioListWidget != null && this.audioTabContent.audioListWidget.visible) {
            if (this.audioTabContent.audioListWidget.isMouseOver(mouseX, mouseY)) {
                return this.audioTabContent.audioListWidget.mouseScrolled(mouseX, mouseY, delta);
            }
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (currentTab == 0 && this.audioTabContent.audioListWidget != null && this.audioTabContent.audioListWidget.visible) {
            if (this.audioTabContent.audioListWidget.isMouseOver(mouseX, mouseY)) {
                return this.audioTabContent.audioListWidget.mouseDragged(mouseX, mouseY, button, dragX, dragY);
            }
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
}
