package com.matoon.herosmp.network;

import com.matoon.herosmp.client.gui.GuiPvpMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;
import java.util.List;

public class PacketOpenPvpMenu implements IMessage {

    private List<ActiveMatchEntry> matches;
    private boolean queued;

    public PacketOpenPvpMenu() {
        this.matches = new ArrayList<ActiveMatchEntry>();
        this.queued = false;
    }

    public PacketOpenPvpMenu(List<ActiveMatchEntry> matches, boolean queued) {
        this.matches = matches;
        this.queued = queued;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.queued = buf.readBoolean();
        int size = buf.readInt();
        this.matches = new ArrayList<ActiveMatchEntry>(size);
        for (int i = 0; i < size; i++) {
            int id = buf.readInt();
            String first = ByteBufUtils.readUTF8String(buf);
            String second = ByteBufUtils.readUTF8String(buf);
            String status = ByteBufUtils.readUTF8String(buf);
            this.matches.add(new ActiveMatchEntry(id, first, second, status));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(queued);
        buf.writeInt(matches.size());
        for (ActiveMatchEntry entry : matches) {
            buf.writeInt(entry.matchId);
            ByteBufUtils.writeUTF8String(buf, entry.firstPlayer);
            ByteBufUtils.writeUTF8String(buf, entry.secondPlayer);
            ByteBufUtils.writeUTF8String(buf, entry.status);
        }
    }

    public static class Handler implements IMessageHandler<PacketOpenPvpMenu, IMessage> {
        @Override
        public IMessage onMessage(PacketOpenPvpMenu message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    Minecraft.getMinecraft().displayGuiScreen(new GuiPvpMenu(message.matches, message.queued));
                }
            });
            return null;
        }
    }

    public static class ActiveMatchEntry {
        public final int matchId;
        public final String firstPlayer;
        public final String secondPlayer;
        public final String status;

        public ActiveMatchEntry(int matchId, String firstPlayer, String secondPlayer, String status) {
            this.matchId = matchId;
            this.firstPlayer = firstPlayer;
            this.secondPlayer = secondPlayer;
            this.status = status;
        }
    }
}
