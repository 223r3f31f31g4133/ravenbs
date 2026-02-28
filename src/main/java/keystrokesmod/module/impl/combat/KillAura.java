package keystrokesmod.module.impl.combat;

import keystrokesmod.Raven;
import keystrokesmod.event.*;
import keystrokesmod.lag.api.EnumLagDirection;
import keystrokesmod.lag.api.LagRequest;
import keystrokesmod.lag.timeout.ModuleBackedTimeout;
import keystrokesmod.mixin.impl.accessor.IAccessorMinecraft;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.minigames.SkyWars;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.ModuleUtils;
import keystrokesmod.utility.ReflectionUtils;
import keystrokesmod.utility.RotationUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityArmorStand;
import net.minecraft.entity.monster.EntityGiantZombie;
import net.minecraft.entity.monster.EntityIronGolem;
import net.minecraft.entity.monster.EntityPigZombie;
import net.minecraft.entity.monster.EntitySilverfish;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.util.*;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingSetAttackTargetEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.input.Mouse;

import java.util.*;

import static net.minecraft.util.EnumFacing.DOWN;


public class KillAura extends Module {
    private SliderSetting aps;
    public SliderSetting autoBlockMode;
    private SliderSetting fov;
    private SliderSetting attackRange;
    private SliderSetting swingRange;
    private SliderSetting blockRange;
    public SliderSetting rotationMode;
    private SliderSetting rotationSmoothing;
    private SliderSetting sortMode;
    private SliderSetting switchDelay;
    private SliderSetting targets;
    private ButtonSetting attackMobs;
    private ButtonSetting targetInvis;
    private ButtonSetting disableInInventory;
    private ButtonSetting disableWhileBlocking;
    private ButtonSetting disableWhileMining;
    private ButtonSetting hitThroughBlocks;
    private ButtonSetting ignoreTeammates;
    public ButtonSetting manualBlock;
    private ButtonSetting prioritizeEnemies;
    private ButtonSetting requireMouseDown;
    private ButtonSetting silentSwing;
    private ButtonSetting weaponOnly;

    private String[] autoBlockModes = new String[] { "Manual", "Vanilla", "Fake", "Partial", "Hypixel", "Blink" };
    private String[] rotationModes = new String[] { "Silent", "Lock view", "None" };
    private String[] sortModes = new String[] { "Distance", "Health", "Hurttime", "Yaw" };

    // autoblock related
    private String[] swapBlacklist = { "compass", "snowball", "spawn", "skull" };

    // target variables
    public static EntityLivingBase target;
    public static EntityLivingBase attackingEntity;
    private HashMap<Integer, Integer> hitMap = new HashMap<>(); // entity id, ticks existed client
    private List<Entity> hostileMobs = new ArrayList<>();
    private Map<Integer, Boolean> golems = new HashMap<>(); // entity id, is teammate

    // blocking related
    public boolean blockingClient;
    public boolean blockingServer;
    private int interactTicks;
    private boolean wasUsing;
    public boolean blocked;
    public boolean hasAB;
    private int unblockTicks;

    // blink related
    private LagRequest outboundLag;
    private boolean lag;
    private boolean swapped;

    // other
    private long lastTime = 0L;
    private long delay;
    private boolean shouldAttack, shouldAttackAB;
    private int previousAutoBlockMode;
    private boolean reset;
    private boolean rotated;
    private boolean sendUnBlock;
    private int delayTicks = 0;
    private boolean lastPressedLeft;
    private boolean lastPressedRight;
    public boolean stoppedTargeting; // can only be true for 1 tick
    private int didBlock;

    public KillAura() {
        super("KillAura", category.combat);
        this.registerSetting(aps = new SliderSetting("APS", 16.0, 1.0, 20.0, 0.5));
        this.registerSetting(autoBlockMode = new SliderSetting("Autoblock", 0, autoBlockModes));
        this.registerSetting(fov = new SliderSetting("FOV", "°", 360.0, 30.0, 360.0, 4.0));
        this.registerSetting(attackRange = new SliderSetting("Range (attack)", 3.0, 3.0, 6.0, 0.05));
        this.registerSetting(swingRange = new SliderSetting("Range (swing)", 3.3, 3.0, 8.0, 0.05));
        this.registerSetting(blockRange = new SliderSetting("Range (block)", 6.0, 3.0, 12.0, 0.05));
        this.registerSetting(rotationMode = new SliderSetting("Rotation mode", 0, rotationModes));
        this.registerSetting(rotationSmoothing = new SliderSetting("Rotation smoothing", 0, 0, 10, 1));
        this.registerSetting(sortMode = new SliderSetting("Sort mode", 0, sortModes));
        this.registerSetting(switchDelay = new SliderSetting("Switch delay", "ms", 200.0, 50.0, 1000.0, 25.0));
        this.registerSetting(targets = new SliderSetting("Targets", 3.0, 1.0, 10.0, 1.0));
        this.registerSetting(targetInvis = new ButtonSetting("Target invis", true));
        this.registerSetting(attackMobs = new ButtonSetting("Attack mobs", false));
        this.registerSetting(disableInInventory = new ButtonSetting("Disable in inventory", true));
        this.registerSetting(disableWhileBlocking = new ButtonSetting("Disable while blocking", false));
        this.registerSetting(disableWhileMining = new ButtonSetting("Disable while mining", false));
        this.registerSetting(hitThroughBlocks = new ButtonSetting("Hit through blocks", true));
        this.registerSetting(ignoreTeammates = new ButtonSetting("Ignore teammates", true));
        this.registerSetting(manualBlock = new ButtonSetting("Manual block", false)); // does absolutely nothing
        this.registerSetting(prioritizeEnemies = new ButtonSetting("Prioritize enemies", false));
        this.registerSetting(requireMouseDown = new ButtonSetting("Require mouse down", false));
        this.registerSetting(silentSwing = new ButtonSetting("Silent swing while blocking", false));
        this.registerSetting(weaponOnly = new ButtonSetting("Weapon only", false));
    }

    @Override
    public String getInfo() {
        if (rotationMode.getInput() == 2) { // None, return FOV if rotation mode is none
            return (int) this.fov.getInput() + fov.getSuffix();
        }
        return rotationModes[(int) rotationMode.getInput()];
    }

    @Override
    public void onEnable() {
        delayTicks = 1;
    }

    @Override
    public void onDisable() {
        handleBlocking(false);
        hitMap.clear();
        resetBlinkState(false);
        interactTicks = 0;
        setTarget(null);
        if (rotated || reset) {
            resetYaw();
        }
        rotated = false;
        delayTicks = 0;
        unblockTicks = 0;
        blocked = false;
    }

    @SubscribeEvent(priority = EventPriority.NORMAL)
    public void onPreUpdate(PreUpdateEvent e) {
        wasUsing = mc.gameSettings.keyBindUseItem.isKeyDown();

        if (didBlock > 0) {
            didBlock--;
            if (target != null) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                return;
            }
        }

        if (autoBlock() && (target == null || manualBlock.isToggled() && !manualBlock())) {
            resetBlinkState(true);
        }

        if (sendUnBlock) {
            if (autoBlockMode.getInput() == 4) {
                if (Utils.holdingSword()) {
                    setSlot();
                    sendDigPacket();
                    ReflectionUtils.setItemInUse(blockingClient = false);
                    sendUnBlock = false;
                    blocked = false;
                }
                else {
                    ReflectionUtils.setItemInUse(blockingClient = false);
                    sendUnBlock = false;
                    blocked = false;
                }
            }
            else {
                sendDigPacket();
                ReflectionUtils.setItemInUse(blockingClient = false);
                sendUnBlock = false;
                blocked = false;
            }
            return;
        }

        if (mc.currentScreen == null || mc.currentScreen.allowUserInput) {
            boolean pressedLeft = Mouse.isButtonDown(0);
            if (pressedLeft && !lastPressedLeft) {
                onCustomMouse(0, true);
            }
            if (!pressedLeft && lastPressedLeft) {
                onCustomMouse(0, false);
            }
            if (target == null) {
                boolean pressedRight = Mouse.isButtonDown(1);
                if (pressedRight && !lastPressedRight) {
                    onCustomMouse(1, true);
                }
                if (!pressedRight && lastPressedRight) {
                    onCustomMouse(1, false);
                }
                lastPressedRight = pressedRight;
            }
            lastPressedLeft = pressedLeft;
        }

        delayTicks--;
        if (delayTicks >= 0) {
            return;
        }
        if (reset) {
            resetYaw();
            reset = false;
        }
        if (!basicCondition() || !settingCondition()) {
            setTarget(null);
            return;
        }
        if (target == null) {
            handleBlocking(false);
            return;
        }
        double distanceToBB = getDistanceToBoundingBox(target);
        boolean inBlockRange = distanceToBB <= blockRange.getInput();
        if (!(autoBlock() && Utils.holdingSword()) || (!manualBlock() && manualBlock.isToggled())) { // regular swing & attack if autoblock isnt overriding or isnt in autoblock range
            handleSwingAndAttack(distanceToBB, false);
        }
        else if ((autoBlock() && Utils.holdingSword()) && manualBlock() && inBlockRange) {
            handleAutoBlock(distanceToBB);
        }
        if (inBlockRange) {
            if (manualBlock()) {
                handleBlocking(true);
            }
            if (manualBlock.isToggled()) {
                handleBlocking(manualBlock());
            }
        }
        if (mc.currentScreen == null || mc.currentScreen.allowUserInput) {
            boolean pressedRight = Mouse.isButtonDown(1);
            if (pressedRight && !lastPressedRight) {
                onCustomMouse(1, true);
            }
            if (!pressedRight && lastPressedRight) {
                onCustomMouse(1, false);
            }
            lastPressedRight = pressedRight;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onClientRotation(ClientRotationEvent e) {
        if (delayTicks >= 0) {
            if (rotated) {
                resetYaw(e);
            }
            return;
        }
        if (!basicCondition() || !settingCondition()) {
            setTarget(null);
            if (rotated) {
                resetYaw(e);
            }
            return;
        }
        if (target == null) {
            //if (Mouse.isButtonDown(0)) Utils.sendMessage("1 " + mc.thePlayer.ticksExisted);
            if (mc.thePlayer.isBlocking()) didBlock = 2;
        }
        handleTarget();
        if (target == null) {
            if (rotated) {
                resetYaw(e);
            }
            return;
        }
        if (rotationMode.getInput() != 2) {
            if (inRange(target, attackRange.getInput() - 0.005)) {
                float[] rotations = RotationUtils.getRotations(target, RotationUtils.prevRenderYaw, RotationUtils.prevRenderPitch);
                if (rotations == null) {
                    if (rotated) {
                        resetYaw(e);
                    }
                    return;
                }

                float[] smoothedRotations = getRotationsSmoothed(rotations);
                if (rotationMode.getInput() == 0) { // silent
                    e.yaw = smoothedRotations[0];
                    e.pitch = smoothedRotations[1];
                    rotated = true;
                }
                else {
                    mc.thePlayer.rotationYaw = smoothedRotations[0];
                    mc.thePlayer.rotationPitch = smoothedRotations[1];
                }
            }
            else if (rotationMode.getInput() == 0) {
                if (rotated) {
                    reset = true;
                    e.yaw = RotationUtils.serverRotations[0];
                    e.pitch = RotationUtils.serverRotations[1];
                    rotated = false;
                }
            }
        }
    }

    @Override
    public void onUpdate() {
        if (rotationMode.getInput() == 1 && target != null) {
            if (inRange(target, attackRange.getInput() - 0.005)) {
                float[] rotations = RotationUtils.getRotations(target, mc.thePlayer.rotationYaw, mc.thePlayer.rotationPitch);
                if (rotations == null) {
                    return;
                }
                float[] smoothedRotations = getRotationsSmoothed(rotations);
                mc.thePlayer.rotationYaw = smoothedRotations[0];
                mc.thePlayer.rotationPitch = smoothedRotations[1];
            }
        }
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!Utils.nullCheck()) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            if (System.currentTimeMillis() - this.lastTime >= delay && target != null) {
                this.lastTime = System.currentTimeMillis();
                updateAttackDelay();
                if (target != null) {
                    shouldAttack = true;
                    shouldAttackAB = true;
                }
                if (rotationMode.getInput() == 0) {
                    mc.thePlayer.renderArmYaw = mc.thePlayer.rotationYaw;
                    mc.thePlayer.prevRenderArmYaw = mc.thePlayer.rotationYaw;
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onSendPacket(SendPacketEvent e) {
        if (!Utils.nullCheck()) {
            return;
        }
        Packet<?> packet = e.getPacket();
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            C08PacketPlayerBlockPlacement p = (C08PacketPlayerBlockPlacement) e.getPacket();
            if (delayTicks >= 0) {
                if (p.getStack() != null && p.getStack().getItem() instanceof ItemSword && p.getPlacedBlockDirection() != 255) {
                    e.setCanceled(true);
                }
            }
        } else if (packet instanceof C07PacketPlayerDigging) {
            sendUnBlock = false;
        }
    }

    @SubscribeEvent
    public void onMouse(MouseEvent e) {
        if (e.button == 0 || e.button == 1) {
            if (!Utils.holdingWeapon() || target == null) {
                return;
            }
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onScrollSlot(PreSlotScrollEvent e) {
        int slot = e.slot;
        slot = Integer.compare(slot, 0);
        slot = Math.floorMod(mc.thePlayer.inventory.currentItem - slot, 9);
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(slot);
        if (stack != null && stack.getItem() instanceof ItemSword && wasUsing && Utils.lookingAtBlock()) {
            onSwapSlot();
            if (Raven.DEBUG) {
                Utils.sendModuleMessage(this, "&7Scroll swap detected, setting delay to &b" + delayTicks + "&7. (&d" + mc.thePlayer.ticksExisted + "&7)");
            }
        }
    }

    @SubscribeEvent
    public void onSlotUpdate(SlotUpdateEvent e) {
        ItemStack stack = mc.thePlayer.inventory.getStackInSlot(e.slot);
        if (stack != null && stack.getItem() instanceof ItemSword && wasUsing && Utils.lookingAtBlock()) {
            onSwapSlot();
            if (Raven.DEBUG) {
                Utils.sendModuleMessage(this, "&7Swap detected, setting delay to &b" + delayTicks + "&7. (&d" + mc.thePlayer.ticksExisted + "&7)");
            }
        }
    }

    @SubscribeEvent
    public void onSetAttackTarget(LivingSetAttackTargetEvent e) {
        if (e.entity != null && !hostileMobs.contains(e.entity)) {
            if (!(e.target instanceof EntityPlayer) || !e.target.getName().equals(mc.thePlayer.getName())) {
                return;
            }
            if (Utils.getBedwarsStatus() == 2 && e.entity instanceof EntityPigZombie) {
                return;
            }
            hostileMobs.add(e.entity);
        }
        if (e.target == null && hostileMobs.contains(e.entity)) {
            hostileMobs.remove(e.entity);
            if (Raven.DEBUG) {
                Utils.sendModuleMessage(this, "&7mob stopped attack player");
            }
        }
    }

    public void onSwapSlot() {
        delayTicks = 1;
        if (autoBlockMode.getInput() > 0 && !manualBlock()) {
            KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
        }
    }

    public void onCustomMouse(int button, boolean state) {
        if (autoBlock()) {
            return;
        }
        if (button == 1) {
            if (state) {
                if (target != null) {
                    if (autoBlock()) {
                        return;
                    }
                    if (basicCondition() && settingCondition()) {
                        if (!ModuleManager.bedAura.rotate) {
                            if (isLookingAtEntity()) {
                                if (!mc.thePlayer.isBlocking() || !disableWhileBlocking.isToggled()) {
                                    interactAt(true);
                                }
                            }
                        }
                    }
                    ReflectionUtils.setItemInUse(blockingClient = true);
                    sendBlockPacket();
                    // cancel
                }
                else {
                    delayTicks = 1;
                }
            }
            else {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindUseItem.getKeyCode(), false);
                if (!autoBlock()) {
                    ReflectionUtils.setItemInUse(blockingClient = false);
                    sendUnBlock = true;
                }
            }
        }
        else if (button == 0) {
            if (!state) {
                delayTicks = 1;
            }
            if (autoBlock()) {
                return;
            }
            if (mc.currentScreen == null && state && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && !Mouse.isButtonDown(1)) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), true);
                KeyBinding.onTick(mc.gameSettings.keyBindAttack.getKeyCode());
            }
            else if (!state) {
                KeyBinding.setKeyBindState(mc.gameSettings.keyBindAttack.getKeyCode(), false);
            }
        }
    }

    @SubscribeEvent
    public void onWorldJoin(EntityJoinWorldEvent e) {
        if (e.entity == mc.thePlayer) {
            hitMap.clear();
            hostileMobs.clear();
            golems.clear();
        }
    }

    private void setTarget(Entity entity) {
        if (!(entity instanceof EntityLivingBase)) {
            if (blockingClient) {
                ReflectionUtils.setItemInUse(blockingClient = false);
                sendUnBlock = true;
            }
            resetBlinkState(true);
            handleBlocking(false);
            if (target != null) {
                stoppedTargeting = true;
            }
            target = null;
            attackingEntity = null;
        }
        else {
            target = (EntityLivingBase) entity;
        }
    }

    private void handleTarget() {
        // Narrow down the targets available
        List<EntityLivingBase> availableTargets = new ArrayList<>();
        double maxRange = getMaxRange();
        for (Entity entity : mc.theWorld.loadedEntityList) {
            if (entity == null || entity == mc.thePlayer || entity.isDead) {
                continue;
            }
            if (entity instanceof EntityPlayer) {
                if (Utils.isFriended((EntityPlayer) entity)) {
                    continue;
                }
                if (((EntityPlayer) entity).deathTime != 0) {
                    continue;
                }
                if (AntiBot.isBot(entity) || (Utils.isTeammate(entity) && ignoreTeammates.isToggled())) {
                    continue;
                }
            }
            else if (entity instanceof EntityCreature && attackMobs.isToggled()) {
                if (((EntityCreature) entity).tasks == null || ((EntityCreature) entity).isAIDisabled() || ((EntityCreature) entity).deathTime != 0) { // no ai
                    continue;
                }
                if (!entity.getClass().getCanonicalName().startsWith("net.minecraft.entity.monster.")) {
                    continue;
                }
            }
            else {
                continue;
            }
            if (entity.isInvisible() && !targetInvis.isToggled()) {
                continue;
            }
            float fovInput = (float) fov.getInput();
            if (fovInput != 360.0f && !Utils.inFov(fovInput, entity)) {
                continue;
            }
            if (mc.thePlayer.getDistanceToEntity(entity) < maxRange + maxRange / 3) { // simple distance check
                availableTargets.add((EntityLivingBase) entity);
            }
        }
        // Init as a new class and adding to list
        List<KillAuraTarget> toClassTargets = new ArrayList<>();
        for (EntityLivingBase target : availableTargets) {
            double distanceRayCasted = getDistanceToBoundingBox(target);
            if (distanceRayCasted > maxRange) {
                continue;
            }
            if (!(target instanceof EntityPlayer) && attackMobs.isToggled() && !isHostile((EntityCreature) target)) {
                continue;
            }
            if (!hitThroughBlocks.isToggled() && (!Utils.canPlayerBeSeen(target) || !inRange(target, attackRange.getInput() - 0.005))) {
                continue;
            }
            toClassTargets.add(new KillAuraTarget(distanceRayCasted, target.getHealth(), target.hurtTime, RotationUtils.distanceFromYaw(target, false), target.getEntityId(), target instanceof EntityPlayer && Utils.isEnemy((EntityPlayer) target)));
        }
        // Sorting targets
        Comparator<KillAuraTarget> comparator = null;
        switch ((int) sortMode.getInput()) {
            case 0:
                comparator = Comparator.comparingDouble(entity -> entity.distance);
                break;
            case 1:
                comparator = Comparator.comparingDouble(entityPlayer -> (double)entityPlayer.health);
                break;
            case 2:
                comparator = Comparator.comparingDouble(entityPlayer2 -> (double)entityPlayer2.hurttime);
                break;
            case 3:
                comparator = Comparator.comparingDouble(entity2 -> entity2.yawDelta);
                break;
        }
        if (prioritizeEnemies.isToggled()) {
            List<KillAuraTarget> enemies = new ArrayList<>();
            for (KillAuraTarget entity : toClassTargets) {
                if (entity.isEnemy) {
                    enemies.add(entity);
                }
            }
            if (!enemies.isEmpty()) {
                toClassTargets = new ArrayList<>(enemies);
            }
        }
        if (sortMode.getInput() != 0) {
            toClassTargets.sort(Comparator.comparingDouble(entity -> entity.distance));
        }
        toClassTargets.sort(comparator); // then sort by selected sorting mode

        List<KillAuraTarget> attackTargets = new ArrayList<>();
        for (KillAuraTarget killAuraTarget : toClassTargets) {
            if (killAuraTarget.distance <= attackRange.getInput() - 0.005) {
                attackTargets.add(killAuraTarget);
            }
        }

        if (!attackTargets.isEmpty()) {
            // Switch aura
            int ticksExisted = mc.thePlayer.ticksExisted;
            int switchDelayTicks = (int) (switchDelay.getInput() / 50);
            long noHitTicks = (long) Math.min(attackTargets.size(), targets.getInput()) * switchDelayTicks;
            for (KillAuraTarget auraTarget : attackTargets) {
                Integer firstHit = hitMap.get(auraTarget.entityId);
                if (firstHit == null || ticksExisted - firstHit >= switchDelayTicks) {
                    continue;
                }
                if (auraTarget.distance < attackRange.getInput() - 0.005) {
                    setTarget(mc.theWorld.getEntityByID(auraTarget.entityId));
                    return;
                }
            }

            for (KillAuraTarget auraTarget : attackTargets) {
                Integer firstHit = hitMap.get(auraTarget.entityId);
                if (firstHit == null || ticksExisted >= firstHit + noHitTicks) {
                    hitMap.put(auraTarget.entityId, ticksExisted);
                    setTarget(mc.theWorld.getEntityByID(auraTarget.entityId));
                    return;
                }
            }
        }
        else if (!toClassTargets.isEmpty()) {
            KillAuraTarget killAuraTarget = toClassTargets.get(0);
            setTarget(mc.theWorld.getEntityByID(killAuraTarget.entityId));
        }
        else {
            setTarget(null);
        }
    }

    private void handleSwingAndAttack(double distance, boolean swung) {
        boolean inAttackDistance = inRange(target, attackRange.getInput() - 0.005);
        if (((distance <= swingRange.getInput() || inAttackDistance) && shouldAttack || distance <= blockRange.getInput() && Mouse.isButtonDown(0)) && !swung) { // swing if in swing range or needs to attack
            if (!mc.thePlayer.isBlocking() || !disableWhileBlocking.isToggled()) {
                swingItem();
            }
        }
        if (inAttackDistance) {
            attackingEntity = target;
            if (shouldAttack) {
                shouldAttack = false;
                if (ModuleManager.bedAura.rotate) {
                    return;
                }
                if (!isLookingAtEntity()) {
                    return;
                }
                if (!mc.thePlayer.isBlocking() || !disableWhileBlocking.isToggled()) {
                    mc.playerController.attackEntity(mc.thePlayer, target);
                }
            }
        }
        else {
            attackingEntity = null;
        }
    }

    private boolean isHostile(EntityCreature entityCreature) {
        if (SkyWars.onlyAuraHostiles()) {
            if (entityCreature instanceof EntityGiantZombie) {
                return false;
            }
            return !ModuleManager.skyWars.spawnedMobs.contains(entityCreature.getEntityId());
        }
        else if (entityCreature instanceof EntitySilverfish) {
            String teamColor = Utils.getFirstColorCode(entityCreature.getCustomNameTag());
            String teamColorSelf = Utils.getFirstColorCode(mc.thePlayer.getDisplayName().getFormattedText());
            // same team
            return teamColor.isEmpty() || (!teamColorSelf.equals(teamColor) && !Utils.isTeammate(entityCreature));
        }
        else if (entityCreature instanceof EntityIronGolem) {
            if (Utils.getBedwarsStatus() != 2) {
                return true;
            }
            if (!golems.containsKey(entityCreature.getEntityId())) {
                double nearestDistance = -1;
                EntityArmorStand nearestArmorStand = null;
                for (Entity entity : mc.theWorld.loadedEntityList) {
                    if (!(entity instanceof EntityArmorStand)) {
                        continue;
                    }
                    String stripped = Utils.stripString(entity.getDisplayName().getFormattedText());
                    if (stripped.contains("[") && stripped.endsWith("]")) {
                        double distanceSq = entity.getDistanceSq(entityCreature.posX, entityCreature.posY, entityCreature.posZ);
                        if (distanceSq < nearestDistance || nearestDistance == -1) {
                            nearestDistance = distanceSq;
                            nearestArmorStand = (EntityArmorStand) entity;
                        }
                    }
                }
                if (nearestArmorStand != null) {
                    String teamColor = Utils.getFirstColorCode(nearestArmorStand.getDisplayName().getFormattedText());
                    String teamColorSelf = Utils.getFirstColorCode(mc.thePlayer.getDisplayName().getFormattedText());
                    boolean isTeam = !teamColor.isEmpty() && (teamColorSelf.equals(teamColor) || Utils.isTeammate(nearestArmorStand));
                    // same team
                    golems.put(entityCreature.getEntityId(), isTeam);
                    return !isTeam;
                }
                return !ModuleManager.bedwars.spawnedMobs.contains(entityCreature.getEntityId());
            }
            else {
                return !golems.getOrDefault(entityCreature.getEntityId(), false);
            }
        }
        else if (entityCreature instanceof EntityPigZombie && Utils.getBedwarsStatus() != 2) {
            return false;
        }
        return hostileMobs.contains(entityCreature);
    }

    private void handleBlocking(boolean blockState) {
        if (!Utils.holdingSword()) {
            if (blockingClient) {
                ReflectionUtils.setItemInUse(blockingClient = false);
            }
            return;
        }
        if (this.blockingClient == blockState) {
            return;
        }
        if (autoBlockMode.getInput() != previousAutoBlockMode) {
            if (previousAutoBlockMode >= 3) { // if == interact
                resetBlinkState(true);
            }
        }
        int keyCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        switch ((int) autoBlockMode.getInput()) {
            case 0: // manual, do nothing
                break;
            case 1: // vanilla
                setKeyBindState(keyCode, blockState);
                this.blockingClient = blockState;
                break;
            case 2: // fake
            case 3: // partial
            case 4: // interact a
            case 5: // interact b
            case 6: // interact c
            case 7: // interact d
                ReflectionUtils.setItemInUse(this.blockingClient = blockState);
                break;
        }
        previousAutoBlockMode = (int) autoBlockMode.getInput();
    }

    private void rightClick(boolean state) {
        int keyCode = mc.gameSettings.keyBindUseItem.getKeyCode();
        KeyBinding.setKeyBindState(keyCode, state);
        if (state) {
            KeyBinding.onTick(keyCode);
        }
        ReflectionUtils.setButton(1, state);
    }

    private double getMaxRange() {
        return Math.max(Math.max(swingRange.getInput(), attackRange.getInput() - 0.005), blockRange.getInput());
    }

    public boolean autoBlock() {
        return autoBlockMode.getInput() >= 3;
    }

    private float unwrapYaw(float yaw, float prevYaw) {
        return prevYaw + ((((yaw - prevYaw + 180f) % 360f) + 360f) % 360f - 180f);
    }

    private boolean isLookingAtEntity() { //
        if (rotationMode.getInput() == 0 && rotationSmoothing.getInput() > 0) { // silent
            return RotationUtils.isPossibleToHit(attackingEntity, attackRange.getInput() - 0.005, RotationUtils.serverRotations);
        }
        return true;
    }

    private void handleAutoBlock(double distance) {
        boolean inAttackDistance = inRange(target, attackRange.getInput() - 0.005);
        if (inAttackDistance) {
            attackingEntity = target;
        }
        boolean swung = false;
        hasAB = true;
        interactTicks++;
        switch ((int) autoBlockMode.getInput()) {
            case 4: // hypixel
                if (interactTicks == 1) {
                    lag = false;
                    lagPackets();
                    if (ModuleUtils.swapTicks == 0) {
                        swapSlot();
                    }
                    sendDigPacket();
                }
                else if (interactTicks >= 2) {
                    if (shouldAttackAB || aps.getInput() >= 10) {
                        setSlot();
                        handleInteractAndAttack(distance, swung);
                        sendBlockPacket();
                        shouldAttackAB = false;
                        lag = true;
                        interactTicks = 0;
                        releasePackets();
                    }
                }
                break;
            case 5: // blink
                if (interactTicks == 1) {
                    lag = false;
                    lagPackets();
                    if (mc.thePlayer.isBlocking() || blocked) {
                        if (ModuleUtils.swapTicks == 0) {
                            sendDigPacket();
                        }
                    }
                }
                else if (interactTicks >= 2) {
                    if (shouldAttackAB || aps.getInput() >= 10) {
                        handleInteractAndAttack(distance, swung);
                        sendBlockPacket();
                        shouldAttackAB = false;
                        lag = true;
                        interactTicks = 0;
                        releasePackets();
                    }
                }
                break;
        }
    }

    private void resetYaw(ClientRotationEvent event) {
        reset = true;
        event.yaw = RotationUtils.serverRotations[0];
        event.pitch = RotationUtils.serverRotations[1];
        rotated = false;
    }

    private boolean basicCondition() {
        if (!Utils.nullCheck()) {
            return false;
        }
        return !mc.thePlayer.isDead;
    }

    private int cSlot = 0;
    private void swapSlot() {
        mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(getBestSwapSlot()));
        Raven.packetsHandler.playerSlot.set(getBestSwapSlot());
        cSlot = getBestSwapSlot();
        swapped = true;
        blocked = false;
    }

    private void setSlot() {
        if (!swapped) {
            return;
        }
        mc.thePlayer.sendQueue.addToSendQueue(new C09PacketHeldItemChange(mc.thePlayer.inventory.currentItem));
        Raven.packetsHandler.playerSlot.set(mc.thePlayer.inventory.currentItem);
        cSlot = mc.thePlayer.inventory.currentItem;
        swapped = false;
    }

    private boolean settingCondition() {
        if (requireMouseDown.isToggled() && !Mouse.isButtonDown(0)) {
            return false;
        }
        else if (weaponOnly.isToggled() && !Utils.holdingWeapon()) {
            return false;
        }
        else if (disableWhileMining.isToggled() && isMining()) {
            return false;
        }
        else if (disableInInventory.isToggled() && mc.currentScreen != null) {
            return false;
        }
        else return ModuleManager.bedAura == null || !ModuleManager.bedAura.isEnabled() || ModuleManager.bedAura.allowAura.isToggled() || ModuleManager.bedAura.currentBlock == null;
    }

    private boolean isMining() {
        return mc.gameSettings.keyBindAttack.isKeyDown() && mc.objectMouseOver != null && mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK && mc.objectMouseOver.getBlockPos() != null;
    }

    private void sendBlockPacket() {
        mc.getNetHandler().addToSendQueue(new C08PacketPlayerBlockPlacement(mc.thePlayer.getHeldItem()));
        blocked = true;
    }


    private void setKeyBindState(int keycode, boolean state) {
        KeyBinding.setKeyBindState(keycode, state);
    }

    private void updateAttackDelay() {
        delay = (long)(1000.0 / aps.getInput() + Utils.randomizeInt(-1, 1));
    }

    private void swingItem() {
        if (silentSwing.isToggled() && mc.thePlayer.isBlocking()) {
            mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
        }
        else {
            mc.thePlayer.swingItem();
        }
    }

    public static double getDistanceToBoundingBox(Entity target) {
        if (mc.thePlayer == null) {
            return 0;
        }
        Vec3 playerEyePos = mc.thePlayer.getPositionEyes(((IAccessorMinecraft) mc).getTimer().renderPartialTicks);
        AxisAlignedBB boundingBox = target.getEntityBoundingBox();
        double nearestX = MathHelper.clamp_double(playerEyePos.xCoord, boundingBox.minX, boundingBox.maxX);
        double nearestY = MathHelper.clamp_double(playerEyePos.yCoord, boundingBox.minY, boundingBox.maxY);
        double nearestZ = MathHelper.clamp_double(playerEyePos.zCoord, boundingBox.minZ, boundingBox.maxZ);
        Vec3 nearestPoint = new Vec3(nearestX, nearestY, nearestZ);
        return playerEyePos.distanceTo(nearestPoint);
    }

    private int getBestSwapSlot() {
        int currentSlot = mc.thePlayer.inventory.currentItem;
        int bestSlot = -1;
        double bestDamage = -1;
        for (int i = 0; i < 9; ++i) {
            if (i == currentSlot) {
                continue;
            }
            ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
            double damage = Utils.getDamageLevel(stack);
            if (damage != 0) {
                if (damage > bestDamage) {
                    bestDamage = damage;
                    bestSlot = i;
                }
            }
        }
        if (bestSlot == -1) {
            for (int i = 0; i < 9; ++i) {
                if (i == currentSlot) {
                    continue;
                }
                ItemStack stack = mc.thePlayer.inventory.getStackInSlot(i);
                if (stack == null || Arrays.stream(swapBlacklist).noneMatch(stack.getUnlocalizedName().toLowerCase()::contains)) {
                    bestSlot = i;
                    break;
                }
            }
        }
        if (bestSlot == currentSlot || bestSlot == cSlot) {
            bestSlot -= 1;
        }
        if (bestSlot == -1) {
            bestSlot += 2;
        }

        return bestSlot;
    }

    public void resetYaw() {
        float serverYaw = RotationUtils.serverRotations[0];
        float unwrappedYaw = unwrapYaw(MathHelper.wrapAngleTo180_float(mc.thePlayer.rotationYaw), serverYaw);
        mc.thePlayer.rotationYaw = unwrappedYaw;
        mc.thePlayer.prevRotationYaw = unwrappedYaw;
    }

    private void interactAt(boolean requireInteractAt) {
        if (attackingEntity == null) {
            return;
        }
        if (ModuleManager.bedAura.rotate) {
            return;
        }
        boolean sent = false;
        boolean canHit = RotationUtils.isPossibleToHit(attackingEntity, attackRange.getInput() - 0.005, RotationUtils.serverRotations);
        if (!canHit) {
            return;
        }
        MovingObjectPosition mov = RotationUtils.rayTrace(10, ((IAccessorMinecraft) mc).getTimer().renderPartialTicks, RotationUtils.serverRotations, hitThroughBlocks.isToggled() ? attackingEntity : null);
        if (mov != null && mov.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && mov.entityHit == attackingEntity) {
            Vec3 hitVec = mov.hitVec;
            hitVec = new Vec3(hitVec.xCoord - attackingEntity.posX, hitVec.yCoord - attackingEntity.posY, hitVec.zCoord - attackingEntity.posZ);
            mc.thePlayer.sendQueue.addToSendQueue(new C02PacketUseEntity(attackingEntity, hitVec));
            sent = true;
        }
        if (requireInteractAt && !sent) {
            return;
        }
        mc.thePlayer.sendQueue.addToSendQueue(new C02PacketUseEntity(attackingEntity, C02PacketUseEntity.Action.INTERACT));
    }

    private void sendDigPacket() {
        if (!Utils.holdingSword()) {
            return;
        }
        mc.thePlayer.sendQueue.addToSendQueue(new C07PacketPlayerDigging(C07PacketPlayerDigging.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, DOWN));
        blocked = false;
    }

    private float[] getRotationsSmoothed(float[] rotations) {
        float serverYaw = RotationUtils.serverRotations[0];
        float serverPitch = RotationUtils.serverRotations[1];
        float unwrappedYaw = unwrapYaw(rotations[0], serverYaw);

        float deltaYaw = unwrappedYaw - serverYaw;
        float deltaPitch = rotations[1] - serverPitch;

        float yawSmoothing = (float) rotationSmoothing.getInput();
        float pitchSmoothing = yawSmoothing;

        float strafe = mc.thePlayer.moveStrafing;
        if (strafe < 0 && deltaYaw < 0 || strafe > 0 && deltaYaw > 0) {
            yawSmoothing = Math.max(1f, yawSmoothing / 2f);
        }

        float motionY = (float) mc.thePlayer.motionY;
        if (motionY > 0 && deltaPitch > 0 || motionY < 0 && deltaPitch < 0) {
            pitchSmoothing = Math.max(1f, pitchSmoothing / 2f);
        }

        serverYaw += deltaYaw / Math.max(1f, yawSmoothing);
        serverPitch += deltaPitch / Math.max(1f, pitchSmoothing);

        return new float[] { serverYaw, serverPitch };
    }

    private void handleInteractAndAttack(double distance, boolean swung) {
        if (ModuleManager.antiFireball != null && ModuleManager.antiFireball.isEnabled() && ModuleManager.antiFireball.fireball != null && ModuleManager.antiFireball.attack) {
            if (ModuleManager.bedAura.rotate) {
                return;
            }
            if (!ModuleManager.antiFireball.silentSwing.isToggled()) {
                mc.thePlayer.swingItem();
            }
            else {
                mc.thePlayer.sendQueue.addToSendQueue(new C0APacketAnimation());
            }
            mc.playerController.attackEntity(mc.thePlayer, ModuleManager.antiFireball.fireball);
            mc.thePlayer.sendQueue.addToSendQueue(new C02PacketUseEntity(ModuleManager.antiFireball.fireball, C02PacketUseEntity.Action.INTERACT));
        }
        else {
            handleSwingAndAttack(distance, swung);
            interactAt(false);
        }
    }

    public void resetBlinkState(boolean unblock) {
        if (!hasAB) {
            return;
        }
        blockingServer = false;
        releasePackets();
        if (Raven.packetsHandler.playerSlot.get() != mc.thePlayer.inventory.currentItem && swapped && Utils.holdingSword()) {
            setSlot();
        }
        else if (unblock && !ModuleManager.scaffold.isEnabled && blocked) {
            sendUnBlock = true;
        }
        interactTicks = 0;
        lag = false;
        hasAB = false;
    }

    private void releasePackets() {
        if (outboundLag != null) {
            outboundLag.getTimeout().forceTimeOut();
            outboundLag = null;
        }
    }

    private void lagPackets() {
        if (outboundLag != null && outboundLag.getTimeout().isTimedOut()) {
            outboundLag = null;
        }

        if (outboundLag == null) {
            outboundLag = new LagRequest(EnumLagDirection.ONLY_OUTBOUND, new ModuleBackedTimeout(this));
            Raven.lagHandler.requestLag(outboundLag);
        }
    }

    private boolean inRange(final Entity target, final double distance) {
        return RotationUtils.isPossibleToHit(target, distance, RotationUtils.getRotations(target));
    }

    private boolean manualBlock() {
        return (!manualBlock.isToggled() || Mouse.isButtonDown(1)) && Utils.holdingSword();
    }

    static class KillAuraTarget {
        double distance;
        float health;
        int hurttime;
        double yawDelta;
        int entityId;
        boolean isEnemy;

        public KillAuraTarget(double distance, float health, int hurttime, double yawDelta, int entityId, boolean isEnemy) {
            this.distance = distance;
            this.health = health;
            this.hurttime = hurttime;
            this.yawDelta = yawDelta;
            this.entityId = entityId;
            this.isEnemy = isEnemy;
        }
    }
}