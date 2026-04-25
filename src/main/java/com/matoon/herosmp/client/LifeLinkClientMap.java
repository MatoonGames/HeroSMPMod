package com.matoon.herosmp.client;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side mirror of the Life Link relationship map.
 *
 * Populated by {@link com.matoon.herosmp.network.PacketLifeLink} packets sent
 * from the server. Only accessed on the client thread.
 *
 * Key   = UUID of the entity with the Life Link potion (the "linked" entity)
 * Value = UUID of the entity that cast the link (the "linker")
 */
@SideOnly(Side.CLIENT)
public final class LifeLinkClientMap {

    private static final Map<UUID, UUID> LINKS = new HashMap<>();

    private LifeLinkClientMap() {
    }

    public static void addLink(UUID linked, UUID linker) {
        LINKS.put(linked, linker);
    }

    public static void removeLink(UUID linked) {
        LINKS.remove(linked);
    }

    public static void clear() {
        LINKS.clear();
    }

    /** Returns an unmodifiable view of all current client-side links. */
    public static Map<UUID, UUID> getLinks() {
        return Collections.unmodifiableMap(LINKS);
    }
}
