package keystrokesmod.module.impl.player;

import keystrokesmod.event.PrePlayerInputEvent;
import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.mixin.interfaces.IMixinItemRenderer;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.script.ScriptDefaults;
import keystrokesmod.script.model.Simulation;
import keystrokesmod.script.model.Vec3;
import keystrokesmod.utility.Utils;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraftforge.fml.common.eventhandler.Event;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.GameRegistry;

public class LegitScaffold extends Module {
    private SliderSetting edgeOffset;
    private SliderSetting unsneakDelay;
    private SliderSetting sneakOnJump;
    private ButtonSetting holdingBlocks;
    private ButtonSetting lookingDown;
    private ButtonSetting disableForward;

    private double HW = 0.3;
    private double[][] CORNERS = {{ -HW, -HW }, { HW, -HW }, { -HW, HW }, { HW, HW }};
    private boolean sneakingFromMod;
    private boolean placed;
    private boolean forceRelease;
    private int sneakJumpDelayTicks = -1;
    private int sneakJumpStartTick = -1;
    private int unsneakDelayTicks = -1;
    private int unsneakStartTick = -1;

    public LegitScaffold() {
        super("LegitScaffold", category.player);


        this.registerSetting(edgeOffset = new SliderSetting("Edge offset", " blocks", 0, 0, 0.3, 0.01));
        this.registerSetting(unsneakDelay = new SliderSetting("Unsneak delay", "ms", 50, 50, 300, 5));
        this.registerSetting(sneakOnJump = new SliderSetting("Sneak on jump", "ms", 0, 0, 500, 5));
        this.registerSetting(holdingBlocks = new ButtonSetting("Holding blocks", false));
        this.registerSetting(lookingDown = new ButtonSetting("Looking down", false));
        this.registerSetting(disableForward = new ButtonSetting("Disable forward", false));
    }

    public void onDisable() {
        sneakingFromMod = false;
        resetUnsneak();
    }

    @SubscribeEvent
    public void onPrePlayerInputEvent(PrePlayerInputEvent e) {
        if (e.getForward() == 0 && e.getStrafe() == 0) {
            resetUnsneak();
            repressSneak(e);
            return;
        }

        if (mc.currentScreen != null ||
                disableForward.isToggled() && e.getForward() > 0 ||
                lookingDown.isToggled() && mc.thePlayer.rotationPitch < 70 ||
                holdingBlocks.isToggled() && !Utils.holdingBlocks()) {
            sneakingFromMod = false;
            resetUnsneak();
            return;
        }

        if (e.isJump() && mc.thePlayer.onGround && (e.getForward() != 0 || e.getStrafe() != 0) && sneakOnJump.getInput() > 0) {
            if (forceRelease) {
                sneakJumpStartTick = mc.thePlayer.ticksExisted;
                double raw = sneakOnJump.getInput() / 50;
                int base = (int) raw;
                sneakJumpDelayTicks = base + (Utils.randomizeDouble(0, 1) < (raw - base) ? 1 : 0);
                pressSneak(e, true);
                return;
            }
        }

        Vec3 position = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        Simulation sim = Simulation.create();
        if (mc.thePlayer.isSneaking()) {
            sim.setForward(e.getForward() / 0.3f);
            sim.setStrafe(e.getStrafe() / 0.3f);
            sim.setSneak(false);
        }
        sim.tick();
        Vec3 simPosition = sim.getPosition();

        double edgeOffsetValue = computeEdgeOffset(simPosition, position);
        if (Double.isNaN(edgeOffsetValue)) {
            if (sneakingFromMod) tryReleaseSneak(e, true);
            return;
        }

        boolean shouldSneak = edgeOffsetValue > edgeOffset.getInput();
        boolean shouldRelease = sneakingFromMod;

        if (shouldSneak) {
            pressSneak(e, true);
        } else if (shouldRelease) {
            tryReleaseSneak(e, true);
        }
    }

    private void repressSneak(PrePlayerInputEvent e) {
        if (forceRelease && mc.gameSettings.keyBindSneak.isKeyDown()) {
            e.setSneak(true);
        }
        forceRelease = false;
    }

    private void pressSneak(PrePlayerInputEvent e, boolean resetDelay) {
        e.setSneak(true);
        sneakingFromMod = true;
        if (resetDelay) {
            unsneakStartTick = -1;
        }
        repressSneak(e);
    }

    private void tryReleaseSneak(PrePlayerInputEvent e, boolean resetDelay) {
        int existed = mc.thePlayer.ticksExisted;
        if (unsneakStartTick == -1 && sneakJumpStartTick == -1) {
            unsneakStartTick = existed;
            double raw = (unsneakDelay.getInput() - 50) / 50;
            int base = (int) raw;
            unsneakDelayTicks = base + (Utils.randomizeDouble(0, 1) < (raw - base) ? 1 : 0);
        }

        if (existed - sneakJumpStartTick < sneakJumpDelayTicks) {
            pressSneak(e, false);
            return;
        }
        if (existed - unsneakStartTick < unsneakDelayTicks) {
            pressSneak(e, false);
            return;
        }

        releaseSneak(e, resetDelay);
    }

    void releaseSneak(PrePlayerInputEvent e, boolean resetDelay) {
        e.setSneak(false);
        if (sneakingFromMod && mc.gameSettings.keyBindSneak.isKeyDown() && (placed || !mc.thePlayer.onGround)) {
            forceRelease = true;
        }

        sneakingFromMod = placed = false;
        if (resetDelay) {
            resetUnsneak();
        }
    }

    private void resetUnsneak() {
        unsneakStartTick = sneakJumpStartTick = sneakJumpDelayTicks = unsneakDelayTicks = -1;
    }

    private double computeEdgeOffset(Vec3 pos1, Vec3 pos2) {
        int floorY = (int)(pos1.y - 0.01);
        double best = Double.NaN;

        for (double[] c : CORNERS) {
            int bx = (int)Math.floor(pos2.x + c[0]);
            int bz = (int)Math.floor(pos2.z + c[1]);
            if (ScriptDefaults.world.getBlockAt(bx, floorY, bz).name.equals("air")) continue;

            double offX = Math.abs(pos1.x - (bx + (pos1.x < bx + 0.5 ? 0 : 1)));
            double offZ = Math.abs(pos1.z - (bz + (pos1.z < bz + 0.5 ? 0 : 1)));
            boolean xDiff = (int)Math.floor(pos1.x) != bx;
            boolean zDiff = (int)Math.floor(pos1.z) != bz;

            double cornerDist;
            if (xDiff) {
                cornerDist = zDiff ? Math.max(offX, offZ) : offX;
            } else {
                cornerDist = zDiff ? offZ : 0;
            }

            best = Double.isNaN(best) ? cornerDist : Math.min(best, cornerDist);
        }

        return best;
    }

}