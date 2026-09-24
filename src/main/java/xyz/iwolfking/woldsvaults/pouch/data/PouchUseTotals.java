package xyz.iwolfking.woldsvaults.pouch.data;

import iskallia.vault.item.gear.VaultUsesHelper;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.world.item.ItemStack;

/** Display totals only. Physical copies retain their own uses and vault registrations. */
public record PouchUseTotals(long remaining, long total) {
    public float fraction() {
        return total == 0 ? 0.0F : (float) remaining / total;
    }

    public static Map<ItemStack, PouchUseTotals> collect(PouchContents contents) {
        Map<ItemStack, String> keys = new IdentityHashMap<>();
        Map<String, PouchUseTotals> totals = new HashMap<>();
        for (int index = 0; index < PouchContents.SIZE; index++) {
            ItemStack stack = contents.getStackInSlot(index);
            if (!PouchRules.isTrinket(stack)) continue;
            String key = PouchRules.effectKey(stack);
            keys.put(stack, key);
            PouchUseTotals uses = new PouchUseTotals(PouchRules.remainingUses(stack), Math.max(0, VaultUsesHelper.getUses(stack)));
            totals.merge(key, uses, (first, second) -> new PouchUseTotals(first.remaining + second.remaining, first.total + second.total));
        }
        Map<ItemStack, PouchUseTotals> result = new IdentityHashMap<>();
        keys.forEach((stack, key) -> result.put(stack, totals.get(key)));
        return result;
    }
}
