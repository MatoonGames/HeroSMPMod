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
 * Operator-only debug commands for the HG system.
 * Usage: /heropvp hg debug <configuremap <map>|endconfigure|solo>
 */
public class CommandHeroPvp extends CommandBase {

    @Override
    public String getName() { return "heropvp"; }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/heropvp hg debug <configuremap <map>|endconfigure|solo>";
    }

    @Override
    public int getRequiredPermissionLevel() { return 2; }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (!(sender instanceof EntityPlayerMP)) {
            sender.sendMessage(new TextComponentString(TextFormatting.RED + "Players only."));
            return;
        }
        EntityPlayerMP player = (EntityPlayerMP) sender;

        if (args.length >= 3 && args[0].equalsIgnoreCase("hg") && args[1].equalsIgnoreCase("debug")) {
            String sub = args[2].toLowerCase();

            if (sub.equals("configuremap") && args.length >= 4) {
                HeroSMP.HUNGER_GAMES_MANAGER.startConfigureMapSession(server, player, args[3]);
                return;
            }
            if (sub.equals("endconfigure")) {
                HeroSMP.HUNGER_GAMES_MANAGER.endConfigureMapSession(server, player, true);
                return;
            }
            if (sub.equals("solo")) {
                HeroSMP.HUNGER_GAMES_MANAGER.startSoloMatch(server, player);
                return;
            }
        }

        player.sendMessage(new TextComponentString(TextFormatting.GOLD + "=== HeroPvP Debug Commands ==="));
        player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/heropvp hg debug configuremap <mapName>" + TextFormatting.GRAY + " - Enter map configure mode"));
        player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/heropvp hg debug endconfigure" + TextFormatting.GRAY + " - Save config and exit configure mode"));
        player.sendMessage(new TextComponentString(TextFormatting.YELLOW + "/heropvp hg debug solo" + TextFormatting.GRAY + " - Force-start a solo HG match for testing"));
    }
}
