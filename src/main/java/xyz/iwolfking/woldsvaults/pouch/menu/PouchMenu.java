package xyz.iwolfking.woldsvaults.pouch.menu;

import xyz.iwolfking.woldsvaults.pouch.PouchRegistration;
import xyz.iwolfking.woldsvaults.pouch.network.PouchNetwork;
import xyz.iwolfking.woldsvaults.pouch.data.PouchCapability;
import xyz.iwolfking.woldsvaults.pouch.data.PouchContents;
import xyz.iwolfking.woldsvaults.pouch.data.PouchMigration;
import xyz.iwolfking.woldsvaults.pouch.data.PouchRules;
import xyz.iwolfking.woldsvaults.pouch.data.PouchRuntime;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.network.NetworkHooks;

public final class PouchMenu extends AbstractContainerMenu {
    public static final int EQUIPPED = -1;
    // Vanilla 1.18.2 decodes container button IDs with readByte, not readUnsignedByte.
    public static final int SAVE_PRESET = 0;
    public static final int APPLY_PRESET = SAVE_PRESET + PouchContents.PRESET_COUNT;
    public static final int AUTO_REPLACE = APPLY_PRESET + PouchContents.PRESET_COUNT;
    public static final int TOGGLE_TRINKET = 100;
    private final Player owner;
    private final int pouchSlot;
    private final ItemStack pouch;
    private final PouchContents contents;
    private CompoundTag lastState;
    private String status = "";
    private boolean storedView;
    private boolean locked;

    public static void open(ServerPlayer player, int pouchSlot) {
        if (player.containerMenu instanceof PouchMenu || !player.containerMenu.getCarried().isEmpty()
                || !player.containerMenu.stillValid(player) || pouchSlot < EQUIPPED
                || pouchSlot >= player.getInventory().getContainerSize()) {
            return;
        }
        if (pouchSlot != EQUIPPED && player.containerMenu.slots.stream().noneMatch(slot ->
                slot.container == player.getInventory() && slot.getSlotIndex() == pouchSlot && slot.mayPickup(player))) {
            return;
        }
        ItemStack pouch = locate(player, pouchSlot);
        if (!PouchRules.isPouch(pouch)) {
            player.displayClientMessage(new TextComponent("Equip a trinket pouch or hold one to open it."), true);
            return;
        }
        PouchMigration.stored(pouch);
        PouchMigration.equipped(player);
        NetworkHooks.openGui(player, new SimpleMenuProvider((id, inventory, user) -> new PouchMenu(id, inventory, pouchSlot, pouch),
                pouch.getHoverName()), buffer -> {
                    buffer.writeInt(pouchSlot);
                    buffer.writeItem(pouch);
                });
    }

    public static PouchMenu client(int id, Inventory inventory, FriendlyByteBuf buffer) {
        return new PouchMenu(id, inventory, buffer.readInt(), buffer.readItem());
    }

    private PouchMenu(int id, Inventory inventory, int pouchSlot, ItemStack pouch) {
        super(PouchRegistration.POUCH_MENU.get(), id);
        this.owner = inventory.player;
        this.pouchSlot = pouchSlot;
        this.pouch = pouch;
        this.contents = PouchCapability.get(pouch);
        this.locked = PouchRules.locked(owner);
        for (int index = 0; index < PouchContents.SIZE; index++) {
            addSlot(new SlotItemHandler(contents, index, PouchLayout.GRID_X + 1 + index % 9 * 18,
                    PouchLayout.GRID_Y + 1 + index / 9 * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return !isLocked() && super.mayPlace(stack);
                }

                @Override
                public boolean mayPickup(Player player) {
                    return !isLocked() && super.mayPickup(player);
                }

                @Override
                public int getMaxStackSize(ItemStack stack) {
                    // The default SlotItemHandler probe temporarily empties the slot, which would erase presets.
                    return 1;
                }

                @Override
                public boolean isActive() {
                    return !owner.level.isClientSide || storedView;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addInventorySlot(inventory, column + row * 9 + 9, PouchLayout.GRID_X + 1 + column * 18, PouchLayout.INVENTORY_Y + 1 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            addInventorySlot(inventory, column, PouchLayout.GRID_X + 1 + column * 18, PouchLayout.HOTBAR_Y + 1);
        }
    }

    private void addInventorySlot(Inventory inventory, int index, int x, int y) {
        addSlot(new Slot(inventory, index, x, y) {
            @Override
            public boolean isActive() {
                return !owner.level.isClientSide || storedView;
            }

            @Override
            public boolean mayPickup(Player player) {
                return index != pouchSlot && !isLocked() && super.mayPickup(player);
            }

            @Override
            public boolean mayPlace(ItemStack stack) {
                return index != pouchSlot && !isLocked() && super.mayPlace(stack);
            }
        });
    }

    private static ItemStack locate(Player player, int slot) {
        if (slot == EQUIPPED) {
            return PouchRules.equipped(player);
        }
        return slot >= 0 && slot < player.getInventory().getContainerSize() ? player.getInventory().getItem(slot) : ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player == owner && player.isAlive() && (owner.level.isClientSide || locate(player, pouchSlot) == pouch);
    }

    @Override
    public void clicked(int slot, int button, ClickType click, Player player) {
        if (!stillValid(player) || isLocked() || click == ClickType.SWAP && button == pouchSlot) {
            return;
        }
        super.clicked(slot, button, click, player);
        if (!owner.level.isClientSide) {
            PouchRuntime.update(owner);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || isLocked() || index < 0 || index >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot source = slots.get(index);
        if (!source.hasItem() || !source.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = source.getItem();
        ItemStack previous = stack.copy();
        boolean moved = index < PouchContents.SIZE
                ? moveItemStackTo(stack, PouchContents.SIZE, slots.size(), true)
                : moveItemStackTo(stack, 0, PouchContents.SIZE, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            source.set(ItemStack.EMPTY);
        } else {
            source.setChanged();
        }
        source.onTake(player, stack);
        return previous;
    }

    @Override
    public boolean clickMenuButton(Player player, int button) {
        if (!stillValid(player) || isLocked() || player.level.isClientSide) {
            return false;
        }
        status = "";
        if (button >= TOGGLE_TRINKET && button < TOGGLE_TRINKET + PouchContents.SIZE) {
            int index = button - TOGGLE_TRINKET;
            List<Integer> active = new ArrayList<>(contents.activeIndices());
            if (!active.remove(Integer.valueOf(index))) {
                active.add(index);
            }
            apply(active);
        } else if (button >= SAVE_PRESET && button < SAVE_PRESET + PouchContents.PRESET_COUNT) {
            contents.savePreset(button - SAVE_PRESET);
            status = "Preset saved";
        } else if (button >= APPLY_PRESET && button < APPLY_PRESET + PouchContents.PRESET_COUNT) {
            apply(contents.preset(button - APPLY_PRESET));
        } else if (button == AUTO_REPLACE) {
            contents.toggleAutoReplace();
        } else {
            return false;
        }
        PouchRuntime.update(player);
        broadcastChanges();
        return true;
    }

    private void apply(List<Integer> active) {
        status = PouchRules.validate(pouch, contents, active, owner);
        if (status.isEmpty()) {
            contents.setActive(active);
        }
    }

    public boolean renamePreset(Player player, int preset, String name) {
        if (!stillValid(player) || isLocked() || player.level.isClientSide || preset < 0 || preset >= PouchContents.PRESET_COUNT) {
            return false;
        }
        try {
            contents.renamePreset(preset, name);
            status = "Preset renamed";
        } catch (IllegalArgumentException exception) {
            status = exception.getMessage();
        }
        broadcastChanges();
        return true;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (owner instanceof ServerPlayer player) {
            CompoundTag state = contents.serializeNBT();
            state.putBoolean("Locked", PouchRules.locked(owner));
            state.putString("Status", status);
            if (!state.equals(lastState)) {
                lastState = state;
                PouchNetwork.sendState(player, containerId, state);
            }
        }
    }

    public void receiveState(CompoundTag state) {
        if (!owner.level.isClientSide) {
            throw new IllegalStateException("Client pouch state must not mutate server storage");
        }
        contents.deserializeNBT(state);
        locked = state.getBoolean("Locked");
        status = state.getString("Status");
    }

    public PouchContents contents() { return contents; }
    public ItemStack pouch() { return pouch; }
    public boolean isEquipped() { return pouchSlot == EQUIPPED; }
    public String status() { return status; }
    public boolean isLocked() { return owner.level.isClientSide ? locked : PouchRules.locked(owner); }
    public boolean storedView() { return storedView; }
    public void setStoredView(boolean value) { storedView = value; }
}
