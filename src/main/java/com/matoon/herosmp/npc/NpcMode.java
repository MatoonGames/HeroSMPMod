package com.matoon.herosmp.npc;

public enum NpcMode {
    COMMAND,
    PVP_QUEUE;

    public static NpcMode fromString(String value) {
        for (NpcMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value)) {
                return mode;
            }
        }
        return null;
    }
}
