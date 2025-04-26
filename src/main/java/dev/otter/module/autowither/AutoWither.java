package dev.otter.module.autowither;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
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
import org.rusherhack.client.api.events.render.EventRender3D;
import org.rusherhack.client.api.feature.module.ModuleCategory;
import org.rusherhack.client.api.feature.module.ToggleableModule;
import org.rusherhack.client.api.render.IRenderer3D;
import org.rusherhack.client.api.setting.ColorSetting;
import org.rusherhack.client.api.utils.*;
import org.rusherhack.core.event.subscribe.Subscribe;
import org.rusherhack.core.notification.NotificationType;
import org.rusherhack.core.setting.BooleanSetting;
import org.rusherhack.core.setting.EnumSetting;
import org.rusherhack.core.setting.NullSetting;
import org.rusherhack.core.setting.NumberSetting;

public final class AutoWither extends ToggleableModule {

    private final NullSetting placeGroup = new NullSetting("Place");
    private final NullSetting renderGroup = new NullSetting("Render");

    private final NumberSetting<Double> placeRange = new NumberSetting<>("Place Range", 4.5, 2.0, 6.0).incremental(0.1);
    private final NumberSetting<Double> cancelRange = new NumberSetting<>("Cancel Extra", 2.0, 0.0, 6.0).incremental(1);
    private final NumberSetting<Double> minAway = new NumberSetting<>("Min Away", 1.0, 0.0, 4.0).incremental(0.1);
    private final NumberSetting<Integer> placeDelay = new NumberSetting<>("Place Delay", 1, 0, 8).incremental(1);
    private final BooleanSetting placeRotate = new BooleanSetting("Rotate", true);
    private final EnumSetting<SwapMode> swapMode = new EnumSetting<>("Swap Mode", SwapMode.SILENT);

    private final BooleanSetting renderPlacePos = new BooleanSetting("Render PlacePos", true);
    private final ColorSetting placeFill = new ColorSetting("PlacePos Fill", new Color(0, 255, 255, 80)).setAlphaAllowed(true);
    private final ColorSetting placeOutline = new ColorSetting("PlacePos Outline", new Color(0, 255, 255, 200)).setAlphaAllowed(false);

    private final BooleanSetting debug = new BooleanSetting("Debug", false);

    public enum SwapMode { NORMAL, SILENT, NONE }

    private boolean summoning = false;
    private int ticks = 0;

    private List<BlockPos> sandBlocks = new ArrayList<>();
    private List<BlockPos> skullBlocks = new ArrayList<>();

    private BlockPos basePreview = null;
    private WitherLayout layoutPreview = null;
    private BlockPos currentPos  = null;

    private int soulSlot = -1;
    private int skullSlot = -1;
    private int returnSlot = -1;

    public AutoWither() {
        super("AutoWither", "Automatically summons a Wither.", ModuleCategory.WORLD);

        placeGroup.addSubSettings(placeRange, cancelRange, minAway, placeDelay, placeRotate, swapMode);
        renderGroup.addSubSettings(renderPlacePos, placeFill, placeOutline);

        registerSettings(placeGroup, renderGroup, debug);
    }

    @Override public void onEnable()  {
        resetAll();
    }

    @Override public void onDisable() {
        resetAll();
    }

    private void resetAll() {
        summoning = false;
        ticks = 0;
        sandBlocks.clear();
        skullBlocks.clear();
        basePreview = currentPos = null;
        layoutPreview = null;
        soulSlot = skullSlot = returnSlot = -1;
    }

    @Subscribe
    private void onUpdate(EventUpdate e) {
        if (mc.player == null || mc.level == null) return;

        if (summoning) {
            handleSummon();
            return;
        }

        computePreview();

        // none found
        if (layoutPreview == null) {
            RusherHackAPI.getNotificationManager().send(NotificationType.ERROR, "No Layout found. Disabling.");
            setToggled(false);
            return;
        }

        // layout exists and we're idle -> start summoning
        if (startSummon() && debug.getValue()) {
            RusherHackAPI.getNotificationManager().send(NotificationType.INFO, "Started summoning at " + basePreview.toShortString());
        }
    }

    private boolean startSummon() {
        soulSlot = InventoryUtils.findItemHotbar(Items.SOUL_SAND);
        skullSlot = InventoryUtils.findItemHotbar(Items.WITHER_SKELETON_SKULL);
        if (soulSlot == -1 || skullSlot == -1) {
            RusherHackAPI.getNotificationManager().send(NotificationType.ERROR, "Missing Materials. Disabling.");
            setToggled(false);
            return false;
        }

        sandBlocks.clear();
        skullBlocks.clear();
        sandBlocks.addAll(layoutPreview.getSoulOffsets(basePreview));
        skullBlocks.addAll(layoutPreview.getSkullOffsets(basePreview));

        summoning = true;
        ticks = 0;
        currentPos = null;
        return true;
    }

    private void handleSummon() {
        if (ticks > 0) {
            --ticks;
            return;
        }

        if (sandBlocks.isEmpty() && skullBlocks.isEmpty()) {
            if (debug.getValue()) RusherHackAPI.getNotificationManager().send(NotificationType.INFO, "Summon complete. Disabling.");
            setToggled(false);
            return;
        }

        // If any block is too far, cancel
        double cancel = placeRange.getValue() + cancelRange.getValue();
        boolean outOfReach = Stream.concat(
                        layoutPreview.getSoulOffsets(basePreview).stream(),
                        layoutPreview.getSkullOffsets(basePreview).stream())
                .anyMatch(pos -> pos.getCenter().distanceToSqr(PlayerUtils.getEyeBlockPos().getCenter()) >= cancel * cancel);

        if (outOfReach) {
            RusherHackAPI.getNotificationManager().send(NotificationType.ERROR, "Blocks out of reach. Disabling.");
            setToggled(false);
            return;
        }

        boolean skullPhase = sandBlocks.isEmpty();
        List<BlockPos> blockPool = skullPhase ? skullBlocks : sandBlocks;

        currentPos = pickNearestPlaceable(blockPool);

        if (currentPos == null) {
            return;
        }

        returnSlot = mc.player.getInventory().selected;

        // Wait for the player to hold the correct item in swapmode none
        if (swapMode.getValue() == SwapMode.NONE) {
            if (!skullPhase && getSelectedItem() != Items.SOUL_SAND) {
                return;
            } else if (skullPhase && getSelectedItem() != Items.WITHER_SKELETON_SKULL) {
                return;
            }
        }

        swapToItem(skullPhase);

        if (placeRotate.getValue()) {
            float[] rot = RotationUtils.getRotations(Vec3.atCenterOf(currentPos));
            RusherHackAPI.getRotationManager().updateRotation(rot[0], rot[1]);
        }

        BlockHitResult hit = skullPhase
                ? hitAgainstSoulSand(currentPos)
                : RusherHackAPI.interactions().getBlockPlaceHitResult(currentPos, false, false, 5.0);
        boolean placed = hit != null && RusherHackAPI.interactions().useBlock(hit, InteractionHand.MAIN_HAND, true);

        blockPool.remove(currentPos);
        swapBack();
        ticks = placeDelay.getValue();
    }

    @Subscribe
    private void onRender3D(EventRender3D e) {
        IRenderer3D r = e.getRenderer();
        r.begin(e.getMatrixStack());
        r.setDepthTest(false);
        r.setLineWidth(1.0f);

        // render current placing pos
        if (summoning && renderPlacePos.getValue() && currentPos != null) {
            r.drawBox(currentPos, true,  false, placeFill.getValueRGB());
            r.drawBox(currentPos, false, true, placeOutline.getValueRGB());
        }

        r.end();
    }

    private void computePreview() {
        Vec3 eye = mc.player.position().add(0, mc.player.getEyeHeight(), 0);
        float range = placeRange.getValue().floatValue();
        double rangeSq = range * range;
        double minSq = minAway.getValue() * minAway.getValue();

        record CandidateLayout(BlockPos base, WitherLayout layout, double avg) {}
        List<CandidateLayout> candidateLayouts = new ArrayList<>();

        // For each Air block check if we can place a wither there
        WorldUtils.getSphere(mc.player.blockPosition(), range, p -> mc.level.getBlockState(p).isAir())
                .forEach(base -> {
                    for (WitherLayout potentialLayout : WitherLayout.findLayoutsAt(base)) {

                        // Add all blocks of the potential wither placement
                        List<BlockPos> allLayoutBlocks = new ArrayList<>();
                        allLayoutBlocks.addAll(potentialLayout.getSoulOffsets(base));
                        allLayoutBlocks.addAll(potentialLayout.getSkullOffsets(base));

                        // Disgard any layouts which blocks are too far or too close
                        if (allLayoutBlocks.stream().anyMatch(p -> p.getCenter().distanceToSqr(eye.x, eye.y, eye.z) > rangeSq)) return;
                        if (allLayoutBlocks.stream().anyMatch(p -> p.getCenter().distanceToSqr(eye.x, eye.y, eye.z) < minSq)) return;

                        // Check if any soulsand bock in the layout has a block next to it to start placing
                        if (potentialLayout.getSoulOffsets(base).stream()
                                .noneMatch(this::hasNeighbor)) return;

                        // Compute the average block distance from the layout to the player
                        double avg = allLayoutBlocks.stream()
                                .mapToDouble(p -> p.getCenter().distanceToSqr(eye.x, eye.y, eye.z))
                                .average().orElse(0);

                        candidateLayouts.add(new CandidateLayout(base, potentialLayout, avg));
                    }
                });

        if (candidateLayouts.isEmpty()) {
            basePreview = null;
            layoutPreview = null;
            return;
        }
        // Choose the layout with the closest average block distance to the player. (Probably best ensuring a successful placement)
        candidateLayouts.sort(Comparator.comparingDouble(c -> c.avg));
        basePreview = candidateLayouts.get(0).base;
        layoutPreview = candidateLayouts.get(0).layout;
    }

    private boolean hasNeighbor(BlockPos pos) {
        return Stream.of(Direction.values())
                .map(pos::relative)
                .anyMatch(p -> !WorldUtils.isReplaceble(p));
    }

    private Item getSelectedItem() {
        return mc.player.getInventory().getItem(mc.player.getInventory().selected).getItem();
    }

    // Special stuff so we only place on soulsand for heads
    private BlockHitResult hitAgainstSoulSand(BlockPos skullPos) {
        for (Direction dir : Direction.values()) {
            BlockPos neighbour = skullPos.relative(dir);
            if (mc.level.getBlockState(neighbour).is(Blocks.SOUL_SAND)) {
                // click the Soul Sand, on the face that touches the skull
                return new BlockHitResult(
                        Vec3.atCenterOf(neighbour),
                        dir.getOpposite(),
                        neighbour,
                        false);
            }
        }
        return null;
    }

    private BlockPos pickNearestPlaceable(List<BlockPos> pool) {
        if (pool.isEmpty()) return null;

        Vec3 eye = mc.player.position().add(0, mc.player.getEyeHeight(), 0);
        return pool.stream()
                .sorted(Comparator.comparingDouble(p -> p.getCenter().distanceToSqr(eye)))
                .filter(this::isCurrentlyPlaceable)
                .findFirst()
                .orElse(null);
    }

    private boolean isCurrentlyPlaceable(BlockPos pos) {
        if (!WorldUtils.isReplaceble(pos)) return false;
        if (!WorldUtils.checkCollision(pos)) return false;
        BlockHitResult hit = RusherHackAPI.interactions().getBlockPlaceHitResult(pos, false, false, 5.0);
        return hit != null;
    }

    private void swapBack() {
        if (swapMode.getValue() == SwapMode.SILENT && returnSlot != -1) {
            mc.player.getInventory().selected = returnSlot;
        }
    }

    private void swapToItem(boolean skull) {
        int wanted = skull ? skullSlot : soulSlot;
        switch (swapMode.getValue()) {
            case NORMAL -> mc.player.getInventory().selected = wanted;
            case SILENT -> {
                if (mc.player.getInventory().selected != wanted) {
                    mc.player.getInventory().selected = wanted;
                }
            }
        }
    }
}