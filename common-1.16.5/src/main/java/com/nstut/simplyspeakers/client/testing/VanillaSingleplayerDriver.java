package com.nstut.simplyspeakers.client.testing;

import com.nstut.simplyspeakers.testing.SingleplayerMenuRoute;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.network.chat.TranslatableComponent;

/** Opt-in normal-menu input. No lower-level world creation or connection APIs. */
public final class VanillaSingleplayerDriver {
    private static final SingleplayerMenuRoute ROUTE = new SingleplayerMenuRoute();
    private static long started;
    private static boolean ready;
    private VanillaSingleplayerDriver() {}

    public static void tick(Minecraft client) {
        if (!Boolean.getBoolean("simplyspeakers.integratedAcceptance") || ready) return;
        if (started == 0) started = System.nanoTime();
        if (System.nanoTime() - started > 300_000_000_000L) fail("normal vanilla world creation timed out");
        boolean world = client.level != null && client.player != null;
        boolean integrated = client.isLocalServer() && client.getSingleplayerServer() != null &&
                !client.getSingleplayerServer().isDedicatedServer();
        Screen screen = client.screen;
        try {
            SingleplayerMenuRoute.Action action = ROUTE.next(kind(screen), client.getOverlay() != null, world, integrated);
            if (action == SingleplayerMenuRoute.Action.WAIT) return;
            if (action == SingleplayerMenuRoute.Action.READY) {
                ready = true;
                System.out.println("SIMPLYSPEAKERS_INTEGRATED_WORLD_PASS server=" + client.getSingleplayerServer().getClass().getName());
                return;
            }
            String key = action == SingleplayerMenuRoute.Action.OPEN_SINGLEPLAYER ? "menu.singleplayer" : "selectWorld.create";
            AbstractButton control = null;
            for (GuiEventListener child : screen.children()) {
                if (!(child instanceof AbstractButton)) continue;
                AbstractButton button = (AbstractButton) child;
                if (button.getMessage() instanceof TranslatableComponent &&
                        key.equals(((TranslatableComponent) button.getMessage()).getKey())) {
                    if (control != null) fail("ambiguous vanilla control " + key);
                    control = button;
                }
            }
            if (control == null) fail("required vanilla control missing: " + key);
            System.out.println("SIMPLYSPEAKERS_INTEGRATED_MENU_CONTROL action=" + action +
                    " enabled=" + control.active + " visible=" + control.visible);
            ROUTE.clicked(action, control.active, control.visible);
            double x = control.x + control.getWidth() / 2.0, y = control.y + control.getHeight() / 2.0;
            if (!screen.mouseClicked(x, y, 0)) fail("native screen rejected vanilla pointer input");
            screen.mouseReleased(x, y, 0);
            if (action == SingleplayerMenuRoute.Action.CREATE_WORLD)
                System.out.println("SIMPLYSPEAKERS_INTEGRATED_MENU_PASS enabled=true path=singleplayer-create");
        } catch (IllegalStateException error) {
            fail(error.getMessage());
        }
    }

    private static SingleplayerMenuRoute.Screen kind(Screen screen) {
        if (screen instanceof TitleScreen) return SingleplayerMenuRoute.Screen.TITLE;
        if (screen instanceof SelectWorldScreen) return SingleplayerMenuRoute.Screen.SELECT_WORLD;
        if (screen instanceof CreateWorldScreen) return SingleplayerMenuRoute.Screen.CREATE_WORLD;
        if (screen == null) return SingleplayerMenuRoute.Screen.LOADING;
        String name = screen.getClass().getSimpleName();
        if (name.equals("LevelLoadingScreen") || name.equals("ProgressScreen") || name.equals("GenericDirtMessageScreen") ||
                name.equals("WorldLoadProgressScreen") || name.equals("WorkingScreen") || name.equals("DirtMessageScreen"))
            return SingleplayerMenuRoute.Screen.LOADING;
        return SingleplayerMenuRoute.Screen.OTHER;
    }

    private static void fail(String reason) {
        throw new IllegalStateException("SIMPLYSPEAKERS_INTEGRATED_FAIL " + reason);
    }
}
