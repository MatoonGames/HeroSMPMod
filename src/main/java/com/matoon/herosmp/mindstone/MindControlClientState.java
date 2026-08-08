package com.matoon.herosmp.mindstone;

import com.matoon.herosmp.network.PacketMindControlEntries;
import java.util.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

@SideOnly(Side.CLIENT)
public final class MindControlClientState {
    private static List<PacketMindControlEntries.Entry> entries = Collections.emptyList();
    private static int revision;
    private MindControlClientState() {}
    public static void set(List<PacketMindControlEntries.Entry> incoming) { entries = Collections.unmodifiableList(new ArrayList<>(incoming)); revision++; }
    public static List<PacketMindControlEntries.Entry> get() { return entries; }
    public static boolean contains(UUID id) { for (PacketMindControlEntries.Entry entry : entries) if (entry.id.equals(id)) return true; return false; }
    public static int revision() { return revision; }
}
