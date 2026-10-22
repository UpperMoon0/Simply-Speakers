package com.nstut.simplyspeakers.compat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LegacyBlockEntitySyncTest {
    private static final BlockPos POS = new BlockPos(1, 2, 3);
    private static class Tile extends LegacyBlockEntity {
        String value = "";
        Tile() { super(BlockEntityType.SIGN, POS, Blocks.STONE.defaultBlockState()); }
        public void tick() { }
        @Override public void load(CompoundTag tag) { super.load(tag); value = tag.getString("fixtureIdentity"); }
        @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.putString("fixtureIdentity", value); }
    }
    @BeforeAll static void bootstrap() { SharedConstants.getCurrentVersion(); Bootstrap.bootStrap(); }
    private static Tile tile(boolean client) throws Exception {
        Level level = mock(Level.class);
        Field side = Level.class.getDeclaredField("isClientSide"); side.setAccessible(true); side.setBoolean(level, client);
        when(level.getBlockState(POS)).thenReturn(Blocks.STONE.defaultBlockState());
        Tile tile = new Tile(); tile.setLevelAndPosition(level, POS); return tile;
    }
    private static CompoundTag tag() { Tile server = new Tile(); server.value = "fresh-identity"; return server.getUpdateTag(); }
    @Test void initialAndIncrementalNativeTagsPreserveTypePositionAndIdentity() throws Exception {
        CompoundTag tag = tag(); assertEquals("minecraft:sign", tag.getString("id"));
        assertEquals(1, tag.getInt("x")); assertEquals(2, tag.getInt("y")); assertEquals(3, tag.getInt("z"));
        Tile tile = tile(true); tile.applyClientUpdate(tag); assertEquals("fresh-identity", tile.value);
        tag.putString("fixtureIdentity", "updated-identity"); tile.applyClientUpdate(tag); assertEquals("updated-identity", tile.value);
    }
    @Test void serverDetachedWrongTypeAndWrongPositionNeverLoad() throws Exception {
        Tile server = tile(false); server.applyClientUpdate(tag()); assertEquals("", server.value);
        Tile detached = new Tile(); detached.applyClientUpdate(tag()); assertEquals("", detached.value);
        Tile client = tile(true); CompoundTag wrong = tag(); wrong.putString("id", "minecraft:chest");
        client.applyClientUpdate(wrong); assertEquals("", client.value);
        wrong = tag(); wrong.putInt("x", 4); client.applyClientUpdate(wrong); assertEquals("", client.value);
        client.applyClientUpdate(null); assertEquals("", client.value);
    }
    @Test void actualLoaderCallbackUsesTheSameChecks() throws Exception {
        Method callback = null;
        for (Method method : Tile.class.getSuperclass().getDeclaredMethods())
            if (method.getName().equals("onDataPacket") || method.getName().equals("fromClientTag")) callback = method;
        assertNotNull(callback, "Each native loader must expose its supported update callback");
        Tile client = tile(true);
        if (callback.getParameterCount() == 1) {
            CompoundTag wrong = tag(); wrong.putString("id", "minecraft:chest"); callback.invoke(client, wrong); assertEquals("", client.value);
            callback.invoke(client, tag());
            CompoundTag serialized = (CompoundTag)Tile.class.getSuperclass().getMethod("toClientTag", CompoundTag.class).invoke(client, new CompoundTag());
            assertEquals("minecraft:sign", serialized.getString("id")); assertEquals("fresh-identity", serialized.getString("fixtureIdentity"));
        } else {
            callback.invoke(client, null, new ClientboundBlockEntityDataPacket(POS, 1, tag())); assertEquals("", client.value);
            callback.invoke(client, null, new ClientboundBlockEntityDataPacket(BlockPos.ZERO, 0, tag())); assertEquals("", client.value);
            callback.invoke(client, null, new ClientboundBlockEntityDataPacket(POS, 0, tag()));
        }
        assertEquals("fresh-identity", client.value);
    }
}
