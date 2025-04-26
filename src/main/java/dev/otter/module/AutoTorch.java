package dev.otter.module;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.events.client.EventUpdate;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.utils.InventoryUtils;
import org.rusherhack.client.api.utils.RotationUtils;
import org.rusherhack.client.api.utils.WorldUtils;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.EnumSetting;
import org.rusherhack.core.setting.NumberSetting;

import java.util.Comparator;
import java.util.List;

public class AutoTorch extends ToggleableModule {

    private final NumberSetting<Double> range = new NumberSetting<>("Range", 4.0, 2.0, 6.0).incremental(0.1);
    private final NumberSetting<Integer> lightLevel = new NumberSetting<>("Light Level", 7, 0, 14).incremental(1);
    private final NumberSetting<Integer> delay = new NumberSetting<>("Delay", 2, 0, 20).incremental(1);
    private final BooleanSetting rotate = new BooleanSetting("Rotate", true);
    private final EnumSetting<SwapMode> swapMode = new EnumSetting<>("Swap Mode", SwapMode.SILENT);
    private final BooleanSetting autoDisable = new BooleanSetting("Auto Disable", false);

    private int ticks;
    private BlockPos targetBlock;

    private enum SwapMode { NORMAL, SILENT, NONE }

    public AutoTorch() {
        super("AutoTorch", "Automatically places torches in low light areas.", ModuleCategory.WORLD);

        registerSettings(
                range,
                lightLevel,
                delay,
                rotate,
                swapMode,
                autoDisable
        );
    }

    @Override
    public void onEnable() {
        ticks = 0;
        targetBlock = null;
    }

    @Override
    public void onDisable() {
        ticks = 0;
        targetBlock = null;
    }

    @Subscribe
    private void onUpdate(EventUpdate event) {
        if (++ticks < delay.getValue()) return;
        if (mc.player == null || mc.level == null) return;
        if (swapMode.getValue() == SwapMode.NONE && mc.player.getInventory().getSelected().getItem() != Items.TORCH) return;

        List<BlockPos> candidates = WorldUtils.getSphere(mc.player.blockPosition(), range.getValue().floatValue(), this::canPlaceTorch);

        if (candidates.isEmpty()) {
            ticks = 0;
            return;
        }

        targetBlock = candidates.stream()
                .min(Comparator.comparingDouble(pos -> mc.player.distanceToSqr(Vec3.atCenterOf(pos))))
                .orElse(null);

        int slot = InventoryUtils.findItemHotbar(Items.TORCH);
        if (slot == -1) {
            if (autoDisable.getValue()) setToggled(false);
            return;
        }

        int origSlot = mc.player.getInventory().selected;
        if (swapMode.getValue() != SwapMode.NONE && slot != origSlot) {
            mc.player.getInventory().selected = slot;
        }

        if (rotate.getValue()) {
            float[] rotations = RotationUtils.getRotations(targetBlock.getCenter());
            RusherHackAPI.getRotationManager().updateRotation(rotations[0], rotations[1]);
        }

        BlockHitResult hit = RusherHackAPI.interactions().getBlockPlaceHitResult(targetBlock, false, false, 5.0);
        if (hit != null) {
            RusherHackAPI.interactions().useBlock(hit, InteractionHand.MAIN_HAND, true);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }

        if (swapMode.getValue() == SwapMode.SILENT && slot != origSlot) {
            mc.player.getInventory().selected = origSlot;
        }

        ticks = 0;
    }

    private boolean canPlaceTorch(BlockPos pos) {
        return mc.level.getBlockState(pos).getBlock() == Blocks.AIR &&
                mc.level.getBrightness(LightLayer.BLOCK, pos) <= lightLevel.getValue() &&
                WorldUtils.isReplaceble(pos) &&
                hasValidSupport(pos);
    }

    private boolean hasValidSupport(BlockPos pos) {
        for (var dir : Direction.values()) {
            if (dir == Direction.UP) continue;

            BlockPos support = pos.relative(dir);
            if (!mc.level.getBlockState(support).isSolid()) continue;

            if (dir == Direction.DOWN) {
                return mc.level.getBlockState(support).isFaceSturdy(mc.level, support, Direction.UP);
            } else {
                return mc.level.getBlockState(support).isFaceSturdy(mc.level, support, dir);
            }
        }
        return false;
    }

}
