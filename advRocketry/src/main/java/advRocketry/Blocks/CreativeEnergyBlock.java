package advRocketry.Blocks;

import advRocketry.BlockEntities.EntityCreativeEnergyBlock;
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

import static advRocketry.Registry.BlockEntities.ENTITY_CREATIVE_ENERGY_BLOCK;

public class CreativeEnergyBlock extends Block implements EntityBlock {
    public CreativeEnergyBlock() {
        super(Properties.of().strength(0.5f).noLootTable());
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos blockPos, BlockState blockState) {
        return ENTITY_CREATIVE_ENERGY_BLOCK.get().create(blockPos, blockState);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return EntityCreativeEnergyBlock::tick;
    }
}
