package keystrokesmod.module.impl.render;

import keystrokesmod.Raven;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.awt.*;

public class Tracers extends Module {
    public ButtonSetting showInvis;
    public SliderSetting red;
    public SliderSetting green;
    public SliderSetting blue;
    public ButtonSetting rainbow;
    public SliderSetting lineWidth;

    private boolean viewBobbingEnabled;
    private int rgb_c = 0;

    public Tracers() {
        super("Tracers", category.render);
        this.registerSetting(showInvis = new ButtonSetting("Show invis", true));
        this.registerSetting(lineWidth = new SliderSetting("Line Width", 1.0D, 1.0D, 5.0D, 1.0D));
        this.registerSetting(red = new SliderSetting("Red", 0.0D, 0.0D, 255.0D, 1.0D));
        this.registerSetting(green = new SliderSetting("Green", 255.0D, 0.0D, 255.0D, 1.0D));
        this.registerSetting(blue = new SliderSetting("Blue", 0.0D, 0.0D, 255.0D, 1.0D));
        this.registerSetting(rainbow = new ButtonSetting("Rainbow", false));
    }

    @Override
    public void onEnable() {
        this.viewBobbingEnabled = mc.gameSettings.viewBobbing;
        if (this.viewBobbingEnabled) {
            mc.gameSettings.viewBobbing = false;
        }
    }

    @Override
    public void onDisable() {
        mc.gameSettings.viewBobbing = this.viewBobbingEnabled;
    }

    @Override
    public void onUpdate() {
        if (mc.gameSettings.viewBobbing) {
            mc.gameSettings.viewBobbing = false;
        }
    }

    @Override
    public void guiUpdate() {
        this.rgb_c = (new Color((int) red.getInput(), (int) green.getInput(), (int) blue.getInput())).getRGB();
    }

    @SubscribeEvent
    public void onRenderWorldLast(RenderWorldLastEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        int rgb = rainbow.isToggled() ? Utils.getChroma(2L, 0L) : this.rgb_c;
        if (Raven.DEBUG) {
            for (Entity en : mc.theWorld.loadedEntityList) {
                if (en instanceof EntityLivingBase && en != mc.thePlayer) {
                    RenderUtils.drawTracerLine(en, rgb, (float) lineWidth.getInput(), ((IAccessorMinecraft) mc).getTimer().renderPartialTicks);
                }
            }
        }
        else {
            for (EntityPlayer en : mc.theWorld.playerEntities) {
                if (en == mc.thePlayer) {
                    continue;
                }
                if (en.deathTime != 0) {
                    continue;
                }
                if (!showInvis.isToggled() && en.isInvisible()) {
                    continue;
                }

                if (!AntiBot.isBot(en)) {
                    RenderUtils.drawTracerLine(en, rgb, (float) lineWidth.getInput(), ((IAccessorMinecraft) mc).getTimer().renderPartialTicks);
                }
            }
        }
    }
}
