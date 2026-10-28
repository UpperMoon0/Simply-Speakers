package com.nstut.simplyspeakers.client;

import java.nio.file.Files;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;

/** Opt-in, isolated Minecraft capture/timing fixture. Never runs in an ordinary game. */
public final class ZonePreviewRuntimeProbe {
    private static final ZonePreviewRuntimeProbe RUN = Boolean.getBoolean("simplyspeakers.zoneShaderAcceptance") ? new ZonePreviewRuntimeProbe() : null;
    private static final String[] SCENES={"small-outside","small-inside","large-outside","large-inside","portable","terrain","fallback"};
    private static final int SAMPLES=300;
    private final long[] cpu=new long[SAMPLES],gpu=new long[SAMPLES],frames=new long[SAMPLES];
    private final int[] queries=new int[4];
    private final boolean[] pending=new boolean[4];
    private final String backend=System.getProperty("simplyspeakers.zonePreviewBackend","shader");
    private int worldTicks,phase=-1,phaseTicks,cpuCount,gpuCount,queryIndex;
    private long started,lastFrame;
    private boolean timing,queryActive,awaitingCapture;
    private volatile boolean written;
    private BlockPos speaker;
    private ArmorStand camera;

    public static void tick(Minecraft client) { if(RUN!=null) RUN.update(client); }
    public static void beginFrame() { if(RUN!=null) RUN.begin(); }
    public static void endFrame() { if(RUN!=null) RUN.end(); }

    private void update(Minecraft client) {
        if(client.level==null || client.player==null) return;
        if(++worldTicks<200 || client.screen!=null) return;
        client.level.setDayTime(6000);
        client.level.setRainLevel(0); client.level.setThunderLevel(0);
        if(phase<0) {
            client.options.enableVsync().set(false); client.options.framerateLimit().set(260);
            client.options.bobView().set(false); client.options.renderDistance().set(8);
            client.options.pauseOnLostFocus=false;
            client.gameRenderer.setRenderHand(false);
            client.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
            client.getToasts().clear();
            if(org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(client.getWindow().getWindow(),org.lwjgl.glfw.GLFW.GLFW_VISIBLE)!=0)
                throw new IllegalStateException("Zone acceptance requires an invisible window");
            client.player.setPos(0,-60,0);
            speaker=new BlockPos(0,-60,0);
            for(int x=-16;x<=16;x++)for(int z=-16;z<=16;z++)
                client.level.setBlock(speaker.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
            client.level.setBlock(speaker,com.nstut.simplyspeakers.blocks.BlockRegistries.SPEAKER.get().defaultBlockState(),3);
            camera=new ArmorStand(client.level,speaker.getX(),speaker.getY(),speaker.getZ());
            camera.setInvisible(true); camera.setNoGravity(true);
            client.setCameraEntity(camera);
            System.out.println("ZONE_PREVIEW_RUNTIME_GPU backend="+backend+" hidden=true renderer="+GL11.glGetString(GL11.GL_RENDERER)+" version="+GL11.glGetString(GL11.GL_VERSION));
            next(client); return;
        }
        if(awaitingCapture) {
            if(written) next(client);
            return;
        }
        phaseTicks++;
        if(phase==4) {
            double radians=phaseTicks*.035;
            client.player.setPos(speaker.getX()+Math.cos(radians)*3,speaker.getY(),speaker.getZ()+Math.sin(radians)*3);
            client.player.setYRot((float)Math.toDegrees(radians));
            client.player.setDeltaMovement(0,0,0);
        }
        if(phaseTicks==100) {
            cpuCount=0;gpuCount=0;lastFrame=0;timing=true;
            for(int i=0;i<queries.length;i++) { queries[i]=GL15.glGenQueries();pending[i]=false; }
        }
    }

    private void next(Minecraft client) {
        DirectionalPreview.clear();
        phase++;phaseTicks=0;timing=false;awaitingCapture=false;written=false;
        if(phase>=SCENES.length || (phase==6 && backend.equals("particles"))) {
            System.clearProperty("simplyspeakers.previewHudOnly");
            client.setCameraEntity(client.player);
            System.out.println("ZONE_PREVIEW_RUNTIME_PASS backend="+backend+" scenes="+phase+" cpuGpuSamples="+SAMPLES);
            client.stop();return;
        }
        if(phase==5) for(int y=0;y<=10;y++)for(int z=-12;z<=12;z++)
            client.level.setBlock(speaker.offset(5,y,z),Blocks.STONE.defaultBlockState(),3);
        System.setProperty("simplyspeakers.previewHudOnly",Boolean.toString(phase==6));
        int range=phase==2 || phase==3?512:16;
        double x=24,y=14,z=24,tx=0,tz=0;
        if(phase==1) { x=0;y=2;z=1;tz=-16; }
        if(phase==2) { x=850;y=500;z=850; }
        if(phase==3) { x=20;y=12;z=20;tx=-200;tz=-400; }
        camera.setPos(speaker.getX()+.5+x,speaker.getY()+.5+y-camera.getEyeHeight(),speaker.getZ()+.5+z);
        double dx=tx-x,dy=-y,dz=tz-z;
        float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz));
        camera.setYRot(yaw);camera.yHeadRot=yaw;camera.yHeadRotO=yaw;camera.yBodyRot=yaw;camera.yBodyRotO=yaw;
        camera.setXRot((float)-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz))));
        camera.setOldPosAndRot();
        DirectionalPreview.show(phase==4?new BlockPos(0,-2048,0):speaker,range,1,1,90,.9);
        System.out.println("ZONE_PREVIEW_RUNTIME_SCENE backend="+backend+" scene="+SCENES[phase]+" range="+range);
    }

    private void begin() {
        if(!timing) return;
        long now=System.nanoTime();
        if(cpuCount<SAMPLES) frames[cpuCount]=lastFrame==0?0:now-lastFrame;
        lastFrame=now;started=now;
        int query=queries[queryIndex];
        if(pending[queryIndex] && GL15.glGetQueryObjecti(query,GL15.GL_QUERY_RESULT_AVAILABLE)!=0) {
            if(gpuCount<SAMPLES) gpu[gpuCount++]=GL33.glGetQueryObjectui64(query,GL15.GL_QUERY_RESULT);
            pending[queryIndex]=false;
        }
        queryActive=!pending[queryIndex] && gpuCount<SAMPLES;
        if(queryActive) GL15.glBeginQuery(GL33.GL_TIME_ELAPSED,query);
    }

    private void end() {
        if(!timing) return;
        long elapsed=System.nanoTime()-started;
        if(cpuCount<SAMPLES) cpu[cpuCount++]=elapsed;
        if(queryActive) { GL15.glEndQuery(GL33.GL_TIME_ELAPSED);pending[queryIndex]=true; }
        queryIndex=(queryIndex+1)%queries.length;
        if(cpuCount<SAMPLES || gpuCount<SAMPLES) return;
        if(!DirectionalPreview.isActive() || DirectionalPreview.renderedHudFrames()==0)
            throw new IllegalStateException("Preview expired or HUD absent during "+SCENES[phase]);
        if(phase!=6 && DirectionalPreview.emittedSamples()==0)
            throw new IllegalStateException("World preview never submitted geometry during "+SCENES[phase]);
        timing=false;
        for(int query:queries) GL15.glDeleteQueries(query);
        var client=Minecraft.getInstance();
        String name=SCENES[phase];
        try {
            var directory=client.gameDirectory.toPath().resolve("zone-preview-evidence");Files.createDirectories(directory);
            StringBuilder rows=new StringBuilder("sample,cpu_render_ns,gpu_render_ns,frame_interval_ns\n");
            for(int i=0;i<SAMPLES;i++) rows.append(i).append(',').append(cpu[i]).append(',').append(gpu[i]).append(',').append(frames[i]).append('\n');
            Files.writeString(directory.resolve(backend+"-"+name+".csv"),rows);
            System.out.println("ZONE_PREVIEW_RUNTIME_TIMING backend="+backend+" scene="+name+" samples="+SAMPLES
                +" cpuMedianUs="+percentile(cpu,.5)/1000+" cpuP95Us="+percentile(cpu,.95)/1000
                +" gpuMedianUs="+percentile(gpu,.5)/1000+" gpuP95Us="+percentile(gpu,.95)/1000);
            awaitingCapture=true;
            net.minecraft.client.Screenshot.grab(client.gameDirectory,backend+"-"+name+".png",client.getMainRenderTarget(),
                message -> { written=true;System.out.println("ZONE_PREVIEW_RUNTIME_CAPTURE "+message.getString()); });
        } catch(Exception error) { throw new IllegalStateException("Zone runtime fixture failed",error); }
    }
    private static long percentile(long[] values,double quantile) {
        long[] sorted=values.clone();Arrays.sort(sorted);return sorted[(int)((sorted.length-1)*quantile)];
    }
}
