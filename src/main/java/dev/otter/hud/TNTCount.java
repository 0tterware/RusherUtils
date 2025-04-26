package dev.otter.hud;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.rusherhack.client.api.feature.hud.ResizeableHudElement;
import org.rusherhack.client.api.render.RenderContext;
import org.rusherhack.client.api.setting.ColorSetting;
import org.rusherhack.client.api.utils.InventoryUtils;
import org.rusherhack.core.setting.BooleanSetting;

import java.awt.*;

public class TNTCount extends ResizeableHudElement {
    private final BooleanSetting textOnly = new BooleanSetting("Text Only", false);
    private final BooleanSetting countInv = new BooleanSetting("Count Inv", true);
    private final ColorSetting textColor = new ColorSetting("TextColor", Color.WHITE).setAlphaAllowed(false);

    private static final int ICON_SIZE = 16;

    public TNTCount() {
        super("TNTCount");
        registerSettings(textOnly, countInv, textColor);
    }

    private int tntCount() {
        return InventoryUtils.getItemCount(Items.TNT, !countInv.getValue(), true);
    }

    @Override
    public double getWidth() {
        int count = tntCount();
        return textOnly.getValue()
                ? getFontRenderer().getStringWidth("TNT: " + count)
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
        if (mc.player == null || mc.level == null) return;
        int count = tntCount();

        if (textOnly.getValue()) {
            getFontRenderer().drawString("TNT: " + count, 0, 0, textColor.getValueRGB(), false);
        } else {
            ItemStack stack = new ItemStack(Items.TNT);
            ctx.graphics().renderItem(stack, 0, 0);
            ctx.graphics().renderItemDecorations(mc.font, stack, 0, 0, String.valueOf(count));
        }
    }
}
