package com.nstut.simplyspeakers.compat;
import net.minecraft.network.FriendlyByteBuf;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
/** VarInt-length collection wire format used by modern FriendlyByteBuf. */
public final class LegacyBuffers {
    public static final int MAX_ENTRIES=16384;
    private LegacyBuffers() {}
    public static FriendlyByteBuf writeUtf(FriendlyByteBuf buffer,String value) { return writeUtf(buffer,value,32767); }
    public static FriendlyByteBuf writeUtf(FriendlyByteBuf buffer,String value,int maxCharacters) {
        if(maxCharacters<0 || maxCharacters>32767 || value.length()>maxCharacters)
            throw new io.netty.handler.codec.EncoderException("String exceeds character bound");
        // 1.16's writer bounds encoded bytes, while modern callers bound UTF-16 characters.
        return buffer.writeUtf(value,Math.multiplyExact(maxCharacters,3));
    }
    public static <T> List<T> readList(FriendlyByteBuf buffer, Function<FriendlyByteBuf,T> decoder) {
        int size=buffer.readVarInt();
        if(size<0 || size>MAX_ENTRIES || size>buffer.readableBytes()) throw new IllegalArgumentException("Invalid audio library entry count: "+size);
        List<T> values=new ArrayList<T>(size);
        for(int i=0;i<size;i++)values.add(decoder.apply(buffer));
        return values;
    }
    public static <T> void writeCollection(FriendlyByteBuf buffer, Collection<T> values, BiConsumer<FriendlyByteBuf,T> encoder) {
        if(values.size()>MAX_ENTRIES)throw new IllegalArgumentException("Audio library exceeds bounded wire limit");
        buffer.writeVarInt(values.size());
        for(T value:values)encoder.accept(buffer,value);
    }
}
