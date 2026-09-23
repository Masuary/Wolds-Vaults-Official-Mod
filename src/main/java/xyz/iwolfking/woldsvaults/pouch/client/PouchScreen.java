package xyz.iwolfking.woldsvaults.pouch.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import iskallia.vault.client.data.ClientDiscoveredEntriesData;
import iskallia.vault.client.gui.framework.ScreenTextures;
import iskallia.vault.gear.trinket.TrinketEffect;
import iskallia.vault.gear.trinket.TrinketEffectRegistry;
import iskallia.vault.item.gear.TrinketItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import xyz.iwolfking.woldsvaults.pouch.data.PouchContents;
import xyz.iwolfking.woldsvaults.pouch.data.PouchRules;
import xyz.iwolfking.woldsvaults.pouch.menu.PouchLayout;
import xyz.iwolfking.woldsvaults.pouch.menu.PouchGridScroll;
import xyz.iwolfking.woldsvaults.pouch.menu.PouchMenu;
import xyz.iwolfking.woldsvaults.pouch.network.PouchNetwork;

public final class PouchScreen extends AbstractContainerScreen<PouchMenu> {
    private static final int TEXT = 0x404040;
    private static final int MUTED = 0x666666;
    private static final String[] COLOR_NAMES = {"Red", "Blue", "Green"};
    private static final int[] COLORS = {0xAD4949, 0x397BAC, 0x548237};
    private final Map<String, ItemStack> catalog = new LinkedHashMap<>();
    private List<Entry> entries = List.of();
    private String inspectedKey = "";
    private boolean keyboardNavigation;
    private final Set<GuiEventListener> pouchControls = new HashSet<>();
    private int colorFilter = -1;
    private final List<Button> colorFilters = new ArrayList<>();
    private final List<Button> cells = new ArrayList<>();
    private final List<Button> presetSelectors = new ArrayList<>();
    private final List<Button> tabs = new ArrayList<>();
    private final PouchGridScroll collectionScroll = new PouchGridScroll(PouchLayout.COLUMN_COUNT, PouchLayout.ROW_COUNT);
    private final PouchGridScroll presetScroll = new PouchGridScroll(PouchLayout.PREVIEW_COLUMNS, PouchLayout.PREVIEW_ROWS);
    private final List<PouchScrollBar> scrollBars = new ArrayList<>();
    private PouchScrollBar draggedScrollBar;
    private EditBox search;
    private EditBox presetName;
    private Button ownership;
    private Button autoReplace;
    private Button confirm;
    private Button cancel;
    private Button renamePreset;
    private Button savePreset;
    private Button applyPreset;
    private View view = View.COLLECTION;
    private Dialog dialog = Dialog.NONE;
    private String query = "";
    private String dialogError = "";
    private boolean ownedOnly = true;
    private int selectedPreset;
    private int dialogPreset;

    public PouchScreen(PouchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = PouchLayout.WIDTH;
        imageHeight = PouchLayout.HEIGHT;
    }

    @Override
    protected void init() {
        imageHeight = PouchLayout.HEIGHT;
        super.init();
        topPos = PouchLayout.panelTop(height, imageHeight);
        cells.clear();
        colorFilters.clear();
        tabs.clear();
        scrollBars.clear();
        draggedScrollBar = null;
        presetSelectors.clear();
        dialog = Dialog.NONE;
        for (View option : View.values()) {
            tabs.add(addRenderableWidget(new Button(leftPos + PouchLayout.tabX(option.ordinal()),
                    topPos + PouchLayout.TAB_Y, PouchLayout.TAB_WIDTH, PouchLayout.TAB_HEIGHT,
                    label(option.label), ignored -> changeView(option)) {
                @Override
                public void renderButton(PoseStack pose, int mouseX, int mouseY, float partialTick) {
                    frame(pose, x, y, width, height);
                    if (view == option) {
                        fill(pose, x + 4, y + height - 5, x + width - 4, y + height - 3, 0xFF71865D);
                    }
                    String caption = fit(getMessage().getString(), width - 8);
                    font.draw(pose, caption, x + (width - font.width(caption)) / 2.0F, y + 5,
                            !active ? MUTED : view == option || isHoveredOrFocused() ? 0x435B33 : TEXT);
                }
            }));
        }
        search = addRenderableWidget(new EditBox(font, leftPos + 8, topPos + 58, 210, 14, label("search")));
        search.setMaxLength(64);
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            resetLaneScrolls();
            refreshEntries();
        });
        ownership = button(224, 57, 72, "owned", ignored -> {
            ownedOnly = !ownedOnly;
            resetLaneScrolls();
            refreshEntries();
        });
        for (int filter = -1; filter < 3; filter++) {
            final int selectedColor = filter;
            colorFilters.add(button(filter < 0 ? 8 : 50 + filter * 83, 78, filter < 0 ? 38 : 80,
                    filter < 0 ? "all" : COLOR_NAMES[filter].toLowerCase(Locale.ROOT), ignored -> {
                        colorFilter = selectedColor;
                        resetLaneScrolls();
                        refreshEntries();
                        updateControls();
                    }));
        }
        for (int index = 0; index < PouchLayout.VISIBLE_CELLS; index++) {
            final int cell = index;
            cells.add(addRenderableWidget(new Button(leftPos + cellX(index), topPos + cellY(index),
                    PouchLayout.CELL_SIZE, PouchLayout.CELL_SIZE, TextComponent.EMPTY,
                    ignored -> toggle(entry(cell))) {
                @Override
                public void renderButton(PoseStack pose, int mouseX, int mouseY, float partialTick) {
                    if (isHoveredOrFocused()) outline(pose, x, y, width, height, 0xFFFFFFFF);
                }
            }));
        }
        scrollBars.add(addRenderableWidget(new PouchScrollBar(leftPos + 182,
                topPos + PouchLayout.COLLECTION_Y, PouchLayout.COLLECTION_SCROLL_HEIGHT,
                label("trinkets").getString(), collectionScroll)));
        for (int preset = 0; preset < PouchContents.PRESET_COUNT; preset++) {
            final int index = preset;
            presetSelectors.add(addRenderableWidget(new Button(leftPos + 11, topPos + 61 + preset * 29, 82, 25,
                    text(menu.contents().presetName(index)), ignored -> {
                        selectedPreset = index;
                        presetScroll.setFirstRow(0);
                        updateControls();
                    }) {
                @Override
                public void renderButton(PoseStack pose, int mouseX, int mouseY, float partialTick) {
                    int color = selectedPreset == index ? 0xFF84916F : isHoveredOrFocused() ? 0xFFCCCCCC : 0xFFB8B8B8;
                    fill(pose, x, y, x + width, y + height, color);
                    font.draw(pose, fit(getMessage().getString(), width - 8), x + 4, y + 7,
                            selectedPreset == index ? 0x26391C : TEXT);
                }
            }));
        }
        renamePreset = button(247, 59, 49, "rename", ignored -> openDialog(Dialog.RENAME, selectedPreset));
        savePreset = button(170, 199, 75, "save_current", ignored -> {
            if (menu.contents().preset(selectedPreset).isEmpty()) action(PouchMenu.SAVE_PRESET + selectedPreset);
            else openDialog(Dialog.OVERWRITE, selectedPreset);
        });
        applyPreset = button(249, 199, 47, "apply", ignored -> action(PouchMenu.APPLY_PRESET + selectedPreset));
        autoReplace = button(8, 220, 12, "off", ignored -> action(PouchMenu.AUTO_REPLACE));
        scrollBars.add(addRenderableWidget(new PouchScrollBar(leftPos + 287, topPos + PouchLayout.PREVIEW_Y,
                PouchLayout.PREVIEW_SCROLL_HEIGHT,
                label("preset_trinkets").getString(), presetScroll)));
        presetName = addRenderableWidget(new EditBox(font, leftPos + 51, topPos + 92, 166, 14, label("preset_name")));
        presetName.setMaxLength(PouchContents.MAX_PRESET_NAME_LENGTH);
        presetName.setResponder(ignored -> dialogError = "");
        confirm = button(152, 116, 65, "save", ignored -> confirmDialog());
        cancel = button(81, 116, 65, "cancel", ignored -> closeDialog());
        refreshEntries();
        updateControls();
        pouchControls.clear();
        pouchControls.addAll(children());
    }

    private Button button(int x, int y, int width, String label, Button.OnPress action) {
        return addRenderableWidget(new Button(leftPos + x, topPos + y, width, 14, label(label), action) {
            @Override
            public void renderButton(PoseStack pose, int mouseX, int mouseY, float partialTick) {
                smallButton(pose, this.x, this.y, this.width, height, active && isHoveredOrFocused());
                if (this == applyPreset && active) fill(pose, this.x + 2, this.y + 2, this.x + this.width - 2, this.y + height - 2, 0xFF60744C);
                drawCenteredString(pose, font, fit(getMessage().getString(), this.width - 6), this.x + this.width / 2, this.y + 3, active ? 0xFFFFFF : 0xAAAAAA);
            }
        });
    }

    private static Component text(String value) { return new TextComponent(value); }
    private static Component label(String key, Object... arguments) {
        return new TranslatableComponent("gui.woldsvaults.pouch." + key, arguments);
    }
    private static int cellX(int cell) { return PouchLayout.COLLECTION_X + cell % PouchLayout.COLUMN_COUNT * PouchLayout.CELL_PITCH; }
    private static int cellY(int cell) { return PouchLayout.COLLECTION_Y + cell / PouchLayout.COLUMN_COUNT * PouchLayout.ROW_PITCH; }

    private void changeView(View next) {
        if (!menu.getCarried().isEmpty()) {
            return;
        }
        view = next;
        menu.setStoredView(view == View.STORAGE);
        search.setFocus(false);
        setFocused(null);
        clearWidgets();
        init();
        updateControls();
    }

    private void resetLaneScrolls() { collectionScroll.setFirstRow(0); }

    private void action(int action) {
        if (!menu.isLocked() && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
        }
    }

    private void toggle(Entry entry) {
        if (entry != null && chosenIndex(entry) >= 0) {
            search.setFocus(false);
            action(PouchMenu.TOGGLE_TRINKET + chosenIndex(entry));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        search.tick();
        presetName.tick();
        if (menu.isLocked() && dialog != Dialog.NONE) {
            closeDialog();
        }
        refreshEntries();
        updateControls();
    }

    private void refreshEntries() {
        if (catalog.isEmpty()) {
            for (TrinketEffect<?> effect : TrinketEffectRegistry.getOrderedEntries()) {
                if (effect.getConfig().hasCuriosSlot() && PouchRules.COLORS.contains(effect.getConfig().getCuriosSlot())) {
                    ItemStack icon = TrinketItem.createBaseTrinket(effect);
                    catalog.put(PouchRules.effectKey(icon), icon);
                }
            }
        }
        Map<String, Entry> combined = new LinkedHashMap<>();
        catalog.forEach((key, icon) -> combined.put(key, new Entry(icon, new ArrayList<>())));
        for (int slot = 0; slot < PouchContents.SIZE; slot++) {
            ItemStack stack = menu.contents().getStackInSlot(slot);
            if (PouchRules.isTrinket(stack)) {
                combined.computeIfAbsent(PouchRules.effectKey(stack), ignored -> new Entry(stack, new ArrayList<>())).indices().add(slot);
            }
        }
        entries = combined.values().stream()
                .filter(entry -> colorFilter < 0 || PouchRules.COLORS.get(colorFilter).equals(PouchRules.color(entry.icon())))
                .filter(entry -> !ownedOnly || !entry.indices().isEmpty())
                .filter(entry -> entry.icon().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))
                .sorted(Comparator.comparing(entry -> entry.icon().getHoverName().getString())).toList();
        collectionScroll.setEntryCount(entries.size());
        if (entries.stream().noneMatch(entry -> PouchRules.effectKey(entry.icon()).equals(inspectedKey))) inspectedKey = "";
    }

    private Entry entry(int cell) {
        int index = collectionScroll.entryIndex(cell);
        return index >= 0 ? entries.get(index) : null;
    }

    private int chosenIndex(Entry entry) {
        for (int index : entry.indices()) {
            if (menu.contents().isActive(index)) return index;
        }
        return entry.indices().stream().min(Comparator.comparingInt(index -> {
            int uses = PouchRules.remainingUses(menu.contents().getStackInSlot(index));
            return uses == 0 ? Integer.MAX_VALUE : uses;
        })).orElse(-1);
    }

    private void updateControls() {
        boolean modal = dialog != Dialog.NONE;
        boolean collection = view == View.COLLECTION;
        search.visible = ownership.visible = collection;
        search.setEditable(!modal);
        search.setSuggestion(query.isEmpty() ? label("search").getString() : "");
        ownership.active = !modal;
        ownership.setMessage(label(ownedOnly ? "owned" : "catalog"));
        tabs.forEach(tab -> tab.active = !modal && menu.getCarried().isEmpty());
        scrollBars.get(0).visible = collection && collectionScroll.canScroll();
        scrollBars.get(0).active = !modal;
        for (int filter = -1; filter < 3; filter++) {
            Button control = colorFilters.get(filter + 1);
            control.visible = collection;
            control.active = !modal;
            Component name = label(filter < 0 ? "all" : COLOR_NAMES[filter].toLowerCase(Locale.ROOT));
            String count = "";
            if (filter >= 0) {
                String color = PouchRules.COLORS.get(filter);
                long active = menu.contents().activeStacks().stream().filter(stack -> color.equals(PouchRules.color(stack))).count();
                count = " " + active + "/" + PouchRules.capacities(menu.pouch()).getOrDefault(color, 0);
            }
            control.setMessage(text((colorFilter == filter ? "> " : "") + name.getString() + count));
        }
        for (int cell = 0; cell < PouchLayout.VISIBLE_CELLS; cell++) {
            Button button = cells.get(cell);
            Entry entry = entry(cell);
            button.visible = collection && entry != null;
            button.active = !modal;
            if (entry != null) button.setMessage(entry.icon().getHoverName());
        }
        for (int preset = 0; preset < presetSelectors.size(); preset++) {
            Button control = presetSelectors.get(preset);
            control.visible = view == View.PRESETS;
            control.active = !modal;
            control.setMessage(text(menu.contents().presetName(preset)));
        }
        for (Button control : List.of(renamePreset, savePreset, applyPreset)) {
            control.visible = view == View.PRESETS;
            control.active = !modal && !menu.isLocked();
        }
        presetScroll.setEntryCount(menu.contents().preset(selectedPreset).size());
        scrollBars.get(1).visible = view == View.PRESETS && presetScroll.canScroll();
        scrollBars.get(1).active = !modal;
        autoReplace.visible = true;
        autoReplace.active = !modal && !menu.isLocked();
        autoReplace.setMessage(text(menu.contents().autoReplace() ? "x" : ""));
        presetName.visible = dialog == Dialog.RENAME;
        confirm.visible = cancel.visible = modal;
        confirm.active = !menu.isLocked();
        confirm.setMessage(label(dialog == Dialog.OVERWRITE ? "replace" : "save"));
    }

    private void openDialog(Dialog next, int preset) {
        dialog = next;
        dialogPreset = preset;
        dialogError = "";
        search.setFocus(false);
        presetName.setValue(menu.contents().presetName(preset));
        updateControls();
        if (next == Dialog.RENAME) {
            setFocused(presetName);
            presetName.setFocus(true);
        }
    }

    private void closeDialog() {
        dialog = Dialog.NONE;
        presetName.setFocus(false);
        setFocused(null);
        updateControls();
    }

    private void confirmDialog() {
        if (menu.isLocked()) return;
        if (dialog == Dialog.RENAME) {
            try {
                String name = PouchContents.validatedPresetName(presetName.getValue());
                PouchNetwork.CHANNEL.sendToServer(new PouchNetwork.Rename(menu.containerId, dialogPreset, name));
            } catch (IllegalArgumentException exception) {
                dialogError = label("invalid_name").getString();
                return;
            }
        } else if (dialog == Dialog.OVERWRITE) {
            selectedPreset = dialogPreset;
            action(PouchMenu.SAVE_PRESET + dialogPreset);
        }
        closeDialog();
    }

    @Override
    public void render(PoseStack pose, int mouseX, int mouseY, float partialTick) {
        removeOverlappingExternalControls();
        int inspectedCell = collectionInspectedCell(mouseX, mouseY);
        if (view == View.COLLECTION && inspectedCell >= 0) {
            inspectedKey = PouchRules.effectKey(entry(inspectedCell).icon());
        }
        renderBackground(pose);
        super.render(pose, mouseX, mouseY, partialTick);
        if (dialog != Dialog.NONE) {
            pose.pushPose();
            pose.translate(0, 0, 400);
            fill(pose, 0, 0, width, height, 0xA0000000);
            frame(pose, leftPos + 42, topPos + 67, 184, 72);
            font.draw(pose, dialog == Dialog.RENAME ? label("rename_title").getString() : label("overwrite_title").getString(), leftPos + 51, topPos + 77, TEXT);
            if (dialog == Dialog.OVERWRITE) {
                font.draw(pose, fit(menu.contents().presetName(dialogPreset), 164), leftPos + 51, topPos + 96, MUTED);
            } else {
                presetName.render(pose, mouseX, mouseY, partialTick);
            }
            confirm.render(pose, mouseX, mouseY, partialTick);
            cancel.render(pose, mouseX, mouseY, partialTick);
            if (!dialogError.isEmpty()) font.draw(pose, dialogError, leftPos + 45, topPos + 145, 0xFF9999);
            pose.popPose();
            return;
        }
        renderTooltip(pose, mouseX, mouseY);
        renderHints(pose, mouseX, mouseY);
    }

    private void removeOverlappingExternalControls() {
        for (GuiEventListener listener : List.copyOf(children())) {
            if (!pouchControls.contains(listener) && listener instanceof AbstractWidget widget
                    && widget.x < leftPos + imageWidth && widget.x + widget.getWidth() > leftPos
                    && widget.y < topPos + imageHeight && widget.y + widget.getHeight() > topPos) {
                // Some injected widgets render even when invisible. Remove overlapping controls from both lists.
                removeWidget(listener);
            }
        }
    }

    private int collectionInspectedCell(int mouseX, int mouseY) {
        for (int index = 0; index < cells.size(); index++) {
            Button cell = cells.get(index);
            if (cell.visible && keyboardNavigation && cell.isFocused()) return index;
        }
        if (!keyboardNavigation) {
            for (int index = 0; index < cells.size(); index++) {
                if (cells.get(index).isMouseOver(mouseX, mouseY)) return index;
            }
        }
        return -1;
    }

    @Override
    protected void renderBg(PoseStack pose, float partialTick, int mouseX, int mouseY) {
        frame(pose, leftPos, topPos, imageWidth, imageHeight);
        fill(pose, leftPos + 5, topPos + 3, leftPos + 299, topPos + 30, 0xFFB3B3B3);
        fill(pose, leftPos + 8, topPos + 217, leftPos + 296, topPos + 218, 0xFF888888);
        if (view == View.COLLECTION) {
            recessed(pose, leftPos + 8, topPos + 97, 182, 116);
            recessed(pose, leftPos + 197, topPos + 97, 99, 116);
            entries.stream().filter(entry -> PouchRules.effectKey(entry.icon()).equals(inspectedKey)).findFirst()
                    .ifPresent(entry -> itemRenderer.renderAndDecorateItem(entry.icon(), leftPos + 205, topPos + 103));
            for (int cell = 0; cell < PouchLayout.VISIBLE_CELLS; cell++) {
                Entry entry = entry(cell);
                if (entry == null) continue;
                int x = leftPos + cellX(cell);
                int y = topPos + cellY(cell);
                int chosen = chosenIndex(entry);
                boolean selected = chosen >= 0 && menu.contents().isActive(chosen);
                collectionSlot(pose, x, y, selected);
                int color = PouchRules.COLORS.indexOf(PouchRules.color(entry.icon()));
                if (color >= 0) fill(pose, x + 1, y + 1, x + 19, y + 2, 0xFF000000 | COLORS[color]);
                itemRenderer.renderAndDecorateItem(entry.icon(), x + 2, y + 2);
                RenderSystem.disableDepthTest();
                if (chosen < 0) fill(pose, x + 2, y + 2, x + 18, y + 18, 0xAA8B8B8B);
                else if (PouchRules.remainingUses(menu.contents().getStackInSlot(chosen)) == 0) {
                    fill(pose, x + 2, y + 2, x + 18, y + 18, 0x88883333);
                } else if (!selected && !selectionError(entry).isEmpty()) {
                    outline(pose, x, y, 20, 20, 0xFFB78236);
                }
                if (selected) check(pose, x + 1, y + 12);
                RenderSystem.enableDepthTest();
                if (entry.indices().size() > 1) itemRenderer.renderGuiItemDecorations(font, entry.icon(), x + 2, y + 2, String.valueOf(entry.indices().size()));
            }
        } else if (view == View.STORAGE) {
            for (Slot slot : menu.slots) {
                if (slot.isActive()) inset(pose, leftPos + slot.x - 1, topPos + slot.y - 1);
            }
        } else {
            recessed(pose, leftPos + 8, topPos + 57, 88, 156);
            recessed(pose, leftPos + 102, topPos + 97, 194, PouchLayout.PREVIEW_PANEL_HEIGHT);
            for (int cell = 0; cell < PouchLayout.PREVIEW_COLUMNS * PouchLayout.PREVIEW_ROWS; cell++) {
                ItemStack stack = presetEntry(cell);
                if (stack.isEmpty()) continue;
                int x = leftPos + previewX(cell);
                int y = topPos + previewY(cell);
                collectionSlot(pose, x, y, false);
                int lane = PouchRules.COLORS.indexOf(PouchRules.color(stack));
                if (lane >= 0) fill(pose, x + 1, y + 1, x + 19, y + 2, 0xFF000000 | COLORS[lane]);
                itemRenderer.renderAndDecorateItem(stack, x + 2, y + 2);
            }
        }
    }

    private static int previewX(int cell) { return PouchLayout.PREVIEW_X + cell % PouchLayout.PREVIEW_COLUMNS * PouchLayout.PREVIEW_PITCH; }
    private static int previewY(int cell) { return PouchLayout.PREVIEW_Y + cell / PouchLayout.PREVIEW_COLUMNS * 22; }

    private ItemStack presetEntry(int cell) {
        int index = presetScroll.entryIndex(cell);
        return index < 0 ? ItemStack.EMPTY : menu.contents().getStackInSlot(menu.contents().preset(selectedPreset).get(index));
    }

    private String currentLoadout() {
        for (int preset = 0; preset < PouchContents.PRESET_COUNT; preset++) {
            if (matchesPreset(preset)) return menu.contents().presetName(preset);
        }
        return label("custom").getString();
    }

    private boolean matchesPreset(int preset) {
        return new HashSet<>(menu.contents().preset(preset)).equals(new HashSet<>(menu.contents().activeIndices()));
    }

    private String selectionError(Entry entry) {
        int chosen = chosenIndex(entry);
        if (chosen < 0) return label("unowned").getString();
        if (menu.contents().isActive(chosen)) return "";
        List<Integer> proposed = new ArrayList<>(menu.contents().activeIndices());
        proposed.add(chosen);
        return PouchRules.validate(menu.pouch(), menu.contents(), proposed, minecraft.player);
    }

    @Override
    protected void renderLabels(PoseStack pose, int mouseX, int mouseY) {
        font.draw(pose, fit(menu.pouch().getHoverName().getString(), 176), 9, 7, TEXT);
        font.draw(pose, "|", 188, 7, MUTED);
        font.draw(pose, fit(currentLoadout(), 99), 197, 7, 0x435B33);
        font.draw(pose, label(menu.isLocked() ? "locked" : menu.isEquipped() ? "equipped" : "unequipped"), 9, 20,
                menu.isLocked() ? 0x992222 : MUTED);
        Component counts = label(menu.isEquipped() ? "counts_active" : "counts_selected",
                menu.contents().activeIndices().size(), PouchContents.SIZE - menu.contents().emptySlots());
        font.draw(pose, fit(counts.getString(), 185), 295 - Math.min(185, font.width(counts)), 20, MUTED);
        Component shortcut = PouchClientSetup.OPEN_POUCH.getTranslatedKeyMessage();
        int shortcutWidth = Math.min(90, font.width(shortcut));
        font.draw(pose, fit(label("auto_replace").getString(), 267 - shortcutWidth), 24, 223, MUTED);
        font.draw(pose, fit(shortcut.getString(), 90), 296 - shortcutWidth, 223, MUTED);
        if (view == View.COLLECTION) {
            if (entries.isEmpty()) font.draw(pose, label("no_matches"), 16, 107, MUTED);
            renderInspector(pose);
        } else if (view == View.STORAGE) {
            font.draw(pose, label("storage_caption"), PouchLayout.GRID_X, 56, TEXT);
            String stored = (PouchContents.SIZE - menu.contents().emptySlots()) + "/27";
            font.draw(pose, stored, PouchLayout.GRID_X + 162 - font.width(stored), 56, MUTED);
            font.draw(pose, playerInventoryTitle, PouchLayout.GRID_X, 128, TEXT);
        } else {
            font.draw(pose, fit(menu.contents().presetName(selectedPreset), 138), 103, 62, TEXT);
            font.draw(pose, fit(label("preset_count", menu.contents().preset(selectedPreset).size(),
                    label(matchesPreset(selectedPreset) ? "matches" : "not_matching")).getString(), 193), 103, 82, MUTED);
            if (menu.contents().preset(selectedPreset).isEmpty()) font.draw(pose, label("empty_preset"), 109, 110, MUTED);
            if (!menu.status().isEmpty()) font.draw(pose, fit(menu.status(), 63), 103, 202, TEXT);
        }
    }

    private void renderInspector(PoseStack pose) {
        Entry inspected = entries.stream().filter(entry -> PouchRules.effectKey(entry.icon()).equals(inspectedKey)).findFirst().orElse(null);
        if (inspected == null) {
            drawWrapped(pose, label("inspect_hint"), 203, 106, 87, 5, MUTED);
            return;
        }

        drawWrapped(pose, inspected.icon().getHoverName(), 203, 124, 87, 2, TEXT);
        String effect = effectText(inspected);
        drawWrapped(pose, text(effect).copy().withStyle(style -> style.withColor(MUTED)), 203, 147, 87, 3, MUTED);
        int chosen = chosenIndex(inspected);
        font.draw(pose, fit(label("uses", chosen < 0 ? 0 : PouchRules.remainingUses(menu.contents().getStackInSlot(chosen))).getString(), 87), 203, 184, TEXT);
        Component state = label(chosen < 0 ? "unowned" : menu.contents().isActive(chosen) ? "active"
                : PouchRules.remainingUses(menu.contents().getStackInSlot(chosen)) == 0 ? "exhausted"
                : selectionError(inspected).isEmpty() ? "inactive" : "unavailable");
        font.draw(pose, fit(state.getString(), 87), 203, 199, chosen < 0 ? 0x883333 : 0x435B33);
    }

    private void drawWrapped(PoseStack pose, Component value, int x, int y, int width, int rows, int color) {
        List<FormattedCharSequence> lines = font.split(value, width);
        int visibleRows = lines.size() > rows ? rows - 1 : lines.size();
        for (int index = 0; index < visibleRows; index++) font.draw(pose, lines.get(index), x, y + index * 10, color);
        if (lines.size() > rows) font.draw(pose, "...", x, y + (rows - 1) * 10, color);
    }

    private String effectText(Entry entry) {
        return PouchRules.effects(entry.icon()).stream().map(value -> value.trinket().getTrinketConfig().getEffectText())
                .filter(value -> value != null && !value.isBlank()).distinct().reduce((first, second) -> first + "; " + second).orElse("");
    }

    private void renderHints(PoseStack pose, int mouseX, int mouseY) {
        if (view == View.COLLECTION) {
            int index = collectionInspectedCell(mouseX, mouseY);
            if (index >= 0) {
                Button cell = cells.get(index);
                if (keyboardNavigation) {
                    mouseX = cell.x + cell.getWidth();
                    mouseY = cell.y;
                }
                Entry entry = entry(index);
                int chosen = chosenIndex(entry);
                String action = menu.isLocked() ? label("locked_hint").getString() : chosen < 0 ? label("unowned").getString()
                        : menu.contents().isActive(chosen) ? label("deactivate").getString() : label("activate").getString();
                if (chosen >= 0 && !menu.contents().isActive(chosen) && !menu.isLocked()) {
                    List<Integer> proposed = new ArrayList<>(menu.contents().activeIndices());
                    proposed.add(chosen);
                    String error = PouchRules.validate(menu.pouch(), menu.contents(), proposed, minecraft.player);
                    if (!error.isEmpty()) action = error;
                }
                String effect = PouchRules.effects(entry.icon()).stream().map(value -> value.trinket().getTrinketConfig().getEffectText())
                        .filter(value -> value != null && !value.isBlank()).distinct().reduce((first, second) -> first + "; " + second).orElse("");
                boolean discovered = PouchRules.effects(entry.icon()).stream().allMatch(value ->
                        ClientDiscoveredEntriesData.Trinkets.getDiscoveredTrinkets().contains(value.trinket().getRegistryName()));
                List<Component> lines = new ArrayList<>();
                lines.add(entry.icon().getHoverName().copy());
                if (!effect.isBlank()) {
                    lines.add(new TextComponent(effect).withStyle(ChatFormatting.GRAY));
                }
                lines.add(chosen < 0
                        ? new TextComponent(label("no_usable").getString()).withStyle(ChatFormatting.RED)
                        : new TextComponent(label("uses_prefix").getString()).withStyle(ChatFormatting.GRAY)
                                .append(new TextComponent(String.valueOf(PouchRules.remainingUses(menu.contents().getStackInSlot(chosen))))
                                        .withStyle(ChatFormatting.YELLOW))
                                .append(new TextComponent(label("selected_copy").getString()).withStyle(ChatFormatting.GRAY)));
                lines.add(new TextComponent(label("copies").getString()).withStyle(ChatFormatting.GRAY)
                        .append(new TextComponent(String.valueOf(entry.indices().size())).withStyle(ChatFormatting.AQUA)));
                lines.add(new TextComponent(discovered ? label("discovered").getString() : label("not_discovered").getString())
                        .withStyle(discovered ? ChatFormatting.GREEN : ChatFormatting.RED));
                lines.add(new TextComponent(action).withStyle(menu.isLocked() || chosen < 0
                        ? ChatFormatting.RED : ChatFormatting.YELLOW));
                tooltip(pose, mouseX, mouseY, lines.toArray(Component[]::new));
                return;
            }
            if (ownership.isHoveredOrFocused()) tooltip(pose, mouseX, mouseY, ownedOnly ? label("show_catalog").getString() : label("show_owned").getString());
        }
        if (view == View.PRESETS) {
            for (int index = 0; index < presetSelectors.size(); index++) {
                if (presetSelectors.get(index).isHoveredOrFocused()) {
                    tooltip(pose, mouseX, mouseY, menu.contents().presetName(index), label("preview_hint").getString());
                    return;
                }
            }
            for (int cell = 0; cell < PouchLayout.PREVIEW_COLUMNS * PouchLayout.PREVIEW_ROWS; cell++) {
                ItemStack stack = presetEntry(cell);
                if (!stack.isEmpty() && inside(mouseX, mouseY, previewX(cell), previewY(cell), 20, 20)) {
                    renderCompactTrinketTooltip(pose, stack, mouseX, mouseY);
                    return;
                }
            }
            if (renamePreset.isHoveredOrFocused()) tooltip(pose, mouseX, mouseY, label("rename_hint").getString());
            if (savePreset.isHoveredOrFocused()) tooltip(pose, mouseX, mouseY, label("save_hint").getString());
            if (applyPreset.isHoveredOrFocused()) tooltip(pose, mouseX, mouseY, label("apply_hint").getString());

            if (!menu.status().isEmpty() && inside(mouseX, mouseY, 103, 199, 63, 14)) tooltip(pose, mouseX, mouseY, menu.status());
        }
        for (int index = 0; index < scrollBars.size(); index++) {
            PouchScrollBar bar = scrollBars.get(index);
            if (bar.visible && bar.isHoveredOrFocused()) {
                PouchGridScroll scroll = index == 0 ? collectionScroll : presetScroll;
                tooltip(pose, mouseX, mouseY, label("scroll_hint").getString(), label("scroll_row", scroll.firstRow() + 1, scroll.totalRows()).getString());
            }
        }
        if (autoReplace.isHoveredOrFocused() || inside(mouseX, mouseY, 23, 220, 170, 14)) {
            tooltip(pose, mouseX, mouseY, label(menu.isLocked() ? "locked_hint" : "auto_replace_hint"));
        } else if (inside(mouseX, mouseY, 8, 3, 288, 27)) {
            tooltip(pose, mouseX, mouseY, menu.pouch().getHoverName(), text(currentLoadout()),
                    label(menu.isLocked() ? "locked_hint" : menu.isEquipped() ? "equipped" : "unequipped_hint"));
        } else if (inside(mouseX, mouseY, 200, 220, 96, 14)) {
            tooltip(pose, mouseX, mouseY, PouchClientSetup.OPEN_POUCH.getTranslatedKeyMessage());
        }
    }

    private void tooltip(PoseStack pose, int mouseX, int mouseY, String... lines) {
        Component[] components = new Component[lines.length];
        for (int index = 0; index < lines.length; index++) {
            components[index] = new TextComponent(lines[index]).withStyle(index == 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY);
        }
        tooltip(pose, mouseX, mouseY, components);
    }

    private void tooltip(PoseStack pose, int mouseX, int mouseY, Component... lines) {
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) {
            if (!line.getString().isBlank()) {
                wrapped.addAll(font.split(line, Math.max(80, Math.min(260, width - 30))));
            }
        }
        int tooltipWidth = Math.max(48, wrapped.stream().mapToInt(font::width).max().orElse(0));
        // Legendary Tooltips pads centered titles after measurement. Reserve that space plus the native border.
        int anchorX = Math.max(0, Math.min(mouseX, width - tooltipWidth - 32));
        int anchorY = Math.max(16, Math.min(mouseY, height - 4));
        renderTooltip(pose, wrapped, anchorX, anchorY);
    }

    private void renderCompactTrinketTooltip(PoseStack pose, ItemStack stack, int mouseX, int mouseY) {
        List<Component> lines = getTooltipFromItem(stack).stream()
                .filter(line -> !line.getString().isBlank())
                .toList();
        renderComponentTooltip(pose, lines, mouseX, mouseY, stack);
    }

    @Override
    protected void renderTooltip(PoseStack pose, int mouseX, int mouseY) {
        if (menu.getCarried().isEmpty() && hoveredSlot != null && hoveredSlot.hasItem()
                && hoveredSlot.getItem().getItem() instanceof TrinketItem) {
            renderCompactTrinketTooltip(pose, hoveredSlot.getItem(), mouseX, mouseY);
            return;
        }
        super.renderTooltip(pose, mouseX, mouseY);
    }

    @Override
    public void mouseMoved(double x, double y) {
        keyboardNavigation = false;
        super.mouseMoved(x, y);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        keyboardNavigation = false;
        if (dialog != Dialog.NONE) {
            if (presetName.visible && presetName.mouseClicked(x, y, button)) {
                setFocused(presetName);
                return true;
            }
            confirm.mouseClicked(x, y, button);
            if (dialog != Dialog.NONE) cancel.mouseClicked(x, y, button);
            return true;
        }
        for (PouchScrollBar bar : scrollBars) {
            if (bar.mouseClicked(x, y, button)) {
                draggedScrollBar = bar;
                search.setFocus(false);
                setFocused(bar);
                updateControls();
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
        if (dialog != Dialog.NONE) return true;
        if (button == 0 && draggedScrollBar != null) {
            draggedScrollBar.dragTo(y);
            updateControls();
            return true;
        }
        return super.mouseDragged(x, y, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (draggedScrollBar != null && button == 0) {
            draggedScrollBar = null;
            return true;
        }
        return dialog != Dialog.NONE || super.mouseReleased(x, y, button);
    }

    private boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= leftPos + left && x < leftPos + left + width && y >= topPos + top && y < topPos + top + height;
    }

    @Override
    public boolean mouseScrolled(double x, double y, double amount) {
        if (dialog != Dialog.NONE) return true;
        if (view == View.COLLECTION && inside(x, y, 8, 97, 182, 116)) {
            collectionScroll.scroll(amount < 0 ? 1 : amount > 0 ? -1 : 0);
            updateControls();
            return true;
        } else if (view == View.PRESETS && inside(x, y, 102, 97, 194, PouchLayout.PREVIEW_PANEL_HEIGHT)) {
            presetScroll.scroll(amount < 0 ? 1 : amount > 0 ? -1 : 0);
            updateControls();
            return true;
        }
        return super.mouseScrolled(x, y, amount);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_TAB) keyboardNavigation = true;
        if (dialog != Dialog.NONE) {
            if (key == GLFW.GLFW_KEY_ESCAPE) closeDialog();
            else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) confirmDialog();
            else if (dialog == Dialog.RENAME) presetName.keyPressed(key, scanCode, modifiers);
            return true;
        }
        if (search.visible && search.isFocused() && key != GLFW.GLFW_KEY_ESCAPE && key != GLFW.GLFW_KEY_TAB) {
            return search.keyPressed(key, scanCode, modifiers) || search.canConsumeInput();
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        if (dialog != Dialog.NONE) return dialog == Dialog.RENAME && presetName.charTyped(character, modifiers);
        return super.charTyped(character, modifiers);
    }

    private String fit(String value, int width) {
        return font.width(value) <= width ? value : font.plainSubstrByWidth(value, width - font.width("...")) + "...";
    }

    private static void check(PoseStack pose, int x, int y) {
        fill(pose, x, y, x + 8, y + 7, 0xFF263526);
        for (int index = 0; index < 3; index++) fill(pose, x + 1 + index, y + 3 + index, x + 2 + index, y + 4 + index, 0xFFB9FC80);
        for (int index = 0; index < 4; index++) fill(pose, x + 3 + index, y + 5 - index, x + 4 + index, y + 6 - index, 0xFFB9FC80);
    }

    private static void frame(PoseStack pose, int x, int y, int width, int height) {
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.enableBlend();
        ScreenTextures.DEFAULT_WINDOW_BACKGROUND.blit(pose, x, y, 0, width, height);
    }

    private static void recessed(PoseStack pose, int x, int y, int width, int height) {
        fill(pose, x, y, x + width, y + height, 0xFFEEEEEE);
        fill(pose, x, y, x + width - 1, y + height - 1, 0xFF777777);
        fill(pose, x + 1, y + 1, x + width - 1, y + height - 1, 0xFFA4A4A4);
    }

    private static void collectionSlot(PoseStack pose, int x, int y, boolean active) {
        fill(pose, x, y, x + 20, y + 20, 0xFFE6E6E6);
        fill(pose, x, y, x + 19, y + 19, 0xFF555555);
        fill(pose, x + 1, y + 1, x + 19, y + 19, active ? 0xFF738268 : 0xFFB5B5B5);
    }

    private static void smallButton(PoseStack pose, int x, int y, int width, int height, boolean hovered) {
        fill(pose, x, y, x + width, y + height, 0xFF444444);
        fill(pose, x + 1, y + 1, x + width - 1, y + height - 1, 0xFFE6E6E6);
        fill(pose, x + 2, y + 2, x + width - 2, y + height - 2, hovered ? 0xFF8A957B : 0xFF777777);
    }

    private static void outline(PoseStack pose, int x, int y, int width, int height, int color) {
        fill(pose, x, y, x + width, y + 1, color);
        fill(pose, x, y + height - 1, x + width, y + height, color);
        fill(pose, x, y, x + 1, y + height, color);
        fill(pose, x + width - 1, y, x + width, y + height, color);
    }

    private static void inset(PoseStack pose, int x, int y) {
        fill(pose, x, y, x + 18, y + 18, 0xFFFFFFFF);
        fill(pose, x, y, x + 17, y + 17, 0xFF373737);
        fill(pose, x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }

    private enum View {
        COLLECTION("trinkets"), STORAGE("storage"), PRESETS("presets");
        private final String label;
        View(String label) { this.label = label; }
    }
    private enum Dialog { NONE, RENAME, OVERWRITE }
    private record Entry(ItemStack icon, List<Integer> indices) {}
}
