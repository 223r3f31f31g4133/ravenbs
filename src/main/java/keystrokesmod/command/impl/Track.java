package keystrokesmod.command.impl;

import keystrokesmod.module.ModuleManager;
import keystrokesmod.module.impl.world.AntiBot;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Utils;
import keystrokesmod.command.Command;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.awt.*;
import java.util.*;
import java.util.List;

public class Track extends Command {
    public List<EntityPlayer> trackedPlayers = new ArrayList<>();

    public Track() {
        super("track");

        MinecraftForge.EVENT_BUS.register(this);
    }

    @Override
    public void onExecute(String[] args) {
        if (args.length == 2) {
            if (args[1].equals("clear")) {
                chatWithPrefix("&b" + this.trackedPlayers.size() + " &7player" + (this.trackedPlayers.size() == 1 ? "" : "s") + " cleared.");
                this.trackedPlayers.clear();
                return;
            }

            String playerName = args[1];
            EntityPlayer player = mc.theWorld.getPlayerEntityByName(playerName);
            if (player == mc.thePlayer) {
                chatWithPrefix("&cYou cannot track yourself.");
                return;
            }
            if (player != null) {
                if (trackedPlayers.contains(player)) {
                    trackedPlayers.remove(player);
                    chatWithPrefix("&7Stopped tracking &b" + playerName);
                }
                else {
                    trackedPlayers.add(player);
                    chatWithPrefix("&7Started tracking &b" + playerName);
                }
            }
            else {
                chatWithPrefix("&b" + playerName + " &7not found.");
            }
        }
        else if (args.length == 1) {
            if (trackedPlayers.isEmpty()) {
                chatWithPrefix("&b0 &7players tracked.");
            } else {
                chatWithPrefix("&7Tracking &b" + trackedPlayers.size() + " &7player" + (this.trackedPlayers.size() == 1 ? "" : "s") + ".");
                for (EntityPlayer player : trackedPlayers) {
                    chatWithPrefix(" &b" + player.getName());
                }
            }
        }
        else {
            syntaxError();
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRenderWorld(RenderWorldLastEvent e) {
        if (!Utils.nullCheck() || trackedPlayers.isEmpty()) {
            return;
        }

        for (EntityPlayer player : new ArrayList<>(trackedPlayers)) {
            if (player == null || player.isDead) {
                continue;
            }
            if (player.deathTime != 0) {
                continue;
            }
            if (mc.thePlayer != player && AntiBot.isBot(player)) {
                continue;
            }

            if (ModuleManager.murderMystery == null || !ModuleManager.murderMystery.isEnabled() || ModuleManager.murderMystery.isEmpty()) {
                RenderUtils.renderEntity(player, 2, 0, 0, Color.red.getRGB(), false);
            }
        }
    }
}