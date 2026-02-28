package keystrokesmod.lag.handler;

import keystrokesmod.event.GameTickEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.lag.api.EnumLagDirection;
import keystrokesmod.lag.api.LagRequest;
import keystrokesmod.lag.queue.BiTrackLagNodeQueue;
import keystrokesmod.utility.ModuleUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.Minecraft;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("DuplicatedCode")
public final class UnifiedLagHandler extends AbstractFastTrackProvider {

    private final @NotNull BiTrackLagNodeQueue queue = new BiTrackLagNodeQueue(this);

    private final @NotNull List<Packet<?>> packetFastTrack = new ArrayList<>();

    public void requestLag(final @NotNull LagRequest request) {
        queue.requestLag(request);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onSendPacket(final @NotNull SendPacketEvent event) {
        if (Minecraft.getMinecraft().getNetHandler() == null) {
            queue.clear();
            return;
        }

        if (event.isCanceled() || event.getPacket().getClass().getSimpleName().startsWith("S") || event.getPacket() instanceof C00PacketLoginStart || event.getPacket() instanceof C00Handshake) {
            return;
        }

        final @NotNull Packet<?> packet = event.getPacket();

        if (packetFastTrack.remove(packet)) {
            return;
        }

        if (queue.tick(packet, EnumLagDirection.OUTBOUND)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onReceivePacket(final @NotNull ReceivePacketEvent event) {
        if (Minecraft.getMinecraft().getNetHandler() == null) {
            queue.clear();
            return;
        }

        if (event.isCanceled()) {
            return;
        }

        final @NotNull Packet<?> packet = event.getPacket();

        if (packetFastTrack.remove(packet)) {
            return;
        }

        if (queue.tick(packet, EnumLagDirection.INBOUND)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onGameTick(final @NotNull GameTickEvent event) {
        if (Minecraft.getMinecraft().getNetHandler() == null) {
            queue.clear();
            return;
        }

        queue.tick(null, null);
    }

    @Override
    public void forPacket(final @NotNull Packet<?> packet) {
        packetFastTrack.add(packet);
    }

}