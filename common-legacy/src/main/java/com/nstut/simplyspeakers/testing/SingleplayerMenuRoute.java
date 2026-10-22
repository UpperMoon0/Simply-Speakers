package com.nstut.simplyspeakers.testing;

/** Input-only route through ordinary vanilla menus; never creates or joins a world. */
public final class SingleplayerMenuRoute {
    public enum Screen { TITLE, SELECT_WORLD, CREATE_WORLD, LOADING, OTHER }
    public enum Action { WAIT, OPEN_SINGLEPLAYER, OPEN_CREATE_WORLD, CREATE_WORLD, READY }
    private int phase;

    public Action next(Screen screen, boolean overlay, boolean worldPresent, boolean integratedServer) {
        if (worldPresent) {
            if (phase != 3 || !integratedServer) throw new IllegalStateException("World did not come from the permitted vanilla creation route");
            phase = 4;
            return Action.READY;
        }
        if (phase == 4) throw new IllegalStateException("Created integrated world disappeared before acceptance completed");
        if (overlay) return Action.WAIT;
        if (phase == 0 && screen == Screen.TITLE) return Action.OPEN_SINGLEPLAYER;
        if (phase == 1 && screen == Screen.SELECT_WORLD) return Action.OPEN_CREATE_WORLD;
        if ((phase == 1 || phase == 2) && screen == Screen.CREATE_WORLD) return Action.CREATE_WORLD;
        if (screen == Screen.LOADING || (phase == 1 && screen == Screen.TITLE) ||
                (phase == 2 && screen == Screen.SELECT_WORLD) || (phase == 3 && screen == Screen.CREATE_WORLD)) return Action.WAIT;
        throw new IllegalStateException("Unexpected vanilla menu at creation phase " + phase + ": " + screen);
    }

    public void clicked(Action action, boolean enabled, boolean visible) {
        if (!enabled || !visible) throw new IllegalStateException("Required vanilla control is disabled or hidden");
        if (phase == 0 && action == Action.OPEN_SINGLEPLAYER) phase = 1;
        else if (phase == 1 && action == Action.OPEN_CREATE_WORLD) phase = 2;
        else if ((phase == 1 || phase == 2) && action == Action.CREATE_WORLD) phase = 3;
        else throw new IllegalStateException("Out-of-order vanilla control activation: " + action);
    }
}
