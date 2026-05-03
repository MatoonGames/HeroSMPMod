package com.matoon.herosmp.network;

import com.matoon.herosmp.client.InfPowerUpOverlay;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.UUID;

/**
 * Server → Client packet that starts or stops the Infinity Gauntlet power-up
 * overlay and sound on the receiving player's screen.
 *
 * Sent when the player slots all six stones into the gauntlet (start=true),
 * and when the gauntlet leaves their hand or the sound finishes (start=false).
 *
 * holderUuid identifies the player who owns the gauntlet so that:
 *  - Bystanders can render the skin overlay on that player's model.
 *  - The holder plays the full HUD overlay + progress bar.
 *  - Bystanders play only the sound + skin overlay (no HUD, no progress bar).
 */
public class PacketInfPowerUp implements IMessage {

    /**
     * Duration of the power-up sequence in ticks, shared between the server-side
     * snap ability cooldown and the client-side overlay timer.
     */
    public static final int POWER_UP_DURATION_TICKS = 400;

    private boolean start;
    private UUID holderUuid;

    /** Required no-arg constructor for Forge deserialization. */
    public PacketInfPowerUp() {}

    public PacketInfPowerUp(boolean start, UUID holderUuid) {
        this.start = start;
        this.holderUuid = holderUuid;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(start);
        buf.writeLong(holderUuid.getMostSignificantBits());
        buf.writeLong(holderUuid.getLeastSignificantBits());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.start = buf.readBoolean();
        this.holderUuid = new UUID(buf.readLong(), buf.readLong());
    }

    @SideOnly(Side.CLIENT)
    public static class Handler implements IMessageHandler<PacketInfPowerUp, IMessage> {
        @Override
        public IMessage onMessage(PacketInfPowerUp msg, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (msg.start) {
                    InfPowerUpOverlay.start(msg.holderUuid);
                } else {
                    InfPowerUpOverlay.stop(msg.holderUuid);
                }
            });
            return null;
        }
    }
}
