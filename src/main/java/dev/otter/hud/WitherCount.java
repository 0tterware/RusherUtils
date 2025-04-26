package dev.otter.hud;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.rusherhack.client.api.feature.hud.ResizeableHudElement;
import org.rusherhack.client.api.render.RenderContext;
import org.rusherhack.client.api.setting.ColorSetting;
import org.rusherhack.client.api.utils.InventoryUtils;
import org.rusherhack.core.setting.BooleanSetting;

import java.awt.*;

public class WitherCount extends ResizeableHudElement {
    private final BooleanSetting textOnly = new BooleanSetting("Text Only", false);
    private final BooleanSetting countInv = new BooleanSetting("Count Inv", true);
    private final ColorSetting textColor = new ColorSetting("TextColor", Color.WHITE).setAlphaAllowed(false);

    private static final int ICON_SIZE = 16;

    public WitherCount() {
        super("WitherCount");
        registerSettings(textOnly, countInv, textColor);
    }

    private int witherCount() {
        int skulls = InventoryUtils.getItemCount(Items.WITHER_SKELETON_SKULL, !countInv.getValue(), true);
        int soulSand = InventoryUtils.getItemCount(Items.SOUL_SAND, !countInv.getValue(), true)
                + InventoryUtils.getItemCount(Items.SOUL_SOIL, !countInv.getValue(), true);
        return Math.min(skulls / 3, soulSand / 4);
    }

    @Override
    public double getWidth() {
        int count = witherCount();
        return textOnly.getValue()
                ? getFontRenderer().getStringWidth("Withers: " + count)
                : ICON_SIZE;
    }

    @Override
    public double getHeight() {
        return textOnly.getValue()
                ? getFontRenderer().getFontHeight()
                : ICON_SIZE;
    }

    @Override
    public void renderContent(RenderContext ctx, double mouseX, double mouseY) {
        if (mc.player == null || mc.level == null) {
            return;
        }

        int count = witherCount();

        if (textOnly.getValue()) {
            getFontRenderer().drawString("Withers: " + count, 0, 0, textColor.getValueRGB(), false);
        } else {
            ItemStack stack = new ItemStack(Items.WITHER_SKELETON_SKULL);
            ctx.graphics().renderItem(stack, 0, 0);
            ctx.graphics().renderItemDecorations(mc.font, stack, 0, 0, String.valueOf(count));
        }
    }
}

