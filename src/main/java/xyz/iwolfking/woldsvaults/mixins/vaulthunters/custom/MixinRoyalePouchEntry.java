package xyz.iwolfking.woldsvaults.mixins.vaulthunters.custom;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import iskallia.vault.core.vault.Vault;
import iskallia.vault.core.vault.VaultUtils;
import iskallia.vault.core.vault.player.ClassicListenersLogic;
import iskallia.vault.gear.trinket.TrinketEffect;
import iskallia.vault.gear.trinket.TrinketHelper;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ClassicListenersLogic.class, remap = false)
public abstract class MixinRoyalePouchEntry {
    // Classic onJoin charges uses before LISTENER_JOIN captures the personal inventory.
    @WrapOperation(method = "lambda$onJoin$19", at = @At(value = "INVOKE",
            target = "Liskallia/vault/gear/trinket/TrinketHelper;getTrinkets(Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/List;"))
    private List<TrinketHelper.TrinketStack<TrinketEffect<?>>> preservePersonalUses(LivingEntity entity,
            Operation<List<TrinketHelper.TrinketStack<TrinketEffect<?>>>> original, @Local(argsOnly = true) Vault vault) {
        return VaultUtils.isAnyRoyale(vault) ? List.of() : original.call(entity);
    }

    @WrapOperation(method = "lambda$onJoin$19", at = @At(value = "INVOKE",
            target = "Liskallia/vault/gear/trinket/TrinketHelper;getTrinkets(Lnet/minecraft/world/entity/LivingEntity;Ljava/lang/Class;)Ljava/util/List;"))
    private <T extends TrinketEffect<?>> List<TrinketHelper.TrinketStack<T>> preventPersonalTimeBonus(LivingEntity entity,
            Class<T> effectClass, Operation<List<TrinketHelper.TrinketStack<T>>> original, @Local(argsOnly = true) Vault vault) {
        return VaultUtils.isAnyRoyale(vault) ? List.of() : original.call(entity, effectClass);
    }
}
