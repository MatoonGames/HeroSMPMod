package com.matoon.herosmp.hungergames.command;

import com.matoon.herosmp.HeroSMP;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

/**
 * Command for Hunger Games functionality.
 * Usage: /hg <join|leave|queue|matches>
 */
public class CommandHungerGames extends CommandBase {

    @Override
    public String getName() {
        return "hg";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/hg <join|leave|queue|matches>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0; // All players can use
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "This command can only be used by players!"));
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP) sender;

        if (args.length == 0) {
            sendUsage(player);
            return;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "join":
            case "queue":
                HeroSMP.HUNGER_GAMES_MANAGER.queuePlayer(player);
                break;

            case "leave":
            case "dequeue":
                HeroSMP.HUNGER_GAMES_MANAGER.dequeuePlayer(player);
                break;

            case "matches":
            case "list":
                listMatches(player);
                break;

            case "help":
                sendUsage(player);
                break;

            default:
                sendUsage(player);
                break;
        }
    }

    private void sendUsage(EntityPlayerMP player) {
        player.sendMessage(new TextComponentString(TextFormatting.GOLD + "=== Hunger Games Commands ==="));
        player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/hg join" + TextFormatting.GRAY + " - Join the Hunger Games queue"));
        player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/hg leave" + TextFormatting.GRAY + " - Leave the queue"));
        player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/hg matches" + TextFormatting.GRAY + " - List active matches"));
        player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/hg help" + TextFormatting.GRAY + " - Show this help"));
    }

    private void listMatches(EntityPlayerMP player) {
        int matchCount = HeroSMP.HUNGER_GAMES_MANAGER.getActiveMatches().size();
        int queueSize = HeroSMP.HUNGER_GAMES_MANAGER.getQueueSize();

        player.sendMessage(new TextComponentString(TextFormatting.GOLD + "=== Hunger Games Status ==="));
        player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Active Matches: " + 
            TextFormatting.WHITE + matchCount));
        player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "Players in Queue: " + 
            TextFormatting.WHITE + queueSize));

        if (matchCount > 0) {
            player.sendMessage(new TextComponentString(TextFormatting.GRAY + "Use /hg spectate <id> to watch a match"));
        }
    }
}
