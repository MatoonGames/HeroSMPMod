package com.matoon.herosmp.network;

import com.matoon.herosmp.HeroSMP;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketPvpMenuAction implements IMessage {

    public enum ActionType {
        QUICK_QUEUE,
        CANCEL_QUEUE,
        MODE_QUEUE,
        SPECTATE,
        REFRESH
    }

    private int action;
    private String modeKey;
    private int matchId;

    public PacketPvpMenuAction() {
    }

    public static PacketPvpMenuAction quickQueue() {
        return new PacketPvpMenuAction(ActionType.QUICK_QUEUE, "", -1);
    }

    public static PacketPvpMenuAction modeQueue(String modeKey) {
        return new PacketPvpMenuAction(ActionType.MODE_QUEUE, modeKey, -1);
    }

    public static PacketPvpMenuAction cancelQueue() {
        return new PacketPvpMenuAction(ActionType.CANCEL_QUEUE, "", -1);
    }

    public static PacketPvpMenuAction spectate(int matchId) {
        return new PacketPvpMenuAction(ActionType.SPECTATE, "", matchId);
    }

    public static PacketPvpMenuAction refresh() {
        return new PacketPvpMenuAction(ActionType.REFRESH, "", -1);
    }

    private PacketPvpMenuAction(ActionType type, String modeKey, int matchId) {
        this.action = type.ordinal();
        this.modeKey = modeKey == null ? "" : modeKey;
        this.matchId = matchId;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readInt();
        this.modeKey = ByteBufUtils.readUTF8String(buf);
        this.matchId = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(action);
        ByteBufUtils.writeUTF8String(buf, modeKey == null ? "" : modeKey);
        buf.writeInt(matchId);
    }

    public static class Handler implements IMessageHandler<PacketPvpMenuAction, IMessage> {
        @Override
        public IMessage onMessage(PacketPvpMenuAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    ActionType[] values = ActionType.values();
                    if (message.action < 0 || message.action >= values.length) {
                        return;
                    }

                    ActionType type = values[message.action];
                    if (type == ActionType.QUICK_QUEUE) {
                        HeroSMP.PVP_QUEUE_MANAGER.queueQuick(player);
                        return;
                    }
                    if (type == ActionType.CANCEL_QUEUE) {
                        HeroSMP.PVP_QUEUE_MANAGER.cancelQueue(player);
                        return;
                    }
                    if (type == ActionType.MODE_QUEUE) {
                        HeroSMP.PVP_QUEUE_MANAGER.queueMode(player, message.modeKey);
                        return;
                    }
                    if (type == ActionType.SPECTATE) {
                        HeroSMP.PVP_QUEUE_MANAGER.trySpectateMatch(player, message.matchId);
                        return;
                    }
                    if (type == ActionType.REFRESH) {
                        HeroSMP.PVP_QUEUE_MANAGER.openPvpMenu(player);
                    }
                }
            });
            return null;
        }
    }
}
