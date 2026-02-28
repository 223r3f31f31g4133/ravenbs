package keystrokesmod.module.impl.player;

import keystrokesmod.event.PrePlayerInteractEvent;
import keystrokesmod.event.PreSlotScrollEvent;
import keystrokesmod.event.SlotUpdateEvent;
import keystrokesmod.mixin.impl.accessor.IAccessorPlayerControllerMP;
import keystrokesmod.mixin.interfaces.IMixinItemRenderer;
import keystrokesmod.module.Module;
import keystrokesmod.module.setting.impl.ButtonSetting;
import keystrokesmod.module.setting.impl.SliderSetting;
import keystrokesmod.utility.BlockUtils;
import keystrokesmod.utility.Utils;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Mouse;

public class AutoTool extends Module {
    private SliderSetting hoverDelay;
    private SliderSetting swapDelay;

    private ButtonSetting disableOnInteractable;
    private ButtonSetting disableWhileHoldingBlocks;
    private ButtonSetting rightDisable;
    private ButtonSetting requireCrouch;
    private ButtonSetting requireMouse;
    public ButtonSetting spoofItem;
    private ButtonSetting swapBack;
    private ButtonSetting overrideSwapBack;

    private boolean hasSwapped = false;
    private int swapDelayTick = 0;
    public int previousSlot = -1;
    private long ticksHovered;

    public AutoTool() {
        super("AutoTool", category.player);
        this.registerSetting(hoverDelay = new SliderSetting("Hover delay", 0.0, 0.0, 20.0, 1.0));
        this.registerSetting(swapDelay = new SliderSetting("Swap delay", 0, 0, 20, 1));
        this.registerSetting(disableOnInteractable = new ButtonSetting("Disable on interactable", true));
        this.registerSetting(disableWhileHoldingBlocks = new ButtonSetting("Disable while holding blocks", true));
        this.registerSetting(rightDisable = new ButtonSetting("Disable while right click", true));
        this.registerSetting(requireCrouch = new ButtonSetting("Only while crouching", false));
        this.registerSetting(requireMouse = new ButtonSetting("Require mouse down", true));
        this.registerSetting(spoofItem = new ButtonSetting("Spoof item", false));
        this.registerSetting(swapBack = new ButtonSetting("Swap to previous slot", true));
        this.registerSetting(overrideSwapBack = new ButtonSetting("Override swap back", false));
        this.closetModule = true;
    }

    @Override
    public void onDisable() {
        resetVariables(true);
    }

    @SubscribeEvent
    public void onScrollSlot(PreSlotScrollEvent e) {
        if (!overrideSwapBack.isToggled() || !hasSwapped) {
            return;
        }
        int slot = e.slot;
        slot = Integer.compare(slot, 0);
        slot = Math.floorMod(mc.thePlayer.inventory.currentItem - slot, 9);
        previousSlot = slot;
        e.setCanceled(true);
    }

    @SubscribeEvent
    public void onSlotUpdate(SlotUpdateEvent e) {
        if (!overrideSwapBack.isToggled() || !hasSwapped) {
            return;
        }
        previousSlot = e.slot;
        e.setCanceled(true);
    }

    @SubscribeEvent
    public void onPrePlayerInteract(PrePlayerInteractEvent e) {
        if (spoofItem.isToggled() && previousSlot != mc.thePlayer.inventory.currentItem && this.previousSlot != -1) {
            ((IMixinItemRenderer) mc.getItemRenderer()).setCancelUpdate(true);
            ((IMixinItemRenderer) mc.getItemRenderer()).setCancelReset(true);
        }
        MovingObjectPosition over = mc.objectMouseOver;
        if (this.hoverDelay.getInput() != 0) {
            if (over == null || over.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK) {
                resetSlot();
                resetVariables(true);
                return;
            }
            long ticks = this.ticksHovered + 1L;
            this.ticksHovered = ticks;
            if (ticks < this.hoverDelay.getInput()) {
                return;
            }
        }
        if (!mc.inGameHasFocus || mc.currentScreen != null || (rightDisable.isToggled() && Mouse.isButtonDown(1)) || !mc.thePlayer.capabilities.allowEdit || (requireCrouch.isToggled() && !mc.thePlayer.isSneaking()) || disableInteractable() || disableBlocks()) {
            resetVariables(false);
            return;
        }
        if (!mc.gameSettings.keyBindAttack.isKeyDown() && requireMouse.isToggled()) {
            resetSlot();
            return;
        }
        if (over == null || over.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || over.getBlockPos() == null) {
            resetVariables(false);
            return;
        }
        int slot = Utils.getTool(BlockUtils.getBlock(over.getBlockPos()));
        if (slot == -1) {
            return;
        }
        if (previousSlot == -1) {
            previousSlot = mc.thePlayer.inventory.currentItem;
        }
        if (!hasSwapped) {
            setSlot(slot);
        }
        else if (slot != mc.thePlayer.inventory.currentItem) {
            if (swapDelayTick-- <= 0) {
                setSlot(slot);
                swapDelayTick = (int) swapDelay.getInput();
            }
        }
    }

    private boolean disableBlocks() {
        if (disableWhileHoldingBlocks.isToggled()) {
            ItemStack heldItem = mc.thePlayer.getHeldItem();
            return heldItem != null && heldItem.getItem() instanceof ItemBlock && Utils.canBePlaced((ItemBlock) heldItem.getItem());
        }
        return false;
    }

    private boolean disableInteractable() {
        if (disableOnInteractable.isToggled()) {
            return BlockUtils.isInteractable(mc.objectMouseOver);
        }
        return false;
    }

    private void resetVariables(boolean resetHover) {
        if (resetHover) {
            ticksHovered = 0;
        }
        resetSlot();
        previousSlot = -1;
        hasSwapped = false;
        swapDelayTick = 0;
    }

    private void resetSlot() {
        if (previousSlot == -1 || !swapBack.isToggled()) {
            return;
        }
        setSlot(previousSlot);
        previousSlot = -1;
        hasSwapped = false;
        swapDelayTick = 0;
    }

    public void setSlot(int currentItem) {
        if (currentItem == -1 || currentItem == mc.thePlayer.inventory.currentItem) {
            return;
        }
        mc.thePlayer.inventory.currentItem = currentItem;
        hasSwapped = true;
        swapDelayTick = (int) swapDelay.getInput();
        ((IAccessorPlayerControllerMP) mc.playerController).callSyncCurrentPlayItem();
    }
}