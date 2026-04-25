package com.matoon.herosmp.npc;

import com.matoon.herosmp.HeroSMP;
import com.matoon.herosmp.network.ModNetwork;
import com.matoon.herosmp.network.PacketOpenNpcEditor;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.World;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class EntityStaticNpc extends EntityCreature {

    private static final DataParameter<String> SKIN_OWNER = EntityDataManager.createKey(EntityStaticNpc.class, DataSerializers.STRING);
    private static final DataParameter<String> SKIN_PROFILE_ID = EntityDataManager.createKey(EntityStaticNpc.class, DataSerializers.STRING);
    private static final DataParameter<String> MODE = EntityDataManager.createKey(EntityStaticNpc.class, DataSerializers.STRING);
    private static final DataParameter<String> COMMAND = EntityDataManager.createKey(EntityStaticNpc.class, DataSerializers.STRING);
    private static final DataParameter<String> NPC_KEY = EntityDataManager.createKey(EntityStaticNpc.class, DataSerializers.STRING);
    private static final DataParameter<String> DISPLAY_ITEM = EntityDataManager.createKey(EntityStaticNpc.class, DataSerializers.STRING);

    public EntityStaticNpc(World worldIn) {
        super(worldIn);
        this.setSize(0.6F, 1.95F);
        this.enablePersistence();
        this.experienceValue = 0;
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        this.dataManager.register(SKIN_OWNER, "Steve");
        this.dataManager.register(SKIN_PROFILE_ID, getOfflineProfileId("Steve"));
        this.dataManager.register(MODE, NpcMode.COMMAND.name());
        this.dataManager.register(COMMAND, "");
        this.dataManager.register(NPC_KEY, "npc_1");
        this.dataManager.register(DISPLAY_ITEM, "");
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0D);
        getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.0D);
        getEntityAttribute(SharedMonsterAttributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
    }

    @Override
    protected ResourceLocation getLootTable() {
        return null;
    }

    @Override
    public void onLivingUpdate() {
        this.motionX = 0.0D;
        this.motionY = 0.0D;
        this.motionZ = 0.0D;
        this.fallDistance = 0.0F;
        super.onLivingUpdate();
    }

    @Override
    public boolean processInteract(EntityPlayer player, EnumHand hand) {
        if (world.isRemote || hand != EnumHand.MAIN_HAND) {
            return false;
        }

        System.out.println("[HeroSMP][NPC] processInteract: player=" + player.getName()
                + " creative=" + player.isCreative()
                + " sneaking=" + player.isSneaking()
                + " isMP=" + (player instanceof EntityPlayerMP));

        if (player.isCreative() && player.isSneaking() && player instanceof EntityPlayerMP) {
            EntityPlayerMP editor = (EntityPlayerMP) player;
            ModNetwork.CHANNEL.sendTo(new PacketOpenNpcEditor(
                    this.getEntityId(),
                    getNpcKey(),
                    getCustomNameTag(),
                    getSkinOwner(),
                    getMode().name(),
                    getCommand(),
                    getDisplayItemId()
            ), editor);
            return true;
        }

        NpcMode mode = getMode();
        if (mode == NpcMode.PVP_QUEUE) {
            if (player instanceof EntityPlayerMP) {
                HeroSMP.PVP_QUEUE_MANAGER.openPvpMenu((EntityPlayerMP) player);
            }
            return true;
        }
        if (mode == NpcMode.HUNGER_GAMES_QUEUE) {
            if (player instanceof EntityPlayerMP) {
                HeroSMP.HUNGER_GAMES_MANAGER.openHungerGamesMenu((EntityPlayerMP) player);
            }
            return true;
        }

        String configuredCommand = getCommand();
        if (configuredCommand == null || configuredCommand.trim().isEmpty()) {
            player.sendMessage(new TextComponentString("This NPC has no command configured."));
            return true;
        }

        String command = configuredCommand.trim();
        if (command.startsWith("/")) {
            command = command.substring(1);
        }

        command = command
                .replace("{player}", player.getName())
                .replace("{x}", Integer.toString(player.getPosition().getX()))
                .replace("{y}", Integer.toString(player.getPosition().getY()))
                .replace("{z}", Integer.toString(player.getPosition().getZ()));

        this.getServer().getCommandManager().executeCommand(this.getServer(), command);
        return true;
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        setSkinOwner(compound.getString("SkinOwner"));
        setMode(NpcMode.fromString(compound.getString("NpcMode")));
        setCommand(compound.getString("NpcCommand"));
        setDisplayItemId(compound.getString("DisplayItem"));
        String key = compound.getString("NpcKey");
        if (key != null && !key.trim().isEmpty()) {
            setNpcKey(key);
        }
        String storedProfile = compound.getString("SkinProfileId");
        if (storedProfile != null && !storedProfile.trim().isEmpty()) {
            this.dataManager.set(SKIN_PROFILE_ID, storedProfile);
        }
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        compound.setString("SkinOwner", getSkinOwner());
        compound.setString("SkinProfileId", getSkinProfileId());
        compound.setString("NpcMode", getMode().name());
        compound.setString("NpcCommand", getCommand());
        compound.setString("NpcKey", getNpcKey());
        compound.setString("DisplayItem", getDisplayItemId());
    }

    @Override
    public boolean isAIDisabled() {
        return true;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        return false;
    }

    public String getSkinOwner() {
        return this.dataManager.get(SKIN_OWNER);
    }

    public void setSkinOwner(String owner) {
        String trimmed = owner == null || owner.trim().isEmpty() ? "Steve" : owner.trim();
        this.dataManager.set(SKIN_OWNER, trimmed);
        this.dataManager.set(SKIN_PROFILE_ID, getOfflineProfileId(trimmed));
    }

    public String getSkinProfileId() {
        return this.dataManager.get(SKIN_PROFILE_ID);
    }

    public NpcMode getMode() {
        NpcMode mode = NpcMode.fromString(this.dataManager.get(MODE));
        return mode == null ? NpcMode.COMMAND : mode;
    }

    public void setMode(NpcMode mode) {
        this.dataManager.set(MODE, mode == null ? NpcMode.COMMAND.name() : mode.name());
    }

    public String getCommand() {
        return this.dataManager.get(COMMAND);
    }

    public void setCommand(String command) {
        this.dataManager.set(COMMAND, command == null ? "" : command);
    }

    public String getNpcKey() {
        return this.dataManager.get(NPC_KEY);
    }

    public void setNpcKey(String npcKey) {
        if (npcKey == null || npcKey.trim().isEmpty()) {
            return;
        }
        this.dataManager.set(NPC_KEY, npcKey.trim());
    }

    public String getDisplayItemId() {
        return this.dataManager.get(DISPLAY_ITEM);
    }

    public void setDisplayItemId(String itemId) {
        this.dataManager.set(DISPLAY_ITEM, itemId == null ? "" : itemId.trim());
    }

    private String getOfflineProfileId(String name) {
        UUID offline = UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
        return offline.toString();
    }
}
