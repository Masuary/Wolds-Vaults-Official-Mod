package xyz.iwolfking.woldsvaults.mixins.vaulthunters.custom;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import iskallia.vault.block.entity.RoyaleDrafterControllerTileEntity;
import iskallia.vault.entity.entity.TommyEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RoyaleDrafterControllerTileEntity.class, remap = false)
public abstract class MixinRoyaleDrafterIdentity {
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", remap = true,
            target = "Lnet/minecraft/world/level/Level;getEntity(I)Lnet/minecraft/world/entity/Entity;"))
    private static Entity resolveOwnDrafter(Level level, int entityId, Operation<Entity> original,
            @Local(argsOnly = true) BlockPos controllerPosition) {
        Entity entity = original.call(level, entityId);
        // Entity IDs are reassigned on restart; never let a stale ID capture a player or another controller's mob.
        return entity instanceof TommyEntity drafter && drafter.getTommyPos().equals(controllerPosition) ? entity : null;
    }
}
