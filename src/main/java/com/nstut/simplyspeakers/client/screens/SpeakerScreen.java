// Language: java
package com.nstut.simplyspeakers.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.network.LoadAudioCallPacketC2S;
import com.nstut.simplyspeakers.network.PacketRegistries;
import com.nstut.simplyspeakers.network.PlayAudioCallPacketC2S;
import com.nstut.simplyspeakers.network.StopAudioPacketS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

import java.util.logging.Logger;

public class SpeakerScreen extends Screen {

    private static final Logger LOGGER = Logger.getLogger(SpeakerScreen.class.getName());
    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation(SimplySpeakers.MOD_ID, "textures/gui/speaker.png");

    private static final int SCREEN_WIDTH = 162;
    private static final int SCREEN_HEIGHT = 128;

    private EditBox audioPathField;
    private final BlockPos blockEntityPos;
    private String initialAudioPath;

    public SpeakerScreen(BlockPos blockEntityPos) {
        super(Component.literal("Speaker Screen"));
        this.blockEntityPos = blockEntityPos;
    }

    @Override
    protected void init() {
        super.init();

        // Load audio path from the block entity.
        fetchDataFromBlockEntity();

        int guiLeft = (this.width - SCREEN_WIDTH) / 2;
        int guiTop = (this.height - SCREEN_HEIGHT) / 2;

        // Initialize the EditBox for audio path display (moved up 5px)
        this.audioPathField = new EditBox(this.font, guiLeft + 10, guiTop + 43, 140, 20, Component.literal(""));
        this.audioPathField.setMaxLength(255);
        if (initialAudioPath != null && !initialAudioPath.isBlank()) {
            this.audioPathField.setValue(initialAudioPath);
        }
        this.audioPathField.setFocused(true);
        this.addRenderableWidget(this.audioPathField);

        // Create the "Play" button (moved up 5px)
        Button playButton = Button.builder(Component.literal("Play"), button -> sendPlayAudioToServer())
                .pos(guiLeft + 10, guiTop + 75)
                .size(60, 20)
                .build();
        this.addRenderableWidget(playButton);

        // Create the "Stop" button (moved up 5px)
        Button stopButton = Button.builder(Component.literal("Stop"), button -> sendStopAudioToServer())
                .pos(guiLeft + 80, guiTop + 75)
                .size(60, 20)
                .build();
        this.addRenderableWidget(stopButton);

        // Create the "Load" button (moved up 5px)
        Button loadButton = Button.builder(Component.literal("Load"), button -> sendLoadAudioToServer())
                .pos(guiLeft + 10, guiTop + 100)
                .size(130, 20)
                .build();
        this.addRenderableWidget(loadButton);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        RenderSystem.setShaderTexture(0, BACKGROUND_TEXTURE);
        int guiLeft = (this.width - SCREEN_WIDTH) / 2;
        int guiTop = (this.height - SCREEN_HEIGHT) / 2;
        guiGraphics.blit(BACKGROUND_TEXTURE, guiLeft, guiTop, 0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

        // Draw label above the input field (moved up 5px)
        guiGraphics.drawString(this.font, Component.literal("Audio Path:"), guiLeft + 10, guiTop + 30, 4210752, false);

        this.audioPathField.render(guiGraphics, mouseX, mouseY, partialTicks);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    private void fetchDataFromBlockEntity() {
        if (Minecraft.getInstance().level == null) {
            return;
        }
        BlockEntity blockEntity = Minecraft.getInstance().level.getBlockEntity(blockEntityPos);
        if (blockEntity instanceof SpeakerBlockEntity) {
            this.initialAudioPath = ((SpeakerBlockEntity) blockEntity).getAudioPath();
        }
    }

    // Sends a packet to the server to play audio.
    private void sendPlayAudioToServer() {
        PlayAudioCallPacketC2S packet = new PlayAudioCallPacketC2S(blockEntityPos);
        PacketRegistries.sendToServer(packet);
    }

    // Sends a packet to the server to stop the audio playback.
    private void sendStopAudioToServer() {
        StopAudioPacketS2C packet = new StopAudioPacketS2C(blockEntityPos);
        PacketRegistries.sendToServer(packet);
    }

    // Sends a packet to the server to update the audio path.
    private void sendLoadAudioToServer() {
        String filePath = audioPathField.getValue();
        if (!filePath.isBlank()) {
            // Call AudioPathPacketC2S to update the audio path in the block entity.
            LoadAudioCallPacketC2S packet = new LoadAudioCallPacketC2S(blockEntityPos, filePath);
            PacketRegistries.sendToServer(packet);
        }
    }
}