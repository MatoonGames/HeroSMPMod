package com.matoon.herosmp.network;

import com.matoon.herosmp.mindstone.GuiMindControl;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.*;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import java.util.*;

/** Server snapshot used to draw the companion screen; no client inventory access is trusted. */
public class PacketOpenMindControlMenu implements IMessage {
    public static class AbilityInfo { public String key,name,description; public boolean ready,triggerable; public AbilityInfo(){} public AbilityInfo(String key,String name,String description,boolean ready,boolean triggerable){this.key=key;this.name=name;this.description=description;this.ready=ready;this.triggerable=triggerable;} }
    private UUID target; private int entityId; private String name,superpowerId,superpowerName; private boolean playerInventory,abilityAutocast; private List<ItemStack> items=new ArrayList<>(); private List<AbilityInfo> abilities=new ArrayList<>(); private float health,maxHealth; private int armor,remainingTicks;
    public PacketOpenMindControlMenu(){} public PacketOpenMindControlMenu(UUID id,int entityId,String name,boolean playerInventory,List<ItemStack> items,float health,float maxHealth,int armor,int remainingTicks,List<AbilityInfo> abilities,boolean abilityAutocast,String superpowerId,String superpowerName){target=id;this.entityId=entityId;this.name=name;this.playerInventory=playerInventory;this.items=items;this.health=health;this.maxHealth=maxHealth;this.armor=armor;this.remainingTicks=remainingTicks;this.abilities=abilities;this.abilityAutocast=abilityAutocast;this.superpowerId=superpowerId;this.superpowerName=superpowerName;}
    @Override public void toBytes(ByteBuf b){b.writeLong(target.getMostSignificantBits());b.writeLong(target.getLeastSignificantBits());b.writeInt(entityId);ByteBufUtils.writeUTF8String(b,name);b.writeBoolean(playerInventory);b.writeFloat(health);b.writeFloat(maxHealth);b.writeInt(armor);b.writeInt(remainingTicks);b.writeInt(items.size());for(ItemStack s:items)ByteBufUtils.writeItemStack(b,s);b.writeInt(abilities.size());for(AbilityInfo a:abilities){ByteBufUtils.writeUTF8String(b,a.key);ByteBufUtils.writeUTF8String(b,a.name);ByteBufUtils.writeUTF8String(b,a.description);b.writeBoolean(a.ready);b.writeBoolean(a.triggerable);}b.writeBoolean(abilityAutocast);ByteBufUtils.writeUTF8String(b,superpowerId==null?"":superpowerId);ByteBufUtils.writeUTF8String(b,superpowerName==null?"":superpowerName);}
    @Override public void fromBytes(ByteBuf b){target=new UUID(b.readLong(),b.readLong());entityId=b.readInt();name=ByteBufUtils.readUTF8String(b);playerInventory=b.readBoolean();health=b.readFloat();maxHealth=b.readFloat();armor=b.readInt();remainingTicks=b.readInt();items=new ArrayList<>();for(int i=b.readInt();i>0;i--)items.add(ByteBufUtils.readItemStack(b));abilities=new ArrayList<>();for(int i=b.readInt();i>0;i--)abilities.add(new AbilityInfo(ByteBufUtils.readUTF8String(b),ByteBufUtils.readUTF8String(b),ByteBufUtils.readUTF8String(b),b.readBoolean(),b.readBoolean()));abilityAutocast=b.readBoolean();superpowerId=ByteBufUtils.readUTF8String(b);superpowerName=ByteBufUtils.readUTF8String(b);}
    @SideOnly(Side.CLIENT) public static class Handler implements IMessageHandler<PacketOpenMindControlMenu,IMessage>{@Override public IMessage onMessage(PacketOpenMindControlMenu m,MessageContext c){net.minecraft.client.Minecraft.getMinecraft().addScheduledTask(()->net.minecraft.client.Minecraft.getMinecraft().displayGuiScreen(new GuiMindControl(m.target,m.entityId,m.name,m.playerInventory,m.items,m.health,m.maxHealth,m.armor,m.remainingTicks,m.abilities,m.abilityAutocast,m.superpowerId,m.superpowerName)));return null;}}
}
