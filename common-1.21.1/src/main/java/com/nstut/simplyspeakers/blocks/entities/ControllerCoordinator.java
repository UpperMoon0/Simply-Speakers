package com.nstut.simplyspeakers.blocks.entities;

import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.control.ControllerAction;
import com.nstut.simplyspeakers.speakers.*;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Resolves all loaded controller inputs once per server tick, independent of block tick order. */
public final class ControllerCoordinator {
    private ControllerCoordinator() { }
    private record Target(ServerLevel level, String network, BlockPos proxy) { }
    private record Setting(Target target, ControllerAction action) { }
    private record Applied(float value, float baseline, Object targetInstance) { }
    private static final Set<RedstoneControllerBlockEntity> controllers = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<Target, List<RedstoneControllerBlockEntity>> pulses = new HashMap<>();
    private static final Map<Setting, Applied> applied = new HashMap<>();
    public static void reset() { controllers.clear(); pulses.clear(); applied.clear(); }
    public static void register(RedstoneControllerBlockEntity controller) { controllers.add(controller); }
    public static void remove(RedstoneControllerBlockEntity controller) {
        controllers.remove(controller);
        pulses.values().forEach(list -> list.removeIf(c -> c == controller));
    }
    private static Target target(RedstoneControllerBlockEntity c) {
        return new Target((ServerLevel)c.getLevel(),c.getNetworkId(),c.isProxyTarget()?c.getProxyPos():null);
    }
    public static void pulse(RedstoneControllerBlockEntity controller) {
        pulses.computeIfAbsent(target(controller), ignored -> new ArrayList<>()).add(controller);
    }
    public static void flush(MinecraftServer server) {
        Map<Setting, List<RedstoneControllerBlockEntity>> groups = new HashMap<>();
        controllers.removeIf(c -> c.isRemoved() || c.getLevel() == null);
        for (var c : controllers) {
            if (c.getLevel() instanceof ServerLevel level && level.getServer() == server
                    && c.getAction().continuous() && eligible(c)) {
                groups.computeIfAbsent(new Setting(target(c),c.getAction()),ignored -> new ArrayList<>()).add(c);
            }
        }
        // Volume first, then enabled intent, then one winning pulse per network.
        for (ControllerAction mode : List.of(ControllerAction.VOLUME,ControllerAction.ENABLED)) {
            for (var entry : groups.entrySet()) {
                Setting setting=entry.getKey(); if (setting.action()!=mode) continue;
                var inputs=entry.getValue(); inputs.sort(Comparator.comparing(c -> c.getBlockPos().asLong()));
                var c=inputs.get(0);
                float value=mode==ControllerAction.ENABLED
                    ? (inputs.stream().anyMatch(i -> i.getLastSignal()>0)?1:0)
                    : (float)inputs.stream().mapToDouble(i -> ControllerAction.volume(i.getLastSignal(),i.getVolumeCeiling())).max().orElse(0);
                Applied previous=applied.get(setting);
                // A target reload must reapply even when the requested value is unchanged.
                float current=volume(setting.target());
                Object instance=setting.target().proxy()==null ? setting.target().level() : setting.target().level().getBlockEntity(setting.target().proxy());
                if (previous==null || previous.value()!=value || previous.targetInstance()!=instance || (mode==ControllerAction.VOLUME && current!=value)) {
                    if (!c.applyCoordinated(setting.target().level(),value)) continue;
                }
                applied.put(setting,new Applied(value,configuredVolume(setting.target()),instance));
                inputs.forEach(RedstoneControllerBlockEntity::coordinatedReady);
            }
        }
        var iterator=applied.entrySet().iterator();
        while(iterator.hasNext()) {
            var old=iterator.next();
            if(old.getKey().target().level().getServer()!=server || groups.containsKey(old.getKey())) continue;
            if (canRestore(old.getKey().target())) {
                if(old.getKey().action()==ControllerAction.VOLUME) restoreVolume(old.getKey().target(),old.getValue().baseline());
                else if(old.getKey().target().proxy()!=null) {
                    var target=old.getKey().target();
                    if(target.level().hasChunkAt(target.proxy()) && target.level().getBlockEntity(target.proxy()) instanceof ProxySpeakerBlockEntity proxy
                        && target.network().equals(proxy.getSpeakerId())) proxy.setControllerPlaying(true);
                    else if (!target.level().hasChunkAt(target.proxy())) restoreUnloadedProxy(target,null,true);
                }
            }
            iterator.remove();
        }
        var pending=new ArrayList<>(pulses.entrySet());
        for(var entry:pending) {
            if(entry.getKey().level().getServer()!=server) continue;
            pulses.remove(entry.getKey());
            entry.getValue().stream().filter(ControllerCoordinator::eligible)
                .max(Comparator.comparingInt((RedstoneControllerBlockEntity c)->priority(c.getAction()))
                    .thenComparing(c -> c.getBlockPos().asLong(),Comparator.reverseOrder()))
                .ifPresent(c -> c.applyPulse(entry.getKey().level()));
        }
    }
    private static int priority(ControllerAction action) {
        return switch(action) { case STOP -> 7; case ANNOUNCEMENT -> 6; case RESTART -> 5; case TOGGLE -> 4; case TRACK -> 3; case NEXT -> 2; case PREVIOUS -> 1; default -> 0; };
    }
    private static boolean eligible(RedstoneControllerBlockEntity c) { return c.coordinatorEligible(); }
    private static boolean canRestore(Target target) {
        SpeakerState state=ServerSpeakerRegistry.getSpeakerState(target.level(),"net_"+target.network());
        return state!=null; // Removing a transient override cannot change protected saved settings.
    }
    private static float volume(Target target) {
        if(target.proxy()!=null && target.level().getBlockEntity(target.proxy()) instanceof ProxySpeakerBlockEntity proxy) return proxy.getEffectiveVolume();
        SpeakerState state=ServerSpeakerRegistry.getSpeakerState(target.level(),"net_"+target.network());
        return state==null?1:state.getMaxVolume();
    }
    private static float configuredVolume(Target target) {
        if(target.proxy()!=null && target.level().getBlockEntity(target.proxy()) instanceof ProxySpeakerBlockEntity proxy) return proxy.getMaxVolume();
        SpeakerState state=ServerSpeakerRegistry.getSpeakerState(target.level(),"net_"+target.network());
        return state==null?1:state.getConfiguredMaxVolume();
    }
    private static void restoreUnloadedProxy(Target target,Float volume,Boolean active) {
        var pos=target.proxy();
        var location=new SpeakerLocation(target.level().dimension().location().toString(),pos.getX(),pos.getY(),pos.getZ());
        var emitter=ServerSpeakerRegistry.getEmitter(location);
        if(emitter!=null && emitter.proxy() && emitter.networkKey().equals("net_"+target.network())) {
            ServerSpeakerRegistry.upsertEmitter(new ServerEmitter(location,emitter.networkKey(),emitter.maxRange(),
                volume==null?emitter.maxVolume():volume,emitter.dropoff(),true,active==null?emitter.active():active,emitter.directionalExtras()));
        }
    }
    private static void restoreVolume(Target target,float value) {
        if(target.proxy()!=null) {
            if(target.level().hasChunkAt(target.proxy()) && target.level().getBlockEntity(target.proxy()) instanceof ProxySpeakerBlockEntity proxy
                && target.network().equals(proxy.getSpeakerId())) proxy.setControllerVolume(null);
            else if (!target.level().hasChunkAt(target.proxy())) restoreUnloadedProxy(target,value,null);
        } else if(ServerSpeakerRegistry.getSpeakerState(target.level(),"net_"+target.network())!=null) {
            ServerSpeakerControlService.setControllerVolume(target.level().getServer(),target.level(),ServerSpeakerRegistry.getRegistryKey(target.level(),"net_"+target.network()),null);
        }
    }
}
