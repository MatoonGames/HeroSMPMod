package com.matoon.herosmp.npc.playtest;

import com.matoon.herosmp.HeroSMP;
import com.matoon.herosmp.npc.kit.KitDefinition;
import net.minecraft.block.BlockButton;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.GameType;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * Kit playtest arena, built in the throwaway playtest world: the player gets the kit, three test
 * dummies (no armor / iron / diamond) report the damage they take, and a gold button starts a solo
 * debug PVP match where the kit can be picked. Runs on the (integrated) server.
 */
public class KitPlaytest {

    /** Button that starts the solo PVP match (overworld of the playtest world), or null. */
    private static BlockPos button;

    /** Save folders of the store's throwaway worlds: playtest, map editor, and the pre-rename editor. */
    private static final String[] THROWAWAY_FOLDERS = {"beyonder_playtest", "beyonder_map_editor", "herosmp_map_editor"};

    public static boolean isThrowawayWorld(String folder) {
        if (folder == null) return false;
        for (String f : THROWAWAY_FOLDERS) if (f.equals(folder)) return true;
        return false;
    }

    /** Forget the arena after its world closed. */
    public static void reset() {
        button = null;
    }

    public static void setup(MinecraftServer server, EntityPlayerMP player, NBTTagCompound kitTag) {
        WorldServer world = server.getWorld(0);
        KitDefinition kit = KitDefinition.deserialize(kitTag);
        kit.setEnabled(true);
        // Make the kit pickable in this world (the PVP kit picker lists the world's kits).
        HeroSMP.KIT_MANAGER.putWorldKit(server, kit);

        BlockPos feet = new BlockPos(player.posX, player.posY, player.posZ);
        int y = world.getHeight(feet).getY();
        BlockPos c = new BlockPos(feet.getX(), y, feet.getZ());

        // Clear out anything left from an earlier setup.
        AxisAlignedBB area = new AxisAlignedBB(c).grow(24);
        for (Entity e : world.getEntitiesWithinAABB(Entity.class, area)) {
            if (e instanceof EntityTestDummy || e instanceof EntityArmorStand) e.setDead();
        }

        // Stone-brick pad.
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                world.setBlockState(c.add(dx, -1, dz), (Math.abs(dx) == 9 || Math.abs(dz) == 9)
                        ? Blocks.STONEBRICK.getStateFromMeta(3) : Blocks.STONEBRICK.getDefaultState());
                for (int up = 0; up < 3; up++) world.setBlockToAir(c.add(dx, up, dz));
            }
        }

        // Dummies in a row in front of the player (south), facing them.
        String[] labels = {"No Armor", "Iron Armor", "Diamond Armor"};
        for (int i = 0; i < 3; i++) {
            EntityTestDummy d = new EntityTestDummy(world, "Dummy - " + labels[i]);
            d.setLocationAndAngles(c.getX() - 3 + i * 3 + 0.5, y, c.getZ() + 6 + 0.5, 180f, 0f);
            d.rotationYawHead = 180f;
            d.renderYawOffset = 180f;
            if (i == 1) armor(d, Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS, Items.IRON_BOOTS);
            if (i == 2) armor(d, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS);
            world.spawnEntity(d);
        }

        // Gold pedestal with a button on top, and a floating label.
        BlockPos pedestal = c.add(5, 0, 0);
        world.setBlockState(pedestal, Blocks.GOLD_BLOCK.getDefaultState());
        button = pedestal.up();
        world.setBlockState(button, Blocks.STONE_BUTTON.getDefaultState().withProperty(BlockButton.FACING, EnumFacing.UP));
        label(world, pedestal.getX() + 0.5, y + 2.15, pedestal.getZ() + 0.5, "§6§lStart Solo PVP Match");
        label(world, pedestal.getX() + 0.5, y + 1.85, pedestal.getZ() + 0.5, "§7press the button");

        // Kit the player and face them at the dummies.
        player.setGameType(GameType.SURVIVAL);
        HeroSMP.KIT_MANAGER.applyKitToPlayer(player, kit);
        player.setHealth(player.getMaxHealth());
        player.getFoodStats().setFoodLevel(20);
        player.connection.setPlayerLocation(c.getX() + 0.5, y, c.getZ() + 0.5, 0f, 0f);

        player.sendMessage(new TextComponentString(TextFormatting.GOLD + "Kit playtest: " + TextFormatting.WHITE + kit.getDisplayName()));
        player.sendMessage(new TextComponentString(TextFormatting.GRAY
                + "Hit the dummies to see how much damage you deal. Press the gold button to start a solo PVP match."));
    }

    private static void armor(EntityTestDummy d, net.minecraft.item.Item head, net.minecraft.item.Item chest,
                              net.minecraft.item.Item legs, net.minecraft.item.Item feet) {
        d.setItemStackToSlot(EntityEquipmentSlot.HEAD, new ItemStack(head));
        d.setItemStackToSlot(EntityEquipmentSlot.CHEST, new ItemStack(chest));
        d.setItemStackToSlot(EntityEquipmentSlot.LEGS, new ItemStack(legs));
        d.setItemStackToSlot(EntityEquipmentSlot.FEET, new ItemStack(feet));
    }

    /** Floating text: an invisible marker armor stand showing only its name. */
    private static void label(WorldServer world, double x, double y, double z, String text) {
        EntityArmorStand stand = new EntityArmorStand(world, x, y, z);
        NBTTagCompound tag = stand.writeToNBT(new NBTTagCompound());
        tag.setBoolean("Marker", true);
        tag.setBoolean("Invisible", true);
        tag.setBoolean("NoGravity", true);
        tag.setBoolean("Invulnerable", true);
        tag.setString("CustomName", text);
        tag.setBoolean("CustomNameVisible", true);
        stand.readFromNBT(tag);
        world.spawnEntity(stand);
    }

    @SubscribeEvent
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (button == null || event.getWorld().isRemote || event.getHand() != EnumHand.MAIN_HAND) return;
        if (event.getWorld().provider.getDimension() != 0 || !event.getPos().equals(button)) return;
        if (event.getWorld().getMinecraftServer() == null
                || !isThrowawayWorld(event.getWorld().getMinecraftServer().getFolderName())) return;
        if (!(event.getEntityPlayer() instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.getEntityPlayer();
        player.sendMessage(new TextComponentString(TextFormatting.GOLD + "Starting a solo PVP match... pick your kit when the arena opens."));
        HeroSMP.PVP_QUEUE_MANAGER.startDebugSoloMatch(player);
    }
}
