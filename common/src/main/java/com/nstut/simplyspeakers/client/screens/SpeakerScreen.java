// Language: java
package com.nstut.simplyspeakers.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.network.LoadAudioCallPacketC2S;
import com.nstut.simplyspeakers.network.PacketRegistries;
import com.nstut.simplyspeakers.network.ToggleLoopPacketC2S;
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

public class SpeakerScreen extends Screen {

    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation(SimplySpeakers.MOD_ID, "textures/gui/speaker.png");

    private static final int SCREEN_WIDTH = 162;
    private static final int SCREEN_HEIGHT = 128;

    private EditBox audioPathField;
    private final BlockPos blockEntityPos;
    private String initialAudioPath;
    private SpeakerBlockEntity speaker;
    private Button loopToggleButton;

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
        this.setFocused(this.audioPathField); // Explicitly set focus for the screen

        // Create the "Load" button, centered horizontally and moved up slightly
        Button loadButton = Button.builder(Component.literal("Load"), button -> sendLoadAudioToServer())
                .pos(guiLeft + (SCREEN_WIDTH - 130) / 2, guiTop + 75) // Centered and moved up
                .size(130, 20)
                .build();
        this.addRenderableWidget(loadButton);

        // Create the "Loop" toggle button
        this.loopToggleButton = Button.builder(getLoopButtonTextComponent(), button -> {
                    if (this.speaker == null) return;
                    boolean currentLoopState = this.speaker.isLooping();
                    boolean newLoopState = !currentLoopState;
                    PacketRegistries.sendToServer(new ToggleLoopPacketC2S(this.blockEntityPos, newLoopState));
                    // Update client-side state for immediate UI feedback
                    this.speaker.setLoopingClient(newLoopState);
                    // Update button text based on the new client-side state
                    button.setMessage(getLoopButtonTextComponent());
                })
                .pos(guiLeft + (SCREEN_WIDTH - 130) / 2, guiTop + 100) // Position below load button
                .size(130, 20)
                .build();
        this.loopToggleButton.active = (this.speaker != null);
        this.addRenderableWidget(this.loopToggleButton);
    }

    private Component getLoopButtonTextComponent() {
        boolean looping = (this.speaker != null) ? this.speaker.isLooping() : false;
        String text = looping ? "Loop: ON" : "Loop: OFF";
        if (this.speaker == null) {
            text = "Loop: N/A";
        }
        return Component.literal(text);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        RenderSystem.setShaderTexture(0, BACKGROUND_TEXTURE);
        int guiLeft = (this.width - SCREEN_WIDTH) / 2;
        int guiTop = (this.height - SCREEN_HEIGHT) / 2;
        guiGraphics.blit(BACKGROUND_TEXTURE, guiLeft, guiTop, 0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
// Draw screen title
        guiGraphics.drawString(this.font, Component.literal("Speaker"), guiLeft + (SCREEN_WIDTH - this.font.width("Speaker")) / 2, guiTop + 10, 4210752, false);

        // Draw label above the input field (moved up 5px)
        guiGraphics.drawString(this.font, Component.literal("Audio Path:"), guiLeft + 10, guiTop + 30, 4210752, false);

        this.audioPathField.render(guiGraphics, mouseX, mouseY, partialTicks);

        // Update loop toggle button text and active state
        if (this.loopToggleButton != null) {
            if (this.speaker != null) {
                this.loopToggleButton.setMessage(getLoopButtonTextComponent());
                this.loopToggleButton.active = true;
            } else {
                // Attempt to re-fetch speaker if it was null, in case it loaded after screen init
                fetchDataFromBlockEntity();
                if (this.speaker != null) {
                    this.loopToggleButton.setMessage(getLoopButtonTextComponent());
                    this.loopToggleButton.active = true;
                } else {
                    this.loopToggleButton.setMessage(Component.literal("Loop: N/A"));
                    this.loopToggleButton.active = false;
                }
            }
        }

        super.render(guiGraphics, mouseX, mouseY, partialTicks);
    }

    private void fetchDataFromBlockEntity() {
        if (Minecraft.getInstance().level == null) {
            this.speaker = null;
            this.initialAudioPath = "";
            return;
        }
        BlockEntity blockEntity = Minecraft.getInstance().level.getBlockEntity(blockEntityPos);
        if (blockEntity instanceof SpeakerBlockEntity) {
            this.speaker = (SpeakerBlockEntity) blockEntity;
            this.initialAudioPath = this.speaker.getAudioPath();
        } else {
            this.speaker = null;
            this.initialAudioPath = "";
        }
    }

    // Removed sendPlayAudioToServer and sendStopAudioToServer methods

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
