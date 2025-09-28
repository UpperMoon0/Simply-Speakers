// Language: java
package com.nstut.simplyspeakers.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nstut.simplyspeakers.Config;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import com.nstut.simplyspeakers.client.gui.widgets.SpeakerAudioList;
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
    private static final int SCREEN_HEIGHT = 158;

    private final BlockPos blockEntityPos;
    private SpeakerBlockEntity speaker;
    private SpeakerAudioList audioListWidget;
    private EditBox searchBar;
    private EditBox speakerIdField;
    private Button loopToggleButton;
    private Button uploadButton;
    private Button saveIdButton;
    private Component statusMessage;

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

        this.audioListWidget = new SpeakerAudioList(guiLeft + 10, guiTop + 80, SCREEN_WIDTH - 20, 60, Component.empty(), (audio) -> {
            if (this.speaker != null) {
                this.speaker.setAudioIdClient(audio.getUuid(), audio.getOriginalFilename());
            }
            PacketRegistries.CHANNEL.sendToServer(new SelectAudioPacketC2S(this.blockEntityPos, audio.getUuid(), audio.getOriginalFilename()));
        });

        this.speakerIdField = new EditBox(this.font, guiLeft + 10, guiTop + 23, SCREEN_WIDTH - 50, 20, Component.literal("Speaker ID"));
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
                .pos(guiLeft + SCREEN_WIDTH - 55, guiTop + 23)
                .size(45, 20)
                .build();

        this.searchBar = new EditBox(this.font, guiLeft + 10, guiTop + 50, SCREEN_WIDTH - 20, 20, Component.literal("Search..."));
        this.searchBar.setResponder(this.audioListWidget::filter);

        this.addRenderableWidget(this.speakerIdField);
        this.addRenderableWidget(this.saveIdButton);
        this.addRenderableWidget(this.searchBar);
        this.addRenderableWidget(this.audioListWidget);

        PacketRegistries.CHANNEL.sendToServer(new RequestAudioListPacketC2S(this.blockEntityPos));

        this.uploadButton = Button.builder(Component.literal("Upload"), button -> {
                    SimplySpeakers.LOGGER.info("Upload button clicked");
                    Services.CLIENT.openFileDialog("mp3,wav", (file) -> {
                        if (file != null) {
                            SimplySpeakers.LOGGER.info("File selected: " + file.getName());
                            var transactionId = ClientAudioPlayer.startUpload(file);
                            PacketRegistries.CHANNEL.sendToServer(new RequestUploadAudioPacketC2S(this.blockEntityPos, transactionId, file.getName(), file.length()));
                        } else {
                            SimplySpeakers.LOGGER.info("No file selected");
                        }
                    });
                })
                .pos(guiLeft + 18, guiTop + 145)
                .size(50, 20)
                .build();
        this.uploadButton.visible = !Config.disableUpload;
        this.addRenderableWidget(uploadButton);

        this.loopToggleButton = Button.builder(getLoopButtonTextComponent(), button -> {
                    if (this.speaker == null) return;
                    boolean newLoopState = !this.speaker.isLooping();
                    this.speaker.setLoopingClient(newLoopState);
                    button.setMessage(getLoopButtonTextComponent());
                    PacketRegistries.CHANNEL.sendToServer(new ToggleLoopPacketC2S(this.blockEntityPos, newLoopState));
                })
                .pos(guiLeft + 78, guiTop + 145)
                .size(60, 20)
                .build();
        this.loopToggleButton.active = (this.speaker != null);
        this.addRenderableWidget(this.loopToggleButton);
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
        
        // Draw label for speaker ID field
        guiGraphics.drawString(this.font, Component.literal("Speaker ID:"), guiLeft + 10, guiTop + 13, 4210752, false);
        
        // Draw label for search bar
        guiGraphics.drawString(this.font, Component.literal("Search:"), guiLeft + 10, guiTop + 40, 4210752, false);

        if (this.speaker != null) {
            this.audioListWidget.setPlayingAudioId(this.speaker.getAudioId());
            this.audioListWidget.setSelectedAudioId(this.speaker.getAudioId());
        }

        if (statusMessage != null) {
            guiGraphics.drawString(this.font, statusMessage, guiLeft + (SCREEN_WIDTH - this.font.width(statusMessage)) / 2, guiTop + 165, 16777215, false);
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
}
