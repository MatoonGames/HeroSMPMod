package com.matoon.herosmp.block;

import com.matoon.herosmp.tileentity.TileEntityCrownfallPodium;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumBlockRenderType;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import javax.annotation.Nullable;

/** A permanent, unbreakable objective pedestal used by Crownfall temples. */
public class BlockCrownfallPodium extends BlockContainer {
    private static final AxisAlignedBB BOUNDS = new AxisAlignedBB(0.0D, 0.0D, 0.0D, 1.0D, 0.875D, 1.0D);

    public BlockCrownfallPodium() {
        super(Material.ROCK);
        setRegistryName("herosmp", "crownfall_podium");
        setTranslationKey("herosmp.crownfall_podium");
        setBlockUnbreakable();
        setResistance(6000000.0F);
    }

    @Nullable
    @Override
    public TileEntity createNewTileEntity(World worldIn, int meta) {
        return new TileEntityCrownfallPodium();
    }

    @Override
    public EnumBlockRenderType getRenderType(IBlockState state) {
        return EnumBlockRenderType.MODEL;
    }

    @Override
    public boolean isOpaqueCube(IBlockState state) {
        return false;
    }

    @Override
    public boolean isFullCube(IBlockState state) {
        return false;
    }

    @Override
    public BlockRenderLayer getRenderLayer() {
        return BlockRenderLayer.CUTOUT;
    }

    @Override
    public AxisAlignedBB getBoundingBox(IBlockState state, IBlockAccess source, BlockPos pos) {
        return BOUNDS;
    }
}
