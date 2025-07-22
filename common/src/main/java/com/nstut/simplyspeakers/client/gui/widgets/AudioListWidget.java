package com.nstut.simplyspeakers.client.gui.widgets;

import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.nstut.simplyspeakers.SimplySpeakers;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.stream.Collectors;

@Deprecated
public class AudioListWidget extends ObjectSelectionList<AudioListWidget.AudioEntry> {
    public AudioListWidget(Minecraft minecraft, int width, int height, int top, int bottom, int itemHeight) {
        super(minecraft, width, height, top, bottom, itemHeight);
        this.centerListVertically = false;
    }

    public void setAudioList(List<String> audioIds) {
        this.clearEntries();
        List<AudioFileMetadata> files = audioIds.stream()
                .map(id -> SimplySpeakers.getAudioFileManager().getManifest().get(id))
                .collect(Collectors.toList());
        files.forEach(file -> this.addEntry(new AudioEntry(file)));
    }

    public void setX(int x) {
        this.x0 = x;
        this.x1 = x + this.width;
    }

    @Override
    public int getScrollbarPosition() {
        return this.x1 - 6;
    }

    @Override
    public int getRowWidth() {
        return super.getRowWidth();
    }

    public class AudioEntry extends ObjectSelectionList.Entry<AudioEntry> {
        private final AudioFileMetadata metadata;

        public AudioEntry(AudioFileMetadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public Component getNarration() {
            return Component.literal(metadata.getOriginalFilename());
        }

        @Override
        public void render(GuiGraphics guiGraphics, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean isHovering, float partialTicks) {
            guiGraphics.drawString(Minecraft.getInstance().font, metadata.getOriginalFilename(), left + 2, top + 2, 0xFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            AudioListWidget.this.setSelected(this);
            return super.mouseClicked(mouseX, mouseY, button);
        }

        public String getAudioId() {
            return metadata.getUuid();
        }
    }
}