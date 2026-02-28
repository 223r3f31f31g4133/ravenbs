package keystrokesmod.utility;

import keystrokesmod.Raven;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.util.BlockPos;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.util.EnumFacing.DOWN;

public class PacketUtils implements IMinecraftInstance {
    public static List<Packet> skipSendEvent = new ArrayList<>();
    public static List<Packet> skipReceiveEvent = new ArrayList<>();

    public static void sendPacketNoEvent(Packet packet) {
        if (packet == null || packet.getClass().getSimpleName().startsWith("S")) {
            return;
        }
        skipSendEvent.add(packet);
        Raven.mc.thePlayer.sendQueue.addToSendQueue(packet);
    }

    public static void receivePacketNoEvent(Packet packet) {
        try {
            skipReceiveEvent.add(packet);
            packet.processPacket(Raven.mc.getNetHandler());
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void sendReleasePacket() {
        mc.thePlayer.sendQueue.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, DOWN));
    }
}
