package com.nstut.simplyspeakers.network;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SettingsPacketPositionTest {
    @Test
    void regularSettingsPacketSnapshotsMutableInteractionPosition() throws Exception {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(4, 70, -9);
        UpdateMaxVolumePacketC2S packet = new UpdateMaxVolumePacketC2S(cursor, 0.4f);
        cursor.set(100, 2, 100);

        BlockPos captured = packetPosition(UpdateMaxVolumePacketC2S.class, packet);
        assertEquals(new BlockPos(4, 70, -9), captured);
        assertFalse(captured instanceof BlockPos.MutableBlockPos);
    }

    @Test
    void proxySettingsPacketSnapshotsMutableInteractionPosition() throws Exception {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(-3, 64, 12);
        UpdateProxyMaxRangePacketC2S packet = new UpdateProxyMaxRangePacketC2S(cursor, 48);
        cursor.set(0, 0, 0);

        assertEquals(new BlockPos(-3, 64, 12), packetPosition(UpdateProxyMaxRangePacketC2S.class, packet));
    }

    private static BlockPos packetPosition(Class<?> type, Object packet) throws Exception {
        Field field = type.getDeclaredField("pos");
        field.setAccessible(true);
        return (BlockPos) field.get(packet);
    }
}
