package com.nstut.simplyspeakers.client.gui.widgets;

import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

public class SpeakerAudioList extends AbstractWidget {
    private static final int ITEM_HEIGHT = 20;
    private List<AudioFileMetadata> audioFiles = new ArrayList<>();
    private double scrollAmount;
    private AudioFileMetadata selected;
    private String playingAudioId;

    public SpeakerAudioList(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFF000000);

        int scrollbarX = this.getX() + this.width - 6;
        int listTop = this.getY();
        int listBottom = this.getY() + this.height;

        int contentHeight = audioFiles.size() * ITEM_HEIGHT;
        int maxScroll = Math.max(0, contentHeight - this.height);

        if (maxScroll > 0) {
            int scrollbarHeight = (int) ((float) this.height / contentHeight * this.height);
            scrollbarHeight = Mth.clamp(scrollbarHeight, 32, this.height - 8);
            int scrollbarY = (int) (this.scrollAmount * (this.height - scrollbarHeight) / maxScroll) + this.getY();
            guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + 6, scrollbarY + scrollbarHeight, 0xFF888888);
        }

        guiGraphics.enableScissor(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height);
        for (int i = 0; i < audioFiles.size(); i++) {
            int itemTop = this.getY() - (int) scrollAmount + i * ITEM_HEIGHT;
            int itemBottom = itemTop + ITEM_HEIGHT;

            if (itemBottom < this.getY() || itemTop > this.getY() + this.height) {
                continue;
            }

            AudioFileMetadata metadata = audioFiles.get(i);
            boolean isHovered = mouseX >= this.getX() && mouseX < scrollbarX && mouseY >= itemTop && mouseY < itemBottom;
            boolean isSelected = selected == metadata;
            boolean isPlaying = this.playingAudioId != null && this.playingAudioId.equals(metadata.getUuid());

            int backgroundColor = isSelected ? 0xFF808080 : (isHovered ? 0xFF404040 : 0xFF202020);
            guiGraphics.fill(this.getX(), itemTop, scrollbarX, itemBottom, backgroundColor);

            int textColor = isPlaying ? 0xFF55FF55 : 0xFFFFFFFF; // Green if playing, white otherwise.
            guiGraphics.drawString(Minecraft.getInstance().font, metadata.getOriginalFilename(), this.getX() + 5, itemTop + (ITEM_HEIGHT - 8) / 2, textColor);
        }
        guiGraphics.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.isMouseOver(mouseX, mouseY)) {
            return false;
        }

        int scrollbarX = this.getX() + this.width - 6;
        if (mouseX >= scrollbarX) {
            return true; // Handled by mouseDragged
        }

        int itemIndex = (int) (mouseY - this.getY() + scrollAmount) / ITEM_HEIGHT;
        if (itemIndex >= 0 && itemIndex < audioFiles.size()) {
            selected = audioFiles.get(itemIndex);
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, audioFiles.size() * ITEM_HEIGHT - this.height);
        scrollAmount = Mth.clamp(scrollAmount - delta * ITEM_HEIGHT, 0, maxScroll);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        // TODO: Implement narration
    }

    public void setAudioList(List<AudioFileMetadata> audioFiles) {
        this.audioFiles = audioFiles;
        this.scrollAmount = 0;
        this.selected = null;
    }

    public AudioFileMetadata getSelected() {
        return selected;
    }

    public void setPlayingAudioId(String audioId) {
        this.playingAudioId = audioId;
    }
}