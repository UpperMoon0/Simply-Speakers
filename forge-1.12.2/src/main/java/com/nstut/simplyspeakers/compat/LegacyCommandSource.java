package com.nstut.simplyspeakers.compat;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.ITextComponent;
public final class LegacyCommandSource {
 public final ICommandSender sender;private final MinecraftServer server;private final net.minecraft.command.ICommand command;
 public LegacyCommandSource(MinecraftServer server,ICommandSender sender){this(server,sender,null);}
 public LegacyCommandSource(MinecraftServer server,ICommandSender sender,net.minecraft.command.ICommand command){this.server=server;this.sender=sender;this.command=command;}
 public boolean hasPermission(int level){return sender.canUseCommand(level,"simplyspeakers");}
 public WorldServer getWorld(){return (WorldServer)sender.getEntityWorld();}
 public MinecraftServer getMinecraftServer(){return server;}
 public net.minecraft.entity.Entity getEntity(){return sender.getCommandSenderEntity();}
 public EntityPlayerMP getPlayer(){return sender instanceof EntityPlayerMP?(EntityPlayerMP)sender:null;}
 public void sendSuccess(java.util.function.Supplier<ITextComponent> text,boolean broadcast){ITextComponent message=text.get();if(broadcast&&command!=null)net.minecraft.command.CommandBase.notifyCommandListener(sender,command,"%s",message);else sender.sendMessage(message);}
 public void sendFailure(ITextComponent text){ITextComponent copy=text.createCopy();copy.getStyle().setColor(net.minecraft.util.text.TextFormatting.RED);sender.sendMessage(copy);}
}
