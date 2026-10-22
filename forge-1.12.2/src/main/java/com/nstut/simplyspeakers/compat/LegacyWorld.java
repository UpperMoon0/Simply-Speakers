package com.nstut.simplyspeakers.compat;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.List;
import java.util.ArrayList;
public final class LegacyWorld {
 private LegacyWorld() {}
 public static Vec3d position(net.minecraft.entity.Entity entity,float partial){return new Vec3d(entity.lastTickPosX+(entity.posX-entity.lastTickPosX)*partial,entity.lastTickPosY+(entity.posY-entity.lastTickPosY)*partial,entity.lastTickPosZ+(entity.posZ-entity.lastTickPosZ)*partial);}
 public static String dimension(World world) { return dimension(world.provider.getDimension()); }
 public static String dimension(int id) { return id==0?"minecraft:overworld":id==-1?"minecraft:the_nether":id==1?"minecraft:the_end":"minecraft:dimension_"+id; }
 public static Vec3d center(BlockPos pos) { return new Vec3d(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5); }
 public static List<EntityPlayerMP> players(World world) { List<EntityPlayerMP> result=new ArrayList<>();for(net.minecraft.entity.player.EntityPlayer p:world.playerEntities)if(p instanceof EntityPlayerMP)result.add((EntityPlayerMP)p);return result; }
}
