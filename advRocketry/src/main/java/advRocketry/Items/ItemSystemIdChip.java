package advRocketry.Items;

import advRocketry.Dimension.Dimension;
import advRocketry.Dimension.DimensionManager;
import advRocketry.Utils.ItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * System ID Chip - stores a star system (parent dimension) ResourceLocation.
 * When inserted into a warp controller, it grants access to warp to any planet
 * in that star system without needing a galaxy database.
 */
public class ItemSystemIdChip extends Item {
    public ItemSystemIdChip() {
        super(new Properties());
    }

    public static void setSystemDimension(ResourceLocation dimensionId, ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        tag.putString("systemId", dimensionId.toString());
        ItemUtils.setTag(stack, tag);
    }

    public static ResourceLocation getSystemDimension(ItemStack stack) {
        CompoundTag tag = ItemUtils.getStacktagOrEmpty(stack);
        if (tag.contains("systemId"))
            return ResourceLocation.parse(tag.getString("systemId"));
        else return null;
    }

    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        Dimension system = DimensionManager.INSTANCE_CLIENT.get(getSystemDimension(stack));
        if (system != null) {
            tooltipComponents.add(
                    Component.literal("System: " + system.getName())
                            .withStyle(ChatFormatting.GRAY)
            );
            // Count planets in this system
            int planetCount = 0;
            for (Dimension d : DimensionManager.INSTANCE_CLIENT.dimensions.values()) {
                if (d instanceof advRocketry.Dimension.PlanetDimension p) {
                    if (p.getParentDimensionId() != null && p.getParentDimensionId().equals(system.getDimensionId())) {
                        planetCount++;
                    }
                }
            }
            if (planetCount > 0) {
                tooltipComponents.add(
                        Component.literal(planetCount + " planets in system")
                                .withStyle(ChatFormatting.DARK_GRAY)
                );
            }
        } else {
            tooltipComponents.add(
                    Component.literal("Empty - use Observatory to write")
                            .withStyle(ChatFormatting.DARK_RED)
            );
        }
    }
}
