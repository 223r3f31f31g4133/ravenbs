package keystrokesmod.module.impl.combat;

import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.*;

import keystrokesmod.event.PreUpdateEvent;
import keystrokesmod.event.SendPacketEvent;
import keystrokesmod.module.Module;
import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.render.HUD;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;

import keystrokesmod.script.packet.serverbound.C08;
import keystrokesmod.utility.*;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.Packet;
import net.minecraft.network.handshake.client.C00Handshake;
import net.minecraft.network.login.client.C00PacketLoginStart;
import net.minecraft.network.play.client.C02PacketUseEntity;
import net.minecraft.network.play.client.C03PacketPlayer;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.lwjgl.opengl.GL11;

public class LagRange extends Module {
    public SliderSetting maxDelay;
    private SliderSetting activationDist;
    private SliderSetting hurttime;
    private ButtonSetting ignoreTeammates, weaponOnly;
    public ButtonSetting initialPosition;

    private ConcurrentSkipListMap<Long, List<Packet<?>>> packetQueue = new ConcurrentSkipListMap<>();
    private Timer timer;
    private long packetDelay;

    private double closest;
    private boolean disable = false;

    Vec3 lagPosition = null;
    private int color = new Color(0, 187, 255, 255).getRGB();

    public LagRange() {
        super("LagRange", category.combat, 0);
        this.registerSetting(maxDelay = new SliderSetting("Maximum Delay", "ms", 50.0, 1.0, 1500.0, 1.0));
        this.registerSetting(activationDist = new SliderSetting("Activation Distance", " blocks", 7, 0, 20, 1));
        this.registerSetting(hurttime = new SliderSetting("Hurttime", 2, 0, 10, 1));
        this.registerSetting(initialPosition = new ButtonSetting("Show initial position", true));
        this.registerSetting(ignoreTeammates = new ButtonSetting("Ignore teammates", false));
        this.registerSetting(weaponOnly = new ButtonSetting("Weapon only", false));
    }

    @Override
    public String getInfo() {
        return packetDelay + "ms";
    }

    @Override
    public void guiUpdate() {
        if (packetDelay != maxDelay.getInput()) {
            if (this.isEnabled()) {
                this.onDisable();
            }
            packetDelay = (int) maxDelay.getInput();
        }
    }

    @Override
    public void onEnable() {
        (timer = new Timer()).scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                updatePacketQueue(false);
            }
        }, 0L, 10L);
    }

    @Override
    public void onDisable() {
        disable = false;
        reset();
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public void onPreUpdate(PreUpdateEvent e) {
        disable = false;
        double boxSize = activationDist.getInput();

        Vec3 myPosition = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);

        a(boxSize, myPosition);

        boolean correctHeldItem = !weaponOnly.isToggled();
        if (!correctHeldItem) {
            boolean holdingWeapon = false;
            holdingWeapon = Utils.holdingWeapon(); // Weapon check
            correctHeldItem = holdingWeapon;
        }

        if (!(correctHeldItem && closest != -1 && closest < boxSize * boxSize)) {
            reset();
            disable = true;
        }

        if (ModuleUtils.isBreaking) {
            reset();
            disable = true;
        }

        if (Utils.getHorizontalSpeed() > 0.4) {
            reset();
            disable = true;
        }

        if (Utils.isBedwarsPracticeOrReplay() || Utils.isLobby()) {
            reset();
            disable = true;
        }

        if (mc.thePlayer.motionX == 0.0D && mc.thePlayer.motionY == -0.0784000015258789D && mc.thePlayer.motionZ == 0.0D && !Utils.isMoving()) {
            reset();
            disable = true;
        }

        if (ModuleUtils.blinking) {
            reset();
            disable = true;
        }

        if (ModuleManager.killAura.hasAB) {
            reset();
            disable = true;
        }

        if (lagPosition != null && inPlayer(lagPosition) && !inPlayer(myPosition)) {
            reset();
            disable = true;
        }

        if (mc.thePlayer.ticksExisted <= 20) {
            reset();
            disable = true;
            Utils.sendMessage("world");
        }

    }

    private boolean inPlayer(Vec3 lagPos) {
        double cl = -1;
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
            } else {
                continue;
            }
            double maxRange = 3.0;
            if (getDistanceToEntityFromVec(lagPos, entity) < maxRange + maxRange / 3) { // simple distance check
                double distanceSq = Utils.distanceToSq(lagPos);
                if (cl == -1 || distanceSq < cl) {
                    cl = distanceSq;
                    if (cl >= 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private float getDistanceToEntityFromVec(Vec3 v, Entity entityIn) {
        float f = (float)(v.xCoord - entityIn.posX);
        float f1 = (float)(v.yCoord - entityIn.posY);
        float f2 = (float)(v.zCoord - entityIn.posZ);
        return MathHelper.sqrt_float(f * f + f1 * f1 + f2 * f2);
    }

    private void a(double boxSize, Vec3 myPosition) {
        closest = -1;
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
            else {
                continue;
            }
            double maxRange = activationDist.getInput();
            if (mc.thePlayer.getDistanceToEntity(entity) < maxRange + maxRange / 3) { // simple distance check
                double distanceSq = Utils.distanceToSq(myPosition);
                if (closest == -1 || distanceSq < closest) {
                    closest = distanceSq;
                }
            }
        }
    }

    private void updatePacketQueue(boolean flush) {
        if (packetQueue.isEmpty()) {
            return;
        }
        if (flush) {
            for (Map.Entry<Long, List<Packet<?>>> entry : packetQueue.entrySet()) {
                for (Packet packet : entry.getValue()) {
                    PacketUtils.sendPacketNoEvent(packet);
                }
            }
            lagPosition = null;
            ModuleUtils.lagging = false;
            packetQueue.clear();
        }
        else {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<Long, List<Packet<?>>>> it = packetQueue.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<Long, List<Packet<?>>> entry2 = it.next();
                if (now < entry2.getKey()) {
                    break;
                }
                for (Packet packet2 : entry2.getValue()) {
                    PacketUtils.sendPacketNoEvent(packet2);
                    if (packet2 instanceof C03PacketPlayer) {
                        C03PacketPlayer c03 = (C03PacketPlayer) packet2;
                        lagPosition = new Vec3(c03.getPositionX(), c03.getPositionY(), c03.getPositionZ());
                        if (packet2 instanceof C03PacketPlayer.C06PacketPlayerPosLook) {
                            C03PacketPlayer.C06PacketPlayerPosLook c06 = (C03PacketPlayer.C06PacketPlayerPosLook) packet2;
                            lagPosition = new Vec3(c06.getPositionX(), c06.getPositionY(), c06.getPositionZ());
                        }
                        if (packet2 instanceof C03PacketPlayer.C05PacketPlayerLook) {
                            C03PacketPlayer.C05PacketPlayerLook c05 = (C03PacketPlayer.C05PacketPlayerLook) packet2;
                            lagPosition = new Vec3(c05.getPositionX(), c05.getPositionY(), c05.getPositionZ());
                        }
                        if (packet2 instanceof C03PacketPlayer.C04PacketPlayerPosition) {
                            C03PacketPlayer.C04PacketPlayerPosition c04 = (C03PacketPlayer.C04PacketPlayerPosition) packet2;
                            lagPosition = new Vec3(c04.getPositionX(), c04.getPositionY(), c04.getPositionZ());
                        }
                    }
                }
                it.remove();
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPacketSent(SendPacketEvent e) {
        if (!Utils.nullCheck() || mc.isSingleplayer() || e.isCanceled() || e.getPacket().getClass().getSimpleName().startsWith("S") || e.getPacket() instanceof C00PacketLoginStart || e.getPacket() instanceof C00Handshake) {
            return;
        }
        if (e.getPacket() instanceof C02PacketUseEntity) {
            C02PacketUseEntity c02 = (C02PacketUseEntity) e.getPacket();
            if (c02.getAction().name().startsWith("ATTACK")) {
                reset();
                disable = true;
                return;
            }
        }
        if (e.getPacket() instanceof C08PacketPlayerBlockPlacement) {
            if (Utils.holdingBlocks()) {
                reset();
                disable = true;
                return;
            }
        }
        if (e.getPacket() instanceof C0BPacketEntityAction) {
            reset();
            disable = true;
            return;
        }
        if (disable) {
            return;
        }
        if (timer == null) {
            (timer = new Timer()).scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    updatePacketQueue(false);
                }
            }, 0L, 10L);
        }
        if (lagPosition == null) {
            lagPosition = new Vec3(mc.thePlayer.posX, mc.thePlayer.posY, mc.thePlayer.posZ);
        }
        ModuleUtils.lagging = true;
        long time = System.currentTimeMillis() + (int) maxDelay.getInput();
        List<Packet<?>> packetList = packetQueue.get(time);
        if (packetList == null) {
            packetList = new ArrayList<>();
        }
        packetList.add(e.getPacket());
        packetQueue.put(time, packetList);
        e.setCanceled(true);
    }

    private void reset() {
        if (timer != null) {
            timer.cancel();
            timer.purge();
            timer = null;
        }
        updatePacketQueue(true);
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!Utils.nullCheck() || mc.gameSettings.thirdPersonView == 0 || lagPosition == null || !initialPosition.isToggled()) {
            return;
        }
        drawBox(lagPosition);
    }

    private void drawBox(net.minecraft.util.Vec3 pos) {
        GlStateManager.pushMatrix();
        color = Theme.getGradient((int) HUD.theme.getInput(), 0);
        double x = pos.xCoord - mc.getRenderManager().viewerPosX;
        double y = pos.yCoord - mc.getRenderManager().viewerPosY;
        double z = pos.zCoord - mc.getRenderManager().viewerPosZ;
        AxisAlignedBB bbox = mc.thePlayer.getEntityBoundingBox().expand(0.1D, 0.1, 0.1);
        AxisAlignedBB axis = new AxisAlignedBB(bbox.minX - mc.thePlayer.posX + x, bbox.minY - mc.thePlayer.posY + y, bbox.minZ - mc.thePlayer.posZ + z, bbox.maxX - mc.thePlayer.posX + x, bbox.maxY - mc.thePlayer.posY + y, bbox.maxZ - mc.thePlayer.posZ + z);
        float a = (float) (color >> 24 & 255) / 255.0F;
        float r = (float) (color >> 16 & 255) / 255.0F;
        float g = (float) (color >> 8 & 255) / 255.0F;
        float b = (float) (color & 255) / 255.0F;
        GL11.glBlendFunc(770, 771);
        GL11.glEnable(3042);
        GL11.glDisable(3553);
        GL11.glDisable(2929);
        GL11.glDepthMask(false);
        GL11.glLineWidth(2.0F);
        GL11.glColor4f(r, g, b, a);
        RenderUtils.drawBoundingBox(axis, r, g, b);
        GL11.glEnable(3553);
        GL11.glEnable(2929);
        GL11.glDepthMask(true);
        GL11.glDisable(3042);
        GlStateManager.popMatrix();
    }


}