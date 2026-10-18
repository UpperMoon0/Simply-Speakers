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
    private static int rangeBlocks;
    private static float dropoff, strength, angle, rear;
    private static int facingOrdinal;
    private static net.minecraft.world.phys.Vec3 renderOrigin;
    private static double rotation;
    private static int emitted;
    private static int hudFrames;
    static boolean isActive() { return remaining > 0; }
    static int renderedHudFrames() { return hudFrames; }
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
        origin=pos.immutable(); dimension=client.level.dimension(); remaining=600; emitted=0; hudFrames=0;
        rangeBlocks=Math.max(1,Math.min(512,(int)Math.round(range)));
        DirectionalPreview.dropoff=(float)dropoff; DirectionalPreview.strength=(float)strength;
        DirectionalPreview.angle=(float)angle; DirectionalPreview.rear=(float)rear; facingOrdinal=facing;
        renderOrigin=net.minecraft.world.phys.Vec3.atCenterOf(origin); rotation=0;
        samples=DirectionalPreviewGeometry.samples((int)Math.round(range),(float)dropoff,(float)strength,(float)angle,(float)rear,facing);
    }
    public static void tick(Minecraft client) {
        if(remaining<=0) return;
        if(client.level==null || !client.level.dimension().equals(dimension)) { clear();return; }
        if (client.screen != null) { clear(); return; }
        --remaining;
        renderOrigin = net.minecraft.world.phys.Vec3.atCenterOf(origin);
        rotation = 0;
        if (portableHolder != null) {
            var holder = client.level.getPlayerByUUID(portableHolder);
            if (holder == null || !holder.isAlive()) { clear(); return; }
            renderOrigin = holder.position().add(0, .5, 0);
            rotation = Math.toRadians(holder.getYRot() + 180);
        }
        if (remaining%6!=0) return;
        for(var point:samples) {
            double x = point.x()*Math.cos(rotation)-point.z()*Math.sin(rotation);
            double z = point.x()*Math.sin(rotation)+point.z()*Math.cos(rotation);
            emitted++;
            int color=DirectionalPreviewGeometry.color(point);
            var particle=new DustParticleOptions(new org.joml.Vector3f(((color >> 16) & 255)/255f,((color >> 8) & 255)/255f,(color & 255)/255f),point.kind()==DirectionalPreviewGeometry.Kind.LEVEL?1.0f:1.6f);
            client.level.addAlwaysVisibleParticle(particle,true,renderOrigin.x+x,renderOrigin.y+point.y(),
                renderOrigin.z+z,0,0,0);
        }
    }
    /** North-up plan plus actual 3D listener gain; all samples use the playback attenuation function. */
    public static void renderHud(net.minecraft.client.gui.GuiGraphics g) {
        var client=Minecraft.getInstance();
        if(remaining<=0 || client.level==null || client.player==null || renderOrigin==null || client.screen!=null || client.options.hideGui) return;
        hudFrames++;
        var font=client.font;
        int x=8, y=8, width=Math.min(310,client.getWindow().getGuiScaledWidth()-16);
        g.fill(x,y,x+width,y+139,0xE619232E);
        g.fill(x,y,x+3,y+139,0xFF5EE3CB);
        g.drawString(font,net.minecraft.network.chat.Component.translatable("gui.simplyspeakers.preview.title",(remaining+19)/20),x+10,y+9,0xFFF4F7FF);
        int cx=x+45, cy=y+65, radius=31;
        for(var point:samples) {
            if(Math.abs(point.y())>.001) continue;
            double px=point.x()*Math.cos(rotation)-point.z()*Math.sin(rotation);
            double pz=point.x()*Math.sin(rotation)+point.z()*Math.cos(rotation);
            int sx=cx+(int)Math.round(px/rangeBlocks*radius), sz=cy+(int)Math.round(pz/rangeBlocks*radius);
            g.fill(sx,sz,sx+1,sz+1,0xFF000000|DirectionalPreviewGeometry.color(point));
        }
        g.fill(cx-2,cy-2,cx+3,cy+3,0xFFF4F7FF);
        var listener=client.player.position();
        double dx=listener.x-renderOrigin.x, dy=listener.y-renderOrigin.y, dz=listener.z-renderOrigin.z;
        double distance=Math.sqrt(dx*dx+dy*dy+dz*dz);
        double[] facing=com.nstut.simplyspeakers.audio.DirectionalAudio.facingFromOrdinal(facingOrdinal);
        double fx=facing[0]*Math.cos(rotation)-facing[1]*Math.sin(rotation);
        double fz=facing[0]*Math.sin(rotation)+facing[1]*Math.cos(rotation);
        float gain=com.nstut.simplyspeakers.audio.SpatialAudioCalculator.calculateDistanceGain(distance,rangeBlocks,1,dropoff,fx,fz,dx,dz,
            new com.nstut.simplyspeakers.audio.SpatialAudioCalculator.ConeSettings(strength,angle,rear));
        double mapScale=Math.max(rangeBlocks,Math.hypot(dx,dz)/1.15);
        int playerX=cx+(int)Math.round(dx/mapScale*radius), playerY=cy+(int)Math.round(dz/mapScale*radius);
        g.fill(playerX-2,playerY-2,playerX+3,playerY+3,0xFFFFD166);
        g.drawString(font,"N",cx-2,cy-radius-10,0xFFB7C7D8);
        g.drawString(font,net.minecraft.network.chat.Component.translatable("gui.simplyspeakers.preview."+(distance<rangeBlocks?"inside":"outside")),x+91,y+30,distance<rangeBlocks?0xFF5EE3CB:0xFFFFD166);
        g.drawString(font,net.minecraft.network.chat.Component.translatable("gui.simplyspeakers.preview.distance",Math.round(distance),rangeBlocks),x+91,y+44,0xFFF4F7FF);
        g.drawString(font,net.minecraft.network.chat.Component.translatable("gui.simplyspeakers.preview.gain",Math.round(gain*100)),x+91,y+58,0xFFF4F7FF);
        g.fill(x+91,y+73,x+width-10,y+77,0xFF394857);
        g.fill(x+91,y+73,x+91+Math.round((width-101)*gain),y+77,0xFF5EE3CB);
        g.drawString(font,net.minecraft.network.chat.Component.translatable("gui.simplyspeakers.preview.you"),x+91,y+84,0xFFFFD166);
        g.drawString(font,net.minecraft.network.chat.Component.translatable("gui.simplyspeakers.preview.legend"),x+10,y+105,0xFFB7C7D8);
        g.drawString(font,net.minecraft.network.chat.Component.translatable("gui.simplyspeakers.preview."+(strength>0?"direction":"omni")),x+10,y+117,0xFFB7C7D8);
        g.drawString(font,net.minecraft.network.chat.Component.translatable("gui.simplyspeakers.preview.dismiss"),x+10,y+129,0xFFB7C7D8);
    }
    public static void clear() { remaining=0;origin=null;dimension=null;portableHolder=null;samples=List.of(); }
}
