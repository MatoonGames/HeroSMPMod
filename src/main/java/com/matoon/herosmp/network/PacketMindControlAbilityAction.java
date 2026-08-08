package com.matoon.herosmp.network;

import com.matoon.herosmp.mindstone.MindControlManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import java.util.UUID;

/** Requests a Lucraft action from a companion; all ownership and cooldown checks remain server-side. */
public class PacketMindControlAbilityAction implements IMessage {
    private UUID target; private boolean autocast, enabled; private String key;
    public PacketMindControlAbilityAction() {}
    public static PacketMindControlAbilityAction trigger(UUID target,String key){PacketMindControlAbilityAction p=new PacketMindControlAbilityAction();p.target=target;p.key=key;return p;}
    public static PacketMindControlAbilityAction autocast(UUID target,boolean enabled){PacketMindControlAbilityAction p=new PacketMindControlAbilityAction();p.target=target;p.autocast=true;p.enabled=enabled;return p;}
    @Override public void toBytes(ByteBuf b){b.writeLong(target.getMostSignificantBits());b.writeLong(target.getLeastSignificantBits());b.writeBoolean(autocast);b.writeBoolean(enabled);ByteBufUtils.writeUTF8String(b,key==null?"":key);}
    @Override public void fromBytes(ByteBuf b){target=new UUID(b.readLong(),b.readLong());autocast=b.readBoolean();enabled=b.readBoolean();key=ByteBufUtils.readUTF8String(b);}
    public static class Handler implements IMessageHandler<PacketMindControlAbilityAction,IMessage>{@Override public IMessage onMessage(PacketMindControlAbilityAction m,MessageContext c){EntityPlayerMP p=c.getServerHandler().player;p.getServerWorld().addScheduledTask(()->{if(m.autocast)MindControlManager.setAbilityAutocast(p,m.target,m.enabled);else MindControlManager.triggerAbility(p,m.target,m.key);});return null;}}
}
