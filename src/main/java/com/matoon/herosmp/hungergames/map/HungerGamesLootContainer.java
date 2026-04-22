package com.matoon.herosmp.hungergames.map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ClickType;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

/**
 * Custom container for the HG loot pool editor.
 *
 * Slot layout (container indices):
 *   0-3   Tab buttons  — click to switch active tab; items locked
 *   4-8   Filler panes — items locked
 *   9-53  Loot grid    — normal item placement/removal
 *   54-80 Player inventory rows 1-3
 *   81-89 Player hotbar
 */
public class HungerGamesLootContainer extends Container {

    private static final int HEADER_START = 0;
    private static final int HEADER_END   = 8;
    private static final int GRID_START   = 9;
    private static final int GRID_END     = 50;  // slots 51-53 are pagination controls
    private static final int SLOT_PREV    = HungerGamesMapLootInventory.SLOT_PREV;
    private static final int SLOT_PAGE    = HungerGamesMapLootInventory.SLOT_PAGE;
    private static final int SLOT_NEXT    = HungerGamesMapLootInventory.SLOT_NEXT;
    private static final int PLAYER_START = 54;
    private static final int PLAYER_END   = 89;

    private final HungerGamesMapLootInventory lootInventory;

    public HungerGamesLootContainer(InventoryPlayer playerInv, HungerGamesMapLootInventory lootInv) {
        this.lootInventory = lootInv;

        // Header slots 0-8: fully locked.
        for (int i = 0; i <= HEADER_END; i++) {
            addSlotToContainer(new Slot(lootInv, i, 8 + (i % 9) * 18, 18) {
                @Override public boolean canTakeStack(EntityPlayer p) { return false; }
                @Override public boolean isItemValid(ItemStack s)     { return false; }
            });
        }

        // Loot grid slots 9-50 (normal) + pagination slots 51-53 (locked).
        for (int row = 1; row < 6; row++) {
            for (int col = 0; col < 9; col++) {
                final int index = col + row * 9;
                if (index >= SLOT_PREV) {
                    // Pagination slot — locked.
                    addSlotToContainer(new Slot(lootInv, index, 8 + col * 18, 18 + row * 18) {
                        @Override public boolean canTakeStack(EntityPlayer p) { return false; }
                        @Override public boolean isItemValid(ItemStack s)     { return false; }
                    });
                } else {
                    addSlotToContainer(new Slot(lootInv, index, 8 + col * 18, 18 + row * 18));
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
        // Drag: block any drag targeting a locked slot (header or pagination).
        if (clickTypeIn == ClickType.QUICK_CRAFT) {
            boolean locked = (slotId >= HEADER_START && slotId <= HEADER_END)
                          || slotId == SLOT_PREV || slotId == SLOT_PAGE || slotId == SLOT_NEXT;
            if (locked) {
                super.slotClick(slotId, 0, ClickType.QUICK_CRAFT, player);
                return ItemStack.EMPTY;
            }
            return super.slotClick(slotId, dragType, clickTypeIn, player);
        }

        // Tab buttons: switch tab.
        if (slotId >= HEADER_START && slotId <= 3) {
            lootInventory.setActiveTab(slotId);
            if (player instanceof EntityPlayerMP) detectAndSendChanges();
            return ItemStack.EMPTY;
        }
        // Filler slots: no-op.
        if (slotId >= 4 && slotId <= HEADER_END) {
            return ItemStack.EMPTY;
        }

        // Pagination controls.
        if (slotId == SLOT_PREV) {
            lootInventory.prevPage();
            if (player instanceof EntityPlayerMP) detectAndSendChanges();
            return ItemStack.EMPTY;
        }
        if (slotId == SLOT_PAGE) {
            return ItemStack.EMPTY; // indicator — no action
        }
        if (slotId == SLOT_NEXT) {
            lootInventory.nextPage();
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
            // Player → loot grid
            if (!mergeItemStack(stack, GRID_START, GRID_END + 1, false)) return ItemStack.EMPTY;
        } else if (index >= GRID_START && index <= GRID_END) {
            // Loot grid → player hotbar, then main inventory
            if (!mergeItemStack(stack, PLAYER_START + 27, PLAYER_END + 1, false)
                    && !mergeItemStack(stack, PLAYER_START, PLAYER_START + 27, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            return ItemStack.EMPTY; // header slots blocked
        }

        if (stack.isEmpty()) src.putStack(ItemStack.EMPTY);
        else src.onSlotChanged();

        if (stack.getCount() == original.getCount()) return ItemStack.EMPTY;
        src.onTake(player, stack);
        return original;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return lootInventory.isUsableByPlayer(player);
    }

    public HungerGamesMapLootInventory getLootInventory() { return lootInventory; }
}
