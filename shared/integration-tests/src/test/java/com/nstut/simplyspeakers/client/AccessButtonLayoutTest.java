package com.nstut.simplyspeakers.client;

import com.nstut.openui.api.Ui;
import com.nstut.openui.api.UIComponent;
import com.nstut.openui.runtime.UiRuntime;
import com.nstut.openui.runtime.NativeWidgetHost;
import com.nstut.simplyspeakers.client.ui.SpeakerMarqueeButton;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicReference;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class AccessButtonLayoutTest {
    @Test void modeChangesReserveLongestLabelAndRemainInsideNarrowRows() {
        var font = mock(Font.class);
        Component[] labels = {Component.literal("Public"), Component.literal("Trusted players"),
                Component.literal("Owner only"), Component.literal("Operators only")};
        for (var label : labels) when(font.width(label)).thenReturn(label.getString().length()*6);
        var selected = new AtomicReference<>(labels[0]);
        var button = new SpeakerMarqueeButton(selected::get, 6).reserveLabels(labels);
        button.ghost().small().flex();
        assertEquals(102, button.preferredWidth(font));
        for (int width : new int[]{160,240,400}) {
            UIComponent row = Ui.row(Ui.text(Component.literal("Control access")).nowrap().marquee().flex(),button).gap(6);
            var runtime = new UiRuntime(font,mock(NativeWidgetHost.class));
            try {
                runtime.setRoot(row);runtime.setViewport(0,0,width,24);runtime.flushFrameTasks();
                for (var label : labels) {
                    selected.set(label);
                    assertEquals(102,button.preferredWidth(font));
                    row.layoutTree(font,0,0,width,24);
                    assertTrue(button.getWidth()>12);
                    assertTrue(button.getX()>=0);
                    assertTrue(button.getX()+button.getWidth()<=width);
                }
            } finally {runtime.close();}
        }
    }
    @Test void emptyTrustedDialogDoesNotMountAnEmptyList() throws Exception {
        com.nstut.simplyspeakers.client.screens.SpeakerScreen screen;
        try (var prefs=mockStatic(com.nstut.simplyspeakers.client.ui.SimplySpeakersUiPreferences.class);
             var minecraft=mockStatic(net.minecraft.client.Minecraft.class)) {
            minecraft.when(net.minecraft.client.Minecraft::getInstance).thenReturn(clientWithFont());
            prefs.when(com.nstut.simplyspeakers.client.ui.SimplySpeakersUiPreferences::getThemeMode)
                    .thenReturn(com.nstut.simplyspeakers.client.ui.UiThemeMode.DARK);
            screen=new com.nstut.simplyspeakers.client.screens.SpeakerScreen(net.minecraft.core.BlockPos.ZERO);
        }
        screen.width=400;screen.height=300;
        var factory=screen.getClass().getDeclaredMethod("buildTrustedPlayersDialog",Runnable.class,Runnable.class);
        factory.setAccessible(true);
        var body=(UIComponent)factory.invoke(screen,(Runnable)() -> {},(Runnable)() -> {});
        var font=mock(Font.class);
        when(font.split(any(net.minecraft.network.chat.FormattedText.class),anyInt()))
                .thenReturn(java.util.List.of(net.minecraft.util.FormattedCharSequence.EMPTY));
        var runtime=new UiRuntime(font,mock(NativeWidgetHost.class));
        try {
            runtime.setRoot(body);runtime.setViewport(0,0,320,240);runtime.flushFrameTasks();
            assertFalse(hasVirtualList(body),"Empty trusted lists must not reserve a scroll viewport");
            assertTrue(body.preferredHeight(font)<160,"Empty state should fit a compact dialog");
            var id=java.util.UUID.randomUUID();
            screen.updateAccessModel(new com.nstut.simplyspeakers.permissions.AccessViewSnapshot(id,"Owner",
                com.nstut.simplyspeakers.SpeakerAccess.TRUSTED,
                java.util.List.of(new com.nstut.simplyspeakers.permissions.AccessViewSnapshot.Player(id,"Player")),true,true,false));
            runtime.flushFrameTasks();
            assertTrue(hasVirtualList(body),"Trusted members must remain visible after sync");
            screen.updateAccessModel(com.nstut.simplyspeakers.permissions.AccessViewSnapshot.EMPTY);
            runtime.flushFrameTasks();
            assertFalse(hasVirtualList(body),"Removing the last trusted member must restore the compact state");
        } finally {runtime.close();}
    }
    @Test void addingFirstPlaylistTrackMountsRowsWithoutRebuildingScreen() throws Exception {
        com.nstut.simplyspeakers.client.screens.SpeakerScreen screen;
        try (var prefs=mockStatic(com.nstut.simplyspeakers.client.ui.SimplySpeakersUiPreferences.class);
             var minecraft=mockStatic(net.minecraft.client.Minecraft.class)) {
            minecraft.when(net.minecraft.client.Minecraft::getInstance).thenReturn(clientWithFont());
            prefs.when(com.nstut.simplyspeakers.client.ui.SimplySpeakersUiPreferences::getThemeMode)
                    .thenReturn(com.nstut.simplyspeakers.client.ui.UiThemeMode.DARK);
            screen=new com.nstut.simplyspeakers.client.screens.SpeakerScreen(net.minecraft.core.BlockPos.ZERO);
        }
        screen.width=400;screen.height=300;
        screen.updatePlaylistCatalog(com.nstut.simplyspeakers.playlist.PlaylistLibrarySnapshot.single(java.util.List.of(),java.util.List.of()));
        var factory=screen.getClass().getDeclaredMethod("buildPlaylistView");factory.setAccessible(true);
        var body=(UIComponent)factory.invoke(screen);
        var font=mock(Font.class);
        when(font.split(any(net.minecraft.network.chat.FormattedText.class),anyInt()))
                .thenReturn(java.util.List.of(net.minecraft.util.FormattedCharSequence.EMPTY));
        var runtime=new UiRuntime(font,mock(NativeWidgetHost.class));
        try {
            runtime.setRoot(body);runtime.setViewport(0,0,320,180);runtime.flushFrameTasks();
            assertFalse(hasVirtualList(body));
            screen.updatePlaylistCatalog(com.nstut.simplyspeakers.playlist.PlaylistLibrarySnapshot.single(java.util.List.of("clip"),java.util.List.of("clip.wav")));
            runtime.flushFrameTasks();
            assertTrue(hasVirtualList(body),"First authoritative addition must mount usable track rows");
            screen.updatePlaylistCatalog(com.nstut.simplyspeakers.playlist.PlaylistLibrarySnapshot.single(java.util.List.of(),java.util.List.of()));
            runtime.flushFrameTasks();
            assertFalse(hasVirtualList(body),"Removing the last track must restore the empty state");
        } finally {runtime.close();}
    }

    @Test void transportPacketsRefreshPlayPauseIconWithoutPlaylistSync() throws Exception {
        try (var prefs=mockStatic(com.nstut.simplyspeakers.client.ui.SimplySpeakersUiPreferences.class);
             var minecraft=mockStatic(net.minecraft.client.Minecraft.class)) {
            minecraft.when(net.minecraft.client.Minecraft::getInstance).thenReturn(clientWithFont());
            prefs.when(com.nstut.simplyspeakers.client.ui.SimplySpeakersUiPreferences::getThemeMode)
                    .thenReturn(com.nstut.simplyspeakers.client.ui.UiThemeMode.DARK);
            var screen=new com.nstut.simplyspeakers.client.screens.SpeakerScreen(net.minecraft.core.BlockPos.ZERO);
            var factory=screen.getClass().getDeclaredMethod("playPauseButton");factory.setAccessible(true);
            var button=factory.invoke(screen);
            var field=com.nstut.simplyspeakers.client.ui.SpeakerIconButton.class.getDeclaredField("icon");field.setAccessible(true);
            var icon=(java.util.function.Supplier<?>)field.get(button);
            var pausedField=screen.getClass().getDeclaredField("paused");pausedField.setAccessible(true);
            var paused=(com.nstut.openui.state.Signal<?>)pausedField.get(screen);
            var hintField=com.nstut.simplyspeakers.client.ui.SpeakerIconButton.class.getDeclaredField("hint");hintField.setAccessible(true);
            var hint=(java.util.function.Supplier<?>)hintField.get(button);
            var positionField=screen.getClass().getDeclaredField("position");positionField.setAccessible(true);
            @SuppressWarnings("unchecked") var position=(com.nstut.openui.state.Signal<Double>)positionField.get(screen);
            boolean running=false;
            for(String action:new String[]{"play","update","pause","update","play","stop","update"}) {
                position.set(12.5);
                screen.refreshFromState(new com.nstut.simplyspeakers.network.SpeakerStateUpdatePacketS2C(net.minecraft.core.BlockPos.ZERO,"test",action,"clip","clip.wav",0,false));
                if(!action.equals("update"))running=action.equals("play");
                assertEquals(running?com.nstut.simplyspeakers.client.ui.SpeakerIconButton.Icon.PAUSE:com.nstut.simplyspeakers.client.ui.SpeakerIconButton.Icon.PLAY,icon.get(),action);
                assertEquals("gui.simplyspeakers.player."+(running?"pause":"play"),((Component)hint.get()).getString());
                assertEquals(action.equals("stop")?0:12.5,position.get(),.001,action);
                if(!action.equals("update"))assertEquals(action.equals("pause"),paused.get(),action);
            }
        }
    }

    @Test void queueClearActionAppearsOnlyWhileTemporaryRequestsExist() throws Exception {
        try (var prefs=mockStatic(com.nstut.simplyspeakers.client.ui.SimplySpeakersUiPreferences.class);
             var minecraft=mockStatic(net.minecraft.client.Minecraft.class)) {
            minecraft.when(net.minecraft.client.Minecraft::getInstance).thenReturn(clientWithFont());
            prefs.when(com.nstut.simplyspeakers.client.ui.SimplySpeakersUiPreferences::getThemeMode)
                    .thenReturn(com.nstut.simplyspeakers.client.ui.UiThemeMode.DARK);
            var screen=new com.nstut.simplyspeakers.client.screens.SpeakerScreen(net.minecraft.core.BlockPos.ZERO);
            var factory=screen.getClass().getDeclaredMethod("buildQueueView");factory.setAccessible(true);
            var body=(UIComponent)factory.invoke(screen);
            var queueField=screen.getClass().getDeclaredField("queueIds");queueField.setAccessible(true);
            @SuppressWarnings("unchecked") var queue=(com.nstut.openui.state.Signal<java.util.List<String>>)queueField.get(screen);
            var font=mock(Font.class);
            when(font.split(any(net.minecraft.network.chat.FormattedText.class),anyInt()))
                    .thenReturn(java.util.List.of(net.minecraft.util.FormattedCharSequence.EMPTY));
            var runtime=new UiRuntime(font,mock(NativeWidgetHost.class));
            try {
                runtime.setRoot(body);runtime.setViewport(0,0,280,120);runtime.flushFrameTasks();
                assertEquals(0,buttonCount(body),"An empty queue has no Clear action");
                queue.set(java.util.List.of("clip"));runtime.flushFrameTasks();
                assertEquals(1,buttonCount(body),"Clear must appear after sync");
                queue.set(java.util.List.of());runtime.flushFrameTasks();
                assertEquals(0,buttonCount(body),"Clearing the last request removes its obsolete action");
            } finally {runtime.close();}
        }
    }

    @Test void settingsScrollbarCapturesDraggingOutsideItsPanel() throws Exception {
        try (var prefs=mockStatic(com.nstut.simplyspeakers.client.ui.SimplySpeakersUiPreferences.class);
             var minecraft=mockStatic(net.minecraft.client.Minecraft.class)) {
            minecraft.when(net.minecraft.client.Minecraft::getInstance).thenReturn(clientWithFont());
            prefs.when(com.nstut.simplyspeakers.client.ui.SimplySpeakersUiPreferences::getThemeMode)
                    .thenReturn(com.nstut.simplyspeakers.client.ui.UiThemeMode.DARK);
            var screen=new com.nstut.simplyspeakers.client.screens.SpeakerScreen(net.minecraft.core.BlockPos.ZERO);
            var speakerField=screen.getClass().getDeclaredField("speaker");speakerField.setAccessible(true);
            speakerField.set(screen,mock(com.nstut.simplyspeakers.blocks.entities.SpeakerBlockEntity.class));
            var factory=screen.getClass().getDeclaredMethod("buildSettingsView");factory.setAccessible(true);
            var scroll=(com.nstut.openui.controls.ScrollView)factory.invoke(screen);
            var font=mock(Font.class);
            when(font.split(any(net.minecraft.network.chat.FormattedText.class),anyInt()))
                    .thenReturn(java.util.List.of(net.minecraft.util.FormattedCharSequence.EMPTY));
            var runtime=new UiRuntime(font,mock(NativeWidgetHost.class));
            try {
                runtime.setRoot(scroll);runtime.setViewport(0,0,280,120);runtime.flushFrameTasks();
                assertTrue(runtime.mouseClicked(279,2,0));
                assertTrue(runtime.mouseDragged(330,180,0,51,178));
                assertTrue(scroll.scrollOffset()>0);
                assertTrue(runtime.mouseReleased(330,180,0));
                double offset=scroll.scrollOffset();
                runtime.mouseDragged(330,-20,0,0,-200);
                assertEquals(offset,scroll.scrollOffset());
            } finally {runtime.close();}
        }
    }

    @Test void addAudioUsesOneEntryPointAndHidesUrlImportWhenDisabled() throws Exception {
        boolean previous=com.nstut.simplyspeakers.Config.disableUpload;com.nstut.simplyspeakers.Config.disableUpload=false;
        try (var prefs=mockStatic(com.nstut.simplyspeakers.client.ui.SimplySpeakersUiPreferences.class);
             var minecraft=mockStatic(net.minecraft.client.Minecraft.class)) {
            minecraft.when(net.minecraft.client.Minecraft::getInstance).thenReturn(clientWithFont());
            prefs.when(com.nstut.simplyspeakers.client.ui.SimplySpeakersUiPreferences::getThemeMode)
                    .thenReturn(com.nstut.simplyspeakers.client.ui.UiThemeMode.DARK);
            var screen=new com.nstut.simplyspeakers.client.screens.SpeakerScreen(net.minecraft.core.BlockPos.ZERO);
            var optionsFactory=screen.getClass().getDeclaredMethod("buildAddAudioOptions",Runnable.class);optionsFactory.setAccessible(true);
            var options=(UIComponent)optionsFactory.invoke(screen,(Runnable)() -> {});
            var toolbarFactory=screen.getClass().getDeclaredMethod("buildAudioToolbar");toolbarFactory.setAccessible(true);
            var toolbar=(UIComponent)toolbarFactory.invoke(screen);
            var font=mock(Font.class);
            when(font.split(any(net.minecraft.network.chat.FormattedText.class),anyInt()))
                    .thenReturn(java.util.List.of(net.minecraft.util.FormattedCharSequence.EMPTY));
            var runtime=new UiRuntime(font,mock(NativeWidgetHost.class));
            try {
                var body=Ui.column(toolbar,options);runtime.setRoot(body);runtime.setViewport(0,0,320,240);
                body.layoutTree(font,0,0,320,240);
                assertNotNull(findKey(body,"library.add_audio"));assertNull(findKey(body,"library.add_stream"));
                assertNotNull(findKey(body,"library.upload_file"));assertNull(findKey(body,"library.direct_url"));
                screen.updateAccessModel(new com.nstut.simplyspeakers.permissions.AccessViewSnapshot(null,"",
                        com.nstut.simplyspeakers.SpeakerAccess.PUBLIC,java.util.List.of(),true,true,true));
                runtime.flushFrameTasks();body.layoutTree(font,0,0,320,240);
                assertNotNull(findKey(body,"library.direct_url"));
                screen.updateAccessModel(com.nstut.simplyspeakers.permissions.AccessViewSnapshot.EMPTY);
                runtime.flushFrameTasks();body.layoutTree(font,0,0,320,240);
                assertNull(findKey(body,"library.direct_url"),"Revoking stream policy hides the URL option immediately");
            } finally {runtime.close();}
            var formFactory=screen.getClass().getDeclaredMethod("buildRemoteAudioForm",Runnable.class);formFactory.setAccessible(true);
            var form=(UIComponent)formFactory.invoke(screen,(Runnable)() -> {});
            assertEquals(2,allButtons(form),"Import only offers Add to Library and Cancel");
            assertNotNull(findKey(form,"library.url"));assertNotNull(findKey(form,"library.url_name"));
            assertNotNull(findKey(form,"library.save_url"));
        } finally {com.nstut.simplyspeakers.Config.disableUpload=previous;}
    }
    private UIComponent findKey(UIComponent node,String key) {
        if(key.equals(node.key()))return node;
        for(var child:node.children()){var found=findKey(child,key);if(found!=null)return found;}
        return null;
    }
    private long allButtons(UIComponent node) {
        return (node instanceof com.nstut.openui.api.ButtonWidget ? 1 : 0)
                +node.children().stream().mapToLong(this::allButtons).sum();
    }

    private net.minecraft.client.Minecraft clientWithFont() {
        var client=mock(net.minecraft.client.Minecraft.class);
        try {
            var field=net.minecraft.client.Minecraft.class.getDeclaredField("font");field.setAccessible(true);
            field.set(client,mock(Font.class, invocation -> {
                if (invocation.getMethod().getReturnType()==String.class && invocation.getArguments().length>0
                        && invocation.getArgument(0) instanceof String text) return text;
                return org.mockito.Answers.RETURNS_DEFAULTS.answer(invocation);
            }));
        } catch(ReflectiveOperationException error) {throw new AssertionError(error);}
        return client;
    }

    private long buttonCount(UIComponent node) {
        return ("queue.clear".equals(node.key()) ? 1 : 0)
                + node.children().stream().mapToLong(this::buttonCount).sum();
    }

    private boolean hasVirtualList(UIComponent node) {
        return node.getClass().getSimpleName().contains("VirtualList") || node.children().stream().anyMatch(this::hasVirtualList);
    }

}
