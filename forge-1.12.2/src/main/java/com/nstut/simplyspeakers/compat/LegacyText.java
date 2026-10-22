package com.nstut.simplyspeakers.compat;
import net.minecraft.util.text.*;
/** Text stays native and translatable; the tiny bridge also supports Brigadier's Message interface. */
public final class LegacyText {
 private LegacyText() {}
 public static Literal literal(String value) { return new Literal(value); }
 public static Translatable translatable(String key,Object... args) { return new Translatable(key,args); }
 public static class Literal extends TextComponentString implements com.mojang.brigadier.Message {
  public Literal(String value) { super(value); }
  public Literal withStyle(TextFormatting color) { getStyle().setColor(color);return this; }
  public String getString() { return getUnformattedText(); }
 }
 public static class Translatable extends TextComponentTranslation implements com.mojang.brigadier.Message {
  public Translatable(String key,Object... args) { super(key,args); }
  public Translatable withStyle(TextFormatting color) { getStyle().setColor(color);return this; }
  public String getString() { return getUnformattedText(); }
 }
}
