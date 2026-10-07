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
    private static int remaining;
    private static int emitted;
    static int emittedSamples() { return emitted; }
    private static List<DirectionalPreviewGeometry.Point> samples=List.of();
    private DirectionalPreview() {}
    public static void show(BlockPos pos,double range,double dropoff,double strength,double angle,double rear) {
        var client=Minecraft.getInstance();
        if(client.level==null) return;
        var state=client.level.getBlockState(pos);
        int facing=state.hasProperty(SpeakerBlock.FACING)?state.getValue(SpeakerBlock.FACING).ordinal():2;
        origin=pos.immutable(); dimension=client.level.dimension(); remaining=160; emitted=0;
        samples=DirectionalPreviewGeometry.samples((int)Math.round(range),(float)dropoff,(float)strength,(float)angle,(float)rear,facing);
    }
    public static void tick(Minecraft client) {
        if(remaining<=0) return;
        if(client.level==null || !client.level.dimension().equals(dimension)) { clear();return; }
        if(--remaining%4!=0) return;
        for(var point:samples) {
            emitted++;
            if(point.boundary() && remaining%12==0) client.level.addAlwaysVisibleParticle(net.minecraft.core.particles.ParticleTypes.END_ROD,true,
                origin.getX()+.5+point.x(),origin.getY()+.4,origin.getZ()+.5+point.z(),0,0,0);
            // Teal outlines; green means stronger sound, amber means reduced gain.
            float gain=point.gain();
            int red=(int)(235+(90-235)*gain), green=(int)(166+(227-166)*gain), blue=(int)(60+(160-60)*gain);
            int color=point.boundary()?0x5EE3CB:(red << 16 | green << 8 | blue);
            var particle=new DustParticleOptions(color,point.boundary()?1.7f:1.1f);
            client.level.addAlwaysVisibleParticle(particle,true,origin.getX()+.5+point.x(),origin.getY()+.3,
                origin.getZ()+.5+point.z(),0,0,0);
        }
    }
    public static void clear() { remaining=0;origin=null;dimension=null;samples=List.of(); }
}
