package advRocketry.BlockEntities;

import ARLib.gui.GuiHandlerBlockEntity;
import ARLib.gui.modules.guiModuleButton;
import ARLib.gui.modules.guiModuleItemHandlerSlot;
import ARLib.gui.modules.guiModuleItemStackRender;
import ARLib.gui.modules.guiModuleText;
import ARLib.network.PacketBlockEntity;
import advRocketry.Data.DataTypes;
import advRocketry.Dimension.Dimension;
import advRocketry.Dimension.DimensionManager;
import advRocketry.Dimension.PlanetDimension;
import advRocketry.Items.ItemGalaxyDatabase;
import advRocketry.Items.ItemPlanetIdChip;
import advRocketry.Items.ItemSystemIdChip;
import advRocketry.Registry.Items;
import advRocketry.Render.starmap.SpaceMapScreen;
import advRocketry.Utils.ClientUtils;
import advRocketry.Utils.ItemUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

import static ARLib.gui.modules.guiModuleButton.BuiltinButtons.*;
import static advRocketry.Registry.BlockEntities.ENTITY_PLANET_AUTO_DISCOVERER;

public class EntityPlanetAutoDiscoverer extends BlockEntity implements ARLib.network.INetworkTagReceiver {

    public static final int CHIP_SLOT = 0;
    public static final int GALAXY_DATABASE_SLOT = 1;

    public GuiHandlerBlockEntity guiHandler;
    public ItemStackHandler inventory;
    public guiModuleItemHandlerSlot chipSlot;
    public guiModuleItemHandlerSlot databaseSlot;
    public guiModuleText statusText;
    public ResourceLocation selectedId = null;
    // For planet chip two-step flow: tracks the system selected in step 1
    public ResourceLocation pendingSystemId = null;

    public EntityPlanetAutoDiscoverer(BlockPos pos, BlockState blockState) {
        super(ENTITY_PLANET_AUTO_DISCOVERER.get(), pos, blockState);
        guiHandler = new GuiHandlerBlockEntity(this);

        inventory = new ItemStackHandler(2) {
            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                if (slot == CHIP_SLOT)
                    return stack.getItem() instanceof ItemPlanetIdChip || stack.getItem() instanceof ItemSystemIdChip;
                if (slot == GALAXY_DATABASE_SLOT)
                    return stack.getItem() instanceof ItemGalaxyDatabase;
                return false;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            public void onContentsChanged(int slot) {
                EntityPlanetAutoDiscoverer.this.setChanged();
            }
        };

        chipSlot = new guiModuleItemHandlerSlot(0, inventory, CHIP_SLOT, 1, 0, guiHandler, 20, 50);
        guiHandler.modules.add(chipSlot);

        databaseSlot = new guiModuleItemHandlerSlot(1, inventory, GALAXY_DATABASE_SLOT, 1, 0, guiHandler, 130, 50);
        guiHandler.modules.add(databaseSlot);

        guiHandler.modules.add(new guiModuleItemStackRender(2, new ItemStack(Items.ITEM_PLANET_ID_CHIP.get()), 1, guiHandler, 20, 30));
        guiHandler.modules.add(new guiModuleItemStackRender(3, new ItemStack(Items.ITEM_GALAXY_DATABASE.get()), 1, guiHandler, 130, 30));

        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) {
            guiModuleButton selectBtn = new guiModuleButton(100, "SELECT", guiHandler, 40, 10, 90, 15, BTN_BLACK, BTN_W, BTN_H) {
                public void onButtonClicked() {
                    Minecraft.getInstance().player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.4f, 2.0f);
                    openSpaceMap();
                }
            };
            selectBtn.color = 0xffffffff;
            guiHandler.modules.add(selectBtn);
        }

        statusText = new guiModuleText(50, "Place chip + database,\nthen press SELECT", guiHandler, 10, 75, 0xff000000, false);
        guiHandler.modules.add(statusText);

        guiModuleButton discoverBtn = new guiModuleButton(200, "DISCOVER", guiHandler, 10, 115, 80, 15, BTN_BLACK, BTN_W, BTN_H) {
            @Override
            public void onButtonClicked() {
                Minecraft.getInstance().player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
                CompoundTag info = new CompoundTag();
                info.putString("discover", "now");
                PacketDistributor.sendToServer(PacketBlockEntity.getBlockEntityPacket(EntityPlanetAutoDiscoverer.this, info));
            }
        };
        guiHandler.modules.add(discoverBtn);

        guiModuleButton discoverAllBtn = new guiModuleButton(201, "DISCOVER ALL", guiHandler, 95, 115, 80, 15, BTN_BLACK, BTN_W, BTN_H) {
            @Override
            public void onButtonClicked() {
                Minecraft.getInstance().player.playSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.5f, 0.8f);
                CompoundTag info = new CompoundTag();
                info.putString("discoverAll", "now");
                PacketDistributor.sendToServer(PacketBlockEntity.getBlockEntityPacket(EntityPlanetAutoDiscoverer.this, info));
            }
        };
        guiHandler.modules.add(discoverAllBtn);

        guiHandler.modules.addAll(ARLib.gui.modules.guiModulePlayerInventorySlot.makePlayerHotbarModules(7, 140, 10000, 0, 1, guiHandler));
    }

    private boolean isPlanetChipInSlot() {
        ItemStack stack = chipSlot.stack;
        return stack != null && stack.getItem() instanceof ItemPlanetIdChip;
    }

    private boolean isSystemChipInSlot() {
        ItemStack stack = chipSlot.stack;
        return stack != null && stack.getItem() instanceof ItemSystemIdChip;
    }

    private void openSpaceMap() {
        boolean planetMode = isPlanetChipInSlot();
        boolean systemMode = isSystemChipInSlot();
        if (!planetMode && !systemMode) return;

        Minecraft.getInstance().setScreen(
                new SpaceMapScreen() {
                    @Override
                    public void init() {
                        super.init();
                        // If planet chip and we already picked a system, zoom into it
                        if (planetMode && pendingSystemId != null) {
                            super.focusPlanet(pendingSystemId);
                        } else if (selectedId != null) {
                            super.focusPlanet(selectedId);
                        }
                    }

                    @Override
                    public void tick() {
                        super.tick();
                        EntityPlanetAutoDiscoverer.this.guiHandler.onGuiClientTick(ClientUtils.getSinglePlayer());
                    }

                    @Override
                    public void onClose() {
                        super.onClose();
                        openGui();
                    }

                    @Override
                    public void interact(ResourceLocation dimensionId) {
                        if (systemMode) {
                            // System chip: find the star for the clicked body
                            ResourceLocation systemId = findSystemForDimension(dimensionId);
                            if (systemId != null) {
                                CompoundTag info = new CompoundTag();
                                info.putString("selectId", systemId.toString());
                                PacketDistributor.sendToServer(PacketBlockEntity.getBlockEntityPacket(EntityPlanetAutoDiscoverer.this, info));
                                openGui();
                            }
                        } else if (planetMode) {
                            if (pendingSystemId == null) {
                                // Step 1: user clicked a star — zoom into its system
                                ResourceLocation systemId = findSystemForDimension(dimensionId);
                                if (systemId != null) {
                                    pendingSystemId = systemId;
                                    // Defer to next tick to avoid re-entrant setScreen (onClose → openGui)
                                    Minecraft.getInstance().tell(() -> openSpaceMap());
                                }
                            } else {
                                // Step 2: user clicked within the map
                                Dimension dim = DimensionManager.INSTANCE_CLIENT.get(dimensionId);
                                if (dim instanceof PlanetDimension planet) {
                                    if (planet.isStar() && !pendingSystemId.equals(dimensionId)) {
                                        // Clicked a different star — reset and zoom into new system
                                        pendingSystemId = dimensionId;
                                        Minecraft.getInstance().tell(() -> openSpaceMap());
                                    } else {
                                        ResourceLocation parentId = planet.getParentDimensionId();
                                        if (pendingSystemId.equals(parentId) || pendingSystemId.equals(dimensionId)) {
                                            // Valid: either a planet in the system, or the star itself
                                            CompoundTag info = new CompoundTag();
                                            info.putString("selectId", dimensionId.toString());
                                            if (planet.isStar()) {
                                                info.putBoolean("isStarSelection", true);
                                            }
                                            PacketDistributor.sendToServer(PacketBlockEntity.getBlockEntityPacket(EntityPlanetAutoDiscoverer.this, info));
                                            pendingSystemId = null;
                                            openGui();
                                        }
                                    }
                                }
                            }
                        }
                    }

                    @Override
                    public String getInteractText(ResourceLocation dimensionId) {
                        if (planetMode && pendingSystemId != null) {
                            return "pick planet";
                        }
                        return "select";
                    }

                    @Override
                    public boolean shouldRenderPlanet(ResourceLocation dimensionId) {
                        return true;
                    }

                    private ResourceLocation findSystemForDimension(ResourceLocation dimId) {
                        Dimension dim = DimensionManager.INSTANCE_CLIENT.get(dimId);
                        if (dim == null) return null;
                        if (dim instanceof PlanetDimension planet) {
                            if (planet.isStar()) return dimId;
                            ResourceLocation parentId = planet.getParentDimensionId();
                            if (parentId != null) return parentId;
                        }
                        return dimId;
                    }
                }
        );
    }

    public static <T extends BlockEntity> void tick(Level level, BlockPos blockPos, BlockState blockState, T t) {
        ((EntityPlanetAutoDiscoverer) t).tick();
    }

    public void tick() {
        if (!level.isClientSide) {
            guiHandler.serverTick();

            if (!guiHandler.playersTrackingGui.isEmpty()) {
                updateStatusText();
            }
        }
    }

    private void updateStatusText() {
        ItemStack chipStack = inventory.getStackInSlot(CHIP_SLOT);
        boolean chipIsSystem = chipStack.getItem() instanceof ItemSystemIdChip;
        boolean chipIsPlanet = chipStack.getItem() instanceof ItemPlanetIdChip;

        if (!chipIsSystem && !chipIsPlanet) {
            statusText.setTextAndSync("Place chip + database,\nthen press SELECT");
            return;
        }

        if (chipIsSystem) {
            ResourceLocation sysId = ItemSystemIdChip.getSystemDimension(chipStack);
            if (sysId != null) {
                Dimension d = DimensionManager.INSTANCE_SERVER.get(sysId);
                if (d != null) {
                    statusText.setTextAndSync("System chip: " + d.getName());
                    return;
                }
            }
            statusText.setTextAndSync("System chip (empty)\nPress SELECT to pick");
            return;
        }

        if (chipIsPlanet) {
            ResourceLocation planetId = ItemPlanetIdChip.getSelectedDimension(chipStack);
            if (planetId != null) {
                Dimension d = DimensionManager.INSTANCE_SERVER.get(planetId);
                if (d != null) {
                    String parentName = "";
                    if (d instanceof PlanetDimension planet) {
                        Dimension parent = DimensionManager.INSTANCE_SERVER.get(planet.getParentDimensionId());
                        if (parent != null) parentName = " in " + parent.getName();
                    }
                    statusText.setTextAndSync("Planet chip: " + d.getName() + parentName);
                    return;
                }
            }
            statusText.setTextAndSync("Planet chip (empty)\nPress SELECT to pick");
        }
    }

    public void openGui() {
        if (level.isClientSide)
            guiHandler.openGui(176, 165, true);
    }

    public void popInventory() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            Block.popResource(level, getBlockPos(), inventory.getStackInSlot(i));
            inventory.setStackInSlot(i, ItemStack.EMPTY);
        }
        setChanged();
    }

    @Override
    public void readServer(CompoundTag compoundTag, ServerPlayer serverPlayer) {
        guiHandler.readServer(compoundTag);

        if (compoundTag.contains("selectId")) {
            ResourceLocation dimId = ResourceLocation.tryParse(compoundTag.getString("selectId"));
            if (dimId != null) {
                Dimension d = DimensionManager.INSTANCE_SERVER.get(dimId);
                if (d != null) {
                    ItemStack chipStack = inventory.getStackInSlot(CHIP_SLOT);

                    if (chipStack.getItem() instanceof ItemSystemIdChip) {
                        // System chip: write the system (star) dimension to the chip
                        ResourceLocation systemId = findSystemServer(dimId);
                        if (systemId != null) {
                            ItemSystemIdChip.setSystemDimension(systemId, chipStack);
                            notifyPlayer("System written to chip: " + d.getName());
                        }
                    } else if (chipStack.getItem() instanceof ItemPlanetIdChip) {
                        if (compoundTag.contains("isStarSelection")) {
                            // Star selected in planet mode — write system to chip as a star reference
                            // Actually for planet chip we need to write a planet, not a star
                            // This case shouldn't happen in normal flow, but handle gracefully
                            notifyPlayer("Please select a planet, not a star");
                        } else {
                            // Planet selected: write planet to chip
                            ItemPlanetIdChip.setSelectedDimension(dimId, chipStack, true);
                            notifyPlayer("Planet written to chip: " + d.getName());
                        }
                    }

                    selectedId = dimId;
                    setChanged();
                    sendUpdatePacket(serverPlayer);
                }
            }
        }

        if (compoundTag.contains("discover")) {
            discoverPlanet(serverPlayer);
        }

        if (compoundTag.contains("discoverAll")) {
            discoverAllPlanets(serverPlayer);
        }
    }

    private ResourceLocation findSystemServer(ResourceLocation dimId) {
        Dimension dim = DimensionManager.INSTANCE_SERVER.get(dimId);
        if (dim == null) return null;
        if (dim instanceof PlanetDimension planet) {
            if (planet.isStar()) return dimId;
            ResourceLocation parentId = planet.getParentDimensionId();
            if (parentId != null) {
                Dimension parent = DimensionManager.INSTANCE_SERVER.get(parentId);
                if (parent instanceof PlanetDimension parentPlanet && parentPlanet.isStar()) {
                    return parentId;
                }
            }
        }
        return dimId;
    }

    private void discoverPlanet(ServerPlayer serverPlayer) {
        ItemStack chipStack = inventory.getStackInSlot(CHIP_SLOT);
        ItemStack galaxyDb = inventory.getStackInSlot(GALAXY_DATABASE_SLOT);

        if (chipStack.getItem() instanceof ItemSystemIdChip) {
            // System chip: discover all planets in the system
            ResourceLocation systemId = ItemSystemIdChip.getSystemDimension(chipStack);
            if (systemId == null) return;
            Dimension d = DimensionManager.INSTANCE_SERVER.get(systemId);
            if (d == null) return;
            if (!(galaxyDb.getItem() instanceof ItemGalaxyDatabase)) return;

            discoverSystemPlanets(galaxyDb, d);
            notifyPlayer("System discovered: " + d.getName());

        } else if (chipStack.getItem() instanceof ItemPlanetIdChip) {
            // Planet chip: discover the selected planet
            ResourceLocation planetId = ItemPlanetIdChip.getSelectedDimension(chipStack);
            if (planetId == null) return;
            Dimension d = DimensionManager.INSTANCE_SERVER.get(planetId);
            if (d == null) return;
            if (!(galaxyDb.getItem() instanceof ItemGalaxyDatabase)) return;

            if (d instanceof PlanetDimension planet) {
                int maxData = planet.getDataRequiredForUnlock();
                ItemGalaxyDatabase.PlanetInfo info = new ItemGalaxyDatabase.PlanetInfo();
                info.put(DataTypes.distance, maxData);
                info.put(DataTypes.mass, maxData);
                info.put(DataTypes.composition, maxData);
                ItemGalaxyDatabase.setPlanetInfo(galaxyDb, planet, info);
                notifyPlayer("Planet discovered: " + planet.getName());
            }

        } else {
            notifyPlayer("Place a chip first");
        }

        setChanged();
        sendUpdatePacket(serverPlayer);
    }

    private void discoverAllPlanets(ServerPlayer serverPlayer) {
        ItemStack galaxyDb = inventory.getStackInSlot(GALAXY_DATABASE_SLOT);
        if (!(galaxyDb.getItem() instanceof ItemGalaxyDatabase)) return;

        int count = 0;
        for (Dimension dim : DimensionManager.INSTANCE_SERVER.dimensions.values()) {
            if (dim instanceof PlanetDimension planet) {
                int maxData = planet.getDataRequiredForUnlock();
                ItemGalaxyDatabase.PlanetInfo info = new ItemGalaxyDatabase.PlanetInfo();
                info.put(DataTypes.distance, maxData);
                info.put(DataTypes.mass, maxData);
                info.put(DataTypes.composition, maxData);
                ItemGalaxyDatabase.setPlanetInfo(galaxyDb, planet, info);
                count++;
            }
        }

        notifyPlayer("Galaxy discovered! " + count + " planets unlocked.");

        setChanged();
        sendUpdatePacket(serverPlayer);
    }

    private void discoverSystemPlanets(ItemStack galaxyDb, Dimension system) {
        ResourceLocation systemId = system.getDimensionId();
        for (Dimension dim : DimensionManager.INSTANCE_SERVER.dimensions.values()) {
            if (dim instanceof PlanetDimension planet) {
                if (systemId.equals(planet.getParentDimensionId())) {
                    int maxData = planet.getDataRequiredForUnlock();
                    ItemGalaxyDatabase.PlanetInfo info = new ItemGalaxyDatabase.PlanetInfo();
                    info.put(DataTypes.distance, maxData);
                    info.put(DataTypes.mass, maxData);
                    info.put(DataTypes.composition, maxData);
                    ItemGalaxyDatabase.setPlanetInfo(galaxyDb, planet, info);
                }
            }
        }
    }

    private void notifyPlayer(String message) {
        for (Player player : level.players()) {
            if (player.position().distanceTo(getBlockPos().getCenter()) < 16) {
                player.sendSystemMessage(Component.literal(message));
            }
        }
    }

    @Override
    public void readClient(CompoundTag compoundTag) {
        guiHandler.readClient(compoundTag);
        if (compoundTag.contains("selectedId")) {
            selectedId = ResourceLocation.tryParse(compoundTag.getString("selectedId"));
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // Guard: if galaxy database has too much NBT, strip oldest entries before saving.
        // Each planet entry is ~65 bytes, so 20000 entries ≈1.3 MB — well under 2 MB limit.
        ItemStack galaxyDb = inventory.getStackInSlot(GALAXY_DATABASE_SLOT);
        if (galaxyDb.getItem() instanceof ItemGalaxyDatabase) {
            CompoundTag dbTag = ItemUtils.getStacktagOrEmpty(galaxyDb);
            if (dbTag.sizeInBytes() > 1500000) {
                List<String> keys = new ArrayList<>(dbTag.getAllKeys());
                int toRemove = keys.size() - 15000;
                for (int i = 0; i < toRemove; i++) {
                    dbTag.remove(keys.get(i));
                }
            }
        }
        tag.put("inventory", inventory.serializeNBT(registries));
        if (selectedId != null) {
            tag.putString("selectedId", selectedId.toString());
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        try {
            inventory.deserializeNBT(registries, tag.getCompound("inventory"));
        } catch (Exception e) {
            inventory = new ItemStackHandler(2);
        }
        if (tag.contains("selectedId")) {
            selectedId = ResourceLocation.tryParse(tag.getString("selectedId"));
        }
    }

    public void sendUpdatePacket(ServerPlayer player) {
        CompoundTag tag = new CompoundTag();
        if (selectedId != null) {
            tag.putString("selectedId", selectedId.toString());
        }
        PacketDistributor.sendToPlayer(player, PacketBlockEntity.getBlockEntityPacket(this, tag));
    }
}
