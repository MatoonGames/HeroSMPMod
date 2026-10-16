package com.matoon.herosmp.network;

import com.matoon.herosmp.HeroSMP;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/**
 * Client → Server. Requests that the player be dropped into Hunger Games "configure map mode" for
 * the named map — the same flow as {@code /heropvp hg debug configuremap <map>}. Gated to op level 2
 * (mirroring that command) in the handler, since the underlying manager method performs no
 * permission check of its own.
 */
public class PacketConfigureMap implements IMessage {

    private String mapName;

    public PacketConfigureMap() {}

    public PacketConfigureMap(String mapName) {
        this.mapName = mapName;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeUTF8String(buf, mapName == null ? "" : mapName);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        mapName = ByteBufUtils.readUTF8String(buf);
    }

    // -------------------------------------------------------------------------

    public static class Handler implements IMessageHandler<PacketConfigureMap, IMessage> {
        @Override
        public IMessage onMessage(PacketConfigureMap msg, MessageContext ctx) {
            final EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                if (msg.mapName == null || msg.mapName.isEmpty()) return;
                // Replicate the op gate of /heropvp (CommandHeroNpc requires permission level 2).
                // The host of a single-player world may always edit, even with cheats off.
                net.minecraft.server.MinecraftServer server = player.getServer();
                boolean singlePlayerHost = server != null && server.isSinglePlayer()
                        && player.getName().equals(server.getServerOwner());
                if (!singlePlayerHost && !player.canUseCommand(2, "heropvp")) {
                    player.sendMessage(new net.minecraft.util.text.TextComponentString(
                            net.minecraft.util.text.TextFormatting.RED
                            + "You don't have permission to edit maps."));
                    return;
                }
                HeroSMP.HUNGER_GAMES_MANAGER.startConfigureMapSession(player.getServer(), player, msg.mapName);
            });
            return null;
        }
    }
}
