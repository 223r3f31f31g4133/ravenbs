package keystrokesmod.module.impl.player;

import keystrokesmod.Raven;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.lag.api.EnumLagDirection;
import keystrokesmod.lag.api.LagRequest;
import keystrokesmod.lag.timeout.ModuleBackedTimeout;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.awt.*;

public class Blink extends Module {
    private ButtonSetting initialPosition;

    private Vec3 pos;
    private int color = new Color(0, 255, 0, 120).getRGB();
    private int blinkTicks;

    public Blink() {
        super("Blink", category.player);
        this.registerSetting(initialPosition = new ButtonSetting("Show initial position", true));
    }

    @Override
    public void onEnable() {
        pos = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        blinkTicks = 0;
        Raven.lagHandler.requestLag(
                new LagRequest(
                        EnumLagDirection.ONLY_OUTBOUND,
                        new ModuleBackedTimeout(this)
                )
        );
    }

    @Override
    public String getInfo() {
        return String.valueOf(blinkTicks);
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        ++blinkTicks;
    }

    // TODO: move this to an external display for all lag system stuff
    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!Utils.nullCheck() || pos == null || !initialPosition.isToggled()) {
            return;
        }
        RenderUtils.drawPlayerBoundingBox(pos, color);
    }
}