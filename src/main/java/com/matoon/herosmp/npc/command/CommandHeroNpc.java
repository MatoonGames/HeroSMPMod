package com.matoon.herosmp.npc.command;

import com.matoon.herosmp.HeroSMP;
import com.matoon.herosmp.npc.EntityStaticNpc;
import com.matoon.herosmp.npc.NpcMode;
import com.matoon.herosmp.npc.kit.KitDefinition;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class CommandHeroNpc extends CommandBase {

    @Override
    public String getName() {
        return "heropvp";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("herpvp");
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/heropvp spawn <x> <y> <z> <skinOwner> <name...> | mode <npcKey> <command|pvp_queue> | setcommand <npcKey> <command...> | info <npcKey> | kit <create|remove|list> ... | lootmenu | debugsolo | debugexit | roundtime <seconds> | hg debug <configuremap <map>|endconfigure|solo>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException(getUsage(sender));
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "spawn":
                handleSpawn(sender, args);
                return;
            case "mode":
                handleMode(sender, args);
                return;
            case "setcommand":
                handleSetCommand(sender, args);
                return;
            case "info":
                handleInfo(sender, args);
                return;
            case "kit":
                handleKit(sender, args);
                return;
            case "lootmenu":
                handleLootMenu(sender);
                return;
            case "debugsolo":
                handleDebugSolo(sender);
                return;
            case "debugexit":
                handleDebugExit(sender);
                return;
            case "roundtime":
                handleRoundTime(server, sender, args);
                return;
            case "hg":
                handleHg(server, sender, args);
                return;
            default:
                throw new WrongUsageException(getUsage(sender));
        }
    }

    private void handleSpawn(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 6) {
            throw new WrongUsageException("/heropvp spawn <x> <y> <z> <skinOwner> <name...>");
        }

        Vec3d base = sender.getPositionVector();
        double x = parseDouble(base.x, args[1], false);
        double y = parseDouble(base.y, args[2], false);
        double z = parseDouble(base.z, args[3], false);

        String skinOwner = args[4];
        String name = buildString(args, 5);

        World world = sender.getEntityWorld();
        EntityStaticNpc npc = new EntityStaticNpc(world);
        float yaw = 0.0F;
        float pitch = 0.0F;
        Entity sourceEntity = sender.getCommandSenderEntity();
        if (sourceEntity != null) {
            yaw = sourceEntity.rotationYaw;
            pitch = sourceEntity.rotationPitch;
        }
        npc.setPositionAndRotation(x, y, z, yaw, pitch);
        npc.rotationYawHead = yaw;
        npc.prevRotationYawHead = yaw;
        npc.setCustomNameTag(name);
        npc.setAlwaysRenderNameTag(true);
        npc.setSkinOwner(skinOwner);
        npc.setNpcKey(createNpcKey(world, name));
        npc.setMode(NpcMode.COMMAND);

        world.spawnEntity(npc);
        sender.sendMessage(new TextComponentString("Spawned NPC '" + name + "' with key " + npc.getNpcKey()));
    }

    private void handleMode(ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 3) {
            throw new WrongUsageException("/heropvp mode <npcKey> <command|pvp_queue>");
        }

        EntityStaticNpc npc = findNpc(sender.getEntityWorld(), args[1]);
        NpcMode mode = NpcMode.fromString(args[2]);
        if (mode == null && "pvp_queue".equalsIgnoreCase(args[2])) {
            mode = NpcMode.PVP_QUEUE;
        }
        if (mode == null) {
            throw new CommandException("Invalid mode. Use command or pvp_queue.");
        }

        npc.setMode(mode);
        sender.sendMessage(new TextComponentString("NPC '" + npc.getNpcKey() + "' mode set to " + mode.name().toLowerCase(Locale.ROOT) + "."));
    }

    private void handleSetCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 3) {
            throw new WrongUsageException("/heropvp setcommand <npcKey> <command...>");
        }

        EntityStaticNpc npc = findNpc(sender.getEntityWorld(), args[1]);
        String command = buildString(args, 2);
        npc.setCommand(command);

        sender.sendMessage(new TextComponentString("NPC '" + npc.getNpcKey() + "' command updated."));
    }

    private void handleInfo(ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 2) {
            throw new WrongUsageException("/heropvp info <npcKey>");
        }

        EntityStaticNpc npc = findNpc(sender.getEntityWorld(), args[1]);
        sender.sendMessage(new TextComponentString("Key=" + npc.getNpcKey()));
        sender.sendMessage(new TextComponentString("Name=" + npc.getName()));
        sender.sendMessage(new TextComponentString("Skin=" + npc.getSkinOwner()));
        sender.sendMessage(new TextComponentString("Mode=" + npc.getMode().name().toLowerCase(Locale.ROOT)));
        sender.sendMessage(new TextComponentString("Command=" + npc.getCommand()));
    }

    private void handleKit(ICommandSender sender, String[] args) throws CommandException {
        if (args.length < 2) {
            throw new WrongUsageException("/heropvp kit <create|remove|list> ...");
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        if ("list".equals(action)) {
            List<KitDefinition> kits = HeroSMP.KIT_MANAGER.getKits(sender.getServer());
            if (kits.isEmpty()) {
                sender.sendMessage(new TextComponentString("No kits configured."));
                return;
            }
            sender.sendMessage(new TextComponentString("Kits:"));
            for (KitDefinition kit : kits) {
                sender.sendMessage(new TextComponentString("- " + kit.getKey() + " (" + kit.getDisplayName() + ")"));
            }
            return;
        }

        if ("create".equals(action)) {
            if (args.length < 4) {
                throw new WrongUsageException("/heropvp kit create <kitName> <iconItem>");
            }

            EntityPlayerMP player = getPlayerSender(sender);
            String kitName = args[2];
            Item item = Item.getByNameOrId(args[3]);
            if (item == null) {
                throw new CommandException("Unknown item: " + args[3]);
            }

            KitDefinition kit = HeroSMP.KIT_MANAGER.createOrUpdateFromPlayer(sender.getServer(), player, kitName, new ItemStack(item));
            sender.sendMessage(new TextComponentString("Saved kit '" + kit.getDisplayName() + "' as key " + kit.getKey()));
            return;
        }

        if ("remove".equals(action)) {
            if (args.length < 3) {
                throw new WrongUsageException("/heropvp kit remove <kitName>");
            }

            boolean removed = HeroSMP.KIT_MANAGER.removeKit(sender.getServer(), args[2]);
            if (!removed) {
                throw new CommandException("Kit not found: " + args[2]);
            }
            sender.sendMessage(new TextComponentString("Removed kit " + args[2]));
            return;
        }

        throw new WrongUsageException("/heropvp kit <create|remove|list> ...");
    }

    private void handleDebugSolo(ICommandSender sender) throws CommandException {
        EntityPlayerMP player = getPlayerSender(sender);
        HeroSMP.PVP_QUEUE_MANAGER.startDebugSoloMatch(player);
    }

    private void handleLootMenu(ICommandSender sender) throws CommandException {
        EntityPlayerMP player = getPlayerSender(sender);
        HeroSMP.PVP_CHEST_LOOT_MANAGER.openLootMenu(player);
        sender.sendMessage(new TextComponentString("Opened PvP chest loot menu. Items placed here can appear in arena chests."));
    }

    private void handleDebugExit(ICommandSender sender) throws CommandException {
        EntityPlayerMP player = getPlayerSender(sender);
        HeroSMP.PVP_QUEUE_MANAGER.exitDebugSoloMatch(player);
    }

    private void handleHg(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length >= 3 && "debug".equalsIgnoreCase(args[1])) {
            EntityPlayerMP player = getPlayerSender(sender);
            String sub = args[2].toLowerCase(Locale.ROOT);
            if ("configuremap".equals(sub) && args.length >= 4) {
                try {
                    HeroSMP.HUNGER_GAMES_MANAGER.startConfigureMapSession(server, player, buildString(args, 3));
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new CommandException("configuremap failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                return;
            }
            if ("endconfigure".equals(sub)) {
                try {
                    HeroSMP.HUNGER_GAMES_MANAGER.endConfigureMapSession(server, player, true);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new CommandException("endconfigure failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                return;
            }
            if ("solo".equals(sub)) {
                try {
                    HeroSMP.HUNGER_GAMES_MANAGER.startSoloMatch(server, player);
                } catch (Exception e) {
                    e.printStackTrace();
                    throw new CommandException("solo failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                }
                return;
            }
            if ("listmaps".equals(sub)) {
                java.util.List<String> maps = HeroSMP.HUNGER_GAMES_MANAGER.getAvailableMaps(server);
                if (maps.isEmpty()) {
                    sender.sendMessage(new TextComponentString("No HG maps found. Drop world folders into herosmp_hg_maps/."));
                } else {
                    sender.sendMessage(new TextComponentString("HG maps (" + maps.size() + "):"));
                    for (String map : maps) {
                        sender.sendMessage(new TextComponentString("  - " + map));
                    }
                }
                return;
            }
        }
        sender.sendMessage(new TextComponentString("Usage: /heropvp hg debug <configuremap <map>|endconfigure|solo|listmaps>"));
    }

    private void handleRoundTime(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 2) {
            throw new WrongUsageException("/heropvp roundtime <seconds>");
        }
        int seconds = parseInt(args[1], 30, 1800);
        HeroSMP.PVP_QUEUE_MANAGER.setRoundDurationSeconds(server, seconds);
        sender.sendMessage(new TextComponentString("PvP round time set to " + seconds + " seconds for this world."));
    }

    private EntityPlayerMP getPlayerSender(ICommandSender sender) throws CommandException {
        Entity commandEntity = sender.getCommandSenderEntity();
        if (!(commandEntity instanceof EntityPlayerMP)) {
            throw new CommandException("This command must be run by a player.");
        }
        return (EntityPlayerMP) commandEntity;
    }

    private EntityStaticNpc findNpc(World world, String token) throws CommandException {
        for (Entity entity : world.loadedEntityList) {
            if (!(entity instanceof EntityStaticNpc)) {
                continue;
            }
            EntityStaticNpc npc = (EntityStaticNpc) entity;
            if (token.equalsIgnoreCase(npc.getNpcKey())) {
                return npc;
            }
        }

        UUID uuid = tryParseUuid(token);
        if (uuid != null) {
            for (Entity entity : world.loadedEntityList) {
                if (entity instanceof EntityStaticNpc && entity.getUniqueID().equals(uuid)) {
                    return (EntityStaticNpc) entity;
                }
            }
        }

        throw new CommandException("NPC not found in this dimension: " + token);
    }

    private UUID tryParseUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String createNpcKey(World world, String nameTag) {
        String base = nameTag.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (base.isEmpty()) {
            base = "npc";
        }

        int max = 0;
        for (Entity entity : world.loadedEntityList) {
            if (!(entity instanceof EntityStaticNpc)) {
                continue;
            }
            String key = ((EntityStaticNpc) entity).getNpcKey();
            if (key == null || !key.toLowerCase(Locale.ROOT).startsWith(base + "_")) {
                continue;
            }
            String suffix = key.substring(base.length() + 1);
            try {
                max = Math.max(max, Integer.parseInt(suffix));
            } catch (NumberFormatException ignored) {
            }
        }
        return base + "_" + (max + 1);
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(args, Arrays.asList("spawn", "mode", "setcommand", "info", "kit", "lootmenu", "debugsolo", "debugexit", "roundtime", "hg"));
        }

        if (args.length == 2 && "hg".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, Collections.singletonList("debug"));
        }

        if (args.length == 3 && "hg".equalsIgnoreCase(args[0]) && "debug".equalsIgnoreCase(args[1])) {
            return getListOfStringsMatchingLastWord(args, Arrays.asList("configuremap", "endconfigure", "solo", "listmaps"));
        }

        if (args.length == 2 && "kit".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, Arrays.asList("create", "remove", "list"));
        }

        if (args.length == 3 && "mode".equalsIgnoreCase(args[0])) {
            return getListOfStringsMatchingLastWord(args, Arrays.asList("command", "pvp_queue"));
        }

        if (args.length == 3 && "kit".equalsIgnoreCase(args[0]) && "remove".equalsIgnoreCase(args[1])) {
            return getKitKeys(server);
        }

        if (args.length == 4 && "kit".equalsIgnoreCase(args[0]) && "create".equalsIgnoreCase(args[1])) {
            return Collections.singletonList("minecraft:diamond_sword");
        }

        if (args.length == 2 && ("mode".equalsIgnoreCase(args[0]) || "setcommand".equalsIgnoreCase(args[0]) || "info".equalsIgnoreCase(args[0]))) {
            return getNpcKeysNear(sender.getEntityWorld(), sender.getPositionVector(), 96.0D);
        }

        return Collections.emptyList();
    }

    private List<String> getKitKeys(MinecraftServer server) {
        List<String> keys = new ArrayList<String>();
        for (KitDefinition kit : HeroSMP.KIT_MANAGER.getKits(server)) {
            keys.add(kit.getKey());
        }
        return keys;
    }

    private List<String> getNpcKeysNear(World world, Vec3d center, double radius) {
        List<String> keys = new ArrayList<String>();
        double maxDistSq = radius * radius;
        for (Entity entity : world.loadedEntityList) {
            if (!(entity instanceof EntityStaticNpc)) {
                continue;
            }
            if (entity.getDistanceSq(center.x, center.y, center.z) <= maxDistSq) {
                keys.add(((EntityStaticNpc) entity).getNpcKey());
            }
        }
        return keys;
    }
}
