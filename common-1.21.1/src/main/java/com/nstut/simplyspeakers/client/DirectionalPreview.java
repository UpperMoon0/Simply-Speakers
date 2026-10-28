package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.audio.DirectionalPreviewGeometry;
import com.nstut.simplyspeakers.blocks.SpeakerBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import java.util.List;

/** Local, temporary shader preview; never changes blocks or loads chunks. */
public final class DirectionalPreview {
    private static final ZonePreviewRenderer renderer = new ZonePreviewRenderer();
    private static com.nstut.simplyspeakers.audio.ZonePreviewMesh mesh;
    private static final org.joml.Matrix4f viewProjection = new org.joml.Matrix4f();
    private static final java.nio.FloatBuffer matrixBuffer = org.lwjgl.BufferUtils.createFloatBuffer(16);
    private static Object world;
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
        renderer.close();
        world=client.level;
        origin=pos.immutable(); dimension=client.level.dimension(); remaining=600; emitted=0; hudFrames=0;
        rangeBlocks=Math.max(1,Math.min(512,(int)Math.round(range)));
        DirectionalPreview.dropoff=(float)dropoff; DirectionalPreview.strength=(float)strength;
        DirectionalPreview.angle=(float)angle; DirectionalPreview.rear=(float)rear; facingOrdinal=facing;
        samples=DirectionalPreviewGeometry.samples((int)Math.round(range),(float)dropoff,(float)strength,(float)angle,(float)rear,facing);
        mesh=com.nstut.simplyspeakers.audio.ZonePreviewMesh.create(rangeBlocks,(float)dropoff,(float)strength,(float)angle,(float)rear,facing);
        if (!updateRenderPose(client)) clear();
    }
    public static void tick(Minecraft client) {
        if(remaining<=0) return;
        if(client.level==null || client.level!=world || !client.level.dimension().equals(dimension)) { clear();return; }
        if (client.screen != null) { clear(); return; }
        --remaining;
        if (!updateRenderPose(client)) { clear(); return; }
        if (remaining == 0) clear();
    }
    /** Use the same world-space render pose as playback, including fixed speakers on moving bodies. */
    private static boolean updateRenderPose(Minecraft client) {
        if (portableHolder != null) {
            var holder = client.level.getPlayerByUUID(portableHolder);
            if (holder == null || !holder.isAlive()) return false;
            var base = com.nstut.simplyspeakers.client.compat.sable.ClientSpeakerSpatialResolver.resolveRender(client.level, holder.position());
            double yaw = Math.toRadians(holder.getYRot());
            var facing = com.nstut.simplyspeakers.client.compat.sable.ClientSpeakerSpatialResolver.resolveRenderFacing(client.level, holder.position(), -Math.sin(yaw), Math.cos(yaw));
            if (base == null || facing == null) return false;
            renderOrigin = base.add(0, .5, 0);
            rotation = Math.atan2(-facing[0], facing[1]) + Math.PI;
        } else {
            var base = com.nstut.simplyspeakers.client.compat.sable.ClientSpeakerSpatialResolver.resolveRender(client.level, origin);
            var facing = com.nstut.simplyspeakers.client.compat.sable.ClientSpeakerSpatialResolver.resolveRenderFacing(client.level, origin, facingOrdinal);
            if (base == null || facing == null) return false;
            double[] local = com.nstut.simplyspeakers.audio.DirectionalAudio.facingFromOrdinal(facingOrdinal);
            renderOrigin = base;
            rotation = Math.atan2(facing[1], facing[0]) - Math.atan2(local[1], local[0]);
        }
        return true;
    }
    public static void renderWorld(org.joml.Matrix4f view, org.joml.Matrix4f projection,
                                   net.minecraft.world.phys.Vec3 camera) {
        var client=Minecraft.getInstance();
        if (remaining<=0 || client.level==null || client.level!=world || client.screen!=null) return;
        if (!updateRenderPose(client)) { clear(); return; }
        if(mesh!=null) { renderer.upload(mesh); mesh=null; }
        viewProjection.set(projection).mul(view).get(0,matrixBuffer);
        renderer.draw(matrixBuffer,renderOrigin.x-camera.x,renderOrigin.y-camera.y,renderOrigin.z-camera.z,rotation);
        emitted++;
    }
    public static void reload() { clear(); renderer.reload(); }
    /** North-up plan plus actual 3D listener gain; all samples use the playback attenuation function. */
    public static void renderHud(net.minecraft.client.gui.GuiGraphics g) {
        var client=Minecraft.getInstance();
        if(remaining<=0 || client.level==null || client.player==null || renderOrigin==null || client.screen!=null || client.options.hideGui) return;
        if (!updateRenderPose(client)) { clear(); return; }
        hudFrames++;
        var font=client.font;
        int x=8, y=8, width=Math.min(230,client.getWindow().getGuiScaledWidth()-16);
        g.fill(x,y,x+width,y+107,0xE619232E);
        g.fill(x,y,x+3,y+107,0xFF5EE3CB);
        g.drawString(font,net.minecraft.network.chat.Component.translatable("gui.simplyspeakers.preview.title",(remaining+19)/20),x+10,y+9,0xFFF4F7FF);
        int cx=x+31, cy=y+50, radius=21;
        for(var point:samples) {
            if(Math.abs(point.y())>.001) continue;
            double px=point.x()*Math.cos(rotation)-point.z()*Math.sin(rotation);
            double pz=point.x()*Math.sin(rotation)+point.z()*Math.cos(rotation);
            int sx=cx+(int)Math.round(px/rangeBlocks*radius), sz=cy+(int)Math.round(pz/rangeBlocks*radius);
            g.fill(sx,sz,sx+1,sz+1,0xFF000000|DirectionalPreviewGeometry.color(point));
        }
        g.fill(cx-2,cy-2,cx+3,cy+3,0xFFF4F7FF);
        var listener=client.gameRenderer.getMainCamera().getPosition();
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
        g.drawString(font,net.minecraft.network.chat.Component.translatable("gui.simplyspeakers.preview."+(distance<rangeBlocks?"inside":"outside")),x+64,y+26,distance<rangeBlocks?0xFF5EE3CB:0xFFFFD166);
        g.drawString(font,net.minecraft.network.chat.Component.translatable("gui.simplyspeakers.preview.distance",Math.round(distance),rangeBlocks),x+64,y+38,0xFFF4F7FF);
        g.drawString(font,net.minecraft.network.chat.Component.translatable("gui.simplyspeakers.preview.gain",Math.round(gain*100)),x+64,y+50,0xFFF4F7FF);
        g.fill(x+64,y+60,x+width-10,y+63,0xFF394857);
        g.fill(x+64,y+60,x+64+Math.round((width-74)*gain),y+63,0xFF5EE3CB);
        g.drawString(font,net.minecraft.network.chat.Component.translatable("gui.simplyspeakers.preview.you"),x+64,y+64,0xFFFFD166);
        g.drawString(font,net.minecraft.network.chat.Component.translatable("gui.simplyspeakers.preview.legend"),x+10,y+75,0xFFB7C7D8);
        g.drawString(font,net.minecraft.network.chat.Component.translatable("gui.simplyspeakers.preview."+(strength>0?"direction":"omni")),x+10,y+85,0xFFB7C7D8);
        g.drawString(font,net.minecraft.network.chat.Component.translatable("gui.simplyspeakers.preview.dismiss"),x+10,y+95,0xFFB7C7D8);
    }
    public static void clear() { remaining=0;origin=null;dimension=null;portableHolder=null;renderOrigin=null;rotation=0;samples=List.of(); }
}
