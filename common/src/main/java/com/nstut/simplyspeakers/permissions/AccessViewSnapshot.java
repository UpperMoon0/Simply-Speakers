package com.nstut.simplyspeakers.permissions;

import com.nstut.simplyspeakers.SpeakerAccess;
import com.nstut.simplyspeakers.SpeakerPermissions;
import com.nstut.simplyspeakers.SpeakerState;
import java.io.*;
import java.util.*;
import java.util.function.Function;

/** Bounded, per-viewer policy data; authority remains on the server. */
public record AccessViewSnapshot(UUID owner, String ownerName, SpeakerAccess access,
        List<Player> trusted, boolean canManage, boolean canControl, boolean streamsAllowed) {
    public static final int MAX_TRUSTED = 128, MAX_BYTES = 30000;
    public static final AccessViewSnapshot EMPTY = new AccessViewSnapshot(null,"",SpeakerAccess.PUBLIC,List.of(),false,false,false);
    public record Player(UUID uuid, String name) {}
    public AccessViewSnapshot {
        ownerName = ownerName == null ? "" : ownerName;
        access = access == null ? SpeakerAccess.PUBLIC : access;
        trusted = List.copyOf(trusted);
        if(trusted.size()>MAX_TRUSTED)throw new IllegalArgumentException("Trusted player count");
    }
    public static AccessViewSnapshot capture(SpeakerState state, UUID viewer, boolean operator,
            boolean streamsAllowed, Function<UUID,String> names) {
        if(state==null)return EMPTY;
        return new AccessViewSnapshot(state.getOwnerUuid(),state.getOwnerUuid()==null?"":names.apply(state.getOwnerUuid()),
            state.getAccessMode(),state.getTrustedPlayers().stream().sorted().limit(MAX_TRUSTED)
                .map(id -> new Player(id,names.apply(id))).toList(),
            SpeakerPermissions.canManage(state,viewer,operator),SpeakerPermissions.canControl(state,viewer,operator),streamsAllowed);
    }
    public byte[] encode() {
        try {
            var bytes=new ByteArrayOutputStream();var out=new DataOutputStream(bytes);
            out.writeBoolean(owner!=null);if(owner!=null)writePlayer(out,new Player(owner,ownerName));
            out.writeByte(access.ordinal());out.writeBoolean(canManage);out.writeBoolean(canControl);out.writeBoolean(streamsAllowed);
            out.writeInt(trusted.size());for(var player:trusted)writePlayer(out,player);
            return bytes.toByteArray();
        } catch(IOException e){throw new IllegalArgumentException(e);}
    }
    public static AccessViewSnapshot decode(byte[] bytes) {
        if(bytes.length>MAX_BYTES)throw new IllegalArgumentException("Policy snapshot size");
        try {
            var in=new DataInputStream(new ByteArrayInputStream(bytes));Player owner=in.readBoolean()?readPlayer(in):null;
            int mode=in.readUnsignedByte();if(mode>=SpeakerAccess.values().length)throw new IllegalArgumentException("Access mode");
            boolean manage=in.readBoolean(),control=in.readBoolean(),streams=in.readBoolean();
            int count=in.readInt();if(count<0||count>MAX_TRUSTED)throw new IllegalArgumentException("Trusted player count");
            List<Player> trusted=new ArrayList<>();for(int i=0;i<count;i++)trusted.add(readPlayer(in));
            if(in.available()!=0)throw new IllegalArgumentException("Trailing policy data");
            return new AccessViewSnapshot(owner==null?null:owner.uuid(),owner==null?"":owner.name(),SpeakerAccess.values()[mode],trusted,manage,control,streams);
        }catch(IOException e){throw new IllegalArgumentException("Malformed policy snapshot",e);}
    }
    private static void writePlayer(DataOutputStream out,Player player)throws IOException {
        out.writeLong(player.uuid().getMostSignificantBits());out.writeLong(player.uuid().getLeastSignificantBits());
        String name=player.name()==null?"":player.name();out.writeUTF(name.substring(0,Math.min(64,name.length())));
    }
    private static Player readPlayer(DataInputStream in)throws IOException {
        UUID id=new UUID(in.readLong(),in.readLong());String name=in.readUTF();if(name.length()>64)throw new IllegalArgumentException("Player name");return new Player(id,name);
    }
}
