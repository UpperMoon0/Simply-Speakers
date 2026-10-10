package com.nstut.simplyspeakers.client;

import com.nstut.simplyspeakers.testing.LivePlaybackServerProbe;
import net.minecraft.client.Minecraft;

/** Client evidence comes from actual decoder/OpenAL resources, never a synthetic packet. */
final class LivePlaybackClientProbe {
    private static final String KEY = "net___simplyspeakers_verify";
    private static final String[] PHASES = {"started", "paused", "resumed", "seeked", "restarted", "stopped", "redstone",
            "portable_started", "portable_moved", "portable_paused", "portable_resumed",
            "portable_stopped", "portable_restarted", "portable_removed"};
    private static final String[] OBSERVER_PHASES = {"observer_started", "observer_farther", "observer_out_of_range",
            "observer_reentered", "observer_paused", "observer_resumed", "observer_stopped", "observer_restarted", "observer_removed"};
    private static int phase, ticks, observerPhase, observerStableTicks;
    private static float observerInitialGain, observerInitialOffset;
    private static int settingIndex;
    private static boolean settingSent;
    private static int previewTicks;
    private static float previewYaw;
    private static boolean speakerSettingsSent, speakerSettingsDone;
    private LivePlaybackClientProbe() {}
    static void tick(Minecraft client) {
        if (!LivePlaybackServerProbe.enabled()) return;
        if ("observer".equals(System.getProperty("simplyspeakers.livePlaybackRole", "carrier"))) {
            verifyObserverPlayback(client); return;
        }
        if (phase == PHASES.length) return;
        if (++ticks > 2400) throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL client phase " + phase);
        if (phase >= 7) { verifyPortablePlayback(client); return; }
        var snapshot = ClientAudioPlayer.verificationSnapshot(KEY);
        if(ticks%100==0) System.out.println("SIMPLYSPEAKERS_CLIENT_PHASE_WAIT "+PHASES[phase]+" "+snapshot);
        boolean observed = switch (phase) {
            case 1, 5 -> snapshot.sources() == 0 && snapshot.emitters() == 0;
            case 3 -> snapshot.playing() && snapshot.decodedBytes() > 0 && Math.abs(snapshot.offset() - 4) < 0.01f;
            case 4, 6 -> snapshot.playing() && snapshot.decodedBytes() > 0 && snapshot.offset() < 1;
            default -> snapshot.playing() && snapshot.decodedBytes() > 0;
        };
        if (!observed) return;
        if (phase != 1 && phase != 5 && (snapshot.sources() != 1 || snapshot.emitters() != 2)) return;
        if (phase == 0) {
            if (!verifyContinuousDrag(client)) return;
            verifyGuide(client);
            DirectionalPreview.show(client.player.blockPosition().offset(2,1,0),16,1,1,90,.9);
            previewYaw=client.player.getYRot();
            client.player.setYRot(-90);
            client.player.setXRot(25);
        }
        if (phase == 1) {
            if(++previewTicks<80)return;
            if(DirectionalPreview.emittedSamples()==0)throw new IllegalStateException("World preview did not emit particles");
            try {
                var capture=SpeakerUiPreviewProbe.class.getDeclaredMethod("capture",Minecraft.class,String.class);
                capture.setAccessible(true);capture.invoke(null,client,"directional-preview-world.png");
                client.player.setXRot(0);
                client.player.setYRot(previewYaw);
            }catch(Exception error){throw new IllegalStateException("Preview capture failed",error);}
            System.out.println("SIMPLYSPEAKERS_DIRECTIONAL_PREVIEW_PASS samples="+DirectionalPreview.emittedSamples());
        }
        if (phase == 5 && (!verifyControllerReopen(client) || !verifySpeakerSettingsReopen(client))) return;
        System.out.println("SIMPLYSPEAKERS_CLIENT_PHASE_PASS " + PHASES[phase]
                + " sources=" + snapshot.sources() + " emitters=" + snapshot.emitters()
                + " decodedBytes=" + snapshot.decodedBytes() + " offset=" + snapshot.offset());
        client.getConnection().sendCommand("simplyspeakers_verify " + PHASES[phase]);
        phase++;
        if (phase == PHASES.length) System.out.println("SIMPLYSPEAKERS_CLIENT_PLAYBACK_PASS");
    }
    private static String portableAudioKey;
    private static Object portableResource;
    private static int portableSource;
    private static net.minecraft.world.phys.Vec3 portableInitialPosition;
    private static net.minecraft.core.BlockPos portableToken;

    /** Requires a decoded OpenAL stream, its actual source position and completed deletion.
     * Pose-cache assertions alone are deliberately insufficient for this live evidence. */
    private static void verifyPortablePlayback(Minecraft client) {
        try {
            if (portableAudioKey == null) {
                if (ClientPortableSpeakers.tokens().size() != 1) return;
                portableToken = ClientPortableSpeakers.tokens().iterator().next();
                portableAudioKey = ClientAudioPlayer.resolveNetworkKey(portableToken);
            }
            var snapshot = ClientAudioPlayer.verificationSnapshot(portableAudioKey);
            boolean silent = phase == 9 || phase == 11 || phase == 13;
            if (ticks % 100 == 0) System.out.println("SIMPLYSPEAKERS_CLIENT_PHASE_WAIT " + PHASES[phase] + " " + snapshot);
            if (silent) {
                if (snapshot.sources() != 0 || snapshot.emitters() != 0 || ClientPortableSpeakers.contains(portableToken)
                        || org.lwjgl.openal.AL10.alIsSource(portableSource)) return;
            } else {
                if (!snapshot.playing() || snapshot.decodedBytes() == 0 || snapshot.sources() != 1 || snapshot.emitters() != 1) return;
                var resourcesField = ClientAudioPlayer.class.getDeclaredField("networkResources"); resourcesField.setAccessible(true);
                Object resource = ((java.util.Map<?, ?>) resourcesField.get(null)).get(portableAudioKey);
                if (resource == null) return;
                var sourceField = resource.getClass().getDeclaredField("sourceID"); sourceField.setAccessible(true);
                int source = sourceField.getInt(resource);
                if (ClientPortableSpeakers.tokens().size() != 1) return;
                var token = ClientPortableSpeakers.tokens().iterator().next();
                var resolved = ClientPortableSpeakers.resolvePosition(token);
                float[] raw = new float[3];
                org.lwjgl.openal.AL10.alGetSourcefv(source, org.lwjgl.openal.AL10.AL_POSITION, raw);
                var actual = new net.minecraft.world.phys.Vec3(raw[0], raw[1], raw[2]);
                if (resolved == null || actual.distanceToSqr(resolved) > 1.0) return;
                if (phase == 8) {
                    if (actual.distanceToSqr(portableInitialPosition) < 36) return;
                    if (resource != portableResource || source != portableSource)
                        throw new IllegalStateException("Moving the inventory holder restarted the portable decoder");
                }
                portableResource = resource; portableSource = source; portableToken = token;
                if (phase == 7) portableInitialPosition = actual;
            }
            System.out.println("SIMPLYSPEAKERS_CLIENT_PHASE_PASS " + PHASES[phase]
                    + " sources=" + snapshot.sources() + " emitters=" + snapshot.emitters()
                    + " decodedBytes=" + snapshot.decodedBytes() + " openAL=" + (silent ? "deleted" : "playing"));
            client.getConnection().sendCommand("simplyspeakers_verify " + PHASES[phase]);
            phase++;
            if (phase == PHASES.length) System.out.println("SIMPLYSPEAKERS_CLIENT_PLAYBACK_PASS");
        } catch (Exception error) { throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL portable playback", error); }
    }

    /** Independent real listener: no UI work and no carrier acknowledgement can
     * substitute for its decoded audio, OpenAL gain, range cleanup or re-entry. */
    private static void verifyObserverPlayback(Minecraft client) {
        if (observerPhase == OBSERVER_PHASES.length || client.player == null || client.level == null) return;
        if (++ticks > 2400) throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL observer phase " + observerPhase);
        try {
            if (portableAudioKey == null) {
                if (ClientPortableSpeakers.tokens().size() != 1) return;
                portableToken = ClientPortableSpeakers.tokens().iterator().next();
                portableAudioKey = ClientAudioPlayer.resolveNetworkKey(portableToken);
            }
            var snapshot = ClientAudioPlayer.verificationSnapshot(portableAudioKey);
            String observed = OBSERVER_PHASES[observerPhase];
            if (ticks % 100 == 0) System.out.println("SIMPLYSPEAKERS_OBSERVER_PHASE_WAIT " + observed + " " + snapshot);
            boolean silent = observerPhase == 2 || observerPhase == 4 || observerPhase == 6 || observerPhase == 8;
            float gain = 0; double distance = 0;
            if (silent) {
                if (snapshot.sources() != 0 || snapshot.emitters() != 0 || ClientPortableSpeakers.contains(portableToken)
                        || org.lwjgl.openal.AL10.alIsSource(portableSource)) return;
            } else {
                if (!snapshot.playing() || snapshot.decodedBytes() == 0 || snapshot.sources() != 1 || snapshot.emitters() != 1) return;
                var resourcesField = ClientAudioPlayer.class.getDeclaredField("networkResources"); resourcesField.setAccessible(true);
                Object resource = ((java.util.Map<?, ?>) resourcesField.get(null)).get(portableAudioKey);
                if (resource == null || ClientPortableSpeakers.tokens().size() != 1) return;
                var sourceField = resource.getClass().getDeclaredField("sourceID"); sourceField.setAccessible(true);
                int source = sourceField.getInt(resource);
                var resolved = ClientPortableSpeakers.resolvePosition(portableToken);
                float[] raw = new float[3];
                org.lwjgl.openal.AL10.alGetSourcefv(source, org.lwjgl.openal.AL10.AL_POSITION, raw);
                var actual = new net.minecraft.world.phys.Vec3(raw[0], raw[1], raw[2]);
                gain = org.lwjgl.openal.AL10.alGetSourcef(source, org.lwjgl.openal.AL10.AL_GAIN);
                if (resolved == null || actual.distanceToSqr(resolved) > 1 || !(gain > 0)) return;
                distance = actual.distanceTo(client.player.position());
                if (observerPhase == 0) {
                    if (distance < 7 || distance > 10 || ++observerStableTicks < 5) return;
                    observerInitialGain = gain; observerInitialOffset = snapshot.offset(); portableInitialPosition = actual;
                } else if (observerPhase == 1) {
                    if (distance < 30 || distance > 34 || actual.distanceToSqr(portableInitialPosition) < 400
                            || gain >= observerInitialGain * .75f || ++observerStableTicks < 5) return;
                    if (resource != portableResource || source != portableSource)
                        throw new IllegalStateException("Observer movement replaced the shared portable decoder");
                } else if (observerPhase == 3) {
                    if (distance < 11 || distance > 14 || snapshot.offset() <= observerInitialOffset + .5f) return;
                    if (resource == portableResource)
                        throw new IllegalStateException("Re-entry reused a resource which should have been deleted at range exit");
                } else if (observerPhase == 7 && snapshot.offset() >= 1) return;
                portableResource = resource; portableSource = source;
            }
            System.out.println("SIMPLYSPEAKERS_OBSERVER_PHASE_PASS " + observed
                    + " sources=" + snapshot.sources() + " emitters=" + snapshot.emitters()
                    + " decodedBytes=" + snapshot.decodedBytes() + " offset=" + snapshot.offset()
                    + " gain=" + gain + " distance=" + distance + " openAL=" + (silent ? "deleted" : "playing"));
            client.getConnection().sendCommand("simplyspeakers_verify " + observed);
            observerPhase++; observerStableTicks = 0;
            if (observerPhase == OBSERVER_PHASES.length) System.out.println("SIMPLYSPEAKERS_OBSERVER_PLAYBACK_PASS");
        } catch (Exception error) { throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL observer playback", error); }
    }

    private static int dragTicks, dragPolicyTicks;
    private static Object dragResource;
    private static com.nstut.simplyspeakers.client.screens.SpeakerScreen dragScreen;
    private static com.nstut.openui.api.UIComponent dragSlider;
    private static double dragX, dragY;
    private static boolean dragDone;
    /** Uses native Screen entry points and Minecraft's isDragging gate, not direct runtime injection. */
    private static boolean verifyContinuousDrag(Minecraft client) {
        if (dragDone) return true;
        try {
            var resourcesField=ClientAudioPlayer.class.getDeclaredField("networkResources"); resourcesField.setAccessible(true);
            var resources=(java.util.Map<?,?>)resourcesField.get(null);
            if(dragScreen==null) {
                dragResource=resources.get(KEY);
                dragScreen=new com.nstut.simplyspeakers.client.screens.SpeakerScreen(client.player.blockPosition().offset(2,1,0));
                client.setScreen(dragScreen);
                return false; // Permission/catalog packets arrive asynchronously after opening.
            }
            if(dragSlider==null) {
                dragScreen.uiRuntime().flushFrameTasks();
                dragScreen.uiRuntime().mouseScrolled(0,0,0);
                dragScreen.uiRuntime().preRender(0,0);
                dragSlider=findKey(dragScreen.uiRuntime().root(),"player.volume");
                if(dragSlider==null || dragSlider.getWidth()<=0 || dragSlider.getHeight()<=0) {
                    dragSlider=null;
                    if(++dragPolicyTicks>100)throw new IllegalStateException("Volume slider missing after permission response");
                    return false;
                }
                dragX=dragSlider.getX()+dragSlider.getWidth()*.2;dragY=dragSlider.getY()+dragSlider.getHeight()/2.0;
                dragScreen.uiRuntime().preRender((int)dragX,(int)dragY);
                nativePointer(dragScreen,"mouseClicked",dragX,dragY,0);
                if(!dragScreen.isDragging())throw new IllegalStateException("Native drag gate closed after slider click");
                return false;
            }
            if(resources.get(KEY)!=dragResource || !ClientAudioPlayer.verificationSnapshot(KEY).playing())
                throw new IllegalStateException("Continuous settings drag replaced or stopped active audio");
            if(++dragTicks<=24) {
                double x=dragSlider.getX()+dragSlider.getWidth()*(.2+dragTicks*.025);
                if(!dragScreen.isDragging())throw new IllegalStateException("Native drag gate closed while held");
                nativePointer(dragScreen,"mouseDragged",x,dragY,x-dragX);dragX=x;
                var field=dragScreen.getClass().getDeclaredField("maxVolume");field.setAccessible(true);
                double value=(Double)((com.nstut.openui.state.Signal<?>)field.get(dragScreen)).get();
                if(Math.abs(value-(.2+dragTicks*.025))>.001)throw new IllegalStateException("Slider did not update before release");
                var channel=new ProbeSender(dragScreen);
                channel.sendToServer(com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.directionality(client.player.blockPosition().offset(2,1,0),dragTicks/24f));
                channel.sendToServer(new com.nstut.simplyspeakers.network.UpdateMaxRangePacketC2S(client.player.blockPosition().offset(2,1,0),24+dragTicks));
                return false;
            }
            if(dragTicks<35)return false; // Allow delayed server updates to reach the same decoder.
            var membershipField=ClientAudioPlayer.class.getDeclaredField("membership");membershipField.setAccessible(true);
            var membership=(PlaybackMembership<net.minecraft.core.BlockPos>)membershipField.get(null);
            var applied=membership.getSettings(client.player.blockPosition().offset(2,1,0));
            if(applied==null || Math.abs(applied.maxVolume()-.8f)>.001 || applied.maxRange()!=48)
                throw new IllegalStateException("Playback settings did not reach active source before release: "+applied);
            nativePointer(dragScreen,"mouseReleased",dragX,dragY,0);
            if(dragScreen.isDragging())throw new IllegalStateException("Native drag gate stuck after release");
            client.setScreen(null);dragDone=true;
            System.out.println("SIMPLYSPEAKERS_CONTINUOUS_DRAG_PASS updates=24 sourcePreserved=true beforeRelease=true");
            return true;
        }catch(Exception error){throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL continuous drag",error);}
    }
    private static com.nstut.openui.api.UIComponent findKey(com.nstut.openui.api.UIComponent node,String key) {
        if(key.equals(node.key()))return node;
        for(var child:node.children()){var match=findKey(child,key);if(match!=null)return match;}
        return null;
    }
    private static void nativePointer(Object screen,String name,double x,double y,double dx) throws Exception {
        var method=java.util.Arrays.stream(screen.getClass().getMethods()).filter(m -> m.getName().equals(name)).findFirst().orElseThrow();
        if(method.getParameterTypes()[0]==double.class) {
            if(name.equals("mouseDragged"))method.invoke(screen,x,y,0,dx,0.0);
            else method.invoke(screen,x,y,0);
        } else {
            var infoClass=Class.forName("net.minecraft.client.input.MouseButtonInfo");
            var info=infoClass.getConstructor(int.class,int.class).newInstance(0,0);
            var event=method.getParameterTypes()[0].getConstructor(double.class,double.class,infoClass).newInstance(x,y,info);
            if(name.equals("mouseDragged"))method.invoke(screen,event,dx,0.0);
            else if(name.equals("mouseClicked"))method.invoke(screen,event,false);
            else method.invoke(screen,event);
        }
    }

    private record ProbeSender(com.nstut.simplyspeakers.client.screens.SpeakerScreen screen) {
        void sendToServer(Object packet) {
            try {
                var method=java.util.Arrays.stream(screen.getClass().getDeclaredMethods())
                    .filter(m -> m.getName().equals("sendToServer")).findFirst().orElseThrow();
                method.setAccessible(true);method.invoke(screen,packet);
            } catch(Exception error) { throw new IllegalStateException("Could not send real settings packet",error); }
        }
    }
    private static void verifyGuide(Minecraft client) {
        try {
            Class<?> registryType=Class.forName("vazkii.patchouli.common.book.BookRegistry");
            Object registry=registryType.getField("INSTANCE").get(null);
            var books=(java.util.Map<?,?>)registryType.getField("books").get(registry);
            Object book=books.entrySet().stream().filter(e -> e.getKey().toString().equals("simplyspeakers:guide"))
                    .map(java.util.Map.Entry::getValue).findFirst().orElseThrow();
            Class<?> bookType=book.getClass();
            bookType.getMethod("reloadContents",net.minecraft.world.level.Level.class,boolean.class).invoke(book,client.level,true);
            Object contents=bookType.getMethod("getContents").invoke(book);
            var entries=(java.util.Map<?,?>)contents.getClass().getField("entries").get(contents);
            var categories=(java.util.Map<?,?>)contents.getClass().getField("categories").get(contents);
            var item=(net.minecraft.world.item.ItemStack)bookType.getMethod("getBookItem").invoke(book);
            if (entries.size()!=24 || categories.size()!=4 || item.isEmpty()) throw new IllegalStateException("guide did not compile");
            int pageCount=0;
            Class<?> guiType=Class.forName("vazkii.patchouli.client.book.gui.GuiBookEntry");
            Class<?> withText=Class.forName("vazkii.patchouli.client.book.page.abstr.PageWithText");
            var textRender=withText.getDeclaredField("textRender");textRender.setAccessible(true);
            for (Object entry:entries.values()) {
                Object gui=guiType.getConstructor(bookType,entry.getClass()).newInstance(book,entry);
                client.setScreen((net.minecraft.client.gui.screens.Screen)gui);
                String entryName=((net.minecraft.network.chat.Component)entry.getClass().getMethod("getName").invoke(entry)).getString();
                if(client.font.width(entryName)>116)throw new IllegalStateException("Book entry heading too wide: "+entryName);
                var pages=(java.util.List<?>)entry.getClass().getMethod("getPages").invoke(entry);
                for(Object page:pages) {
                    page.getClass().getMethod("onDisplayed",guiType,int.class,int.class).invoke(page,gui,0,0);
                    pageCount++;
                    if(page.getClass().getSimpleName().equals("PageText")) {
                        var titleField=page.getClass().getDeclaredField("title");titleField.setAccessible(true);
                        String title=(String)titleField.get(page);
                        if(title!=null && client.font.width(title)>116)throw new IllegalStateException("Book page title too wide: "+title);
                    }
                    if(!withText.isInstance(page)) continue;
                    Object renderer=textRender.get(page);
                    var wordsField=renderer.getClass().getDeclaredField("words");wordsField.setAccessible(true);
                    var scaleField=renderer.getClass().getDeclaredField("scale");scaleField.setAccessible(true);
                    if(scaleField.getFloat(renderer)<.999f) throw new IllegalStateException("Book page required downscaling: "+entry+" "+page);
                    for(Object word:(java.util.List<?>)wordsField.get(renderer)) {
                        int bottom=word.getClass().getField("y").getInt(word)+word.getClass().getField("height").getInt(word);
                        if(bottom>156) throw new IllegalStateException("Book page overflows: "+entry+" "+page+" bottom="+bottom);
                        // Patchouli Word.width includes the unbroken span, so measure its actual rendered fragment.
                        var wordText=word.getClass().getDeclaredField("text");wordText.setAccessible(true);
                        int left=word.getClass().getField("x").getInt(word);
                        var fragment=(net.minecraft.network.chat.Component)wordText.get(word);
                        String visible=fragment.getString().stripTrailing();
                        if(visible.isEmpty())continue;
                        int right=left+client.font.width(net.minecraft.network.chat.Component.literal(visible).setStyle(fragment.getStyle()));
                        if(left<0 || right>116) throw new IllegalStateException("Book text crosses page edge: "+entryName+" x="+left+" right="+right+" text="+((net.minecraft.network.chat.Component)wordText.get(word)).getString());
                    }
                }
            }
            client.setScreen(null);
            System.out.println("SIMPLYSPEAKERS_GUIDE_LAYOUT_PASS pages="+pageCount);
            System.out.println("SIMPLYSPEAKERS_GUIDE_PASS entries="+entries.size()+" categories="+categories.size());
        } catch (Exception error) { throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL guide",error); }
    }
    /** Real C2S edit, close without Save, receive authoritative BE update, reopen. */
    private static boolean verifyControllerReopen(Minecraft client) {
        try {
            var jobs=com.nstut.simplyspeakers.control.ControllerAction.values();
            var pos=client.player.blockPosition().offset(2,1,3);
            if(!(client.level.getBlockEntity(pos) instanceof com.nstut.simplyspeakers.blocks.entities.RedstoneControllerBlockEntity controller)) return false;
            if(settingIndex==jobs.length) return true;
            var job=jobs[settingIndex];
            if(!settingSent) {
                var screen=new com.nstut.simplyspeakers.client.screens.RedstoneControllerScreen(pos);
                client.setScreen(screen);
                setSignal(screen,"action",job);
                setSignal(screen,"network","__simplyspeakers_verify");
                setSignal(screen,"ceiling",.35);
                setSignal(screen,"restart",true);
                var speaker=(com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity)client.level.getBlockEntity(pos.offset(0,0,-3));
                setSignal(screen,"clip",speaker.getSpeakerState().getAudioId());
                screen.onClose(); settingSent=true; return false;
            }
            if(controller.getAction()!=job || Math.abs(controller.getVolumeCeiling()-.35f)>.001 || !controller.isRestartAnnouncement()) return false;
            var reopened=new com.nstut.simplyspeakers.client.screens.RedstoneControllerScreen(pos);
            client.setScreen(reopened);
            var field=reopened.getClass().getDeclaredField("action");field.setAccessible(true);
            if(((com.nstut.openui.state.Signal<?>)field.get(reopened)).get()!=job)throw new IllegalStateException("Controller action reset on reopen");
            client.setScreen(null);
            System.out.println("SIMPLYSPEAKERS_CONTROLLER_REOPEN_PASS action="+job.id());
            settingIndex++;settingSent=false;return settingIndex==jobs.length;
        } catch(Exception error) { throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL controller reopen",error); }
    }
    private static boolean verifySpeakerSettingsReopen(Minecraft client) {
        if(speakerSettingsDone)return true;
        var pos=client.player.blockPosition().offset(2,1,0);
        if(!(client.level.getBlockEntity(pos) instanceof com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity speaker))return false;
        if(!speakerSettingsSent) {
            var channel=new ProbeSender(new com.nstut.simplyspeakers.client.screens.SpeakerScreen(pos));
            channel.sendToServer(new com.nstut.simplyspeakers.network.UpdateMaxVolumePacketC2S(pos,.42f));
            channel.sendToServer(new com.nstut.simplyspeakers.network.UpdateMaxRangePacketC2S(pos,23));
            channel.sendToServer(new com.nstut.simplyspeakers.network.UpdateAudioDropoffPacketC2S(pos,.25f));
            channel.sendToServer(com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.networkName(pos,"Test station"));
            channel.sendToServer(com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.directionality(pos,.6f));
            channel.sendToServer(com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.coneAngle(pos,80));
            channel.sendToServer(com.nstut.simplyspeakers.network.SpeakerPolicyPacketC2S.rearAttenuation(pos,.4f));
            speakerSettingsSent=true;return false;
        }
        var state=speaker.getSpeakerState();
        if(Math.abs(state.getMaxVolume()-.42f)>.001 || state.getMaxRange()!=23 || Math.abs(state.getAudioDropoff()-.25f)>.001
            || !state.getNetworkName().equals("Test station") || Math.abs(state.getDirectionality()-.6f)>.001
            || state.getConeAngleDegrees()!=80 || Math.abs(state.getRearAttenuation()-.4f)>.001)return false;
        try {
            var screen=new com.nstut.simplyspeakers.client.screens.SpeakerScreen(pos);client.setScreen(screen);
            for(var pair:new Object[][]{{"maxVolume",.42},{"maxRange",23.0},{"audioDropoff",.25},{"directionality",.6},{"coneAngle",80.0},{"rearAttenuation",.4}}) {
                var field=screen.getClass().getDeclaredField((String)pair[0]);field.setAccessible(true);
                double value=(Double)((com.nstut.openui.state.Signal<?>)field.get(screen)).get();
                if(Math.abs(value-(Double)pair[1])>.001)throw new IllegalStateException("Setting reset on reopen: "+pair[0]);
            }
            var field=screen.getClass().getDeclaredField("networkName");field.setAccessible(true);
            if(!((com.nstut.openui.state.Signal<?>)field.get(screen)).get().equals("Test station"))throw new IllegalStateException("Network name reset on reopen");
            client.setScreen(null);speakerSettingsDone=true;
            new ProbeSender(new com.nstut.simplyspeakers.client.screens.SpeakerScreen(pos)).sendToServer(new com.nstut.simplyspeakers.network.UpdateMaxVolumePacketC2S(pos,0));
            System.out.println("SIMPLYSPEAKERS_SPEAKER_SETTINGS_REOPEN_PASS settings=7");
            return true;
        }catch(Exception error){throw new IllegalStateException("SIMPLYSPEAKERS_VERIFY_FAIL speaker settings reopen",error);}
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void setSignal(Object screen,String name,Object value) throws Exception {
        var field=screen.getClass().getDeclaredField(name);field.setAccessible(true);
        ((com.nstut.openui.state.Signal)field.get(screen)).set(value);
    }
}
