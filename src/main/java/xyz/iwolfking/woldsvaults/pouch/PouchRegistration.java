package xyz.iwolfking.woldsvaults.pouch;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import xyz.iwolfking.woldsvaults.WoldsVaults;
import xyz.iwolfking.woldsvaults.pouch.data.PouchCapability;
import xyz.iwolfking.woldsvaults.pouch.data.PouchContents;
import xyz.iwolfking.woldsvaults.pouch.data.PouchRuntime;
import xyz.iwolfking.woldsvaults.pouch.menu.PouchMenu;
import xyz.iwolfking.woldsvaults.pouch.network.PouchNetwork;

public final class PouchRegistration {
    public static final String MOD_ID = WoldsVaults.MOD_ID;
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.CONTAINERS, MOD_ID);
    public static final RegistryObject<MenuType<PouchMenu>> POUCH_MENU = MENUS.register("trinket_pouch", () -> IForgeMenuType.create(PouchMenu::client));

    private PouchRegistration() {}

    public static void register(IEventBus modBus) {
        if (LoadingModList.get().getModFileById("trinketcollection") != null) {
            throw new IllegalStateException("Trinket pouches are now built into Wolds. Remove the standalone Trinket Collection addon; its stored pouch data is supported natively.");
        }
        MENUS.register(modBus);
        modBus.addListener((RegisterCapabilitiesEvent event) -> event.register(PouchContents.class));
        MinecraftForge.EVENT_BUS.addGenericListener(ItemStack.class, PouchCapability::attach);
        MinecraftForge.EVENT_BUS.addListener(PouchRuntime::tick);
        MinecraftForge.EVENT_BUS.addListener(PouchRuntime::logout);
        PouchNetwork.register();
    }
}
