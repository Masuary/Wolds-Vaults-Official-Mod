package xyz.iwolfking.woldsvaults.pouch.data;

import iskallia.vault.block.entity.RoyaleDrafterControllerTileEntity;
import iskallia.vault.container.RoyaleDraftContainer;
import iskallia.vault.core.vault.Vault;
import iskallia.vault.core.vault.VaultUtils;
import iskallia.vault.core.vault.player.Runner;
import iskallia.vault.gear.trinket.TrinketEffect;
import iskallia.vault.gear.trinket.TrinketEffectRegistry;
import iskallia.vault.integration.IntegrationCurios;
import iskallia.vault.item.gear.TrinketItem;
import iskallia.vault.item.gear.VaultUsesHelper;
import iskallia.vault.world.data.RoyaleInventorySnapshotData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import xyz.iwolfking.woldsvaults.WoldsVaults;
import xyz.iwolfking.woldsvaults.items.TrinketPouchItem;

/** Royale owns its temporary pouch. Normal menu mutation remains locked. */
public final class PouchRoyale {
    private static final String SESSION = "WoldsRoyaleSession";
    private static final String OWNER = "WoldsRoyaleOwner";
    private static final String CLAIMS = "WoldsRoyaleClaims";

    private PouchRoyale() {}

    public static void equipAfterSnapshot(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer)) return;
        if (!RoyaleInventorySnapshotData.get(serverPlayer.getLevel()).hasSnapshot(player)) return;
        ItemStack current = PouchRules.equipped(player);
        if (PouchRules.isPouch(current) && TrinketPouchItem.isTemporary(current)) return;
        if (!current.isEmpty()) throw new IllegalStateException("Royale snapshot did not remove the original pouch");
        ItemStack temporary = TrinketPouchItem.create(WoldsVaults.id("prismatic"), true);
        temporary.getOrCreateTag().putUUID(OWNER, player.getUUID());
        if (!IntegrationCurios.setCurioItemStack(player, temporary, "trinket_pouch", 0)) {
            throw new IllegalStateException("Cannot equip the Royale pouch: trinket_pouch slot is unavailable");
        }
    }

    public static boolean select(ServerPlayer player, ResourceLocation reward, int entityId, Boolean expectedBlueOffer) {
        Vault vault = VaultUtils.getVault(player.level).orElse(null);
        if (!player.isAlive() || vault == null || !VaultUtils.isAnyRoyale(vault)
                || !(vault.get(Vault.LISTENERS).get(player.getUUID()) instanceof Runner)
                || !RoyaleInventorySnapshotData.get(player.getLevel()).hasSnapshot(player)) {
            return reject(player, "session");
        }
        if (!(player.containerMenu instanceof RoyaleDraftContainer draft) || draft.getEntityId() != entityId) {
            return reject(player, "draft");
        }
        RoyaleDrafterControllerTileEntity controller = draft.getControllerTile();
        if (controller == null || controller.getMonitoredEntityId() != entityId
                || player.level.getEntity(entityId) == null
                || player.distanceToSqr(player.level.getEntity(entityId)) > 64.0) {
            return reject(player, "draft");
        }
        int tab = controller.getTab(player.getUUID());
        // The native bare-loadout button advances only the client tab. Its first reward is still stage one.
        int stage = tab == 0 ? 1 : tab;
        if (stage != 1 && stage != 2 || expectedBlueOffer != null && expectedBlueOffer != (stage == 1)) {
            return reject(player, "consumed");
        }
        List<ResourceLocation> offered = stage == 1 ? controller.getBlueTrinketPresets(player.getUUID())
                : controller.getRedTrinketPresets(player.getUUID());
        if (offered == null || !offered.contains(reward)) return reject(player, "offer");
        TrinketEffect<?> effect = TrinketEffectRegistry.getEffect(reward);
        if (effect == null) return reject(player, "offer");
        ItemStack pouch = PouchRules.equipped(player);
        if (!PouchRules.isPouch(pouch) || !TrinketPouchItem.isTemporary(pouch)) return reject(player, "pouch");
        CompoundTag tag = pouch.getOrCreateTag();
        if (!tag.hasUUID(OWNER) || !tag.getUUID(OWNER).equals(player.getUUID())
                || tag.hasUUID(SESSION) && !tag.getUUID(SESSION).equals(vault.get(Vault.ID))) return reject(player, "pouch");
        String claim = controller.getBlockPos().asLong() + ":" + stage;
        if (tag.getCompound(CLAIMS).contains(claim)) return reject(player, "consumed");
        ItemStack stack = TrinketItem.createBaseTrinket(effect);
        VaultUsesHelper.setUses(stack, 2);
        VaultUsesHelper.addUsedVault(stack, vault.get(Vault.ID));
        PouchContents contents = PouchCapability.get(pouch);
        int slot = contents.firstEmpty();
        if (slot < 0) return reject(player, "full");
        // Validate against an isolated candidate so a failed choice cannot erase selections or consume the offer.
        ItemStack candidate = pouch.copy();
        PouchContents candidateContents = PouchCapability.get(candidate);
        candidateContents.setStackInSlot(slot, stack);
        List<Integer> active = new ArrayList<>(contents.activeIndices());
        active.add(slot);
        // Native canEquip rejects all modern Royale vaults. The validated reward path deliberately
        // bypasses that general editing lock while retaining capacity and duplicate-effect rules.
        String error = PouchRules.validate(candidate, candidateContents, active, null);
        if (!error.isEmpty()) {
            player.displayClientMessage(new TranslatableComponent("gui.woldsvaults.pouch.royale.invalid", error), false);
            player.closeContainer();
            return false;
        }
        contents.setStackInSlot(slot, stack);
        contents.setActive(active);
        tag.putUUID(SESSION, vault.get(Vault.ID));
        CompoundTag claims = tag.getCompound(CLAIMS);
        claims.putString(claim, reward.toString());
        tag.put(CLAIMS, claims);
        controller.setTab(stage + 1, player.getUUID());
        controller.setChanged();
        PouchRuntime.update(player);
        player.containerMenu.broadcastChanges();
        return true;
    }

    public static void discardTemporary(Player player) {
        ItemStack pouch = PouchRules.equipped(player);
        if (PouchRules.isPouch(pouch) && TrinketPouchItem.isTemporary(pouch)) {
            PouchRuntime.clear(player);
            if (!IntegrationCurios.setCurioItemStack(player, ItemStack.EMPTY, "trinket_pouch", 0)) {
                throw new IllegalStateException("Cannot remove the temporary Royale pouch");
            }
        }
    }

    private static boolean reject(ServerPlayer player, String reason) {
        player.displayClientMessage(new TranslatableComponent("gui.woldsvaults.pouch.royale." + reason), false);
        if (player.containerMenu instanceof RoyaleDraftContainer) player.closeContainer();
        return false;
    }
}
