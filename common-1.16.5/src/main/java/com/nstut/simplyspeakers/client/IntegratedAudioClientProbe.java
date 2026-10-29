package com.nstut.simplyspeakers.client;

import com.nstut.openui.api.UIComponent;
import com.nstut.openui.controls.Tabs;
import com.nstut.openui.state.Signal;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.audio.AudioContextGate;
import com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity;
import com.nstut.simplyspeakers.client.screens.SpeakerScreen;
import com.nstut.simplyspeakers.network.PacketRegistries;
import com.nstut.simplyspeakers.network.TransportControlPacketC2S;
import com.nstut.simplyspeakers.testing.IntegratedAudioServerProbe;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC10;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Uses only a normally entered integrated world and real production transport packets.
 * Client registry, decoder, OpenAL and OpenUI observations are independent of server objects.
 * All markers are integrated-only: OpenAL progress is NOT proof of audible physical output.
 */
public final class IntegratedAudioClientProbe {
    private static final String KEY = IntegratedAudioServerProbe.KEY;
    private static final String[] PHASES = {
            "playing_reload", "paused_reload", "resumed", "seeked", "restarted", "stopped", "ui"
    };
    private static final byte[] ACTIONS = {
            TransportControlPacketC2S.ACTION_PLAY, TransportControlPacketC2S.ACTION_PAUSE,
            TransportControlPacketC2S.ACTION_PLAY, TransportControlPacketC2S.ACTION_SEEK,
            TransportControlPacketC2S.ACTION_RESTART, TransportControlPacketC2S.ACTION_STOP
    };
    private static Object connection, lastResource, progressResource, reloadResource;
    private static BlockPos position;
    private static String audioId;
    private static int phase, phaseTicks, progressTicks, reloadTicks, warmupTicks;
    private static long startedNanos, reloadStartedNanos, reloadOldEpoch, reloadOldContext;
    private static float firstSampleOffset, reloadOldOffset;
    private static boolean sent, reloadStarted, reloadPassed, finished;
    private static java.util.concurrent.CompletableFuture<Void> reloadFuture;
    private static SpeakerScreen screen;
    private static int uiStep, uiTicks, originalWidth, originalHeight, originalScale, wideWidth;
    private static boolean viewportChanged;

    private IntegratedAudioClientProbe() { }

    public static void tick(Minecraft client) {
        if (!IntegratedAudioServerProbe.enabled() || finished) return;
        // The separate vanilla-menu driver owns world entry. Never create or connect here.
        if (client.player == null || client.level == null || client.getConnection() == null) {
            require(connection == null, "integrated world disconnected before acceptance completed");
            return;
        }
        try {
            require(client.getSingleplayerServer() != null && client.isLocalServer()
                            && !client.getSingleplayerServer().isPublished(),
                    "audio probe requires an existing, unpublished IntegratedServer");
            require("SSIClient".equals(client.player.getName().getString()), "unexpected integrated participant");
            if (connection == null) {
                require("1.8".equals(System.getProperty("java.specification.version")), "native acceptance must run on Java 8");
                System.out.println("SIMPLYSPEAKERS_INTEGRATED_RUNTIME_PASS javaMajor=8");
                connection = client.getConnection(); startedNanos = System.nanoTime();
            }
            require(connection == client.getConnection(), "connection changed during integrated acceptance");
            require(System.nanoTime() - startedNanos < TimeUnit.SECONDS.toNanos(150),
                    "client timeout at " + PHASES[phase]);
            if (client.getOverlay() != null) return;
            if (position == null && !findFixture(client)) return;
            SpeakerState state = ClientSpeakerRegistry.getState(KEY);
            if (state == null || !audioId.equals(state.getAudioId())) return;
            if (phase == 6) {
                if (verifyUi(client)) acknowledge(client);
                return;
            }
            if (!sent) {
                // This is the real mod C2S codec/handler path, never a direct service call.
                PacketRegistries.CHANNEL.sendToServer(new TransportControlPacketC2S(position, ACTIONS[phase], phase == 3 ? 4f : 0f));
                sent = true;
                return;
            }
            phaseTicks++;
            if (!ClientAudioPlayer.contextAvailable()) return;
            ClientAudioPlayer.VerificationSnapshot snapshot = ClientAudioPlayer.verificationSnapshot(KEY);
            boolean silent = phase == 1 || phase == 5;
            if (phaseTicks % 100 == 0)
                System.out.println("SIMPLYSPEAKERS_INTEGRATED_WAIT " + PHASES[phase] + " " + snapshot);
            if (silent) {
                if (snapshot.sources() != 0 || snapshot.emitters() != 0 || resource() != null || !cleaned(lastResource)) return;
                if (phase == 1 ? !state.isPlaying() || !state.isPaused() : state.isPlaying() || state.isPaused()) return;
            } else {
                if (!state.isPlaying() || state.isPaused() || !playing(snapshot)) return;
                Object current = resource();
                if (current == null) return;
                if (phase == 3 && (snapshot.offset() < 4 || snapshot.offset() >= 5)) return;
                if (phase == 4 && snapshot.offset() >= 1) return;
                if (!sampleProgress(current)) return;
                lastResource = current;
            }
            if ((phase == 0 || phase == 1) && !verifyReload(client, phase == 0)) return;
            System.out.println("SIMPLYSPEAKERS_INTEGRATED_CLIENT_PHASE_PASS " + PHASES[phase]
                    + " sources=" + snapshot.sources() + " emitters=" + snapshot.emitters()
                    + " decodedBytes=" + snapshot.decodedBytes() + " offset=" + snapshot.offset()
                    + " productionS2C=true backendOnly=true audibleOutputVerified=false");
            acknowledge(client);
        } catch (AudioContextGate.StaleContextException pendingReload) {
            // A reload may invalidate the read-only sample between adjacent client ticks.
        } catch (Exception error) {
            restoreViewport(client);
            throw new IllegalStateException("SIMPLYSPEAKERS_INTEGRATED_FAIL client phase " + phase, error);
        }
    }

    private static boolean findFixture(Minecraft client) {
        BlockPos center = client.player.blockPosition();
        for (int x = -4; x <= 4; x++) for (int y = -2; y <= 4; y++) for (int z = -4; z <= 4; z++) {
            BlockPos candidate = center.offset(x, y, z);
            if (!(client.level.getBlockEntity(candidate) instanceof SpeakerBlockEntity)) continue;
            SpeakerBlockEntity speaker = (SpeakerBlockEntity) client.level.getBlockEntity(candidate);
            if (!IntegratedAudioServerProbe.ID.equals(speaker.getSpeakerId())) continue;
            SpeakerState state = ClientSpeakerRegistry.getState(KEY);
            if (state == null || !state.hasAudio()) return false;
            position = candidate; audioId = state.getAudioId();
            System.out.println("SIMPLYSPEAKERS_INTEGRATED_CLIENT_READY participant=" + client.player.getUUID()
                    + " audio=" + audioId + " productionBlockSync=true");
            return true;
        }
        return false;
    }

    private static boolean playing(ClientAudioPlayer.VerificationSnapshot snapshot) {
        return snapshot.playing() && snapshot.decodedBytes() > 0 && snapshot.sources() == 1 && snapshot.emitters() == 1;
    }

    /** Require actual sample movement on the same resource, not merely AL_PLAYING. */
    private static boolean sampleProgress(Object current) throws Exception {
        final int source = ((Number) field(current, "sourceID")).intValue();
        final long epoch = ((Number) field(current, "contextEpoch")).longValue();
        final AtomicBoolean stopping = (AtomicBoolean) field(current, "stopFlag");
        float sample = gate().call(epoch, () -> {
            if (stopping.get()) throw new AudioContextGate.StaleContextException();
            require(AL10.alIsSource(source) && AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE) == AL10.AL_PLAYING,
                    "decoded resource has no playing native OpenAL source");
            return AL10.alGetSourcef(source, AL11.AL_SEC_OFFSET);
        });
        if (progressResource != current) {
            progressResource = current; progressTicks = 0; firstSampleOffset = sample;
            return false;
        }
        return ++progressTicks >= 10 && Math.abs(sample - firstSampleOffset) >= 0.01f;
    }

    /** Real vanilla sound reload, sharing production epoch protection and decoder state. */
    private static boolean verifyReload(Minecraft client, boolean playing) throws Exception {
        if (reloadPassed) return true;
        if (!reloadStarted) {
            if (playing && ++warmupTicks < 20) return false;
            reloadOldEpoch = ClientAudioPlayer.contextEpoch();
            reloadOldContext = ALC10.alcGetCurrentContext();
            require(reloadOldContext != 0, "no actual OpenAL context before reload");
            reloadResource = resource();
            reloadOldOffset = ClientAudioPlayer.verificationSnapshot(KEY).offset();
            reloadStartedNanos = System.nanoTime(); reloadStarted = true;
            progressResource = null; progressTicks = 0;
            reloadFuture = client.reloadResourcePacks();
            return false;
        }
        require(System.nanoTime() - reloadStartedNanos < TimeUnit.SECONDS.toNanos(30), "sound reload recovery timed out");
        if (!reloadFuture.isDone()) return false;
        reloadFuture.join();
        long epoch = ClientAudioPlayer.contextEpoch();
        if (!ClientAudioPlayer.contextAvailable() || epoch <= reloadOldEpoch || ALC10.alcGetCurrentContext() == 0) return false;
        ClientAudioPlayer.VerificationSnapshot snapshot = ClientAudioPlayer.verificationSnapshot(KEY);
        Object current = resource();
        if (phaseTicks % 100 == 0)
            System.out.println("SIMPLYSPEAKERS_INTEGRATED_RELOAD_WAIT playing=" + playing
                    + " epoch=" + reloadOldEpoch + "->" + epoch + " offset=" + reloadOldOffset + "->" + snapshot.offset()
                    + " newResource=" + (current != reloadResource) + " oldWorkerCleaned=" + cleaned(reloadResource));
        if (playing) {
            if (!playing(snapshot) || current == null || current == reloadResource || !cleaned(reloadResource)) return false;
            if (((Number) field(current, "contextEpoch")).longValue() != epoch || snapshot.offset() <= reloadOldOffset + .25f) return false;
            if (!sampleProgress(current)) return false;
            lastResource = current;
        } else {
            require(snapshot.sources() == 0 && snapshot.emitters() == 0 && current == null,
                    "paused playback resurrected after sound reload");
            if (++reloadTicks < 20) return false;
        }
        boolean staleRejected = false;
        try { gate().call(reloadOldEpoch, () -> Boolean.TRUE); }
        catch (AudioContextGate.StaleContextException expected) { staleRejected = true; }
        require(staleRejected, "old-context epoch was not rejected");
        System.out.println("SIMPLYSPEAKERS_INTEGRATED_AUDIO_RELOAD_PASS " + (playing ? "playing" : "paused")
                + " epoch=" + reloadOldEpoch + "->" + epoch + " context=" + reloadOldContext + "->" + ALC10.alcGetCurrentContext()
                + " decodedBytes=" + snapshot.decodedBytes() + " oldWorkerStopped=" + cleaned(reloadResource)
                + " staleEpochRejected=true backendOnly=true audibleOutputVerified=false");
        reloadPassed = true;
        return true;
    }

    private static Object resource() throws Exception {
        return ((Map<?, ?>) field(null, ClientAudioPlayer.class, "networkResources")).get(KEY);
    }

    private static AudioContextGate gate() throws Exception {
        return (AudioContextGate) field(null, ClientAudioPlayer.class, "audioContext");
    }

    private static boolean cleaned(Object resource) throws Exception {
        if (resource == null) return true;
        Thread worker = (Thread) field(resource, "streamingThread");
        return Boolean.TRUE.equals(field(resource, "cleanupComplete")) && (worker == null || !worker.isAlive());
    }

    /** Read diagnostic fields only. Unlike a preview, this never seeds UI or server state. */
    private static Object field(Object object, String name) throws Exception {
        return field(object, object.getClass(), name);
    }

    private static Object field(Object object, Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name); field.setAccessible(true); return field.get(object);
    }

    private static void acknowledge(Minecraft client) {
        client.getConnection().send(new ServerboundChatPacket("/" + IntegratedAudioServerProbe.COMMAND
                + " " + PHASES[phase] + " " + audioId));
        phase++; phaseTicks = 0; sent = false; progressResource = null; progressTicks = 0;
        reloadStarted = false; reloadPassed = false; reloadTicks = 0; warmupTicks = 0;
        if (phase == PHASES.length) {
            finished = true;
            System.out.println("SIMPLYSPEAKERS_INTEGRATED_CLIENT_PASS phases=" + PHASES.length
                    + " participants=1 productionTransport=true nativeOpenUI=true backendOnly=true audibleOutputVerified=false dedicated=false twoClient=false");
        }
    }

    /** Native OpenUI pointer/key routing, real policy packet, close/reopen and real GLFW resize. */
    private static boolean verifyUi(Minecraft client) throws Exception {
        if (uiStep == 0) {
            originalWidth = client.getWindow().getScreenWidth(); originalHeight = client.getWindow().getScreenHeight();
            originalScale = client.options.guiScale; viewportChanged = true;
            client.options.guiScale = 1;
            resize(client, 854, 640);
            screen = new SpeakerScreen(position); client.setScreen(screen);
            uiStep++; uiTicks = 0; return false;
        }
        if (uiStep == 7) {
            SpeakerState state = ClientSpeakerRegistry.getState(KEY);
            if (!IntegratedAudioServerProbe.SETTINGS_NAME.equals(state.getNetworkName())) return false;
            screen = new SpeakerScreen(position); client.setScreen(screen);
            uiStep++; uiTicks = 0; return false;
        }
        require(client.screen == screen, "OpenUI screen unexpectedly closed or replaced");
        screen.uiRuntime().flushFrameTasks();
        screen.uiRuntime().mouseScrolled(0, 0, 0);
        if (++uiTicks < 5) return false;
        uiTicks = 0;
        if (uiStep >= 1 && uiStep <= 4) {
            int tab = uiStep == 4 ? 0 : uiStep;
            navigate(client, tab);
            require(tabName().equals(new String[]{"AUDIO", "PLAYLIST", "QUEUE", "SETTINGS"}[tab]),
                    "native OpenUI tab input did not select requested view");
            if (uiStep == 4) { wideWidth = screen.width; resize(client, 640, 480); }
            uiStep++; return false;
        }
        if (uiStep == 5) {
            require(screen.width < wideWidth, "native window resize did not relayout OpenUI");
            navigate(client, 3);
            require("SETTINGS".equals(tabName()), "settings tab lost after resize");
            uiStep++; return false;
        }
        if (uiStep == 6) {
            UIComponent input = keyed("settings.network_name");
            UIComponent save = keyed("settings.save_network_name");
            require(input != null && save != null, "real settings controls missing");
            if (!reveal(input)) return false;
            click(input, input.getX() + input.getWidth() / 2.0);
            // The newly created fixture has an empty name, so no synthetic Signal.set is needed.
            require("".equals(((Signal<?>) field(screen, "networkName")).get()), "unexpected initial settings value");
            for (char letter : IntegratedAudioServerProbe.SETTINGS_NAME.toCharArray()) screen.charTyped(letter, 0);
            require(IntegratedAudioServerProbe.SETTINGS_NAME.equals(((Signal<?>) field(screen, "networkName")).get()),
                    "native keyboard input did not reach settings field");
            click(save, save.getX() + save.getWidth() / 2.0);
            closeNative(client);
            uiStep++; return false;
        }
        if (uiStep == 8) {
            require(IntegratedAudioServerProbe.SETTINGS_NAME.equals(((Signal<?>) field(screen, "networkName")).get()),
                    "server-backed settings did not survive close/reopen");
            navigate(client, 3);
            require("SETTINGS".equals(tabName()), "reopened settings tab failed");
            closeNative(client);
            restoreViewport(client);
            System.out.println("SIMPLYSPEAKERS_INTEGRATED_UI_PASS tabs=4 nativePointer=true nativeKeyboard=true"
                    + " productionSettingsRoundTrip=true resize=true closeReopen=true");
            return true;
        }
        throw new IllegalStateException("unexpected UI step " + uiStep);
    }

    private static String tabName() throws Exception { return ((Signal<?>) field(screen, "tab")).get().toString(); }

    private static void navigate(Minecraft client, int index) throws Exception {
        UIComponent tabs = (UIComponent) helper("findTabs", new Class<?>[]{UIComponent.class}, screen.uiRuntime().root());
        if (tabs instanceof Tabs<?>) {
            click(tabs, tabs.getX() + (index + .5) * tabs.getWidth() / 4.0);
        } else {
            String label = new TranslatableComponent("gui.simplyspeakers.player."
                    + new String[]{"library", "playlist", "queue", "settings"}[index]).getString();
            UIComponent button = (UIComponent) helper("findButton", new Class<?>[]{UIComponent.class, String.class},
                    screen.uiRuntime().root(), label);
            require(button != null, "tab control missing: " + label);
            click(button, button.getX() + button.getWidth() / 2.0);
        }
        screen.uiRuntime().flushFrameTasks();
    }

    private static UIComponent keyed(String key) throws Exception {
        return (UIComponent) helper("keyed", new Class<?>[]{UIComponent.class, String.class}, screen.uiRuntime().root(), key);
    }

    private static boolean reveal(UIComponent target) throws Exception {
        UIComponent viewport = keyed("settings.content");
        double center = target.getY() + target.getHeight() / 2.0;
        if (center >= viewport.getY() && center < viewport.getY() + viewport.getHeight()) return true;
        double viewportCenter = viewport.getY() + viewport.getHeight() / 2.0;
        screen.mouseScrolled(viewport.getX() + viewport.getWidth() - 1, viewportCenter, (viewportCenter - center) / 24.0);
        return false;
    }

    private static void click(UIComponent target, double x) {
        double y = target.getY() + target.getHeight() / 2.0;
        require(target.getWidth() > 0 && target.getHeight() > 0 && target.hasVisibleAreaWithinAncestorClips()
                        && x >= 0 && x < screen.width && y >= 0 && y < screen.height,
                "native pointer target outside visible viewport");
        screen.uiRuntime().preRender((int) x, (int) y);
        require(screen.mouseClicked(x, y, 0), "native OpenUI click was not handled");
        screen.mouseReleased(x, y, 0);
    }

    private static void closeNative(Minecraft client) {
        screen.keyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);
        require(client.screen == null, "native Escape did not close OpenUI screen");
    }

    /** Only stateless helpers from the maintained UI probe are reused; its fixture never runs. */
    private static Object helper(String name, Class<?>[] types, Object... arguments) throws Exception {
        Method method = SpeakerUiPreviewProbe.class.getDeclaredMethod(name, types); method.setAccessible(true);
        return method.invoke(null, arguments);
    }

    private static void resize(Minecraft client, int width, int height) throws Exception {
        long handle = ((Number) helper("windowHandle", new Class<?>[]{Minecraft.class}, client)).longValue();
        GLFW.glfwSetWindowSize(handle, width, height);
        helper("resizeGui", new Class<?>[]{Minecraft.class}, client);
    }

    private static void restoreViewport(Minecraft client) {
        if (!viewportChanged) return;
        viewportChanged = false;
        try { client.options.guiScale = originalScale; resize(client, originalWidth, originalHeight); }
        catch (Exception error) { throw new IllegalStateException("SIMPLYSPEAKERS_INTEGRATED_FAIL restore viewport", error); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException("SIMPLYSPEAKERS_INTEGRATED_FAIL " + message);
    }
}
