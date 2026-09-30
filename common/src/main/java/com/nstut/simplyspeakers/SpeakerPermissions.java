package com.nstut.simplyspeakers;

import java.util.UUID;

/**
 * Pure permission checks shared by packet handlers, commands, and the Java API.
 * Packet-level security (distance/chunks/block entity validity) remains in each
 * version module's SpeakerPacketSecurity.
 */
public final class SpeakerPermissions {

    private SpeakerPermissions() {
    }

    /**
     * Who is invoking a control operation. {@link #PLAYER} carries the acting player so
     * ownership/access rules can be enforced; {@link #AUTOMATION} is untrusted external
     * automation (ComputerCraft, other mods) which may only touch public speakers;
     * {@link #SYSTEM} is privileged internal code (GUI packets already authenticated the
     * player, the sleep/wake manager, etc.).
     */
    public enum ControlActor {
        PLAYER,
        AUTOMATION,
        SYSTEM
    }

    /** May untrusted automation (CC:Tweaked, etc.) control this speaker's transport/settings? */
    public static boolean canAutomationControl(SpeakerState state) {
        return canControl(state, null, false);
    }

    /** May untrusted automation re-link, rename, or re-own this network? */
    public static boolean canAutomationManage(SpeakerState state) {
        return canManage(state, null, false);
    }

    /** May the player change playback or settings on this speaker state? */
    public static boolean canControl(SpeakerState state, UUID playerUuid, boolean isOperator) {
        if (state == null) return false;
        if (isOperator || state.getOwnerUuid() == null) return true;
        if (playerUuid == null) {
            // Anonymous automation may only touch public speakers.
            return state.getAccessMode() == SpeakerAccess.PUBLIC;
        }
        switch (state.getAccessMode()) {
            case PUBLIC:
                return true;
            case OPERATORS:
                return false;
            case OWNER_ONLY:
                return state.getOwnerUuid().equals(playerUuid);
            case TRUSTED:
                return state.getOwnerUuid().equals(playerUuid) || state.isTrusted(playerUuid);
            default:
                return false;
        }
    }

    /** May the player re-link, delete, or re-own this network? Stricter than control. */
    public static boolean canManage(SpeakerState state, UUID playerUuid, boolean isOperator) {
        if (state == null) return false;
        if (isOperator) return true;
        if (state.getOwnerUuid() == null) return true;
        return state.getOwnerUuid().equals(playerUuid);
    }

    /** May the player listen? Open in 0.8.x; range already gates audibility. */
    public static boolean canListen(SpeakerState state, UUID playerUuid) {
        return true;
    }
}
