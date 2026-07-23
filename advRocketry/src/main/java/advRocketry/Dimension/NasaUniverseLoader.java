package advRocketry.Dimension;

import advRocketry.Config;
import advRocketry.Main;
import advRocketry.Registry.GasRegistry;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads the NASA exoplanet + Gaia star data from universe_data.json
 * and creates PlanetDimensionProperties for each star and planet.
 *
 * Stars are placed at their real 3D positions (converted from parsecs to AU).
 * Planets orbit their parent star via orbitalDistanceToParent.
 *
 * Texture ResourceLocations are assigned using a deterministic scheme based on
 * planet type, so ProceduralPlanetTextureGenerator can create them on the client.
 */
public class NasaUniverseLoader {

    public static final String UNIVERSE_DATA_PATH = "/data/adv_rocketry/universe_data.json";
    public static final String NASA_TEXTURE_NAMESPACE = "adv_rocketry";

    // Planet type constants (must match ProceduralPlanetTextureGenerator)
    public static final int TYPE_ROCKY = 0;
    public static final int TYPE_EARTH_LIKE = 1;
    public static final int TYPE_VENUS_LIKE = 2;
    public static final int TYPE_GAS_GIANT = 3;
    public static final int TYPE_ICE_GIANT = 4;
    public static final int TYPE_LAVA = 5;
    public static final int TYPE_ICE_WORLD = 6;

    // Fluid type constants
    public static final int FLUID_NONE = 0;
    public static final int FLUID_WATER = 1;
    public static final int FLUID_METHANE = 2;
    public static final int FLUID_LAVA = 3;

    /**
     * Load NASA universe data and return a list of DimensionProperties.
     * Called by DimensionManager during server start, before loading from disk.
     *
     * @param maxStars maximum number of star systems to load (0 = unlimited)
     * @return list of DimensionProperties for all stars and planets
     */
    public static List<DimensionProperties> loadUniverse(int maxStars) {
        String json;
        try {
            InputStream is = NasaUniverseLoader.class.getResourceAsStream(UNIVERSE_DATA_PATH);
            if (is == null) {
                System.out.println("[NasaUniverseLoader] universe_data.json not found in resources, skipping NASA universe.");
                return Collections.emptyList();
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            json = sb.toString();
            reader.close();
        } catch (Exception e) {
            System.err.println("[NasaUniverseLoader] Failed to read universe_data.json: " + e.getMessage());
            return Collections.emptyList();
        }

        Gson gson = new Gson();
        JsonObject root = gson.fromJson(json, JsonObject.class);
        JsonArray bodies = root.getAsJsonArray("bodies");

        if (bodies == null || bodies.size() == 0) {
            System.out.println("[NasaUniverseLoader] No bodies found in universe_data.json.");
            return Collections.emptyList();
        }

        List<DimensionProperties> result = new ArrayList<>();
        Map<String, ResourceLocation> hostnameToDimId = new HashMap<>();

        int starCount = 0;

        for (JsonElement elem : bodies) {
            JsonObject body = elem.getAsJsonObject();
            String type = body.has("type") ? body.get("type").getAsString() : "star";

            if ("star".equals(type)) {
                if (maxStars > 0 && starCount >= maxStars) break;

                List<DimensionProperties> starAndPlanets = parseStar(body, hostnameToDimId);
                result.addAll(starAndPlanets);
                starCount++;
            }
        }

        // Post-process: ensure at least starCount/60 systems have a planet with life
        int minLifeSystems = Math.max(1, starCount / 60);
        Set<ResourceLocation> systemsWithLife = new HashSet<>();
        for (DimensionProperties dp : result) {
            if (dp instanceof PlanetDimensionProperties pp && pp.canVisit) {
                if (pp.parentDimensionId != null) systemsWithLife.add(pp.parentDimensionId);
            }
        }
        System.out.println("[NasaUniverseLoader] " + systemsWithLife.size() + " systems have life-bearing planets (need " + minLifeSystems + ")");

        if (systemsWithLife.size() < minLifeSystems) {
            // Collect star IDs that don't have life yet
            List<DimensionProperties> stars = new ArrayList<>();
            for (DimensionProperties dp : result) {
                if (dp instanceof PlanetDimensionProperties pp && pp.radiationIntensity > 0) {
                    if (!systemsWithLife.contains(pp.dimensionId)) {
                        stars.add(pp);
                    }
                }
            }
            Collections.shuffle(stars, new Random(42));
            int needed = minLifeSystems - systemsWithLife.size();
            for (int i = 0; i < Math.min(needed, stars.size()); i++) {
                PlanetDimensionProperties starProps = (PlanetDimensionProperties) stars.get(i);
                ResourceLocation parentDimId = starProps.dimensionId;
                float starMass = starProps.gravitationalMultiplier / 200f;
                addLifeBearingPlanet(starProps, parentDimId, result);
                systemsWithLife.add(parentDimId);
            }
            System.out.println("[NasaUniverseLoader] Added " + Math.min(needed, stars.size()) + " life-bearing planets to reach minimum");
        }

        System.out.println("[NasaUniverseLoader] Loaded " + starCount + " star systems with " +
                (result.size() - starCount) + " planets from NASA universe data.");

        return result;
    }

    private static List<DimensionProperties> parseStar(JsonObject star, Map<String, ResourceLocation> hostnameToDimId) {
        List<DimensionProperties> result = new ArrayList<>();

        String hostname = star.get("name").getAsString();
        int temp = star.has("temperature") ? star.get("temperature").getAsInt() : 5778;
        float mass = star.has("mass") ? star.get("mass").getAsFloat() : 1.0f;
        float radius = star.has("radius") ? star.get("radius").getAsFloat() : 1.0f;
        float radiation = star.has("radiationIntensity") ? star.get("radiationIntensity").getAsFloat() : 1.0f;
        boolean isSol = star.has("isSol") && star.get("isSol").getAsBoolean();

        // Build dimension ID - use "sol" for our sun to avoid conflict with DefaultGalaxy
        ResourceLocation dimId;
        if (isSol) {
            // Sol is already defined in DefaultGalaxy, skip re-creating it
            // But we still need to register it so planets can reference it
            dimId = ResourceLocation.fromNamespaceAndPath("adv_rocketry", "sun");
            hostnameToDimId.put(hostname, dimId);
            return result; // DefaultGalaxy handles Sol
        }

        String safeName = hostname.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (safeName.isEmpty()) safeName = "unknown_star";
        // Truncate to keep ResourceLocation paths reasonable
        if (safeName.length() > 40) safeName = safeName.substring(0, 40);

        dimId = ResourceLocation.fromNamespaceAndPath("adv_rocketry", "star_" + safeName);
        hostnameToDimId.put(hostname, dimId);

        // Parse position
        JsonArray posArr = star.getAsJsonArray("position");
        double x = 0, y = 0, z = 0;
        if (posArr != null && posArr.size() >= 3) {
            x = posArr.get(0).getAsDouble();
            y = posArr.get(1).getAsDouble();
            z = posArr.get(2).getAsDouble();
        }

        // Parse emissive color
        Vector3f emissiveColor = new Vector3f(1f, 0.95f, 0.8f);
        JsonArray colorArr = star.getAsJsonArray("emissiveColor");
        if (colorArr != null && colorArr.size() >= 3) {
            emissiveColor = new Vector3f(
                    colorArr.get(0).getAsFloat(),
                    colorArr.get(1).getAsFloat(),
                    colorArr.get(2).getAsFloat()
            );
        }

        // Create star PlanetDimensionProperties
        PlanetDimensionProperties starProps = new PlanetDimensionProperties();
        starProps.name = hostname;
        starProps.dimensionId = dimId;
        starProps.gravitationalMultiplier = Math.max(100, mass * 200);
        starProps.earthRadiusMultiplier = Math.max(50, radius * 50);
        starProps.rotationAxis = new Vec3(0, 1, 0).normalize();
        starProps.position = new Vec3(x, y, z);
        starProps.radiationIntensity = radiation;
        starProps.emissiveLightColor = emissiveColor;
        starProps.emissiveTextureTintColor = new Vector3f(1, 1, 1).mul(Math.max(5f, radiation * 3f));
        // Stars are always known — they are visible in the sky, no discovery needed
        starProps.isKnown = true;
        starProps.textureSeed = hostname.hashCode();
        starProps.texture = getStarTextureResourceLocation(star);

        result.add(starProps);

        // Parse planets from JSON data
        JsonArray planetsArr = star.getAsJsonArray("planets");
        int planetCount = 0;
        if (planetsArr != null) {
            for (JsonElement pElem : planetsArr) {
                JsonObject pObj = pElem.getAsJsonObject();
                DimensionProperties planetProps = parsePlanet(pObj, dimId, hostname);
                if (planetProps != null) {
                    result.add(planetProps);
                    planetCount++;
                }
            }
        }

        // Generate imagined planets for stars that have no planet data
        if (planetCount == 0) {
            int imaginedCount = generateImaginedPlanetsForStar(starProps, dimId, hostname, temp, mass, result);
            System.out.println("[NasaUniverseLoader] Generated " + imaginedCount + " imagined planets for " + hostname);
        }

        return result;
    }

    private static DimensionProperties parsePlanet(JsonObject pObj, ResourceLocation parentDimId, String hostname) {
        String name = pObj.has("name") ? pObj.get("name").getAsString() : "Unknown";
        float semiAxis = pObj.has("semiMajorAxisAU") ? pObj.get("semiMajorAxisAU").getAsFloat() : 1.0f;
        float radiusEarth = pObj.has("radiusEarth") ? pObj.get("radiusEarth").getAsFloat() : 1.0f;
        float gravity = pObj.has("gravity") ? pObj.get("gravity").getAsFloat() : 1.0f;
        float eqTemp = pObj.has("eqTemp") ? pObj.get("eqTemp").getAsFloat() : 250f;
        int planetType = pObj.has("planetType") ? pObj.get("planetType").getAsInt() : TYPE_ROCKY;
        int fluid = pObj.has("fluid") ? pObj.get("fluid").getAsInt() : FLUID_NONE;
        int palette = pObj.has("palette") ? pObj.get("palette").getAsInt() : 0;
        float humidity = pObj.has("humidity") ? pObj.get("humidity").getAsFloat() : 0.1f;
        boolean canVisit = pObj.has("canVisit") && pObj.get("canVisit").getAsBoolean();
        String biomePreset = pObj.has("biomePreset") && !pObj.get("biomePreset").isJsonNull()
                ? pObj.get("biomePreset").getAsString() : null;
        int seed = pObj.has("seed") ? pObj.get("seed").getAsInt() : name.hashCode();
        float orbitalOffset = pObj.has("orbitalOffsetDeg") ? pObj.get("orbitalOffsetDeg").getAsFloat() : 0f;

        // Build safe dimension ID
        String safeName = name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (safeName.isEmpty()) safeName = "unknown_planet";
        if (safeName.length() > 40) safeName = safeName.substring(0, 40);

        ResourceLocation dimId = ResourceLocation.fromNamespaceAndPath("adv_rocketry", "nasa_" + safeName);

        // Create PlanetDimensionProperties
        PlanetDimensionProperties props = new PlanetDimensionProperties();
        props.name = name;
        props.dimensionId = dimId;
        props.parentDimensionId = parentDimId;
        props.dayTimeReference = parentDimId;  // Use parent star for day/night
        props.orbitalDistanceToParent = semiAxis;
        props.orbitalBaseOffsetDegrees = orbitalOffset;
        props.earthRadiusMultiplier = radiusEarth;
        props.gravitationalMultiplier = gravity;
        props.currentTemp = eqTemp;

        // Atmosphere
        JsonObject atmObj = pObj.has("atmosphere") ? pObj.getAsJsonObject("atmosphere") : null;
        if (atmObj != null) {
            parseAtmosphere(props, atmObj);
        }

        // Fluid and biome
        if (fluid == FLUID_WATER && canVisit) {
            // Water world
            props.customSeaFluid = ResourceLocation.parse("minecraft:water");
            props.customSeaFluidLevel = 62;
        } else if (fluid == FLUID_LAVA && canVisit) {
            props.customSeaFluid = ResourceLocation.parse("minecraft:lava");
            props.customSeaFluidLevel = 52;
        }

        // Biome preset and visitability
        props.canVisit = canVisit;
        if (biomePreset != null) {
            props.biomePreset = biomePreset;
        }

        // Gas mining for gas giants
        if (planetType == TYPE_GAS_GIANT || planetType == TYPE_ICE_GIANT) {
            props.canGasMine = true;
        }

        // Texture: procedural, generated on client
        props.textureSeed = seed;
        props.texture = getPlanetTextureResourceLocation(name, planetType, seed);

        // Visual properties based on planet type
        configureVisuals(props, planetType, eqTemp, palette, seed);

        return props;
    }

    private static void parseAtmosphere(PlanetDimensionProperties props, JsonObject atmObj) {
        // Map gas names to our GasRegistry IDs
        Map<String, String> gasNameMap = new HashMap<>();
        gasNameMap.put("hydrogen", GasRegistry.hydrogen);
        gasNameMap.put("oxygen", GasRegistry.oxygen);
        gasNameMap.put("nitrogen", GasRegistry.nitrogen);
        gasNameMap.put("methane", GasRegistry.methane);
        gasNameMap.put("co2", GasRegistry.co2);
        gasNameMap.put("water", GasRegistry.water);

        for (Map.Entry<String, JsonElement> entry : atmObj.entrySet()) {
            String gasName = entry.getKey();
            // Skip non-standard gases (e.g. "helium_proxy", "so2_proxy")
            String registryName = gasNameMap.get(gasName);
            if (registryName == null) continue;

            JsonObject gasObj = entry.getValue().getAsJsonObject();
            double inAtm = gasObj.has("in_atm") ? gasObj.get("in_atm").getAsDouble() : 0;
            double liquid = gasObj.has("liquid") ? gasObj.get("liquid").getAsDouble() : 0;
            double frozenSurface = gasObj.has("frozen_surface") ? gasObj.get("frozen_surface").getAsDouble() : 0;
            double frozenDeep = gasObj.has("frozen_deep") ? gasObj.get("frozen_deep").getAsDouble() : 0;

            props.atmosphereComposition.put(registryName,
                    new PlanetDimensionProperties.GasProperty(inAtm, liquid, frozenSurface, frozenDeep));
        }
    }

    private static void configureVisuals(PlanetDimensionProperties props, int planetType, float eqTemp, int palette, int seed) {
        Random rng = new Random(seed);

        switch (planetType) {
            case TYPE_GAS_GIANT:
                props.skyColor = new Vector3f(0.5f, 0.5f, 0.6f);
                props.fogColor = new Vector3f(0.6f, 0.55f, 0.5f);
                props.cloudColor = new Vector3f(0.8f, 0.7f, 0.5f);
                props.emissiveTextureTintColor = new Vector3f(0, 0, 0);
                props.reflectiveTextureTintColor = new Vector3f(1, 1, 1);
                props.skyDarken = 0.1f;
                break;

            case TYPE_ICE_GIANT:
                props.skyColor = new Vector3f(0.3f, 0.5f, 0.7f);
                props.fogColor = new Vector3f(0.4f, 0.6f, 0.8f);
                props.cloudColor = new Vector3f(0.7f, 0.8f, 0.9f);
                props.emissiveTextureTintColor = new Vector3f(0, 0, 0);
                props.reflectiveTextureTintColor = new Vector3f(0.9f, 1f, 1f);
                props.skyDarken = 0.05f;
                break;

            case TYPE_EARTH_LIKE:
                props.skyColor = new Vector3f(0.45f, 0.7f, 1f);
                props.fogColor = new Vector3f(0.8f, 0.9f, 1.1f);
                props.sunRiseColor = new Vector3f(3f, 1.6f, 0.2f);
                props.emissiveTextureTintColor = new Vector3f(0, 0, 0);
                props.reflectiveTextureTintColor = new Vector3f(1, 1, 1);
                break;

            case TYPE_VENUS_LIKE:
                props.skyColor = new Vector3f(0.8f, 0.7f, 0.4f);
                props.fogColor = new Vector3f(0.9f, 0.8f, 0.5f);
                props.cloudColor = new Vector3f(0.8f, 0.7f, 0.4f);
                props.emissiveTextureTintColor = new Vector3f(0, 0, 0);
                props.skyDarken = 0.2f;
                break;

            case TYPE_LAVA:
                props.skyColor = new Vector3f(0.5f, 0.3f, 0.2f);
                props.fogColor = new Vector3f(0.7f, 0.33f, 0.25f);
                props.cloudColor = new Vector3f(0.25f, 0.22f, 0.20f);
                props.cloudValueOverwrite = 0.7f;
                props.skyDarken = 0.5f;
                props.emissiveTextureTintColor = new Vector3f(1, 0.5f, 0.1f).mul(0.3f);
                props.baseEnergyGain = 0.08f;
                break;

            case TYPE_ICE_WORLD:
                props.skyColor = new Vector3f(0.7f, 0.8f, 0.9f);
                props.fogColor = new Vector3f(0.85f, 0.9f, 1.0f);
                props.emissiveTextureTintColor = new Vector3f(0, 0, 0);
                props.reflectiveTextureTintColor = new Vector3f(1.1f, 1.1f, 1.2f);
                break;

            case TYPE_ROCKY:
            default:
                props.skyColor = new Vector3f(0.6f, 0.6f, 0.7f);
                props.fogColor = new Vector3f(0.7f, 0.7f, 0.8f);
                props.emissiveTextureTintColor = new Vector3f(0, 0, 0);
                break;
        }

        // Randomize some visual properties based on seed
        props.orbitAxis = new Vec3(
                rng.nextDouble() * 0.3,
                1.0,
                rng.nextDouble() * 0.3
        ).normalize();

        props.rotationAxis = new Vec3(
                (rng.nextDouble() - 0.5) * 0.4,
                1.0,
                (rng.nextDouble() - 0.5) * 0.4
        ).normalize();

        // Target day length: random between 12000 and 48000 ticks
        props.targetDayLength = 12000 + rng.nextInt(36000);
    }

    /**
     * Generate imagined planets for a star that has no planet data in the NASA catalog.
     * Uses the star's properties (mass, temperature, radius) to create plausible planet configurations.
     *
     * @return number of imagined planets generated
     */
    private static int generateImaginedPlanetsForStar(
            PlanetDimensionProperties starProps,
            ResourceLocation parentDimId,
            String hostname,
            int temp,
            float starMass,
            List<DimensionProperties> result
    ) {
        Random rng = new Random(hostname.hashCode());
        int numPlanets = 1 + rng.nextInt(3); // 1-3 imagined planets

        // Star habitable zone estimate (rough AU range based on luminosity proxy)
        float luminosityProxy = starMass * starMass; // rough L ~ M^3.5, simplified
        float hzInner = (float) Math.sqrt(luminosityProxy) * 0.8f;
        float hzOuter = (float) Math.sqrt(luminosityProxy) * 1.5f;
        if (hzInner < 0.1f) hzInner = 0.1f;

        for (int i = 0; i < numPlanets; i++) {
            // Orbital distance: spread planets around the star, including some in habitable zone
            float semiMajorAxis = hzInner + rng.nextFloat() * (hzOuter - hzInner + 2.0f);
            semiMajorAxis = Math.max(0.05f, semiMajorAxis);

            // Determine planet type based on distance from star
            int planetType;
            int fluid;
            float radiusEarth;
            float gravity;
            float eqTemp;
            boolean canVisit = false;
            String biomePreset = null;

            if (semiMajorAxis < hzInner * 0.6f) {
                // Hot rocky / lava world
                planetType = TYPE_LAVA;
                fluid = FLUID_LAVA;
                radiusEarth = 0.5f + rng.nextFloat() * 1.0f;
                gravity = radiusEarth * (0.7f + rng.nextFloat() * 0.6f);
                eqTemp = 400 + rng.nextFloat() * 600;
            } else if (semiMajorAxis < hzInner) {
                // Venus-like
                planetType = TYPE_VENUS_LIKE;
                fluid = FLUID_NONE;
                radiusEarth = 0.7f + rng.nextFloat() * 0.8f;
                gravity = radiusEarth * (0.8f + rng.nextFloat() * 0.5f);
                eqTemp = 300 + rng.nextFloat() * 200;
            } else if (semiMajorAxis < hzOuter) {
                // Habitable zone — Earth-like or Venus-like
                float roll = rng.nextFloat();
                if (roll < 0.4f) {
                    planetType = TYPE_EARTH_LIKE;
                    fluid = FLUID_WATER;
                    canVisit = true;
                    biomePreset = "OVERWORLD";
                } else if (roll < 0.7f) {
                    planetType = TYPE_VENUS_LIKE;
                    fluid = FLUID_NONE;
                } else {
                    planetType = TYPE_ROCKY;
                    fluid = FLUID_NONE;
                }
                radiusEarth = 0.8f + rng.nextFloat() * 1.2f;
                gravity = radiusEarth * (0.8f + rng.nextFloat() * 0.5f);
                eqTemp = 200 + rng.nextFloat() * 150;
            } else if (semiMajorAxis < hzOuter * 3) {
                // Cold rocky / ice world
                planetType = TYPE_ICE_WORLD;
                fluid = FLUID_NONE;
                radiusEarth = 0.5f + rng.nextFloat() * 2.0f;
                gravity = radiusEarth * (0.6f + rng.nextFloat() * 0.8f);
                eqTemp = 100 + rng.nextFloat() * 150;
            } else if (semiMajorAxis < hzOuter * 8) {
                // Gas giant
                float roll = rng.nextFloat();
                if (roll < 0.6f) {
                    planetType = TYPE_GAS_GIANT;
                    fluid = FLUID_NONE;
                } else {
                    planetType = TYPE_ICE_GIANT;
                    fluid = FLUID_NONE;
                }
                radiusEarth = 5f + rng.nextFloat() * 10f;
                gravity = radiusEarth * (0.3f + rng.nextFloat() * 0.4f);
                eqTemp = 50 + rng.nextFloat() * 100;
                canVisit = false; // gas giants not visitable
            } else {
                // Far ice world
                planetType = TYPE_ICE_WORLD;
                fluid = FLUID_NONE;
                radiusEarth = 0.3f + rng.nextFloat() * 1.5f;
                gravity = radiusEarth * (0.5f + rng.nextFloat() * 0.5f);
                eqTemp = 30 + rng.nextFloat() * 80;
            }

            // Build planet name
            String planetName = hostname + " " + (char) ('b' + i);

            // Build safe dimension ID
            String safePlanetName = planetName.toLowerCase(Locale.ROOT)
                    .replaceAll("[^a-z0-9]", "_")
                    .replaceAll("_+", "_")
                    .replaceAll("^_|_$", "");
            if (safePlanetName.isEmpty()) safePlanetName = "imagined_" + i;
            if (safePlanetName.length() > 40) safePlanetName = safePlanetName.substring(0, 40);

            ResourceLocation planetDimId = ResourceLocation.fromNamespaceAndPath("adv_rocketry", "nasa_" + safePlanetName);

            int seed = planetName.hashCode();
            float orbitalOffset = rng.nextFloat() * 360f;

            PlanetDimensionProperties props = new PlanetDimensionProperties();
            props.name = planetName;
            props.dimensionId = planetDimId;
            props.parentDimensionId = parentDimId;
            props.dayTimeReference = parentDimId;
            props.orbitalDistanceToParent = semiMajorAxis;
            props.orbitalBaseOffsetDegrees = orbitalOffset;
            props.earthRadiusMultiplier = radiusEarth;
            props.gravitationalMultiplier = gravity;
            props.currentTemp = eqTemp;
            props.textureSeed = seed;
            props.texture = getPlanetTextureResourceLocation(planetName, planetType, seed);

            // Generate minimal atmosphere for rocky/earth-like planets
            if (planetType == TYPE_EARTH_LIKE || planetType == TYPE_VENUS_LIKE || planetType == TYPE_ROCKY) {
                float humidity = 0.1f + rng.nextFloat() * 0.6f;
                float n2 = 0.5f + rng.nextFloat() * 1.5f;
                float o2 = planetType == TYPE_EARTH_LIKE ? 0.15f + rng.nextFloat() * 0.2f : 0.01f;
                float co2 = planetType == TYPE_VENUS_LIKE ? 0.5f + rng.nextFloat() * 2.0f : 0.003f + rng.nextFloat() * 0.01f;
                float waterAtm = 0.002f + rng.nextFloat() * 0.01f;

                props.atmosphereComposition.put(GasRegistry.nitrogen,
                        new PlanetDimensionProperties.GasProperty(n2, 0, 0, 0));
                props.atmosphereComposition.put(GasRegistry.oxygen,
                        new PlanetDimensionProperties.GasProperty(o2, 0, 0, 0));
                props.atmosphereComposition.put(GasRegistry.co2,
                        new PlanetDimensionProperties.GasProperty(co2, 0, 0, 0));
                props.atmosphereComposition.put(GasRegistry.water,
                        new PlanetDimensionProperties.GasProperty(waterAtm, humidity * 0.3f, humidity * 0.1f, 0));

                if (fluid == FLUID_WATER && canVisit) {
                    props.customSeaFluid = ResourceLocation.parse("minecraft:water");
                    props.customSeaFluidLevel = 62;
                }
            }

            props.canVisit = canVisit;
            if (biomePreset != null) {
                props.biomePreset = biomePreset;
            }

            if (planetType == TYPE_GAS_GIANT || planetType == TYPE_ICE_GIANT) {
                props.canGasMine = true;
            }

            configureVisuals(props, planetType, eqTemp, 0, seed);
            result.add(props);
        }

        return numPlanets;
    }

    /**
     * Add an Earth-like planet with life to a system that doesn't have one.
     * Places it in the habitable zone of the star.
     */
    private static void addLifeBearingPlanet(
            PlanetDimensionProperties starProps,
            ResourceLocation parentDimId,
            List<DimensionProperties> result
    ) {
        String hostname = starProps.name;
        float starMass = Math.max(0.1f, starProps.gravitationalMultiplier / 200f);

        // Calculate habitable zone
        float luminosityProxy = starMass * starMass;
        float hzInner = (float) Math.sqrt(luminosityProxy) * 0.8f;
        float hzOuter = (float) Math.sqrt(luminosityProxy) * 1.5f;
        if (hzInner < 0.1f) hzInner = 0.1f;

        Random rng = new Random(hostname.hashCode() ^ 0xDEADBEEF);
        float semiMajorAxis = hzInner + rng.nextFloat() * (hzOuter - hzInner);
        float radiusEarth = 0.9f + rng.nextFloat() * 0.3f;
        float gravity = radiusEarth * (0.9f + rng.nextFloat() * 0.2f);
        float eqTemp = 250 + rng.nextFloat() * 50;
        int seed = (hostname + "_life").hashCode();
        float orbitalOffset = rng.nextFloat() * 360f;

        String planetName = hostname + " " + (char) ('b' + result.size());
        String safePlanetName = planetName.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_|_$", "");
        if (safePlanetName.isEmpty()) safePlanetName = "life_" + seed;
        if (safePlanetName.length() > 40) safePlanetName = safePlanetName.substring(0, 40);

        ResourceLocation planetDimId = ResourceLocation.fromNamespaceAndPath("adv_rocketry", "nasa_" + safePlanetName);

        PlanetDimensionProperties props = new PlanetDimensionProperties();
        props.name = planetName;
        props.dimensionId = planetDimId;
        props.parentDimensionId = parentDimId;
        props.dayTimeReference = parentDimId;
        props.orbitalDistanceToParent = semiMajorAxis;
        props.orbitalBaseOffsetDegrees = orbitalOffset;
        props.earthRadiusMultiplier = radiusEarth;
        props.gravitationalMultiplier = gravity;
        props.currentTemp = eqTemp;
        props.textureSeed = seed;
        props.texture = getPlanetTextureResourceLocation(planetName, TYPE_EARTH_LIKE, seed);
        props.canVisit = true;
        props.biomePreset = "OVERWORLD";

        // Atmosphere
        float n2 = 0.7f + rng.nextFloat() * 1.0f;
        float o2 = 0.15f + rng.nextFloat() * 0.2f;
        float waterAtm = 0.003f + rng.nextFloat() * 0.01f;
        props.atmosphereComposition.put(GasRegistry.nitrogen,
                new PlanetDimensionProperties.GasProperty(n2, 0, 0, 0));
        props.atmosphereComposition.put(GasRegistry.oxygen,
                new PlanetDimensionProperties.GasProperty(o2, 0, 0, 0));
        props.atmosphereComposition.put(GasRegistry.co2,
                new PlanetDimensionProperties.GasProperty(0.003f + rng.nextFloat() * 0.01f, 0, 0, 0));
        props.atmosphereComposition.put(GasRegistry.water,
                new PlanetDimensionProperties.GasProperty(waterAtm, 0.2f + rng.nextFloat() * 0.3f, 0.05f, 0));

        props.customSeaFluid = ResourceLocation.parse("minecraft:water");
        props.customSeaFluidLevel = 62;

        configureVisuals(props, TYPE_EARTH_LIKE, eqTemp, 0, seed);
        result.add(props);
        System.out.println("[NasaUniverseLoader] Added life-bearing planet " + planetName + " to " + hostname);
    }

    private static ResourceLocation getStarTextureResourceLocation(JsonObject star) {
        int seed = star.has("name") ? star.get("name").getAsString().hashCode() : 0;
        return ResourceLocation.fromNamespaceAndPath(NASA_TEXTURE_NAMESPACE,
                "textures/planet/procedural_star_" + Integer.toHexString(seed) + ".png");
    }

    private static ResourceLocation getPlanetTextureResourceLocation(String name, int planetType, int seed) {
        return ResourceLocation.fromNamespaceAndPath(NASA_TEXTURE_NAMESPACE,
                "textures/planet/procedural_" + planetType + "_" + Integer.toHexString(seed) + ".png");
    }
}
