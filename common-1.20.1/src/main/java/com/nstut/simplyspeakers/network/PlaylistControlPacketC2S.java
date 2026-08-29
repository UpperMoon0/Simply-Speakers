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

public class PlaylistControlPacketC2S {

    public static final byte OP_ADD = 0;
    public static final byte OP_REMOVE_AUDIO = 1;
    public static final byte OP_SELECT_INDEX = 2;
    public static final byte OP_MOVE_UP = 3;
    public static final byte OP_MOVE_DOWN = 4;
    public static final byte OP_CLEAR = 5;
    public static final byte OP_QUEUE_NEXT = 6;
    public static final byte OP_SET_SHUFFLE = 7;
    public static final byte OP_SET_REPEAT = 8;

    private final BlockPos pos;
    private final byte op;
    private final int index;
    private final boolean flag;
    private final String audioId;
    private final String filename;

    public PlaylistControlPacketC2S(BlockPos pos, byte op, int index, boolean flag, String audioId, String filename) {
        this.pos = pos;
        this.op = op;
        this.index = index;
        this.flag = flag;
        this.audioId = audioId != null ? audioId : "";
        this.filename = filename != null ? filename : "";
    }

    public PlaylistControlPacketC2S(FriendlyByteBuf buf) {
        this.pos = buf.readBlockPos();
        this.op = buf.readByte();
        this.index = Math.max(0, buf.readVarInt());
        this.flag = buf.readBoolean();
        this.audioId = buf.readUtf(256);
        this.filename = buf.readUtf(256);
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.pos);
        buf.writeByte(this.op);
        buf.writeVarInt(Math.max(0, this.index));
        buf.writeBoolean(this.flag);
        buf.writeUtf(this.audioId, 256);
        buf.writeUtf(this.filename, 256);
    }

    public static void handle(PlaylistControlPacketC2S pkt, Supplier<NetworkManager.PacketContext> ctxSupplier) {
        NetworkManager.PacketContext context = ctxSupplier.get();
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (!SpeakerPacketSecurity.canModify(player, pkt.pos)) return;
            ServerLevel level = player.serverLevel();
            if (level.getBlockEntity(pkt.pos) instanceof SpeakerBlockEntity speaker) {
                SpeakerState state = speaker.getSpeakerState();
                if (state == null || !SpeakerPermissions.canControl(state, player.getUUID(), player.hasPermissions(2))) return;
                // Content-level authorization: ADD and QUEUE_NEXT can introduce new
                // tracks, so the audioId must resolve to an owned local entry or an
                // allowed remote stream. The filename is derived server-side and the
                // packet-supplied filename is never trusted.
                String audioId = pkt.audioId;
                String filename = pkt.filename;
                if (pkt.op == OP_ADD || pkt.op == OP_QUEUE_NEXT) {
                    SpeakerPacketSecurity.AuthorizedTrack track =
                            SpeakerPacketSecurity.resolveAuthorizedTrack(player, pkt.audioId);
                    if (track == null) return;
                    audioId = track.audioId();
                    filename = track.filename();
                }
                speaker.playlistControl(level, pkt.op, pkt.index, pkt.flag, audioId, filename);
            }
        });
    }
}
