package com.nstut.simplyspeakers.compat;
import java.util.*;
import java.util.function.*;
public final class LegacyBuffers {
 private LegacyBuffers() {}
 public static <T> List<T> readList(LegacyBuffer buf,Function<LegacyBuffer,T> decoder) { int n=buf.readVarInt();if(n<0||n>16384||n>buf.readableBytes())throw new IllegalArgumentException("Invalid list length");List<T> out=new ArrayList<>(n);for(int i=0;i<n;i++)out.add(decoder.apply(buf));return out; }
 public static <T> void writeCollection(LegacyBuffer buf,Collection<T> values,BiConsumer<LegacyBuffer,T> encoder) { if(values.size()>16384)throw new IllegalArgumentException("List exceeds wire limit");buf.writeVarInt(values.size());for(T value:values)encoder.accept(buf,value); }
}
