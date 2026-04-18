package com.matoon.herosmp.network;

import com.matoon.herosmp.client.gui.GuiNpcEditor;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

public class PacketOpenNpcEditor implements IMessage {

    private int entityId;
    private String npcKey;
    private String displayName;
    private String skinOwner;
    private String mode;
    private String command;
    private String displayItemId;

    public PacketOpenNpcEditor() {
    }

    public PacketOpenNpcEditor(int entityId, String npcKey, String displayName, String skinOwner, String mode, String command, String displayItemId) {
        this.entityId = entityId;
        this.npcKey = npcKey == null ? "" : npcKey;
        this.displayName = displayName == null ? "" : displayName;
        this.skinOwner = skinOwner == null ? "" : skinOwner;
        this.mode = mode == null ? "" : mode;
        this.command = command == null ? "" : command;
        this.displayItemId = displayItemId == null ? "" : displayItemId;
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
    }

    public static class Handler implements IMessageHandler<PacketOpenNpcEditor, IMessage> {
        @Override
        public IMessage onMessage(PacketOpenNpcEditor message, MessageContext ctx) {
            Minecraft.getMinecraft().addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    Minecraft.getMinecraft().displayGuiScreen(new GuiNpcEditor(
                            message.entityId,
                            message.npcKey,
                            message.displayName,
                            message.skinOwner,
                            message.mode,
                            message.command,
                            message.displayItemId
                    ));
                }
            });
            return null;
        }
    }
}
