package com.matoon.herosmp.network;

import com.matoon.herosmp.HeroSMP;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketSelectKit implements IMessage {

    private String key;

    public PacketSelectKit() {
    }

    public PacketSelectKit(String key) {
        this.key = key;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.key = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, key == null ? "" : key);
    }

    public static class Handler implements IMessageHandler<PacketSelectKit, IMessage> {
        @Override
        public IMessage onMessage(PacketSelectKit message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    HeroSMP.PVP_QUEUE_MANAGER.selectKit(player, message.key);
                }
            });
            return null;
        }
    }
}
