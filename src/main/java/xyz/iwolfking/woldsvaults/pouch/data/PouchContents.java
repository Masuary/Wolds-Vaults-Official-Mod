package xyz.iwolfking.woldsvaults.pouch.data;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/** Owns physical stacks and active slot indices; presets remember effect keys independently of storage. */
public final class PouchContents extends ItemStackHandler {
    public static final int SIZE = 27;
    public static final int SCHEMA = 4;
    public static final int PRESET_COUNT = 3;
    public static final int MAX_PRESET_NAME_LENGTH = 24;
    private static final List<String> DEFAULT_PRESET_NAMES = List.of("Combat", "Looting", "Exploration");
    private final List<String> presetNames = new ArrayList<>(DEFAULT_PRESET_NAMES);
    private final Set<Integer> active = new LinkedHashSet<>();
    private final List<Set<String>> presets = List.of(new LinkedHashSet<>(), new LinkedHashSet<>(), new LinkedHashSet<>());
    private boolean autoReplace;
    private int appliedPreset = -1;

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
        validateStoredStack(stack);
        if (getStackInSlot(slot) != stack && !ItemStack.matches(getStackInSlot(slot), stack)) {
            forget(slot);
        }
        super.setStackInSlot(slot, stack);
    }

    public void synchronizeStackInSlot(int slot, ItemStack stack) {
        validateStoredStack(stack);
        // Network replacements can differ only in client display caches, not the stored trinket.
        super.setStackInSlot(slot, stack);
    }

    private static void validateStoredStack(ItemStack stack) {
        if (!stack.isEmpty() && (!PouchRules.isStoredItem(stack) || stack.getCount() != 1)) {
            throw new IllegalArgumentException("Pouch storage requires one trinket per slot");
        }
    }

    @Override
    protected void onContentsChanged(int slot) {
        if (getStackInSlot(slot).isEmpty()) {
            forget(slot);
        }
    }

    private void forget(int slot) {
        if (active.remove(slot)) appliedPreset = -1;
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
        if (appliedPreset >= 0 && !effectKeys(activeIndices()).equals(effectKeys(indices))) appliedPreset = -1;
        active.clear();
        active.addAll(indices);
    }

    public void savePreset(int preset) {
        Set<String> selection = new LinkedHashSet<>();
        for (ItemStack stack : activeStacks()) selection.add(validatedEffectKey(PouchRules.effectKey(stack)));
        presets.get(preset).clear();
        presets.get(preset).addAll(selection);
        if (appliedPreset == preset && selection.isEmpty()) appliedPreset = -1;
    }

    private Set<String> effectKeys(List<Integer> indices) {
        Set<String> keys = new LinkedHashSet<>();
        for (int index : indices) keys.add(PouchRules.effectKey(getStackInSlot(index)));
        return keys;
    }

    public int appliedPreset() {
        return appliedPreset;
    }

    public void markPresetApplied(int preset) {
        Set<String> desired = presets.get(preset);
        if (!desired.containsAll(effectKeys(activeIndices()))) {
            throw new IllegalStateException("Applied trinkets must belong to the saved preset");
        }
        appliedPreset = desired.isEmpty() ? -1 : preset;
    }

    public List<String> preset(int preset) {
        return List.copyOf(presets.get(preset));
    }

    public List<Integer> resolvePreset(int preset) {
        return preset(preset).stream().map(this::usableIndex).filter(index -> index >= 0).toList();
    }

    public int usableIndex(String effectKey) {
        int chosen = -1;
        int fewestUses = Integer.MAX_VALUE;
        for (int index = 0; index < SIZE; index++) {
            ItemStack stack = getStackInSlot(index);
            if (!PouchRules.isTrinket(stack) || !effectKey.equals(PouchRules.effectKey(stack))) continue;
            int uses = PouchRules.remainingUses(stack);
            if (uses == 0) continue;
            if (isActive(index)) return index;
            if (uses < fewestUses) {
                chosen = index;
                fewestUses = uses;
            }
        }
        return chosen;
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
            ListTag entries = new ListTag();
            presets.get(index).forEach(key -> entries.add(StringTag.valueOf(key)));
            result.put("Preset" + index, entries);
            result.putString("PresetName" + index, presetNames.get(index));
        }
        result.putBoolean("AutoReplace", autoReplace);
        result.putInt("AppliedPreset", appliedPreset);
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
        List<List<String>> decodedPresets = new ArrayList<>();
        List<String> decodedNames = new ArrayList<>();
        for (int index = 0; index < presets.size(); index++) {
            if (tag.getInt("Schema") < 3) {
                List<Integer> indices = readIndices(tag.getIntArray("Preset" + index), occupied);
                Set<String> migrated = new LinkedHashSet<>();
                for (int slot : indices) {
                    int entry = new ArrayList<>(occupied).indexOf(slot);
                    migrated.add(validatedEffectKey(PouchRules.effectKey(decoded.get(entry))));
                }
                decodedPresets.add(List.copyOf(migrated));
            } else {
                decodedPresets.add(readPreset(tag, "Preset" + index));
            }
            decodedNames.add(tag.getInt("Schema") == 1 ? DEFAULT_PRESET_NAMES.get(index)
                    : validatedPresetName(tag.getString("PresetName" + index)));
        }
        int decodedAppliedPreset = -1;
        if (tag.getInt("Schema") >= 4) {
            if (!tag.contains("AppliedPreset", Tag.TAG_INT)) throw new IllegalStateException("Missing applied pouch preset");
            decodedAppliedPreset = tag.getInt("AppliedPreset");
            if (decodedAppliedPreset < -1 || decodedAppliedPreset >= PRESET_COUNT) {
                throw new IllegalStateException("Invalid applied pouch preset: " + decodedAppliedPreset);
            }
            if (decodedAppliedPreset >= 0) {
                List<String> desired = decodedPresets.get(decodedAppliedPreset);
                List<Integer> slots = new ArrayList<>(occupied);
                if (desired.isEmpty() || decodedActive.stream().anyMatch(slot ->
                        !desired.contains(PouchRules.effectKey(decoded.get(slots.indexOf(slot)))))) {
                    throw new IllegalStateException("Applied pouch preset does not match its active trinket types");
                }
            }
        }
        // Validate everything before replacing the live contents.
        for (int slot = 0; slot < SIZE; slot++) {
            stacks.set(slot, ItemStack.EMPTY);
        }
        for (int index = 0; index < items.size(); index++) {
            stacks.set(items.getCompound(index).getInt("Slot"), decoded.get(index));
        }
        appliedPreset = -1;
        setActive(decodedActive);
        appliedPreset = decodedAppliedPreset;
        for (int index = 0; index < presets.size(); index++) {
            presets.get(index).clear();
            presets.get(index).addAll(decodedPresets.get(index));
            presetNames.set(index, decodedNames.get(index));
        }
        autoReplace = tag.getBoolean("AutoReplace");
    }

    private static List<String> readPreset(CompoundTag tag, String name) {
        if (!(tag.get(name) instanceof ListTag entries) || entries.size() > SIZE
                || !entries.isEmpty() && entries.getElementType() != Tag.TAG_STRING) {
            throw new IllegalStateException("Invalid pouch preset " + name + "; refusing to discard data");
        }
        Set<String> result = new LinkedHashSet<>();
        for (int index = 0; index < entries.size(); index++) {
            String key = validatedEffectKey(entries.getString(index));
            if (!result.add(key)) throw new IllegalStateException("Duplicate pouch preset effect: " + key);
        }
        return List.copyOf(result);
    }

    private static String validatedEffectKey(String key) {
        Set<String> effects = new LinkedHashSet<>();
        for (String effect : key.split("\\+", -1)) {
            if (!effect.contains(":") || ResourceLocation.tryParse(effect) == null || !effects.add(effect)) {
                throw new IllegalStateException("Invalid pouch preset effect key: " + key + "; refusing to discard data");
            }
        }
        return key;
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
