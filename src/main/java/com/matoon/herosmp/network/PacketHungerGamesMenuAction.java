package com.matoon.herosmp.network;

import com.matoon.herosmp.HeroSMP;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketHungerGamesMenuAction implements IMessage {

    public enum ActionType {
        QUEUE,
        CANCEL_QUEUE,
        REFRESH
    }

    private int action;

    public PacketHungerGamesMenuAction() {
    }

    public static PacketHungerGamesMenuAction queue() {
        return new PacketHungerGamesMenuAction(ActionType.QUEUE);
    }

    public static PacketHungerGamesMenuAction cancelQueue() {
        return new PacketHungerGamesMenuAction(ActionType.CANCEL_QUEUE);
    }

    public static PacketHungerGamesMenuAction refresh() {
        return new PacketHungerGamesMenuAction(ActionType.REFRESH);
    }

    private PacketHungerGamesMenuAction(ActionType type) {
        this.action = type.ordinal();
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.action = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(action);
    }

    public static class Handler implements IMessageHandler<PacketHungerGamesMenuAction, IMessage> {
        @Override
        public IMessage onMessage(PacketHungerGamesMenuAction message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    ActionType[] values = ActionType.values();
                    if (message.action < 0 || message.action >= values.length) {
                        return;
                    }

                    ActionType type = values[message.action];
                    if (type == ActionType.QUEUE) {
                        HeroSMP.HUNGER_GAMES_MANAGER.queuePlayer(player);
                        return;
                    }
                    if (type == ActionType.CANCEL_QUEUE) {
                        HeroSMP.HUNGER_GAMES_MANAGER.dequeuePlayer(player);
                        return;
                    }
                    if (type == ActionType.REFRESH) {
                        HeroSMP.HUNGER_GAMES_MANAGER.openHungerGamesMenu(player);
                    }
                }
            });
            return null;
        }
    }
}
