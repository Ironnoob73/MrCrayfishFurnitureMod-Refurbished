package com.mrcrayfish.furniture.refurbished.client.gui.screen;

import com.google.common.collect.ImmutableMap;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mrcrayfish.furniture.refurbished.Components;
import com.mrcrayfish.furniture.refurbished.client.gui.ClientWorkbenchRecipeIngredientTooltip;
import com.mrcrayfish.furniture.refurbished.client.gui.ClientWorkbenchRecipeTooltip;
import com.mrcrayfish.furniture.refurbished.client.util.ScreenHelper;
import com.mrcrayfish.furniture.refurbished.core.ModTags;
import com.mrcrayfish.furniture.refurbished.crafting.WorkbenchContructingRecipe;
import com.mrcrayfish.furniture.refurbished.inventory.WorkbenchMenu;
import com.mrcrayfish.furniture.refurbished.network.Network;
import com.mrcrayfish.furniture.refurbished.network.message.MessageWorkbench;
import com.mrcrayfish.furniture.refurbished.platform.ClientServices;
import com.mrcrayfish.furniture.refurbished.util.Utils;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTextTooltip;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.lwjgl.glfw.GLFW;

import org.jetbrains.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Author: MrCrayfish
 */
public class WorkbenchScreen extends ElectricityContainerScreen<WorkbenchMenu>
{
    public static final ResourceLocation WORKBENCH_TEXTURE = Utils.resource("textures/gui/container/workbench.png");
    public static final WidgetSprites FILTER_BUTTON_SPRITES = new WidgetSprites(
        ResourceLocation.withDefaultNamespace("recipe_book/filter_enabled"),
        ResourceLocation.withDefaultNamespace("recipe_book/filter_disabled"),
        ResourceLocation.withDefaultNamespace("recipe_book/filter_enabled_highlighted"),
        ResourceLocation.withDefaultNamespace("recipe_book/filter_disabled_highlighted")
    );
    public static final WidgetSprites SEARCH_NEIGHBOURS_SPRITES = new WidgetSprites(
        Utils.resource("search_neighbours_selected"),
        Utils.resource("search_neighbours_unselected"),
        Utils.resource("search_neighbours_selected_focused"),
        Utils.resource("search_neighbours_unselected_focused")
    );

    private static final int BUTTON_SIZE = 20;
    private static final int RECIPES_PER_ROW = 6;
    private static final int WINDOW_WIDTH = BUTTON_SIZE * RECIPES_PER_ROW;
    private static final int WINDOW_HEIGHT = 88;
    private static final int SCROLL_SPEED = 10;
    private static final int SCROLLBAR_HEIGHT = 15;
    private static final int SCROLLBAR_AREA = 106;

    private static final Category CATEGORY_ALL = new Category();
    private static final Category CATEGORY_GENERAL = new Category(ModTags.Items.GENERAL, ModTags.Items.BEDROOM);
    private static final Category CATEGORY_KITCHEN = new Category(ModTags.Items.KITCHEN);
    private static final Category CATEGORY_OUTDOORS = new Category(ModTags.Items.OUTDOORS);
    private static final Category CATEGORY_BATHROOM = new Category(ModTags.Items.BATHROOM);
    private static final Category CATEGORY_ELECTRONICS = new Category(ModTags.Items.ELECTRONICS);
    private static final List<Category> CATEGORIES = List.of(CATEGORY_ALL, CATEGORY_GENERAL, CATEGORY_KITCHEN, CATEGORY_OUTDOORS, CATEGORY_BATHROOM, CATEGORY_ELECTRONICS);

    private static boolean craftableOnly; // Persistent

    protected final Map<ResourceLocation, Integer> recipeToIndex;
    protected final List<RecipeHolder<WorkbenchContructingRecipe>> displayRecipes = new ArrayList<>();
    protected StateSwitchingButton craftableOnlyButton;
    protected StateSwitchingButton searchNeighboursButton;
    protected double scroll; // 0 - content height
    protected int hoveredIndex = -1;
    protected int clickedY = -1;

    public WorkbenchScreen(WorkbenchMenu menu, Inventory playerInventory, Component title)
    {
        super(menu, playerInventory, title);
        this.imageWidth = 216;
        this.imageHeight = 229;
        this.inventoryLabelX = 28;
        this.inventoryLabelY = this.imageHeight - 94;
        this.menu.setUpdateCallback(this::updateRecipes);
        this.recipeToIndex = Util.make(() -> {
            Map<ResourceLocation, Integer> map = new HashMap<>();
            List<RecipeHolder<WorkbenchContructingRecipe>> recipes = menu.getRecipes();
            for(int i = 0; i < recipes.size(); i++) {
                map.put(recipes.get(i).id(), i);
            }
            return ImmutableMap.copyOf(map);
        });
        this.updateRecipes();
        this.scrollToSelected();
    }

    private void updateRecipes()
    {
        this.displayRecipes.clear();
        Category selectedCategory = CATEGORIES.stream().filter(Category::isEnabled).findFirst().orElse(CATEGORY_ALL);
        for(RecipeHolder<WorkbenchContructingRecipe> holder : this.menu.getRecipes())
        {
            if(!selectedCategory.in(holder.value().getResult()))
                continue;

            if(!craftableOnly || this.menu.canCraft(holder))
            {
                this.displayRecipes.add(holder);
            }
        }
        this.displayRecipes.sort(Comparator.comparing(holder -> holder.value().getResultId()));
    }

    @Override
    protected int getBannerTop()
    {
        return this.topPos + 52;
    }

    @Override
    protected void init()
    {
        super.init();
        this.craftableOnlyButton = this.addRenderableWidget(new CraftableButton(this.leftPos + 184, this.topPos + 44, 26, 16, craftableOnly));
        this.craftableOnlyButton.initTextureValues(FILTER_BUTTON_SPRITES);
        this.searchNeighboursButton = this.addRenderableWidget(new SearchNeighboursButton(this.leftPos + 184, this.topPos + 62, 26, 16, this.menu.shouldSearchNeighbours()));
        this.searchNeighboursButton.initTextureValues(SEARCH_NEIGHBOURS_SPRITES);
        this.addRenderableWidget(new CategoryButton(this.leftPos + 46, this.topPos + 108, 236, 55, CATEGORY_ALL));
        this.addRenderableWidget(new CategoryButton(this.leftPos + 66, this.topPos + 108, 236, 69, CATEGORY_GENERAL));
        this.addRenderableWidget(new CategoryButton(this.leftPos + 86, this.topPos + 108, 236, 83, CATEGORY_KITCHEN));
        this.addRenderableWidget(new CategoryButton(this.leftPos + 106, this.topPos + 108, 236, 97, CATEGORY_OUTDOORS));
        this.addRenderableWidget(new CategoryButton(this.leftPos + 126, this.topPos + 108, 236, 111, CATEGORY_BATHROOM));
        this.addRenderableWidget(new CategoryButton(this.leftPos + 146, this.topPos + 108, 236, 125, CATEGORY_ELECTRONICS));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
    {
        this.searchNeighboursButton.setStateTriggered(this.menu.shouldSearchNeighbours());
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
        if(this.menu.isPowered() && this.hoveredIndex != -1)
        {
            this.renderRecipeTooltip(graphics, mouseX, mouseY, this.hoveredIndex);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        graphics.blit(WORKBENCH_TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight);
        this.renderScrollbar(graphics, mouseY);
        this.renderRecipes(graphics, partialTick, mouseX, mouseY);
        this.renderOverlay(graphics);
        super.renderBg(graphics, partialTick, mouseX, mouseY);

        if(this.isHovering(199, 5, 10, 10, mouseX, mouseY))
        {
            this.setTooltipForNextRenderPass(ScreenHelper.createMultilineTooltip(List.of(Utils.translation("gui", "how_to").withStyle(ChatFormatting.GOLD), Utils.translation("gui", "workbench_info"))).toCharSequence(this.minecraft));
        }
    }

    private void renderScrollbar(GuiGraphics graphics, int mouseY)
    {
        int textureU = this.getMaxScroll() > 0 ? 216 : 228;
        graphics.blit(WORKBENCH_TEXTURE, this.leftPos + 169, this.topPos + 18 + this.getScrollbarPosition(mouseY), textureU, 40, 12, SCROLLBAR_HEIGHT);
    }

    private void renderRecipes(GuiGraphics graphics, float partialTick, int mouseX, int mouseY)
    {
        this.hoveredIndex = -1;
        graphics.enableScissor(this.leftPos + 46, this.topPos + 18, this.leftPos + 46 + WINDOW_WIDTH, this.topPos + 18 + WINDOW_HEIGHT);
        List<RecipeHolder<WorkbenchContructingRecipe>> recipes = this.displayRecipes;
        double scroll = this.getScrollAmount(mouseY);
        int startIndex = (int) (scroll / BUTTON_SIZE) * RECIPES_PER_ROW;
        int endIndex = startIndex + Mth.ceil(WINDOW_HEIGHT / (double) BUTTON_SIZE + 1) * RECIPES_PER_ROW;
        boolean mouseInWindow = ScreenHelper.isMouseWithinBounds(mouseX, mouseY, this.leftPos + 46, this.topPos + 18, WINDOW_WIDTH, WINDOW_HEIGHT);
        for(int i = startIndex; i < endIndex && i < recipes.size(); i++)
        {
            RecipeHolder<WorkbenchContructingRecipe> recipe = recipes.get(i);
            int recipeIndex = this.recipeToIndex.get(recipe.id());
            boolean canCraft = this.menu.canCraft(recipe);
            boolean selected = recipeIndex == this.menu.getSelectedRecipeIndex();
            int buttonX = this.leftPos + 46 + (i % RECIPES_PER_ROW) * BUTTON_SIZE;
            int buttonY = this.topPos + 18 + (i / RECIPES_PER_ROW) * BUTTON_SIZE - (int) scroll;
            int textureU = 216 + (!canCraft ? BUTTON_SIZE : 0);
            int textureV = selected ? BUTTON_SIZE : 0;
            graphics.blit(WORKBENCH_TEXTURE, buttonX, buttonY, textureU, textureV, BUTTON_SIZE, BUTTON_SIZE);
            graphics.renderFakeItem(recipe.value().getResultItem(this.menu.getLevel().registryAccess()), buttonX + 2, buttonY + 2);
            if(mouseInWindow && ScreenHelper.isMouseWithinBounds(mouseX, mouseY, buttonX, buttonY, BUTTON_SIZE, BUTTON_SIZE))
            {
                this.hoveredIndex = recipeIndex;
            }
        }
        graphics.disableScissor();
    }

    private void renderOverlay(GuiGraphics graphics)
    {
        if(!this.menu.isPowered())
        {
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(0, 0, 200);
            graphics.fill(this.leftPos + 46, this.topPos + 18, this.leftPos + 46 + WINDOW_WIDTH, this.topPos + 18 + WINDOW_HEIGHT, 0xAA000000);
            pose.popPose();
        }
    }

    private void renderRecipeTooltip(GuiGraphics graphics, int mouseX, int mouseY, int recipeIndex)
    {
        RecipeHolder<WorkbenchContructingRecipe> holder = this.menu.getRecipes().get(recipeIndex);
        List<ClientTooltipComponent> components = new ArrayList<>();
        components.add(new ClientTextTooltip(holder.value().getResultItem(this.menu.getLevel().registryAccess()).getHoverName().getVisualOrderText()));
        if(!Screen.hasShiftDown())
        {
            components.add(new ClientWorkbenchRecipeTooltip(this.menu, holder.value()));
            components.add(new ClientTextTooltip(Components.GUI_HOLD_SHIFT_DETAILS.getVisualOrderText()));
        }
        else
        {
            Map<Integer, Integer> counted = new HashMap<>();
            holder.value().getMaterials().forEach(material -> components.add(new ClientWorkbenchRecipeIngredientTooltip(this.menu, material, counted)));
        }
        ClientServices.PLATFORM.renderTooltip(graphics, this.font, components, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button)
    {
        if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT)
        {
            if(this.menu.isPowered() && this.hoveredIndex != -1)
            {
                if(this.minecraft != null && this.minecraft.gameMode != null)
                {
                    Network.getPlay().sendToServer(new MessageWorkbench.SelectRecipe(this.hoveredIndex));
                    this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    return true;
                }
            }

            if(ScreenHelper.isMouseWithinBounds(mouseX, mouseY, this.leftPos + 169, this.topPos + 18 + this.getScrollbarPosition((int) mouseY), 12, SCROLLBAR_HEIGHT))
            {
                this.clickedY = (int) mouseY;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button)
    {
        if(button == GLFW.GLFW_MOUSE_BUTTON_LEFT && this.clickedY != -1)
        {
            this.scroll = this.getScrollAmount((int) mouseY);
            this.clickedY = -1;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY)
    {
        if(this.clickedY == -1 && ScreenHelper.isMouseWithinBounds(mouseX, mouseY, this.leftPos + 46, this.topPos + 18, WINDOW_WIDTH + 15, WINDOW_HEIGHT))
        {
            this.scroll = Mth.clamp(this.scroll - deltaY * SCROLL_SPEED, 0, this.getMaxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }

    private void updateScroll()
    {
        this.scroll = Mth.clamp(this.scroll, 0, this.getMaxScroll());
    }

    private double getScrollAmount(int mouseY)
    {
        return Mth.clamp(this.scroll + this.getScrollbarDelta(mouseY), 0, this.getMaxScroll());
    }

    private int getMaxScroll()
    {
        return !this.menu.isPowered() ? 0 : Math.max(0, (int) (Math.ceil(this.displayRecipes.size() / (double) RECIPES_PER_ROW) * BUTTON_SIZE) - WINDOW_HEIGHT);
    }

    private int getScrollbarPosition(int mouseY)
    {
        return (int) ((this.getScrollAmount(mouseY) / this.getMaxScroll()) * (SCROLLBAR_AREA - SCROLLBAR_HEIGHT));
    }

    private int getScrollbarDelta(int mouseY)
    {
        double scrollPerUnit = this.getMaxScroll() / (double) (SCROLLBAR_AREA - SCROLLBAR_HEIGHT);
        return this.clickedY != -1 ? (int) ((mouseY - this.clickedY) * scrollPerUnit) : 0;
    }

    private void scrollToSelected()
    {
        int selectedIndex = this.menu.getSelectedRecipeIndex();
        if(selectedIndex >= 0 && selectedIndex < this.displayRecipes.size())
        {
            int newScroll = (selectedIndex / RECIPES_PER_ROW) * BUTTON_SIZE;
            newScroll -= (WINDOW_HEIGHT - BUTTON_SIZE) / 2;
            this.scroll = Mth.clamp(newScroll, 0, this.getMaxScroll());
        }
    }

    @Override
    public void setFocused(@Nullable GuiEventListener listener)
    {
        if(listener == this.craftableOnlyButton || listener == this.searchNeighboursButton)
            return;
        super.setFocused(listener);
    }

    private class CraftableButton extends StateSwitchingButton
    {
        private static final Component VANILLA_ONLY_CRAFTABLE = Component.translatable("gui.recipebook.toggleRecipes.craftable");
        private static final Component VANILLA_ALL_RECIPES = Component.translatable("gui.recipebook.toggleRecipes.all");

        public CraftableButton(int x, int y, int width, int height, boolean state)
        {
            super(x, y, width, height, state);
            this.updateTooltip();
        }

        @Override
        public void onClick(double mouseX, double mouseY)
        {
            this.isStateTriggered = !this.isStateTriggered;
            WorkbenchScreen.craftableOnly = this.isStateTriggered;
            WorkbenchScreen.this.updateRecipes();
            this.updateTooltip();
        }

        private void updateTooltip()
        {
            this.setTooltip(Tooltip.create(this.isStateTriggered ? VANILLA_ONLY_CRAFTABLE : VANILLA_ALL_RECIPES));
        }
    }

    private static class SearchNeighboursButton extends StateSwitchingButton
    {
        private static final Component SEARCH_NEIGHBOURS_OFF = Utils.translation("gui", "workbench.search_neighbours.off");
        private static final Component SEARCH_NEIGHBOURS_ON = Utils.translation("gui", "workbench.search_neighbours.on");

        public SearchNeighboursButton(int x, int y, int width, int height, boolean state)
        {
            super(x, y, width, height, state);
            this.updateTooltip();
        }

        @Override
        public void onClick(double mouseX, double mouseY)
        {
            Network.getPlay().sendToServer(new MessageWorkbench.SearchNeighbours());
        }

        /*@Override
        public void renderWidget(GuiGraphics graphics, int $$1, int $$2, float $$3)
        {
            RenderSystem.disableDepthTest();
            int u = this.xTexStart;
            int v = this.yTexStart;
            v += this.isStateTriggered ? this.height * 2 : 0;
            v += this.isHoveredOrFocused() ? this.height : 0;
            graphics.blit(this.resourceLocation, this.getX(), this.getY(), u, v, this.width, this.height);
            RenderSystem.enableDepthTest();
        }*/

        @Override
        public void setStateTriggered(boolean state)
        {
            boolean original = this.isStateTriggered();
            super.setStateTriggered(state);
            if(original != state)
            {
                this.updateTooltip();
            }
        }

        private void updateTooltip()
        {
            this.setTooltip(Tooltip.create(this.isStateTriggered ? SEARCH_NEIGHBOURS_ON : SEARCH_NEIGHBOURS_OFF));
        }
    }

    public static class Category
    {
        private final TagKey<Item>[] tags;
        private boolean enabled;

        @SafeVarargs
        private Category(TagKey<Item> ... tags)
        {
            this.tags = tags;
            this.enabled = tags.length == 0;
        }

        public boolean isEnabled()
        {
            return this.enabled;
        }

        public void setEnabled(boolean enabled)
        {
            this.enabled = enabled;
        }

        public boolean in(ItemStack result)
        {
            if(this.tags.length == 0)
            {
                return true;
            }
            for(TagKey<Item> tag : this.tags)
            {
                if(result.is(tag))
                {
                    return true;
                }
            }
            return false;
        }
    }

    public class CategoryButton extends Button
    {
        private final int iconU;
        private final int iconV;
        private final Category category;

        protected CategoryButton(int x, int y, int iconU, int iconV, Category category)
        {
            super(x, y, 20, 16, CommonComponents.EMPTY, btn -> {
                ((CategoryButton) btn).toggle();
            }, DEFAULT_NARRATION);
            this.iconU = iconU;
            this.iconV = iconV;
            this.category = category;
            this.updateTooltip();
        }

        private void updateTooltip()
        {
            if(this.category.tags.length > 0)
            {
                ResourceLocation tagId = this.category.tags[0].location();
                String tooltipTitle = String.format("filterCategory.%s.%s", tagId.getNamespace(), tagId.getPath().replace("/", "."));
                String tooltipDesc = tooltipTitle + ".desc";
                this.setTooltip(ScreenHelper.createMultilineTooltip(List.of(Component.translatable(tooltipTitle), Component.translatable(tooltipDesc).withStyle(ChatFormatting.GRAY))));
            }
            else
            {
                this.setTooltip(Tooltip.create(Components.GUI_SHOW_ALL_CATEGORIES));
            }
        }

        private void toggle()
        {
            CATEGORIES.forEach(c -> c.setEnabled(false));
            this.category.enabled = true;
            WorkbenchScreen.this.updateRecipes();
            WorkbenchScreen.this.updateScroll();
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick)
        {
            int textureV = this.isHovered ? 87 : this.category.enabled ? 71 : 55;
            graphics.blit(WORKBENCH_TEXTURE, this.getX(), this.getY(), 216, textureV, 20, 16);
            graphics.blit(WORKBENCH_TEXTURE, this.getX() + 3, this.getY() + 1, this.iconU, this.iconV, 14, 14);
        }
    }
}
