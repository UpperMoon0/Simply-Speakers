package com.nstut.simplyspeakers.testing;

/** Client-thread-only diagnostic state. Never owns or changes a native screen. */
public final class JoinWaitDiagnostics {
    private String previous;

    /** Returns only changed class identities and native permission state, never screen contents. */
    public String update(boolean connected, Object screen, Object overlay, boolean multiplayerAllowed) {
        if (connected) {
            previous = null;
            return null;
        }
        String state = "screen=" + identity(screen) + ", overlay=" + identity(overlay) + ", multiplayerAllowed=" + multiplayerAllowed;
        if (state.equals(previous)) return null;
        previous = state;
        return state;
    }

    private static String identity(Object value) {
        return value == null ? "none" : value.getClass().getName();
    }
}
