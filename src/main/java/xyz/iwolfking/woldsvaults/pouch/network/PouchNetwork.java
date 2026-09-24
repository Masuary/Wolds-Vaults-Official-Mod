package xyz.iwolfking.woldsvaults.pouch.network;

import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import xyz.iwolfking.woldsvaults.WoldsVaults;
import xyz.iwolfking.woldsvaults.pouch.data.PouchContents;
import xyz.iwolfking.woldsvaults.pouch.menu.PouchMenu;

public final class PouchNetwork {
    private static final String VERSION = "3";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(WoldsVaults.id("trinket_pouch"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private PouchNetwork() {}

    public static void register() {
        CHANNEL.messageBuilder(Open.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, buffer) -> { buffer.writeInt(message.containerId()); buffer.writeInt(message.inventorySlot()); })
                .decoder(buffer -> new Open(buffer.readInt(), buffer.readInt()))
                .consumer(PouchNetwork::open).add();
        CHANNEL.messageBuilder(State.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((message, buffer) -> { buffer.writeVarInt(message.containerId()); buffer.writeNbt(message.data()); })
                .decoder(buffer -> new State(buffer.readVarInt(), buffer.readNbt()))
                .consumer(PouchNetwork::state).add();
        CHANNEL.messageBuilder(Rename.class, 2, NetworkDirection.PLAY_TO_SERVER)
                .encoder((message, buffer) -> {
                    buffer.writeInt(message.containerId());
                    buffer.writeVarInt(message.preset());
                    buffer.writeUtf(message.name(), PouchContents.MAX_PRESET_NAME_LENGTH);
                })
                .decoder(buffer -> new Rename(buffer.readInt(), buffer.readVarInt(), buffer.readUtf(PouchContents.MAX_PRESET_NAME_LENGTH)))
                .consumer(PouchNetwork::rename).add();
    }

    private static void open(Open message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.containerMenu.containerId == message.containerId()) {
                PouchMenu.open(player, message.inventorySlot());
            }
        });
        context.setPacketHandled(true);
    }

    private static void rename(Rename message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player != null && player.containerMenu instanceof PouchMenu menu && menu.containerId == message.containerId()) {
                menu.renamePreset(player, message.preset(), message.name());
            }
        });
        context.setPacketHandled(true);
    }

    private static void state(State message, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> xyz.iwolfking.woldsvaults.pouch.client.ClientPacketHandler.receive(message));
        context.setPacketHandled(true);
    }

    public static void sendState(ServerPlayer player, int containerId, CompoundTag data) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new State(containerId, data));
    }

    public record Open(int containerId, int inventorySlot) {}
    public record State(int containerId, CompoundTag data) {}
    public record Rename(int containerId, int preset, String name) {}
}
