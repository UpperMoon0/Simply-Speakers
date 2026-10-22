package com.nstut.simplyspeakers.compat;

import io.netty.buffer.Unpooled;
import me.shedaniel.architectury.networking.NetworkManager;
import me.shedaniel.architectury.platform.Platform;
import me.shedaniel.architectury.utils.Env;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Preserve Architectury's wire IDs, but reject wrong-side payloads before decoding. */
public final class LegacyChannel {
    private final ResourceLocation id;
    private final Map<Class<?>, MessageInfo<?>> messages=new HashMap<>();
    private LegacyChannel(ResourceLocation id) { this.id=id; }
    public static LegacyChannel create(ResourceLocation id) { return new LegacyChannel(id); }
    static boolean accepts(Class<?> type, Env environment) {
        return type.getSimpleName().endsWith("C2S") ? environment==Env.SERVER
                : type.getSimpleName().endsWith("S2C") && environment==Env.CLIENT;
    }
    static ResourceLocation packetId(ResourceLocation channel,Class<?> type) {
        String key=UUID.nameUUIDFromBytes(type.getName().getBytes(StandardCharsets.UTF_8)).toString().replace("-", "");
        return new ResourceLocation(channel+"/"+key);
    }
    static <T> NetworkManager.NetworkReceiver receiver(Class<T> type,Function<FriendlyByteBuf,T> decoder,
                             BiConsumer<T,Supplier<NetworkManager.PacketContext>> handler) {
        return (buffer,context)-> {
            if(context==null || !accepts(type,context.getEnvironment()))return;
            T message=decoder.apply(buffer);
            handler.accept(message,()->context);
        };
    }
    public <T> void register(Class<T> type, BiConsumer<T,FriendlyByteBuf> encoder, Function<FriendlyByteBuf,T> decoder,
                             BiConsumer<T,Supplier<NetworkManager.PacketContext>> handler) {
        boolean c2s=type.getSimpleName().endsWith("C2S");
        if(!c2s && !type.getSimpleName().endsWith("S2C"))
            throw new IllegalArgumentException("Packet direction is not explicit: "+type.getName());
        ResourceLocation packet=packetId(id,type);
        messages.put(type,new MessageInfo<>(packet,encoder));
        // NetworkChannel 1.32 registers every packet on both sides. Native
        // NetworkManager supports real side-specific registration instead.
        if(c2s || Platform.getEnvironment()==Env.CLIENT)
            NetworkManager.registerReceiver(c2s?NetworkManager.c2s():NetworkManager.s2c(),packet,receiver(type,decoder,handler));
    }
    @SuppressWarnings("unchecked")
    private <T> MessageInfo<T> info(T message) {
        return (MessageInfo<T>)Objects.requireNonNull(messages.get(message.getClass()),"Unregistered packet type");
    }
    public <T> void sendToPlayer(ServerPlayer player,T message) {
        MessageInfo<T> info=info(message);NetworkManager.sendToPlayer(player,info.id,info.encode(message));
    }
    public <T> void sendToPlayers(Iterable<ServerPlayer> players,T message) {
        MessageInfo<T> info=info(message);NetworkManager.sendToPlayers(players,info.id,info.encode(message));
    }
    public <T> void sendToServer(T message) {
        MessageInfo<T> info=info(message);NetworkManager.sendToServer(info.id,info.encode(message));
    }
    private static final class MessageInfo<T> {
        final ResourceLocation id;
        final BiConsumer<T,FriendlyByteBuf> encoder;
        MessageInfo(ResourceLocation id,BiConsumer<T,FriendlyByteBuf> encoder) { this.id=id;this.encoder=encoder; }
        FriendlyByteBuf encode(T message) {
            FriendlyByteBuf buffer=new FriendlyByteBuf(Unpooled.buffer());
            try {encoder.accept(message,buffer);return buffer;}
            catch(RuntimeException|Error error) {buffer.release();throw error;}
        }
    }
}
