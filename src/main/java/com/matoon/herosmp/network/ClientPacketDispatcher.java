package com.matoon.herosmp.network;

import com.matoon.herosmp.client.InfPowerUpOverlay;
import com.matoon.herosmp.client.LifeLinkClientMap;
import com.matoon.herosmp.client.SnapEffectOverlay;
import com.matoon.herosmp.client.SnapSkinOverlay;
import com.matoon.herosmp.mindstone.GuiMindControl;
import com.matoon.herosmp.mindstone.MindControlAuraRenderer;
import com.matoon.herosmp.mindstone.MindControlClientState;
import com.matoon.herosmp.mindstone.MindControlPlayerLock;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/**
 * Physical-client-only endpoint for packet effects. Registered packet handlers delegate
 * here through ClientProxy so dedicated servers never resolve GUI, audio, or renderer types.
 */
@SideOnly(Side.CLIENT)
public final class ClientPacketDispatcher {
    private ClientPacketDispatcher() {
    }

    public static void handle(IMessage message) {
        Minecraft.getMinecraft().addScheduledTask(() -> handleOnClientThread(message));
    }

    private static void handleOnClientThread(IMessage message) {
        if (message instanceof PacketLifeLink) {
            PacketLifeLink packet = (PacketLifeLink) message;
            if (packet.add) LifeLinkClientMap.addLink(packet.linked, packet.linker);
            else LifeLinkClientMap.removeLink(packet.linked);
        } else if (message instanceof PacketInfPowerUp) {
            PacketInfPowerUp packet = (PacketInfPowerUp) message;
            if (packet.start) InfPowerUpOverlay.start(packet.holderUuid);
            else InfPowerUpOverlay.stop(packet.holderUuid);
        } else if (message instanceof PacketSnapEffect) {
            SnapEffectOverlay.trigger(((PacketSnapEffect) message).soundIndex);
        } else if (message instanceof PacketSnapOverlay) {
            PacketSnapOverlay packet = (PacketSnapOverlay) message;
            if (packet.active) SnapSkinOverlay.add(packet.playerUuid, packet.mainHand);
            else SnapSkinOverlay.remove(packet.playerUuid);
        } else if (message instanceof PacketMindControlEntries) {
            MindControlClientState.set(((PacketMindControlEntries) message).entries);
        } else if (message instanceof PacketMindControlAura) {
            PacketMindControlAura packet = (PacketMindControlAura) message;
            MindControlAuraRenderer.set(packet.entity, packet.enabled);
        } else if (message instanceof PacketMindControlLock) {
            PacketMindControlLock packet = (PacketMindControlLock) message;
            MindControlPlayerLock.setLocked(packet.locked, packet.remainingTicks);
        } else if (message instanceof PacketOpenMindControlMenu) {
            PacketOpenMindControlMenu packet = (PacketOpenMindControlMenu) message;
            Minecraft.getMinecraft().displayGuiScreen(new GuiMindControl(
                    packet.target, packet.entityId, packet.name, packet.playerInventory,
                    packet.items, packet.health, packet.maxHealth, packet.armor,
                    packet.remainingTicks, packet.abilities, packet.abilityAutocast,
                    packet.superpowerId, packet.superpowerName));
        } else if (message instanceof PacketMindControlResistance) {
            PacketMindControlResistance packet = (PacketMindControlResistance) message;
            MindControlPlayerLock.startResistance(packet.token, packet.duration,
                    packet.windowStart, packet.windowEnd, packet.escape);
        } else if (message instanceof PacketMindControlResistanceResult) {
            MindControlPlayerLock.finishResistance();
        }
    }
}
