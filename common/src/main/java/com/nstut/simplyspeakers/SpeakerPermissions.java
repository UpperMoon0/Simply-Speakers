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
