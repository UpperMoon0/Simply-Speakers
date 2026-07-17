package com.nstut.simplyspeakers.audio;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

/** Version-neutral ownership rules for private player audio libraries. */
public final class AudioOwnership {
    private AudioOwnership() {
    }

    public static boolean isOwnedBy(String ownerUUID, String playerUUID) {
        return ownerUUID != null && playerUUID != null && ownerUUID.equals(playerUUID);
    }

    public static <T> List<T> ownedBy(
            Collection<T> entries, Function<T, String> ownerUUID, String playerUUID) {
        return entries.stream()
                .filter(entry -> isOwnedBy(ownerUUID.apply(entry), playerUUID))
                .toList();
    }
}
