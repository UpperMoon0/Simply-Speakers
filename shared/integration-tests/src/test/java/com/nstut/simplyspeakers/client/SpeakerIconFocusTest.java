package com.nstut.simplyspeakers.client;
import com.nstut.simplyspeakers.client.ui.SpeakerIconButton;
import com.nstut.openui.runtime.UiRuntime;
import com.nstut.openui.runtime.NativeWidgetHost;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class SpeakerIconFocusTest {
    @Test void pointerClicksDoNotLeaveARepeatOutlineButKeyboardFocusStillWorks() {
        var presses=new AtomicInteger();var button=new SpeakerIconButton(SpeakerIconButton.Icon.REPEAT,Component.literal("Repeat: off"),presses::incrementAndGet);
        var runtime=new UiRuntime(mock(Font.class),mock(NativeWidgetHost.class));
        try {
            runtime.setRoot(button);runtime.setViewport(0,0,100,100);runtime.flushFrameTasks();runtime.mouseScrolled(4,4,0);
            assertTrue(runtime.mouseClicked(4,4,0));assertEquals(1,presses.get());assertFalse(button.isFocused());
            runtime.mouseReleased(90,90,0);assertFalse(button.isFocused());
            button.requestFocus();assertTrue(button.isFocused());assertTrue(runtime.keyPressed(257,0,0));assertEquals(2,presses.get());assertTrue(button.isFocused());
        } finally { runtime.close(); }
    }
}
