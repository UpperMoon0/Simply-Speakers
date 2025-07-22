// Language: java
package com.nstut.simplyspeakers.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.client.ClientAudioPlayer;
import com.nstut.simplyspeakers.client.gui.widgets.SpeakerAudioList;
import com.nstut.simplyspeakers.network.*;
import com.nstut.simplyspeakers.platform.Services;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

import java.io.File;

public class SpeakerScreen extends Screen {

    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation(SimplySpeakers.MOD_ID, "textures/gui/speaker.png");

    private static final int SCREEN_WIDTH = 176;
    private static final int SCREEN_HEIGHT = 166;

    private final BlockPos blockEntityPos;
    private SpeakerBlockEntity speaker;
    private SpeakerAudioList audioListWidget;
    private Button loopToggleButton;
    private Button playButton;
    private Button uploadButton;

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

        this.audioListWidget = new SpeakerAudioList(guiLeft + 10, guiTop + 20, SCREEN_WIDTH - 20, 100, Component.empty());
        this.addRenderableWidget(this.audioListWidget);

        PacketRegistries.CHANNEL.sendToServer(new RequestAudioListPacketC2S(this.blockEntityPos));

        this.playButton = Button.builder(Component.literal("Play"), button -> {
                    var selected = audioListWidget.getSelected();
                    if (selected != null) {
                        PacketRegistries.CHANNEL.sendToServer(new SelectAudioPacketC2S(this.blockEntityPos, selected.getUuid()));
                    }
                })
                .pos(guiLeft + 10, guiTop + 130)
                .size(50, 20)
                .build();
        this.addRenderableWidget(playButton);

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
                .pos(guiLeft + 70, guiTop + 130)
                .size(50, 20)
                .build();
        this.addRenderableWidget(uploadButton);

        this.loopToggleButton = Button.builder(getLoopButtonTextComponent(), button -> {
                    if (this.speaker == null) return;
                    boolean newLoopState = !this.speaker.isLooping();
                    PacketRegistries.CHANNEL.sendToServer(new ToggleLoopPacketC2S(this.blockEntityPos, newLoopState));
                    this.speaker.setLoopingClient(newLoopState);
                    button.setMessage(getLoopButtonTextComponent());
                })
                .pos(guiLeft + 130, guiTop + 130)
                .size(50, 20)
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

        if (this.speaker != null) {
            this.audioListWidget.setPlayingAudioId(this.speaker.getAudioId());
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
}
