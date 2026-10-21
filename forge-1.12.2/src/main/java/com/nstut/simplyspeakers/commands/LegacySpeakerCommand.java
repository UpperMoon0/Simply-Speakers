package com.nstut.simplyspeakers.commands;
import net.minecraft.command.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import com.nstut.simplyspeakers.compat.LegacyCommandSource;
import java.util.*;
/** Native Forge command registration backed by the unchanged full command tree. */
public final class LegacySpeakerCommand extends CommandBase {
 private final com.mojang.brigadier.CommandDispatcher<LegacyCommandSource> dispatcher=new com.mojang.brigadier.CommandDispatcher<>();
 public LegacySpeakerCommand(){SpeakerCommands.register(dispatcher);}
 public String getName(){return "simplyspeakers";}
 public List<String> getAliases(){return Collections.singletonList("ss");}
 public String getUsage(ICommandSender sender){return "/ss networks | network <name> ... | speaker <x y z> ... | audio ...";}
 public int getRequiredPermissionLevel(){return 2;}
 public void execute(MinecraftServer server,ICommandSender sender,String[] args)throws CommandException{try{dispatcher.execute("simplyspeakers "+String.join(" ",args),new LegacyCommandSource(server,sender,this));}catch(com.mojang.brigadier.exceptions.CommandSyntaxException e){throw new CommandException(e.getMessage());}}
 public List<String> getTabCompletions(MinecraftServer server,ICommandSender sender,String[] args,BlockPos pos){String text="simplyspeakers "+String.join(" ",args);List<String> out=new ArrayList<>();try{for(com.mojang.brigadier.suggestion.Suggestion suggestion:dispatcher.getCompletionSuggestions(dispatcher.parse(text,new LegacyCommandSource(server,sender,this))).get().getList())out.add(suggestion.getText());}catch(InterruptedException e){Thread.currentThread().interrupt();}catch(java.util.concurrent.ExecutionException e){com.nstut.simplyspeakers.SimplySpeakers.LOGGER.warn("Command suggestions failed",e);}return out;}
}
