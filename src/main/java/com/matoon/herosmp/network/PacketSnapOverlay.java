package com.matoon.herosmp.network;

import com.matoon.herosmp.client.SnapSkinOverlay;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.UUID;

/**
 * Server → Client packet that sets or clears the permanent snap skin overlay
 * on a specific player's model.
 *
 * Broadcast to all online players so the overlay renders correctly for everyone
 * who can see the snapper, regardless of proximity.
 *
 * {@code active=true}  → apply overlay (snapper just snapped)
 * {@code active=false} → remove overlay (snapper died)
 */
public class PacketSnapOverlay implements IMessage {

    private UUID playerUuid;
    private boolean mainHand;
    private boolean active;

    /** Required no-arg constructor for Forge deserialization. */
    public PacketSnapOverlay() {}

    public PacketSnapOverlay(UUID playerUuid, boolean mainHand, boolean active) {
        this.playerUuid = playerUuid;
        this.mainHand   = mainHand;
        this.active     = active;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeLong(playerUuid.getMostSignificantBits());
        buf.writeLong(playerUuid.getLeastSignificantBits());
        buf.writeBoolean(mainHand);
        buf.writeBoolean(active);
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        playerUuid = new UUID(buf.readLong(), buf.readLong());
        mainHand   = buf.readBoolean();
        active     = buf.readBoolean();
    }

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketSnapOverlay, IMessage> {
        @Override
        public IMessage onMessage(PacketSnapOverlay msg, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (msg.active) {
                    SnapSkinOverlay.add(msg.playerUuid, msg.mainHand);
                } else {
                    SnapSkinOverlay.remove(msg.playerUuid);
                }
            });
            return null;
        }
    }
}
