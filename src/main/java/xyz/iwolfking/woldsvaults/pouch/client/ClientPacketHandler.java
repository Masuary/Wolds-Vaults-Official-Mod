package xyz.iwolfking.woldsvaults.pouch.client;

import xyz.iwolfking.woldsvaults.pouch.menu.PouchMenu;
import xyz.iwolfking.woldsvaults.pouch.network.PouchNetwork;
import net.minecraft.client.Minecraft;

public final class ClientPacketHandler {
    private ClientPacketHandler() {}

    public static void receive(PouchNetwork.State message) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.containerMenu instanceof PouchMenu menu
                && menu.containerId == message.containerId() && message.data() != null) {
            menu.receiveState(message.data());
        }
    }
}
