package xyz.iwolfking.woldsvaults.mixins.vaulthunters.custom;

import xyz.iwolfking.woldsvaults.pouch.data.PouchCapability;
import xyz.iwolfking.woldsvaults.pouch.data.PouchContents;
import xyz.iwolfking.woldsvaults.pouch.data.PouchRules;
import iskallia.vault.gear.trinket.TrinketEffect;
import iskallia.vault.gear.trinket.TrinketHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.util.Tuple;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TrinketHelper.class, remap = false)
public abstract class MixinPouchTrinketEffects {
    @Inject(method = "getTrinkets(Ljava/util/Map;Ljava/lang/Class;)Ljava/util/List;", at = @At("RETURN"), cancellable = true)
    private static <T extends TrinketEffect<?>> void collectionEffects(Map<String, List<Tuple<ItemStack, Integer>>> slots,
            Class<? super T> effectClass, CallbackInfoReturnable<List<TrinketHelper.TrinketStack<T>>> callback) {
        List<TrinketHelper.TrinketStack<T>> result = null;
        for (Tuple<ItemStack, Integer> entry : slots.getOrDefault("trinket_pouch", List.of())) {
            if (entry.getB() == 0 && PouchRules.isPouch(entry.getA())) {
                PouchContents contents = PouchCapability.get(entry.getA());
                for (int index : PouchRules.validSelection(entry.getA(), contents, contents.activeIndices())) {
                    if (result == null) {
                        result = new ArrayList<>(callback.getReturnValue());
                    }
                    ItemStack stack = contents.getStackInSlot(index);
                    // Only effect enumeration expands. Inventory/death enumeration still sees one pouch.
                    result.addAll(TrinketHelper.getTrinkets(Map.of(PouchRules.color(stack), List.of(new Tuple<>(stack, index))), effectClass));
                }
            }
        }
        if (result != null) {
            callback.setReturnValue(result);
        }
    }
}
