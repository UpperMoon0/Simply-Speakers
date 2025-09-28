// Language: java
package com.nstut.simplyspeakers.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.blocks.entities.ProxySpeakerBlockEntity;
import com.nstut.simplyspeakers.network.*;
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

public class ProxySpeakerScreen extends Screen {

    private static final ResourceLocation BACKGROUND_TEXTURE = new ResourceLocation(SimplySpeakers.MOD_ID, "textures/gui/proxy_speaker.png");

    private static final int SCREEN_WIDTH = 162;
    private static final int SCREEN_HEIGHT = 70;

    private final BlockPos blockEntityPos;
    private ProxySpeakerBlockEntity speaker;
    private EditBox speakerIdField;
    private Button saveIdButton;

    public ProxySpeakerScreen(BlockPos blockEntityPos) {
        super(Component.literal("Proxy Speaker Screen"));
        this.blockEntityPos = blockEntityPos;
    }

    @Override
    protected void init() {
        super.init();

        fetchDataFromBlockEntity();

        int guiLeft = (this.width - SCREEN_WIDTH) / 2;
        int guiTop = (this.height - SCREEN_HEIGHT) / 2;

        this.speakerIdField = new EditBox(this.font, guiLeft + 10, guiTop + 33, SCREEN_WIDTH - 80, 20, Component.literal("Speaker ID"));
        if (this.speaker != null) {
            this.speakerIdField.setValue(this.speaker.getSpeakerId());
        }

        this.saveIdButton = Button.builder(Component.literal("Save"), button -> {
                    if (this.speaker != null) {
                        String newId = this.speakerIdField.getValue();
                        // Optimistically update the client-side speaker entity
                        this.speaker.setSpeakerIdClient(newId);
                        PacketRegistries.CHANNEL.sendToServer(new SetSpeakerIdPacketC2S(this.blockEntityPos, newId));
                    }
                })
                .pos(guiLeft + SCREEN_WIDTH - 55, guiTop + 33)
                .size(45, 20)
                .build();

        this.addRenderableWidget(this.speakerIdField);
        this.addRenderableWidget(this.saveIdButton);
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(guiGraphics);
        RenderSystem.setShaderTexture(0, BACKGROUND_TEXTURE);
        int guiLeft = (this.width - SCREEN_WIDTH) / 2;
        int guiTop = (this.height - SCREEN_HEIGHT) / 2;
        guiGraphics.blit(BACKGROUND_TEXTURE, guiLeft, guiTop, 0, 0, SCREEN_WIDTH, SCREEN_HEIGHT);
        guiGraphics.drawString(this.font, Component.literal("Proxy Speaker"), guiLeft + (SCREEN_WIDTH - this.font.width("Proxy Speaker")) / 2, guiTop + 10, 4210752, false);
        
        // Draw label for speaker ID field
        guiGraphics.drawString(this.font, Component.literal("Speaker ID:"), guiLeft + 10, guiTop + 23, 4210752, false);

        super.render(guiGraphics, mouseX, mouseY, partialTicks);
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