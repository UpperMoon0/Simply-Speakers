package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SpeakerPermissions;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Supplier;

public class TransportControlPacketC2S {

    public static final byte ACTION_PLAY = 0;
    public static final byte ACTION_PAUSE = 1;
    public static final byte ACTION_TOGGLE = 2;
    public static final byte ACTION_STOP = 3;
    public static final byte ACTION_RESTART = 4;
    public static final byte ACTION_NEXT = 5;
    public static final byte ACTION_PREVIOUS = 6;
    public static final byte ACTION_SEEK = 7;

    private final BlockPos pos;
    private final byte action;
    private final float seekSeconds;

    public TransportControlPacketC2S(BlockPos pos, byte action) {
        this(pos, action, 0.0f);
    }

    public TransportControlPacketC2S(BlockPos pos, byte action, float seekSeconds) {
        this.pos = pos;
        this.action = action;
        this.seekSeconds = seekSeconds;
    }

    public TransportControlPacketC2S(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.action = buf.readByte();
        this.seekSeconds = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeByte(this.action);
        buf.writeFloat(this.seekSeconds);
    }

    public static void handle(TransportControlPacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (!SpeakerPacketSecurity.canModify(player, pkt.pos)) return;
            ServerLevel level = player.serverLevel();
            if (level.getBlockEntity(pkt.pos) instanceof SpeakerBlockEntity speaker) {
                SpeakerState state = speaker.getSpeakerState();
                if (state == null || !SpeakerPermissions.canControl(state, player.getUUID(), player.hasPermissions(2))) return;
                speaker.transportAction(level, pkt.action, pkt.seekSeconds);
            }
        });
    }
}
