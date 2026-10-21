package com.nstut.simplyspeakers.items;

import com.nstut.simplyspeakers.portable.PortableSpeakerManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import java.util.UUID;

/** A one-stack inventory speaker. The server registry owns its audio and permissions. */
public class PortableSpeakerItem extends Item {
    private static final String ID = "SimplySpeakersPortableId";
    private static final String LINK = "SimplySpeakersPortableLink";
    private static final String DIMENSION = "SimplySpeakersPortableDimension";

    public PortableSpeakerItem(Properties properties) { super(properties.stacksTo(1)); }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            PortableSpeakerManager.open(serverPlayer, player.getItemInHand(hand));
        }
        return InteractionResult.SUCCESS;
    }

    private static CompoundTag data(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }
    private static void save(ItemStack stack, CompoundTag tag) { stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); }
    private static String text(CompoundTag tag, String key) { return tag.getStringOr(key, ""); }

    public static UUID getIdentity(ItemStack stack) {
        try { return UUID.fromString(text(data(stack), ID)); }
        catch (IllegalArgumentException ignored) { return null; }
    }
    public static String getSpeakerId(ItemStack stack) { return text(data(stack), LINK); }
    public static String getDimension(ItemStack stack) { return text(data(stack), DIMENSION); }

    /** Re-keying a copied stack clears its old network reference as well. */
    public static void setIdentity(ItemStack stack, UUID identity) {
        CompoundTag tag = data(stack);
        tag.putString(ID, identity.toString());
        tag.remove(LINK);
        tag.remove(DIMENSION);
        save(stack, tag);
    }
    public static void setLink(ItemStack stack, String speakerId, String dimension) {
        CompoundTag tag = data(stack);
        tag.putString(LINK, speakerId == null ? "" : speakerId);
        tag.putString(DIMENSION, dimension == null ? "" : dimension);
        save(stack, tag);
    }
}
