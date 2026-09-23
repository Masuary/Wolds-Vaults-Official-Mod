package xyz.iwolfking.woldsvaults.pouch.data;

import iskallia.vault.core.vault.Vault;
import iskallia.vault.gear.trinket.TrinketEffect;
import iskallia.vault.gear.trinket.TrinketHelper;
import iskallia.vault.integration.IntegrationCurios;
import iskallia.vault.item.gear.TrinketItem;
import iskallia.vault.item.gear.VaultUsesHelper;
import iskallia.vault.world.data.ServerVaults;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;
import xyz.iwolfking.woldsvaults.items.TrinketPouchItem;

public final class PouchRules {
    public static final List<String> COLORS = List.of("red_trinket", "blue_trinket", "green_trinket");

    private PouchRules() {}

    public static boolean isPouch(ItemStack stack) {
        return stack.getItem() instanceof TrinketPouchItem;
    }

    public static boolean isStoredItem(ItemStack stack) {
        return stack.getItem() instanceof TrinketItem;
    }

    public static boolean isTrinket(ItemStack stack) {
        return isStoredItem(stack) && TrinketItem.isIdentified(stack) && COLORS.contains(color(stack));
    }

    public static String color(ItemStack stack) {
        return TrinketItem.getSlotIdentifier(stack).orElse("");
    }

    public static int remainingUses(ItemStack stack) {
        return Math.max(0, VaultUsesHelper.getUses(stack) - VaultUsesHelper.getUsedVaults(stack).size());
    }

    public static ItemStack equipped(Player player) {
        return IntegrationCurios.getCurioItemStack(player, "trinket_pouch", 0);
    }

    public static boolean locked(Player player) {
        if (player.level.dimension().location().getNamespace().equals("the_vault")) return true;
        if (player.level.isClientSide) return false;
        if (ServerVaults.get(player.level).isPresent()) return true;
        // Entry charges trinket uses before teleporting the registered listener out of the overworld.
        return ServerVaults.getAll().stream().anyMatch(vault -> vault.has(Vault.LISTENERS)
                && vault.get(Vault.LISTENERS).contains(player.getUUID()));
    }

    public static Map<String, Integer> capacities(ItemStack pouch) {
        return TrinketPouchItem.getPouchConfigFor(pouch).SLOT_ENTRIES;
    }

    public static SlotContext context(Player player, ItemStack stack, int index) {
        return new SlotContext(color(stack), player, index, false, true);
    }

    public static List<TrinketHelper.TrinketStack<TrinketEffect<?>>> effects(ItemStack stack) {
        return TrinketHelper.getTrinkets(Map.of(color(stack), List.of(new Tuple<>(stack, 0))), TrinketEffect.class);
    }

    public static String effectKey(ItemStack stack) {
        return effects(stack).stream().map(entry -> String.valueOf(entry.trinket().getRegistryName())).distinct().sorted()
                .reduce((first, second) -> first + "+" + second).orElse("");
    }

    public static String validate(ItemStack pouch, PouchContents contents, List<Integer> indices, Player player) {
        Map<String, Integer> counts = new HashMap<>();
        Set<TrinketEffect<?>> effects = new HashSet<>();
        for (int index : indices) {
            if (index < 0 || index >= PouchContents.SIZE) {
                return "Invalid stored item";
            }
            ItemStack stack = contents.getStackInSlot(index);
            String color = color(stack);
            if (!isTrinket(stack)) {
                return "Only identified colored trinkets can be activated";
            }
            if (player != null && !contents.isActive(index) && remainingUses(stack) == 0) {
                return "This trinket has no remaining uses";
            }
            if (counts.merge(color, 1, Integer::sum) > capacities(pouch).getOrDefault(color, 0)) {
                return "No free " + color.replace("_trinket", "") + " capacity in this pouch";
            }
            for (TrinketHelper.TrinketStack<TrinketEffect<?>> effect : effects(stack)) {
                if (!effects.add(effect.trinket())) {
                    return "This effect is already active";
                }
            }
            if (player != null && !((ICurioItem) stack.getItem()).canEquip(context(player, stack, index), stack)) {
                return "This trinket cannot be equipped right now";
            }
        }
        return "";
    }

    public static List<Integer> validSelection(ItemStack pouch, PouchContents contents, List<Integer> requested) {
        List<Integer> accepted = new ArrayList<>();
        for (int index : requested) {
            List<Integer> candidate = new ArrayList<>(accepted);
            candidate.add(index);
            if (validate(pouch, contents, candidate, null).isEmpty()) {
                accepted.add(index);
            }
        }
        return accepted;
    }
}
