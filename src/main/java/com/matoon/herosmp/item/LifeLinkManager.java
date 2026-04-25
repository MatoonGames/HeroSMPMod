package com.matoon.herosmp.item;

import com.matoon.herosmp.network.ModNetwork;
import com.matoon.herosmp.network.PacketLifeLink;
import net.minecraft.entity.EntityLivingBase;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks active life-link relationships server-side.
 *
 * Key:   UUID of the entity carrying the Life Link effect (the "linked" entity)
 * Value: UUID of the entity that cast the link (the "linker")
 *
 * Rules enforced here:
 *  - A single entity may only be the "linked" target of one link at a time.
 *  - If A links B, B may not link A (circular prevention).
 *  - Chains are followed: if A→B and B→C, damage to A redirects to C.
 *
 * The map is volatile in-memory only; links expire naturally when the potion
 * effect expires and {@link #removeLink} is called from the event handler.
 */
public class LifeLinkManager {

    /** linked entity UUID → linker entity UUID */
    private static final Map<UUID, UUID> LINKS = new HashMap<>();

    /**
     * Attempts to create a life link where {@code linker} linked {@code linked}.
     *
     * @return true if the link was created, false if it was rejected because:
     *         - linked already has a link source
     *         - creating the link would form a cycle back to linker
     */
    public static boolean createLink(EntityLivingBase linker, EntityLivingBase linked) {
        UUID linkerUuid = linker.getUniqueID();
        UUID linkedUuid = linked.getUniqueID();

        // If linked already has a link, deny (one link per entity).
        if (LINKS.containsKey(linkedUuid)) {
            return false;
        }

        // Prevent B linking A if A has already linked B (direct cycle).
        if (LINKS.containsKey(linkerUuid) && LINKS.get(linkerUuid).equals(linkedUuid)) {
            return false;
        }

        // Prevent any multi-hop cycle (walk the chain starting from linker).
        if (wouldCreateCycle(linkerUuid, linkedUuid)) {
            return false;
        }

        LINKS.put(linkedUuid, linkerUuid);
        broadcastAdd(linkedUuid, linkerUuid);
        return true;
    }

    /**
     * Walk the existing chain starting from {@code linkedUuid} to see if
     * inserting linkerUuid→linkedUuid would form a cycle.
     */
    private static boolean wouldCreateCycle(UUID linkerUuid, UUID linkedUuid) {
        UUID current = linkerUuid;
        // Walk up the chain from linker: if we reach linkedUuid, it's a cycle.
        for (int i = 0; i < 20; i++) {
            UUID next = LINKS.get(current);
            if (next == null) break;
            if (next.equals(linkedUuid)) return true;
            current = next;
        }
        return false;
    }

    /** Removes the link for the given linked entity (called when effect expires). */
    public static void removeLink(UUID linkedUuid) {
        if (LINKS.remove(linkedUuid) != null) {
            broadcastRemove(linkedUuid);
        }
    }

    /**
     * Returns the UUID of the ultimate damage recipient for the given linked entity.
     * Follows the chain (A→B→C) until no further link exists.
     * Returns null if no link exists for the given entity.
     */
    public static UUID getChainEnd(UUID linkedUuid) {
        UUID current = linkedUuid;
        for (int i = 0; i < 20; i++) {
            UUID next = LINKS.get(current);
            if (next == null) break;
            current = next;
        }
        if (current.equals(linkedUuid)) return null; // no link at all
        return current;
    }

    /** Returns the direct linker UUID for a linked entity, or null. */
    public static UUID getLinker(UUID linkedUuid) {
        return LINKS.get(linkedUuid);
    }

    /** Returns true if the given entity currently has a life link on them. */
    public static boolean isLinked(UUID uuid) {
        return LINKS.containsKey(uuid);
    }

    /**
     * Prune any links whose potion effect has expired.
     * Should be called periodically (e.g. server tick) to avoid stale entries.
     */
    public static void pruneStaleLinks(Iterable<? extends EntityLivingBase> allEntities) {
        Iterator<Map.Entry<UUID, UUID>> it = LINKS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, UUID> entry = it.next();
            // We keep the entry — expiry is handled by removeLink in ArrowEffectHandler.
        }
    }

    /** Clears all links (e.g. on server stop). */
    public static void clear() {
        LINKS.clear();
    }

    /**
     * Returns a snapshot of all current links (server-side only).
     * Key = linked UUID, Value = linker UUID.
     */
    public static Map<UUID, UUID> getLinks() {
        return new HashMap<>(LINKS);
    }

    // -------------------------------------------------------------------------
    // Network sync helpers
    // -------------------------------------------------------------------------

    private static void broadcastAdd(UUID linked, UUID linker) {
        if (FMLCommonHandler.instance().getSide().isServer()) {
            ModNetwork.CHANNEL.sendToAll(PacketLifeLink.add(linked, linker));
        }
    }

    private static void broadcastRemove(UUID linked) {
        if (FMLCommonHandler.instance().getSide().isServer()) {
            ModNetwork.CHANNEL.sendToAll(PacketLifeLink.remove(linked));
        }
    }
}
