package advRocketry.Blocks;

import advRocketry.BlockEntities.EntityPlanetAutoDiscoverer;
import advRocketry.Registry.BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import static advRocketry.Registry.BlockEntities.ENTITY_PLANET_AUTO_DISCOVERER;

public class PlanetAutoDiscoverer extends Block implements EntityBlock {
    public PlanetAutoDiscoverer() {
        super(Properties.of().destroyTime(0.5f));
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return ENTITY_PLANET_AUTO_DISCOVERER.get().create(blockPos, blockState);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockEntity b = level.getBlockEntity(pos);
        if (b instanceof EntityPlanetAutoDiscoverer h)
            h.openGui();
        return InteractionResult.SUCCESS_NO_ITEM_USED;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level.getBlockEntity(pos) instanceof EntityPlanetAutoDiscoverer discoverer) {
            discoverer.popInventory();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return EntityPlanetAutoDiscoverer::tick;
    }
}
