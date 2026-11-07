package io.github.tanguygab.petnamefix;

import io.netty.channel.*;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData.DataValue;
import net.minecraft.world.entity.TamableAnimal;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.lang.reflect.Field;
import java.util.*;

public class PipelineInjector {

    private static final String DECODER_NAME = "PetNameFix";
    /** DataWatcher position of pet owner field */
    private static final int PET_OWNER_POSITION;

    static {
        try {
            Field petOwnerId = TamableAnimal.class.getDeclaredField("DATA_OWNERUUID_ID");
            petOwnerId.setAccessible(true);
            PET_OWNER_POSITION = ((EntityDataAccessor<?>)petOwnerId.get(null)).id();
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }


    public void unload() {
        Bukkit.getServer().getOnlinePlayers().forEach(this::uninject);
    }

    public void inject(Player player) {
        inject(getChannel(player));
    }

    static void inject(Channel channel) {
        uninject(channel);
        channel.pipeline().addBefore("packet_handler", DECODER_NAME, new BukkitChannelDuplexHandler());
    }

    public void uninject(Player player) {
        uninject(getChannel(player));
    }

    static void uninject(Channel channel) {
        if (channel != null && channel.pipeline().names().contains(DECODER_NAME))
            channel.pipeline().remove(DECODER_NAME);
    }

    private Channel getChannel(Player player) {
        return ((CraftPlayer) player).getHandle().connection.connection.channel;
    }

    public static class BukkitChannelDuplexHandler extends ChannelDuplexHandler {

        @Override
        public void write(ChannelHandlerContext ctx, Object packet, ChannelPromise promise) throws Exception {
            switch (packet) {
                case ClientboundBundlePacket bundle -> {
                    List<Packet<? super ClientGamePacketListener>> validPackets = new ArrayList<>();
                    for (Packet<? super ClientGamePacketListener> pack : bundle.subPackets()) {
                        if (pack instanceof ClientboundSetEntityDataPacket data) {
                            pack = filterMetadata(data);
                        }
                        if (pack != null) validPackets.add(pack);
                    }
                    if (validPackets.isEmpty()) {
                        promise.setSuccess();
                        return;
                    }
                    packet = new ClientboundBundlePacket(validPackets);
                }
                case ClientboundSetEntityDataPacket data -> {
                    packet = filterMetadata(data);
                    if (packet == null) {
                        promise.setSuccess();
                        return;
                    }
                }
                default -> {}
            }
            super.write(ctx, packet, promise);
        }
    }

    private static ClientboundSetEntityDataPacket filterMetadata(ClientboundSetEntityDataPacket packet) {
        // Packets can be shared between viewers and contain immutable metadata lists.
        List<DataValue<?>> items = packet.packedItems().stream()
                .filter(Objects::nonNull)
                .filter(item -> item.id() != PET_OWNER_POSITION
                        || item.serializer() != EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE)
                .toList();
        if (items.isEmpty()) return null;
        if (items.size() == packet.packedItems().size()) return packet;
        return new ClientboundSetEntityDataPacket(packet.id(), items);
    }
}
