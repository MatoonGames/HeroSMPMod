package com.matoon.herosmp.client.gui.editor;

import com.matoon.herosmp.client.gui.GuiKitDownload;
import com.matoon.herosmp.client.gui.KitAvatar;
import com.matoon.herosmp.client.gui.bedrock.BedrockScreen;
import com.matoon.herosmp.client.gui.bedrock.BedrockTextField;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI.Style;
import com.matoon.herosmp.client.store.InstalledKits;
import com.matoon.herosmp.network.PacketKitUpload;
import com.matoon.herosmp.npc.kit.KitDefinition;
import com.matoon.herosmp.npc.kit.KitLibrary;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;
import org.lwjgl.input.Keyboard;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Visual kit editor. The kit is laid out like a player inventory (armor, offhand, 27 main slots and
 * the hotbar, the same 41 slots as {@link KitDefinition}); items are added through the item picker,
 * moved or swapped by drag and drop, and their stack size set in the inspector. Kits are saved to the
 * shared {@link KitLibrary} and can be made available to every world or any set of worlds. Editing a
 * kit that was saved inside one world moves it into the library.
 *
 * Works the same on dedicated servers: Download saves the kit file (the store's kit template), and
 * Publish to Server sends it to the connected server's own kit library (operators only).
 */
public class GuiKitEditor extends BedrockScreen {

    private static final int SLOT = 22, GAP = 2, PANEL_W = 190;
    private static final int SLOTS = 41;
    // KitDefinition layout: 0-8 hotbar, 9-35 main, 36 feet, 37 legs, 38 chest, 39 head, 40 offhand.
    private static final int[] ARMOR_SLOTS = {39, 38, 37, 36};
    private static final String[] ARMOR_LABELS = {"Head", "Chest", "Legs", "Feet"};
    private static final int OFFHAND = 40;

    // Where the kit came from.
    private final String originalKey;       // library key being edited, or null for a new kit
    private final File sourceWorld;         // world the kit was saved in (moved into the library on save)
    private final String sourceWorldKey;

    // Model.
    private final ItemStack[] slots = new ItemStack[SLOTS];
    private ItemStack icon = ItemStack.EMPTY;
    /** Optional LucraftCore injection granted with the kit (empty = none). */
    private ItemStack power = ItemStack.EMPTY;
    private boolean enabled = true;
    private boolean allWorlds = true;
    private final Set<String> worlds = new LinkedHashSet<String>();
    private boolean dirty;

    // UI.
    private BedrockTextField nameField;
    private String name;
    private NumberStepper countStepper;
    private int selected = -1;
    private int dragFrom = -1;
    private boolean dragging;
    private int pressX, pressY;
    private final int[][] slotRects = new int[SLOTS][];
    private List<InstalledKits.WorldInfo> knownWorlds;
    private final SmoothScroll panelScroll = new SmoothScroll();
    private float panelContentH;
    private int panelX;

    private GuiKitEditor(GuiScreen parent, String title, String originalKey, File sourceWorld, String sourceWorldKey) {
        super(parent, title);
        this.originalKey = originalKey;
        this.sourceWorld = sourceWorld;
        this.sourceWorldKey = sourceWorldKey;
        for (int i = 0; i < SLOTS; i++) slots[i] = ItemStack.EMPTY;
    }

    /** A brand-new, empty kit. */
    public static GuiKitEditor create(GuiScreen parent) {
        GuiKitEditor e = new GuiKitEditor(parent, "New Kit", null, null, null);
        e.name = "";
        e.dirty = true;
        return e;
    }

    /** Edits a kit from the library ({@code worldDir == null}) or one saved inside a world. */
    public static GuiKitEditor edit(GuiScreen parent, InstalledKits.Kit kit, File worldDir) {
        boolean inWorld = worldDir != null;
        GuiKitEditor e = new GuiKitEditor(parent, "Edit Kit", inWorld ? null : kit.key, worldDir, inWorld ? kit.key : null);
        KitDefinition def = KitDefinition.deserialize(kit.tag);
        e.name = kit.name();
        e.icon = def.getIcon();
        e.power = def.getPower();
        e.enabled = kit.enabled;
        NonNullList<ItemStack> contents = def.getContentsCopy();
        for (int i = 0; i < SLOTS && i < contents.size(); i++) e.slots[i] = contents.get(i);
        if (inWorld) {
            e.allWorlds = false;
            e.worlds.add(worldDir.getName());
            e.dirty = true; // saving moves it into the library
        } else {
            e.allWorlds = kit.worlds.isEmpty();
            e.worlds.addAll(kit.worlds);
        }
        return e;
    }

    @Override
    public void initGui() {
        super.initGui();
        if (nameField == null) {
            nameField = BedrockTextField.text(fontRenderer, 32, s -> { name = s; dirty = true; });
            nameField.setText(name);
            countStepper = new NumberStepper(this, 1, 64);
        }
        if (knownWorlds == null) knownWorlds = InstalledKits.worlds();
    }

    // ---------------------------------------------------------------------
    // Model helpers
    // ---------------------------------------------------------------------

    private String key() {
        return KitLibrary.normalizeKey(name);
    }

    private int itemCount() {
        int n = 0;
        for (ItemStack s : slots) if (!s.isEmpty()) n++;
        return n;
    }

    private KitDefinition buildKit() {
        NonNullList<ItemStack> contents = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        for (int i = 0; i < SLOTS; i++) contents.set(i, slots[i].copy());
        ItemStack ic = icon;
        if (ic.isEmpty()) {
            for (ItemStack s : slots) if (!s.isEmpty()) { ic = s; break; }
        }
        if (ic.isEmpty()) ic = new ItemStack(Items.IRON_SWORD);
        ItemStack iconCopy = ic.copy();
        iconCopy.setCount(1);
        KitDefinition kit = new KitDefinition(key(), name.trim(), iconCopy, contents);
        kit.setEnabled(enabled);
        kit.setPower(power);
        return kit;
    }

    private List<String> worldList() {
        return allWorlds ? Collections.<String>emptyList() : new ArrayList<String>(worlds);
    }

    private void set(int slot, ItemStack stack) {
        slots[slot] = stack == null ? ItemStack.EMPTY : stack;
        dirty = true;
    }

    private String slotName(int i) {
        if (i == OFFHAND) return "Offhand";
        for (int a = 0; a < ARMOR_SLOTS.length; a++) if (ARMOR_SLOTS[a] == i) return ARMOR_LABELS[a];
        if (i < 9) return "Hotbar " + (i + 1);
        return "Inventory " + (i - 8);
    }

    // ---------------------------------------------------------------------
    // Saving / exporting
    // ---------------------------------------------------------------------

    /** Checks the kit can be saved; toasts the reason if not. */
    private boolean validate() {
        if (key().isEmpty()) {
            toastWarn("Give the kit a name first");
            return false;
        }
        if (itemCount() == 0) {
            toastWarn("Add at least one item");
            return false;
        }
        if (!allWorlds && worlds.isEmpty()) {
            toastWarn("Pick at least one world, or choose All worlds");
            return false;
        }
        return true;
    }

    private void save(final Runnable after) {
        if (!validate()) return;
        boolean clash = !key().equals(originalKey) && KitLibrary.get(key()) != null;
        if (clash) {
            confirm("Replace \"" + name.trim() + "\"?", "Your Kit Library already has a kit with this name. Saving replaces it.",
                    "Replace", Style.DANGER, () -> doSave(after));
        } else {
            doSave(after);
        }
    }

    private void doSave(Runnable after) {
        try {
            KitDefinition kit = buildKit();
            KitLibrary.write(kit.serialize(), worldList());
            if (originalKey != null && !originalKey.equals(kit.getKey())) KitLibrary.delete(originalKey); // renamed
            if (sourceWorld != null) InstalledKits.removeFromWorld(sourceWorld, sourceWorldKey); // moved into the library
            dirty = false;
            toast("Saved " + kit.getDisplayName() + " to your Kit Library");
            if (after != null) after.run();
        } catch (IOException e) {
            toast("Couldn't save: " + e.getMessage());
        }
    }

    private void download() {
        if (!validate()) return;
        try {
            KitDefinition kit = buildKit();
            // The template carries no world list: whoever installs it picks their own worlds.
            GuiKitDownload.local(this, kit.getDisplayName(), KitLibrary.toFileBytes(kit, Collections.<String>emptyList()));
        } catch (IOException e) {
            toast("Couldn't create the file: " + e.getMessage());
        }
    }

    /** Saves (leaving for the test world would lose unsaved edits), then confirms and launches. */
    private void playtest() {
        if (!validate()) return;
        Runnable go = () -> com.matoon.herosmp.client.gui.Playtest.kit(this, buildKit().serialize(), name.trim());
        if (dirty) save(go);
        else go.run();
    }

    private boolean onRemoteServer() {
        return mc.world != null && mc.getIntegratedServer() == null;
    }

    private void publish() {
        if (!validate()) return;
        confirm("Publish to this server?", "Adds \"" + name.trim() + "\" to the connected server's kit library, "
                        + "available in all of its worlds. Requires operator permission on the server.",
                "Publish", Style.PRIMARY, () -> {
                    try {
                        PacketKitUpload.send(KitLibrary.toFileBytes(buildKit(), Collections.<String>emptyList()));
                        toast("Sent to the server (see chat for the result)");
                    } catch (IOException e) {
                        toast("Couldn't publish: " + e.getMessage());
                    }
                });
    }

    private void copyInventory() {
        if (mc.player == null) return;
        InventoryPlayer inv = mc.player.inventory;
        for (int i = 0; i < 36; i++) slots[i] = inv.mainInventory.get(i).copy();
        for (int i = 0; i < 4; i++) slots[36 + i] = inv.armorInventory.get(i).copy();
        slots[OFFHAND] = inv.offHandInventory.get(0).copy();
        dirty = true;
        selected = -1;
        toast("Copied your inventory into the kit");
    }

    @Override
    protected void goBack() {
        if (!dirty) {
            super.goBack();
            return;
        }
        confirm("Unsaved changes", "Save this kit to your Kit Library before leaving?",
                "Discard", () -> mc.displayGuiScreen(parent),
                "Save", Style.PRIMARY, () -> save(() -> mc.displayGuiScreen(parent)));
    }

    // ---------------------------------------------------------------------
    // Header
    // ---------------------------------------------------------------------

    @Override
    protected int headerTitleLimit() {
        return width - 8 - 70 - 8 - (dirty ? BedrockUI.chipWidth(fontRenderer, "UNSAVED") + 8 : 0);
    }

    @Override
    protected void drawHeaderExtras() {
        int sx = width - 8 - 70;
        button(sx, 4, 70, 22, "Save", Style.PRIMARY, dirty, () -> save(null));
        if (dirty) {
            int cw = BedrockUI.chipWidth(fontRenderer, "UNSAVED");
            BedrockUI.chip(fontRenderer, sx - 8 - cw, 9, "UNSAVED", BedrockUI.GOLD, BedrockUI.TEXT_DARK);
        }
    }

    // ---------------------------------------------------------------------
    // Body
    // ---------------------------------------------------------------------

    @Override
    protected void drawBody(float partialTicks) {
        int x = 10, w = width - 20;
        int y = HEADER_H + 8;
        int h = height - y - 8;
        BedrockUI.rect(x, y, w, h, BedrockUI.OUTLINE);
        BedrockUI.rect(x + 1, y + 1, w - 2, h - 2, BedrockUI.PANEL);

        int pw = Math.min(PANEL_W, w / 2);
        panelX = x + w - pw - 8;
        int lx = x + 10, lw = panelX - 10 - lx;
        drawKitColumn(lx, y + 10, lw);
        drawInspector(panelX, y + 8, pw, h - 16);

        // Item being dragged follows the cursor.
        if (dragging && dragFrom >= 0 && !slots[dragFrom].isEmpty()) {
            BedrockUI.clearDepth();
            BedrockUI.item(slots[dragFrom], mx - 8, my - 8, 1f);
        }
    }

    private void drawKitColumn(int x, int y, int w) {
        // Name, icon, enabled.
        fontRenderer.drawString("Kit name", x, y, BedrockUI.TEXT_DIM, false);
        int fieldW = Math.min(220, w - 120);
        nameField.draw(this, x, y + 11, fieldW, 20, "e.g. Archer");
        String keyHint = key().isEmpty() ? "" : "ID: " + key();
        fontRenderer.drawString(BedrockUI.trim(fontRenderer, keyHint, fieldW), x, y + 35, BedrockUI.TEXT_MUTED, false);

        int iconX = x + fieldW + 12;
        fontRenderer.drawString("Icon", iconX, y, BedrockUI.TEXT_DIM, false);
        boolean iconHov = hover(iconX, y + 10, SLOT, SLOT);
        BedrockUI.slot(iconX, y + 10, SLOT, false, iconHov);
        ItemStack shownIcon = icon.isEmpty() ? firstItem() : icon;
        if (!shownIcon.isEmpty()) BedrockUI.item(shownIcon, iconX + 3, y + 13, 1f);
        hit(iconX, y + 10, SLOT, SLOT, () -> openOverlay(ItemPicker.single(this, ItemPicker.Filter.ALL, "Choose the kit's icon",
                st -> { icon = st; dirty = true; closeOverlay(); })));
        if (iconHov) setTooltip("Kit icon", icon.isEmpty() ? "§7Uses the first item until you pick one" : "§7Click to change");

        int togX = iconX + SLOT + 14;
        fontRenderer.drawString("Enabled", togX, y, BedrockUI.TEXT_DIM, false);
        toggle(togX, y + 12, enabled, true, () -> { enabled = !enabled; dirty = true; });

        // Inventory grid.
        y += 52;
        int cols = 9;
        int gridW = cols * SLOT + (cols - 1) * GAP;
        int gx = x;

        fontRenderer.drawString("Armor & Offhand", gx, y, BedrockUI.TEXT_DIM, false);
        y += 11;
        for (int a = 0; a < 4; a++) drawSlot(ARMOR_SLOTS[a], gx + a * (SLOT + GAP), y, ARMOR_LABELS[a].substring(0, 1));
        drawSlot(OFFHAND, gx + 4 * (SLOT + GAP) + 10, y, "O");
        String count = itemCount() + " / " + SLOTS + " slots used";
        fontRenderer.drawString(count, gx + gridW - fontRenderer.getStringWidth(count), y + 7, BedrockUI.TEXT_MUTED, false);
        y += SLOT + 10;

        fontRenderer.drawString("Inventory", gx, y, BedrockUI.TEXT_DIM, false);
        y += 11;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) drawSlot(9 + r * 9 + c, gx + c * (SLOT + GAP), y + r * (SLOT + GAP), null);
        }
        y += 3 * (SLOT + GAP) + 8;

        fontRenderer.drawString("Hotbar", gx, y, BedrockUI.TEXT_DIM, false);
        y += 11;
        for (int c = 0; c < 9; c++) drawSlot(c, gx + c * (SLOT + GAP), y, String.valueOf(c + 1));
        y += SLOT + 10;

        fontRenderer.drawString(BedrockUI.trim(fontRenderer,
                "Click a slot to add an item. Drag to move or swap. Right-click or Delete clears a slot.", w),
                gx, y, BedrockUI.TEXT_MUTED, false);
    }

    private ItemStack firstItem() {
        for (ItemStack s : slots) if (!s.isEmpty()) return s;
        return ItemStack.EMPTY;
    }

    private void drawSlot(int i, int x, int y, String ghost) {
        slotRects[i] = new int[]{x, y, SLOT, SLOT};
        boolean hov = hover(x, y, SLOT, SLOT);
        BedrockUI.slot(x, y, SLOT, i == selected, hov || (dragging && hov));
        ItemStack st = slots[i];
        if (!st.isEmpty() && !(dragging && i == dragFrom)) {
            BedrockUI.item(st, x + 3, y + 3, 1f);
            if (st.getCount() > 1) {
                String c = String.valueOf(st.getCount());
                fontRenderer.drawString(c, x + SLOT - 2 - fontRenderer.getStringWidth(c), y + SLOT - 10, 0xFFFFFFFF, true);
            }
        } else if (st.isEmpty() && ghost != null) {
            fontRenderer.drawString(ghost, x + (SLOT - fontRenderer.getStringWidth(ghost)) / 2, y + 7, 0x40FFFFFF, false);
        }
        if (hov && !dragging) {
            if (!st.isEmpty()) setTooltip(ItemPicker.displayName(st) + (st.getCount() > 1 ? " x" + st.getCount() : ""), "§7" + slotName(i));
            else setTooltip(slotName(i), "§7Empty: click to add an item");
        }
    }

    // ---------------------------------------------------------------------
    // Inspector
    // ---------------------------------------------------------------------

    private void drawInspector(int x, int y, int w, int h) {
        EditorTab.panel(x, y, w, h);
        panelScroll.clamp(panelContentH - (h - 8));
        BedrockUI.pushClip(x + 1, y + 1, w - 2, h - 2);
        int ix = x + 8, iw = w - 16;
        int iy = y + 8 - (int) panelScroll.value();
        int start = iy;

        // Live preview of the kit's figure; hover to see it swing.
        int ph = 104;
        BedrockUI.rect(ix, iy, iw, ph, BedrockUI.OUTLINE);
        BedrockUI.rect(ix + 1, iy + 1, iw - 2, ph - 2, 0xFF1A1B1D);
        KitAvatar.draw(KitAvatar.Loadout.of(slots), "editor", ix + 1, iy + 1, iw - 2, ph - 2, hover(ix, iy, iw, ph));
        BedrockUI.chip(fontRenderer, ix + 4, iy + 4, "PREVIEW", BedrockUI.PANEL_LIGHT, BedrockUI.TEXT_DIM);
        if (!power.isEmpty()) {
            String pn = BedrockUI.trim(fontRenderer, ItemPicker.displayName(power).toUpperCase(), iw - 16);
            int cw = BedrockUI.chipWidth(fontRenderer, pn);
            BedrockUI.chip(fontRenderer, ix + iw - 4 - cw, iy + ph - 16, pn, 0xFF7A3FC0, BedrockUI.TEXT);
        }
        iy += ph + 10;

        // Superpower.
        iy = EditorTab.heading(this, ix, iy, iw, "Superpower");
        iy = drawPowerCard(ix, iy, iw) + 8;

        // Selected slot.
        if (selected >= 0) {
            final int sel = selected;
            iy = EditorTab.heading(this, ix, iy, iw, slotName(sel));
            ItemStack st = slots[sel];
            if (!st.isEmpty()) {
                BedrockUI.slot(ix, iy, 34, true, false);
                BedrockUI.item(st, ix + 1, iy + 1, 2f);
                List<String> lines = fontRenderer.listFormattedStringToWidth(ItemPicker.displayName(st), iw - 40);
                for (int i = 0; i < Math.min(3, lines.size()); i++) fontRenderer.drawString(lines.get(i), ix + 40, iy + 3 + i * 10, BedrockUI.TEXT, true);
                iy += 40;
                final int max = Math.max(1, st.getMaxStackSize());
                if (max > 1) {
                    iy += countStepper.draw(this, ix, iy, iw, "Amount", st.getCount(), v -> {
                        ItemStack c = slots[sel].copy();
                        c.setCount(Math.max(1, Math.min(max, v)));
                        set(sel, c);
                    }) + 2;
                    // Quick amounts.
                    int[] quick = max >= 64 ? new int[]{1, 16, 32, max} : max >= 16 ? new int[]{1, max / 2, max} : new int[]{1, max};
                    int qw = (iw - (quick.length - 1) * 4) / quick.length;
                    for (int q = 0; q < quick.length; q++) {
                        final int amount = quick[q];
                        boolean now = st.getCount() == amount;
                        button(ix + q * (qw + 4), iy, qw, 16, amount == max ? "Max" : String.valueOf(amount),
                                now ? Style.PRIMARY : Style.DARK, true, () -> {
                                    ItemStack c = slots[sel].copy();
                                    c.setCount(amount);
                                    set(sel, c);
                                });
                    }
                    iy += 20;
                } else {
                    fontRenderer.drawString("Doesn't stack", ix, iy + 2, BedrockUI.TEXT_MUTED, false);
                    iy += 14;
                }
                int half = (iw - 6) / 2;
                button(ix, iy, half, 20, "Change", Style.DARK, true, () -> pickInto(sel));
                button(ix + half + 6, iy, iw - half - 6, 20, "Clear", Style.DANGER, true, () -> set(sel, ItemStack.EMPTY));
                iy += 30;
            } else {
                iy = EditorTab.note(this, ix, iy, iw, "This slot is empty.", BedrockUI.TEXT_MUTED) + 4;
                button(ix, iy, iw, 20, "Add Item", Style.PRIMARY, true, () -> pickInto(sel));
                iy += 30;
            }
        } else {
            iy = EditorTab.heading(this, ix, iy, iw, "Kit Contents");
            iy = EditorTab.note(this, ix, iy, iw, "Select a slot on the left to add or change its item.", BedrockUI.TEXT_MUTED) + 8;
        }

        // Availability.
        iy = EditorTab.heading(this, ix, iy, iw, "Available In");
        toggle(ix, iy, allWorlds, true, () -> { allWorlds = !allWorlds; dirty = true; });
        fontRenderer.drawString("All worlds", ix + TOGGLE_W + 8, iy + 5, BedrockUI.TEXT, false);
        iy += TOGGLE_H + 6;
        if (!allWorlds) {
            if (knownWorlds.isEmpty()) {
                iy = EditorTab.note(this, ix, iy, iw, "No singleplayer worlds yet.", BedrockUI.TEXT_MUTED) + 4;
            }
            for (final InstalledKits.WorldInfo wi : knownWorlds) {
                final boolean on = worlds.contains(wi.folder());
                boolean hov = hover(ix, iy, iw, 18);
                if (hov) BedrockUI.rect(ix - 2, iy - 1, iw + 4, 18, 0xFF323335);
                BedrockUI.rect(ix, iy + 2, 12, 12, BedrockUI.OUTLINE);
                BedrockUI.rect(ix + 1, iy + 3, 10, 10, on ? BedrockUI.GREEN : 0xFF161718);
                if (on) BedrockUI.check(ix + 1, iy + 5, 0xFFFFFFFF);
                fontRenderer.drawString(BedrockUI.trim(fontRenderer, wi.name, iw - 18), ix + 17, iy + 4, BedrockUI.TEXT, false);
                hit(ix - 2, iy - 1, iw + 4, 18, () -> {
                    if (on) worlds.remove(wi.folder());
                    else worlds.add(wi.folder());
                    dirty = true;
                });
                iy += 19;
            }
        }
        iy = EditorTab.note(this, ix, iy + 2, iw, "Servers: use Publish to Server, or put the downloaded file in the "
                + "server's beyonder_kits folder.", BedrockUI.TEXT_MUTED) + 10;

        // Tools.
        iy = EditorTab.heading(this, ix, iy, iw, "Tools");
        button(ix, iy, iw, 20, "Copy My Inventory", Style.DARK, mc.player != null, this::copyInventory);
        if (mc.player == null && hover(ix, iy, iw, 20)) setTooltip("Join a world to copy your inventory");
        iy += 24;
        button(ix, iy, iw, 20, "Clear All Slots", Style.DARK, itemCount() > 0, () -> confirm("Clear the kit?",
                "Removes every item from this kit (not saved until you press Save).", "Clear", Style.DANGER, () -> {
                    for (int i = 0; i < SLOTS; i++) slots[i] = ItemStack.EMPTY;
                    selected = -1;
                    dirty = true;
                }));
        iy += 24;
        button(ix, iy, iw, 20, dirty ? "Save & Playtest" : "Playtest", Style.PRIMARY, true, this::playtest);
        if (hover(ix, iy, iw, 20)) setTooltip("Playtest", "§7Try the kit on test dummies, then in a solo PVP match");
        iy += 24;
        button(ix, iy, iw, 20, "Download File", Style.DARK, true, this::download);
        if (hover(ix, iy, iw, 20)) setTooltip("Save as a kit file", "§7Upload it to the store's PVP Kits folder, or a server's beyonder_kits");
        iy += 24;
        if (onRemoteServer()) {
            button(ix, iy, iw, 20, "Publish to Server", Style.PRIMARY, true, this::publish);
            iy += 24;
        }

        panelContentH = iy - start + 8;
        BedrockUI.popClip();
    }

    /** The kit's superpower: a card naming it, or an invitation to add one. Returns the bottom y. */
    private int drawPowerCard(int x, int y, int w) {
        int h = 30;
        boolean has = !power.isEmpty();
        boolean hov = hover(x, y, w, h);
        float t = (System.currentTimeMillis() % 100000L) / 1000f;
        BedrockUI.rect(x, y, w, h, has ? 0xFF7A3FC0 : (hov ? 0xFF5E5F61 : BedrockUI.OUTLINE));
        BedrockUI.rect(x + 1, y + 1, w - 2, h - 2, has ? 0xFF2E2140 : (hov ? 0xFF323335 : 0xFF1E1F21));
        if (has) {
            BedrockUI.glow(x, y, w, h, 0xB57EDC, 0.35f + 0.15f * (float) Math.sin(t * 3));
            BedrockUI.item(power, x + 6, y + 7, 1f);
            String n = BedrockUI.trim(fontRenderer, ItemPicker.displayName(power), w - 52);
            fontRenderer.drawString(n, x + 27, y + 6, 0xFFFFFFFF, true);
            fontRenderer.drawString("Granted on pick", x + 27, y + 17, 0xFFB9A8D8, false);
            button(x + w - 22, y + 5, 18, 20, "x", Style.DARK, true, () -> { power = ItemStack.EMPTY; dirty = true; });
            if (hover(x + w - 22, y + 5, 18, 20)) setTooltip("Remove the superpower");
            hit(x, y, w - 24, h, this::pickPower);
            if (hover(x, y, w - 24, h)) setTooltip(ItemPicker.displayName(power),
                    "§7Players get this LucraftCore power when they", "§7pick the kit. It's removed when the round ends.", "§8Click to change");
        } else {
            fontRenderer.drawString("+ Add a superpower", x + 8, y + 6, hov ? 0xFFFFFFFF : BedrockUI.TEXT_DIM, false);
            fontRenderer.drawString("Optional, from LucraftCore", x + 8, y + 17, BedrockUI.TEXT_MUTED, false);
            hit(x, y, w, h, this::pickPower);
        }
        return y + h;
    }

    private void pickPower() {
        openOverlay(ItemPicker.single(this, ItemPicker.Filter.INJECTIONS, "Choose the kit's superpower", st -> {
            power = st;
            dirty = true;
            closeOverlay();
            toast(ItemPicker.displayName(st) + " will be granted with this kit");
        }));
    }

    private void pickInto(final int slot) {
        openOverlay(ItemPicker.single(this, ItemPicker.Filter.ALL, "Choose an item for " + slotName(slot), st -> {
            set(slot, st);
            selected = slot;
            closeOverlay();
        }));
    }

    // ---------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------

    private int slotAt(int x, int y) {
        for (int i = 0; i < SLOTS; i++) {
            int[] r = slotRects[i];
            if (r != null && x >= r[0] && y >= r[1] && x < r[0] + r[2] && y < r[1] + r[3]) return i;
        }
        return -1;
    }

    @Override
    protected void onMouseDown(int x, int y, int button) {
        int s = slotAt(x, y);
        if (s < 0) return;
        if (button == 1) { // right-click clears
            set(s, ItemStack.EMPTY);
            selected = s;
            return;
        }
        if (button != 0) return;
        dragFrom = s;
        dragging = false;
        pressX = x;
        pressY = y;
    }

    @Override
    protected void onDrag(int x, int y, int button) {
        if (dragFrom >= 0 && !slots[dragFrom].isEmpty() && Math.abs(x - pressX) + Math.abs(y - pressY) > 3) dragging = true;
    }

    @Override
    protected void onRelease(int x, int y, int button) {
        if (dragFrom < 0) return;
        int target = slotAt(x, y);
        if (dragging && target >= 0 && target != dragFrom) {
            ItemStack a = slots[dragFrom];
            slots[dragFrom] = slots[target];
            slots[target] = a;
            dirty = true;
            selected = target;
        } else if (!dragging) {
            if (slots[dragFrom].isEmpty()) pickInto(dragFrom); // clicking an empty slot adds an item straight away
            selected = dragFrom;
        }
        dragFrom = -1;
        dragging = false;
    }

    @Override
    protected void onScroll(int direction) {
        if (mx >= panelX) panelScroll.add(-direction * 30);
    }

    @Override
    protected void onKey(char c, int key) {
        if (key == Keyboard.KEY_S && isCtrlKeyDown()) {
            if (dirty) save(null);
            return;
        }
        if ((key == Keyboard.KEY_DELETE || key == Keyboard.KEY_BACK) && selected >= 0) set(selected, ItemStack.EMPTY);
    }
}
