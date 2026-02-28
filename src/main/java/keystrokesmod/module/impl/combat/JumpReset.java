package keystrokesmod.module.impl.combat;

import keystrokesmod.Raven;
import keystrokesmod.event.PostMotionEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class JumpReset extends Module {
    private SliderSetting chance;
    private SliderSetting minimizeMode;
    private ButtonSetting requireMouseDown;
    private ButtonSetting requireMovingForward;
    private ButtonSetting requireAim;

    private boolean setJump;
    private boolean ignoreNext;
    private boolean lastGround;
    private int lastHurtTime;
    private double lastFallDistance;

    private String[] minimizeModes = new String[] { "§cDisabled", "Vertical", "Horizontal" };

    public JumpReset() {
        super("Jump Reset", category.combat);
        this.registerSetting(chance = new SliderSetting("Chance", "%", 80, 0, 100, 1));
        this.registerSetting(minimizeMode = new  SliderSetting("Minimize", 2, minimizeModes));
        this.registerSetting(requireMouseDown = new ButtonSetting("Require mouse down", false));
        this.registerSetting(requireMovingForward = new ButtonSetting("Require moving forward", true));
        this.registerSetting(requireAim = new ButtonSetting("Require aim", true));
        this.closetModule = true;
    }

    @Override
    public String getInfo() {
        return (int) chance.getInput() == 100 ? "" : ((int) chance.getInput()) + "%";
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        int hurtTime = mc.thePlayer.hurtTime;
        boolean onGround = mc.thePlayer.onGround;

        if (onGround && lastFallDistance > 3 && !mc.thePlayer.capabilities.allowFlying) {
            ignoreNext = true;
        }

        if (lastGround && onGround) {
            ignoreNext = false;
        }

        if (hurtTime > lastHurtTime) {
            boolean mouseDown = mc.gameSettings.keyBindAttack.isKeyDown() || !requireMouseDown.isToggled();
            boolean aimingAt = !requireAim.isToggled() || checkAim();
            boolean forward = mc.gameSettings.keyBindForward.isKeyDown() || !requireMovingForward.isToggled();
            boolean randomization = (int) chance.getInput() == 100 || Utils.randomizeDouble(0, 100) < chance.getInput();
            boolean minimizing = true;
            boolean fov = Utils.inFov(Utils.getDirection(), 330, RotationUtils.deltaAngle(mc.thePlayer.motionX, mc.thePlayer.motionZ));

            switch ((int) minimizeMode.getInput()) {
                case 1: {
                    minimizing = mc.thePlayer.motionY >= 0.42f;
                } break;
                case 2: {
                    minimizing = Math.sqrt(Math.pow(mc.thePlayer.motionX, 2) + Math.pow(mc.thePlayer.motionZ, 2)) > 0.2;
                } break;
            }

            if (!ignoreNext && !mc.thePlayer.isBurning() && onGround && aimingAt && forward && mouseDown && randomization && minimizing && !hasBadEffect() && fov) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), setJump = true);
                KeyBinding.onTick(mc.gameSettings.keyBindJump.getKeyCode());
                if (Raven.DEBUG) {
                    Utils.sendModuleMessage(this, "&7jumping enabled, y " + mc.thePlayer.motionY + " xz " + Math.sqrt(Math.pow(mc.thePlayer.motionX, 2) + Math.pow(mc.thePlayer.motionZ, 2)));
                }
            }

            ignoreNext = false;
        }

        lastHurtTime = hurtTime;
        lastFallDistance = mc.thePlayer.fallDistance;
        lastGround = onGround;
    }

    @SubscribeEvent
    public void onPostMotion(PostMotionEvent e) {
        if (setJump && !Utils.jumpDown()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindJump.getKeyCode(), setJump = false);
            if (Raven.DEBUG) {
                Utils.sendModuleMessage(this, "&7jumping disabled");
            }
        }
    }

    private boolean hasBadEffect() {
        PotionEffect jump = mc.thePlayer.getActivePotionEffect(Potion.jump);
        PotionEffect poison = mc.thePlayer.getActivePotionEffect(Potion.poison);
        PotionEffect wither = mc.thePlayer.getActivePotionEffect(Potion.wither);
        return jump != null || poison != null || wither != null;
    }

    private boolean checkAim() {
        MovingObjectPosition result = mc.objectMouseOver;
        return result != null && result.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && result.entityHit instanceof EntityPlayer;
    }

}