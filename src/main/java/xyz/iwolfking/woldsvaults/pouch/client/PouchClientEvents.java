package xyz.iwolfking.woldsvaults.pouch.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.TranslatableComponent;
import java.util.OptionalInt;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.common.inventory.CurioSlot;
import xyz.iwolfking.woldsvaults.WoldsVaults;
import xyz.iwolfking.woldsvaults.pouch.data.PouchRules;
import xyz.iwolfking.woldsvaults.pouch.menu.PouchMenu;
import xyz.iwolfking.woldsvaults.pouch.network.PouchNetwork;

@Mod.EventBusSubscriber(modid = WoldsVaults.MOD_ID, value = Dist.CLIENT)
public final class PouchClientEvents {
    private PouchClientEvents() {}

    @SubscribeEvent
    public static void renderTick(TickEvent.RenderTickEvent event) {
        PouchUseDisplay.clearFrame();
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        while (PouchClientSetup.OPEN_POUCH.consumeClick()) {
            if (client.player != null && client.screen == null) {
                PouchNetwork.CHANNEL.sendToServer(new PouchNetwork.Open(client.player.containerMenu.containerId, PouchMenu.EQUIPPED));
            }
        }
    }

    @SubscribeEvent
    public static void inventoryRightClick(ScreenEvent.MouseClickedEvent.Pre event) {
        Minecraft client = Minecraft.getInstance();
        if (event.getButton() != 1 || Screen.hasShiftDown() || client.player == null
                || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)
                || screen instanceof PouchScreen || !screen.getMenu().getCarried().isEmpty()) {
            return;
        }
        Slot slot = slotAt(screen, event.getMouseX(), event.getMouseY());
        if (slot == null || !PouchRules.isPouch(slot.getItem())) {
            return;
        }
        OptionalInt pouchSlot = openablePouchSlot(slot, client.player);
        if (pouchSlot.isEmpty()) {
            return;
        }
        // Cancel the pickup; the server resolves the stack from its own inventory or Curios slot.
        event.setCanceled(true);
        PouchNetwork.CHANNEL.sendToServer(new PouchNetwork.Open(client.player.containerMenu.containerId, pouchSlot.getAsInt()));
    }

    private static Slot slotAt(AbstractContainerScreen<?> screen, double mouseX, double mouseY) {
        for (Slot slot : screen.getMenu().slots) {
            int left = screen.getGuiLeft() + slot.x;
            int top = screen.getGuiTop() + slot.y;
            if (slot.isActive() && mouseX >= left - 1 && mouseX < left + 17 && mouseY >= top - 1 && mouseY < top + 17) {
                return slot;
            }
        }
        return null;
    }

    private static OptionalInt openablePouchSlot(Slot slot, LocalPlayer player) {
        // The equipped pouch stays viewable while locked in a vault, where its Curios slot refuses pickup.
        if (slot instanceof CurioSlot curioSlot && curioSlot.getIdentifier().equals("trinket_pouch") && curioSlot.getSlotIndex() == 0) {
            return OptionalInt.of(PouchMenu.EQUIPPED);
        }
        if (slot.container == player.getInventory() && slot.mayPickup(player)) {
            return OptionalInt.of(slot.getSlotIndex());
        }
        return OptionalInt.empty();
    }

    @SubscribeEvent
    public static void tooltip(ItemTooltipEvent event) {
        if (!PouchRules.isPouch(event.getItemStack())) {
            return;
        }
        event.getToolTip().add(new TranslatableComponent("item.woldsvaults.trinket_pouch.open").withStyle(ChatFormatting.GRAY));
        event.getToolTip().add((PouchClientSetup.OPEN_POUCH.isUnbound()
                ? new TranslatableComponent("item.woldsvaults.trinket_pouch.unbound")
                : new TranslatableComponent("item.woldsvaults.trinket_pouch.shortcut", PouchClientSetup.OPEN_POUCH.getTranslatedKeyMessage()))
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
