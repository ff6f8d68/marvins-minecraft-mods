package advRocketry.mixins;

import advRocketry.Dimension.WaterCompositionTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.FlowingFluid;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts vanilla FlowingFluid water spreading to avoid triggering
 * composition tracking (which used StackWalker.walk() on every setBlock).
 * Water source creation and flowing water updates should not affect
 * planet composition — only player-placed/removed water blocks should.
 */
@Mixin(FlowingFluid.class)
public abstract class FlowingFluidMixin {

    @Inject(method = "spreadTo", at = @At("HEAD"))
    private void onSpreadTo(LevelAccessor level, BlockPos pos, BlockState blockState, Direction direction, FluidState fluidState, CallbackInfo ci) {
        WaterCompositionTracker.setIgnoreNextCompositionChange();
    }
}
