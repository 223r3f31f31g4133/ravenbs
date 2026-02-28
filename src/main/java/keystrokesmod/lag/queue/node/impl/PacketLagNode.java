package keystrokesmod.lag.queue.node.impl;

import keystrokesmod.lag.api.EnumLagDirection;
import keystrokesmod.lag.handler.AbstractFastTrackProvider;
import keystrokesmod.lag.queue.node.api.AbstractLagNode;
import net.minecraft.network.Packet;
import org.jetbrains.annotations.NotNull;

public final class PacketLagNode extends AbstractLagNode {

    private final @NotNull Packet<?> packet;
    private final @NotNull EnumLagDirection direction;

    public PacketLagNode(final @NotNull Packet<?> packet, final @NotNull EnumLagDirection direction) {
        this.packet = packet;
        this.direction = direction;
    }

    public void goThrough(final @NotNull AbstractFastTrackProvider fastTrack) {
        fastTrack.forPacket(packet);
        direction.passThroughChannel(packet);
    }

}