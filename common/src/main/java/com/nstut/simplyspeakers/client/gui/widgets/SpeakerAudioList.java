package com.nstut.simplyspeakers.client.gui.widgets;

import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SpeakerAudioList extends AbstractWidget {
    private static final int ITEM_HEIGHT = 20;
    private List<AudioFileMetadata> audioFiles = new ArrayList<>();
    private List<AudioFileMetadata> filteredAudioFiles = new ArrayList<>();
    private double scrollAmount;
    private AudioFileMetadata selected;
    private String playingAudioId;
    private final Consumer<AudioFileMetadata> onSelect;

    public SpeakerAudioList(int x, int y, int width, int height, Component message, Consumer<AudioFileMetadata> onSelect) {
        super(x, y, width, height, message);
        this.onSelect = onSelect;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks) {
        guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFF000000);

        if (filteredAudioFiles.isEmpty()) {
            Component message = Component.literal("No audio uploaded");
            int textWidth = Minecraft.getInstance().font.width(message);
            int textX = this.getX() + (this.width - textWidth) / 2;
            int textY = this.getY() + (this.height - 8) / 2;
            guiGraphics.drawString(Minecraft.getInstance().font, message, textX, textY, 0xFFFFFFFF);
            return;
        }

        int scrollbarX = this.getX() + this.width - 6;
        int listTop = this.getY();
        int listBottom = this.getY() + this.height;

        int contentHeight = filteredAudioFiles.size() * ITEM_HEIGHT;
        int maxScroll = Math.max(0, contentHeight - this.height);

        if (maxScroll > 0) {
            int scrollbarHeight = (int) ((float) this.height / contentHeight * this.height);
            scrollbarHeight = Mth.clamp(scrollbarHeight, 32, this.height - 8);
            int scrollbarY = (int) (this.scrollAmount * (this.height - scrollbarHeight) / maxScroll) + this.getY();
            guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + 6, scrollbarY + scrollbarHeight, 0xFF888888);
        }

        guiGraphics.enableScissor(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height);
        for (int i = 0; i < filteredAudioFiles.size(); i++) {
            int itemTop = this.getY() - (int) scrollAmount + i * ITEM_HEIGHT;
            int itemBottom = itemTop + ITEM_HEIGHT;

            if (itemBottom < this.getY() || itemTop > this.getY() + this.height) {
                continue;
            }

            AudioFileMetadata metadata = filteredAudioFiles.get(i);
            boolean isHovered = mouseX >= this.getX() && mouseX < scrollbarX && mouseY >= itemTop && mouseY < itemBottom;
            boolean isSelected = selected == metadata;
            boolean isPlaying = this.playingAudioId != null && this.playingAudioId.equals(metadata.getUuid());

            int backgroundColor = isSelected ? 0xFF808080 : (isHovered ? 0xFF404040 : 0xFF202020);
            guiGraphics.fill(this.getX(), itemTop, scrollbarX, itemBottom, backgroundColor);

            int textColor = isPlaying ? 0xFF55FF55 : 0xFFFFFFFF; // Green if playing, white otherwise.
            guiGraphics.drawString(Minecraft.getInstance().font, metadata.getOriginalFilename(), this.getX() + 5, itemTop + (ITEM_HEIGHT - 8) / 2, textColor);

            if (isHovered && !isPlaying) {
                int buttonWidth = 50;
                int buttonHeight = 18;
                int buttonX = scrollbarX - buttonWidth - 2;
                int buttonY = itemTop + 1;
                Button selectButton = Button.builder(Component.literal("Select"), (button) -> onSelect.accept(metadata))
                        .bounds(buttonX, buttonY, buttonWidth, buttonHeight)
                        .build();
                selectButton.render(guiGraphics, mouseX, mouseY, partialTicks);
            }
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
        if (itemIndex >= 0 && itemIndex < filteredAudioFiles.size()) {
            AudioFileMetadata metadata = filteredAudioFiles.get(itemIndex);
            this.selected = metadata;

            int itemTop = this.getY() - (int) scrollAmount + itemIndex * ITEM_HEIGHT;
            boolean isHovered = mouseX >= this.getX() && mouseX < scrollbarX && mouseY >= itemTop && mouseY < itemTop + ITEM_HEIGHT;
            boolean isPlaying = this.playingAudioId != null && this.playingAudioId.equals(metadata.getUuid());

            if (isHovered && !isPlaying) {
                int buttonWidth = 50;
                int buttonX = scrollbarX - buttonWidth - 2;
                if (mouseX >= buttonX && mouseX < scrollbarX) {
                    onSelect.accept(metadata);
                    return true;
                }
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = Math.max(0, filteredAudioFiles.size() * ITEM_HEIGHT - this.height);
        scrollAmount = Mth.clamp(scrollAmount - delta * ITEM_HEIGHT, 0, maxScroll);
        return true;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
        // TODO: Implement narration
    }

    public void setAudioList(List<AudioFileMetadata> audioFiles) {
        this.audioFiles = new ArrayList<>(audioFiles);
        this.filteredAudioFiles = new ArrayList<>(audioFiles);
        this.scrollAmount = 0;
        this.selected = null;
    }

    public void filter(String searchTerm) {
        if (searchTerm == null || searchTerm.isEmpty()) {
            this.filteredAudioFiles = new ArrayList<>(this.audioFiles);
        } else {
            String lowerCaseSearchTerm = searchTerm.toLowerCase();
            this.filteredAudioFiles = this.audioFiles.stream()
                    .filter(file -> file.getOriginalFilename().toLowerCase().contains(lowerCaseSearchTerm))
                    .collect(Collectors.toList());
        }
        this.scrollAmount = 0;
        if (!this.filteredAudioFiles.contains(this.selected)) {
            this.selected = null;
        }
    }

    public AudioFileMetadata getSelected() {
        return selected;
    }

    public void setPlayingAudioId(String audioId) {
        this.playingAudioId = audioId;
    }
}