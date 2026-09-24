package xyz.iwolfking.woldsvaults.pouch.client;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
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
        Slot slot = screen.getSlotUnderMouse();
        if (slot == null || slot.container != client.player.getInventory() || !slot.mayPickup(client.player)
                || !PouchRules.isPouch(slot.getItem())) {
            return;
        }
        // Cancel the inventory pickup; the server resolves the stack from its own inventory.
        event.setCanceled(true);
        PouchNetwork.CHANNEL.sendToServer(new PouchNetwork.Open(client.player.containerMenu.containerId, slot.getSlotIndex()));
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
