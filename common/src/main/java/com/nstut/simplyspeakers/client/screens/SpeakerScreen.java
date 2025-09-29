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
    private static final int SCREEN_HEIGHT = 194;

    private final BlockPos blockEntityPos;
    private SpeakerBlockEntity speaker;
    private SpeakerAudioList audioListWidget;
    private EditBox searchBar;
    private EditBox speakerIdField;
    private Button loopToggleButton;
    private Button uploadButton;
    private Button saveIdButton;
    private Button audioTabButton;
    private Button settingsTabButton;
    private SettingsSlider maxVolumeSlider;
    private SettingsSlider maxRangeSlider;
    private SettingsSlider audioDropoffSlider;
    private Component statusMessage;
    private int currentTab = 0; // 0 = audio tab, 1 = settings tab

    public SpeakerScreen(BlockPos blockEntityPos) {
        super(Component.literal("Speaker Screen"));
        this.blockEntityPos = blockEntityPos;
    }

    @Override
    protected void init() {
        super.init();

        fetchDataFromBlockEntity();

        int guiLeft = (this.width - SCREEN_WIDTH) / 2;
        int guiTop = (this.height - SCREEN_HEIGHT) / 2;

        // Create tab buttons
        this.audioTabButton = Button.builder(Component.literal("Audio"), button -> {
                    this.currentTab = 0;
                    updateVisibility();
                })
                .pos(guiLeft + 10, guiTop + 10)
                .size(50, 20)
                .build();

        this.settingsTabButton = Button.builder(Component.literal("Settings"), button -> {
                    this.currentTab = 1;
                    updateVisibility();
                })
                .pos(guiLeft + 65, guiTop + 10)
                .size(60, 20)
                .build();

        this.addRenderableWidget(this.audioTabButton);
        this.addRenderableWidget(this.settingsTabButton);

        // Audio tab components
        this.speakerIdField = new EditBox(this.font, guiLeft + 10, guiTop + 33, SCREEN_WIDTH - 80, 20, Component.literal("Speaker ID"));
        if (this.speaker != null) {
            this.speakerIdField.setValue(this.speaker.getSpeakerId());
        }

        this.saveIdButton = Button.builder(Component.literal("Save"), button -> {
                    if (this.speaker != null) {
                        String newId = this.speakerIdField.getValue();
                        // Optimistically update the client-side speaker entity
                        this.speaker.setSpeakerId(newId);
                        PacketRegistries.CHANNEL.sendToServer(new SetSpeakerIdPacketC2S(this.blockEntityPos, newId));
                    }
                })
                .pos(guiLeft + SCREEN_WIDTH - 55, guiTop + 33)
                .size(45, 20)
                .build();

        this.audioListWidget = new SpeakerAudioList(guiLeft + 10, guiTop + 100, SCREEN_WIDTH - 20, 60, Component.empty(), (audio) -> {
            if (this.speaker != null) {
                this.speaker.setAudioIdClient(audio.getUuid(), audio.getOriginalFilename());
            }
            PacketRegistries.CHANNEL.sendToServer(new SelectAudioPacketC2S(this.blockEntityPos, audio.getUuid(), audio.getOriginalFilename()));
        });

        this.searchBar = new EditBox(this.font, guiLeft + 10, guiTop + 70, SCREEN_WIDTH - 20, 20, Component.literal("Search..."));
        this.searchBar.setResponder(this.audioListWidget::filter);

        this.uploadButton = Button.builder(Component.literal("Upload"), button -> {
                    SimplySpeakers.LOGGER.info("Upload button clicked");
                    Services.CLIENT.openFileDialog("mp3,wav", (file) -> {
                        if (file != null) {
                            SimplySpeakers.LOGGER.info("File selected: " + file.getName());
                            // Validate file extension before starting upload
                            String fileName = file.getName().toLowerCase();
                            if (!fileName.endsWith(".mp3") && !fileName.endsWith(".wav")) {
                                SimplySpeakers.LOGGER.warn("Invalid file type selected: " + file.getName());
                                setStatusMessage(Component.literal("Invalid file type. Only MP3 and WAV files are supported."));
                                return;
                            }
                            var transactionId = ClientAudioPlayer.startUpload(file);
                            PacketRegistries.CHANNEL.sendToServer(new RequestUploadAudioPacketC2S(this.blockEntityPos, transactionId, file.getName(), file.length()));
                        } else {
                            SimplySpeakers.LOGGER.info("No file selected");
                        }
                    });
                })
                .pos(guiLeft + 18, guiTop + 165)
                .size(50, 20)
                .build();
        this.uploadButton.visible = !Config.disableUpload;

        // Settings tab components
        if (this.speaker != null) {
            this.maxVolumeSlider = new SettingsSlider(
                    guiLeft + 10, guiTop + 35, SCREEN_WIDTH - 20, 20,
                    Component.literal("Max Volume: "),
                    this.speaker.getMaxVolume(),
                    0.0, 1.0,
                    value -> Component.literal(String.format("Max Volume: %d%%", (int) (value * 100))),
                    value -> PacketRegistries.CHANNEL.sendToServer(new UpdateMaxVolumePacketC2S(this.blockEntityPos, (float) value))
            );

            this.maxRangeSlider = new SettingsSlider(
                    guiLeft + 10, guiTop + 65, SCREEN_WIDTH - 20, 20,
                    Component.literal("Max Range: "),
                    this.speaker.getMaxRange(),
                    1, Config.speakerRange,
                    value -> Component.literal(String.format("Max Range: %d", (int) value)),
                    value -> PacketRegistries.CHANNEL.sendToServer(new UpdateMaxRangePacketC2S(this.blockEntityPos, (int) value))
            );

            this.audioDropoffSlider = new SettingsSlider(
                    guiLeft + 10, guiTop + 95, SCREEN_WIDTH - 20, 20,
                    Component.literal("Audio Dropoff: "),
                    this.speaker.getAudioDropoff(),
                    0.0, 1.0,
                    value -> Component.literal(String.format("Audio Dropoff: %d%%", (int) (value * 100))),
                    value -> PacketRegistries.CHANNEL.sendToServer(new UpdateAudioDropoffPacketC2S(this.blockEntityPos, (float) value))
            );
            
            this.loopToggleButton = Button.builder(getLoopButtonTextComponent(), button -> {
                        if (this.speaker == null) return;
                        boolean newLoopState = !this.speaker.isLooping();
                        this.speaker.setLoopingClient(newLoopState);
                        button.setMessage(getLoopButtonTextComponent());
                        PacketRegistries.CHANNEL.sendToServer(new ToggleLoopPacketC2S(this.blockEntityPos, newLoopState));
                    })
                    .pos(guiLeft + 10, guiTop + 125)
                    .size(80, 20)
                    .build();
        }

        // Add all widgets
        this.addRenderableWidget(this.speakerIdField);
        this.addRenderableWidget(this.saveIdButton);
        this.addRenderableWidget(this.searchBar);
        this.addRenderableWidget(this.audioListWidget);
        this.addRenderableWidget(this.uploadButton);
        
        if (this.maxVolumeSlider != null) {
            this.addRenderableWidget(this.maxVolumeSlider);
        }
        if (this.maxRangeSlider != null) {
            this.addRenderableWidget(this.maxRangeSlider);
        }
        if (this.audioDropoffSlider != null) {
            this.addRenderableWidget(this.audioDropoffSlider);
        }
        if (this.loopToggleButton != null) {
            this.addRenderableWidget(this.loopToggleButton);
        }

        PacketRegistries.CHANNEL.sendToServer(new RequestAudioListPacketC2S(this.blockEntityPos));
        
        // Set initial visibility
        updateVisibility();
    }

    private Component getLoopButtonTextComponent() {
        boolean looping = (this.speaker != null) && this.speaker.isLooping();
        return Component.literal("Loop: " + (looping ? "ON" : "OFF"));
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        RenderSystem.setShaderTexture(0, BACKGROUND_TEXTURE);
        int guiLeft = (this.width - SCREEN_WIDTH) / 2;
        int guiTop = (this.height - SCREEN_HEIGHT) / 2;
        guiGraphics.blit(BACKGROUND_TEXTURE, guiLeft, guiTop, 0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
        guiGraphics.drawString(this.font, Component.literal("Speaker"), guiLeft + (SCREEN_WIDTH - this.font.width("Speaker")) / 2, guiTop + 10, 4210752, false);
        
        // Draw tab-specific content
        if (currentTab == 0) {
            // Audio tab labels
            guiGraphics.drawString(this.font, Component.literal("Speaker ID:"), guiLeft + 10, guiTop + 23, 4210752, false);
            guiGraphics.drawString(this.font, Component.literal("Search:"), guiLeft + 10, guiTop + 60, 4210752, false);
        } else if (currentTab == 1) {
            // Settings tab labels
            if (this.maxVolumeSlider != null) {
                guiGraphics.drawString(this.font, Component.literal("Max Volume (0-100%):"), guiLeft + 10, guiTop + 25, 4210752, false);
            }
            if (this.maxRangeSlider != null) {
                guiGraphics.drawString(this.font, Component.literal("Max Range (1-" + Config.speakerRange + "):"), guiLeft + 10, guiTop + 55, 4210752, false);
            }
            if (this.audioDropoffSlider != null) {
                guiGraphics.drawString(this.font, Component.literal("Audio Dropoff (0-100%):"), guiLeft + 10, guiTop + 85, 4210752, false);
            }
            if (this.loopToggleButton != null) {
                guiGraphics.drawString(this.font, Component.literal("Loop Settings:"), guiLeft + 10, guiTop + 115, 4210752, false);
            }
        }

        if (this.speaker != null) {
            this.audioListWidget.setPlayingAudioId(this.speaker.getAudioId());
            this.audioListWidget.setSelectedAudioId(this.speaker.getAudioId());
        }

        if (statusMessage != null) {
            guiGraphics.drawString(this.font, statusMessage, guiLeft + (SCREEN_WIDTH - this.font.width(statusMessage)) / 2, guiTop + 200, 16777215, false);
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
        this.audioListWidget.setAudioList(audioList);
        
        // Set the selected audio in the list based on the speaker's current audio
        if (this.speaker != null) {
            this.audioListWidget.setSelectedAudioId(this.speaker.getAudioId());
        }
    }
    
    private void updateVisibility() {
        boolean isAudioTab = (currentTab == 0);
        boolean isSettingsTab = (currentTab == 1);
        
        // Audio tab components
        this.speakerIdField.visible = isAudioTab;
        this.saveIdButton.visible = isAudioTab;
        this.searchBar.visible = isAudioTab;
        this.audioListWidget.visible = isAudioTab;
        this.uploadButton.visible = isAudioTab && !Config.disableUpload;
        
        // Settings tab components
        if (this.maxVolumeSlider != null) {
            this.maxVolumeSlider.visible = isSettingsTab;
        }
        if (this.maxRangeSlider != null) {
            this.maxRangeSlider.visible = isSettingsTab;
        }
        if (this.audioDropoffSlider != null) {
            this.audioDropoffSlider.visible = isSettingsTab;
        }
        if (this.loopToggleButton != null) {
            this.loopToggleButton.visible = isSettingsTab;
        }
    }
}
