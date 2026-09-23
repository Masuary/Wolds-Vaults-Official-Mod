package xyz.iwolfking.woldsvaults.mixins.vaulthunters.custom;

import iskallia.vault.world.data.RoyaleInventorySnapshotData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xyz.iwolfking.woldsvaults.pouch.data.PouchRoyale;
import xyz.iwolfking.woldsvaults.pouch.data.PouchRuntime;

@Mixin(value = RoyaleInventorySnapshotData.class, remap = false)
public abstract class MixinRoyalePouchSnapshot {
    @Inject(method = "createSnapshot", at = @At("HEAD"), cancellable = true)
    private void beforeCapture(Player player, CallbackInfo callback) {
        if (((RoyaleInventorySnapshotData) (Object) this).hasSnapshot(player)) {
            // A reconnect or repeated join must never replace the saved personal inventory with the run's inventory.
            callback.cancel();
            return;
        }
        PouchRuntime.clear(player);
    }

    @Inject(method = "createSnapshot", at = @At("TAIL"))
    private void afterCapture(Player player, CallbackInfo callback) {
        PouchRoyale.equipAfterSnapshot(player);
    }

    @Inject(method = "restoreSnapshot", at = @At("HEAD"), cancellable = true)
    private void beforeRestore(Player player, CallbackInfoReturnable<Boolean> callback) {
        if (!player.isAlive()) {
            // Upstream removes the snapshot before apply checks life state. Retain it until respawn instead.
            callback.setReturnValue(false);
        } else if (player instanceof ServerPlayer serverPlayer
                && RoyaleInventorySnapshotData.get(serverPlayer.getLevel()).hasSnapshot(player)) {
            PouchRuntime.clear(player);
        }
    }
}
