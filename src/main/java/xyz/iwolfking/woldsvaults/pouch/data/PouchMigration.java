package xyz.iwolfking.woldsvaults.pouch.data;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

public final class PouchMigration {
    private static final ThreadLocal<Boolean> RESTORING = ThreadLocal.withInitial(() -> false);
    private static final UUID LEGACY_POUCH_MODIFIER = UUID.nameUUIDFromBytes("trinket_pouch0".getBytes(StandardCharsets.UTF_8));

    private PouchMigration() {}

    public static boolean restoring() {
        return RESTORING.get();
    }

    public static void setRestoring(boolean value) {
        if (value) {
            RESTORING.set(true);
        } else {
            RESTORING.remove();
        }
    }

    public static Tag serializedCurios(Tag original) {
        if (!(original instanceof CompoundTag originalCompound)) {
            return original;
        }
        CompoundTag converted = originalCompound.copy();
        ListTag curios = converted.getList("Curios", Tag.TAG_COMPOUND);
        CompoundTag pouchEntry = null;
        for (int index = 0; index < curios.size(); index++) {
            CompoundTag curio = curios.getCompound(index);
            if (curio.getString("Identifier").equals("trinket_pouch")) {
                ListTag items = curio.getCompound("StacksHandler").getCompound("Stacks").getList("Items", Tag.TAG_COMPOUND);
                for (int item = 0; item < items.size(); item++) {
                    if (items.getCompound(item).getInt("Slot") == 0) {
                        pouchEntry = items.getCompound(item);
                    }
                }
            }
        }
        if (pouchEntry == null) {
            return original;
        }
        ItemStack pouch = ItemStack.of(pouchEntry);
        if (!PouchRules.isPouch(pouch)) {
            return original;
        }
        stored(pouch);
        PouchContents contents = PouchCapability.get(pouch);
        List<ItemStack> pending = new ArrayList<>();
        List<CompoundTag> coloredHandlers = new ArrayList<>();
        for (int index = 0; index < curios.size(); index++) {
            CompoundTag curio = curios.getCompound(index);
            if (PouchRules.COLORS.contains(curio.getString("Identifier"))) {
                CompoundTag handler = curio.getCompound("StacksHandler");
                coloredHandlers.add(handler);
                ListTag items = handler.getCompound("Stacks").getList("Items", Tag.TAG_COMPOUND);
                for (int item = 0; item < items.size(); item++) {
                    ItemStack stack = ItemStack.of(items.getCompound(item));
                    requireMigratable(stack);
                    pending.add(stack);
                }
            }
        }
        requireSpace(contents, pending.size());
        List<Integer> active = new ArrayList<>(contents.activeIndices());
        for (ItemStack stack : pending) {
            int slot = contents.firstEmpty();
            contents.setStackInSlot(slot, stack);
            active.add(slot);
        }
        contents.setActive(PouchRules.validSelection(pouch, contents, active));
        for (CompoundTag handler : coloredHandlers) {
            handler.getCompound("Stacks").put("Items", new ListTag());
            for (String key : List.of("CachedModifiers", "PersistentModifiers")) {
                ListTag modifiers = handler.getList(key, Tag.TAG_COMPOUND);
                for (int index = modifiers.size() - 1; index >= 0; index--) {
                    CompoundTag modifier = modifiers.getCompound(index);
                    if (modifier.hasUUID("UUID") && modifier.getUUID("UUID").equals(LEGACY_POUCH_MODIFIER)) {
                        modifiers.remove(index);
                    }
                }
            }
        }
        CompoundTag serialized = pouch.save(new CompoundTag());
        for (String key : serialized.getAllKeys()) {
            pouchEntry.put(key, serialized.get(key));
        }
        return converted;
    }

    public static void stored(ItemStack pouch) {
        CompoundTag tag = pouch.getTag();
        if (tag == null || !tag.contains("StoredCurios", Tag.TAG_LIST)) {
            return;
        }
        PouchContents contents = PouchCapability.get(pouch);
        ListTag legacy = tag.getList("StoredCurios", Tag.TAG_COMPOUND);
        List<ItemStack> pending = new ArrayList<>();
        for (int index = 0; index < legacy.size(); index++) {
            ItemStack stack = ItemStack.of(legacy.getCompound(index));
            if (!stack.isEmpty()) {
                requireMigratable(stack);
                pending.add(stack);
            } else {
                throw new IllegalStateException("Cannot decode legacy pouch entry " + index + "; original StoredCurios was retained");
            }
        }
        requireSpace(contents, pending.size());
        List<Integer> active = new ArrayList<>(contents.activeIndices());
        for (ItemStack stack : pending) {
            int slot = contents.firstEmpty();
            contents.setStackInSlot(slot, stack);
            active.add(slot);
        }
        contents.setActive(PouchRules.validSelection(pouch, contents, active));
        tag.remove("StoredCurios");
    }

    public static void equipped(Player player) {
        if (player.level.isClientSide) {
            return;
        }
        ItemStack pouch = PouchRules.equipped(player);
        if (!PouchRules.isPouch(pouch)) {
            return;
        }
        stored(pouch);
        PouchContents contents = PouchCapability.get(pouch);
        CuriosApi.getCuriosHelper().getCuriosHandler(player).ifPresent(handler -> {
            List<LegacySlot> pending = new ArrayList<>();
            for (String color : PouchRules.COLORS) {
                handler.getStacksHandler(color).ifPresent(slots -> {
                    for (int index = 0; index < slots.getStacks().getSlots(); index++) {
                        ItemStack stack = slots.getStacks().getStackInSlot(index);
                        if (!stack.isEmpty()) {
                            requireMigratable(stack);
                            pending.add(new LegacySlot(slots.getStacks(), index, stack));
                        }
                    }
                });
            }
            if (!pending.isEmpty()) {
                requireSpace(contents, pending.size());
                List<Integer> active = new ArrayList<>(contents.activeIndices());
                for (LegacySlot legacy : pending) {
                    // Move the original object only after every source item and capacity was validated.
                    int slot = contents.firstEmpty();
                    contents.setStackInSlot(slot, legacy.stack());
                    legacy.handler().setStackInSlot(legacy.index(), ItemStack.EMPTY);
                    active.add(slot);
                }
                contents.setActive(PouchRules.validSelection(pouch, contents, active));
            }
            Multimap<String, AttributeModifier> obsolete = HashMultimap.create();
            for (String color : PouchRules.COLORS) {
                for (AttributeModifier modifier : handler.getModifiers().get(color)) {
                    if (modifier.getId().equals(LEGACY_POUCH_MODIFIER)) {
                        obsolete.put(color, modifier);
                    }
                }
            }
            if (!obsolete.isEmpty()) {
                handler.removeSlotModifiers(obsolete);
            }
        });
    }

    private static void requireMigratable(ItemStack stack) {
        if (!PouchRules.isStoredItem(stack) || stack.getCount() != 1) {
            throw new IllegalStateException("Legacy pouch contains an unsupported item; refusing to remove the original stack: " + stack);
        }
    }

    private static void requireSpace(PouchContents contents, int count) {
        if (count > contents.emptySlots()) {
            throw new IllegalStateException("Legacy pouch needs " + count + " free entries but has " + contents.emptySlots() + "; originals retained");
        }
    }

    private record LegacySlot(IDynamicStackHandler handler, int index, ItemStack stack) {}
}
