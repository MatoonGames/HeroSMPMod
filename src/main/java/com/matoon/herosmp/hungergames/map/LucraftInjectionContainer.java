package com.matoon.herosmp.hungergames.map;

import com.matoon.herosmp.integration.LucraftInjectionEntry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Container for the tabbed injection pool editor chest.
 *
 * Header row (slots 0-8): tabs 0-3 are clickable to switch phases; 4-8 are locked fillers.
 * Grid slots (9-50): accept only valid lucraftcore:injection items.
 * Pagination slots (51-53): locked.
 */
public class LucraftInjectionContainer extends Container {

    private static final int TAB_START    = 0;
    private static final int TAB_END      = 3;
    private static final int HEADER_START = 0;
    private static final int HEADER_END   = 8;
    private static final int GRID_START   = LucraftInjectionInventory.GRID_START;
    private static final int GRID_END     = 50;
    private static final int SLOT_PREV    = LucraftInjectionInventory.SLOT_PREV;
    private static final int SLOT_PAGE    = LucraftInjectionInventory.SLOT_PAGE;
    private static final int SLOT_NEXT    = LucraftInjectionInventory.SLOT_NEXT;
    private static final int PLAYER_START = 54;
    private static final int PLAYER_END   = 89;

    private final LucraftInjectionInventory injInv;

    public LucraftInjectionContainer(InventoryPlayer playerInv, LucraftInjectionInventory inv) {
        this.injInv = inv;

        // Header slots 0-8: tabs (0-3) and fillers (4-8) — all locked from item movement.
        for (int i = 0; i <= HEADER_END; i++) {
            addSlotToContainer(new Slot(inv, i, 8 + (i % 9) * 18, 18) {
                @Override public boolean canTakeStack(EntityPlayer p) { return false; }
                @Override public boolean isItemValid(ItemStack s)     { return false; }
            });
        }

        // Grid slots 9-50 and pagination slots 51-53.
        for (int row = 1; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                final int index = col + row * 9;
                if (index >= SLOT_PREV) {
                    // Pagination slots — locked.
                    addSlotToContainer(new Slot(inv, index, 8 + col * 18, 18 + row * 18) {
                        @Override public boolean canTakeStack(EntityPlayer p) { return false; }
                        @Override public boolean isItemValid(ItemStack s)     { return false; }
                    });
                } else {
                    // Grid slots — only accept real lucraftcore:injection items.
                    addSlotToContainer(new Slot(inv, index, 8 + col * 18, 18 + row * 18) {
                        @Override public boolean isItemValid(ItemStack s) {
                            return LucraftInjectionEntry.isValidInjection(s);
                        }
                    });
                }
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
        if (clickTypeIn == ClickType.QUICK_CRAFT) {
            boolean locked = (slotId >= HEADER_START && slotId <= HEADER_END)
                          || slotId == SLOT_PREV || slotId == SLOT_PAGE || slotId == SLOT_NEXT;
            if (locked) {
                super.slotClick(slotId, 0, ClickType.QUICK_CRAFT, player);
                return ItemStack.EMPTY;
            }
            return super.slotClick(slotId, dragType, clickTypeIn, player);
        }

        // Tab buttons (slots 0-3): switch phase tab.
        if (slotId >= TAB_START && slotId <= TAB_END) {
            injInv.setActiveTab(slotId);
            if (player instanceof EntityPlayerMP) detectAndSendChanges();
            return ItemStack.EMPTY;
        }

        // Remaining header fillers (slots 4-8): no-op.
        if (slotId >= 4 && slotId <= HEADER_END) {
            return ItemStack.EMPTY;
        }

        // Pagination controls.
        if (slotId == SLOT_PREV) {
            injInv.prevPage();
            if (player instanceof EntityPlayerMP) detectAndSendChanges();
            return ItemStack.EMPTY;
        }
        if (slotId == SLOT_PAGE) {
            return ItemStack.EMPTY;
        }
        if (slotId == SLOT_NEXT) {
            injInv.nextPage();
            if (player instanceof EntityPlayerMP) detectAndSendChanges();
            return ItemStack.EMPTY;
        }

        return super.slotClick(slotId, dragType, clickTypeIn, player);
    }

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        if (index < 0) return ItemStack.EMPTY;
        Slot src = index < inventorySlots.size() ? inventorySlots.get(index) : null;
        if (src == null || !src.getHasStack()) return ItemStack.EMPTY;

        ItemStack stack    = src.getStack();
        ItemStack original = stack.copy();

        if (index >= PLAYER_START && index <= PLAYER_END) {
            // Shift-click from player inventory → try to put into grid.
            if (!mergeItemStack(stack, GRID_START, GRID_END + 1, false)) return ItemStack.EMPTY;
        } else if (index >= GRID_START && index <= GRID_END) {
            // Shift-click from grid → try to send to player inventory.
            if (!mergeItemStack(stack, PLAYER_START + 27, PLAYER_END + 1, false)
                    && !mergeItemStack(stack, PLAYER_START, PLAYER_START + 27, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) src.putStack(ItemStack.EMPTY);
        else src.onSlotChanged();

        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        src.onTake(player, stack);
        return original;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return injInv.isUsableByPlayer(player);
    }

    public LucraftInjectionInventory getInjectionInventory() { return injInv; }
}
