package com.nstut.simplyspeakers.client.gui.widgets;

import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class SpeakerAudioList extends AbstractWidget {
    private static final int ITEM_HEIGHT = 20;
    private static final int TEXT_PADDING = 5;
    private static final int ACTION_BUTTON_WIDTH = 45;
    private static final int ACTION_BUTTON_GAP = 2;
    private static final int MARQUEE_PAUSE_MS = 1000;
    private static final int MARQUEE_PIXELS_PER_SECOND = 30;
    private List<AudioFileMetadata> audioFiles = new ArrayList<>();
    private List<AudioFileMetadata> filteredAudioFiles = new ArrayList<>();
    private double scrollAmount;
    private AudioFileMetadata selected;
    private String playingAudioId;
    private final Consumer<AudioFileMetadata> onSelect;
    private final Consumer<AudioFileMetadata> onRemove;
    private boolean isDraggingScrollbar = false;

    public SpeakerAudioList(int x, int y, int width, int height, Component message, Consumer<AudioFileMetadata> onSelect, Consumer<AudioFileMetadata> onRemove) {
        super(x, y, width, height, message);
        this.onSelect = onSelect;
        this.onRemove = onRemove;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTicks) {
        guiGraphics.fill(this.getX(), this.getY(), this.getX() + this.width, this.getY() + this.height, 0xFF000000);

        if (filteredAudioFiles.isEmpty()) {
            Component message = Component.literal("No audio uploaded");
            int textWidth = Minecraft.getInstance().font.width(message);
            int textX = this.getX() + (this.width - textWidth) / 2;
            int textY = this.getY() + (this.height - 8) / 2;
            guiGraphics.text(Minecraft.getInstance().font, message, textX, textY, 0xFFFFFFFF);
            return;
        }

        int scrollbarX = this.getX() + this.width - 6;
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

            int selectButtonX = scrollbarX - ACTION_BUTTON_WIDTH - ACTION_BUTTON_GAP;
            int removeButtonX = selectButtonX - ACTION_BUTTON_WIDTH - ACTION_BUTTON_GAP;
            int textLeft = this.getX() + TEXT_PADDING;
            int textRight = removeButtonX - TEXT_PADDING;
            int textColor = isSelected ? 0xFF55FF55 : 0xFFFFFFFF; // Green if selected, white otherwise.
            renderMarqueeText(guiGraphics, metadata.getOriginalFilename(), textLeft,
                    itemTop + (ITEM_HEIGHT - 8) / 2, textRight, textColor);

            if (isHovered) {
                int buttonHeight = 18;
                int buttonY = itemTop + 1;
                int visibleRemoveButtonX = isPlaying ? selectButtonX : removeButtonX;
                renderActionButton(guiGraphics, Component.literal("Delete"), visibleRemoveButtonX, buttonY,
                        ACTION_BUTTON_WIDTH, buttonHeight, mouseX, mouseY);
                if (!isPlaying) {
                    renderActionButton(guiGraphics, Component.literal("Select"), selectButtonX, buttonY,
                            ACTION_BUTTON_WIDTH, buttonHeight, mouseX, mouseY);
                }
            }
        }
        guiGraphics.disableScissor();
    }

    private void renderActionButton(GuiGraphicsExtractor guiGraphics, Component label, int x, int y,
                                    int width, int height, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        guiGraphics.fill(x, y, x + width, y + height, 0xFFFFFFFF);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + height - 1,
                hovered ? 0xFF7A7A7A : 0xFF555555);

        int textX = x + (width - Minecraft.getInstance().font.width(label)) / 2;
        int textY = y + (height - Minecraft.getInstance().font.lineHeight) / 2;
        guiGraphics.text(Minecraft.getInstance().font, label, textX, textY,
                hovered ? 0xFFFFFFA0 : 0xFFFFFFFF, false);
    }

    private void renderMarqueeText(GuiGraphicsExtractor guiGraphics, String text, int left, int y, int right, int color) {
        int availableWidth = Math.max(0, right - left);
        int textWidth = Minecraft.getInstance().font.width(text);
        int overflow = Math.max(0, textWidth - availableWidth);
        int offset = getMarqueeOffset(overflow);

        guiGraphics.enableScissor(left, this.getY(), right, this.getY() + this.height);
        guiGraphics.text(Minecraft.getInstance().font, text, left - offset, y, color);
        guiGraphics.disableScissor();
    }

    private int getMarqueeOffset(int overflow) {
        if (overflow <= 0) return 0;

        long travelMs = Math.max(1L, overflow * 1000L / MARQUEE_PIXELS_PER_SECOND);
        long cycleMs = MARQUEE_PAUSE_MS * 2L + travelMs * 2L;
        long elapsed = System.currentTimeMillis() % cycleMs;
        if (elapsed < MARQUEE_PAUSE_MS) return 0;
        elapsed -= MARQUEE_PAUSE_MS;
        if (elapsed < travelMs) return (int) (overflow * elapsed / travelMs);
        elapsed -= travelMs;
        if (elapsed < MARQUEE_PAUSE_MS) return overflow;
        elapsed -= MARQUEE_PAUSE_MS;
        return overflow - (int) (overflow * elapsed / travelMs);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        if (!this.isMouseOver(mouseX, mouseY)) {
            return false;
        }

        int scrollbarX = this.getX() + this.width - 6;
        if (mouseX >= scrollbarX) {
            this.isDraggingScrollbar = true;
            return true;
        }

        int itemIndex = (int) (mouseY - this.getY() + scrollAmount) / ITEM_HEIGHT;
        if (itemIndex >= 0 && itemIndex < filteredAudioFiles.size()) {
            AudioFileMetadata metadata = filteredAudioFiles.get(itemIndex);
            this.selected = metadata;

            int itemTop = this.getY() - (int) scrollAmount + itemIndex * ITEM_HEIGHT;
            boolean isHovered = mouseX >= this.getX() && mouseX < scrollbarX && mouseY >= itemTop && mouseY < itemTop + ITEM_HEIGHT;
            boolean isPlaying = this.playingAudioId != null && this.playingAudioId.equals(metadata.getUuid());

            if (isHovered) {
                int removeButtonWidth = 45;
                int selectButtonWidth = 45;
                int selectButtonX = scrollbarX - selectButtonWidth - 2;
                int removeButtonX = selectButtonX - removeButtonWidth - 2;
                if (!isPlaying && mouseX >= selectButtonX && mouseX < scrollbarX) {
                    onSelect.accept(metadata);
                    return true;
                }
                int visibleRemoveButtonX = isPlaying ? selectButtonX : removeButtonX;
                int visibleRemoveButtonRight = isPlaying ? scrollbarX : selectButtonX;
                if (mouseX >= visibleRemoveButtonX && mouseX < visibleRemoveButtonRight) {
                    onRemove.accept(metadata);
                    return true;
                }
            }

            return true;
        }

        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.isDraggingScrollbar = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseY = event.y();
        if (this.isDraggingScrollbar) {
            int contentHeight = filteredAudioFiles.size() * ITEM_HEIGHT;
            int maxScroll = Math.max(0, contentHeight - this.height);
            if (maxScroll > 0) {
                int scrollbarHeight = (int) ((float) this.height / contentHeight * this.height);
                scrollbarHeight = Mth.clamp(scrollbarHeight, 32, this.height - 8);
                // Convert mouse Y position to scroll amount
                double relativeY = mouseY - this.getY() - scrollbarHeight / 2.0;
                scrollAmount = Mth.clamp((relativeY / (this.height - scrollbarHeight)) * maxScroll, 0, maxScroll);
            }
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = Math.max(0, filteredAudioFiles.size() * ITEM_HEIGHT - this.height);
        scrollAmount = Mth.clamp(scrollAmount - scrollY * ITEM_HEIGHT, 0, maxScroll);
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
    
    /**
     * Sets the selected audio by its ID.
     *
     * @param audioId The ID of the audio to select
     */
    public void setSelectedAudioId(String audioId) {
        if (audioId == null || audioId.isEmpty()) {
            this.selected = null;
            return;
        }
        
        // Find the audio file with the matching ID
        for (AudioFileMetadata metadata : this.audioFiles) {
            if (audioId.equals(metadata.getUuid())) {
                this.selected = metadata;
                return;
            }
        }
        
        // If not found in the full list, check the filtered list
        for (AudioFileMetadata metadata : this.filteredAudioFiles) {
            if (audioId.equals(metadata.getUuid())) {
                this.selected = metadata;
                return;
            }
        }
        
        // If still not found, clear selection
        this.selected = null;
    }
}
