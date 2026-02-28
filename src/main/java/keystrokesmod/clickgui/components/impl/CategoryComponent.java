package keystrokesmod.clickgui.components.impl;

import keystrokesmod.Raven;
import keystrokesmod.clickgui.components.Component;
import keystrokesmod.module.Module;
import keystrokesmod.module.impl.client.Gui;
import keystrokesmod.utility.RenderUtils;
import keystrokesmod.utility.Timer;
import keystrokesmod.utility.Utils;
import keystrokesmod.utility.profile.Manager;
import keystrokesmod.utility.profile.Profile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class CategoryComponent {
    public List<ModuleComponent> modules = new CopyOnWriteArrayList<>();
    public Module.category category;
    public boolean opened;
    public float width;
    public float y;
    public float x;
    public float titleHeight;
    public boolean dragging;
    public float xx;
    public float yy;
    public boolean hovering = false;
    public boolean hoveringOverCategory = false;
    public Timer smoothTimer;
    private Timer textTimer;
    public Timer smoothScrollTimer;
    public ScaledResolution scale;
    public float big;

    private final int translucentBackground = new Color(0, 0, 0, 110).getRGB();
    private final int regularOutline = new Color(81, 99, 149).getRGB();
    private final int regularOutline2 = new Color(97, 67, 133).getRGB();
    private final int categoryNameColor = new Color(220, 220, 220).getRGB();
    
    private float lastHeight;
    public float moduleY;
    private float lastModuleY;
    private float screenHeight;
    private boolean scrolled;
    private float targetModuleY;
    private float animationStartHeight;

    public CategoryComponent(Module.category category) {
        this.category = category;
        this.width = 92;
        this.x = 5;
        this.moduleY = this.y = 5;
        this.titleHeight = 13;
        this.smoothTimer = null;
        this.textTimer = null;
        this.xx = 0;
        this.opened = false;
        this.dragging = false;
        float moduleRenderY = this.titleHeight + 3;
        this.scale = new ScaledResolution(Minecraft.getMinecraft());
        this.targetModuleY = this.moduleY;

        this.lastHeight = this.y + this.titleHeight + 4;
        this.animationStartHeight = this.lastHeight;

        for (Module mod : Raven.getModuleManager().inCategory(this.category)) {
            ModuleComponent b = new ModuleComponent(mod, this, moduleRenderY);
            this.modules.add(b);
            moduleRenderY += 16;
        }
    }

    public List<ModuleComponent> getModules() {
        return this.modules;
    }

    public void reloadModules(boolean isProfile) {
        this.modules.clear();
        this.titleHeight = 13;
        float moduleRenderY = this.titleHeight + 3;

        if ((this.category == Module.category.profiles && isProfile) || (this.category == Module.category.scripts && !isProfile)) {
            ModuleComponent manager = new ModuleComponent(isProfile ? new Manager() : new keystrokesmod.script.Manager(), this, moduleRenderY);
            this.modules.add(manager);

            if ((Raven.profileManager == null && isProfile) || (Raven.scriptManager == null && !isProfile)) {
                return;
            }

            if (isProfile) {
                for (Profile profile : Raven.profileManager.profiles) {
                    moduleRenderY += 16;
                    ModuleComponent b = new ModuleComponent(profile.getModule(), this, moduleRenderY);
                    this.modules.add(b);
                }
            }
            else {
                Collection<Module> modulesCollection = Raven.scriptManager.scripts.values();
                List<Module> sortedModules = modulesCollection.stream().sorted(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER)).collect(Collectors.toList());
                for (Module module : sortedModules) {
                    moduleRenderY += 16;
                    ModuleComponent b = new ModuleComponent(module, this, moduleRenderY);
                    this.modules.add(b);
                }
            }
        }
    }

    public void setX(float newX, boolean limit) {
        if (limit) {
            ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
            float screenW = sr.getScaledWidth();
            newX = Math.max(newX, 2);
            newX = Math.min(newX, screenW - this.width - 4);
        }
        this.x = newX;
    }

    public void setY(float y, boolean limit) {
        if (limit) {
            ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
            float screenH = sr.getScaledHeight();
            float catHeight = this.titleHeight;

            y = Math.max(y, 1);
            float maxY = screenH - catHeight - 5;
            y = Math.min(y, maxY);
        }

        float scrollOffset = this.targetModuleY - this.y;
        this.y = y;
        this.moduleY = y + scrollOffset;
        this.targetModuleY = y + scrollOffset;
    }

    public void overTitle(boolean d) {
        this.dragging = d;
    }

    public boolean isOpened() {
        return this.opened;
    }

    public void mouseClicked(boolean on) {
        float currentActualHeight = this.lastHeight > 0 ? this.lastHeight : (this.y + this.titleHeight + 4);
        if (this.lastHeight <= 0 && !this.modules.isEmpty() && this.opened) {
            int currentModulesHeight = 0;
            for (ModuleComponent c : this.modules) {
                currentModulesHeight += c.getHeight();
            }
            currentActualHeight += currentModulesHeight;
        }

        float animationDuration = 250.0f;
        
        this.animationStartHeight = currentActualHeight;
        
        this.opened = on;
        (this.smoothTimer = new Timer(animationDuration)).start();
        (this.textTimer = new Timer(animationDuration)).start();
    }

    public void openModule(ModuleComponent component) {
        float currentBottom = this.lastHeight > 0 ? this.lastHeight : (this.y + this.titleHeight + 4);
        if (this.lastHeight <= 0 && !this.modules.isEmpty() && this.opened) {
            int currentModulesHeight = 0;
            for (ModuleComponent c : this.modules) {
                currentModulesHeight += c.getHeight();
            }
            currentBottom = this.y + this.titleHeight + currentModulesHeight + 4;
        }

        this.animationStartHeight = currentBottom;
        (this.smoothTimer = new Timer(250)).start();
    }

    public void onScroll(int mouseScrollInput) {
        for (Component component : this.modules) {
            component.onScroll(mouseScrollInput);
        }
        if (!hoveringOverCategory || !this.opened) {
            return;
        }
        int scrollSpeed = (int) Gui.scrollSpeed.getInput();

        if (this.smoothScrollTimer == null) {
            this.smoothScrollTimer = new Timer(200.0f);
            this.smoothScrollTimer.start();
        }

        if (mouseScrollInput > 0) {
            this.targetModuleY += scrollSpeed;
        }
        else if (mouseScrollInput < 0) {
            this.targetModuleY -= scrollSpeed;
        }
        scrolled = true;
    }

    public void render(FontRenderer renderer) {
        this.width = 92;
        int modulesHeight = 0;

        if (!this.modules.isEmpty() && (this.opened || smoothTimer != null)) {
            float maxCategoryHeight = this.screenHeight * 0.9f;
            float maxModulesHeight = maxCategoryHeight - this.titleHeight - 4;
            
            for (ModuleComponent c : this.modules) {
                int moduleHeight = c.getHeight();

                if (modulesHeight + moduleHeight > maxModulesHeight) {
                    float remainingHeight = maxModulesHeight - modulesHeight;
                    if (remainingHeight > 0) {
                        modulesHeight += (int) remainingHeight;
                    }
                    break;
                }
                modulesHeight += moduleHeight;
            }
            big = modulesHeight;
        }
        else if (!this.opened && smoothTimer == null) {
            big = 0;
        }

        float maxScrollY = this.y;
        float minScrollY = this.y;
        
        if (!this.modules.isEmpty() && (this.opened || smoothTimer != null)) {
            int totalModulesHeight = 0;
            for (ModuleComponent c : this.modules) {
                totalModulesHeight += c.getHeight();
            }

            float visibleHeight = big;

            if (totalModulesHeight > visibleHeight && visibleHeight > 0) {
                minScrollY = this.y - (totalModulesHeight - visibleHeight);
            }
        }

        this.targetModuleY = Math.max(minScrollY, Math.min(maxScrollY, this.targetModuleY));

        if (scrolled && smoothScrollTimer != null) {
            moduleY = Math.max(minScrollY, Math.min(maxScrollY, moduleY));
        }

        if (smoothTimer != null || this.opened) {
            this.updateHeight();
        }

        float middlePos = this.x + this.width / 2 - Minecraft.getMinecraft().fontRendererObj.getStringWidth(this.category.name()) / 2;
        float xPos = opened ? middlePos : this.x + 12;

        float maxCategoryBottom = this.y + (this.screenHeight * 0.9f);

        float targetHeight = this.opened ? Math.min(this.y + this.titleHeight + modulesHeight + 4, maxCategoryBottom) : (this.y + this.titleHeight + 4);
        float extra = targetHeight;

        if (smoothTimer != null && System.currentTimeMillis() - smoothTimer.last >= 280) {
            smoothTimer = null;
        }
        
        if (textTimer != null && System.currentTimeMillis() - textTimer.last >= 280) {
            textTimer = null;
        }

        if (smoothTimer != null) {
            boolean anyModuleAnimating = false;
            for (ModuleComponent c : this.modules) {
                int moduleHeight = c.getHeight();
                int fullHeight = c.isOpened ? c.getModuleHeight() : 16;
                if (moduleHeight != fullHeight) {
                    anyModuleAnimating = true;
                    break;
                }
            }

            if (anyModuleAnimating) {
                extra = targetHeight;
            }
            else {
                extra = smoothTimer.getValueFloat(animationStartHeight, targetHeight, 1);
                if ((this.opened && extra > targetHeight) || (!this.opened && extra < targetHeight)) {
                    extra = targetHeight;
                }
            }
        }

        float namePos = textTimer == null ? xPos : textTimer.getValueFloat(this.x + 12, middlePos, 1);
        if (!this.opened) {
            namePos = textTimer == null ? xPos : middlePos - textTimer.getValueFloat(0, this.width / 2 - Minecraft.getMinecraft().fontRendererObj.getStringWidth(this.category.name()) / 2 - 12, 1);
        }

        if (scrolled && smoothScrollTimer != null) {
            long elapsed = System.currentTimeMillis() - smoothScrollTimer.last;
            if (elapsed <= 250) {
                float interpolated = smoothScrollTimer.getValueFloat(lastModuleY, targetModuleY, 4);
                moduleY = (int) Math.max(minScrollY, Math.min(maxScrollY, interpolated));
            }
            else {
                moduleY = (int) targetModuleY;
                scrolled = false;
                smoothScrollTimer = null;
            }
        }
        else {
            moduleY = (int) targetModuleY;
        }
        lastModuleY = moduleY;

        lastHeight = extra;
        GL11.glPushMatrix();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        float scissorHeight = extra - this.y + 4;
        RenderUtils.scissor(0, this.y - 2, this.x + this.width + 4, scissorHeight);
        RenderUtils.drawRoundedGradientOutlinedRectangle(this.x - 2, this.y, this.x + this.width + 2, extra, 10, translucentBackground,
                ((opened || hovering) && Gui.rainBowOutlines.isToggled()) ? RenderUtils.setAlpha(Utils.getChroma(2, 0), 0.5) : regularOutline, ((opened || hovering) && Gui.rainBowOutlines.isToggled()) ? RenderUtils.setAlpha(Utils.getChroma(2, 700), 0.5) : regularOutline2);
        renderItemForCategory(this.category, (int) (this.x + 1), (int) (this.y + 4), opened || hovering);
        renderer.drawString(this.category.name(), namePos, this.y + 4, categoryNameColor, false);
        float moduleAreaTop = this.y + this.titleHeight + 3;
        float moduleAreaHeight = Math.max(0, extra - moduleAreaTop);
        RenderUtils.scissor(0, (int)moduleAreaTop, this.x + this.width + 4, (int)moduleAreaHeight);

        float prevY = this.y;
        this.y = this.moduleY;

        if (this.opened || smoothTimer != null) {
            for (Component c2 : this.modules) {
                c2.render();
            }
        }
        this.y = prevY;
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glPopMatrix();
    }

    public void updateHeight() {
        float y = this.titleHeight + 3;

        for (Component component : this.modules) {
            component.updateHeight(y);
            y += component.getHeight();
        }
    }

    public float getX() {
        return this.x;
    }

    public float getY() {
        return this.y;
    }

    public float getModuleY() {
        return this.moduleY;
    }

    public float getWidth() {
        return this.width;
    }

    public void mousePosition(int mouseX, int mouseY) {
        if (this.dragging) {
            float newX = mouseX - this.xx;
            float newY = mouseY - this.yy;

            if (Gui.limitToScreen.isToggled()) {
                ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
                int screenW = sr.getScaledWidth();
                int screenH = sr.getScaledHeight();

                float catHeight = this.titleHeight;

                newX = Math.max(newX, 2);
                newX = Math.min(newX, screenW - this.width - 4);

                newY = Math.max(newY, 1);
                int maxY = (int) (screenH - catHeight - 5);
                newY = Math.min(newY, maxY);
            }

            this.setX(newX, false);
            this.setY(newY, false);
        }

        hoveringOverCategory = overCategory(mouseX, mouseY);
        hovering = overTitle(mouseX, mouseY);
    }

    public boolean overTitle(int x, int y) {
        return x >= this.x && x <= this.x + this.width && (float) y >= (float) this.y + 2.0F && y <= this.y + this.titleHeight + 1;
    }

    public boolean overCategory(int x, int y) {
        return x >= this.x - 2 && x <= this.x + this.width + 2 && (float) y >= (float) this.y + 2.0F && y <= this.y + this.titleHeight + big + 1;
    }

    public boolean draggable(int x, int y) {
        return x >= this.x && x <= this.x + this.width && y >= this.y && y <= this.y + this.titleHeight;
    }

    public boolean overRect(int x, int y) {
        return x >= this.x - 2 && x <= this.x + this.width + 2 && y >= this.y && y <= lastHeight;
    }

    private void renderItemForCategory(Module.category category, int x, int y, boolean enchant) {
        RenderItem renderItem = Minecraft.getMinecraft().getRenderItem();
        double scale = 0.55;
        GlStateManager.pushMatrix();
        GlStateManager.scale(scale, scale, scale);
        ItemStack itemStack = null;
        switch (category) {
            case combat:
                itemStack = new ItemStack(Items.diamond_sword);
                break;
            case movement:
                itemStack = new ItemStack(Items.diamond_boots);
                break;
            case player:
                itemStack = new ItemStack(Items.golden_apple);
                break;
            case world:
                itemStack = new ItemStack(Items.filled_map);
                break;
            case render:
                itemStack = new ItemStack(Items.ender_eye);
                break;
            case minigames:
                itemStack = new ItemStack(Items.gold_ingot);
                break;
            case fun:
                itemStack = new ItemStack(Items.slime_ball);
                break;
            case other:
                itemStack = new ItemStack(Items.clock);
                break;
            case client:
                itemStack = new ItemStack(Items.compass);
                break;
            case profiles:
                itemStack = new ItemStack(Items.book);
                break;
            case scripts:
                itemStack = new ItemStack(Items.redstone);
                break;
        }
        if (itemStack != null) {
            if (enchant) {
                if (category != Module.category.player) {
                    itemStack.addEnchantment(Enchantment.unbreaking, 2);
                } else {
                    itemStack.setItemDamage(1);
                }
            }
            RenderHelper.enableGUIStandardItemLighting();
            GlStateManager.disableBlend();
            renderItem.renderItemAndEffectIntoGUI(itemStack, (int) (x / scale), (int) (y / scale));
            GlStateManager.enableBlend();
            RenderHelper.disableStandardItemLighting();
        }
        GlStateManager.scale(1, 1, 1);
        GlStateManager.popMatrix();
    }

    public void setScreenHeight(int screenHeight) {
        this.screenHeight = screenHeight;
    }

    public void limitPositions() {
        setX(this.x, true);
        setY(this.y, true);
    }

    public void onGuiClosed() {
        if (smoothTimer != null || textTimer != null) {
            float finalHeight = this.y + this.titleHeight;
            if (this.opened && !this.modules.isEmpty()) {
                int modulesHeight = 0;
                for (ModuleComponent c : this.modules) {
                    modulesHeight += c.getHeight();
                }
                finalHeight += modulesHeight + 4;
            } else {
                finalHeight += 4;
            }
            this.lastHeight = finalHeight;
        }
        
        smoothTimer = null;
        textTimer = null;
        smoothScrollTimer = null;
        scrolled = false;
        moduleY = targetModuleY;
        lastModuleY = moduleY;
    }
}