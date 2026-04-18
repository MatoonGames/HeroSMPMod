package com.matoon.herosmp.network;

import com.matoon.herosmp.client.gui.GuiKitSelection;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

public class PacketOpenKitSelection implements IMessage {

    private List<KitEntry> kits;

    public PacketOpenKitSelection() {
        this.kits = new ArrayList<KitEntry>();
    }

    public PacketOpenKitSelection(List<KitEntry> kits) {
        this.kits = kits;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        int size = buf.readInt();
        this.kits = new ArrayList<KitEntry>(size);
        for (int i = 0; i < size; i++) {
            String key = ByteBufUtils.readUTF8String(buf);
            String name = ByteBufUtils.readUTF8String(buf);
            ItemStack icon = ByteBufUtils.readItemStack(buf);
            this.kits.add(new KitEntry(key, name, icon));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(kits.size());
        for (KitEntry entry : kits) {
            ByteBufUtils.writeUTF8String(buf, entry.key);
            ByteBufUtils.writeUTF8String(buf, entry.displayName);
            ByteBufUtils.writeItemStack(buf, entry.icon);
        }
    }

    public static class Handler implements IMessageHandler<PacketOpenKitSelection, IMessage> {
        @Override
        public IMessage onMessage(PacketOpenKitSelection message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    Minecraft.getMinecraft().displayGuiScreen(new GuiKitSelection(message.kits));
                }
            });
            return null;
        }
    }

    public static class KitEntry {
        public final String key;
        public final String displayName;
        public final ItemStack icon;

        public KitEntry(String key, String displayName, ItemStack icon) {
            this.key = key;
            this.displayName = displayName;
            this.icon = icon == null ? ItemStack.EMPTY : icon.copy();
        }
    }
}
