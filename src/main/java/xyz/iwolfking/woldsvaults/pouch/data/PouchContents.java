package xyz.iwolfking.woldsvaults.pouch.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/** The capability owns the actual stacks. Active and preset entries are indices, never copies. */
public final class PouchContents extends ItemStackHandler {
    public static final int SIZE = 27;
    public static final int SCHEMA = 2;
    public static final int PRESET_COUNT = 3;
    public static final int MAX_PRESET_NAME_LENGTH = 24;
    private static final List<String> DEFAULT_PRESET_NAMES = List.of("Combat", "Looting", "Exploration");
    private final List<String> presetNames = new ArrayList<>(DEFAULT_PRESET_NAMES);
    private final Set<Integer> active = new LinkedHashSet<>();
    private final List<Set<Integer>> presets = List.of(new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
    private boolean autoReplace;

    public PouchContents() {
        super(SIZE);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        return PouchRules.isTrinket(stack);
    }

    @Override
    public void setStackInSlot(int slot, ItemStack stack) {
        if (!stack.isEmpty() && (!PouchRules.isStoredItem(stack) || stack.getCount() != 1)) {
            throw new IllegalArgumentException("Pouch storage requires one trinket per slot");
        }
        if (getStackInSlot(slot) != stack && !ItemStack.matches(getStackInSlot(slot), stack)) {
            forget(slot);
        }
        super.setStackInSlot(slot, stack);
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (getStackInSlot(slot).isEmpty()) {
            forget(slot);
        }
    }

    private void forget(int slot) {
        active.remove(slot);
        presets.forEach(preset -> preset.remove(slot));
    }

    public List<Integer> activeIndices() {
        return List.copyOf(active);
    }

    public List<ItemStack> activeStacks() {
        return active.stream().map(this::getStackInSlot).filter(stack -> !stack.isEmpty()).toList();
    }

    public boolean isActive(int slot) {
        return active.contains(slot);
    }

    public void setActive(List<Integer> indices) {
        for (int index : indices) {
            validateSlotIndex(index);
            if (getStackInSlot(index).isEmpty()) {
                throw new IllegalArgumentException("Cannot activate an empty pouch slot: " + index);
            }
        }
        active.clear();
        active.addAll(indices);
    }

    public void savePreset(int preset) {
        Set<Integer> selection = presets.get(preset);
        selection.clear();
        selection.addAll(active);
    }

    public List<Integer> preset(int preset) {
        return List.copyOf(presets.get(preset));
    }

    public String presetName(int preset) {
        return presetNames.get(preset);
    }

    public void renamePreset(int preset, String name) {
        presetNames.set(preset, validatedPresetName(name));
    }

    public static String validatedPresetName(String name) {
        String trimmed = name.strip();
        if (trimmed.isEmpty() || trimmed.length() > MAX_PRESET_NAME_LENGTH
                || trimmed.codePoints().anyMatch(character -> Character.isISOControl(character) || character == 0xA7)) {
            throw new IllegalArgumentException("Preset names need 1-24 characters without formatting or control codes");
        }
        return trimmed;
    }

    public boolean autoReplace() {
        return autoReplace;
    }

    public void toggleAutoReplace() {
        autoReplace = !autoReplace;
    }

    public int firstEmpty() {
        for (int index = 0; index < SIZE; index++) {
            if (getStackInSlot(index).isEmpty()) {
                return index;
            }
        }
        return -1;
    }

    public int emptySlots() {
        int empty = 0;
        for (int index = 0; index < SIZE; index++) {
            if (getStackInSlot(index).isEmpty()) {
                empty++;
            }
        }
        return empty;
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag result = super.serializeNBT();
        result.putInt("Schema", SCHEMA);
        result.putIntArray("Active", active.stream().mapToInt(Integer::intValue).toArray());
        for (int index = 0; index < presets.size(); index++) {
            result.putIntArray("Preset" + index, presets.get(index).stream().mapToInt(Integer::intValue).toArray());
            result.putString("PresetName" + index, presetNames.get(index));
        }
        result.putBoolean("AutoReplace", autoReplace);
        return result;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        if (tag.isEmpty()) {
            return;
        }
        if (tag.getInt("Schema") < 1 || tag.getInt("Schema") > SCHEMA || tag.getInt("Size") != SIZE) {
            throw new IllegalStateException("Unsupported Trinket Collection pouch schema or capacity; retain the original world backup");
        }
        ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
        Set<Integer> occupied = new LinkedHashSet<>();
        List<ItemStack> decoded = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) {
            CompoundTag item = items.getCompound(index);
            int slot = item.getInt("Slot");
            ItemStack stack = ItemStack.of(item);
            if (slot < 0 || slot >= SIZE || !occupied.add(slot) || stack.isEmpty()
                    || stack.getCount() != 1 || !PouchRules.isStoredItem(stack)) {
                throw new IllegalStateException("Invalid stored pouch item at entry " + index + "; refusing to discard data");
            }
            decoded.add(stack);
        }
        List<Integer> decodedActive = readIndices(tag.getIntArray("Active"), occupied);
        List<List<Integer>> decodedPresets = new ArrayList<>();
        List<String> decodedNames = new ArrayList<>();
        for (int index = 0; index < presets.size(); index++) {
            decodedPresets.add(readIndices(tag.getIntArray("Preset" + index), occupied));
            decodedNames.add(tag.getInt("Schema") == 1 ? DEFAULT_PRESET_NAMES.get(index)
                    : validatedPresetName(tag.getString("PresetName" + index)));
        }
        // Validate everything before replacing the live contents.
        for (int slot = 0; slot < SIZE; slot++) {
            stacks.set(slot, ItemStack.EMPTY);
        }
        for (int index = 0; index < items.size(); index++) {
            stacks.set(items.getCompound(index).getInt("Slot"), decoded.get(index));
        }
        setActive(decodedActive);
        for (int index = 0; index < presets.size(); index++) {
            presets.get(index).clear();
            presets.get(index).addAll(decodedPresets.get(index));
            presetNames.set(index, decodedNames.get(index));
        }
        autoReplace = tag.getBoolean("AutoReplace");
    }

    private static List<Integer> readIndices(int[] values, Set<Integer> occupied) {
        Set<Integer> result = new LinkedHashSet<>();
        for (int value : values) {
            if (!occupied.contains(value) || !result.add(value)) {
                throw new IllegalStateException("Invalid pouch selection index " + value + "; refusing to discard data");
            }
        }
        return List.copyOf(result);
    }
}
