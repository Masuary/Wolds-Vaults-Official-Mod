package xyz.iwolfking.woldsvaults.pouch.data;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import iskallia.vault.snapshot.AttributeSnapshotHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public final class PouchRuntime {
    // Client and integrated-server ticks share a JVM, but never a Player instance.
    private static final Map<Player, Map<ItemStack, WornEntry>> WORN = new WeakHashMap<>();

    private PouchRuntime() {}

    public static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            PouchMigration.equipped(event.player);
        } else {
            update(event.player);
        }
    }

    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        clear(event.getPlayer());
    }

    public static synchronized void clear(Player player) {
        Map<ItemStack, WornEntry> previous = WORN.remove(player);
        if (previous != null) {
            previous.values().forEach(entry -> unequip(player, entry.snapshot(), entry.index()));
        }
    }

    public static synchronized void update(Player player) {
        ItemStack pouch = PouchRules.equipped(player);
        Map<ItemStack, WornEntry> next = new IdentityHashMap<>();
        Map<ItemStack, WornEntry> previous = WORN.getOrDefault(player, Map.of());
        if (player.isAlive() && PouchRules.isPouch(pouch)) {
            PouchContents contents = PouchCapability.get(pouch);
            if (!player.level.isClientSide && !PouchRules.locked(player)) {
                replaceExhausted(pouch, contents, player);
            }
            for (int index : PouchRules.validSelection(pouch, contents, contents.activeIndices())) {
                ItemStack stack = contents.getStackInSlot(index);
                WornEntry entry = previous.get(stack);
                next.put(stack, entry == null ? new WornEntry(stack.copy(), index) : entry);
            }
        }
        previous.forEach((stack, entry) -> {
            if (!next.containsKey(stack)) {
                unequip(player, entry.snapshot(), entry.index());
            }
        });
        next.forEach((stack, entry) -> {
            ICurioItem item = (ICurioItem) stack.getItem();
            if (!previous.containsKey(stack)) {
                item.onEquip(PouchRules.context(player, stack, entry.index()), ItemStack.EMPTY, stack);
            }
            item.curioTick(PouchRules.context(player, stack, entry.index()), stack);
        });
        if (player instanceof ServerPlayer serverPlayer && !previous.keySet().equals(next.keySet())) {
            AttributeSnapshotHelper.getInstance().refreshSnapshotDelayed(serverPlayer);
        }
        if (next.isEmpty()) {
            WORN.remove(player);
        } else {
            WORN.put(player, next);
        }
    }

    private record WornEntry(ItemStack snapshot, int index) {}

    private static void unequip(Player player, ItemStack stack, int index) {
        if (stack.getItem() instanceof ICurioItem item) {
            item.onUnequip(PouchRules.context(player, stack, index), ItemStack.EMPTY, stack);
        }
    }

    private static void replaceExhausted(ItemStack pouch, PouchContents contents, Player player) {
        if (!contents.autoReplace()) {
            return;
        }
        List<Integer> replacement = new ArrayList<>(contents.activeIndices());
        for (int position = 0; position < replacement.size(); position++) {
            ItemStack exhausted = contents.getStackInSlot(replacement.get(position));
            if (PouchRules.remainingUses(exhausted) > 0) {
                continue;
            }
            String effect = PouchRules.effectKey(exhausted);
            for (int reserve = 0; reserve < PouchContents.SIZE; reserve++) {
                ItemStack candidate = contents.getStackInSlot(reserve);
                if (!replacement.contains(reserve) && PouchRules.remainingUses(candidate) > 0
                        && effect.equals(PouchRules.effectKey(candidate))) {
                    List<Integer> trial = new ArrayList<>(replacement);
                    trial.set(position, reserve);
                    if (PouchRules.validate(pouch, contents, trial, player).isEmpty()) {
                        replacement = trial;
                        break;
                    }
                }
            }
        }
        contents.setActive(replacement);
    }
}
