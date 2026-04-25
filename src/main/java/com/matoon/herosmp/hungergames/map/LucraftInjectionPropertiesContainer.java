package com.matoon.herosmp.hungergames.map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Container for the injection properties editor (54-slot chest).
 *
 * All chest slots are locked — no item movement.
 * Clicks are interpreted as:
 *   Row 0 (0-3):    Switch phase tab
 *   Row 0 (4-8):    Fillers — ignored
 *   Slot 9:         Selected injection display — ignored
 *   Slots 10-13:    Property buttons — left +1, right -1, shift ×10
 *   Slots 14-17:    Fillers — ignored
 *   Slots 18-50:    Injection grid — click to select
 *   Slot 51:        Previous page
 *   Slot 52:        Page indicator — ignored
 *   Slot 53:        Next page
 *   Slots 54+:      Player inventory — normal behavior
 */
public class LucraftInjectionPropertiesContainer extends Container {

    private static final int CHEST_SIZE   = LucraftInjectionPropertiesInventory.SIZE; // 54
    private static final int PLAYER_START = CHEST_SIZE;       // 54
    private static final int PLAYER_END   = CHEST_SIZE + 35;  // 89

    private final LucraftInjectionPropertiesInventory propInv;

    public LucraftInjectionPropertiesContainer(InventoryPlayer playerInv,
                                                LucraftInjectionPropertiesInventory propInv) {
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

        // Filler row-0 slots (4-8), selected-display (9): no-op.
        if (slotId >= 4 && slotId <= 9) return ItemStack.EMPTY;
        // Filler slots 14-17: no-op.
        if (slotId >= 14 && slotId <= 17) return ItemStack.EMPTY;

        // Property buttons (10-13).
        if (slotId >= LucraftInjectionPropertiesInventory.SLOT_WEIGHT
                && slotId <= LucraftInjectionPropertiesInventory.SLOT_GLOB_MAX) {
            boolean shift     = (clickTypeIn == ClickType.QUICK_MOVE);
            int     magnitude = shift ? 10 : 1;
            int     delta     = (dragType == 1) ? -magnitude : magnitude;
            propInv.adjustProperty(slotId, delta);
            if (player instanceof EntityPlayerMP) detectAndSendChanges();
            return ItemStack.EMPTY;
        }

        // Pagination controls (51-53).
        if (slotId == LucraftInjectionPropertiesInventory.SLOT_PREV) {
            propInv.prevPage();
            if (player instanceof EntityPlayerMP) detectAndSendChanges();
            return ItemStack.EMPTY;
        }
        if (slotId == LucraftInjectionPropertiesInventory.SLOT_PAGE) {
            return ItemStack.EMPTY;
        }
        if (slotId == LucraftInjectionPropertiesInventory.SLOT_NEXT) {
            propInv.nextPage();
            if (player instanceof EntityPlayerMP) detectAndSendChanges();
            return ItemStack.EMPTY;
        }

        // Injection grid (18-50): click to select.
        if (slotId >= LucraftInjectionPropertiesInventory.GRID_START
                && slotId < LucraftInjectionPropertiesInventory.SLOT_PREV) {
            propInv.selectItem(slotId);
            if (player instanceof EntityPlayerMP) detectAndSendChanges();
            return ItemStack.EMPTY;
        }

        // Player inventory slots: pass through normally.
        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        return ItemStack.EMPTY; // block shift-clicks into/out of chest area
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return propInv.isUsableByPlayer(player);
    }

    public LucraftInjectionPropertiesInventory getPropInv() { return propInv; }
}
