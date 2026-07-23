package advRocketry.Dimension;

import advRocketry.Config;
import advRocketry.Utils.CelestialUtils;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;

public class PlanetRenderCache {
    /// Depth sorts planets / stars for correct rendering without depth buffer problems
    /// Culls away distant planets so we dont waste draw calls on planets we can not see

    public static final PlanetRenderCache INSTANCE = new PlanetRenderCache();

    // The list we can sort by index
    protected final ArrayList<PlanetDimension> allSortedPlanets = new ArrayList<>();

    // A persistent set for O(1) contains checks (should stay in sync with the sorted list)
    protected final HashSet<PlanetDimension> knownPlanetsSet = new HashSet<>();

    // The final list handed to the renderer
    protected final ArrayList<PlanetDimension> visiblePlanets = new ArrayList<>();

    public ArrayList<PlanetDimension> getPlanetsToRenderInSky() {
        return visiblePlanets;
    }

    public void clearCache() {
        allSortedPlanets.clear();
        knownPlanetsSet.clear();
        visiblePlanets.clear();
    }

    public void updatePlanetsToRenderInSky(Vec3 myDimensionPosition) {

        // 1. Clean up removed dimensions using an iterator to keep both collections synced
        Iterator<PlanetDimension> it = allSortedPlanets.iterator();
        while (it.hasNext()) {
            PlanetDimension dim = it.next();
            if (!DimensionManager.INSTANCE_CLIENT.dimensions.containsKey(dim.getDimensionId())) {
                it.remove();
                knownPlanetsSet.remove(dim);
            }
        }

        // 2. Add new dimensions (HashSet.add returns true only if the item wasn't already there)
        for (Dimension i : DimensionManager.INSTANCE_CLIENT.dimensions.values()) {
            if (i instanceof PlanetDimension p) {
                if (knownPlanetsSet.add(p)) {
                    allSortedPlanets.add(p);
                }
            }
        }

        // 3. Full sort by squared distance ascending (closest first) so the cap
        //    removes the farthest bodies instead of the closest ones
        allSortedPlanets.sort(Comparator.comparingDouble(
                (PlanetDimension p) -> p.getPosition(0).distanceToSqr(myDimensionPosition)
        ));

        // 4. Build the visible list with aggressive culling
        visiblePlanets.clear();
        double minApparentSize = 0.002;
        double cullThreshold = minApparentSize / 8.0;
        int maxBodies = Config.INSTANCE.max_Rendered_Bodies_In_Sky;
        int starCount = 0;
        int maxStars = Math.max(50, maxBodies / 4); // cap stars to ~25% of budget

        for (PlanetDimension dim : allSortedPlanets) {
            // Never cull stars within reason, but do cap star count
            if (dim.isStar()) {
                starCount++;
                if (starCount > maxStars) continue;
                visiblePlanets.add(dim);
                continue;
            }

            double dist = dim.getPosition(0).distanceTo(myDimensionPosition) * CelestialUtils.ASTRONOMICAL_UNIT;
            if(dist < 0.000001){
                visiblePlanets.add(dim);
                continue;
            }

            // the scale used in SkyRenderer
            double geometryScale = CelestialUtils.fromEarthRadius(dim.getEarthRadiusMultiplier()) * Config.INSTANCE.planet_Render_Scale_Multiplier;

            double apparentSizeRatio = geometryScale / dist;

            // Only add to the render list if it is close enough to be seen
            if (apparentSizeRatio >= cullThreshold) {
                visiblePlanets.add(dim);
            }
        }

        // 5. Cap total rendered bodies to prevent GPU lag with thousands of NASA stars
        if (maxBodies > 0 && visiblePlanets.size() > maxBodies) {
            visiblePlanets.subList(maxBodies, visiblePlanets.size()).clear();
        }

        // 6. Reverse for back-to-front render order (farthest first)
        Collections.reverse(visiblePlanets);
    }
}