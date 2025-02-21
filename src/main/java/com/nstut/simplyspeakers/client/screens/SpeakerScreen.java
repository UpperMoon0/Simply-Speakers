// Java
package com.nstut.simplyspeakers.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.network.PacketRegistries;
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

        fetchDataFromBlockEntity();

        int guiLeft = (this.width - SCREEN_WIDTH) / 2;
        int guiTop = (this.height - SCREEN_HEIGHT) / 2;

        // Initialize the EditBox for audio path input
        this.audioPathField = new EditBox(this.font, guiLeft + 10, guiTop + 48, 140, 20, Component.literal(""));
        this.audioPathField.setMaxLength(255);

        if (initialAudioPath != null && !initialAudioPath.isBlank()) {
            this.audioPathField.setValue(initialAudioPath);
        }

        // Ensure the EditBox gets focus for text input
        this.audioPathField.setFocused(true);

        // Register the EditBox as an interactive widget
        this.addRenderableWidget(this.audioPathField);

        // Create the "Load Audio" button
        Button uploadButton = Button.builder(Component.literal("Load Audio"), button -> {
                    String filePath = audioPathField.getValue();
                    if (!filePath.isBlank()) {
                        sendScreenInputsToServer(filePath);
                    }
                })
                .pos(guiLeft + 21, guiTop + 90)
                .size(120, 20)
                .build();

        this.addRenderableWidget(uploadButton);
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

    private void sendScreenInputsToServer(String filePath) {
        PacketRegistries.sendMusicPathToServer(blockEntityPos, filePath);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        RenderSystem.setShaderTexture(0, BACKGROUND_TEXTURE);
        int guiLeft = (this.width - SCREEN_WIDTH) / 2;
        int guiTop = (this.height - SCREEN_HEIGHT) / 2;
        guiGraphics.blit(BACKGROUND_TEXTURE, guiLeft, guiTop, 0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);

        // Draw label above the input field
        guiGraphics.drawString(this.font, Component.literal("Audio Path:"), guiLeft + 10, guiTop + 35, 4210752, false);

        this.audioPathField.render(guiGraphics, mouseX, mouseY, partialTicks);
        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }
}