package com.matoon.herosmp.hungergames.map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Container for the all-in-one loot properties editor (54-slot chest).
 *
 * All chest slots are locked — no item movement.
 * Clicks are interpreted as:
 *   Row 0 (0-3):    switch phase tab
 *   Row 0 (4-8):    fillers — ignored
 *   Slot 9:         selected-item display — ignored
 *   Slots 10-14:    property buttons — left +1, right -1, shift ×10
 *   Slots 15-17:    fillers — ignored
 *   Slots 18-53:    item grid — click to select
 */
public class HungerGamesLootPropertiesContainer extends Container {

    private static final int CHEST_SIZE   = HungerGamesLootPropertiesInventory.SIZE; // 54
    private static final int PLAYER_START = CHEST_SIZE;       // 54
    private static final int PLAYER_END   = CHEST_SIZE + 35;  // 89

    private final HungerGamesLootPropertiesInventory propInv;

    public HungerGamesLootPropertiesContainer(InventoryPlayer playerInv,
                                               HungerGamesLootPropertiesInventory propInv) {
        this.propInv = propInv;

        // 54 chest slots — all locked.
        for (int row = 0; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                final int index = col + row * 9;
                addSlotToContainer(new Slot(propInv, index, 8 + col * 18, 18 + row * 18) {
                    @Override public boolean canTakeStack(EntityPlayer p) { return false; }
                    @Override public boolean isItemValid(ItemStack s)     { return false; }
                });
            }
        }

        // Player inventory rows 1-3 (container slots 54-80).
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlotToContainer(new Slot(playerInv, col + row * 9 + 9,
                        8 + col * 18, 140 + row * 18));
            }
        }

        // Player hotbar (container slots 81-89).
        for (int col = 0; col < 9; col++) {
            addSlotToContainer(new Slot(playerInv, col, 8 + col * 18, 198));
        }
    }

    @Override
    public ItemStack slotClick(int slotId, int dragType, ClickType clickTypeIn, EntityPlayer player) {
        // Block all drag operations on chest slots.
        if (clickTypeIn == ClickType.QUICK_CRAFT) {
            if (slotId >= 0 && slotId < CHEST_SIZE) return ItemStack.EMPTY;
            return super.slotClick(slotId, dragType, clickTypeIn, player);
        }

        // Phase tab buttons (0-3).
        if (slotId >= 0 && slotId <= 3) {
            propInv.setActiveTab(slotId);
            if (player instanceof EntityPlayerMP) detectAndSendChanges();
            return ItemStack.EMPTY;
        }

        // Filler row-0 slots (4-8), selected-item display (9), and control-row fillers (15-17): no-op.
        if (slotId >= 4 && slotId <= 9)   return ItemStack.EMPTY;
        if (slotId >= 15 && slotId <= 17) return ItemStack.EMPTY;

        // Property buttons (10-14).
        if (slotId >= HungerGamesLootPropertiesInventory.SLOT_WEIGHT
                && slotId <= HungerGamesLootPropertiesInventory.SLOT_MAX) {
            if (propInv.getSelectedIndex() < 0) return ItemStack.EMPTY;
            boolean shift = (clickTypeIn == ClickType.QUICK_MOVE);
            int magnitude = shift ? 10 : 1;
            int delta = (dragType == 1) ? -magnitude : magnitude;
            propInv.adjustProperty(slotId, delta);
            if (player instanceof EntityPlayerMP) detectAndSendChanges();
            return ItemStack.EMPTY;
        }

        // Pagination controls (51-53).
        if (slotId == HungerGamesLootPropertiesInventory.SLOT_PREV) {
            propInv.prevPage();
            if (player instanceof EntityPlayerMP) detectAndSendChanges();
            return ItemStack.EMPTY;
        }
        if (slotId == HungerGamesLootPropertiesInventory.SLOT_PAGE) {
            return ItemStack.EMPTY;
        }
        if (slotId == HungerGamesLootPropertiesInventory.SLOT_NEXT) {
            propInv.nextPage();
            if (player instanceof EntityPlayerMP) detectAndSendChanges();
            return ItemStack.EMPTY;
        }

        // Item grid (18-50): click to select.
        if (slotId >= HungerGamesLootPropertiesInventory.GRID_START
                && slotId < HungerGamesLootPropertiesInventory.SLOT_PREV) {
            propInv.selectItem(slotId);
            if (player instanceof EntityPlayerMP) detectAndSendChanges();
            return ItemStack.EMPTY;
        }

        // Player inventory slots: pass through normally.
        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        return ItemStack.EMPTY; // block all shift-clicks into/out of chest area
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return propInv.isUsableByPlayer(player);
    }

    public HungerGamesLootPropertiesInventory getPropInv() { return propInv; }
}
