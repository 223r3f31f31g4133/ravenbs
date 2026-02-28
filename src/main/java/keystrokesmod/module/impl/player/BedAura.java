package keystrokesmod.module.impl.player;

import keystrokesmod.Raven;
import keystrokesmod.event.ClientRotationEvent;
import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.ReceivePacketEvent;
import keystrokesmod.lag.api.EnumLagDirection;
import keystrokesmod.lag.api.LagRequest;
import keystrokesmod.lag.timeout.ModuleBackedTimeout;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.minigames.BedWars;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.*;
import net.minecraft.block.Block;
import net.minecraft.block.BlockBed;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.network.play.client.C07PacketPlayerDigging;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.util.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.awt.*;
import java.util.*;
import java.util.List;

public class BedAura extends Module {
    private SliderSetting breakSpeed;
    private SliderSetting fov;
    public SliderSetting range;
    private SliderSetting rate;
    public ButtonSetting allowAura;
    private ButtonSetting breakNearBlock;
    private ButtonSetting resetProgress;
    public ButtonSetting disableBHop;
    private ButtonSetting disableBreakEffects;
    private ButtonSetting ignoreSlow;
    private SliderSetting groundSpoofMode;
    private SliderSetting ignoreVelocityMode;
    private ButtonSetting onlyWhileVisible;
    private ButtonSetting renderOutline;
    private ButtonSetting sendAnimations;
    private ButtonSetting silentSwing;

    private int OUTLINE_COLOR = new Color(226, 65, 65).getRGB();
    private int DEFAULT_OUTLINE_COLOR = new Color(226, 65, 65).getRGB();

    private BlockPos[] bedPos;
    public float breakProgress, groundBreakProgress;
    private int lastSlot = -1;
    public boolean rotate;
    public BlockPos currentBlock;
    private long lastCheck = 0;

    private BlockPos nearestBlock;

    private boolean aiming;
    private BlockPos previousBlockBroken;
    private BlockPos rotateLastBlock;

    private LagRequest inboundLag = null;

    private String[] groundSpoofModes = new String[] { "§cDisabled", "Wait", "Force", "Simulate" };
    private String[] ignoreVelocityModes = new String[] { "§cDisabled", "Cancel", "Delay" };

    public BedAura() {
        super("BedAura", category.player, 0);
        this.registerSetting(breakSpeed = new SliderSetting("Break speed", "x", 1, 0.8, 2, 0.01));
        this.registerSetting(fov = new SliderSetting("FOV", 360.0, 30.0, 360.0, 4.0));
        this.registerSetting(range = new SliderSetting("Range", 4.5, 1.0, 8.0, 0.5));
        this.registerSetting(rate = new SliderSetting("Rate", " second", 0.2, 0.05, 3.0, 0.05));
        this.registerSetting(allowAura = new ButtonSetting("Allow aura", true));
        this.registerSetting(breakNearBlock = new ButtonSetting("Break near block", false));
        this.registerSetting(resetProgress = new ButtonSetting("Reset progress", true));
        this.registerSetting(disableBHop = new ButtonSetting("Disable bhop", false));
        this.registerSetting(disableBreakEffects = new ButtonSetting("Disable break effects", false));
        this.registerSetting(groundSpoofMode = new SliderSetting("Ground spoof", 0, groundSpoofModes));
        this.registerSetting(ignoreSlow = new ButtonSetting("Ignore fatigue", false));
        this.registerSetting(ignoreVelocityMode = new SliderSetting("Ignore velocity", 0, ignoreVelocityModes));
        this.registerSetting(onlyWhileVisible = new ButtonSetting("Only while visible", false));
        this.registerSetting(renderOutline = new ButtonSetting("Render block outline", true));
        this.registerSetting(sendAnimations = new ButtonSetting("Send animations", false));
        this.registerSetting(silentSwing = new ButtonSetting("Silent swing", false));
    }

    @Override
    public String getInfo() {
        return ((int) breakSpeed.getInput() == breakSpeed.getInput() ? (int) breakSpeed.getInput() + "" : breakSpeed.getInput()) + breakSpeed.getSuffix();
    }

    @Override
    public void onDisable() {
        reset(false);
        previousBlockBroken = null;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) // takes priority over ka & antifireball
    public void onPreUpdate(PreUpdateEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (ModuleManager.bedwars != null && ModuleManager.bedwars.isEnabled() && BedWars.whitelistOwnBed.isToggled() && !BedWars.outsideSpawn) {
            reset(false);
            return;
        }
        if (Utils.isBedwarsPracticeOrReplay()) {
            return;
        }
        if (!mc.thePlayer.capabilities.allowEdit || mc.thePlayer.isSpectator()) {
            reset(false);
            return;
        }
        if (bedPos != null) {
            if (!(BlockUtils.getBlock(bedPos[0]) instanceof BlockBed) || (currentBlock != null && BlockUtils.replaceable(currentBlock)) || !RotationUtils.inRange(bedPos[0], range.getInput() * 1.5)) {
                reset(false);
            }
        }
        if (bedPos == null) {
            if (System.currentTimeMillis() - lastCheck >= rate.getInput() * 1000) {
                lastCheck = System.currentTimeMillis();
                bedPos = getBedPos();
            }
            if (bedPos == null) {
                reset(false);
                return;
            }
        }
        if (breakNearBlock.isToggled() && ((isCovered(bedPos[0]) && isCovered(bedPos[1])) && !(!resetProgress.isToggled() && currentBlock != null && BlockUtils.getBlock(currentBlock) instanceof BlockBed))) {
            if (nearestBlock == null) {
                nearestBlock = getBestBlock(bedPos, true);
            }
            breakBlock(nearestBlock);
        } else {
            nearestBlock = null;
            breakBlock(getBestBlock(bedPos, false) != null ? getBestBlock(bedPos, false) : bedPos[0]);
        }
    }

    @SubscribeEvent
    public void onWorldJoin(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) {
            currentBlock = null;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onReceivePacket(ReceivePacketEvent e) {
        if (!e.isCanceled() && e.getPacket() instanceof S12PacketEntityVelocity && ((S12PacketEntityVelocity) e.getPacket()).getEntityID() == mc.thePlayer.getEntityId()) {
            S12PacketEntityVelocity packet = (S12PacketEntityVelocity) e.getPacket();

            if (packet.getMotionX() / 8000.0 < 0.1 && packet.getMotionZ() / 8000.0 < 0.1) {
                return;
            }

            switch ((int) ignoreVelocityMode.getInput()) {
                case 1: {
                    e.setCanceled(true);
                } break;
                case 2: {
                    if (inboundLag != null && !inboundLag.getTimeout().isTimedOut()) {
                        Raven.lagHandler.requestLag(inboundLag);
                    }
                } break;
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onClientRotation(ClientRotationEvent e) {
        aiming = false;
        if ((rotate || breakProgress >= 1 || breakProgress == 0) && (currentBlock != null || rotateLastBlock != null)) {
            float[] rotations = RotationUtils.getRotations(currentBlock == null ? rotateLastBlock : currentBlock, RotationUtils.prevRenderYaw, RotationUtils.prevRenderPitch);
            if (currentBlock != null && !RotationUtils.inRange(currentBlock, range.getInput())) {
                return;
            }
            e.setYaw(RotationUtils.applyVanilla(rotations[0]));
            e.setPitch(rotations[1]);
            if (Raven.DEBUG) {
                Utils.sendModuleMessage(this, "&7rotating (&3" + mc.thePlayer.ticksExisted + "&7).");
            }
            rotate = false;
            aiming = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!renderOutline.isToggled() || currentBlock == null || !Utils.nullCheck() || bedPos == null) {
            return;
        }
        if (ModuleManager.bedESP != null && ModuleManager.bedESP.isEnabled()) {
            OUTLINE_COLOR = Theme.getGradient((int) ModuleManager.bedESP.theme.getInput(), 0);
        }
        else if (ModuleManager.hud != null && ModuleManager.hud.isEnabled()) {
            OUTLINE_COLOR = Theme.getGradient((int) ModuleManager.hud.theme.getInput(), 0);
        }
        else {
            OUTLINE_COLOR = DEFAULT_OUTLINE_COLOR;
        }
        RenderUtils.renderBlock(currentBlock, OUTLINE_COLOR, (Arrays.asList(bedPos).contains(currentBlock) ? 0.5625 : 1),true, false);
    }

    private BlockPos[] getBedPos() {
        int range;
        priority:
        for (int n = range = (int) this.range.getInput(); range >= -n; --range) {
            for (int j = -n; j <= n; ++j) {
                for (int k = -n; k <= n; ++k) {
                    final BlockPos blockPos = new BlockPos(mc.thePlayer.posX + j, mc.thePlayer.posY + range, mc.thePlayer.posZ + k);
                    final IBlockState getBlockState = mc.theWorld.getBlockState(blockPos);
                    if (getBlockState.getBlock() == Blocks.bed && getBlockState.getValue((IProperty) BlockBed.PART) == BlockBed.EnumPartType.FOOT) {
                        float fov = (float) this.fov.getInput();
                        if (fov != 360 && !Utils.inFov(fov, blockPos)) {
                            continue priority;
                        }
                        return new BlockPos[]{blockPos, blockPos.offset((EnumFacing) getBlockState.getValue((IProperty) BlockBed.FACING))};
                    }
                }
            }
        }
        return null;
    }

    public BlockPos getBestBlock(BlockPos[] positions, boolean getSurrounding) {
        if (positions == null || positions.length == 0) {
            return null;
        }
        HashMap<BlockPos, double[]> blockMap = new HashMap<>();
        for (BlockPos pos : positions) {
            if (pos == null) {
                continue;
            }
            if (getSurrounding) {
                for (EnumFacing enumFacing : EnumFacing.values()) {
                    if (enumFacing == EnumFacing.DOWN) {
                        continue;
                    }
                    BlockPos offset = pos.offset(enumFacing);
                    if (Arrays.asList(positions).contains(offset)) {
                        continue;
                    }
                    if (!RotationUtils.inRange(offset, range.getInput())) {
                        continue;
                    }
                    double[] efficiency = getEfficiency(offset);
                    double distance = mc.thePlayer.getDistanceSqToCenter(offset);
                    blockMap.put(offset, new double[]{distance, efficiency[0], efficiency[1]});
                }
            }
            else {
                if (!RotationUtils.inRange(pos, range.getInput())) {
                    continue;
                }
                double[] efficiency = getEfficiency(pos);
                double distance = mc.thePlayer.getDistanceSqToCenter(pos);
                blockMap.put(pos, new double[]{distance, efficiency[0], efficiency[1]});
            }
        }
        List<Map.Entry<BlockPos, double[]>> list = new ArrayList<>(blockMap.entrySet());
        list.sort(Comparator.comparingDouble(entry -> entry.getValue()[0]));

        if (groundSpoofMode.getInput() == 0) {
            list.sort((entry1, entry2) -> Double.compare(entry2.getValue()[1], entry1.getValue()[1]));
        } else {
            list.sort((entry1, entry2) -> Double.compare(entry2.getValue()[2], entry1.getValue()[2]));
        }

        return list.isEmpty() ? null : list.get(0).getKey();
    }

    private double[] getEfficiency(BlockPos pos) {
        Block block = BlockUtils.getBlock(pos);
        boolean ignoreFatigue = ignoreSlow.isToggled();
        return new double[] {
                BlockUtils.getBlockHardness(block, mc.thePlayer.getHeldItem(), ignoreFatigue, false),
                BlockUtils.getBlockHardness(block, mc.thePlayer.getHeldItem(), ignoreFatigue, true)
        };
    }

    private void reset(boolean continueLag) {
        bedPos = null;
        breakProgress = 0;
        groundBreakProgress = 0;
        rotate = false;
        nearestBlock = null;
        aiming = false;
        currentBlock = null;
        if (lastSlot != -1) {
            mc.thePlayer.inventory.currentItem = lastSlot;
        }
        lastSlot = -1;
        rotateLastBlock = null;

        if (inboundLag != null && !continueLag) {
            inboundLag.getTimeout().forceTimeOut();
            inboundLag = null;
        }
    }

    private void startBreak(BlockPos blockPos) {
        if (Raven.DEBUG) {
            Utils.sendModuleMessage(this, "sending c07 &astart &7break &7(&b" + mc.thePlayer.ticksExisted + "&7)");
        }
        mc.thePlayer.sendQueue.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.START_DESTROY_BLOCK, blockPos, EnumFacing.UP));
    }

    private void stopBreak(BlockPos blockPos) {
        if (Raven.DEBUG) {
            Utils.sendModuleMessage(this, "sending c07 &cstop &7break &7(&b" + mc.thePlayer.ticksExisted + "&7)");
        }
        mc.thePlayer.sendQueue.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.STOP_DESTROY_BLOCK, blockPos, EnumFacing.UP));
    }

    private void swing() {
        mc.thePlayer.swingItem();
    }

    private void breakBlock(BlockPos blockPos) {
        if (blockPos == null) {
            return;
        }
        float fov = (float) this.fov.getInput();
        if (fov != 360 && !Utils.inFov(fov, blockPos)) {
            return;
        }
        if (!RotationUtils.inRange(blockPos, range.getInput())) {
            return;
        }
        if (onlyWhileVisible.isToggled() && (mc.objectMouseOver == null || mc.objectMouseOver.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || !mc.objectMouseOver.getBlockPos().equals(blockPos))) {
            return;
        }
        if (BlockUtils.replaceable(currentBlock == null ? blockPos : currentBlock)) {
            reset(false);
            return;
        }

        if (inboundLag == null) {
            inboundLag = new LagRequest(EnumLagDirection.ONLY_INBOUND, new ModuleBackedTimeout(this));
        }

        currentBlock = blockPos;
        Block block = BlockUtils.getBlock(blockPos);

        if (!silentSwing.isToggled()) {
            swing();
        }

        if (breakProgress == 0 && !aiming) {
            return;
        }

        rotate = true;

        if (breakProgress == 0) {
            setSlot(Utils.getTool(block));
            startBreak(blockPos);
        } else {
            boolean enoughForNormal = breakProgress >= 1;
            boolean enoughForGround = groundBreakProgress >= 1;
            boolean canGroundSpoof = (groundSpoofMode.getInput() == 1 && mc.thePlayer.onGround) || groundSpoofMode.getInput() == 2 || groundSpoofMode.getInput() == 3;

            if ((enoughForNormal || (canGroundSpoof && enoughForGround)) && aiming) {
                if (groundSpoofMode.getInput() == 2) {
                    mc.thePlayer.onGround = true;
                }

                stopBreak(blockPos);
                previousBlockBroken = currentBlock;
                BlockPos nextBestBlock = getBestBlock(getBedPos(), true);
                reset(!resetProgress.isToggled() && nextBestBlock != null);

                if (!disableBreakEffects.isToggled()) {
                    mc.playerController.onPlayerDestroyBlock(blockPos, EnumFacing.UP);
                }

                rotate = true;
                rotateLastBlock = previousBlockBroken;

                if (!resetProgress.isToggled()) {
                    currentBlock = nextBestBlock;
                }

                return;
            }
        }

        double[] efficiency = getEfficiency(blockPos);

        breakProgress += (float) (efficiency[0] * breakSpeed.getInput());
        groundBreakProgress += (float) (efficiency[1] * breakSpeed.getInput());

        if (sendAnimations.isToggled()) {
            mc.theWorld.sendBlockBreakProgress(mc.thePlayer.getEntityId(), blockPos, (int) ((breakProgress * 10) - 1));
        }

        aiming = false;
    }

    private void setSlot(int slot) {
        if (slot == -1 || slot == mc.thePlayer.inventory.currentItem) {
            return;
        }
        if (lastSlot == -1) {
            lastSlot = mc.thePlayer.inventory.currentItem;
        }
        mc.thePlayer.inventory.currentItem = slot;
    }

    private boolean isCovered(BlockPos blockPos) {
        for (EnumFacing enumFacing : EnumFacing.values()) {
            BlockPos offset = blockPos.offset(enumFacing);
            if (BlockUtils.replaceable(offset) || BlockUtils.notFull(BlockUtils.getBlock(offset)) ) {
                return false;
            }
        }
        return true;
    }
}