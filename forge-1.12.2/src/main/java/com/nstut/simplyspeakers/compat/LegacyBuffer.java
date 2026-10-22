package com.nstut.simplyspeakers.compat;
import net.minecraft.network.PacketBuffer;
import io.netty.buffer.ByteBuf;
/** Minecraft-native packet buffer with the newer bounded method spellings. */
public final class LegacyBuffer extends PacketBuffer {
 public LegacyBuffer(ByteBuf buf) { super(buf); }
 public net.minecraft.util.text.ITextComponent readComponent(){try{return readTextComponent();}catch(java.io.IOException error){throw new io.netty.handler.codec.DecoderException("Invalid text component",error);}}
 public LegacyBuffer writeComponent(net.minecraft.util.text.ITextComponent text){writeTextComponent(text);return this;}
 public String readUtf() { return readString(32767); }
 public String readUtf(int limit) { return readString(limit); }
 public LegacyBuffer writeUtf(String value) { return writeUtf(value,32767); }
 public LegacyBuffer writeUtf(String value,int limit) { if(limit<0||value.length()>limit)throw new IllegalArgumentException("String exceeds wire limit");byte[] bytes=value.getBytes(java.nio.charset.StandardCharsets.UTF_8);if(bytes.length>(long)limit*3)throw new IllegalArgumentException("Encoded string exceeds wire limit");writeVarInt(bytes.length);writeBytes(bytes);return this; }
 public int readVarInt() { return super.readVarInt(); }
 public LegacyBuffer writeVarInt(int value) { super.writeVarInt(value);return this; }
 public java.util.UUID readUUID() { return readUniqueId(); }
 public LegacyBuffer writeUUID(java.util.UUID value) { writeUniqueId(value);return this; }
 public byte[] readByteArray(int limit) { int length=readVarInt();if(length<0||length>limit||length>readableBytes())throw new IllegalArgumentException("Invalid byte array length");byte[] data=new byte[length];readBytes(data);return data; }
}
