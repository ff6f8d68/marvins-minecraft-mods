package advRocketry.Dimension;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class StarCache {

    public int MAX_LIGHT_SOURCES = 4;
    LinkedHashMap<ResourceLocation, Double> significantLightSourcesCache = new LinkedHashMap<>();

    private List<Dimension> cachedDimValues = null;
    private int lastSeenVersion = -1;
    private int scanIndex = 0;

    // Number of dimensions to evaluate per tick. Fast enough for the cache to fill
    // within a few seconds even with 10 000+ NASA dimensions, but not so aggressive
    // that it causes lag spikes.
    private static final int DIMS_PER_TICK = 64;

    // On the client, do a full scan on first call so render has light data immediately.
    private boolean clientFullScanDone = false;

    // updates the cached light sources that are considered for lighting calculations
    // for simplicity, only self emitted light is considered. if a moon reflects a lot of light, this would be ignored.
    public void updateSignificantLightSourcesCache(Dimension myDimension) {

        int currentVersion = myDimension.dimensionManager.dimensionsVersion;
        if (cachedDimValues == null || lastSeenVersion != currentVersion) {
            cachedDimValues = new ArrayList<>(myDimension.dimensionManager.dimensions.values());
            lastSeenVersion = currentVersion;
            // Dimensions changed (new sync packets arrived). Reset the full-scan flag
            // so the next call does a complete scan with the updated dimension set.
            // Without this, the first scan runs before all dimensions are synced and
            // never re-scans fully — leaving StarCache empty for non-player dimensions.
            clientFullScanDone = false;
            // Clamp but do NOT reset — resetting during client sync (where each dimension
            // packet bumps the version) would prevent the scan from ever progressing.
            if (scanIndex >= cachedDimValues.size()) {
                scanIndex = 0;
            }

            // Prune any cached entries for dimensions that no longer exist
            Iterator<ResourceLocation> it = significantLightSourcesCache.keySet().iterator();
            while (it.hasNext()) {
                ResourceLocation id = it.next();
                if (!myDimension.dimensionManager.dimensions.containsKey(id)) {
                    it.remove();
                }
            }
        }

        if (cachedDimValues.isEmpty()) return;

        int myId = myDimension.getDimensionId().hashCode();

        // On client: first call does a full scan so render has light data immediately.
        // Without this, non-player dimensions never tick their StarCache on the client,
        // leaving renderPlanet/renderRingSystem with LightCount=0.
        int batchSize = DIMS_PER_TICK;
        if (!myDimension.isClientSide || clientFullScanDone) {
            // normal incremental scan
        } else {
            batchSize = cachedDimValues.size();
            clientFullScanDone = true;
        }

        // Process multiple dimensions per tick so the cache fills quickly.
        for (int step = 0; step < batchSize; step++) {
            if (scanIndex >= cachedDimValues.size()) {
                scanIndex = 0;
            }

            Dimension otherDimension = cachedDimValues.get(scanIndex);
            scanIndex++;

            ResourceLocation id = otherDimension.getDimensionId();

            // skip if it is my id
            if (id.hashCode() == myId && id.equals(myDimension.getDimensionId())) {
                continue;
            }

            // Skip if it's already in the top list
            if (significantLightSourcesCache.containsKey(id)) {
                continue;
            }

            // skip if no color is emitted from other dimension
            double radiationIntensity = otherDimension.getRadiationIntensity();
            if (radiationIntensity <= 0) {
                continue;
            }

            Vec3 myPos = myDimension.getPosition(0);
            Vec3 targetPosition = otherDimension.getPosition(0);
            double distance = myPos.distanceTo(targetPosition);
            double brightness = radiationIntensity / (distance * distance);

            // If we still have room, just add it
            if (significantLightSourcesCache.size() < MAX_LIGHT_SOURCES) {
                significantLightSourcesCache.put(id, brightness);
            } else {
                // Find the dimmest currently stored and maybe replace it
                ResourceLocation weakestId = null;
                double weakestBrightness = Double.MAX_VALUE;

                for (Map.Entry<ResourceLocation, Double> entry : significantLightSourcesCache.entrySet()) {
                    if (entry.getValue() < weakestBrightness) {
                        weakestBrightness = entry.getValue();
                        weakestId = entry.getKey();
                    }
                }

                // Replace if the new one is brighter
                if (brightness > weakestBrightness && weakestId != null) {
                    significantLightSourcesCache.remove(weakestId);
                    significantLightSourcesCache.put(id, brightness);
                }
            }
        }
    }
}
