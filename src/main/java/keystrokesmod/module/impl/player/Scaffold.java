package keystrokesmod.module.impl.player;

import keystrokesmod.event.*;
import keystrokesmod.helper.ScaffoldBlockCountHelper;
import keystrokesmod.mixin.impl.accessor.IAccessorEntityPlayerSP;
import keystrokesmod.mixin.interfaces.IMixinItemRenderer;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.combat.KillAura;
import keystrokesmod.module.impl.movement.LongJump;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.*;
import keystrokesmod.utility.Timer;
import net.minecraft.block.BlockTNT;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.server.S12PacketEntityVelocity;
import net.minecraft.network.play.server.S27PacketExplosion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.*;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.commons.lang3.RandomUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import scala.tools.nsc.symtab.classfile.Pickler;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Scaffold extends Module {
    private final SliderSetting motion;
    public SliderSetting rotation, fakeRotation;
    private SliderSetting fastScaffold;
    public ButtonSetting autoSwap;
    private ButtonSetting cancelKnockBack;
    private ButtonSetting fastOnRMB;
    public ButtonSetting highlightBlocks;
    private ButtonSetting jumpFacingForward;
    public ButtonSetting safeWalk;
    public ButtonSetting showBlockCount;
    private ButtonSetting silentSwing;

    private String[] rotationModes = new String[] { "§cDisabled", "Simple", "Offset", "Precise", "Accurate"};
    private String[] fakeRotationModes = new String[] { "§cDisabled", "Strict", "Smooth", "Spin" };
    private String[] fastScaffoldModes = new String[] { "§cDisabled", "Keep-Y" };

    public Map<BlockPos, Timer> highlight = new HashMap<>();

    private ScaffoldBlockCountHelper scaffoldBlockCount;

    public AtomicInteger lastSlot = new AtomicInteger(-1);

    public boolean hasSwapped;
    private boolean hasPlaced;

    private boolean rotateForward;
    private double startYPos = -1;
    public boolean fastScaffoldKeepY;
    private boolean firstKeepYPlace;
    private boolean rotatingForward;
    private int keepYTicks;
    public boolean lowhop;
    private int rotationDelay;
    private int blockSlot = -1;

    private float fakeYaw1, fakeYaw2;

    public boolean canBlockFade;

    private Vec3 targetBlock;
    private PlaceData blockInfo;
    private Vec3 hitVec, lookVec;
    private float[] blockRotations;
    public float scaffoldYaw, scaffoldPitch, blockYaw, yawOffset;
    private boolean set2;

    public boolean moduleEnabled;
    public boolean isEnabled;
    private boolean disabledModule;
    private boolean dontDisable, towerEdge;
    private int disableTicks;
    private int scaffoldTicks;

    private boolean was451, was452;

    private float minOffset;
    private float minPitch = 80F;

    private float edge;

    private long firstStroke;
    private float lastEdge2, yawAngle, theYaw;
    private float fakeYaw, fakePitch;
    private float lastPlacementYaw = 0;

    private int currentFace;
    private boolean enabledOffGround = false;
    private int groundTicks;
    private int airTicks;

    public Scaffold() {
        super("Scaffold", category.player);
        this.registerSetting(motion = new SliderSetting("Motion", "%", 100, 50, 150, 1));
        this.registerSetting(rotation = new SliderSetting("Rotation", 1, rotationModes));
        this.registerSetting(fakeRotation = new SliderSetting("Rotation (fake)", 0, fakeRotationModes));
        this.registerSetting(fastScaffold = new SliderSetting("Fast scaffold", 0, fastScaffoldModes));
        this.registerSetting(autoSwap = new ButtonSetting("Auto swap", true));
        this.registerSetting(cancelKnockBack = new ButtonSetting("Cancel knockback", false));
        this.registerSetting(fastOnRMB = new ButtonSetting("Fast on RMB", true));
        this.registerSetting(highlightBlocks = new ButtonSetting("Highlight blocks", true));
        this.registerSetting(jumpFacingForward = new ButtonSetting("Jump facing forward", false));
        this.registerSetting(safeWalk = new ButtonSetting("Safewalk", true));
        this.registerSetting(showBlockCount = new ButtonSetting("Show block count", true));
        this.registerSetting(silentSwing = new ButtonSetting("Silent swing", false));

        this.alwaysOn = true;
    }

    public void onDisable() {
        if (ModuleManager.tower.canTower() && (ModuleManager.tower.dCount == 0 || !Utils.isMoving())) {
            towerEdge = true;
        }
        disabledModule = true;
        moduleEnabled = false;
    }

    public void onEnable() {
        isEnabled = true;
        moduleEnabled = true;
        ModuleUtils.fadeEdge = 0;
        edge = -999999929;
        minPitch = 80F;
        if (!mc.thePlayer.onGround) {
            rotationDelay = 2;
            enabledOffGround = true;
        }
        lastEdge2 = mc.thePlayer.rotationYaw;

        FMLCommonHandler.instance().bus().register(scaffoldBlockCount = new ScaffoldBlockCountHelper(mc));
        lastSlot.set(-1);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouse(MouseEvent e) {
        if (!isEnabled) {
            return;
        }
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
        KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        e.setCanceled(true);
    }

    @SubscribeEvent
    public void onTick(TickEvent e) {
        if (mc.thePlayer == null) return;
        if (mc.thePlayer.onGround) {
            airTicks = 0;
            groundTicks++;
        }
        else {
            airTicks++;
            groundTicks = 0;
        }
    }

    @SubscribeEvent
    public void onClientRotation(ClientRotationEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (!isEnabled) {
            return;
        }
        if (Utils.isMoving()) {
            scaffoldTicks++;
        }
        else {
            scaffoldTicks = 0;
        }
        canBlockFade = true;



        /// Needs reworking
        if (Utils.keysDown() && usingFastScaffold() && fastScaffold.getInput() >= 1 && !ModuleManager.tower.canTower() && !ModuleManager.longJump.function) { // jump mode
            if (mc.thePlayer.onGround && Utils.isMoving()) {
                if (scaffoldTicks > 1) {
                    //if (startYPos == -1 || Math.abs(startYPos - e.posY) > 5) {
                        //startYPos = e.posY;
                        //fastScaffoldKeepY = true;
                    //}
                }
            }
        }
        else if (fastScaffoldKeepY) {
            fastScaffoldKeepY = firstKeepYPlace = false;
            startYPos = -1;
            keepYTicks = 0;
        }

        if (targetBlock != null) {
            Vec3 lookAt = new Vec3(targetBlock.xCoord - lookVec.xCoord, targetBlock.yCoord - lookVec.yCoord, targetBlock.zCoord - lookVec.zCoord);
            blockRotations = RotationUtils.getRotations(lookAt);
            targetBlock = null;
            fakeYaw1 = mc.thePlayer.rotationYaw - hardcodedYaw();
        }
        if (blockRotations == null) {
            fakeYaw1 = mc.thePlayer.rotationYaw - hardcodedYaw();
        }

        switch ((int) rotation.getInput()) {
            case 1:
                scaffoldYaw = mc.thePlayer.rotationYaw - hardcodedYaw();
                scaffoldPitch = 79F;
                if (currentFace == 1) {
                    scaffoldPitch = 87F;
                }
                e.setRotations(scaffoldYaw, scaffoldPitch);
                break;
            case 2:
                float moveAngle = (float) getMovementAngle();
                float relativeYaw = mc.thePlayer.rotationYaw + moveAngle;
                float normalizedYaw = (relativeYaw % 360 + 360) % 360;
                float quad = normalizedYaw % 90;

                float side = MathHelper.wrapAngleTo180_float(getMotionYaw() - scaffoldYaw);
                float yawBackwards = MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw) - hardcodedYaw();
                float blockYawOffset = MathHelper.wrapAngleTo180_float(yawBackwards - blockYaw);

                long strokeDelay = 250;

                if (quad <= 5 || quad >= 85) {
                    yawAngle = 127.40F;
                    minOffset = 13;
                    minPitch = 75.48F;
                }
                if (quad > 5 && quad <= 15 || quad >= 75 && quad < 85) {
                    yawAngle = 128.55F;
                    minOffset = 11;
                    minPitch = 75.74F;
                }
                if (quad > 15 && quad <= 25 || quad >= 65 && quad < 75) {
                    yawAngle = 129.70F;
                    minOffset = 8;
                    minPitch = 75.95F;
                }
                if (quad > 25 && quad <= 32 || quad >= 58 && quad < 65) {
                    yawAngle = 130.85F;
                    minOffset = 6;
                    minPitch = 76.13F;
                }
                if (quad > 32 && quad <= 38 || quad >= 52 && quad < 58) {
                    yawAngle = 131.80F;
                    minOffset = 5;
                    minPitch = 76.41F;
                }
                if (quad > 38 && quad <= 42 || quad >= 48 && quad < 52) {
                    yawAngle = 134.30F;
                    minOffset = 4;
                    minPitch = 77.54F;
                }
                if (quad > 42 && quad <= 45 || quad >= 45 && quad < 48) {
                    yawAngle = 137.85F;
                    minOffset = 3;
                    minPitch = 77.93F;
                }

                float offset = yawAngle;


                if (firstStroke > 0 && (System.currentTimeMillis() - firstStroke) > strokeDelay) {
                    firstStroke = 0;
                }
                if (enabledOffGround) {
                    if (blockRotations != null) {
                        scaffoldYaw = blockRotations[0];
                        scaffoldPitch = blockRotations[1];
                    }
                    else {
                        scaffoldYaw = mc.thePlayer.rotationYaw - hardcodedYaw();
                        scaffoldPitch = 78f;
                    }
                    e.setRotations(scaffoldYaw, scaffoldPitch);
                    break;
                }

                if (blockRotations != null) {
                    blockYaw = blockRotations[0];
                    scaffoldPitch = blockRotations[1];
                    yawOffset = blockYawOffset;
                    if (scaffoldPitch < minPitch) {
                        scaffoldPitch = minPitch;
                    }
                } else {
                    scaffoldPitch = minPitch;
                    if (edge == 1) {
                        firstStroke = System.currentTimeMillis();
                    }
                    yawOffset = 0;
                }
                if (!Utils.isMoving() || Utils.getHorizontalSpeed() == 0.0D) {
                    e.setRotations(theYaw, scaffoldPitch);
                    break;
                }

                float motionYaw = getMotionYaw();

                float newYaw = motionYaw - offset * Math.signum(
                        MathHelper.wrapAngleTo180_float(motionYaw - scaffoldYaw)
                );
                scaffoldYaw = MathHelper.wrapAngleTo180_float(newYaw);

                if (quad > 5 && quad < 85) {
                    if (quad < 45F) {
                        if (firstStroke == 0) {
                            if (side >= 0) {
                                set2 = false;
                            } else {
                                set2 = true;
                            }
                        }
                        if (was452) {
                            firstStroke = System.currentTimeMillis();
                        }
                        was451 = true;
                        was452 = false;
                    } else {
                        if (firstStroke == 0) {
                            if (side >= 0) {
                                set2 = true;
                            } else {
                                set2 = false;
                            }
                        }
                        if (was451) {
                            firstStroke = System.currentTimeMillis();
                        }
                        was452 = true;
                        was451 = false;
                    }
                }

                double minSwitch = (!Utils.scaffoldDiagonal(false)) ? 9 : 15;
                if (side >= 0) {
                    if (yawOffset <= -minSwitch && firstStroke == 0) {
                        if (quad <= 5 || quad >= 85) {
                            if (set2) {
                                firstStroke = System.currentTimeMillis();
                            }
                            set2 = false;
                        }
                    } else if (yawOffset >= 0 && firstStroke == 0) {
                        if (quad <= 5 || quad >= 85) {
                            if (yawOffset >= minSwitch) {
                                if (!set2) {
                                    firstStroke = System.currentTimeMillis();
                                }
                                set2 = true;
                            }
                        }
                    }
                    if (set2) {
                        if (yawOffset <= -0) yawOffset = -0;
                        if (yawOffset >= minOffset) yawOffset = minOffset;
                        theYaw = (scaffoldYaw + offset * 2) - yawOffset;
                        e.setRotations(theYaw, scaffoldPitch);
                        break;
                    }
                } else if (side <= -0) {
                    if (yawOffset >= minSwitch && firstStroke == 0) {
                        if (quad <= 5 || quad >= 85) {
                            if (set2) {
                                firstStroke = System.currentTimeMillis();
                            }
                            set2 = false;
                        }
                    } else if (yawOffset <= 0 && firstStroke == 0) {
                        if (quad <= 5 || quad >= 85) {
                            if (yawOffset <= -minSwitch) {
                                if (!set2) {
                                    firstStroke = System.currentTimeMillis();
                                }
                                set2 = true;
                            }
                        }
                    }
                    if (set2) {
                        if (yawOffset >= 0) yawOffset = 0;
                        if (yawOffset <= -minOffset) yawOffset = -minOffset;
                        theYaw = (scaffoldYaw - offset * 2) - yawOffset;
                        e.setRotations(theYaw, scaffoldPitch);
                        break;
                    }
                }

                if (side >= 0) {
                    if (yawOffset >= 0) yawOffset = 0;
                    if (yawOffset <= -minOffset) yawOffset = -minOffset;
                } else if (side <= -0) {
                    if (yawOffset <= -0) yawOffset = -0;
                    if (yawOffset >= minOffset) yawOffset = minOffset;
                }
                theYaw = scaffoldYaw - yawOffset;
                e.setRotations(theYaw, scaffoldPitch);
                break;
            case 3:
                if (blockRotations != null) {
                    scaffoldYaw = blockRotations[0];
                    scaffoldPitch = blockRotations[1];
                }
                else {
                    scaffoldYaw = mc.thePlayer.rotationYaw - hardcodedYaw();
                    scaffoldPitch = 80F;
                }
                e.setRotations(scaffoldYaw, scaffoldPitch);
                //theYaw = e.getYaw();
                break;
        }
        if (edge != 1) {
            firstStroke = System.currentTimeMillis();
            edge = 1;
        }
        if (mc.thePlayer.onGround) {
            enabledOffGround = false;
        }

        //jump facing forward
        if (ModuleUtils.inAirTicks >= 1) {
            rotateForward = false;
        }
        if (rotateForward && jumpFacingForward.isToggled()) {
            if (rotation.getInput() > 0) {
                if (!rotatingForward) {
                    rotationDelay = 2;
                    rotatingForward = true;
                }
                float forwardYaw = (mc.thePlayer.rotationYaw - hardcodedYaw() - 180);
                e.setYaw(forwardYaw);
                e.setPitch(20f + RandomUtils.nextFloat(20f,50f));
            }
        }
        else {
            rotatingForward = false;
        }

        if (ModuleManager.tower.isVerticalTowering()) {
            if (blockRotations != null) {
                e.setYaw(blockRotations[0]);
            }
            if (ModuleManager.tower.yaw != 0) {
                e.setYaw(ModuleManager.tower.yaw);
            }
            if (ModuleManager.tower.pitch != 0) {
                e.setPitch(ModuleManager.tower.pitch);
            }
        }

        //pitch fix
        //if (e.getPitch() > 89.9F) {
            //e.setPitch(89.9F);
        //}

        if (rotationDelay > 0) --rotationDelay;

        //Fake rotations
        if (fakeRotation.getInput() > 0) {
            if (fakeRotation.getInput() == 1) {
                fakeYaw = fakeYaw1;
                if (blockRotations != null) {
                    fakePitch = blockRotations[1] + 5;
                } else {
                    fakePitch = scaffoldPitch;
                }
            }
            else if (fakeRotation.getInput() == 2) {
                fakeYaw2 = mc.thePlayer.rotationYaw - hardcodedYaw();
                float yawDifference = getAngleDifference(lastEdge2, fakeYaw2);
                float smoothingFactor = (1.0f - (65.0f / 100.0f));
                fakeYaw2 = (lastEdge2 + yawDifference * smoothingFactor);
                lastEdge2 = fakeYaw2;

                fakeYaw = fakeYaw2;
                if (blockRotations != null) {
                    fakePitch = blockRotations[1] + 5;
                } else {
                    fakePitch = scaffoldPitch;
                }
            }
            else if (fakeRotation.getInput() == 3) {
                fakeYaw += 25.71428571428571F;
                fakePitch = 90F;
            }
            RotationUtils.setFakeRotations(fakeYaw, fakePitch);
        }
    }

    @SubscribeEvent
    public void onSendPacket(SendPacketEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (!isEnabled) {
            return;
        }
        if (e.getPacket() instanceof C08PacketPlayerBlockPlacement) {
            currentFace = ((C08PacketPlayerBlockPlacement) e.getPacket()).getPlacedBlockDirection();
        }
    }

    @SubscribeEvent
    public void onSlotUpdate(SlotUpdateEvent e) {
        if (isEnabled) {
            lastSlot.set(e.slot);
        }
    }

    @SubscribeEvent
    public void onPreUpdate(PreUpdateEvent e) {
        if (!isEnabled) {
            return;
        }
        if (LongJump.function) {
            startYPos = -1;
        }
        if (holdingBlocks() && setSlot()) {

            if (LongJump.stopModules) {
                return;
            }
            if (KillAura.target != null  || ModuleManager.killAura.stoppedTargeting) {
                return;
            }

            hasSwapped = true;
            int mode = (int) fastScaffold.getInput();
            if (rotation.getInput() == 0 || rotationDelay == 0) {
                placeBlock(0, 0);
            }
            if (ModuleManager.tower.placeExtraBlock) {
                placeBlock(0, -1);
            }
            if (fastScaffoldKeepY && !ModuleManager.tower.canTower()) {
                ++keepYTicks;
                if ((int) mc.thePlayer.posY > (int) startYPos) {
                    switch (mode) {
                        case 1:
                            if (!firstKeepYPlace && keepYTicks == 8 || keepYTicks == 11) {
                                placeBlock(1, 0);
                                firstKeepYPlace = true;
                            }
                            break;
                        case 2:
                            if (!firstKeepYPlace && keepYTicks == 8 || firstKeepYPlace && keepYTicks == 7) {
                                placeBlock(1, 0);
                                firstKeepYPlace = true;
                            }
                            break;
                        case 3:
                            if (!firstKeepYPlace && keepYTicks == 7) {
                                placeBlock(1, 0);
                                firstKeepYPlace = true;
                            }
                            break;
                        case 6:
                            if (!firstKeepYPlace && keepYTicks == 3) {
                                placeBlock(1, 0);
                                firstKeepYPlace = true;
                            }
                            break;
                    }
                }
                if (mc.thePlayer.onGround) keepYTicks = 0;
                if ((int) mc.thePlayer.posY == (int) startYPos) firstKeepYPlace = false;
            }
            handleMotion();
        }

        if (disabledModule) {
            if (dontDisable && ++disableTicks >= 2) {
                isEnabled = false;
            }
            this.lastPlacementYaw = 0;
            if (!dontDisable) {
                isEnabled = false;
            }
            if (!isEnabled) {
                disabledModule = dontDisable = false;
                disableTicks = 0;
                if (ModuleManager.tower.speed) {
                    Utils.setSpeed(Utils.getHorizontalSpeed(mc.thePlayer) / 1.6);
                }

                if (lastSlot.get() != -1) {
                    mc.thePlayer.inventory.currentItem = lastSlot.get();
                    lastSlot.set(-1);
                }
                blockSlot = -1;
                if (autoSwap.isToggled() && ModuleManager.autoSwap.spoofItem.isToggled()) {
                    ((IMixinItemRenderer) mc.getItemRenderer()).setCancelUpdate(false);
                    ((IMixinItemRenderer) mc.getItemRenderer()).setCancelReset(false);
                }
                scaffoldBlockCount.beginFade();
                hasSwapped = hasPlaced = false;
                targetBlock = null;
                blockInfo = null;
                blockRotations = null;
                fastScaffoldKeepY = firstKeepYPlace = rotateForward = rotatingForward = towerEdge =
                        was451 = was452 = enabledOffGround = false;
                rotationDelay = keepYTicks = scaffoldTicks = 0;
                firstStroke = 0;
                startYPos = -1;
                lookVec = null;
            }
        }
    }

    @SubscribeEvent
    public void onReceivePacket(ReceivePacketEvent e) {
        if (!isEnabled) {
            return;
        }
        if (!Utils.nullCheck() || !cancelKnockBack.isToggled()) {
            return;
        }
        if (e.getPacket() instanceof S12PacketEntityVelocity) {
            if (((S12PacketEntityVelocity) e.getPacket()).getEntityID() == mc.thePlayer.getEntityId()) {
                e.setCanceled(true);
            }
        }
        else if (e.getPacket() instanceof S27PacketExplosion) {
            e.setCanceled(true);
        }
    }

    @Override
    public String getInfo() {
        String info;
        info = rotationModes[(int) rotation.getInput()];
        if (info.equals("§cDisabled")) {
            return "Disabled";
        }
        return info;
    }

    public boolean stopFastPlace() {
        return this.isEnabled();
    }

    float getAngleDifference(float from, float to) {
        float difference = (to - from) % 360.0F;
        if (difference < -180.0F) {
            difference += 360.0F;
        } else if (difference >= 180.0F) {
            difference -= 360.0F;
        }
        return difference;
    }

    private boolean usingFastScaffold() {
        return fastScaffold.getInput() > 0 && (!fastOnRMB.isToggled() || (Mouse.isButtonDown(1) || ModuleManager.bHop.isEnabled()) && Utils.tabbedIn());
    }

    private void place(PlaceData block) {
        ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (heldItem == null || !(heldItem.getItem() instanceof ItemBlock) || !Utils.canBePlaced((ItemBlock) heldItem.getItem())) {
            return;
        }
        MovingObjectPosition raycast = RotationUtils.rayTraceCustom(mc.playerController.getBlockReachDistance(), RotationUtils.serverRotations[0], RotationUtils.serverRotations[1]);
        if (raycast != null && raycast.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && raycast.getBlockPos().equals(block.blockPos) && raycast.sideHit.equals(block.enumFacing)) {
            block.hitVec = raycast.hitVec;
        }
        if (mc.playerController.onPlayerRightClick(mc.thePlayer, mc.theWorld, heldItem, block.blockPos, block.enumFacing, block.hitVec)) {
            if (silentSwing.isToggled()) {
                mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
            }
            else {
                mc.thePlayer.swingItem();
                if (!(autoSwap.isToggled() && ModuleManager.autoSwap.spoofItem.isToggled())) {
                    mc.getItemRenderer().resetEquippedProgress();
                }
            }
            highlight.put(block.blockPos.offset(block.enumFacing), null);
            hasPlaced = true;
        }
    }

    public int totalBlocks() {
        int totalBlocks = 0;
        for (int i = 0; i < 9; ++i) {
            final ItemStack stack = mc.thePlayer.inventory.mainInventory[i];
            if (stack != null && stack.getItem() instanceof ItemBlock && Utils.canBePlaced((ItemBlock) stack.getItem()) && stack.stackSize > 0) {
                totalBlocks += stack.stackSize;
            }
        }
        return totalBlocks;
    }

    private void placeBlock(int yOffset, int xOffset) {
        locateAndPlaceBlock(yOffset, xOffset);
        locateAndPlaceBlock(yOffset, xOffset);
    }

    private void locateAndPlaceBlock(int yOffset, int xOffset) {
        locateBlocks(yOffset, xOffset);
        if (blockInfo == null) {
            return;
        }
        place(blockInfo);
        blockInfo = null;
    }

    private void locateBlocks(int yOffset, int xOffset) {
        List<PlaceData> blocksInfo = findBlocks(yOffset, xOffset);

        if (blocksInfo == null) {
            return;
        }

        if (preciseScanConditions()) {
            BlockPos playerFeet = new BlockPos(mc.thePlayer.posX, mc.thePlayer.posY + 1, mc.thePlayer.posZ);
            blocksInfo.sort(Comparator.comparingDouble(b -> b.blockPos.distanceSq(playerFeet)));
        }

        double sumX = 0, sumY = !mc.thePlayer.onGround ? 0 : blocksInfo.get(0).blockPos.getY(), sumZ = 0;
        int index = 0;
        for (PlaceData blockssInfo : blocksInfo) {
            if (index > 1 || (!Utils.isDiagonal(false) && index > 0 && mc.thePlayer.onGround)) {
                break;
            }
            sumX += blockssInfo.blockPos.getX();
            if (!mc.thePlayer.onGround) {
                sumY += blockssInfo.blockPos.getY();
            }
            sumZ += blockssInfo.blockPos.getZ();
            index++;
        }

        double avgX = sumX / index;
        double avgY = !mc.thePlayer.onGround ? sumY / index : blocksInfo.get(0).blockPos.getY();
        double avgZ = sumZ / index;

        targetBlock = new Vec3(avgX, avgY, avgZ);

        PlaceData blockInfo2 = blocksInfo.get(0);
        int blockX = blockInfo2.blockPos.getX();
        int blockY = blockInfo2.blockPos.getY();
        int blockZ = blockInfo2.blockPos.getZ();
        EnumFacing blockFacing = blockInfo2.enumFacing;
        blockInfo = blockInfo2;

        double hitX = (blockX + 0.5D) + getCoord(blockFacing.getOpposite(), "x") * 0.5D;
        double hitY = (blockY + 0.5D) + getCoord(blockFacing.getOpposite(), "y") * 0.5D;
        double hitZ = (blockZ + 0.5D) + getCoord(blockFacing.getOpposite(), "z") * 0.5D;
        lookVec = new Vec3(0.5D + getCoord(blockFacing.getOpposite(), "x") * 0.5D, 0.5D + getCoord(blockFacing.getOpposite(), "y") * 0.5D, 0.5D + getCoord(blockFacing.getOpposite(), "z") * 0.5D);
        hitVec = new Vec3(hitX, hitY, hitZ);
        blockInfo.hitVec = hitVec;
    }

    private boolean preciseScanConditions() {
        return !Utils.isDiagonal(false) && !mc.thePlayer.onGround ||
                mc.thePlayer.onGround && !usingFastScaffold() &&
                        (
                                Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode()) ||
                                        !Keyboard.isKeyDown(mc.gameSettings.keyBindJump.getKeyCode())
                        );
    }

    private double getCoord(EnumFacing facing, String axis) {
        switch (axis) {
            case "x": return (facing == EnumFacing.WEST) ? -0.5 : (facing == EnumFacing.EAST) ? 0.5 : 0;
            case "y": return (facing == EnumFacing.DOWN) ? -0.5 : (facing == EnumFacing.UP) ? 0.5 : 0;
            case "z": return (facing == EnumFacing.NORTH) ? -0.5 : (facing == EnumFacing.SOUTH) ? 0.5 : 0;
        }
        return 0;
    }

    private List<PlaceData> findBlocks(int yOffset, int xOffset) {
        int x = (int) Math.floor(mc.thePlayer.posX + xOffset);
        int y = (int) Math.floor(((startYPos != -1) ? startYPos : mc.thePlayer.posY) + yOffset);
        int z = (int) Math.floor(mc.thePlayer.posZ);

        BlockPos base = new BlockPos(x, y - 1, z);

        if (!BlockUtils.replaceable(base)) {
            return null;
        }

        EnumFacing[] allFacings = getFacingsSorted();
        List<EnumFacing> validFacings = new ArrayList<>(5);
        for (EnumFacing facing : allFacings) {
            if (facing != EnumFacing.UP && placeConditions(facing, yOffset, xOffset)) {
                validFacings.add(facing);
            }
        }
        int maxYLayer = 2;
        List<PlaceData> possibleBlocks = new ArrayList<>();

        for (int dy = 1; dy <= maxYLayer; dy++) {
            BlockPos layerBase = new BlockPos(x, y - dy, z);
            if (dy == 1) {
                for (EnumFacing facing : validFacings) {
                    BlockPos neighbor = layerBase.offset(facing);
                    if (!BlockUtils.replaceable(neighbor) && !BlockUtils.isInteractable(BlockUtils.getBlock(neighbor))) {
                        possibleBlocks.add(new PlaceData(neighbor, facing.getOpposite()));
                    }
                }
            }
            for (EnumFacing facing : validFacings) {
                BlockPos adjacent = layerBase.offset(facing);
                if (BlockUtils.replaceable(adjacent)) {
                    for (EnumFacing nestedFacing : validFacings) {
                        BlockPos nestedNeighbor = adjacent.offset(nestedFacing);
                        if (!BlockUtils.replaceable(nestedNeighbor) && !BlockUtils.isInteractable(BlockUtils.getBlock(nestedNeighbor))) {
                            possibleBlocks.add(new PlaceData(nestedNeighbor, nestedFacing.getOpposite()));
                        }
                    }
                }
            }
            for (EnumFacing facing : validFacings) {
                BlockPos adjacent = layerBase.offset(facing);
                if (BlockUtils.replaceable(adjacent)) {
                    for (EnumFacing nestedFacing : validFacings) {
                        BlockPos nestedNeighbor = adjacent.offset(nestedFacing);
                        if (BlockUtils.replaceable(nestedNeighbor)) {
                            for (EnumFacing thirdFacing : validFacings) {
                                BlockPos thirdNeighbor = nestedNeighbor.offset(thirdFacing);
                                if (!BlockUtils.replaceable(thirdNeighbor) && !BlockUtils.isInteractable(BlockUtils.getBlock(thirdNeighbor))) {
                                    possibleBlocks.add(new PlaceData(thirdNeighbor, thirdFacing.getOpposite()));
                                }
                            }
                        }
                    }
                }
            }
        }

        return possibleBlocks.isEmpty() ? null : possibleBlocks;
    }

    private EnumFacing[] getFacingsSorted() {
        EnumFacing lastFacing = EnumFacing.getHorizontal(MathHelper.floor_double((((IAccessorEntityPlayerSP)mc.thePlayer).getLastReportedYaw() * 4.0F / 360.0F) + 0.5D) & 3);

        EnumFacing perpClockwise = lastFacing.rotateY();
        EnumFacing perpCounterClockwise = lastFacing.rotateYCCW();

        EnumFacing opposite = lastFacing.getOpposite();

        float yaw = ((IAccessorEntityPlayerSP)mc.thePlayer).getLastReportedYaw() % 360;
        if (yaw > 180) {
            yaw -= 360;
        }
        else if (yaw < -180) {
            yaw += 360;
        }

        // Calculates the difference from the last placed angle and gets the closest one
        float diffClockwise = Math.abs(MathHelper.wrapAngleTo180_float(yaw - getFacingAngle(perpClockwise)));
        float diffCounterClockwise = Math.abs(MathHelper.wrapAngleTo180_float(yaw - getFacingAngle(perpCounterClockwise)));

        EnumFacing firstPerp, secondPerp;
        if (diffClockwise <= diffCounterClockwise) {
            firstPerp = perpClockwise;
            secondPerp = perpCounterClockwise;
        }
        else {
            firstPerp = perpCounterClockwise;
            secondPerp = perpClockwise;
        }

        return new EnumFacing[]{EnumFacing.UP, EnumFacing.DOWN, lastFacing, firstPerp, secondPerp, opposite};
    }

    private float getFacingAngle(EnumFacing facing) {
        switch (facing) {
            case WEST:
                return 90;
            case NORTH:
                return 180;
            case EAST:
                return -90;
            default:
                return 0;
        }
    }

    private boolean placeConditions(EnumFacing enumFacing, int yCondition, int xCondition) {
        if (xCondition == -1) {
            return enumFacing == EnumFacing.EAST;
        }
        if (yCondition == 1) {
            return enumFacing == EnumFacing.DOWN;
        }

        return true;
    }

    float getMotionYaw() {
        return MathHelper.wrapAngleTo180_float((float) Math.toDegrees(Math.atan2(mc.thePlayer.motionZ, mc.thePlayer.motionX)) - 90.0F);
    }

    private int getSpeedLevel() {
        for (PotionEffect potionEffect : mc.thePlayer.getActivePotionEffects()) {
            if (potionEffect.getEffectName().equals("potion.moveSpeed")) {
                return potionEffect.getAmplifier() + 1;
            }
            return 0;
        }
        return 0;
    }

    double[] speedLevels = {0.48, 0.5, 0.52, 0.58, 0.68};

    double getSpeed(int speedLevel) {
        if (speedLevel >= 0) {
            return speedLevels[speedLevel];
        }
        return speedLevels[0];
    }

    double[] floatSpeedLevels = {0.2, 0.22, 0.28, 0.29, 0.3};

    double getFloatSpeed(int speedLevel) {
        double min = 0;
        double value = 0;
        double input = (motion.getInput() / 100);
        if (mc.thePlayer.moveStrafing != 0 && mc.thePlayer.moveForward != 0) min = 0.003;
        value = floatSpeedLevels[0] - min;
        if (speedLevel >= 0) {
            value = floatSpeedLevels[speedLevel] - min;
        }
        value *= input;
        return value;
    }

    private void handleMotion() {
        if (ModuleManager.tower.canTower() || !mc.thePlayer.onGround || motion.getInput() == 1) {
            return;
        }
        double input = (motion.getInput() / 100);
        mc.thePlayer.motionX *= input;
        mc.thePlayer.motionZ *= input;
    }

    public float hardcodedYaw() {
        float simpleYaw = 0F;
        float f = 0.8F;

        if (mc.thePlayer.moveForward >= f) {
            simpleYaw -= 180;
            if (mc.thePlayer.moveStrafing >= f) simpleYaw += 45;
            if (mc.thePlayer.moveStrafing <= -f) simpleYaw -= 45;
        }
        else if (mc.thePlayer.moveForward == 0) {
            simpleYaw -= 180;
            if (mc.thePlayer.moveStrafing >= f) simpleYaw += 90;
            if (mc.thePlayer.moveStrafing <= -f) simpleYaw -= 90;
        }
        else if (mc.thePlayer.moveForward <= -f) {
            if (mc.thePlayer.moveStrafing >= f) simpleYaw -= 45;
            if (mc.thePlayer.moveStrafing <= -f) simpleYaw += 45;
        }
        return simpleYaw;
    }

    public boolean holdingBlocks() {
        if (autoSwap.isToggled() && ModuleManager.autoSwap.spoofItem.isToggled() && lastSlot.get() != mc.thePlayer.inventory.currentItem && totalBlocks() > 0) {
            ((IMixinItemRenderer) mc.getItemRenderer()).setCancelUpdate(true);
            ((IMixinItemRenderer) mc.getItemRenderer()).setCancelReset(true);
        }
        ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (!autoSwap.isToggled() || getSlot() == -1) {
            if (heldItem == null || !(heldItem.getItem() instanceof ItemBlock) || !Utils.canBePlaced((ItemBlock) heldItem.getItem())) {
                return false;
            }
        }
        return true;
    }

    private double getMovementAngle() {
        double angle = Math.toDegrees(Math.atan2(-mc.thePlayer.moveStrafing, mc.thePlayer.moveForward));
        return angle == -0 ? 0 : angle;
    }

    private int getSlot() {
        int slot = -1;
        int highestStack = -1;
        ItemStack heldItem = mc.thePlayer.getHeldItem();
        for (int i = 0; i < 9; ++i) {
            final ItemStack itemStack = mc.thePlayer.inventory.mainInventory[i];
            if (itemStack != null && itemStack.getItem() instanceof ItemBlock && Utils.canBePlaced((ItemBlock) itemStack.getItem()) && itemStack.stackSize > 0) {
                if (Utils.getBedwarsStatus() == 2 && ((ItemBlock) itemStack.getItem()).getBlock() instanceof BlockTNT) {
                    continue;
                }
                if (itemStack != null && heldItem != null && (heldItem.getItem() instanceof ItemBlock) && Utils.canBePlaced((ItemBlock) heldItem.getItem()) && ModuleManager.autoSwap.sameType.isToggled() && !(itemStack.getItem().getClass().equals(heldItem.getItem().getClass()))) {
                    continue;
                }
                if (itemStack.stackSize > highestStack) {
                    highestStack = itemStack.stackSize;
                    slot = i;
                }
            }
        }
        return slot;
    }

    public boolean setSlot() {
        int slot = getSlot();
        if (slot == -1) {
            return false;
        }
        if (blockSlot == -1) {
            blockSlot = slot;
        }
        if (lastSlot.get() == -1) {
            lastSlot.set(mc.thePlayer.inventory.currentItem);
        }
        if (autoSwap.isToggled() && blockSlot != -1) {
            if (ModuleManager.autoSwap.swapToGreaterStack.isToggled()) {
                mc.thePlayer.inventory.currentItem = slot;
            }
            else {
                mc.thePlayer.inventory.currentItem = blockSlot;
            }
        }

        ItemStack heldItem = mc.thePlayer.getHeldItem();
        if (heldItem == null || !(heldItem.getItem() instanceof ItemBlock) || !Utils.canBePlaced((ItemBlock) heldItem.getItem())) {
            blockSlot = -1;
            return false;
        }
        return true;
    }


    static class PlaceData {
        EnumFacing enumFacing;
        BlockPos blockPos;
        Vec3 hitVec;

        PlaceData(BlockPos blockPos, EnumFacing enumFacing) {
            this.enumFacing = enumFacing;
            this.blockPos = blockPos;
        }
    }
}