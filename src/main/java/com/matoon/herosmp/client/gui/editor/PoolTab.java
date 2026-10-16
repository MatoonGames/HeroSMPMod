package com.matoon.herosmp.client.gui.editor;

import com.matoon.herosmp.client.gui.bedrock.BedrockScreen;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI;
import com.matoon.herosmp.client.gui.bedrock.BedrockUI.Style;
import com.matoon.herosmp.hungergames.map.HungerGamesLootEntry;
import com.matoon.herosmp.hungergames.map.HungerGamesMapConfig;
import com.matoon.herosmp.integration.LucraftInjectionEntry;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Loot or injection pools, one per phase (Phase 1/2/3 + All Phases). Left: the pool as an item grid.
 * Right: an inspector for the selected entry. Loot entries carry weight / global max / per-chest
 * max / min-max count; injections carry weight / max on map, and each phase also has a min/max
 * number of powers placed. Value ranges and the min-vs-max coupling follow the in-world
 * configure tools.
 */
final class PoolTab extends EditorTab {

    enum Kind { LOOT, INJECTIONS }

    private static final int PANEL_W = 176;

    private final Kind kind;
    private int phase = 0;
    private final int[] selected = {-1, -1, -1, -1};
    private final BedrockScreen.SmoothScroll gridScroll = new BedrockScreen.SmoothScroll();
    private final BedrockScreen.SmoothScroll panelScroll = new BedrockScreen.SmoothScroll();
    private float gridContentH, gridViewH, panelContentH, panelViewH;
    private int gridX, gridW, panelX;

    private final NumberStepper weight, globalMax, perChest, minCount, maxCount, maxOnMap, injMin, injMax;

    PoolTab(GuiMapEditor ed, Kind kind) {
        super(ed);
        this.kind = kind;
        this.phase = kind == Kind.INJECTIONS ? 3 : 0;
        weight = new NumberStepper(ed, 1, 1000);
        globalMax = new NumberStepper(ed, 0, 999);
        perChest = new NumberStepper(ed, 1, 999);
        minCount = new NumberStepper(ed, 1, 64);
        maxCount = new NumberStepper(ed, 1, 64);
        maxOnMap = new NumberStepper(ed, 0, 999);
        injMin = new NumberStepper(ed, 0, 999);
        injMax = new NumberStepper(ed, 0, 999);
    }

    // ---------------------------------------------------------------------
    // Model access
    // ---------------------------------------------------------------------

    private List<ItemStack> list(int p) {
        HungerGamesMapConfig c = session.config;
        if (kind == Kind.LOOT) {
            switch (p) {
                case 0: return c.getLootPhase1();
                case 1: return c.getLootPhase2();
                case 2: return c.getLootPhase3();
                default: return c.getLootAllPhases();
            }
        }
        switch (p) {
            case 0: return c.getInjectionPhase1();
            case 1: return c.getInjectionPhase2();
            case 2: return c.getInjectionPhase3();
            default: return c.getInjectionAllPhases();
        }
    }

    private void setList(int p, List<ItemStack> items) {
        HungerGamesMapConfig c = session.config;
        if (kind == Kind.LOOT) {
            switch (p) {
                case 0: c.setLootPhase1(items); break;
                case 1: c.setLootPhase2(items); break;
                case 2: c.setLootPhase3(items); break;
                default: c.setLootAllPhases(items); break;
            }
        } else {
            switch (p) {
                case 0: c.setInjectionPhase1(items); break;
                case 1: c.setInjectionPhase2(items); break;
                case 2: c.setInjectionPhase3(items); break;
                default: c.setInjectionAllPhases(items); break;
            }
        }
        session.markDirty();
    }

    private int injMinOf(int p) {
        HungerGamesMapConfig c = session.config;
        return p == 0 ? c.getInjMinPhase1() : p == 1 ? c.getInjMinPhase2() : p == 2 ? c.getInjMinPhase3() : c.getInjMinAllPhases();
    }

    private int injMaxOf(int p) {
        HungerGamesMapConfig c = session.config;
        return p == 0 ? c.getInjMaxPhase1() : p == 1 ? c.getInjMaxPhase2() : p == 2 ? c.getInjMaxPhase3() : c.getInjMaxAllPhases();
    }

    private void setInjRange(int p, int min, int max) {
        HungerGamesMapConfig c = session.config;
        switch (p) {
            case 0: c.setInjMinPhase1(min); c.setInjMaxPhase1(max); break;
            case 1: c.setInjMinPhase2(min); c.setInjMaxPhase2(max); break;
            case 2: c.setInjMinPhase3(min); c.setInjMaxPhase3(max); break;
            default: c.setInjMinAllPhases(min); c.setInjMaxAllPhases(max); break;
        }
        session.markDirty();
    }

    /** Applies an edit to the selected entry and writes the list back. */
    private void editSelected(java.util.function.Consumer<ItemStack> edit) {
        List<ItemStack> items = list(phase);
        int sel = selected[phase];
        if (sel < 0 || sel >= items.size()) return;
        edit.accept(items.get(sel));
        setList(phase, items);
    }

    // ---------------------------------------------------------------------
    // Drawing
    // ---------------------------------------------------------------------

    @Override
    void draw(int x, int y, int w, int h) {
        int[] counts = new int[4];
        for (int p = 0; p < 4; p++) counts[p] = list(p).size();
        phaseTabs(ed, x, y, w, phase, counts, p -> { phase = p; gridScroll.set(0); panelScroll.set(0); });
        y += 28;
        h -= 28;

        int pw = Math.min(PANEL_W, w / 2);
        gridX = x;
        gridW = w - pw - 10;
        panelX = x + w - pw;

        final List<ItemStack> items = list(phase);
        if (selected[phase] >= items.size()) selected[phase] = -1;

        // Pool grid.
        String caption = kind == Kind.LOOT
                ? PHASES[phase] + " loot" + (phase == 3 ? "  ·  added to every phase's chests" : "  ·  merged with All Phases")
                : PHASES[phase] + " powers" + (phase == 3 ? "  ·  can appear in every phase" : "  ·  merged with All Phases");
        ed.font().drawString(BedrockUI.trim(ed.font(), caption, gridW), gridX, y, BedrockUI.TEXT_DIM, false);
        int gy = y + 14, gh = h - 14;
        gridViewH = gh;
        gridScroll.clamp(gridContentH - gridViewH);
        final int poolWeight = poolWeight(phase);
        gridContentH = itemGrid(ed, items, gridX, gy, gridW, gh, gridScroll.value(), selected[phase],
                i -> selected[phase] = selected[phase] == i ? -1 : i,
                () -> ed.openOverlay(new ItemPicker(ed,
                        kind == Kind.LOOT ? ItemPicker.Filter.ALL : ItemPicker.Filter.INJECTIONS,
                        kind == Kind.LOOT ? "Add loot to " + PHASES[phase] : "Add a power to " + PHASES[phase],
                        st -> addEntry(st))),
                true,
                (st, i, sx, sy, hov) -> tooltipFor(st, poolWeight));

        // Inspector.
        panel(panelX, y, pw, h);
        panelViewH = h - 8;
        panelScroll.clamp(panelContentH - panelViewH);
        BedrockUI.pushClip(panelX + 1, y + 1, pw - 2, h - 2);
        int py = y + 8 - (int) panelScroll.value();
        int start = py;
        py = kind == Kind.LOOT ? drawLootInspector(panelX + 8, py, pw - 16, items, poolWeight)
                : drawInjectionInspector(panelX + 8, py, pw - 16, items);
        panelContentH = py - start + 8;
        BedrockUI.popClip();
    }

    private int poolWeight(int p) {
        // Chests draw from the phase pool merged with All Phases.
        int total = 0;
        for (ItemStack st : list(p)) total += weightOf(st);
        if (p != 3) for (ItemStack st : list(3)) total += weightOf(st);
        return total;
    }

    private int weightOf(ItemStack st) {
        return kind == Kind.LOOT ? HungerGamesLootEntry.getWeight(st) : LucraftInjectionEntry.getWeight(st);
    }

    private List<String> tooltipFor(ItemStack st, int poolWeight) {
        List<String> tip = new ArrayList<String>();
        tip.add(ItemPicker.displayName(st));
        if (kind == Kind.LOOT) {
            int min = HungerGamesLootEntry.getMinCount(st), max = HungerGamesLootEntry.getMaxCount(st);
            tip.add("§7Weight " + HungerGamesLootEntry.getWeight(st) + "  ·  "
                    + (min == max ? "x" + min : min + "-" + max) + "  ·  " + chance(HungerGamesLootEntry.getWeight(st), poolWeight));
        } else {
            int m = LucraftInjectionEntry.getMaxOnMap(st);
            tip.add("§7Weight " + LucraftInjectionEntry.getWeight(st) + "  ·  max on map " + (m == 0 ? "unlimited" : m));
        }
        return tip;
    }

    private static String chance(int weight, int pool) {
        if (pool <= 0) return "-";
        return String.format(Locale.ROOT, "%.1f%% per roll", weight * 100f / pool);
    }

    private void addEntry(ItemStack st) {
        List<ItemStack> items = list(phase);
        if (kind == Kind.LOOT) {
            ItemStack e = st.copy();
            e.setCount(1);
            HungerGamesLootEntry.embed(e, 10, 0, 1, 1, 1);
            items.add(e);
        } else {
            items.add(LucraftInjectionEntry.withProperties(st, 10, 0));
        }
        setList(phase, items);
        selected[phase] = items.size() - 1;
    }

    // ---------------------------------------------------------------------
    // Loot inspector
    // ---------------------------------------------------------------------

    private int drawLootInspector(int x, int y, int w, final List<ItemStack> items, int poolWeight) {
        int sel = selected[phase];
        if (sel < 0) {
            y = heading(ed, x, y, w, PHASES[phase]);
            y = note(ed, x, y, w, items.size() + " items  ·  total weight " + poolWeight(phase), BedrockUI.TEXT_DIM) + 4;
            y = note(ed, x, y, w, "Select an item to edit how often it appears and how many drop. "
                    + "Each chest gets 3-7 picks from this phase's pool plus All Phases.", BedrockUI.TEXT_MUTED) + 8;
            ed.button(x, y, w, 22, "Add Item", Style.PRIMARY, true, () -> ed.openOverlay(new ItemPicker(ed,
                    ItemPicker.Filter.ALL, "Add loot to " + PHASES[phase], st -> addEntry(st))));
            y += 28;
            ed.button(x, y, w, 22, "Reset to Default Loot", Style.DARK, true, () -> ed.confirm("Reset all loot?",
                    "Replaces every phase's loot pool with Beyonder's default loot table.", "Reset", Style.DANGER, this::resetLoot));
            return y + 22;
        }

        ItemStack st = items.get(sel);
        BedrockUI.slot(x, y, 34, true, false);
        BedrockUI.item(st, x + 1, y + 1, 2f);
        int tx = x + 40, ty = y + 3;
        List<String> nameLines = ed.font().listFormattedStringToWidth(ItemPicker.displayName(st), w - 40);
        for (int i = 0; i < Math.min(2, nameLines.size()); i++) {
            ed.font().drawString(nameLines.get(i), tx, ty, BedrockUI.TEXT, true);
            ty += 10;
        }
        ed.font().drawString(chance(HungerGamesLootEntry.getWeight(st), poolWeight), tx, ty + 1, BedrockUI.GREEN_LIGHT, false);
        y += 42;

        final int wv = HungerGamesLootEntry.getWeight(st), gv = HungerGamesLootEntry.getGlobalMax(st),
                cv = HungerGamesLootEntry.getPerChestMax(st), mn = HungerGamesLootEntry.getMinCount(st),
                mx = HungerGamesLootEntry.getMaxCount(st);
        y += weight.draw(ed, x, y, w, "Weight", wv, v -> setLoot(v, gv, cv, mn, mx)) + 4;
        y += globalMax.draw(ed, x, y, w, "Global max", gv, v -> setLoot(wv, v, cv, mn, mx)) + 4;
        y += perChest.draw(ed, x, y, w, "Per chest", cv, v -> setLoot(wv, gv, v, mn, mx)) + 4;
        y += minCount.draw(ed, x, y, w, "Min count", mn, v -> setLoot(wv, gv, cv, v, Math.max(v, mx))) + 4;
        y += maxCount.draw(ed, x, y, w, "Max count", mx, v -> setLoot(wv, gv, cv, Math.min(mn, v), v)) + 4;
        y = note(ed, x, y + 2, w, "Global max 0 = no limit across all chests. Shift-click steps by 10.", BedrockUI.TEXT_MUTED) + 8;

        y = entryActions(x, y, w, items);
        return y;
    }

    private void setLoot(int w, int g, int c, int mn, int mx) {
        editSelected(st -> HungerGamesLootEntry.embed(st, w, g, c, mn, mx));
    }

    private void resetLoot() {
        HungerGamesMapConfig defaults = new HungerGamesMapConfig(session.mapName);
        HungerGamesMapConfig.applyDefaultLootPreset(defaults);
        session.config.setLootPhase1(defaults.getLootPhase1());
        session.config.setLootPhase2(defaults.getLootPhase2());
        session.config.setLootPhase3(defaults.getLootPhase3());
        session.config.setLootAllPhases(defaults.getLootAllPhases());
        session.markDirty();
        for (int i = 0; i < 4; i++) selected[i] = -1;
        ed.toast("Loot reset to defaults");
    }

    // ---------------------------------------------------------------------
    // Injection inspector
    // ---------------------------------------------------------------------

    private int drawInjectionInspector(int x, int y, int w, final List<ItemStack> items) {
        // Per-phase power count, always visible.
        y = heading(ed, x, y, w, phase == 3 ? "Default power count" : PHASES[phase] + " power count");
        final int mn = injMinOf(phase), mx = injMaxOf(phase);
        y += injMin.draw(ed, x, y, w, "Min on map", mn, v -> setInjRange(phase, v, (mx > 0 && v > mx) ? v : mx)) + 4;
        y += injMax.draw(ed, x, y, w, "Max on map", mx, v -> setInjRange(phase, (v > 0 && mn > v) ? v : mn, v)) + 4;
        if (phase == 3) {
            y = note(ed, x, y + 2, w, "Used by any phase whose own count is 0.", BedrockUI.TEXT_MUTED);
        } else {
            int[] eff = session.config.getEffectiveInjectionRange(phase + 1);
            int emin = eff[0], emax = eff[1];
            if (emin == 0 && emax == 0) { emin = 3; emax = 8; }
            y = note(ed, x, y + 2, w, (mn == 0 && mx == 0 ? "0 = uses the All Phases count. " : "")
                    + "This phase places " + emin + "-" + emax + " powers.", BedrockUI.GREEN_LIGHT);
        }
        y += 10;

        int sel = selected[phase];
        if (sel < 0) {
            y = note(ed, x, y, w, items.isEmpty() ? "No powers in this pool yet."
                    : "Select a power to edit how likely it is and how many can be on the map.", BedrockUI.TEXT_MUTED) + 8;
            ed.button(x, y, w, 22, "Add Power", Style.PRIMARY, true, () -> ed.openOverlay(new ItemPicker(ed,
                    ItemPicker.Filter.INJECTIONS, "Add a power to " + PHASES[phase], st -> addEntry(st))));
            return y + 22;
        }

        ItemStack st = items.get(sel);
        y = heading(ed, x, y, w, "Selected power");
        BedrockUI.slot(x, y, 34, true, false);
        BedrockUI.item(st, x + 1, y + 1, 2f);
        int ty = y + 4;
        List<String> nameLines = ed.font().listFormattedStringToWidth(ItemPicker.displayName(st), w - 40);
        for (int i = 0; i < Math.min(3, nameLines.size()); i++) {
            ed.font().drawString(nameLines.get(i), x + 40, ty, BedrockUI.TEXT, true);
            ty += 10;
        }
        y += 40;

        final int wv = LucraftInjectionEntry.getWeight(st), mv = LucraftInjectionEntry.getMaxOnMap(st);
        y += weight.draw(ed, x, y, w, "Weight", Math.max(1, wv), v -> setInj(v, mv)) + 4;
        y += maxOnMap.draw(ed, x, y, w, "Max on map", mv, v -> setInj(Math.max(1, wv), v)) + 4;
        y = note(ed, x, y + 2, w, "Max on map 0 = no limit for this power.", BedrockUI.TEXT_MUTED) + 8;
        return entryActions(x, y, w, items);
    }

    private void setInj(int weight, int maxOnMap) {
        editSelected(st -> {
            LucraftInjectionEntry.embed(st, weight, maxOnMap);
            LucraftInjectionEntry.refreshLore(st);
        });
    }

    // ---------------------------------------------------------------------
    // Duplicate / move / remove
    // ---------------------------------------------------------------------

    private int entryActions(int x, int y, int w, final List<ItemStack> items) {
        final int sel = selected[phase];
        int half = (w - 6) / 2;
        if (kind == Kind.LOOT) {
            ed.button(x, y, half, 22, "Duplicate", Style.DARK, true, () -> {
                List<ItemStack> l = list(phase);
                l.add(sel + 1, l.get(sel).copy());
                setList(phase, l);
                selected[phase] = sel + 1;
            });
        } else {
            ed.button(x, y, half, 22, "Deselect", Style.DARK, true, () -> selected[phase] = -1);
        }
        final int moveW = w - half - 6;
        final int moveRight = x + half + 6 + moveW;
        final int moveBottom = y + 22;
        ed.button(x + half + 6, y, moveW, 22, "Move to...", Style.DARK, true,
                () -> openMoveMenu(sel, moveRight, moveBottom));
        y += 28;
        ed.button(x, y, w, 22, "Remove", Style.DANGER, true, () -> {
            List<ItemStack> l = list(phase);
            l.remove(sel);
            setList(phase, l);
            selected[phase] = -1;
        });
        return y + 22;
    }

    private void openMoveMenu(final int sel, int anchorRight, int anchorBottom) {
        List<String> labels = new ArrayList<String>();
        List<Runnable> actions = new ArrayList<Runnable>();
        for (int p = 0; p < 4; p++) {
            if (p == phase) continue;
            final int target = p;
            labels.add("Move to " + PHASES[p]);
            actions.add(() -> {
                List<ItemStack> from = list(phase);
                ItemStack st = from.remove(sel);
                setList(phase, from);
                List<ItemStack> to = list(target);
                to.add(st);
                setList(target, to);
                selected[phase] = -1;
                ed.toast("Moved to " + PHASES[target]);
            });
        }
        ed.openMenu(anchorRight, anchorBottom, labels.toArray(new String[0]), actions.toArray(new Runnable[0]));
    }

    // ---------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------

    @Override
    void onScroll(int direction) {
        if (ed.mouseX() >= panelX) panelScroll.add(-direction * 30);
        else gridScroll.add(-direction * (SLOT + SLOT_GAP) * 2);
    }

    @Override
    void onKey(char c, int key) {
        if (key == org.lwjgl.input.Keyboard.KEY_DELETE && selected[phase] >= 0) {
            List<ItemStack> l = list(phase);
            l.remove(selected[phase]);
            setList(phase, l);
            selected[phase] = -1;
        }
    }
}
