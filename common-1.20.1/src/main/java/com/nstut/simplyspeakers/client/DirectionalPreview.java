package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.audio.DirectionalPreviewGeometry;
import com.nstut.simplyspeakers.blocks.SpeakerBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import java.util.List;

/** Local, temporary particles only; never changes blocks or loads chunks. */
public final class DirectionalPreview {
    private static BlockPos origin;
    private static Object dimension;
    private static java.util.UUID portableHolder;
    private static int remaining;
    private static int emitted;
    static int emittedSamples() { return emitted; }
    private static List<DirectionalPreviewGeometry.Point> samples=List.of();
    private DirectionalPreview() {}
    public static void show(BlockPos pos,double range,double dropoff,double strength,double angle,double rear) {
        var client=Minecraft.getInstance();
        if(client.level==null) return;
        portableHolder = ClientPortableSpeakers.isPortableToken(pos) && client.player != null ? client.player.getUUID() : null;
        int facing = 2;
        if (portableHolder == null) {
            var state=client.level.getBlockState(pos);
            facing=state.hasProperty(SpeakerBlock.FACING)?state.getValue(SpeakerBlock.FACING).ordinal():2;
        }
        origin=pos.immutable(); dimension=client.level.dimension(); remaining=160; emitted=0;
        samples=DirectionalPreviewGeometry.samples((int)Math.round(range),(float)dropoff,(float)strength,(float)angle,(float)rear,facing);
    }
    public static void tick(Minecraft client) {
        if(remaining<=0) return;
        if(client.level==null || !client.level.dimension().equals(dimension)) { clear();return; }
        if(--remaining%4!=0) return;
        var renderOrigin = net.minecraft.world.phys.Vec3.atCenterOf(origin);
        double rotation = 0;
        if (portableHolder != null) {
            var holder = client.level.getPlayerByUUID(portableHolder);
            if (holder == null || !holder.isAlive()) { clear(); return; }
            renderOrigin = holder.position().add(0, .5, 0);
            rotation = Math.toRadians(holder.getYRot() + 180);
        }
        for(var point:samples) {
            double x = point.x()*Math.cos(rotation)-point.z()*Math.sin(rotation);
            double z = point.x()*Math.sin(rotation)+point.z()*Math.cos(rotation);
            emitted++;
            if(point.boundary() && remaining%12==0) client.level.addAlwaysVisibleParticle(net.minecraft.core.particles.ParticleTypes.END_ROD,true,
                renderOrigin.x+x,renderOrigin.y-.1,renderOrigin.z+z,0,0,0);
            // Teal outlines; green means stronger sound, amber means reduced gain.
            float gain=point.gain();
            int red=(int)(235+(90-235)*gain), green=(int)(166+(227-166)*gain), blue=(int)(60+(160-60)*gain);
            int color=point.boundary()?0x5EE3CB:(red << 16 | green << 8 | blue);
            var particle=new DustParticleOptions(new org.joml.Vector3f(((color >> 16) & 255)/255f,((color >> 8) & 255)/255f,(color & 255)/255f),point.boundary()?1.7f:1.1f);
            client.level.addAlwaysVisibleParticle(particle,true,renderOrigin.x+x,renderOrigin.y-.2,
                renderOrigin.z+z,0,0,0);
        }
    }
    public static void clear() { remaining=0;origin=null;dimension=null;portableHolder=null;samples=List.of(); }
}
