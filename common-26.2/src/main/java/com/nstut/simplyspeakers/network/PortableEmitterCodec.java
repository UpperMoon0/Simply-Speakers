package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.portable.PortableEmitterSnapshot;
import net.minecraft.network.FriendlyByteBuf;

/** Shared wire shape for initial playback and subsequent movement snapshots. */
final class PortableEmitterCodec {
    private PortableEmitterCodec() {}
    static void write(FriendlyByteBuf buffer, PortableEmitterSnapshot snapshot) {
        buffer.writeUUID(snapshot.identity());
        buffer.writeUUID(snapshot.holderId());
        buffer.writeUtf(snapshot.dimension(), 256);
        buffer.writeDouble(snapshot.x());
        buffer.writeDouble(snapshot.y());
        buffer.writeDouble(snapshot.z());
        buffer.writeFloat(snapshot.yaw());
    }
    static PortableEmitterSnapshot read(FriendlyByteBuf buffer) {
        return new PortableEmitterSnapshot(buffer.readUUID(), buffer.readUUID(), buffer.readUtf(256),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(), buffer.readFloat());
    }
}
