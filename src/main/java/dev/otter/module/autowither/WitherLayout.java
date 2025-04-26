package dev.otter.module.autowither;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.rusherhack.client.api.utils.WorldUtils;

import java.util.ArrayList;
import java.util.List;

import static org.rusherhack.client.api.Globals.mc;

public record WitherLayout(WitherDirection witherDirection, WitherArmAxis witherArmAxis) {

    public enum WitherDirection {UP, DOWN, SIDENORTH, SIDESOUTH, SIDEEAST, SIDEWEST}

    public enum WitherArmAxis {EASTWEST, NORTHSOUTH, UPDOWN}

    public List<BlockPos> getSoulOffsets(BlockPos base) {
        List<BlockPos> soulBlocks = new ArrayList<>();
        BlockPos middle = getMiddle(base);

        soulBlocks.add(base);
        soulBlocks.add(middle);

        switch (witherDirection) {
            case UP, DOWN -> {
                if (witherArmAxis == WitherArmAxis.EASTWEST) {
                    soulBlocks.add(middle.east());
                    soulBlocks.add(middle.west());
                } else if (witherArmAxis == WitherArmAxis.NORTHSOUTH) {
                    soulBlocks.add(middle.north());
                    soulBlocks.add(middle.south());
                }
            }
            case SIDEEAST, SIDEWEST -> {
                if (witherArmAxis == WitherArmAxis.NORTHSOUTH) {
                    soulBlocks.add(middle.north());
                    soulBlocks.add(middle.south());
                } else if (witherArmAxis == WitherArmAxis.UPDOWN) {
                    soulBlocks.add(middle.above());
                    soulBlocks.add(middle.below());
                }
            }
            case SIDENORTH, SIDESOUTH -> {
                if (witherArmAxis == WitherArmAxis.EASTWEST) {
                    soulBlocks.add(middle.east());
                    soulBlocks.add(middle.west());
                } else if (witherArmAxis == WitherArmAxis.UPDOWN) {
                    soulBlocks.add(middle.above());
                    soulBlocks.add(middle.below());
                }
            }
        }
        return soulBlocks;
    }

    public List<BlockPos> getSkullOffsets(BlockPos base) {
        List<BlockPos> skullBlocks = new ArrayList<>();
        BlockPos middle = getMiddle(base);
        BlockPos middleSkull = getMiddle(middle);

        skullBlocks.add(middleSkull);

        switch (witherDirection) {
            case UP, DOWN -> {
                if (witherArmAxis == WitherArmAxis.EASTWEST) {
                    skullBlocks.add(middleSkull.east());
                    skullBlocks.add(middleSkull.west());
                } else if (witherArmAxis == WitherArmAxis.NORTHSOUTH) {
                    skullBlocks.add(middleSkull.north());
                    skullBlocks.add(middleSkull.south());
                }
            }
            case SIDENORTH, SIDESOUTH -> {
                if (witherArmAxis == WitherArmAxis.EASTWEST) {
                    skullBlocks.add(middleSkull.east());
                    skullBlocks.add(middleSkull.west());
                } else if (witherArmAxis == WitherArmAxis.UPDOWN) {
                    skullBlocks.add(middleSkull.above());
                    skullBlocks.add(middleSkull.below());
                }
            }
            case SIDEEAST, SIDEWEST -> {
                if (witherArmAxis == WitherArmAxis.NORTHSOUTH) {
                    skullBlocks.add(middleSkull.north());
                    skullBlocks.add(middleSkull.south());
                } else if (witherArmAxis == WitherArmAxis.UPDOWN) {
                    skullBlocks.add(middleSkull.above());
                    skullBlocks.add(middleSkull.below());
                }
            }
        }
        return skullBlocks;
    }

    public List<BlockPos> getAirOffsets(BlockPos base) {
        List<BlockPos> airBlocks = new ArrayList<>();
        BlockPos middle = getMiddle(base);
        Direction back = opposite(witherDirection);

        switch (witherDirection) {
            case UP -> {
                if (witherArmAxis == WitherArmAxis.EASTWEST) {
                    airBlocks.add(middle.east().below());
                    airBlocks.add(middle.west().below());
                } else if (witherArmAxis == WitherArmAxis.NORTHSOUTH) {
                    airBlocks.add(middle.north().below());
                    airBlocks.add(middle.south().below());
                }
            }
            case DOWN -> {
                if (witherArmAxis == WitherArmAxis.EASTWEST) {
                    airBlocks.add(middle.east().above());
                    airBlocks.add(middle.west().above());
                } else if (witherArmAxis == WitherArmAxis.NORTHSOUTH) {
                    airBlocks.add(middle.north().above());
                    airBlocks.add(middle.south().above());
                }
            }
            case SIDENORTH, SIDESOUTH -> {
                if (witherArmAxis == WitherArmAxis.UPDOWN) {
                    airBlocks.add(middle.above().relative(back));
                    airBlocks.add(middle.below().relative(back));
                } else {
                    airBlocks.add(middle.east().relative(back));
                    airBlocks.add(middle.west().relative(back));
                }
            }
            case SIDEWEST, SIDEEAST -> {
                if (witherArmAxis == WitherArmAxis.UPDOWN) {
                    airBlocks.add(middle.above().relative(back));
                    airBlocks.add(middle.below().relative(back));
                } else {
                    airBlocks.add(middle.north().relative(back));
                    airBlocks.add(middle.south().relative(back));
                }
            }
        }
        return airBlocks;
    }

    private BlockPos getMiddle(BlockPos base) {
        return switch (witherDirection) {
            case UP -> base.above();
            case DOWN -> base.below();
            case SIDENORTH -> base.relative(Direction.NORTH);
            case SIDESOUTH -> base.relative(Direction.SOUTH);
            case SIDEEAST -> base.relative(Direction.EAST);
            case SIDEWEST -> base.relative(Direction.WEST);
        };
    }

    private static Direction opposite(WitherDirection direction) {
        return switch (direction) {
            case UP -> Direction.DOWN;
            case DOWN -> Direction.UP;
            case SIDENORTH -> Direction.SOUTH;
            case SIDESOUTH -> Direction.NORTH;
            case SIDEEAST -> Direction.WEST;
            case SIDEWEST -> Direction.EAST;
        };
    }

    public static List<WitherLayout> findLayoutsAt(BlockPos base) {
        List<WitherLayout> validLayouts = new ArrayList<>();

        for (WitherLayout layout : WITHER_LAYOUTS) {
            boolean valid = true;

            for (BlockPos block : layout.getSoulOffsets(base)) {
                if (!WorldUtils.isReplaceble(block) || !WorldUtils.checkCollision(block)) {
                    valid = false;
                    break;
                }
            }
            if (!valid) continue;

            for (BlockPos block : layout.getSkullOffsets(base)) {
                if (!WorldUtils.isReplaceble(block) || !WorldUtils.checkCollision(block)) {
                    valid = false;
                    break;
                }
            }
            if (!valid) continue;

            for (BlockPos block : layout.getAirOffsets(base)) {
                if (!mc.level.getBlockState(block).isAir()) {
                    valid = false;
                    break;
                }
            }
            if (!valid) continue;

            validLayouts.add(layout);
        }
        return validLayouts;
    }

    public static final WitherLayout[] WITHER_LAYOUTS = new WitherLayout[]{
            new WitherLayout(WitherDirection.UP, WitherArmAxis.EASTWEST),
            new WitherLayout(WitherDirection.UP, WitherArmAxis.NORTHSOUTH),
            new WitherLayout(WitherDirection.DOWN, WitherArmAxis.EASTWEST),
            new WitherLayout(WitherDirection.DOWN, WitherArmAxis.NORTHSOUTH),
            new WitherLayout(WitherDirection.SIDENORTH, WitherArmAxis.EASTWEST),
            new WitherLayout(WitherDirection.SIDESOUTH, WitherArmAxis.EASTWEST),
            new WitherLayout(WitherDirection.SIDEEAST, WitherArmAxis.NORTHSOUTH),
            new WitherLayout(WitherDirection.SIDEWEST, WitherArmAxis.NORTHSOUTH),
            new WitherLayout(WitherDirection.SIDENORTH, WitherArmAxis.UPDOWN),
            new WitherLayout(WitherDirection.SIDESOUTH, WitherArmAxis.UPDOWN),
            new WitherLayout(WitherDirection.SIDEEAST, WitherArmAxis.UPDOWN),
            new WitherLayout(WitherDirection.SIDEWEST, WitherArmAxis.UPDOWN),
    };
}