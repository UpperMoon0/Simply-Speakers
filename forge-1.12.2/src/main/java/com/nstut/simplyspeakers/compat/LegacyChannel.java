package com.nstut.simplyspeakers.compat;
import java.util.*;
import java.util.function.*;
import io.netty.buffer.*;
import net.minecraft.entity.player.*;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
/** Strict direction-checked dispatcher over Forge's real SimpleNetworkWrapper. */
public final class LegacyChannel {
 private static final int MAX_BYTES=1024*1024;
 private static LegacyChannel active;
 private final SimpleNetworkWrapper channel;
 private final List<Codec<?>> codecs=new ArrayList<>();
 private final Map<Class<?>,Integer> ids=new HashMap<>();
 private LegacyChannel(String name) { channel=NetworkRegistry.INSTANCE.newSimpleChannel(name);active=this;channel.registerMessage(ServerHandler.class,Wire.class,0,Side.SERVER);channel.registerMessage(ClientHandler.class,Wire.class,1,Side.CLIENT); }
 public static LegacyChannel create(ResourceLocation id) { return new LegacyChannel(id.getNamespace()); }
 public <T> void register(Class<T> type,BiConsumer<T,LegacyBuffer> encode,Function<LegacyBuffer,T> decode,BiConsumer<T,Supplier<LegacyNetwork.PacketContext>> handle) {
  if(ids.containsKey(type))throw new IllegalStateException("Duplicate packet");boolean c2s=type.getSimpleName().endsWith("C2S");if(!c2s&&!type.getSimpleName().endsWith("S2C"))throw new IllegalArgumentException("Packet direction required");ids.put(type,codecs.size());codecs.add(new Codec<T>(encode,decode,handle,c2s));
 }
 @SuppressWarnings("unchecked") private Wire encode(Object packet,boolean c2s) { Integer id=ids.get(packet.getClass());if(id==null)throw new IllegalArgumentException("Unregistered packet");Codec<Object> codec=(Codec<Object>)codecs.get(id);if(codec.c2s!=c2s)throw new IllegalArgumentException("Wrong packet direction");LegacyBuffer buf=new LegacyBuffer(Unpooled.buffer());try { codec.encode.accept(packet,buf);if(buf.readableBytes()>MAX_BYTES)throw new IllegalArgumentException("Packet too large");byte[] bytes=new byte[buf.readableBytes()];buf.readBytes(bytes);return new Wire(id,bytes); } finally { buf.release(); } }
 public void sendToServer(Object packet) { channel.sendToServer(encode(packet,true)); }
 public void sendToPlayer(EntityPlayerMP player,Object packet) { channel.sendTo(encode(packet,false),player); }
 public void sendToPlayers(Iterable<? extends EntityPlayerMP> players,Object packet) { Wire wire=encode(packet,false);for(EntityPlayerMP player:players)channel.sendTo(wire,player); }
 @SuppressWarnings("unchecked") private void receive(Wire wire,boolean c2s,LegacyNetwork.PacketContext context) {
  if(wire.id<0||wire.id>=codecs.size())throw new IllegalArgumentException("Unknown packet ID");Codec<Object> codec=(Codec<Object>)codecs.get(wire.id);if(codec.c2s!=c2s)throw new IllegalArgumentException("Rejected reversed packet direction");LegacyBuffer buf=new LegacyBuffer(Unpooled.wrappedBuffer(wire.bytes));try { Object packet=codec.decode.apply(buf);if(buf.isReadable())throw new IllegalArgumentException("Trailing packet data");codec.handle.accept(packet,()->context); } finally { buf.release(); }
 }
 private static final class Codec<T> { final BiConsumer<T,LegacyBuffer> encode;final Function<LegacyBuffer,T> decode;final BiConsumer<T,Supplier<LegacyNetwork.PacketContext>> handle;final boolean c2s;Codec(BiConsumer<T,LegacyBuffer> e,Function<LegacyBuffer,T>d,BiConsumer<T,Supplier<LegacyNetwork.PacketContext>>h,boolean c){encode=e;decode=d;handle=h;c2s=c;} }
 public static final class Wire implements IMessage {
  int id;byte[] bytes;
  public Wire() {}
  Wire(int id,byte[] bytes) { this.id=id;this.bytes=bytes; }
  public void fromBytes(ByteBuf buf) { if(buf.readableBytes()<4||buf.readableBytes()>MAX_BYTES+4)throw new IllegalArgumentException("Invalid packet size");id=buf.readInt();bytes=new byte[buf.readableBytes()];buf.readBytes(bytes); }
  public void toBytes(ByteBuf buf) { buf.writeInt(id);buf.writeBytes(bytes); }
 }
 public static final class ServerHandler implements IMessageHandler<Wire,IMessage> {
  public IMessage onMessage(Wire wire,MessageContext ctx) { final EntityPlayerMP player=ctx.getServerHandler().player;active.receive(wire,true,new LegacyNetwork.PacketContext(){public EntityPlayer getPlayer(){return player;}public void queue(Runnable work){player.getServerWorld().addScheduledTask(()->{if(!player.isDead)work.run();});}});return null; }
 }
 public static final class ClientHandler implements IMessageHandler<Wire,IMessage> {
  public IMessage onMessage(Wire wire,MessageContext ctx) { active.receive(wire,false,com.nstut.simplyspeakers.client.LegacyClient.packetContext());return null; }
 }
}
