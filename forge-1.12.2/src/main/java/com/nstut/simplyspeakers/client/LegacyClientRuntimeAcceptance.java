package com.nstut.simplyspeakers.client;

import com.nstut.openui.api.ButtonWidget;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.controls.TextField;
import com.nstut.openui.minecraft.UiScreen;
import com.nstut.openui.runtime.UiRuntime;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.client.screens.SpeakerScreen;
import com.nstut.simplyspeakers.client.ui.SimplySpeakersUiScreen;
import com.nstut.simplyspeakers.testing.LegacyRuntimeAcceptance;
import com.nstut.simplyspeakers.testing.RuntimeReceipt;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.text.ITextComponent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;

/** Real native client evidence. Null OpenAL output proves lifecycle, never audibility. */
public final class LegacyClientRuntimeAcceptance {
    private static final String KEY = "net_" + LegacyRuntimeAcceptance.SPEAKER_ID;
    private static final String[] PHASES = {"started", "paused", "resumed", "stopped"};
    private static boolean attemptedJoin, joined, done, uiDone, unloading;
    private static int phase, ticks, stableTicks, disconnectTicks, uiTicks;
    private static long reloadEpoch;
    private static Object resourceBeforePause, reloadResource, reloadContext;
    private static String reloadCase;
    private static float sampleOffset;
    private static SpeakerScreen screen;
    private static int modelTicks;
    private static boolean modelDone;
    private LegacyClientRuntimeAcceptance() { }

    public static void tick(Minecraft client) {
        if (!LegacyRuntimeAcceptance.enabled() || done) return;
        try {
            require(++ticks < 2400, "native client timed out at phase " + phase);
            if (!attemptedJoin) {
                String address = System.getProperty("simplyspeakers.legacyRuntimeServer", "");
                require(address.matches("127\\.0\\.0\\.1:[0-9]{1,5}"), "explicit loopback runtime server required");
                if (!(client.currentScreen instanceof GuiMainMenu)) return;
                require("1.8".equals(System.getProperty("java.specification.version")), "game client is not Java 8");
                RuntimeReceipt.emit("client"); attemptedJoin = true;
                client.displayGuiScreen(new GuiConnecting(client.currentScreen, client, new ServerData("Native acceptance", address, false)));
                return;
            }
            if (phase == PHASES.length) {
                if (++disconnectTicks < 20) return;
                if (client.world != null) {
                    client.world.sendQuittingDisconnectingPacket(); client.loadWorld(null);
                    client.displayGuiScreen(new GuiMainMenu()); return;
                }
                require(resource() == null && ClientAudioPlayer.verificationSnapshot(KEY).emitters() == 0,
                        "disconnected client retained native audio membership");
                done = true;
                System.out.println("SIMPLYSPEAKERS_1122_CLIENT_DISCONNECT_PASS");
                System.out.println("SIMPLYSPEAKERS_1122_CLIENT_ACCEPTANCE_PASS backendLifecycleOnly=true");
                client.shutdown(); return;
            }
            if (client.player == null || client.world == null) {
                require(!joined, "client disconnected before acceptance"); return;
            }
            if (!joined) { joined = true; System.out.println("SIMPLYSPEAKERS_1122_CLIENT_JOIN_PASS"); }
            if (!nativeContextReady()) return;
            ClientAudioPlayer.VerificationSnapshot snapshot = ClientAudioPlayer.verificationSnapshot(KEY);
            if (ticks % 100 == 0) System.out.println("SIMPLYSPEAKERS_1122_CLIENT_WAIT phase=" + phase + " " + snapshot);
            if (phase == 0) {
                if (!playingProgress(snapshot)) return;
                if (!verifyUi(client)) return;
                resourceBeforePause = resource();
            } else if (phase == 1) {
                if (!silent(snapshot) || !cleaned(resourceBeforePause)) return;
                if (!verifyReload(client, "paused", false)) return;
            } else if (phase == 2) {
                if (!playingProgress(snapshot)) return;
                if (!verifyReload(client, "playing", true)) return;
                resourceBeforePause = resource();
            } else {
                if (!silent(snapshot) || !cleaned(resourceBeforePause)) return;
                if (!unloading) {
                    reloadEpoch = ClientAudioPlayer.contextEpoch();
                    client.getSoundHandler().unloadSounds();
                    require(!ClientAudioPlayer.contextAvailable(), "vanilla unload did not suspend transformed audio gate");
                    require(!AL.isCreated(), "vanilla unload kept OpenAL context alive");
                    unloading = true;
                    client.getSoundHandler().onResourceManagerReload(client.getResourceManager()); return;
                }
                require(ClientAudioPlayer.contextEpoch() > reloadEpoch, "unload/reload failed to retire old epoch");
                if (++stableTicks < 20) return;
                require(silent(snapshot), "stopped audio resurrected after unload/reload");
                System.out.println("SIMPLYSPEAKERS_1122_AUDIO_UNLOAD_PASS epoch=" + reloadEpoch + "->" + ClientAudioPlayer.contextEpoch());
            }
            System.out.println("SIMPLYSPEAKERS_1122_CLIENT_PHASE_PASS " + PHASES[phase]
                    + " sources=" + snapshot.sources() + " emitters=" + snapshot.emitters() + " decodedBytes=" + snapshot.decodedBytes());
            client.player.sendChatMessage("/simplyspeakers_1122_accept " + PHASES[phase]);
            phase++; stableTicks = 0;
        } catch (Exception error) { throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL native client acceptance", error); }
    }

    private static boolean nativeContextReady() {
        return ClientAudioPlayer.contextAvailable() && AL.isCreated() && AL.getContext() != null
                && AL.getContext() == ALC10.alcGetCurrentContext();
    }
    private static boolean silent(ClientAudioPlayer.VerificationSnapshot snapshot) {
        return snapshot.sources() == 0 && snapshot.emitters() == 0;
    }
    private static boolean playingProgress(ClientAudioPlayer.VerificationSnapshot snapshot) throws Exception {
        if (!snapshot.playing() || snapshot.sources() != 1 || snapshot.emitters() != 1 || snapshot.decodedBytes() <= 0) {
            stableTicks = 0; return false;
        }
        int source = sourceId(resource());
        require(AL10.alIsSource(source), "source is not an actual OpenAL resource");
        float offset = AL10.alGetSourcef(source, AL11.AL_SEC_OFFSET);
        if (stableTicks++ == 0) { sampleOffset = offset; return false; }
        return stableTicks >= 10 && Math.abs(offset - sampleOffset) > .01f;
    }
    private static boolean verifyReload(Minecraft client, String testCase, boolean playing) throws Exception {
        if (reloadCase == null) {
            reloadCase = testCase; reloadEpoch = ClientAudioPlayer.contextEpoch();
            reloadResource = resource(); reloadContext = AL.getContext(); stableTicks = 0;
            client.getSoundHandler().onResourceManagerReload(client.getResourceManager()); return false;
        }
        require(reloadCase.equals(testCase), "overlapping reload probes");
        if (!nativeContextReady() || ClientAudioPlayer.contextEpoch() <= reloadEpoch || AL.getContext() == reloadContext) return false;
        ClientAudioPlayer.VerificationSnapshot snapshot = ClientAudioPlayer.verificationSnapshot(KEY);
        if (playing) {
            if (resource() == reloadResource || !playingProgress(snapshot) || !cleaned(reloadResource)) return false;
        } else {
            require(silent(snapshot) && resource() == null, "paused sound reload resurrected playback");
            if (++stableTicks < 20) return false;
        }
        System.out.println("SIMPLYSPEAKERS_1122_AUDIO_RELOAD_PASS " + testCase + " epoch=" + reloadEpoch + "->"
                + ClientAudioPlayer.contextEpoch() + " contextRecreated=true oldWorkerStopped=true decodedBytes=" + snapshot.decodedBytes());
        reloadCase = null; stableTicks = 0; return true;
    }

    private static boolean verifyUi(Minecraft client) throws Exception {
        if (uiDone) return true;
        if (screen == null) {
            SpeakerBlockEntity tile = null;
            for (TileEntity candidate : client.world.loadedTileEntityList)
                if (candidate instanceof SpeakerBlockEntity && LegacyRuntimeAcceptance.SPEAKER_ID.equals(((SpeakerBlockEntity) candidate).getSpeakerId()))
                    tile = (SpeakerBlockEntity) candidate;
            if (tile == null) return false;
            if (!verifyPlacedModel(client, tile)) return false;
            screen = new SpeakerScreen(tile.getPos()); client.displayGuiScreen(screen); return false;
        }
        require(client.currentScreen == screen, "native speaker screen lost ownership");
        UiRuntime runtime = screen.uiRuntime(); runtime.flushFrameTasks(); runtime.preRender(0, 0);
        if (++uiTicks < 10) return false; // Let actual permission and catalog packets reach the screen.
        TextField input = findText(runtime.root());
        if (input == null || input.getWidth() <= 0) return false;
        click(screen, input);
        require(runtime.hasTextInputFocus() && runtime.focus().focused() == input, "native click did not focus text input");
        String before = input.getValue(); key(screen, '\0', Keyboard.KEY_END);
        require(input.getEditBox().getCursorPosition() == before.length(), "native End did not move caret");
        key(screen, 'q', Keyboard.KEY_Q);
        require(input.getValue().equals(before + "q"), "native key event did not edit focused text");
        String oldClipboard = GuiScreen.getClipboardString();
        try { GuiScreen.setClipboardString("speaker-1122-clipboard"); require("speaker-1122-clipboard".equals(GuiScreen.getClipboardString()), "native clipboard roundtrip failed"); }
        finally { GuiScreen.setClipboardString(oldClipboard); }
        key(screen, '\t', Keyboard.KEY_TAB);
        require(runtime.focus().focused() != input, "native Tab did not transfer focus");
        ButtonWidget settings = findButton(runtime.root(), net.minecraft.client.resources.I18n.format("gui.simplyspeakers.player.settings"));
        require(settings != null, "native settings navigation button missing"); click(screen, settings);
        runtime.flushFrameTasks(); runtime.preRender(0, 0);
        require(findKey(runtime.root(), "settings.content") != null, "navigation did not mount settings view");
        UIComponent oldRoot = runtime.root();
        screen.setWorldAndResolution(client, 420, 300);
        require(screen.uiRuntime() != runtime && oldRoot.lifecycleState() == com.nstut.openui.component.LifecycleState.UNMOUNTED && runtime.focus().focused() == null,
                "native resize retained old UI ownership");
        UiRuntime replacement = screen.uiRuntime(); UIComponent replacementRoot = replacement.root();
        key(screen, (char)27, Keyboard.KEY_ESCAPE);
        require(client.currentScreen != screen && screen.uiRuntime() == null && replacementRoot.lifecycleState() == com.nstut.openui.component.LifecycleState.UNMOUNTED,
                "native Escape close retained UI ownership");
        uiDone = true;
        System.out.println("SIMPLYSPEAKERS_1122_UI_PASS focus=true text=true clipboardRoundtrip=true tab=true navigation=true resize=true close=true");
        return true;
    }
    private static boolean verifyPlacedModel(Minecraft client, SpeakerBlockEntity tile) throws Exception {
        if (modelDone) return true;
        if (client.currentScreen != null) return false;
        net.minecraft.block.state.IBlockState state = client.world.getBlockState(tile.getPos());
        net.minecraft.client.renderer.BlockRendererDispatcher renderer = client.getBlockRendererDispatcher();
        int quads = validatePlacedModel(renderer.getModelForState(state),
                renderer.getBlockModelShapes().getModelManager().getMissingModel(),
                client.getTextureMapBlocks().getMissingSprite(), state);
        // Orient only the isolated fixture's camera, then allow genuine rendered
        // frames before capturing. This does not fabricate framebuffer evidence.
        double dx = tile.getPos().getX() + .5 - client.player.posX;
        double dy = tile.getPos().getY() + .5 - client.player.posY - client.player.getEyeHeight();
        double dz = tile.getPos().getZ() + .5 - client.player.posZ;
        client.player.rotationYaw = client.player.prevRotationYaw = (float)Math.toDegrees(Math.atan2(dz, dx)) - 90;
        client.player.rotationPitch = client.player.prevRotationPitch = (float)-Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        if (++modelTicks < 20) return false;
        java.io.File file = new java.io.File(client.gameDir, "screenshots/placed-speaker-1122.png");
        require(!file.exists(), "stale placed-speaker screenshot");
        net.minecraft.util.ScreenShotHelper.saveScreenshot(client.gameDir, file.getName(), client.displayWidth,
                client.displayHeight, client.getFramebuffer());
        java.awt.image.BufferedImage captured = javax.imageio.ImageIO.read(file);
        require(captured != null && captured.getWidth() > 0 && captured.getHeight() > 0, "placed-speaker screenshot was not written");
        modelDone = true;
        System.out.println("SIMPLYSPEAKERS_1122_PLACED_MODEL_PASS missingModel=false missingSprite=false quads=" + quads
                + " screenshot=placed-speaker-1122.png");
        return true;
    }
    static int validatePlacedModel(net.minecraft.client.renderer.block.model.IBakedModel model,
            net.minecraft.client.renderer.block.model.IBakedModel missing,
            net.minecraft.client.renderer.texture.TextureAtlasSprite missingSprite,
            net.minecraft.block.state.IBlockState state) {
        require(model != null && model != missing, "placed speaker resolved missing model");
        int count = 0;
        net.minecraft.util.EnumFacing[] sides = net.minecraft.util.EnumFacing.values();
        for (int side = -1; side < sides.length; side++) {
            for (net.minecraft.client.renderer.block.model.BakedQuad quad : model.getQuads(state, side < 0 ? null : sides[side], 0)) {
                net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = quad.getSprite();
                require(sprite != null && sprite != missingSprite && sprite.getIconName().startsWith("simplyspeakers:"),
                        "placed speaker quad resolved missing or unexpected texture");
                count++;
            }
        }
        require(count > 0, "placed speaker baked model has no quads");
        return count;
    }
    private static void click(GuiScreen target, UIComponent node) throws Exception {
        Method click = UiScreen.class.getDeclaredMethod("mouseClicked", int.class, int.class, int.class); click.setAccessible(true);
        click.invoke(target, node.getX() + node.getWidth() / 2, node.getY() + node.getHeight() / 2, 0);
        Method release = UiScreen.class.getDeclaredMethod("mouseReleased", int.class, int.class, int.class); release.setAccessible(true);
        release.invoke(target, node.getX() + node.getWidth() / 2, node.getY() + node.getHeight() / 2, 0);
    }
    private static void key(GuiScreen target, char character, int scan) throws Exception {
        Method method = SimplySpeakersUiScreen.class.getDeclaredMethod("keyTyped", char.class, int.class); method.setAccessible(true);
        method.invoke(target, character, scan);
    }
    private static TextField findText(UIComponent node) {
        if (node instanceof TextField && node.isVisible()) return (TextField) node;
        for (UIComponent child : node.children()) { TextField found = findText(child); if (found != null) return found; }
        return null;
    }
    private static UIComponent findKey(UIComponent node, String key) {
        if (key.equals(node.key())) return node;
        for (UIComponent child : node.children()) { UIComponent found = findKey(child, key); if (found != null) return found; }
        return null;
    }
    private static ButtonWidget findButton(UIComponent node, String label) throws Exception {
        if (node instanceof ButtonWidget) {
            Field field = ButtonWidget.class.getDeclaredField("labelSupplier"); field.setAccessible(true);
            if (label.equals(((ITextComponent) ((Supplier<?>) field.get(node)).get()).getUnformattedText())) return (ButtonWidget) node;
        }
        for (UIComponent child : node.children()) { ButtonWidget found = findButton(child, label); if (found != null) return found; }
        return null;
    }
    private static Object resource() throws Exception {
        Field field = ClientAudioPlayer.class.getDeclaredField("networkResources"); field.setAccessible(true);
        return ((Map<?, ?>) field.get(null)).get(KEY);
    }
    private static int sourceId(Object resource) throws Exception {
        Field field = resource.getClass().getDeclaredField("sourceID"); field.setAccessible(true); return field.getInt(resource);
    }
    private static boolean cleaned(Object resource) throws Exception {
        if (resource == null) return true;
        Field workerField = resource.getClass().getDeclaredField("streamingThread"); workerField.setAccessible(true);
        Thread worker = (Thread) workerField.get(resource);
        Field cleanup = resource.getClass().getDeclaredField("cleanupComplete"); cleanup.setAccessible(true);
        return (worker == null || !worker.isAlive()) && cleanup.getBoolean(resource);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL " + message);
    }
}
