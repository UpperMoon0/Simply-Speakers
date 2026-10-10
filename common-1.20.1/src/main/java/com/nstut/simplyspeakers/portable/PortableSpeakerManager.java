package com.nstut.simplyspeakers.portable;

import com.nstut.simplyspeakers.SpeakerLink;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.items.PortableSpeakerItem;
import com.nstut.simplyspeakers.network.OpenPortableSpeakerPacketS2C;
import com.nstut.simplyspeakers.network.SpeakerStateUpdatePacketS2C;
import com.nstut.simplyspeakers.speakers.ServerPlaybackManager;
import com.nstut.simplyspeakers.speakers.ServerSpeakerRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Server-authoritative inventory ownership, identity reconciliation and emitter lifetime. */
public final class PortableSpeakerManager {
    public static final int TOKEN_Y = -2048;
    private static final String BACKUP_DIMENSION = "simplyspeakers:portable";
    private static final Map<UUID, Entry> entries = new HashMap<>();
    private static final Map<BlockPos, Entry> tokens = new HashMap<>();
    private static long nextToken = 1;

    @FunctionalInterface interface EndpointFactory {
        PortableSpeakerEndpoint create(UUID identity, BlockPos token, String speakerId);
    }
    private static EndpointFactory endpointFactory = PortableSpeakerEndpoint::new;
    static void setEndpointFactoryForTests(EndpointFactory factory) { endpointFactory = factory; }

    private static final class Entry {
        final PortableSpeakerEndpoint endpoint;
        ServerPlayer holder;
        ItemStack stack;
        Entry(PortableSpeakerEndpoint endpoint, ServerPlayer holder, ItemStack stack) {
            this.endpoint = endpoint; this.holder = holder; this.stack = stack;
        }
    }
    private record Carried(ServerPlayer holder, ItemStack stack) { }
    private PortableSpeakerManager() { }

    public static boolean isPortablePosition(BlockPos pos) { return pos != null && pos.getY() == TOKEN_Y; }
    public static PortableSpeakerEndpoint getEndpoint(UUID identity) {
        Entry entry = entries.get(identity);
        return entry == null ? null : entry.endpoint;
    }
    public static void resetForWorld() {
        entries.clear(); tokens.clear(); nextToken = 1;
        endpointFactory = PortableSpeakerEndpoint::new;
    }
    private static boolean eligible(ServerPlayer player) {
        return player != null && player.isAlive() && !player.isRemoved() && !player.isSpectator();
    }
    private static boolean isPortable(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getCount() == 1 && stack.getItem() instanceof PortableSpeakerItem;
    }
    private static List<ItemStack> inventoryStacks(ServerPlayer player) {
        List<ItemStack> result = new ArrayList<>();
        var seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<ItemStack, Boolean>());
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isPortable(stack) && seen.add(stack)) result.add(stack);
        }
        // Newer runtimes keep equipment separately; older ones expose the same
        // offhand stack through Inventory. Identity de-duplication covers both.
        for (var hand : net.minecraft.world.InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (isPortable(stack) && seen.add(stack)) result.add(stack);
        }
        return result;
    }
    private static boolean stillCarries(Entry entry, ServerPlayer player) {
        if (!eligible(player) || entry.holder != player || entry.endpoint.getLevel() != player.level()) return false;
        for (ItemStack stack : inventoryStacks(player)) {
            if (stack == entry.stack && entry.endpoint.getIdentity().equals(PortableSpeakerItem.getIdentity(stack))) return true;
        }
        return false;
    }

    /** Never resolve a client-selected token without checking current physical possession. */
    public static PortableSpeakerEndpoint resolve(ServerPlayer player, BlockPos token) {
        Entry entry = tokens.get(token);
        return entry != null && stillCarries(entry, player) ? entry.endpoint : null;
    }
    public static UUID holderId(Level level, BlockPos token) {
        Entry entry = tokens.get(token);
        return entry != null && entry.endpoint.getLevel() == level && stillCarries(entry, entry.holder)
                ? entry.holder.getUUID() : null;
    }
    public static Vec3 emitterPosition(Level level, BlockPos token) {
        PortableEmitterSnapshot pose = snapshot(level, token);
        return pose == null ? null : new Vec3(pose.x(), pose.y(), pose.z());
    }
    public static PortableEmitterSnapshot snapshot(Level level, BlockPos token) {
        Entry entry = tokens.get(token);
        if (entry == null || entry.endpoint.getLevel() != level || !stillCarries(entry, entry.holder)) return null;
        Vec3 pos = entry.holder.position().add(0, PortableEmitterSnapshot.EMITTER_HEIGHT, 0);
        return new PortableEmitterSnapshot(entry.endpoint.getIdentity(), entry.holder.getUUID(),
                ServerSpeakerRegistry.getDimension(level), pos.x, pos.y, pos.z, entry.holder.getYRot());
    }
    private static String backupKey(UUID identity) { return BACKUP_DIMENSION + "/portable_" + identity; }

    /** An independent paused snapshot keeps settings, ownership and all playlists on handoff/restart. */
    public static void persist(PortableSpeakerEndpoint endpoint) {
        if (endpoint == null || endpoint.getLevel() == null || endpoint.getLevel().isClientSide()) return;
        SpeakerState state = ServerSpeakerRegistry.getSpeakerState(endpoint.getLevel(), endpoint.getStateKey());
        if (state == null) return;
        SpeakerState saved = state.copy();
        if (saved.isPlaying() && !saved.isPaused()) saved.pauseAt(endpoint.getLevel().getGameTime());
        saved.setControllerVolume(null);
        ServerSpeakerRegistry.updateSpeakerStateByFullKey(backupKey(endpoint.getIdentity()), saved);
    }
    public static void linkChanged(PortableSpeakerEndpoint endpoint) {
        Entry entry = entries.get(endpoint.getIdentity());
        if (entry == null) return;
        PortableSpeakerItem.setLink(entry.stack, endpoint.getSpeakerId(), ServerSpeakerRegistry.getDimension(endpoint.getLevel()));
        persist(endpoint);
        // Reopening is deliberate only for this holder: the target and state key changed.
        sendOpen(entry.holder, endpoint);
    }

    private static BlockPos allocateToken() {
        // Both axes fit BlockPos's signed 26-bit network representation. Tokens never
        // collide within a server session and are not derived from a UUID hash.
        long token = nextToken++;
        if (token >= (1L << 50)) throw new IllegalStateException("Portable speaker token space exhausted");
        return new BlockPos((int) (token & 0x1ffffff), TOKEN_Y, (int) (token >>> 25));
    }
    private static Entry attach(UUID identity, Carried carried) {
        Level level = carried.holder().level();
        String dimension = ServerSpeakerRegistry.getDimension(level);
        String link = PortableSpeakerItem.getSpeakerId(carried.stack()).trim();
        if (link.length() > PortableSpeakerEndpoint.MAX_SPEAKER_ID_LENGTH) link = "";
        String oldDimension = PortableSpeakerItem.getDimension(carried.stack());
        SpeakerState saved = ServerSpeakerRegistry.getSpeakerStateByFullKey(backupKey(identity));
        // Named networks are dimension-scoped. Carrying an item through a portal must
        // never silently gain control of a same-named network in the destination.
        if (!oldDimension.isEmpty() && !oldDimension.equals(dimension)) link = "";
        if (SpeakerLink.isLinkableId(link)) {
            boolean operator = com.nstut.simplyspeakers.network.SpeakerPacketSecurity.isOperator(carried.holder());
            SpeakerState destination = ServerSpeakerRegistry.getSpeakerState(level, "net_" + link);
            // Item NBT is a reference, not authority to join a protected network.
            // A holder who cannot re-link keeps the private snapshot and its policy.
            if (saved == null || !com.nstut.simplyspeakers.SpeakerPermissions.canManage(saved, carried.holder().getUUID(), operator)
                    || (destination != null && !com.nstut.simplyspeakers.SpeakerPermissions.canManage(destination, carried.holder().getUUID(), operator))) link = "";
        }
        PortableSpeakerEndpoint endpoint = endpointFactory.create(identity, allocateToken(), link);
        endpoint.setLevel(level);
        Entry entry = new Entry(endpoint, carried.holder(), carried.stack());
        entries.put(identity, entry); tokens.put(endpoint.getBlockPos(), entry);
        SpeakerState state = ServerSpeakerRegistry.getSpeakerState(level, endpoint.getStateKey());
        if (state == null || (!SpeakerLink.isLinkableId(link) && saved != null)) {
            state = saved != null ? saved.copy() : new SpeakerState();
            if (saved == null) state.setOwnerUuid(carried.holder().getUUID());
            ServerSpeakerRegistry.updateSpeakerState(level, endpoint.getStateKey(), state);
            state = endpoint.getSpeakerState();
        }
        // An inventory acquisition is not permission to resume a saved standalone item.
        if (!SpeakerLink.isLinkableId(link) && state.isPlaying() && !state.isPaused()) state.pauseAt(level.getGameTime());
        PortableSpeakerItem.setLink(carried.stack(), link, dimension);
        endpoint.ensureServerRegistration();
        persist(endpoint);
        return entry;
    }
    private static void detach(Entry entry) {
        PortableSpeakerEndpoint endpoint = entry.endpoint;
        Level level = endpoint.getLevel();
        SpeakerState state = endpoint.getSpeakerState();
        boolean lastMain = ServerSpeakerRegistry.getSpeakerPositions(level, endpoint.getStateKey()).size() <= 1;
        if (state != null && lastMain && state.isPlaying() && !state.isPaused()) {
            state.pauseAt(level.getGameTime());
            ServerSpeakerRegistry.markDirty();
            ServerPlaybackManager.resyncState(level.getServer(), (net.minecraft.server.level.ServerLevel) level, endpoint.getFullStateKey());
        }
        persist(endpoint);
        // This overload removes only the emitter, preserving its state for later use.
        ServerSpeakerRegistry.unregisterSpeaker(level, endpoint.getBlockPos());
        entries.remove(endpoint.getIdentity()); tokens.remove(endpoint.getBlockPos());
    }
    public static void handlePlayerUnavailable(MinecraftServer server, UUID playerId) {
        for (Entry entry : new ArrayList<>(entries.values())) {
            if (entry.holder.getUUID().equals(playerId)) detach(entry);
        }
    }
    public static void shutdown(MinecraftServer server) {
        for (Entry entry : new ArrayList<>(entries.values())) detach(entry);
    }

    /** Runs before playback's throttled scan, including ticks where playback does no range work. */
    public static void serverTick(MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) return;
        Map<UUID, List<Carried>> candidates = new LinkedHashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!eligible(player)) continue;
            for (ItemStack stack : inventoryStacks(player)) {
                UUID id = PortableSpeakerItem.getIdentity(stack);
                if (id == null) { id = UUID.randomUUID(); PortableSpeakerItem.setIdentity(stack, id); }
                candidates.computeIfAbsent(id, ignored -> new ArrayList<>()).add(new Carried(player, stack));
            }
        }
        Map<UUID, Carried> carried = new LinkedHashMap<>();
        for (var candidate : candidates.entrySet()) {
            Entry existing = entries.get(candidate.getKey());
            List<Carried> copies = candidate.getValue();
            Carried original = copies.get(0);
            if (existing != null) {
                for (Carried copy : copies) if (copy.stack() == existing.stack) { original = copy; break; }
            }
            carried.put(candidate.getKey(), original);
            for (Carried copy : copies) {
                if (copy == original) continue;
                UUID id = UUID.randomUUID();
                PortableSpeakerItem.setIdentity(copy.stack(), id);
                carried.put(id, copy);
            }
        }
        // Detach before attaching to avoid orphaned old-dimension emitters or controls.
        for (Entry entry : new ArrayList<>(entries.values())) {
            Carried current = carried.get(entry.endpoint.getIdentity());
            if (current == null || current.holder() != entry.holder || current.holder().level() != entry.endpoint.getLevel()) detach(entry);
        }
        for (var item : carried.entrySet()) {
            Entry entry = entries.get(item.getKey());
            if (entry == null) entry = attach(item.getKey(), item.getValue());
            else entry.stack = item.getValue().stack();
            entry.endpoint.updateEmitterSnapshot();
            if (server.getTickCount() % 20 == 0) persist(entry.endpoint);
            if (server.getTickCount() % 2 == 0) {
                PortableEmitterSnapshot pose = snapshot(entry.endpoint.getLevel(), entry.endpoint.getBlockPos());
                if (pose != null) ServerPlaybackManager.sendPortablePosition(server, entry.endpoint.location(), pose);
            }
        }
    }
    public static void open(ServerPlayer player, ItemStack stack) {
        if (!eligible(player) || !isPortable(stack)) return;
        serverTick(player.level().getServer());
        UUID id = PortableSpeakerItem.getIdentity(stack);
        Entry entry = entries.get(id);
        if (entry == null || entry.stack != stack || !stillCarries(entry, player)) return;
        sendOpen(player, entry.endpoint);
    }
    private static void sendOpen(ServerPlayer player, PortableSpeakerEndpoint endpoint) {
        SpeakerState state = endpoint.getSpeakerState();
        if (state == null) return;
        String action = state.isPaused() ? "pause" : state.isPlaying() ? "play" : "stop";
        SpeakerStateUpdatePacketS2C update = new SpeakerStateUpdatePacketS2C(endpoint.getBlockPos(), endpoint.getSpeakerId(), action,
                state.getAudioId(), state.getAudioFilename(), state.getPlaybackStartTick(), state.isPlaybackLooping(), endpoint.getFullStateKey()).withSettings(state);
        com.nstut.simplyspeakers.network.PacketRegistries.CHANNEL.sendToPlayer(player, update);
        OpenPortableSpeakerPacketS2C.sendToPlayer(player, new OpenPortableSpeakerPacketS2C(endpoint.getBlockPos(), endpoint.getIdentity(), endpoint.getSpeakerId(), endpoint.getFullStateKey()));
        endpoint.sendPlaylistSync(player);
    }
}
