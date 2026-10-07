package com.nstut.simplyspeakers.client;

import com.nstut.openui.api.ButtonWidget;
import com.nstut.openui.api.UIComponent;
import com.nstut.simplyspeakers.SpeakerState;
import com.nstut.simplyspeakers.audio.AudioFileMetadata;
import com.nstut.simplyspeakers.client.screens.SpeakerScreen;
import com.nstut.simplyspeakers.network.PlaylistSyncPacketS2C;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.Consumer;

/** Opt-in native render/layout probe, using the real screen with sample audio and no world. */
final class SpeakerUiPreviewProbe {
    private static int ticks, phase, oldScale, oldWidth, oldHeight;
    private static SpeakerScreen screen;
    private static com.nstut.simplyspeakers.client.ui.SimplySpeakersUiScreen current;
    private static boolean ready, finished, oldFullscreen;
    private static long lastStepNanos;
    private record Case(String name, int size, boolean light) { }
    private static final String[] VIEWS = {"Library", "Playlist", "Queue", "Settings"};
    private static final java.util.List<Case> CASES = new java.util.ArrayList<>();
    static {
        for(int size=0;size<4;size++) for(boolean light : new boolean[]{false,true}) {
            for(String view:VIEWS) CASES.add(new Case(view,size,light));
            for(String view:new String[]{"Empty","NoMatches","LongNames","Paused","Stream","EmptyPlaylist","EmptyQueue","TrackMenu","DeleteDialog","ClearDialog","Proxy","ProxyMissing","JumpDialog","JumpInvalid","JumpValid","TooltipPlay","TooltipRepeat","TooltipRestart","ScrolledSettings","ProxyScrolled","AnnouncementScrolled"})
                CASES.add(new Case(view,size,light));
            for(var action:com.nstut.simplyspeakers.control.ControllerAction.values()) { CASES.add(new Case("Controller_"+action.id(),size,light)); CASES.add(new Case("Controller_"+action.id()+"Scrolled",size,light)); CASES.add(new Case("Controller_"+action.id()+"Picker",size,light)); }
            for(String name:new String[]{"PlaylistMany","PlaylistLongNames","PlaylistBrowse","PlaylistCreateDialog","PlaylistRenameDialog","PlaylistDuplicateDialog","PlaylistDeleteDialog","PlaylistChooser","PlaylistSearch","PlaylistSearchNone","PlaylistFullLibrary","AddToPlaylistDialog"})CASES.add(new Case(name,size,light));
            for(String help:new String[]{"directionality","cone_angle","rear_attenuation","network_name","directional_preview"}) CASES.add(new Case("SettingsHelp"+help,size,light));
            CASES.add(new Case("ProxyVolume",size,light)); CASES.add(new Case("ProxyEnabled",size,light)); CASES.add(new Case("ProxyVolumePicker",size,light)); CASES.add(new Case("ProxyEnabledPicker",size,light));
        }
    }
    static {
        if("controller".equals(System.getProperty("simplyspeakers.uiAuditGroup"))) CASES.removeIf(c -> !c.name.startsWith("Controller_") && !c.name.startsWith("Proxy") && !c.name.equals("AnnouncementScrolled") && !c.name.equals("JumpInvalid") && !c.name.equals("JumpValid") && !c.name.startsWith("Tooltip"));
    }
    static {
        if("interaction".equals(System.getProperty("simplyspeakers.uiAuditGroup"))) CASES.removeIf(c -> !c.name.equals("JumpInvalid") && !c.name.equals("JumpValid") && !c.name.startsWith("Tooltip"));
    }
    static {
        if("forms_scrolled".equals(System.getProperty("simplyspeakers.uiAuditGroup"))) CASES.removeIf(c -> !c.name.startsWith("Controller_") || !c.name.endsWith("Scrolled"));
    }
    static {
        if("playlists".equals(System.getProperty("simplyspeakers.uiAuditGroup"))) CASES.removeIf(c -> !c.name.startsWith("Playlist") && !c.name.equals("EmptyPlaylist") && !c.name.equals("ClearDialog") && !c.name.equals("Queue") && !c.name.equals("AddToPlaylistDialog"));
    }
    static {
        if("playlist_picker".equals(System.getProperty("simplyspeakers.uiAuditGroup"))) CASES.removeIf(c -> !java.util.Set.of("Playlist","PlaylistChooser","PlaylistLongNames","PlaylistFullLibrary","AddToPlaylistDialog").contains(c.name));
    }
    static {
        if("settings_layout".equals(System.getProperty("simplyspeakers.uiAuditGroup"))) CASES.removeIf(c -> !java.util.Set.of("Library","Settings","ScrolledSettings","JumpDialog","JumpInvalid","JumpValid").contains(c.name));
    }
    static {
        if("settings_help".equals(System.getProperty("simplyspeakers.uiAuditGroup"))) CASES.removeIf(c -> !c.name.startsWith("SettingsHelp"));
    }
    static {
        if("redstone_rework".equals(System.getProperty("simplyspeakers.uiAuditGroup"))) CASES.removeIf(c ->
            !c.name.startsWith("Controller_") && !c.name.startsWith("Proxy") && !c.name.equals("AnnouncementScrolled")
            && !c.name.startsWith("Jump") && !c.name.startsWith("Tooltip") && !c.name.startsWith("SettingsHelp")
            && !java.util.Set.of("Library","Settings","ScrolledSettings").contains(c.name));
    }
    static {
        if("ux_repair".equals(System.getProperty("simplyspeakers.uiAuditGroup"))) CASES.removeIf(c ->
            !c.name.startsWith("Playlist") && !c.name.startsWith("Queue") && !c.name.equals("EmptyQueue")
            && !c.name.startsWith("Settings") && !c.name.equals("ScrolledSettings")
            && !c.name.equals("TooltipRestart") && !c.name.startsWith("Controller_") && !c.name.startsWith("Proxy"));
    }
    static {
        if("ux_core".equals(System.getProperty("simplyspeakers.uiAuditGroup"))) CASES.removeIf(c ->
            !c.name.startsWith("Playlist") && !c.name.equals("Queue") && !c.name.equals("EmptyQueue")
            && !c.name.startsWith("Settings") && !c.name.equals("ScrolledSettings") && !c.name.equals("TooltipRestart"));
    }
    private SpeakerUiPreviewProbe() { }
    static void tick(Minecraft client) {
        if (!Boolean.getBoolean("simplyspeakers.uiPreview") || finished) return;
        try {
            if(current==null && phase==0) {
                if(client.getOverlay()!=null || client.screen==null) return;
                oldScale=client.options.guiScale().get(); oldWidth=client.getWindow().getScreenWidth(); oldHeight=client.getWindow().getScreenHeight();
                oldFullscreen=(Boolean)client.getWindow().getClass().getMethod("isFullscreen").invoke(client.getWindow());
                if(oldFullscreen) client.getWindow().getClass().getMethod("toggleFullScreen").invoke(client.getWindow());
            }
            Case c=CASES.get(phase);
            if(!ready) { prepare(client,c); ready=true; ticks=0; return; }
            // Resolve pending reactive layout through the same input path before
            // reading coordinates. A zero wheel delta does not scroll content.
            current.uiRuntime().flushFrameTasks();
            current.uiRuntime().mouseScrolled(0,0,0);
            long now=System.nanoTime();
            if(now-lastStepNanos<50_000_000L) return;
            lastStepNanos=now;
            ticks++;
            if(c.name.startsWith("SettingsHelp")) {
                String key = c.name.startsWith("SettingsHelpMode_") || c.name.equals("SettingsHelpCycle") ? "settings.redstone" : "settings."+c.name.substring("SettingsHelp".length());
                var target=keyed(current.uiRuntime().root(),key);
                if(ticks==2) {
                    var scroll=keyed(current.uiRuntime().root(),"settings.content");
                    moveMouse(client,scroll.getX()+scroll.getWidth()-1,scroll.getY()+scroll.getHeight()/2.0);
                }
                // Layout/focus reveal may settle after the first wheel event.
                // Repeat real wheel input until the requested control is visible.
                if(ticks>=3 && ticks<=6) {
                    var scroll=keyed(current.uiRuntime().root(),"settings.content");
                    double center=target.getY()+target.getHeight()/2.0;
                    double viewportCenter=scroll.getY()+scroll.getHeight()/2.0;
                    if(center<scroll.getY() || center>=scroll.getY()+scroll.getHeight())
                        current.uiRuntime().mouseScrolled(scroll.getX()+scroll.getWidth()-1,viewportCenter,(viewportCenter-center)/24.0);
                }
                if(ticks==7) {
                    var scroll=keyed(current.uiRuntime().root(),"settings.content");
                    int center=target.getY()+target.getHeight()/2;
                    if(center<scroll.getY() || center>=scroll.getY()+scroll.getHeight())throw new IllegalStateException("Settings target center clipped: "+key+" center="+center+" viewport="+scroll.getY()+".."+(scroll.getY()+scroll.getHeight()));
                    if(c.name.equals("SettingsHelpCycle"))click(target);
                    moveMouse(client,target.getX()+target.getWidth()/2.0,target.getY()+target.getHeight()/2.0);
                }
                if(ticks==28) {
                    if(!target.hasVisibleAreaWithinAncestorClips() || target.tooltip()==null || target.tooltip().getString().isBlank() || target.tooltip().getString().contains("gui.simplyspeakers"))throw new IllegalStateException("Missing/unlocalized settings help: "+key);
                    if(target instanceof ButtonWidget button && (c.name.startsWith("SettingsHelpMode_") || c.name.equals("SettingsHelpCycle"))) {
                        String mode=c.name.equals("SettingsHelpCycle")?"pulse":c.name.substring("SettingsHelpMode_".length());
                        if(!button.getLabel().getString().equals(Component.translatable("gui.simplyspeakers.redstone.mode."+mode).getString()))throw new IllegalStateException("Unlocalized mode label");
                        String help=Component.translatable("gui.simplyspeakers.redstone.mode."+mode+".tooltip").getString();
                        if(!target.tooltip().getString().contains(help))throw new IllegalStateException("Mode tooltip did not update");
                    }
                }
            }
            if(ticks==3 && c.name.endsWith("Picker")) {
                var button=keyed(current.uiRuntime().root(),"controller.mode");
                moveMouse(client,button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0);
            }
            if(ticks==5 && c.name.endsWith("Picker")) click(keyed(current.uiRuntime().root(),"controller.mode"));
            if(c.name.endsWith("Picker")) {
                String mode=c.name.startsWith("Proxy")?(c.name.startsWith("ProxyV")?"volume":"enabled"):c.name.substring(11).replace("Picker","");
                if(ticks==7) {
                    var overlay=current.uiRuntime().overlays().topBlockingComponent();
                    if(overlay==null)throw new IllegalStateException("Controller mode picker did not open");
                    var selected=keyed(overlay,"controller.mode."+mode);
                    var scroll=keyed(overlay,"controller.choices");
                    if(selected==null || selected.tooltip()==null || selected.tooltip().getString().isBlank())throw new IllegalStateException("Mode missing help: "+mode);
                    double center=selected.getY()+selected.getHeight()/2.0;
                    if(selected.getY()<scroll.getY() || selected.getY()+selected.getHeight()>scroll.getY()+scroll.getHeight()) throw new IllegalStateException("Controller choice must be fully visible without scrolling: "+mode);
                    moveMouse(client,selected.getX()+selected.getWidth()/2.0,center);
                }
                if(ticks==11) {
                    var overlay=current.uiRuntime().overlays().topBlockingComponent();verifyBounds(overlay);
                    capture(client,"picker-"+c.size+"-"+c.light+"-"+c.name+".png");
                    var selected=keyed(overlay,"controller.mode."+mode);
                    if(!selected.hasVisibleAreaWithinAncestorClips())throw new IllegalStateException("Mode selector clipped: "+mode);
                    click(selected);
                }
                if(ticks==18) {
                    if(current.uiRuntime().overlays().hasModal())throw new IllegalStateException("Mode selection failed");
                    var field=current.getClass().getDeclaredField("action");field.setAccessible(true);
                    if(((com.nstut.openui.state.Signal<?>)field.get(current)).get()!=com.nstut.simplyspeakers.control.ControllerAction.byId(mode))throw new IllegalStateException("Wrong selected controller mode");
                }
            }
            if(c.name.startsWith("PlaylistSearch")) {
                if(ticks==3) {
                    var overlay=current.uiRuntime().overlays().topBlockingComponent();
                    click(keyed(overlay,"playlist.search"));
                    for(char letter:(c.name.equals("PlaylistSearchNone")?"xyzmissing":"EVENING").toCharArray())current.uiRuntime().charTyped(letter,0);
                }
                if(ticks==8) {
                    var field=SpeakerScreen.class.getDeclaredField("playlistSearch");field.setAccessible(true);
                    String query=(String)((com.nstut.openui.state.Signal<?>)field.get(screen)).get();
                    if(query.isBlank())throw new IllegalStateException("Search field did not receive native keyboard input");
                    var overlay=current.uiRuntime().overlays().topBlockingComponent();
                    if(c.name.equals("PlaylistSearchNone")) {
                        if(!containsText(overlay,Component.translatable("gui.simplyspeakers.player.no_playlists_found").getString()))throw new IllegalStateException("No matches state missing");
                    } else if(!containsText(overlay,"Evening mix - 1 track") || containsText(overlay,"Default - 3 tracks"))throw new IllegalStateException("Playlist search did not filter by name case-insensitively");
                }
            }
            if(ticks==3 && c.name.contains("Scrolled")) current.uiRuntime().mouseScrolled(current.width/2.0,current.height/2.0,-30);
            if(ticks>=4 && ticks<=6 && c.name.startsWith("Jump") && !current.uiRuntime().overlays().hasModal()) { var controls=keyed(current.uiRuntime().root(),"player.transport");var jump=controls.child(controls.childCount()-1);((ButtonWidget)jump).enabled(true);click(jump); }
            if(ticks==7 && (c.name.equals("JumpInvalid") || c.name.equals("JumpValid"))) {
                signal(screen,"jumpTimestamp",c.name.equals("JumpInvalid")?"1:99":"1:30");
                var overlay=current.uiRuntime().overlays().topBlockingComponent();
                if(overlay==null) throw new IllegalStateException("Timestamp dialog failed to open before submission");var button=findIcon(overlay); if(button==null)throw new IllegalStateException("Missing timestamp confirmation");click(button);
            }
            if(ticks==2 && c.name.startsWith("Tooltip")) { var controls=keyed(current.uiRuntime().root(),"player.transport");var button=controls.child(c.name.equals("TooltipPlay")?3:c.name.equals("TooltipRestart")?7:6);moveMouse(client,button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0); }
            if(ticks<(c.name.endsWith("Picker")?18:c.name.startsWith("SettingsHelp")?28:c.name.startsWith("Tooltip")?24:c.name.startsWith("Jump")?16:10)) return;
            if(c.name.equals("JumpDialog") && !current.uiRuntime().overlays().hasModal()) throw new IllegalStateException("Timestamp dialog did not open");
            if(c.name.equals("JumpInvalid") && (!current.uiRuntime().overlays().hasModal() || !containsText(current.uiRuntime().overlays().topBlockingComponent(),Component.translatable("gui.simplyspeakers.player.invalid_timestamp").getString())))throw new IllegalStateException("Timestamp error not visible");
            if(c.name.equals("JumpValid") && current.uiRuntime().overlays().hasModal())throw new IllegalStateException("Valid timestamp did not close dialog");
            verifyBounds(current.uiRuntime().root());
            for(var overlay:current.uiRuntime().overlays().components()) verifyBounds(overlay);
            if(current instanceof SpeakerScreen) {
                verifyPlayer();
                var f=SpeakerScreen.class.getDeclaredField("tab");f.setAccessible(true);
                String actual=((com.nstut.openui.state.Signal<?>)f.get(screen)).get().toString();
                String expected=c.name.equals("Settings") || c.name.equals("ScrolledSettings") || c.name.startsWith("SettingsHelp") ? "SETTINGS" : c.name.startsWith("Playlist") || c.name.equals("EmptyPlaylist") ? "PLAYLIST" : c.name.equals("Queue") || c.name.equals("EmptyQueue") ? "QUEUE" : "AUDIO";
                if(!actual.equals(expected)) throw new IllegalStateException("Wrong view: "+actual+" expected "+expected);
            }
            if(c.name.equals("Library")) verifySeek();
            String id="audit-"+c.size+"-"+(c.light?"light":"dark")+"-"+c.name.toLowerCase(java.util.Locale.ROOT);
            capture(client,id+".png");
            System.out.println("SIMPLYSPEAKERS_UI_CASE_PASS "+id+" viewport="+current.width+"x"+current.height);
            phase++;ready=false;
            if(phase==CASES.size()) {
                finished=true;
                client.options.guiScale().set(oldScale);
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(windowHandle(client),oldWidth,oldHeight);
                if(oldFullscreen) client.getWindow().getClass().getMethod("toggleFullScreen").invoke(client.getWindow());
                client.setScreen(null);
                System.out.println("SIMPLYSPEAKERS_UI_AUDIT_PASS cases="+CASES.size()); client.stop();
            }
        } catch(Exception e) { throw new IllegalStateException("Visual audit case "+CASES.get(phase),e); }
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static void signal(Object object,String name,Object value) throws Exception {
        var f=object.getClass(); while(f!=null) { try { var field=f.getDeclaredField(name);field.setAccessible(true); ((com.nstut.openui.state.Signal)field.get(object)).set(value);return; } catch(NoSuchFieldException ignored) { f=f.getSuperclass(); } }
        throw new NoSuchFieldException(name);
    }
    private static void invoke(Object object,String name) throws Exception {
        var method=SpeakerScreen.class.getDeclaredMethod(name);method.setAccessible(true);method.invoke(object);
    }
    private static void prepare(Minecraft client,Case c) throws Exception {
        client.options.guiScale().set(c.size==2?1:2);
        org.lwjgl.glfw.GLFW.glfwSetWindowSize(windowHandle(client),c.size==1?1280:c.size==3?640:854,c.size==1?720:480);
        resizeGui(client);
        if(c.name.equals("Proxy") || c.name.equals("ProxyMissing") || c.name.equals("ProxyScrolled")) {
            current=new com.nstut.simplyspeakers.client.screens.ProxySpeakerScreen(BlockPos.ZERO) {
                @Override protected void fetchDataFromBlockEntity() {
                    if(c.name.equals("ProxyMissing")) return;
                    try { var field=com.nstut.simplyspeakers.client.screens.ProxySpeakerScreen.class.getDeclaredField("speaker"); field.setAccessible(true);
                        field.set(this,new com.nstut.simplyspeakers.blocks.entities.ProxySpeakerBlockEntity(BlockPos.ZERO,com.nstut.simplyspeakers.blocks.BlockRegistries.PROXY_SPEAKER.get().defaultBlockState()));
                    }catch(Exception e){throw new IllegalStateException(e);}
                }
            };
        } else if(c.name.startsWith("Controller_") || c.name.startsWith("ProxyV") || c.name.startsWith("ProxyE") || c.name.equals("AnnouncementScrolled")) {
            var controller=new com.nstut.simplyspeakers.client.screens.RedstoneControllerScreen(BlockPos.ZERO);
            signal(controller,"network","Workshop station");
            var action=c.name.equals("AnnouncementScrolled") ? com.nstut.simplyspeakers.control.ControllerAction.ANNOUNCEMENT : c.name.startsWith("Controller_") ? com.nstut.simplyspeakers.control.ControllerAction.byId(c.name.substring(11).replace("Scrolled","").replace("Picker","")) : c.name.startsWith("ProxyVolume") ? com.nstut.simplyspeakers.control.ControllerAction.VOLUME : com.nstut.simplyspeakers.control.ControllerAction.ENABLED;
            if(c.name.endsWith("Picker")) action=c.name.startsWith("Proxy")
                ? (action==com.nstut.simplyspeakers.control.ControllerAction.ENABLED?com.nstut.simplyspeakers.control.ControllerAction.VOLUME:com.nstut.simplyspeakers.control.ControllerAction.ENABLED)
                : (action==com.nstut.simplyspeakers.control.ControllerAction.TOGGLE?com.nstut.simplyspeakers.control.ControllerAction.VOLUME:com.nstut.simplyspeakers.control.ControllerAction.TOGGLE);
            signal(controller,"action",action);signal(controller,"proxy",c.name.startsWith("Proxy"));signal(controller,"proxyCoordinates","12 -60 34");
            controller.updateAudioList(java.util.List.of(new AudioFileMetadata("a","Arrival announcement.wav","",24),new AudioFileMetadata("b","A very long station safety announcement name.wav","",35)));
            current=controller;
        } else {
            screen=new SpeakerScreen(BlockPos.ZERO) { @Override protected void fetchDataFromBlockEntity(){installPreviewSpeaker(this);} };current=screen;
        }
        signal(current,"themeMode",c.light?com.nstut.simplyspeakers.client.ui.UiThemeMode.LIGHT:com.nstut.simplyspeakers.client.ui.UiThemeMode.DARK);
        client.setScreen(current);
        if(!(current instanceof SpeakerScreen)) return;
        seed();
        if(c.name.startsWith("Playlist"))navigate("Playlist",1);
        if(c.name.equals("PlaylistBrowse") || c.name.equals("PlaylistLongNames")) {
            var field=SpeakerScreen.class.getDeclaredField("savedPlaylists");field.setAccessible(true);
            var entries=(java.util.List<com.nstut.simplyspeakers.playlist.PlaylistLibrarySnapshot.Entry>)((com.nstut.openui.state.Signal<?>)field.get(screen)).get();
            signal(screen,"selectedPlaylistId",entries.get(c.name.equals("PlaylistBrowse")?1:2).id());invoke(screen,"refreshSelectedPlaylist");
            if(c.name.equals("PlaylistBrowse") && !screenPlayingSource(screen).equals("default"))throw new IllegalStateException("Browsing changed playback source");
        }
        if(c.name.equals("PlaylistCreateDialog") || c.name.equals("PlaylistRenameDialog") || c.name.equals("PlaylistDuplicateDialog")) {
            var method=SpeakerScreen.class.getDeclaredMethod("playlistNameDialog",byte.class);method.setAccessible(true);
            method.invoke(screen,c.name.equals("PlaylistCreateDialog")?com.nstut.simplyspeakers.network.PlaylistControlPacketC2S.OP_CREATE_PLAYLIST:c.name.equals("PlaylistRenameDialog")?com.nstut.simplyspeakers.network.PlaylistControlPacketC2S.OP_RENAME_PLAYLIST:com.nstut.simplyspeakers.network.PlaylistControlPacketC2S.OP_DUPLICATE_PLAYLIST);
        }
        if(c.name.equals("PlaylistDeleteDialog"))invoke(screen,"confirmDeletePlaylist");
        if(c.name.equals("PlaylistChooser") || c.name.startsWith("PlaylistSearch") || c.name.equals("PlaylistFullLibrary") || c.name.equals("AddToPlaylistDialog")) {
            var method=SpeakerScreen.class.getDeclaredMethod("chooseSavedPlaylist",AudioFileMetadata.class);method.setAccessible(true);
            method.invoke(screen,c.name.equals("AddToPlaylistDialog")?new AudioFileMetadata("a","Workshop ambience.wav","",272):null);
        }
        if(c.name.startsWith("Jump")) signal(screen,"seekable",true);
        if(c.name.equals("Empty")) screen.updateAudioList(java.util.List.of());
        if(c.name.equals("NoMatches")) signal(screen,"search","not present");
        if(c.name.equals("LongNames")) {
            screen.updateAudioList(java.util.List.of(new AudioFileMetadata("a","An exceptionally long music title that must truncate without colliding with duration or action buttons.mp3","",7200)));
            signal(screen,"playingFilename","An exceptionally long music title that must truncate without covering the volume controls");
        }
        if(c.name.equals("Paused")) signal(screen,"paused",true);
        if(c.name.equals("Stream")){signal(screen,"seekable",false);signal(screen,"duration",0.0);signal(screen,"playingFilename","https://radio.example/stream");}
        if(c.name.equals("EmptyPlaylist")){signal(screen,"playlistIds",java.util.List.of());navigate("Playlist",1);}
        if(c.name.equals("EmptyQueue")){signal(screen,"queueIds",java.util.List.of());signal(screen,"upcomingIndices",java.util.List.of());navigate("Queue",2);}
        for(int i=0;i<VIEWS.length;i++) if(c.name.equals(VIEWS[i])) navigate(VIEWS[i],i);
        if(c.name.equals("TrackMenu")){var m=SpeakerScreen.class.getDeclaredMethod("openAudioMenu",AudioFileMetadata.class);m.setAccessible(true);m.invoke(screen,new AudioFileMetadata("a","Workshop ambience.wav","",272));}
        if(c.name.equals("DeleteDialog")){var m=SpeakerScreen.class.getDeclaredMethod("confirmDelete",AudioFileMetadata.class);m.setAccessible(true);m.invoke(screen,new AudioFileMetadata("a","Long recording name that should wrap safely inside the confirmation dialog.wav","",272));}
        if(c.name.equals("ClearDialog"))invoke(screen,"confirmClearPlaylist");
        if(c.name.equals("ScrolledSettings"))navigate("Settings",3);
        if(c.name.startsWith("SettingsHelp")) {
            navigate("Settings",3);
        }
        moveMouse(client,2,2);
    }
    private static String screenPlayingSource(SpeakerScreen screen)throws Exception {
        var f=SpeakerScreen.class.getDeclaredField("activePlaylistId");f.setAccessible(true);return ((com.nstut.openui.state.Signal<?>)f.get(screen)).get().toString();
    }
    private static UIComponent keyed(UIComponent root,String key) {
        if(key.equals(root.key()))return root;
        for(var child:root.children()){var found=keyed(child,key);if(found!=null)return found;}return null;
    }
    private static void verifyPlayer() {
        UIComponent root=current.uiRuntime().root();
        for(String key:new String[]{"player.transport"}) {
            UIComponent group=keyed(root,key);
            if(group==null || group.children().isEmpty())throw new IllegalStateException("Missing "+key);
            var first=group.child(0);var last=group.child(group.childCount()-1);
            if(Math.abs((first.getX()+last.getX()+last.getWidth())-(2*group.getX()+group.getWidth()))>2)
                throw new IllegalStateException("Controls not centered: "+key);
        }
        UIComponent progress=keyed(root,"player.progress");
        int center=progress.getY()+progress.getHeight()/2;
        for(var child:progress.children()) if(Math.abs(child.getY()+child.getHeight()/2-center)>1)throw new IllegalStateException("Duration baseline not centered");
        verifyIcons(root);
        verifyLibraryRows(root);
        for (String setting : new String[]{"directionality", "cone_angle", "rear_attenuation"}) {
            UIComponent value=keyed(root,"settings."+setting+".value");
            UIComponent slider=keyed(root,"settings."+setting);
            if(value!=null && slider!=null && value.hasVisibleAreaWithinAncestorClips()
                    && Math.abs(value.getX()+value.getWidth()-slider.getX()-slider.getWidth())>2)
                throw new IllegalStateException("Directional value not right aligned: "+setting);
        }
    }
    private static void verifyLibraryRows(UIComponent root) {
        if("library.title".equals(root.key()) && root instanceof ButtonWidget button && button.hasVisibleAreaWithinAncestorClips()) {
            if(Minecraft.getInstance().font.width(button.getLabel())>button.getWidth()-12)throw new IllegalStateException("Track title overflows");
            int center=button.getY()+button.getHeight()/2;
            for(var sibling:button.parent().children()) if(Math.abs(sibling.getY()+sibling.getHeight()/2-center)>1)throw new IllegalStateException("Track row baseline");
        }
        for(var child:root.children())verifyLibraryRows(child);
    }
    private static UIComponent findIcon(UIComponent root) {
        if(root instanceof com.nstut.simplyspeakers.client.ui.SpeakerIconButton)return root;
        for(var child:root.children()){var found=findIcon(child);if(found!=null)return found;}return null;
    }
    private static boolean containsText(UIComponent root,String text) {
        if(root instanceof ButtonWidget button && (button.getLabel().getString().contains(text) || button.tooltip()!=null && button.tooltip().getString().contains(text)))return true;
        if(root instanceof com.nstut.openui.api.TextWidget label && label.getText().getString().contains(text))return true;
        for(var child:root.children())if(containsText(child,text))return true;return false;
    }
    private static void verifyIcons(UIComponent root) {
        if(root instanceof com.nstut.simplyspeakers.client.ui.SpeakerIconButton icon) {
            if(!icon.getLabel().getString().isEmpty() || icon.tooltip()==null || icon.tooltip().getString().isBlank())throw new IllegalStateException("Icon missing tooltip");
            if(icon.getWidth()!=24 || icon.getHeight()!=24)throw new IllegalStateException("Icon target dimensions");
        }
        for(var child:root.children())verifyIcons(child);
    }

    private static void resizeGui(Minecraft client) throws ReflectiveOperationException {
        for(String name:new String[]{"resizeDisplay","resizeGui"})try{Minecraft.class.getMethod(name).invoke(client);return;}catch(NoSuchMethodException ignored){}
        throw new NoSuchMethodException("GUI resize");
    }
    private static void moveMouse(Minecraft client,double x,double y) throws ReflectiveOperationException {
        double scale=client.getWindow().getGuiScale();
        var method=client.mouseHandler.getClass().getDeclaredMethod("onMove",long.class,double.class,double.class);
        method.setAccessible(true); method.invoke(client.mouseHandler,windowHandle(client),x*scale,y*scale);
    }
    private static long windowHandle(Minecraft client) throws ReflectiveOperationException {
        for(String name:new String[]{"getWindow","handle"}) try {
            return ((Number)client.getWindow().getClass().getMethod(name).invoke(client.getWindow())).longValue();
        }catch(NoSuchMethodException ignored) { }
        throw new NoSuchMethodException("Window handle accessor");
    }

    private static void installPreviewSpeaker(SpeakerScreen preview) {
        try {
            var field = SpeakerScreen.class.getDeclaredField("speaker"); field.setAccessible(true);
            field.set(preview, new com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity(BlockPos.ZERO,
                    com.nstut.simplyspeakers.blocks.BlockRegistries.SPEAKER.get().defaultBlockState()));
        } catch (ReflectiveOperationException e) { throw new IllegalStateException("Preview speaker unavailable", e); }
    }

    private static void seed() {
        screen.updateAudioList(List.of(new AudioFileMetadata("a", "Workshop ambience.wav", "", 272),
                new AudioFileMetadata("b", "Night drive.wav", "", 198),
                new AudioFileMetadata("c", "Rain on the roof.wav", "", 364),
                new AudioFileMetadata("d", "Factory announcement.wav", "", 24),
                new AudioFileMetadata("e", "Quiet evening.wav", "", 312)));
        SpeakerState state = new SpeakerState("a", "Workshop ambience", false, false, -1);
        state.getPlaylist().add("a", "Workshop ambience");
        state.getPlaylist().add("c", "Rain on the roof");
        state.getPlaylist().add("e", "Quiet evening");
        state.getPlaylist().playFromStart(); state.getPlaylist().queueLast("b");
        state.startPlaybackAt(0, 83);
        String evening=state.createSavedPlaylist("Evening mix");state.findSavedPlaylist(evening).getPlaylist().add("b","Night drive.wav");
        String station=state.createSavedPlaylist("A very long named playlist for checking truncation and selection");state.findSavedPlaylist(station).getPlaylist().add("d","Factory announcement.wav");
        if(CASES.get(phase).name.equals("PlaylistFullLibrary"))for(int n=3;n<com.nstut.simplyspeakers.SpeakerState.MAX_SAVED_PLAYLISTS;n++)state.createSavedPlaylist("Saved playlist "+n);
        screen.updatePlaylistModel(PlaylistSyncPacketS2C.fromState(BlockPos.ZERO, "", state, 0));
    }

    @SuppressWarnings("unchecked")
    private static void verifySeek() throws ReflectiveOperationException {
        var sliderField = SpeakerScreen.class.getDeclaredField("timeline"); sliderField.setAccessible(true);
        UIComponent slider = (UIComponent) sliderField.get(screen);
        double y = slider.getY() + slider.getHeight() / 2.0;
        double x = slider.getX() + slider.getWidth() * 0.25;
        double end = slider.getX() + slider.getWidth() * 0.75;
        current.uiRuntime().preRender((int)x,(int)y);
        current.uiRuntime().mouseClicked(x, y, 0);
        screen.uiRuntime().mouseDragged(end, y, 0, end - x, 0);
        screen.uiRuntime().mouseReleased(end, y, 0);
        var positionField = SpeakerScreen.class.getDeclaredField("position"); positionField.setAccessible(true);
        double position = ((com.nstut.openui.state.Signal<Double>) positionField.get(screen)).get();
        if (position < 180 || position > 230) throw new IllegalStateException("Timeline drag did not seek: " + position);
        System.out.println("SIMPLYSPEAKERS_UI_SEEK_PASS position=" + position);
    }

    private static void click(UIComponent component) {
        double x = component.getX() + component.getWidth() / 2.0;
        double y = component.getY() + component.getHeight() / 2.0;
        current.uiRuntime().preRender((int)x,(int)y);
        current.uiRuntime().mouseClicked(x, y, 0);
        current.uiRuntime().mouseReleased(x, y, 0);
    }

    private static ButtonWidget findButton(UIComponent component, String label) {
        if (component instanceof ButtonWidget button && label.equals(button.getLabel().getString())) return button;
        for (var child : component.children()) {
            ButtonWidget found = findButton(child, label);
            if (found != null) return found;
        }
        return null;
    }

    @SuppressWarnings({"unchecked","rawtypes"})
    private static void navigate(String label,int index) {
        try {
            var field=SpeakerScreen.class.getDeclaredField("tab");field.setAccessible(true);
            var tab=(com.nstut.openui.state.Signal)field.get(screen);
            tab.set(Enum.valueOf((Class)tab.get().getClass(),new String[]{"AUDIO","PLAYLIST","QUEUE","SETTINGS"}[index]));
        }catch(Exception e){throw new IllegalStateException(e);}
    }

    private static UIComponent findTabs(UIComponent component) {
        if (component instanceof com.nstut.openui.controls.Tabs<?>) return component;
        for (var child : component.children()) {
            UIComponent result = findTabs(child); if (result != null) return result;
        }
        return null;
    }

    private static void verifyBounds(UIComponent component) { verifyBounds(component, false); }

    private static void verifyBounds(UIComponent component, boolean verticallyClipped) {
        verticallyClipped |= component instanceof com.nstut.openui.controls.ScrollView
                || component instanceof com.nstut.openui.controls.VirtualList<?>;
        if (component instanceof ButtonWidget && component.getWidth() > 0 && component.getHeight() > 0) {
            if (component.getX() < 0 || component.getX() + component.getWidth() > current.width
                    || (!verticallyClipped && (component.getY() < 0 || component.getY() + component.getHeight() > current.height)))
                throw new IllegalStateException("Button outside screen: " + ((ButtonWidget) component).getLabel().getString());
        }
        if(component.getWidth()>0 && component.hasVisibleAreaWithinAncestorClips() && "controller.paragraph".equals(component.key()) && component instanceof com.nstut.openui.api.TextWidget label
                && component.getHeight()<Minecraft.getInstance().font.split(label.getText(),component.getWidth()).size()*Minecraft.getInstance().font.lineHeight)
            throw new IllegalStateException("Wrapped controller text overlaps next field: "+label.getText().getString()+" bounds="+component.getWidth()+"x"+component.getHeight());
        for (var child : component.children()) verifyBounds(child, verticallyClipped);
    }

    private static void capture(Minecraft client, String filename) throws ReflectiveOperationException {
        Object target = Minecraft.class.getMethod("getMainRenderTarget").invoke(client);
        Consumer<Component> result = text -> System.out.println("SIMPLYSPEAKERS_UI_SCREENSHOT " + text.getString());
        for (var method : Class.forName("net.minecraft.client.Screenshot").getMethods()) {
            if (method.getName().equals("grab") && method.getParameterCount() == 5
                    && method.getParameterTypes()[0] == java.io.File.class && method.getParameterTypes()[1] == String.class) {
                method.invoke(null, client.gameDirectory, filename, target, 1, result); return;
            }
            if (method.getName().equals("grab") && method.getParameterCount() == 4
                    && method.getParameterTypes()[0] == java.io.File.class && method.getParameterTypes()[1] == String.class) {
                method.invoke(null, client.gameDirectory, filename, target, result);
                return;
            }
        }
        throw new NoSuchMethodException("Screenshot.grab(File, String, target, callback)");
    }
}
