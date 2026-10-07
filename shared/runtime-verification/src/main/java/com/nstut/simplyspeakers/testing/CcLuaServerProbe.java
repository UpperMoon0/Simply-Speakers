package com.nstut.simplyspeakers.testing;

import com.nstut.simplyspeakers.*;
import com.nstut.simplyspeakers.blocks.BlockRegistries;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.playlist.PlayerPlaylistStore;
import com.nstut.simplyspeakers.speakers.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Opt-in isolated-world fixture: real computer blocks boot real Lua through CC/Cobalt. */
public final class CcLuaServerProbe {
    private static MinecraftServer current;
    private static ServerLevel level;
    private static SpeakerBlockEntity owned,linked,unowned;
    private static Object computer,mount;
    private static Class<?> mountType;
    private static int ticks;
    private static boolean finished;
    private static String request="";
    private static final BlockPos ORIGIN=new BlockPos(8,5,8);
    private static final UUID OWNER=UUID.fromString("233da121-9900-4bac-9903-467c0f4b44fa");
    private CcLuaServerProbe() {}

    public static void tick(MinecraftServer server) {
        if(!Boolean.getBoolean("simplyspeakers.ccLuaTest") || finished)return;
        try {
            if(current!=server) {current=server;setup(server);}
            if(++ticks>1800)throw new IllegalStateException("Lua timeout; request="+request+" result="+read("result.txt"));
            String result=read("result.txt");
            if(result.startsWith("FAIL"))throw new IllegalStateException(result);
            if(result.startsWith("PASS")) {
                var state=owned.getSpeakerState();
                if(state.getAccessMode()!=SpeakerAccess.PUBLIC || state.isPlaying())throw new IllegalStateException("final server state incorrect");
                var saved=PlayerPlaylistStore.library(OWNER).getSavedPlaylists();
                if(saved.size()!=2 || saved.get(0).getPlaylist().size()!=2)throw new IllegalStateException("Lua edited owner's saved templates");
                ServerSpeakerRegistry.flushDirty();
                finished=true;System.out.println("SIMPLYSPEAKERS_CC_LUA_PASS "+result);return;
            }
            String next=read("request.txt");
            if(!next.isEmpty() && !next.equals(request)) {
                request=next;
                switch(next) {
                    case "owner_only" -> owned.getSpeakerState().setAccessMode(SpeakerAccess.OWNER_ONLY);
                    case "trusted" -> owned.getSpeakerState().setAccessMode(SpeakerAccess.TRUSTED);
                    case "operators" -> owned.getSpeakerState().setAccessMode(SpeakerAccess.OPERATORS);
                    case "streams_on" -> Config.allowRemoteStreams=true;
                    case "streams_off" -> Config.allowRemoteStreams=false;
                    case "public" -> owned.getSpeakerState().setAccessMode(SpeakerAccess.PUBLIC);
                    case "detach" -> level.removeBlock(linked.getBlockPos(),false);
                    case "reattach" -> linked=place(ORIGIN.south(),"__cc_owned");
                    default -> throw new IllegalStateException("unknown Lua fixture checkpoint "+next);
                }
                // Delay acknowledgement for actual peripheral neighbour refresh events.
                pendingAck=next;ackTick=ticks+5;
            }
            if(pendingAck!=null && ticks>=ackTick) {
                computer.getClass().getMethod("queueEvent",String.class,Object[].class)
                    .invoke(computer,"ss_fixture_ack",new Object[]{pendingAck});pendingAck=null;
            }
        } catch(Exception error) {
            finished=true;System.err.println("SIMPLYSPEAKERS_VERIFY_FAIL cc_lua "+error);error.printStackTrace();
        }
    }
    private static String pendingAck;
    private static int ackTick;
    private static SpeakerBlockEntity place(BlockPos pos,String id) {
        level.setBlockAndUpdate(pos,BlockRegistries.SPEAKER.get().defaultBlockState());
        var block=(SpeakerBlockEntity)level.getBlockEntity(pos);block.setSpeakerId(id);return block;
    }
    private static void setup(MinecraftServer server) throws Exception {
        level=server.overworld();level.setChunkForced(0,0,true);level.getChunk(ORIGIN);
        owned=place(ORIGIN.east(),"__cc_owned");linked=place(ORIGIN.south(),"__cc_owned");
        unowned=place(ORIGIN.west(),"__cc_unowned");
        var state=owned.getSpeakerState();state.setOwnerUuid(OWNER);state.setAccessMode(SpeakerAccess.PUBLIC);
        var files=SimplySpeakers.getAudioFileManager();
        var first=files.saveFile(new ByteArrayInputStream(WaveFixture.tone(60)),"cc-first.wav",OWNER.toString());
        var second=files.saveFile(new ByteArrayInputStream(WaveFixture.tone(60)),"cc-second.wav",OWNER.toString());
        var shortTrack=files.saveFile(new ByteArrayInputStream(WaveFixture.tone(1)),"cc-short.wav",OWNER.toString());
        var foreign=files.saveFile(new ByteArrayInputStream(WaveFixture.tone(1)),"cc-foreign.wav",UUID.randomUUID().toString());
        var library=PlayerPlaylistStore.library(OWNER);String a=library.createSavedPlaylist("CC test A"),b=library.createSavedPlaylist("CC test B");
        library.findSavedPlaylist(a).getPlaylist().add(first.getUuid(),first.getOriginalFilename());
        library.findSavedPlaylist(a).getPlaylist().add(second.getUuid(),second.getOriginalFilename());
        library.findSavedPlaylist(b).getPlaylist().add(second.getUuid(),second.getOriginalFilename());PlayerPlaylistStore.changed();
        var block=BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse("computercraft:computer_advanced"));
        if(block==net.minecraft.world.level.block.Blocks.AIR)throw new IllegalStateException("CC computer not installed");
        level.setBlockAndUpdate(ORIGIN,block.defaultBlockState());var be=level.getBlockEntity(ORIGIN);
        computer=be.getClass().getMethod("createServerComputer").invoke(be);
        mount=computer.getClass().getMethod("createRootMount").invoke(computer);
        mountType=Class.forName("dan200.computercraft.api.filesystem.WritableMount");
        String lua;
        try(var resource=CcLuaServerProbe.class.getResourceAsStream("/assets/simplyspeakers/testing/cc_acceptance.lua")) {
            if(resource==null)throw new IllegalStateException("Lua test resource missing");lua=new String(resource.readAllBytes(),StandardCharsets.UTF_8);
        }
        lua=lua.replace("@FIRST@",first.getUuid()).replace("@SECOND@",second.getUuid()).replace("@SHORT@",shortTrack.getUuid()).replace("@FOREIGN@",foreign.getUuid());
        write("startup.lua",lua);computer.getClass().getMethod("turnOn").invoke(computer);
        System.out.println("SIMPLYSPEAKERS_CC_LUA_STARTED computer="+computer.getClass().getMethod("getID").invoke(computer));
    }
    private static void write(String path,String value) throws Exception {
        try(var channel=openForWrite(path)) {
            var data=ByteBuffer.wrap(value.getBytes(StandardCharsets.UTF_8));while(data.hasRemaining())channel.write(data);
        }
    }
    private static SeekableByteChannel openForWrite(String path) throws Exception {
        try {
            return (SeekableByteChannel)mountType.getMethod("openForWrite",String.class).invoke(mount,path);
        } catch(NoSuchMethodException newerFilesystemApi) {
            // CC 1.120 replaces openForWrite with openFile and explicit options.
            return (SeekableByteChannel)mountType.getMethod("openFile",String.class,java.util.Set.class)
                .invoke(mount,path,java.util.Set.of(java.nio.file.StandardOpenOption.WRITE,
                    java.nio.file.StandardOpenOption.CREATE,java.nio.file.StandardOpenOption.TRUNCATE_EXISTING));
        }
    }
    private static String read(String path) throws Exception {
        if(mount==null || !Boolean.TRUE.equals(mountType.getMethod("exists",String.class).invoke(mount,path)))return "";
        try(var channel=(SeekableByteChannel)mountType.getMethod("openForRead",String.class).invoke(mount,path)) {
            var bytes=ByteBuffer.allocate((int)Math.min(16384,channel.size()));while(bytes.hasRemaining() && channel.read(bytes)>0){}
            bytes.flip();return StandardCharsets.UTF_8.decode(bytes).toString().trim();
        }
    }
}
