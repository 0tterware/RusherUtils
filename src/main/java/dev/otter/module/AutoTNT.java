package dev.otter.module;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.events.client.EventUpdate;
import org.rusherhack.client.api.events.render.EventRender3D;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.setting.ColorSetting;
import org.rusherhack.client.api.utils.*;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.EnumSetting;
import org.rusherhack.core.setting.NullSetting;
import org.rusherhack.core.setting.NumberSetting;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public class AutoTNT extends ToggleableModule {
    private final NullSetting generalGroup = new NullSetting("Place");
    private final NullSetting positionGroup = new NullSetting("Position");
    private final NullSetting renderGroup = new NullSetting("Render");
    private final NullSetting miscGroup = new NullSetting("Misc");

    // Place
    private final NumberSetting<Double> placeRange = new NumberSetting<>("Range",  4.0, 2.0, 6.0).incremental(0.1);
    private final NumberSetting<Integer> placeDelay = new NumberSetting<>("Place Delay",      1, 0, 8).incremental(1);
    private final BooleanSetting placeRotate = new BooleanSetting("Rotate", true);
    private final EnumSetting<SwapMode> swapMode = new EnumSetting<>("Swap Mode", SwapMode.SILENT);

    // Position
    private final NumberSetting<Integer> horizontalSpread = new NumberSetting<>("Horizontal Spread", 1, 0, 2).incremental(1);
    private final NumberSetting<Integer> verticalSpread = new NumberSetting<>("Vertical Spread", 1, 0, 2).incremental(1);
    private final EnumSetting<SortMode> sortMode = new EnumSetting<>("Sort Mode", SortMode.FURTHEST);

    // Render
    private final BooleanSetting renderCurrent = new BooleanSetting("Render Current", false);
    private final ColorSetting currentFill = new ColorSetting("Current Fill", new Color(200, 200, 200, 100))
            .setAlphaAllowed(true)
            .setVisibility(renderCurrent::getValue);
    private final ColorSetting currentOutline = new ColorSetting("Current Outline", new Color(200, 200, 200, 200))
            .setAlphaAllowed(false)
            .setVisibility(renderCurrent::getValue);
    private final BooleanSetting renderPotential = new BooleanSetting("Render Potential", false);
    private final ColorSetting potentialFill = new ColorSetting("Potential Fill", new Color(255, 0, 0, 100))
            .setAlphaAllowed(true)
            .setVisibility(renderPotential::getValue);
    private final ColorSetting potentialOutline = new ColorSetting("Potential Outline", new Color(255, 0, 0, 200))
            .setAlphaAllowed(false)
            .setVisibility(renderPotential::getValue);

    // Misc
    private final BooleanSetting autoDisable = new BooleanSetting("Auto Disable", false);
    private final BooleanSetting debug = new BooleanSetting("Debug", false);

    private int ticks;
    private List<BlockPos> lastPotentials = new ArrayList<>();
    private BlockPos lastPlacement;
    int tntSlot = -1;
    int origSlot = -1;

    public enum SortMode { CLOSEST, FURTHEST }
    public enum SwapMode { NORMAL, SILENT, NONE }

    public AutoTNT() {
        super("AutoTNT", "Places TNT around you.", ModuleCategory.WORLD);
        // general
        this.generalGroup.addSubSettings(
                this.placeRange,
                this.placeDelay,
                this.placeRotate,
                this.swapMode
        );

        // position
        this.positionGroup.addSubSettings(
                this.horizontalSpread,
                this.verticalSpread,
                this.sortMode
        );

        // render
        this.renderGroup.addSubSettings(
                this.renderCurrent,
                this.currentFill,
                this.currentOutline,
                this.renderPotential,
                this.potentialFill,
                this.potentialOutline
        );

        // misc
        this.miscGroup.addSubSettings(
                this.autoDisable,
                this.debug
        );

        this.registerSettings(
                generalGroup,
                positionGroup,
                renderGroup,
                miscGroup
        );
    }

    @Override
    public void onEnable() {
        ticks = 0;
        lastPotentials.clear();
        lastPlacement = null;
        tntSlot = -1;
        origSlot = -1;
    }

    @Override
    public void onDisable() {
        ticks = 0;
        lastPotentials.clear();
        lastPlacement = null;
        tntSlot = -1;
        origSlot = -1;
    }

    @Subscribe
    private void onUpdate(EventUpdate event) {
        if (mc.player == null || mc.level == null) return;
        if (++ticks < placeDelay.getValue()) return;

        lastPotentials = computeCandidates();
        lastPlacement = lastPotentials.isEmpty() ? null : lastPotentials.get(0);
        if (lastPlacement == null) { ticks = 0; return; }

        tntSlot = InventoryUtils.findItemHotbar(Items.TNT);
        if (tntSlot == -1) {
            if (debug.getValue()) ChatUtils.print("AutoTNT: No TNT found");
            if (autoDisable.getValue()) setToggled(false);
            ticks = 0;
            return;
        }
        origSlot = mc.player.getInventory().selected;

        switch (swapMode.getValue()) {
            case NORMAL, SILENT:
                if (tntSlot != origSlot) {
                    mc.player.getInventory().selected = tntSlot;
                }
                break;
            case NONE:
                if (mc.player.getInventory().getSelected().getItem() != Items.TNT) {
                    return;
                }
                break;
        }

        if (placeRotate.getValue()) {
            float[] rot = RotationUtils.getRotations(Vec3.atCenterOf(lastPlacement));
            RusherHackAPI.getRotationManager().updateRotation(rot[0], rot[1]);
        }

        BlockHitResult hit = RusherHackAPI.interactions().getBlockPlaceHitResult(lastPlacement, false, false, 5.0);
        boolean placed = hit != null && RusherHackAPI.interactions().useBlock(hit, InteractionHand.MAIN_HAND, true);
        if (placed) {
            if (debug.getValue()) ChatUtils.print("AutoTNT: Placed at " + lastPlacement.toShortString());
        } else if (debug.getValue()) {
            ChatUtils.print("AutoTNT: Failed at " + lastPlacement.toShortString());
        }

        if (swapMode.getValue() == SwapMode.SILENT && tntSlot != origSlot) {
            mc.player.getInventory().selected = origSlot;
        }

        ticks = 0;
    }

    private List<BlockPos> computeCandidates() {
        BlockPos playerPos = mc.player.blockPosition();
        float range = placeRange.getValue().floatValue();

        List<BlockPos> positions = WorldUtils.getSphere(playerPos, range, pos -> {
            var block = mc.level.getBlockState(pos).getBlock();
            if (block == Blocks.WATER || block == Blocks.BUBBLE_COLUMN || block == Blocks.LAVA) return false;

            if (!WorldUtils.isReplaceble(pos)) return false;
            if (!WorldUtils.checkCollision(pos)) return false;
            if (RusherHackAPI.interactions().getBlockPlaceHitResult(pos, false, false, 5.0) == null) return false;

            int hs = horizontalSpread.getValue();
            int vs = verticalSpread.getValue();
            for (BlockPos near : BlockPos.betweenClosed(pos.offset(-hs, -vs, -hs), pos.offset(hs, vs, hs))) {
                if (mc.level.getBlockState(near).getBlock() == Blocks.TNT) return false;
            }

            return true;
        });

        Vec3 playerCenter = mc.player.position().add(0, mc.player.getBbHeight() / 2, 0);
        positions.sort(Comparator.comparingDouble(pos -> {
            double distSq = pos.getCenter().distanceToSqr(playerCenter);
            return sortMode.getValue() == SortMode.CLOSEST ? distSq : -distSq;
        }));

        return positions;
    }


    @Subscribe
    private void onRender3D(EventRender3D event) {
        if (mc.player == null || mc.level == null) return;
        boolean hasTNT;
        if (Objects.requireNonNull(swapMode.getValue()) == SwapMode.NONE) {
            hasTNT = mc.player.getInventory().getSelected().getItem() == Items.TNT;
        } else {
            hasTNT = InventoryUtils.findItemHotbar(Items.TNT) != -1;
        }
        if (!hasTNT) return;

        var r = event.getRenderer();
        r.begin(event.getMatrixStack());

        if (renderPotential.getValue()) {
            int fill = potentialFill.getValueRGB();
            int outline = potentialOutline.getValueRGB();
            for (BlockPos p : lastPotentials) {
                r.drawBox(p, true, false, fill);
                r.drawBox(p, false, true, outline);
            }
        }

        if (renderCurrent.getValue() && lastPlacement != null) {
            int fill = currentFill.getValueRGB();
            int outline = currentOutline.getValueRGB();
            r.drawBox(lastPlacement, true, false, fill);
            r.drawBox(lastPlacement, false, true, outline);
        }

        r.end();
    }
}
