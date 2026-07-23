package advRocketry.Dimension;


import advRocketry.API;
import advRocketry.BlockEntities.EntityFluidRelease;
import advRocketry.Blocks.FluidRelease;
import advRocketry.Registry.GasRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluids;

/// This class should help to track water on a planet
/// Water should be added / removed to / from the composition when water blocks are placed / removed in the world
/// But there are some exceptions, for example melting ice or freezing water to ice should be ignored
/// Actions that reflect composition change like adjusting sea level or boiling water should be ignored
public class WaterCompositionTracker {

    // Thread-local flag to avoid composition tracking for known system callers.
    // Replaces the expensive StackWalker.walk() that was called on every setBlock().
    // Set this to true before block operations that should not affect composition,
    // then clear it after. The flag is checked once and cleared automatically.
    private static final ThreadLocal<Boolean> IGNORE_NEXT = ThreadLocal.withInitial(() -> false);

    /// Call this before setBlock operations that should not modify planet composition.
    /// Typical callers: SeaLevelAdjustment, DimensionEvents, TerraformingSystem,
    /// EntityFluidRelease, IceBlock melting, FlowingFluid spreading.
    public static void setIgnoreNextCompositionChange() {
        IGNORE_NEXT.set(true);
    }

    /// Clear the ignore flag (called automatically after use, but can be called explicitly).
    public static void clearIgnoreCompositionChange() {
        IGNORE_NEXT.set(false);
    }

    // called from mixin
    public static void onSetBlock(Level level, BlockPos pos, BlockState newState) {
        if (level.isClientSide)
            return;

        // Fast check: if the flag is set, skip immediately (no StackWalker, no block state lookups)
        if (IGNORE_NEXT.get()) {
            IGNORE_NEXT.set(false);
            return;
        }

        BlockState oldState = level.getBlockState(pos);

        boolean wasH2O = isWaterSourceOrIce(oldState);
        boolean isH2O = isWaterSourceOrIce(newState);
        if (wasH2O == isH2O)
            return;

        if (isWaterSource(newState)) {
            if (!oldState.getBlock().equals(Blocks.WATER) && oldState.getFluidState().is(Fluids.WATER))
                // old state was no water but had a water fluid state (kelp for example)
                // this should not contribute to composition
                return;
            API.addLiquidInBuckets(level.dimension().location(), GasRegistry.water, 1);
        } else if (isIce(newState)) {
            API.addSurfaceIceInBlocks(level.dimension().location(), GasRegistry.water, 1);
        } else if (!isH2O) {
            if (isIce(oldState)) {
                API.addSurfaceIceInBlocks(level.dimension().location(), GasRegistry.water, -1);
            }
            if (isWaterSource(oldState) && newState.isAir()) {
                // only remove water from composition when it was replaced with air
                // so it ignores kelp growing or placing blocks in water
                API.addLiquidInBuckets(level.dimension().location(), GasRegistry.water, -1);
            }
        }
    }

    private static boolean isWaterSource(BlockState state) {
        return state.is(Blocks.WATER) && state.getFluidState().isSource();
    }

    private static boolean isIce(BlockState state) {
        return state.is(Blocks.ICE);
    }

    private static boolean isWaterSourceOrIce(BlockState state) {
        return isWaterSource(state) || isIce(state);
    }

}
