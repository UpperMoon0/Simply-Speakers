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
            minecraft.when(net.minecraft.client.Minecraft::getInstance).thenReturn(mock(net.minecraft.client.Minecraft.class));
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
            minecraft.when(net.minecraft.client.Minecraft::getInstance).thenReturn(mock(net.minecraft.client.Minecraft.class));
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
            minecraft.when(net.minecraft.client.Minecraft::getInstance).thenReturn(mock(net.minecraft.client.Minecraft.class));
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

    private boolean hasVirtualList(UIComponent node) {
        return node.getClass().getSimpleName().contains("VirtualList") || node.children().stream().anyMatch(this::hasVirtualList);
    }

}
