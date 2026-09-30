package com.nstut.simplyspeakers.network;

import com.nstut.simplyspeakers.SimplySpeakers;
import com.nstut.simplyspeakers.SpeakerPermissions;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import dev.architectury.networking.NetworkManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Client -> server playlist mutations: add/remove/select/reorder/clear/queue plus shuffle and repeat. */
public class PlaylistControlPacketC2S implements CustomPacketPayload {

    public static final byte OP_ADD = 0;
    public static final byte OP_REMOVE_AUDIO = 1;
    public static final byte OP_SELECT_INDEX = 2;
    public static final byte OP_MOVE_UP = 3;
    public static final byte OP_MOVE_DOWN = 4;
    public static final byte OP_CLEAR = 5;
    public static final byte OP_QUEUE_NEXT = 6;
    public static final byte OP_SET_SHUFFLE = 7;
    public static final byte OP_SET_REPEAT = 8;

    public static final CustomPacketPayload.Type<PlaylistControlPacketC2S> TYPE =
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(SimplySpeakers.MOD_ID, "playlist_control"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlaylistControlPacketC2S> STREAM_CODEC =
        StreamCodec.of(PlaylistControlPacketC2S::encode, PlaylistControlPacketC2S::decode);

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

    public static void encode(RegistryFriendlyByteBuf buffer, PlaylistControlPacketC2S packet) {
        buffer.writeBlockPos(packet.pos);
        buffer.writeByte(packet.op);
        buffer.writeVarInt(Math.max(0, packet.index));
        buffer.writeBoolean(packet.flag);
        buffer.writeUtf(packet.audioId, 256);
        buffer.writeUtf(packet.filename, 256);
    }

    public static PlaylistControlPacketC2S decode(RegistryFriendlyByteBuf buffer) {
        return new PlaylistControlPacketC2S(buffer.readBlockPos(), buffer.readByte(), buffer.readVarInt(),
                buffer.readBoolean(), buffer.readUtf(256), buffer.readUtf(256));
    }

    public static void handle(PlaylistControlPacketC2S packet, NetworkManager.PacketContext context) {
        ServerPlayer player = (ServerPlayer) context.getPlayer();
        context.queue(() -> {
            if (!SpeakerPacketSecurity.canModify(player, packet.pos)) {
                return;
            }
            if (player.level().getBlockEntity(packet.pos) instanceof SpeakerBlockEntity speaker) {
                SpeakerState state = speaker.getSpeakerState();
                if (state == null || !SpeakerPermissions.canControl(state, player.getUUID(), player.hasPermissions(2))) {
                    return;
                }
                // Content-level authorization: ADD and QUEUE_NEXT can introduce new
                // tracks, so the audioId must resolve to an owned local entry or an
                // allowed remote stream. The filename is derived server-side and the
                // packet-supplied filename is never trusted.
                String audioId = packet.audioId;
                String filename = packet.filename;
                if (packet.op == OP_ADD || packet.op == OP_QUEUE_NEXT) {
                    SpeakerPacketSecurity.AuthorizedTrack track =
                            SpeakerPacketSecurity.resolveAuthorizedTrack(player, packet.audioId);
                    if (track == null) return;
                    audioId = track.audioId();
                    filename = track.filename();
                }
                speaker.playlistControl(player.level(), packet.op, packet.index, packet.flag,
                        audioId, filename);
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
