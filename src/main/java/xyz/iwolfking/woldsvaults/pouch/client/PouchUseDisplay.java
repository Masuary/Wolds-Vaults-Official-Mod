package xyz.iwolfking.woldsvaults.pouch.client;

import java.util.IdentityHashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import xyz.iwolfking.woldsvaults.pouch.data.PouchCapability;
import xyz.iwolfking.woldsvaults.pouch.data.PouchContents;
import xyz.iwolfking.woldsvaults.pouch.data.PouchRules;
import xyz.iwolfking.woldsvaults.pouch.data.PouchUseTotals;

public final class PouchUseDisplay {
    private static final Map<PouchContents, Map<ItemStack, PouchUseTotals>> FRAME_TOTALS = new IdentityHashMap<>();

    private PouchUseDisplay() {}

    public static void clearFrame() {
        // Item tags can change in place. Share calculations within a frame, never across frames.
        FRAME_TOTALS.clear();
    }

    @Nullable
    private static PouchUseTotals pooledUses(PouchContents contents, ItemStack stack) {
        if (!contents.autoReplace()) return null;
        return FRAME_TOTALS.computeIfAbsent(contents, PouchUseTotals::collect).get(stack);
    }

    public static long remainingUses(PouchContents contents, ItemStack stack) {
        PouchUseTotals pooled = pooledUses(contents, stack);
        return pooled == null ? PouchRules.remainingUses(stack) : pooled.remaining();
    }

    @Nullable
    public static PouchUseTotals equippedUses(Player player, ItemStack stack) {
        ItemStack pouch = PouchRules.equipped(player);
        return PouchRules.isPouch(pouch) ? pooledUses(PouchCapability.get(pouch), stack) : null;
    }
}
