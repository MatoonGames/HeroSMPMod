package com.matoon.herosmp.client.gui.editor;

import com.matoon.herosmp.client.gui.bedrock.BedrockScreen;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI.Style;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

/**
 * Visual map editor: edits an installed Hunger Games map's settings (spawns, lobby, center, border,
 * loot, powers and breakable blocks) directly in its {@code herosmp_config.dat}, without loading a
 * world. Terrain itself can't be changed here; that's what Edit in World is for.
 */
public class GuiMapEditor extends BedrockScreen {

    private enum Tab { LAYOUT, LOOT, INJECTIONS, BREAKABLE }

    private static final String[] TAB_LABELS = {"Layout", "Loot", "Powers", "Breakable Blocks"};

    final MapEditSession session;
    private Tab tab = Tab.LAYOUT;
    private final EditorTab[] tabs;
    private boolean closing;

    public GuiMapEditor(GuiScreen parent, String mapName) {
        super(parent, "Visual Editor  ·  " + mapName);
        this.session = new MapEditSession(mapName);
        this.tabs = new EditorTab[]{
                new LayoutTab(this),
                new PoolTab(this, PoolTab.Kind.LOOT),
                new PoolTab(this, PoolTab.Kind.INJECTIONS),
                new BreakableTab(this)
        };
    }

    private EditorTab current() {
        return tabs[tab.ordinal()];
    }

    // ---------------------------------------------------------------------
    // Header
    // ---------------------------------------------------------------------

    @Override
    protected int headerTitleLimit() {
        return width - 8 - 70 - 8 - (session.isDirty() ? BedrockUI.chipWidth(fontRenderer, "UNSAVED") + 8 : 0);
    }

    @Override
    protected void drawHeaderExtras() {
        int sx = width - 8 - 70;
        button(sx, 4, 70, 22, "Save", Style.PRIMARY, session.isDirty(), this::save);
        if (session.isDirty()) {
            int cw = BedrockUI.chipWidth(fontRenderer, "UNSAVED");
            BedrockUI.chip(fontRenderer, sx - 8 - cw, 9, "UNSAVED", BedrockUI.GOLD, BedrockUI.TEXT_DARK);
        }
    }

    private boolean save() {
        try {
            session.save();
            toast("Saved " + session.mapName);
            return true;
        } catch (IOException e) {
            toast("Couldn't save: " + e.getMessage());
            return false;
        }
    }

    // ---------------------------------------------------------------------
    // Body
    // ---------------------------------------------------------------------

    private static final ItemStack[] TAB_ICONS = {
            new ItemStack(Items.MAP), new ItemStack(Blocks.CHEST), new ItemStack(Items.NETHER_STAR), new ItemStack(Items.IRON_PICKAXE)
    };
    private static final int TAB_H = 26;

    @Override
    protected void drawBody(float partialTicks) {
        int x = 10, w = width - 20;
        int y = HEADER_H + 8;

        // Content panel first, so the selected tab can be drawn over its top edge and merge with it.
        int panelY = y + TAB_H - 1;
        int panelH = height - panelY - 8;
        BedrockUI.rect(x, panelY, w, panelH, BedrockUI.OUTLINE);
        BedrockUI.rect(x + 1, panelY + 1, w - 2, panelH - 2, BedrockUI.PANEL);
        drawTabs(x, y);

        int cx = x + 9, cw = w - 18;
        int cy = panelY + 9;
        if (session.droppedEntries > 0) {
            String warn = session.droppedEntries + " entr" + (session.droppedEntries == 1 ? "y uses an item" : "ies use items")
                    + " from mods that aren't installed. Saving will remove " + (session.droppedEntries == 1 ? "it." : "them.");
            BedrockUI.rect(cx, cy, cw, 16, BedrockUI.OUTLINE);
            BedrockUI.rect(cx + 1, cy + 1, cw - 2, 14, 0xFF4A3A14);
            BedrockUI.rect(cx + 1, cy + 1, 3, 14, 0xFFF2C230);
            fontRenderer.drawString(BedrockUI.trim(fontRenderer, warn, cw - 16), cx + 10, cy + 4, 0xFFFFE7A0, false);
            cy += 22;
        }

        current().draw(cx, cy, cw, panelY + panelH - 9 - cy);
    }

    /**
     * Folder-style tabs: unselected tabs sit lower and darker; the selected one is taller, the same
     * colour as the panel, has a green top accent, and overlaps the panel's top border so the two
     * read as one surface.
     */
    private void drawTabs(int x, int y) {
        int tabCount = session.lobbyMap ? 1 : TAB_LABELS.length;
        int tx = x + 6;
        for (int i = 0; i < tabCount; i++) {
            final Tab t = Tab.values()[i];
            boolean sel = tab == t;
            int tw = 16 + 6 + fontRenderer.getStringWidth(TAB_LABELS[i]) + 22;
            boolean hov = !sel && hover(tx, y, tw, TAB_H);
            if (sel) {
                BedrockUI.rect(tx, y, tw, TAB_H, BedrockUI.OUTLINE);
                BedrockUI.rect(tx + 1, y + 1, tw - 2, TAB_H, BedrockUI.PANEL); // runs over the panel border
                BedrockUI.rect(tx + 1, y + 1, tw - 2, 2, BedrockUI.GREEN_LIGHT);
            } else {
                BedrockUI.rect(tx, y + 4, tw, TAB_H - 4, BedrockUI.OUTLINE);
                BedrockUI.rect(tx + 1, y + 5, tw - 2, TAB_H - 6, hov ? 0xFF2C2D2F : 0xFF202123);
            }
            int iy = sel ? y + 6 : y + 8;
            BedrockUI.item(TAB_ICONS[i], tx + 11, iy, 1f);
            fontRenderer.drawString(TAB_LABELS[i], tx + 11 + 16 + 6, iy + 4,
                    sel ? BedrockUI.TEXT : (hov ? BedrockUI.TEXT_DIM : BedrockUI.TEXT_MUTED), sel);
            if (!sel) hit(tx, y, tw, TAB_H, () -> tab = t);
            tx += tw + 2;
        }
        if (session.lobbyMap) {
            fontRenderer.drawString("Lobby map: only the lobby spawn is used.", tx + 8, y + 12, BedrockUI.TEXT_MUTED, false);
        }
    }

    // ---------------------------------------------------------------------
    // Input routing
    // ---------------------------------------------------------------------

    @Override
    protected void onMouseDown(int x, int y, int button) { current().onMouseDown(x, y, button); }

    @Override
    protected void onDrag(int x, int y, int button) { current().onDrag(x, y, button); }

    @Override
    protected void onRelease(int x, int y, int button) { current().onRelease(x, y, button); }

    @Override
    protected void onScroll(int direction) { current().onScroll(direction); }

    @Override
    protected void onKey(char c, int key) {
        if (key == Keyboard.KEY_S && isCtrlKeyDown()) {
            if (session.isDirty()) save();
            return;
        }
        current().onKey(c, key);
    }

    // ---------------------------------------------------------------------
    // Leaving
    // ---------------------------------------------------------------------

    @Override
    protected void goBack() {
        if (!session.isDirty()) {
            close();
            return;
        }
        confirm("Unsaved changes", "Save your changes to " + session.mapName + " before leaving?",
                "Discard", this::close,
                "Save", Style.PRIMARY, () -> { if (save()) close(); });
    }

    private void close() {
        closing = true;
        mc.displayGuiScreen(parent);
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        if (closing) for (EditorTab t : tabs) t.dispose();
    }
}
