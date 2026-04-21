package com.matoon.herosmp.npc.command;

import com.matoon.herosmp.HeroSMP;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;

public class CommandReturn extends CommandBase {
    @Override
    public String getName() {
        return "return";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/return";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean checkPermission(MinecraftServer server, ICommandSender sender) {
        return true;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        Entity entity = sender.getCommandSenderEntity();
        if (!(entity instanceof EntityPlayerMP)) {
            throw new CommandException("This command can only be used by a player.");
        }
        EntityPlayerMP player = (EntityPlayerMP) entity;
        if (!HeroSMP.HUNGER_GAMES_MANAGER.tryReturnPlayer(server, player)) {
            HeroSMP.PVP_QUEUE_MANAGER.handleReturnCommand(player);
        }
    }
}
