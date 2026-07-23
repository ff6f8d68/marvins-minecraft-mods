package advRocketry.BlockEntities;

import ARLib.utils.BlockEntityBattery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

import static advRocketry.Registry.BlockEntities.ENTITY_CREATIVE_ENERGY_BLOCK;

public class EntityCreativeEnergyBlock extends BlockEntity {

    public BlockEntityBattery battery;

    public EntityCreativeEnergyBlock(BlockPos pos, BlockState blockState) {
        super(ENTITY_CREATIVE_ENERGY_BLOCK.get(), pos, blockState);
        battery = new BlockEntityBattery(this, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);
    }

    public static <T extends BlockEntity> void tick(Level level, BlockPos blockPos, BlockState blockState, T t) {
        ((EntityCreativeEnergyBlock) t).tick();
    }

    public void tick() {
        if (!level.isClientSide) {
            battery.setEnergy(Integer.MAX_VALUE);

            for (Direction dir : Direction.values()) {
                if (battery.getEnergyStored() == 0) break;

                IEnergyStorage neighbor = level.getCapability(Capabilities.EnergyStorage.BLOCK,
                        getBlockPos().relative(dir), dir.getOpposite());

                if (neighbor != null && neighbor.canReceive()) {
                    int received = neighbor.receiveEnergy(Integer.MAX_VALUE, false);
                    // battery is infinite, no need to extract
                }
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
    }
}
