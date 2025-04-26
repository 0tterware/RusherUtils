package dev.otter.module;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.events.client.EventUpdate;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.utils.ChatUtils;
import org.rusherhack.client.api.utils.InventoryUtils;
import org.rusherhack.client.api.utils.RotationUtils;
import org.rusherhack.client.api.utils.WorldUtils;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.EnumSetting;
import org.rusherhack.core.setting.NumberSetting;

import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class AutoIgnite extends ToggleableModule {

    private final NumberSetting<Double> igniteRange = new NumberSetting<>("Ignite Range", 4.0, 1.0, 6.0).incremental(0.1);
    private final NumberSetting<Integer> igniteDelay = new NumberSetting<>("Ignite Delay", 1, 0, 8).incremental(1);
    private final BooleanSetting igniteRotate = new BooleanSetting("Rotate", true);
    private final EnumSetting<SwapMode> swapMode = new EnumSetting<>("Swap Mode", SwapMode.SILENT);
    private final EnumSetting<SortMode> sortMode = new EnumSetting<>("Sort Mode", SortMode.CLOSEST);
    private final BooleanSetting autoDisable = new BooleanSetting("Auto Disable", false);

    private int ticks;
    private BlockPos targetBlock;
    private final Random random = new Random();

    private enum SwapMode { NORMAL, SILENT, NONE }
    private enum SortMode { CLOSEST, FURTHEST, RANDOM }

    private static final Set<Item> FIRE_ITEMS = Set.of(
            Items.FLINT_AND_STEEL,
            Items.FIRE_CHARGE
    );


    public AutoIgnite() {
        super("AutoIgnite", "Automatically ignites nearby TNT blocks.", ModuleCategory.WORLD);

        registerSettings(
                igniteRange,
                igniteDelay,
                igniteRotate,
                swapMode,
                sortMode,
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
        if (++ticks < igniteDelay.getValue()) return;
        if (mc.player == null || mc.level == null) return;
        if (swapMode.getValue() == SwapMode.NONE && FIRE_ITEMS.contains(mc.player.getInventory().getSelected().getItem())) return;

        List<BlockPos> candidates = WorldUtils.getSphere(mc.player.blockPosition(), igniteRange.getValue().floatValue(), pos ->
                mc.level.getBlockState(pos).getBlock() == Blocks.TNT
        );

        if (candidates.isEmpty()) {
            ticks = 0;
            return;
        }

        targetBlock = switch (sortMode.getValue()) {
            case RANDOM -> candidates.get(random.nextInt(candidates.size()));
            case FURTHEST -> candidates.stream()
                    .max(Comparator.comparingDouble(pos -> mc.player.distanceToSqr(Vec3.atCenterOf(pos))))
                    .orElse(null);
            case CLOSEST -> candidates.stream()
                    .min(Comparator.comparingDouble(pos -> mc.player.distanceToSqr(Vec3.atCenterOf(pos))))
                    .orElse(null);
        };

        int slot = findFireItemSlot();
        if (slot == -1) {
            if (autoDisable.getValue()) setToggled(false);
            return;
        }

        int origSlot = mc.player.getInventory().selected;
        if (swapMode.getValue() != SwapMode.NONE && slot != origSlot) {
            mc.player.getInventory().selected = slot;
        }

        if (igniteRotate.getValue()) {
            float[] rotations = RotationUtils.getRotations(targetBlock.getCenter());
            RusherHackAPI.getRotationManager().updateRotation(rotations[0], rotations[1]);
        }

        BlockHitResult hit = new BlockHitResult(
                targetBlock.getCenter(),
                Direction.UP,
                targetBlock,
                true
        );

        if (hit != null) {
            mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
            mc.player.swing(InteractionHand.MAIN_HAND);
        }

        if (swapMode.getValue() == SwapMode.SILENT && slot != origSlot) {
            mc.player.getInventory().selected = origSlot;
        }

        ticks = 0;
    }

    private int findFireItemSlot() {
        for (Item item : FIRE_ITEMS) {
            int slot = InventoryUtils.findItemHotbar(item);
            if (slot != -1) {
                return slot;
            }
        }
        return -1;
    }
}
