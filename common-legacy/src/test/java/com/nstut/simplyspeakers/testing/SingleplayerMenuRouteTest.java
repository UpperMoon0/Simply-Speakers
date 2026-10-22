package com.nstut.simplyspeakers.testing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.nstut.simplyspeakers.testing.SingleplayerMenuRoute.Action.*;
import static com.nstut.simplyspeakers.testing.SingleplayerMenuRoute.Screen.*;

public class SingleplayerMenuRouteTest {
    @Test public void ordinaryEnabledMenuRouteReachesOnlyIntegratedWorld() {
        SingleplayerMenuRoute route = new SingleplayerMenuRoute();
        assertEquals(WAIT, route.next(TITLE, true, false, false));
        assertEquals(OPEN_SINGLEPLAYER, route.next(TITLE, false, false, false));
        route.clicked(OPEN_SINGLEPLAYER, true, true);
        assertEquals(OPEN_CREATE_WORLD, route.next(SELECT_WORLD, false, false, false));
        route.clicked(OPEN_CREATE_WORLD, true, true);
        assertEquals(SingleplayerMenuRoute.Action.CREATE_WORLD, route.next(SingleplayerMenuRoute.Screen.CREATE_WORLD, false, false, false));
        route.clicked(SingleplayerMenuRoute.Action.CREATE_WORLD, true, true);
        assertEquals(WAIT, route.next(LOADING, false, false, true));
        assertEquals(READY, route.next(OTHER, false, true, true));
    }
    @Test public void emptySaveListCanOpenNativeCreationScreenDirectly() {
        SingleplayerMenuRoute route = new SingleplayerMenuRoute();
        route.clicked(OPEN_SINGLEPLAYER, true, true);
        assertEquals(SingleplayerMenuRoute.Action.CREATE_WORLD, route.next(SingleplayerMenuRoute.Screen.CREATE_WORLD, false, false, false));
        route.clicked(SingleplayerMenuRoute.Action.CREATE_WORLD, true, true);
        assertEquals(READY, route.next(OTHER, false, true, true));
    }
    @Test public void disabledOrHiddenControlsNeverAdvance() {
        for (boolean[] state : new boolean[][] {{false,true},{true,false},{false,false}}) {
            SingleplayerMenuRoute route = new SingleplayerMenuRoute();
            assertThrows(IllegalStateException.class, () -> route.clicked(OPEN_SINGLEPLAYER,state[0],state[1]));
            assertEquals(OPEN_SINGLEPLAYER, route.next(TITLE,false,false,false));
        }
    }
    @Test public void remoteExistingOrUnexpectedWorldsCannotPass() {
        assertThrows(IllegalStateException.class, () -> new SingleplayerMenuRoute().next(TITLE,false,true,true));
        SingleplayerMenuRoute route = new SingleplayerMenuRoute();
        route.clicked(OPEN_SINGLEPLAYER,true,true);route.clicked(SingleplayerMenuRoute.Action.CREATE_WORLD,true,true);
        assertThrows(IllegalStateException.class, () -> route.next(OTHER,false,true,false));
        assertThrows(IllegalStateException.class, () -> new SingleplayerMenuRoute().next(OTHER,false,false,false));
    }
    @Test public void repeatedOrOutOfOrderInputCannotCreateAnotherWorld() {
        SingleplayerMenuRoute route = new SingleplayerMenuRoute();
        assertThrows(IllegalStateException.class, () -> route.clicked(SingleplayerMenuRoute.Action.CREATE_WORLD,true,true));
        route.clicked(OPEN_SINGLEPLAYER,true,true);
        assertThrows(IllegalStateException.class, () -> route.clicked(OPEN_SINGLEPLAYER,true,true));
        route.clicked(SingleplayerMenuRoute.Action.CREATE_WORLD,true,true);
        assertThrows(IllegalStateException.class, () -> route.clicked(SingleplayerMenuRoute.Action.CREATE_WORLD,true,true));
    }
}
