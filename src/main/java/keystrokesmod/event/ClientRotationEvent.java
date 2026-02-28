package keystrokesmod.event;

import net.minecraftforge.fml.common.eventhandler.Event;

public class ClientRotationEvent extends Event {
    public Float yaw;
    public Float pitch;

    public ClientRotationEvent(Float yaw, Float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public void setYaw(Float yaw) {
        this.yaw = yaw;
    }

    public void setPitch(Float pitch) {
        this.pitch = pitch;
    }

    public void setRotations(Float yaw, Float pitch) {
        this.yaw = yaw;
        this.pitch = pitch;
    }
}
