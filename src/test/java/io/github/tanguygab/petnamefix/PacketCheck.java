package io.github.tanguygab.petnamefix;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.channel.ChannelDuplexHandler;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData.DataValue;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.fox.Fox;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.NoSuchElementException;

/** Executable checks against the actual Paper dev bundle, without a running server. */
public final class PacketCheck {
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        var ownerField = TamableAnimal.class.getDeclaredField("DATA_OWNERUUID_ID");
        ownerField.setAccessible(true);
        int ownerId = ((EntityDataAccessor<?>) ownerField.get(null)).id();
        var owner = new DataValue<>(ownerId, EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE, Optional.empty());
        var name = new DataValue<>(2, EntityDataSerializers.OPTIONAL_COMPONENT, Optional.empty());
        var mixed = new ClientboundSetEntityDataPacket(42, List.of(owner, name));
        var ownerOnly = new ClientboundSetEntityDataPacket(42, List.of(owner));
        var unrelated = new ClientboundSetEntityDataPacket(43,
                List.of(new DataValue<>(ownerId, EntityDataSerializers.OPTIONAL_COMPONENT, Optional.empty())));
        var channel = new EmbeddedChannel(new PipelineInjector.BukkitChannelDuplexHandler());
        try {
            assert channel.writeOutbound(mixed);
            ClientboundSetEntityDataPacket filtered = channel.readOutbound();
            assert filtered.id() == 42 && filtered.packedItems().equals(List.of(name));
            assert mixed.packedItems().equals(List.of(owner, name)) : "Shared packet was mutated";

            var promise = channel.newPromise();
            channel.writeAndFlush(ownerOnly, promise);
            assert promise.isSuccess() && channel.readOutbound() == null : "Dropped write must complete";

            assert channel.writeOutbound(unrelated);
            assert channel.readOutbound() == unrelated : "Other optional metadata must survive";

            assert channel.writeOutbound(new ClientboundBundlePacket(List.of(ownerOnly, mixed, unrelated)));
            ClientboundBundlePacket bundle = channel.readOutbound();
            var packets = bundle.subPackets().iterator();
            assert ((ClientboundSetEntityDataPacket) packets.next()).packedItems().equals(List.of(name));
            assert packets.next() == unrelated && !packets.hasNext();

            promise = channel.newPromise();
            channel.writeAndFlush(new ClientboundBundlePacket(List.of(ownerOnly)), promise);
            assert promise.isSuccess() && channel.readOutbound() == null : "Empty bundle must complete";

            promise = channel.newPromise();
            channel.writeAndFlush(new ClientboundSetEntityDataPacket(42, List.of()), promise);
            assert promise.isSuccess() && channel.readOutbound() == null;
            var owned = new ClientboundSetEntityDataPacket(42, List.of(new DataValue<>(ownerId,
                    EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE,
                    Optional.of(EntityReference.<LivingEntity>of(UUID.randomUUID())))));
            channel.writeAndFlush(owned, channel.voidPromise());
            channel.checkException();
            assert channel.readOutbound() == null : "Populated owner must be hidden with Paper's void promise";

            for (String fieldName : List.of("DATA_TRUSTED_ID_0", "DATA_TRUSTED_ID_1")) {
                var field = Fox.class.getDeclaredField(fieldName);
                field.setAccessible(true);
                int trustedId = ((EntityDataAccessor<?>) field.get(null)).id();
                var trusted = new ClientboundSetEntityDataPacket(43, List.of(new DataValue<>(trustedId,
                        EntityDataSerializers.OPTIONAL_LIVING_ENTITY_REFERENCE, Optional.empty())));
                assert channel.writeOutbound(trusted);
                assert channel.readOutbound() == trusted : "Fox trust metadata must survive";
            }

            var lifecycle = new EmbeddedChannel();
            try {
                lifecycle.pipeline().addLast("packet_handler", new ChannelDuplexHandler());
                PipelineInjector.inject(lifecycle);
                PipelineInjector.inject(lifecycle);
                assert lifecycle.pipeline().names().stream().filter("PetNameFix"::equals).count() == 1;
                assert lifecycle.writeOutbound(mixed);
                ClientboundSetEntityDataPacket output = lifecycle.readOutbound();
                assert output.packedItems().equals(List.of(name));
                PipelineInjector.uninject(lifecycle);
                PipelineInjector.uninject(lifecycle);
                assert lifecycle.writeOutbound(mixed);
                assert lifecycle.readOutbound() == mixed : "Unload must restore normal packet delivery";
                lifecycle.pipeline().remove("packet_handler");
                boolean failed = false;
                try {
                    PipelineInjector.inject(lifecycle);
                } catch (NoSuchElementException expected) {
                    failed = true;
                }
                assert failed : "Missing packet handler must surface instead of silently disabling the fix";
            } finally {
                lifecycle.finishAndReleaseAll();
            }
            System.out.println("Packet and lifecycle checks passed (12 scenarios).");
        } finally {
            channel.finishAndReleaseAll();
        }
    }
}
