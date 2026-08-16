package com.matoon.herosmp.network;

import com.matoon.herosmp.HeroSMP;
import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.UUID;

/**
 * Sent from server to all clients whenever a Life Link is created or removed.
 *
 * Payload:
 *   - boolean add  : true = add link, false = remove link
 *   - UUID linked  : the entity that carries the Life Link effect
 *   - UUID linker  : the entity that cast the link (only meaningful when add=true)
 */
public class PacketLifeLink implements IMessage {

    boolean add;
    UUID linked;
    UUID linker;

    /** Required no-arg constructor for deserialization. */
    public PacketLifeLink() {
    }

    public static PacketLifeLink add(UUID linked, UUID linker) {
        PacketLifeLink p = new PacketLifeLink();
        p.add    = true;
        p.linked = linked;
        p.linker = linker;
        return p;
    }

    public static PacketLifeLink remove(UUID linked) {
        PacketLifeLink p = new PacketLifeLink();
        p.add    = false;
        p.linked = linked;
        p.linker = new UUID(0, 0); // placeholder — not used on removal
        return p;
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(add);
        buf.writeLong(linked.getMostSignificantBits());
        buf.writeLong(linked.getLeastSignificantBits());
        buf.writeLong(linker.getMostSignificantBits());
        buf.writeLong(linker.getLeastSignificantBits());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        add    = buf.readBoolean();
        linked = new UUID(buf.readLong(), buf.readLong());
        linker = new UUID(buf.readLong(), buf.readLong());
    }

    public static class Handler implements IMessageHandler<PacketLifeLink, IMessage> {
        @Override
        public IMessage onMessage(PacketLifeLink msg, MessageContext ctx) {
            HeroSMP.proxy.handleClientPacket(msg);
            return null;
        }
    }
}
