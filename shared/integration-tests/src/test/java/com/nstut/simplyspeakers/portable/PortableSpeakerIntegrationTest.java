package com.nstut.simplyspeakers.portable;

import com.nstut.simplyspeakers.SpeakerAccess;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.items.PortableSpeakerItem;
import com.nstut.simplyspeakers.network.*;
import com.nstut.simplyspeakers.speakers.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual item metadata, inventory reconciliation, endpoint, authorization and playback services.
 * Only Minecraft world/player objects, loader delivery and item/block-entity registration are supplied. */
class PortableSpeakerIntegrationTest {
    private static final String URL = "https://example.invalid/portable.wav";
    private MinecraftServer server;
    private ServerLevel level, nether;
    private PortableSpeakerItem item;
    private final List<ServerPlayer> players = new ArrayList<>();
    private final Map<ServerPlayer, ItemStack[]> inventories = new IdentityHashMap<>();
    private final List<Delivery> deliveries = new ArrayList<>();
    private MockedStatic<?> environment, spatial;
    private long time;
    private record Delivery(Object recipient, Object packet) { }

    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    @BeforeEach void setup() throws Exception {
        ServerSpeakerRegistry.resetForWorld(); ServerPlaybackManager.resetForWorld(); PortableSpeakerManager.resetForWorld();
        server = mock(MinecraftServer.class); level = world(Level.OVERWORLD); nether = world(Level.NETHER);
        PlayerList list = mock(PlayerList.class);
        when(server.getPlayerList()).thenReturn(list); when(server.getAllLevels()).thenReturn(List.of(level, nether));
        when(server.getTickCount()).thenReturn(4); when(list.getPlayers()).thenReturn(players);
        when(list.getPlayer(any(UUID.class))).thenAnswer(call -> players.stream()
                .filter(p -> p.getUUID().equals(call.getArgument(0))).findFirst().orElse(null));
        item = makeItem();
        installEndpointFactory();
        try {
            spatial = mockStatic(Class.forName("com.nstut.simplyspeakers.compat.sable.SpeakerSpatialResolver"), call -> {
                if (call.getMethod().getName().equals("resolveLogical")) {
                    var position = (net.minecraft.core.Position) call.getArgument(1);
                    return new Vec3(position.x(), position.y(), position.z());
                }
                if (call.getMethod().getName().equals("resolveLogicalFacing") && call.getArguments().length == 4)
                    return com.nstut.simplyspeakers.audio.DirectionalAudio.normalize(call.<Double>getArgument(2), call.<Double>getArgument(3));
                return RETURNS_DEFAULTS.answer(call);
            });
        } catch (ClassNotFoundException vanilla) { /* Only the 1.21.1 adapter integrates Sable. */ }
        environment = mockStatic(Class.forName("com.nstut.simplyspeakers.speakers.ServerPlaybackEnvironment"), call -> {
            return switch (call.getMethod().getName()) {
                case "audioFiles" -> null;
                case "emitterPosition" -> {
                    BlockPos pos = call.getArgument(1);
                    yield PortableSpeakerManager.isPortablePosition(pos)
                            ? PortableSpeakerManager.emitterPosition(call.getArgument(0), pos) : Vec3.atCenterOf(pos);
                }
                case "listenerPosition" -> ((ServerPlayer) call.getArgument(1)).position();
                default -> { deliveries.add(new Delivery(call.getArgument(0), call.getArgument(1))); yield null; }
            };
        });
    }
    @AfterEach void cleanup() {
        PortableSpeakerManager.resetForWorld(); ServerPlaybackManager.resetForWorld(); ServerSpeakerRegistry.resetForWorld();
        if (environment != null) environment.close();
        if (spatial != null) spatial.close();
    }
    private void installEndpointFactory() {
        PortableSpeakerManager.setEndpointFactoryForTests((id, pos, link) -> {
            BlockEntityType<?> type = mock(BlockEntityType.class); when(type.isValid(any())).thenReturn(true);
            return new PortableSpeakerEndpoint(id, pos, link, type, Blocks.IRON_BLOCK.defaultBlockState());
        });
    }
    private ServerLevel world(ResourceKey<Level> dimension) {
        ServerLevel world = mock(ServerLevel.class);
        when(world.dimension()).thenReturn(dimension); when(world.getServer()).thenReturn(server);
        when(world.getGameTime()).thenAnswer(call -> time); when(world.players()).thenReturn(players);
        return world;
    }
    /** Minecraft freezes intrusive item registries during bootstrap. Substitute only
     * registration: real ItemStacks still run all production metadata/copy operations.
     * A registered minecart supplies ordinary one-stack defaults on every version;
     * the holder's value retains the portable type, including the 26.1.2 holder API.
     * The live fixture separately asserts the real registered portable item's cap. */
    @SuppressWarnings("unchecked")
    private PortableSpeakerItem makeItem() {
        Item defaults = net.minecraft.world.item.Items.MINECART;
        var identity = new java.util.concurrent.atomic.AtomicReference<PortableSpeakerItem>();
        var holder = (net.minecraft.core.Holder.Reference<Item>) mock(net.minecraft.core.Holder.Reference.class, call -> {
            if (call.getMethod().getName().equals("value")) return identity.get();
            return call.getMethod().invoke(defaults.builtInRegistryHolder(), call.getArguments());
        });
        PortableSpeakerItem portable = mock(PortableSpeakerItem.class, call -> {
            if (call.getMethod().getName().equals("asItem")) return call.getMock();
            if (call.getMethod().getName().equals("builtInRegistryHolder")) return holder;
            if (call.getMethod().getDeclaringClass().isInstance(defaults))
                return call.getMethod().invoke(defaults, call.getArguments());
            return RETURNS_DEFAULTS.answer(call);
        });
        identity.set(portable);
        return portable;
    }
    private ServerPlayer player(double x) {
        ServerPlayer player = mock(ServerPlayer.class); Inventory inventory = mock(Inventory.class);
        when(player.getUUID()).thenReturn(UUID.randomUUID()); when(player.level()).thenReturn(level);
        when(player.isAlive()).thenReturn(true); when(player.getInventory()).thenReturn(inventory);
        try { when((ServerLevel) ServerPlayer.class.getMethod("serverLevel").invoke(player)).thenReturn(level); }
        catch (NoSuchMethodException modern) { /* 26.1.2 obtains the server level from level(). */ }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
        ItemStack[] slots = new ItemStack[41]; Arrays.fill(slots, ItemStack.EMPTY); inventories.put(player, slots);
        when(inventory.getContainerSize()).thenReturn(slots.length);
        when(inventory.getItem(anyInt())).thenAnswer(call -> slots[(Integer) call.getArgument(0)]);
        move(player, x); players.add(player); return player;
    }
    private void move(ServerPlayer player, double x) { when(player.position()).thenReturn(new Vec3(x, 64, 0)); }
    private ItemStack give(ServerPlayer player, int slot) {
        ItemStack stack = new ItemStack(item); inventories.get(player)[slot] = stack; return stack;
    }
    private void reconcile() { PortableSpeakerManager.serverTick(server); }
    private void scan() { ServerPlaybackManager.serverTick(server); }
    private PortableSpeakerEndpoint endpoint(ItemStack stack) { return PortableSpeakerManager.getEndpoint(PortableSpeakerItem.getIdentity(stack)); }
    private SpeakerState configure(PortableSpeakerEndpoint endpoint) {
        SpeakerState state = endpoint.getSpeakerState(); state.setAudioId(URL); state.setAudioFilename("portable.wav");
        state.setMaxRange(16); state.setLooping(true); endpoint.updateEmitterSnapshot(); return state;
    }
    private void play(PortableSpeakerEndpoint endpoint) {
        assertTrue(ServerSpeakerControlService.play(server, (ServerLevel) endpoint.getLevel(), endpoint.getFullStateKey()));
    }
    private long packets(ServerPlayer recipient, Class<?> type) {
        return deliveries.stream().filter(d -> d.recipient() == recipient && type.isInstance(d.packet())).count();
    }

    @Test void identitySurvivesEveryInventorySlotStackReplacementAndRepeatedScans() {
        ServerPlayer owner = player(0); ItemStack stack = give(owner, 35); reconcile();
        UUID identity = PortableSpeakerItem.getIdentity(stack); PortableSpeakerEndpoint first = endpoint(stack);
        assertNotNull(identity); assertNotNull(first); assertEquals(1, stack.getMaxStackSize());
        assertSame(first, SpeakerPacketSecurity.resolveTarget(owner, first.getBlockPos()));
        for (int slot : new int[]{0, 8, 35, 40}) {
            Arrays.fill(inventories.get(owner), ItemStack.EMPTY); inventories.get(owner)[slot] = stack;
            reconcile(); assertSame(first, endpoint(stack)); assertEquals(identity, PortableSpeakerItem.getIdentity(stack));
            assertSame(first, PortableSpeakerManager.resolve(owner, first.getBlockPos()));
        }
        // Normal item serialization/container code may replace the stack instance.
        ItemStack replacement = stack.copy(); inventories.get(owner)[40] = replacement; reconcile();
        assertSame(first, endpoint(replacement)); assertEquals(identity, PortableSpeakerItem.getIdentity(replacement));
        assertEquals(1, ServerSpeakerRegistry.getEmitters().size());
    }

    @Test void separateOffhandEquipmentIsIncludedAndInventoryAliasesAreNotTreatedAsCopies() {
        ServerPlayer owner = player(0); ItemStack stack = new ItemStack(item);
        when(owner.getItemInHand(net.minecraft.world.InteractionHand.OFF_HAND)).thenReturn(stack);
        reconcile(); UUID identity = PortableSpeakerItem.getIdentity(stack); PortableSpeakerEndpoint first = endpoint(stack);
        assertNotNull(first); assertSame(first, PortableSpeakerManager.resolve(owner, first.getBlockPos()));
        inventories.get(owner)[40] = stack; reconcile();
        assertEquals(identity, PortableSpeakerItem.getIdentity(stack)); assertEquals(1, ServerSpeakerRegistry.getEmitters().size());
    }

    @Test void diskReloadRestoresTheOriginalItemsSettingsPlaylistsAndPolicyPaused(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path world) throws Exception {
        ServerSpeakerRegistry.init(world); installEndpointFactory();
        ServerPlayer owner = player(0); ItemStack stack = give(owner, 35); reconcile();
        PortableSpeakerEndpoint before = endpoint(stack); UUID identity = before.getIdentity();
        SpeakerState state = configure(before); UUID friend = UUID.randomUUID();
        state.setAccessMode(SpeakerAccess.TRUSTED); state.trustPlayer(friend); state.setMaxVolume(.37f); state.setMaxRange(29);
        state.setNetworkName("Travel mix"); state.getPlaylist().add(URL, "Favorite");
        String playlistId = state.createSavedPlaylist("Road trip");
        state.findSavedPlaylist(playlistId).getPlaylist().add("second", "Second track");
        play(before); time = 80; state.getPlaylist().queueLast("request");
        PortableSpeakerManager.shutdown(server); ServerSpeakerRegistry.flushDirty();
        assertTrue(java.nio.file.Files.readString(world.resolve("speaker_registry.json")).contains(identity.toString()));
        ItemStack persistedItem = stack.copy(); inventories.get(owner)[35] = persistedItem;
        ServerPlaybackManager.resetForWorld(); ServerSpeakerRegistry.init(world); installEndpointFactory();
        assertNull(PortableSpeakerManager.getEndpoint(identity)); assertTrue(ServerSpeakerRegistry.getEmitters().isEmpty());
        reconcile(); PortableSpeakerEndpoint restored = endpoint(persistedItem); SpeakerState loaded = restored.getSpeakerState();
        assertEquals(identity, restored.getIdentity()); assertEquals(owner.getUUID(), loaded.getOwnerUuid());
        assertEquals(SpeakerAccess.TRUSTED, loaded.getAccessMode()); assertTrue(loaded.getTrustedPlayers().contains(friend));
        assertEquals(.37f, loaded.getMaxVolume()); assertEquals(29, loaded.getMaxRange()); assertEquals("Travel mix", loaded.getNetworkName());
        assertEquals(1, loaded.getPlaylist().size()); assertEquals(List.of("request"), loaded.getPlaylist().getQueue());
        assertEquals("Road trip", loaded.findSavedPlaylist(playlistId).getName());
        assertEquals(1, loaded.findSavedPlaylist(playlistId).getPlaylist().size()); assertTrue(loaded.isPaused());
        assertEquals(4f, loaded.getPlaybackPositionSeconds(time), .001);
        assertTrue(ServerPlaybackManager.getSubscribers(restored.location()).isEmpty());
    }

    @Test void simultaneousCreativeCopiesKeepOriginalIdentityAndSanitizeClones() {
        ServerPlayer originalHolder = player(0); ItemStack original = give(originalHolder, 35); reconcile();
        UUID originalId = PortableSpeakerItem.getIdentity(original); PortableSpeakerEndpoint originalEndpoint = endpoint(original);
        originalEndpoint.getSpeakerState().setNetworkName("Original private settings");
        PortableSpeakerItem.setLink(original, "protected", "minecraft:overworld");
        ServerPlayer copyingHolder = player(0); ItemStack clone = original.copy(); inventories.get(copyingHolder)[0] = clone;
        // The copy is scanned first: previously tracked ownership must still win.
        Collections.swap(players, 0, 1); reconcile();
        assertEquals(originalId, PortableSpeakerItem.getIdentity(original)); assertSame(originalEndpoint, endpoint(original));
        assertNotEquals(originalId, PortableSpeakerItem.getIdentity(clone));
        assertEquals("", PortableSpeakerItem.getSpeakerId(clone));
        assertEquals("", endpoint(clone).getSpeakerState().getNetworkName());
        assertFalse(endpoint(clone).getSpeakerState().isPlaying()); assertEquals(2, ServerSpeakerRegistry.getEmitters().size());
    }

    @Test void malformedOversizedStacksNeverBecomeActiveInventorySpeakers() {
        ServerPlayer owner = player(0); ItemStack stack = give(owner, 0); stack.setCount(2);
        reconcile(); assertNull(PortableSpeakerItem.getIdentity(stack));
        assertTrue(ServerSpeakerRegistry.getEmitters().isEmpty());
        stack.setCount(1); reconcile(); assertNotNull(endpoint(stack));
    }

    @Test void unauthorizedSavedLinkCannotAttachToAProtectedNetwork() {
        ServerPlayer holder = player(0); ItemStack stack = give(holder, 0);
        UUID id = UUID.randomUUID(); PortableSpeakerItem.setIdentity(stack, id);
        PortableSpeakerItem.setLink(stack, "private", "minecraft:overworld");
        SpeakerState saved = new SpeakerState(URL, "private.wav", false, false, -1);
        saved.setOwnerUuid(UUID.randomUUID()); saved.setAccessMode(SpeakerAccess.OWNER_ONLY);
        ServerSpeakerRegistry.updateSpeakerStateByFullKey("simplyspeakers:portable/portable_" + id, saved);
        ServerSpeakerRegistry.updateSpeakerStateByFullKey("minecraft:overworld/net_private", saved);
        reconcile(); PortableSpeakerEndpoint endpoint = endpoint(stack);
        assertEquals("", endpoint.getSpeakerId()); assertEquals("portable_" + id, endpoint.getStateKey());
        assertEquals(saved.getOwnerUuid(), endpoint.getSpeakerState().getOwnerUuid());
        assertFalse(SpeakerPacketSecurity.canControlSpeaker(holder, endpoint.getBlockPos()));
    }

    private void transport(ServerPlayer sender, BlockPos pos, byte action) throws Exception {
        var context = mock(dev.architectury.networking.NetworkManager.PacketContext.class, call -> {
            if (call.getMethod().getName().equals("getPlayer")) return sender;
            if (call.getMethod().getName().equals("queue")) { ((Runnable) call.getArgument(0)).run(); return null; }
            return RETURNS_DEFAULTS.answer(call);
        });
        var handle = Arrays.stream(TransportControlPacketC2S.class.getMethods())
                .filter(m -> m.getName().equals("handle")).findFirst().orElseThrow();
        Object argument = handle.getParameterTypes()[1] == java.util.function.Supplier.class
                ? (java.util.function.Supplier<dev.architectury.networking.NetworkManager.PacketContext>) () -> context : context;
        handle.invoke(null, new TransportControlPacketC2S(pos, action), argument);
    }

    @Test void holderTransferRotatesSessionTokenPausesAndPreservesIdentitySettingsAndOwnership() throws Exception {
        ServerPlayer first = player(0), next = player(4); ItemStack stack = give(first, 35); reconcile();
        PortableSpeakerEndpoint old = endpoint(stack); SpeakerState state = configure(old);
        state.setOwnerUuid(first.getUUID()); state.setAccessMode(SpeakerAccess.OWNER_ONLY); state.setMaxVolume(.37f);
        state.getPlaylist().add(URL, "Saved clip"); String savedId = state.createSavedPlaylist("Walking");
        play(old); time = 80;
        inventories.get(first)[35] = ItemStack.EMPTY; inventories.get(next)[40] = stack;
        assertNull(PortableSpeakerManager.resolve(first, old.getBlockPos()), "possession is checked before the next tick");
        assertNull(PortableSpeakerManager.resolve(next, old.getBlockPos()), "a transferred item must not reuse the old control session");
        reconcile(); PortableSpeakerEndpoint transferred = endpoint(stack);
        assertNotEquals(old.getBlockPos(), transferred.getBlockPos());
        assertEquals(old.getIdentity(), transferred.getIdentity()); assertTrue(transferred.getSpeakerState().isPaused());
        assertEquals(.37f, transferred.getSpeakerState().getMaxVolume());
        assertEquals(first.getUUID(), transferred.getSpeakerState().getOwnerUuid());
        assertNotNull(transferred.getSpeakerState().findSavedPlaylist(savedId));
        assertFalse(SpeakerPacketSecurity.canControlSpeaker(next, transferred.getBlockPos()));
        assertNull(ServerSpeakerRegistry.getEmitter(old.location()));
        transport(first, old.getBlockPos(), TransportControlPacketC2S.ACTION_PLAY);
        transport(next, old.getBlockPos(), TransportControlPacketC2S.ACTION_PLAY);
        transport(next, transferred.getBlockPos(), TransportControlPacketC2S.ACTION_PLAY);
        assertTrue(transferred.getSpeakerState().isPaused(), "actual stale and unauthorized C2S packets must not resume playback");
        transferred.getSpeakerState().setAccessMode(SpeakerAccess.PUBLIC);
        transport(next, transferred.getBlockPos(), TransportControlPacketC2S.ACTION_PLAY);
        assertFalse(transferred.getSpeakerState().isPaused(), "the current authorized holder's real packet handler must still work");
    }

    @Test void removalToGroundOrContainerAndReturnCannotLeaveGhostPlaybackOrAutoResume() {
        ServerPlayer owner = player(0), listener = player(4); ItemStack stack = give(owner, 0); reconcile();
        PortableSpeakerEndpoint old = endpoint(stack); configure(old); play(old); scan();
        assertEquals(Set.of(owner.getUUID(), listener.getUUID()), ServerPlaybackManager.getSubscribers(old.location()));
        inventories.get(owner)[0] = ItemStack.EMPTY; deliveries.clear();
        assertFalse(SpeakerPacketSecurity.canControlSpeaker(owner, old.getBlockPos()));
        reconcile();
        assertNull(endpoint(stack)); assertNull(ServerSpeakerRegistry.getEmitter(old.location()));
        assertTrue(ServerPlaybackManager.getSubscribers(old.location()).isEmpty());
        assertTrue(packets(listener, StopAudioPacketS2C.class) > 0);
        assertTrue(ServerSpeakerRegistry.getSpeakerStateByFullKey(old.getFullStateKey()).isPaused());
        inventories.get(owner)[35] = stack; reconcile();
        assertTrue(endpoint(stack).getSpeakerState().isPaused());
        assertTrue(ServerPlaybackManager.getSubscribers(endpoint(stack).location()).isEmpty());
    }

    @ParameterizedTest @ValueSource(strings = {"logout", "death", "removed", "spectator"})
    void unavailableHoldersImmediatelyLoseTheirEmitterAndControlSession(String cause) {
        ServerPlayer owner = player(0), listener = player(4); ItemStack stack = give(owner, 0); reconcile();
        PortableSpeakerEndpoint endpoint = endpoint(stack); configure(endpoint); play(endpoint); scan();
        switch (cause) {
            case "logout" -> { players.remove(owner); ServerPlaybackManager.handlePlayerQuit(server, owner.getUUID()); }
            case "death" -> when(owner.isAlive()).thenReturn(false);
            case "removed" -> when(owner.isRemoved()).thenReturn(true);
            case "spectator" -> when(owner.isSpectator()).thenReturn(true);
        }
        reconcile();
        assertNull(endpoint(stack)); assertNull(PortableSpeakerManager.resolve(owner, endpoint.getBlockPos()));
        assertTrue(ServerPlaybackManager.getSubscribers(endpoint.location()).isEmpty());
        assertTrue(packets(listener, StopAudioPacketS2C.class) > 0);
    }

    @Test void removingLinkedPortableLeavesExistingBlockNetworkPlaying() {
        ServerPlayer owner = player(0), listener = player(4); ItemStack stack = give(owner, 0);
        UUID id = UUID.randomUUID(); PortableSpeakerItem.setIdentity(stack, id);
        PortableSpeakerItem.setLink(stack, "shared", "minecraft:overworld");
        SpeakerState shared = new SpeakerState(URL, "shared.wav", false, true, -1);
        shared.setOwnerUuid(owner.getUUID());
        ServerSpeakerRegistry.updateSpeakerStateByFullKey("minecraft:overworld/net_shared", shared);
        ServerSpeakerRegistry.updateSpeakerStateByFullKey("simplyspeakers:portable/portable_" + id, shared);
        var block = new BlockPos(2, 64, 0); var location = new SpeakerLocation("minecraft:overworld", 2, 64, 0);
        ServerSpeakerRegistry.registerSpeaker(level, block, "net_shared");
        ServerSpeakerRegistry.upsertEmitter(new ServerEmitter(location, "net_shared", 16, 1, 1, false, true));
        reconcile(); PortableSpeakerEndpoint portable = endpoint(stack); assertEquals("shared", portable.getSpeakerId());
        play(portable); scan(); inventories.get(owner)[0] = ItemStack.EMPTY; deliveries.clear(); reconcile();
        assertNull(endpoint(stack));
        assertTrue(ServerSpeakerRegistry.getSpeakerStateByFullKey("minecraft:overworld/net_shared").isPlaying());
        assertFalse(ServerSpeakerRegistry.getSpeakerStateByFullKey("minecraft:overworld/net_shared").isPaused());
        assertTrue(ServerPlaybackManager.getSubscribers(location).contains(listener.getUUID()));
        assertTrue(ServerPlaybackManager.getSubscribers(portable.location()).isEmpty());
    }

    @Test void dimensionChangeClearsOldAudienceAndCannotJoinSameNamedDestinationNetwork() {
        ServerPlayer owner = player(0); ItemStack stack = give(owner, 0);
        PortableSpeakerItem.setIdentity(stack, UUID.randomUUID()); PortableSpeakerItem.setLink(stack, "station", "minecraft:overworld");
        SpeakerState oldNetwork = new SpeakerState(URL, "portable.wav", false, false, -1);
        oldNetwork.setNetworkName("Original station"); oldNetwork.setOwnerUuid(owner.getUUID());
        ServerSpeakerRegistry.updateSpeakerStateByFullKey("minecraft:overworld/net_station", oldNetwork);
        ServerSpeakerRegistry.updateSpeakerStateByFullKey("simplyspeakers:portable/portable_" + PortableSpeakerItem.getIdentity(stack), oldNetwork);
        SpeakerState destination = new SpeakerState(); destination.setNetworkName("Unrelated station");
        ServerSpeakerRegistry.updateSpeakerStateByFullKey("minecraft:the_nether/net_station", destination);
        reconcile(); PortableSpeakerEndpoint old = endpoint(stack); configure(old); play(old);
        when(owner.level()).thenReturn(nether); ServerPlaybackManager.handlePlayerDimensionChange(server, owner.getUUID()); reconcile();
        PortableSpeakerEndpoint moved = endpoint(stack);
        assertEquals(nether, moved.getLevel()); assertEquals("", moved.getSpeakerId());
        assertEquals("minecraft:the_nether/portable_" + old.getIdentity(), moved.getFullStateKey());
        assertEquals("Original station", moved.getSpeakerState().getNetworkName()); assertTrue(moved.getSpeakerState().isPaused());
        assertEquals("Unrelated station", ServerSpeakerRegistry.getSpeakerStateByFullKey("minecraft:the_nether/net_station").getNetworkName());
        assertNull(ServerSpeakerRegistry.getEmitter(old.location())); assertTrue(ServerPlaybackManager.getSubscribers(old.location()).isEmpty());
    }

    @Test void movingHolderReconcilesNewAndLostListenersWithoutRestartingThoseStillInRange() {
        ServerPlayer owner = player(0), left = player(-10), right = player(30); ItemStack stack = give(owner, 0); reconcile();
        PortableSpeakerEndpoint endpoint = endpoint(stack); configure(endpoint); play(endpoint); scan();
        assertEquals(Set.of(owner.getUUID(), left.getUUID()), ServerPlaybackManager.getSubscribers(endpoint.location()));
        deliveries.clear(); move(owner, 20); scan();
        assertEquals(Set.of(owner.getUUID(), right.getUUID()), ServerPlaybackManager.getSubscribers(endpoint.location()));
        assertEquals(0, packets(owner, PlayAudioPacketS2C.class), "movement must not restart an existing listener's decoder");
        assertEquals(1, packets(left, StopAudioPacketS2C.class)); assertEquals(1, packets(right, PlayAudioPacketS2C.class));
        assertTrue(packets(owner, PortableSpeakerPositionPacketS2C.class) > 0);
        var play = deliveries.stream().map(Delivery::packet).filter(PlayAudioPacketS2C.class::isInstance)
                .map(PlayAudioPacketS2C.class::cast).findFirst().orElseThrow();
        assertEquals(endpoint.getIdentity(), play.getPortableEmitter().identity());
        assertEquals(owner.getUUID(), play.getPortableEmitter().holderId()); assertEquals(20, play.getPortableEmitter().x());
        assertEquals(65, play.getPortableEmitter().y()); assertEquals(endpoint.getBlockPos(), play.getPos());
        ServerPlayer joining = player(22); deliveries.clear(); scan();
        assertEquals(1, packets(joining, PlayAudioPacketS2C.class));
        assertEquals(Set.of(owner.getUUID(), right.getUUID(), joining.getUUID()), ServerPlaybackManager.getSubscribers(endpoint.location()));
        assertEquals(0, packets(owner, PlayAudioPacketS2C.class));
    }

    @Test void staleForeignAndForgedTokensCannotBypassHolderOrDestinationPolicies() {
        ServerPlayer owner = player(0), stranger = player(0); ItemStack stack = give(owner, 0); reconcile();
        PortableSpeakerEndpoint endpoint = endpoint(stack); SpeakerState state = endpoint.getSpeakerState();
        state.setOwnerUuid(owner.getUUID()); state.setAccessMode(SpeakerAccess.OWNER_ONLY);
        assertTrue(SpeakerPacketSecurity.canControlSpeaker(owner, endpoint.getBlockPos()));
        assertFalse(SpeakerPacketSecurity.canControlSpeaker(stranger, endpoint.getBlockPos()));
        assertNull(SpeakerPacketSecurity.resolveTarget(stranger, endpoint.getBlockPos()));
        assertFalse(SpeakerPacketSecurity.canModify(owner, endpoint.getBlockPos().offset(1, 0, 0)));
        SpeakerState protectedNetwork = new SpeakerState(); protectedNetwork.setOwnerUuid(stranger.getUUID());
        protectedNetwork.setAccessMode(SpeakerAccess.OWNER_ONLY);
        ServerSpeakerRegistry.updateSpeakerStateByFullKey("minecraft:overworld/net_protected", protectedNetwork);
        assertFalse(SpeakerPacketSecurity.canRelinkSpeaker(owner, endpoint.getBlockPos(), "protected"));
        assertTrue(SpeakerPacketSecurity.canRelinkSpeaker(owner, endpoint.getBlockPos(), "new_station"));
    }
}
