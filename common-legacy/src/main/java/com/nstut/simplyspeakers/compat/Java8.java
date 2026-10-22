package com.nstut.simplyspeakers.compat;
import java.util.*;
public final class Java8 {
    private Java8() {}
    @SafeVarargs public static <T> List<T> list(T... values) { return listCopy(Arrays.asList(values)); }
    public static <T> List<T> listCopy(Collection<? extends T> values) { List<T> copy=new ArrayList<>(); for(T value:values) copy.add(Objects.requireNonNull(value)); return Collections.unmodifiableList(copy); }
    @SafeVarargs public static <T> Set<T> set(T... values) { Set<T> result=new HashSet<>(); for(T value:values) if(!result.add(Objects.requireNonNull(value))) throw new IllegalArgumentException("Duplicate element"); return Collections.unmodifiableSet(result); }
    public static <T> Set<T> setCopy(Collection<? extends T> values) { Set<T> copy=new HashSet<>(); for(T value:values) copy.add(Objects.requireNonNull(value)); return Collections.unmodifiableSet(copy); }
    @SuppressWarnings("unchecked") public static <K,V> Map<K,V> map(Object... pairs) { Map<K,V> result=new HashMap<>(); for(int i=0;i<pairs.length;i+=2) { K key=(K)Objects.requireNonNull(pairs[i]); V val=(V)Objects.requireNonNull(pairs[i+1]); if(result.put(key,val)!=null) throw new IllegalArgumentException("Duplicate key"); } return Collections.unmodifiableMap(result); }
    public static <K,V> Map<K,V> mapCopy(Map<? extends K,? extends V> values) { Map<K,V> copy=new HashMap<>(); values.forEach((k,v)->copy.put(Objects.requireNonNull(k),Objects.requireNonNull(v))); return Collections.unmodifiableMap(copy); }
    public static <T> T requireNonNullElse(T value,T fallback) { return value!=null?value:Objects.requireNonNull(fallback); }
    public static boolean isBlank(String text) { for(int i=0;i<text.length();) { int cp=text.codePointAt(i); if(!Character.isWhitespace(cp)) return false; i+=Character.charCount(cp); } return true; }
    public static String repeat(String text,int count) { if(count<0) throw new IllegalArgumentException("Negative repeat count"); StringBuilder out=new StringBuilder(Math.multiplyExact(text.length(),count)); for(int i=0;i<count;i++) out.append(text); return out.toString(); }
    public static byte[] readAllBytes(java.io.InputStream input) throws java.io.IOException { java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream(); byte[] block=new byte[8192]; int read; while((read=input.read(block))!=-1) { if(read>0)out.write(block,0,read); } return out.toByteArray(); }
    public static String readString(java.nio.file.Path path) throws java.io.IOException { return new String(java.nio.file.Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8); }
    public static java.nio.file.Path writeString(java.nio.file.Path path, CharSequence value) throws java.io.IOException { return java.nio.file.Files.write(path,value.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    public static <T> T service(Class<T> type) { java.util.Iterator<T> iterator=java.util.ServiceLoader.load(type).iterator(); if(!iterator.hasNext()) throw new IllegalStateException("Missing service: "+type.getName()); return iterator.next(); }
    public static String stripTrailing(String text) { int end=text.length(); while(end>0) { int cp=text.codePointBefore(end); if(!Character.isWhitespace(cp))break;end-=Character.charCount(cp); } return text.substring(0,end); }
}
