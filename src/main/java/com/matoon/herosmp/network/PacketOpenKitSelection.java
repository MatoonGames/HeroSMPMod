package com.matoon.herosmp.network;

import com.matoon.herosmp.HeroSMP;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Opens the fighter-select screen at the start of a PVP round: every pickable kit with the gear
 * its 3D figure wears, the mode, how long players have to pick, and the music to play (track names
 * from the server's KitSelect / Fight music folders, empty when there are none).
 */
public class PacketOpenKitSelection implements IMessage {

    public List<KitEntry> kits;
    public String mode = "";
    public int seconds;
    public String musicTrack = "";
    public String fightTrack = "";

    public PacketOpenKitSelection() {
        this.kits = new ArrayList<KitEntry>();
    }

    public PacketOpenKitSelection(List<KitEntry> kits, String mode, int seconds, String musicTrack, String fightTrack) {
        this.kits = kits;
        this.mode = mode == null ? "" : mode;
        this.seconds = seconds;
        this.musicTrack = musicTrack == null ? "" : musicTrack;
        this.fightTrack = fightTrack == null ? "" : fightTrack;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        mode = ByteBufUtils.readUTF8String(buf);
        seconds = buf.readInt();
        musicTrack = ByteBufUtils.readUTF8String(buf);
        fightTrack = ByteBufUtils.readUTF8String(buf);
        int size = buf.readInt();
        this.kits = new ArrayList<KitEntry>(size);
        for (int i = 0; i < size; i++) {
            String key = ByteBufUtils.readUTF8String(buf);
            String name = ByteBufUtils.readUTF8String(buf);
            ItemStack icon = ByteBufUtils.readItemStack(buf);
            ItemStack[] gear = new ItemStack[KitEntry.GEAR_SLOTS];
            for (int g = 0; g < gear.length; g++) gear[g] = ByteBufUtils.readItemStack(buf);
            int itemCount = buf.readInt();
            int extras = buf.readByte();
            List<ItemStack> hotbar = new ArrayList<ItemStack>(extras);
            for (int h = 0; h < extras; h++) hotbar.add(ByteBufUtils.readItemStack(buf));
            String powerName = ByteBufUtils.readUTF8String(buf);
            String missing = ByteBufUtils.readUTF8String(buf);
            this.kits.add(new KitEntry(key, name, icon, gear, itemCount, hotbar, powerName, missing));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, mode);
        buf.writeInt(seconds);
        ByteBufUtils.writeUTF8String(buf, musicTrack);
        ByteBufUtils.writeUTF8String(buf, fightTrack);
        buf.writeInt(kits.size());
        for (KitEntry entry : kits) {
            ByteBufUtils.writeUTF8String(buf, entry.key);
            ByteBufUtils.writeUTF8String(buf, entry.displayName);
            ByteBufUtils.writeItemStack(buf, entry.icon);
            for (ItemStack g : entry.gear) ByteBufUtils.writeItemStack(buf, g);
            buf.writeInt(entry.itemCount);
            buf.writeByte(entry.hotbar.size());
            for (ItemStack h : entry.hotbar) ByteBufUtils.writeItemStack(buf, h);
            ByteBufUtils.writeUTF8String(buf, entry.powerName);
            ByteBufUtils.writeUTF8String(buf, entry.missing);
        }
    }

    public static class Handler implements IMessageHandler<PacketOpenKitSelection, IMessage> {
        @Override
        public IMessage onMessage(PacketOpenKitSelection message, MessageContext ctx) {
            HeroSMP.proxy.handleClientPacket(message);
            return null;
        }
    }

    public static class KitEntry {
        /** head, chest, legs, feet, main hand, off hand: what the figure wears. */
        public static final int GEAR_SLOTS = 6;
        public static final int MAX_HOTBAR = 9;

        public final String key;
        public final String displayName;
        public final ItemStack icon;
        public final ItemStack[] gear;
        public final int itemCount;
        /** Non-empty hotbar items, shown as the kit's "moveset". */
        public final List<ItemStack> hotbar;
        /** The kit's superpower ("" for none). */
        public final String powerName;
        /** Mods / Hero Addons the server is missing for this kit ("" when it loads fully). */
        public final String missing;

        public KitEntry(String key, String displayName, ItemStack icon, ItemStack[] gear, int itemCount,
                        List<ItemStack> hotbar, String powerName, String missing) {
            this.powerName = powerName == null ? "" : powerName;
            this.missing = missing == null ? "" : missing;
            this.key = key;
            this.displayName = displayName;
            this.icon = icon == null ? ItemStack.EMPTY : icon.copy();
            this.gear = new ItemStack[GEAR_SLOTS];
            for (int i = 0; i < GEAR_SLOTS; i++) {
                ItemStack s = gear != null && i < gear.length ? gear[i] : null;
                this.gear[i] = s == null ? ItemStack.EMPTY : s.copy();
            }
            this.itemCount = itemCount;
            List<ItemStack> h = new ArrayList<ItemStack>();
            if (hotbar != null) for (ItemStack s : hotbar) if (s != null && !s.isEmpty() && h.size() < MAX_HOTBAR) h.add(s.copy());
            this.hotbar = h;
        }
    }
}
