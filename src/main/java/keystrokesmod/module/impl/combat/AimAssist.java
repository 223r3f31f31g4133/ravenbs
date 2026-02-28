package keystrokesmod.module.impl.combat;

import keystrokesmod.event.ClientLookEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.helper.RotationHelper;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

public class AimAssist extends Module {

    private SliderSetting mode;
    private SliderSetting speed;
    private SliderSetting fov;
    private SliderSetting range;

    private ButtonSetting rotateYaw;
    private ButtonSetting rotatePitch;
    private ButtonSetting aimInvis;
    private ButtonSetting clickAim;
    private ButtonSetting ignoreTeammates;
    private ButtonSetting stopOnTarget;
    private ButtonSetting stopWhenBreaking;
    private ButtonSetting weaponOnly;

    private String[] AIM_MODES = new String[]{"Normal", "Silent"};

    private Float[] lookingAt = null;

    public AimAssist() {
        super("AimAssist", category.combat);
        this.registerSetting(mode = new SliderSetting("Mode", 0, AIM_MODES));
        this.registerSetting(speed = new SliderSetting("Speed", 45.0D, 1.0D, 100.0D, 1.0D));
        this.registerSetting(fov = new SliderSetting("FOV", 90.0D, 15.0D, 360.0D, 1.0D));
        this.registerSetting(range = new SliderSetting("Range", 4.5D, 1.0D, 10.0D, 0.5D));
        this.registerSetting(rotateYaw = new ButtonSetting("Rotate yaw", true));
        this.registerSetting(rotatePitch = new ButtonSetting("Rotate pitch", true));
        this.registerSetting(aimInvis = new ButtonSetting("Aim invis", false));
        this.registerSetting(clickAim = new ButtonSetting("Click aim", true));
        this.registerSetting(ignoreTeammates = new ButtonSetting("Ignore teammates", true));
        this.registerSetting(stopOnTarget = new ButtonSetting("Stop on target", false));
        this.registerSetting(stopWhenBreaking = new ButtonSetting("Stop when breaking", false));
        this.registerSetting(weaponOnly = new ButtonSetting("Weapon only", false));
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        this.lookingAt = null;
        if (mode.getInput() == 0 || !conditionsMet()) {
            return;
        }
        Entity en = this.getEnemy();

        if (en == null) {
            return;
        }

        if (stopOnTarget.isToggled() && mc.objectMouseOver != null && mc.objectMouseOver.entityHit == en) {
            return;
        }

        float yaw = RotationUtils.serverRotations[0];
        float pitch = RotationUtils.serverRotations[1];
        boolean yawRotated = false;
        boolean pitchRotated = false;

        if (rotateYaw.isToggled()) {
            double diff = Utils.aimDifference(en, this.mode.getInput() == 1);
            float val = (float) (-(diff / (101.0D - speed.getInput()))) * 1.2F;
            yaw = RotationUtils.serverRotations[0] + val;
            RotationHelper.get().setYaw(yaw);
            yawRotated = true;
        }

        if (rotatePitch.isToggled()) {
            double diffPitch = Utils.pitchDifference(en, this.mode.getInput() == 1);
            float valPitch = (float) (-(diffPitch / (101.0D - speed.getInput()))) * 1.2F;
            pitch = RotationUtils.serverRotations[1] + valPitch;
            RotationHelper.get().setPitch(pitch);
            pitchRotated = true;
        }

        if (yawRotated && pitchRotated) {
            lookingAt = new Float[]{yaw, pitch};
        }
        else if (yawRotated) {
            lookingAt = new Float[]{yaw};
        }
        else if (pitchRotated) {
            lookingAt = new Float[]{null, pitch};
        }
    }

    @Override
    public void onUpdate() {
        if (mode.getInput() == 1 || !conditionsMet()) {
            return;
        }
        Entity en = this.getEnemy();
        if (en == null) {
            return;
        }

        if (stopOnTarget.isToggled() && mc.objectMouseOver != null && mc.objectMouseOver.entityHit == en) {
            return;
        }

        if (rotateYaw.isToggled()) {
            double yawDiff = Utils.aimDifference(en, false);
            if (yawDiff > 1.0D || yawDiff < -1.0D) {
                float val = (float) (-(yawDiff / (101.0D - (speed.getInput()))));
                mc.thePlayer.rotationYaw += val;
            }
        }

        if (rotatePitch.isToggled()) {
            double pitchDiff = Utils.pitchDifference(en, false);
            if (pitchDiff > 1.0D || pitchDiff < -1.0D) {
                float val = (float) (-(pitchDiff / (101.0D - (speed.getInput()))));
                mc.thePlayer.rotationPitch += val;
            }
        }
    }

    @SubscribeEvent
    public void onClientLook(ClientLookEvent e) {
        if (this.lookingAt == null) {
            return;
        }
        if (this.lookingAt.length == 2) {
            if (this.lookingAt[1] != null) {
                e.pitch = this.lookingAt[1];
            }
        }
        if (this.lookingAt[0] != null) {
            e.yaw = this.lookingAt[0];
        }
    }

    private Entity getEnemy() {
        final int fov = (int) this.fov.getInput();
        for (final EntityPlayer entityPlayer : mc.theWorld.playerEntities) {
            if (entityPlayer != mc.thePlayer && entityPlayer.deathTime == 0) {
                if (Utils.isFriended(entityPlayer)) {
                    continue;
                }
                if (ignoreTeammates.isToggled() && Utils.isTeammate(entityPlayer)) {
                    continue;
                }
                if (!aimInvis.isToggled() && entityPlayer.isInvisible()) {
                    continue;
                }
                if (mc.thePlayer.getDistanceToEntity(entityPlayer) > range.getInput()) {
                    continue;
                }
                if (AntiBot.isBot(entityPlayer)) {
                    continue;
                }
                if (fov != 360 && !Utils.inFov((float) fov, entityPlayer)) {
                    continue;
                }
                return entityPlayer;
            }
        }
        return null;
    }

    private boolean conditionsMet() {
        if (mc.currentScreen != null || !mc.inGameHasFocus) {
            return false;
        }
        if (weaponOnly.isToggled() && !Utils.holdingWeapon()) {
            return false;
        }
        if (clickAim.isToggled() && !Utils.isClicking()) {
            return false;
        }
        if (stopWhenBreaking.isToggled() && isMining()) {
            return false;
        }
        return true;
    }

    private boolean isMining() {
        return mc.gameSettings.keyBindAttack.isKeyDown() && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && mc.objectMouseOver.getBlockPos() != null;
    }
}