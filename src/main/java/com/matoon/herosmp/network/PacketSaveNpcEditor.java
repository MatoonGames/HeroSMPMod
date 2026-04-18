package com.matoon.herosmp.network;

import com.matoon.herosmp.npc.EntityStaticNpc;
import com.matoon.herosmp.npc.NpcMode;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketSaveNpcEditor implements IMessage {

    private int entityId;
    private String npcKey;
    private String displayName;
    private String skinOwner;
    private String mode;
    private String command;
    private String displayItemId;
    private boolean deleteNpc;

    public PacketSaveNpcEditor() {
    }

    public PacketSaveNpcEditor(int entityId, String npcKey, String displayName, String skinOwner, String mode, String command, String displayItemId, boolean deleteNpc) {
        this.entityId = entityId;
        this.npcKey = npcKey == null ? "" : npcKey;
        this.displayName = displayName == null ? "" : displayName;
        this.skinOwner = skinOwner == null ? "" : skinOwner;
        this.mode = mode == null ? "" : mode;
        this.command = command == null ? "" : command;
        this.displayItemId = displayItemId == null ? "" : displayItemId;
        this.deleteNpc = deleteNpc;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.entityId = buf.readInt();
        this.npcKey = ByteBufUtils.readUTF8String(buf);
        this.displayName = ByteBufUtils.readUTF8String(buf);
        this.skinOwner = ByteBufUtils.readUTF8String(buf);
        this.mode = ByteBufUtils.readUTF8String(buf);
        this.command = ByteBufUtils.readUTF8String(buf);
        this.displayItemId = ByteBufUtils.readUTF8String(buf);
        this.deleteNpc = buf.readBoolean();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(entityId);
        ByteBufUtils.writeUTF8String(buf, npcKey);
        ByteBufUtils.writeUTF8String(buf, displayName);
        ByteBufUtils.writeUTF8String(buf, skinOwner);
        ByteBufUtils.writeUTF8String(buf, mode);
        ByteBufUtils.writeUTF8String(buf, command);
        ByteBufUtils.writeUTF8String(buf, displayItemId);
        buf.writeBoolean(deleteNpc);
    }

    public static class Handler implements IMessageHandler<PacketSaveNpcEditor, IMessage> {
        @Override
        public IMessage onMessage(PacketSaveNpcEditor message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    if (!player.isCreative()) {
                        return;
                    }
                    Entity entity = player.world.getEntityByID(message.entityId);
                    if (!(entity instanceof EntityStaticNpc)) {
                        return;
                    }
                    EntityStaticNpc npc = (EntityStaticNpc) entity;
                    if (npc.getDistanceSq(player) > 64.0D * 64.0D) {
                        return;
                    }

                    if (message.deleteNpc) {
                        npc.setDead();
                        return;
                    }

                    String trimmedName = message.displayName == null ? "" : message.displayName.trim();
                    npc.setCustomNameTag(trimmedName);
                    npc.setAlwaysRenderNameTag(!trimmedName.isEmpty());

                    String trimmedKey = message.npcKey == null ? "" : message.npcKey.trim();
                    if (!trimmedKey.isEmpty()) {
                        npc.setNpcKey(trimmedKey);
                    }

                    npc.setSkinOwner(message.skinOwner == null ? "" : message.skinOwner.trim());
                    NpcMode parsed = NpcMode.fromString(message.mode);
                    npc.setMode(parsed == null ? NpcMode.COMMAND : parsed);
                    npc.setCommand(message.command == null ? "" : message.command.trim());
                    String itemId = message.displayItemId == null ? "" : message.displayItemId.trim();
                    if (!itemId.isEmpty() && Item.getByNameOrId(itemId) == null) {
                        itemId = "";
                    }
                    npc.setDisplayItemId(itemId);
                }
            });
            return null;
        }
    }
}
