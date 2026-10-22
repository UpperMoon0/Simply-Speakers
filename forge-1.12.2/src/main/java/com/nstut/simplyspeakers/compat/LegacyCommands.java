package com.nstut.simplyspeakers.compat;
public final class LegacyCommands {
 public static com.mojang.brigadier.builder.LiteralArgumentBuilder<LegacyCommandSource> literal(String text){return com.mojang.brigadier.builder.LiteralArgumentBuilder.literal(text);}
 public static <T> com.mojang.brigadier.builder.RequiredArgumentBuilder<LegacyCommandSource,T> argument(String name,com.mojang.brigadier.arguments.ArgumentType<T> type){return com.mojang.brigadier.builder.RequiredArgumentBuilder.argument(name,type);}
}
