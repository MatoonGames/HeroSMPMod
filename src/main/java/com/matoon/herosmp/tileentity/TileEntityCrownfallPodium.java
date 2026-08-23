package com.matoon.herosmp.tileentity;

import net.minecraft.block.state.IBlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

import javax.annotation.Nullable;

/** Server-owned Crownfall objective data, synchronized for the floating item renderer. */
public class TileEntityCrownfallPodium extends TileEntity {
    private ItemStack displayStack = ItemStack.EMPTY;
    private boolean gauntlet;

    public ItemStack getDisplayStack() {
        return displayStack;
    }

    public boolean isGauntlet() {
        return gauntlet;
    }

    public void setObjective(ItemStack stack, boolean isGauntlet) {
        displayStack = stack.isEmpty() ? ItemStack.EMPTY : stack.copy();
        gauntlet = isGauntlet;
        sync();
    }

    public void clearObjective() {
        displayStack = ItemStack.EMPTY;
        gauntlet = false;
        sync();
    }

    private void sync() {
        markDirty();
        if (world != null) {
            IBlockState state = world.getBlockState(pos);
            world.notifyBlockUpdate(pos, state, state, 3);
        }
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        compound.setBoolean("Gauntlet", gauntlet);
        if (!displayStack.isEmpty()) {
            compound.setTag("DisplayStack", displayStack.writeToNBT(new NBTTagCompound()));
        }
        return compound;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        gauntlet = compound.getBoolean("Gauntlet");
        displayStack = compound.hasKey("DisplayStack", 10)
                ? new ItemStack(compound.getCompoundTag("DisplayStack")) : ItemStack.EMPTY;
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        return writeToNBT(new NBTTagCompound());
    }

    @Nullable
    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity packet) {
        readFromNBT(packet.getNbtCompound());
    }
}
