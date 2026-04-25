package com.matoon.herosmp.network;

import com.matoon.herosmp.client.gui.GuiHungerGamesMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketOpenHungerGamesMenu implements IMessage {

    private boolean queued;
    private int queueSize;
    private int activeMatches;

    public PacketOpenHungerGamesMenu() {
    }

    public PacketOpenHungerGamesMenu(boolean queued, int queueSize, int activeMatches) {
        this.queued = queued;
        this.queueSize = queueSize;
        this.activeMatches = activeMatches;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.queued = buf.readBoolean();
        this.queueSize = buf.readInt();
        this.activeMatches = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(queued);
        buf.writeInt(queueSize);
        buf.writeInt(activeMatches);
    }

    public static class Handler implements IMessageHandler<PacketOpenHungerGamesMenu, IMessage> {
        @Override
        public IMessage onMessage(PacketOpenHungerGamesMenu message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    Minecraft.getMinecraft().displayGuiScreen(new GuiHungerGamesMenu(message.queued, message.queueSize, message.activeMatches));
                }
            });
            return null;
        }
    }
}
