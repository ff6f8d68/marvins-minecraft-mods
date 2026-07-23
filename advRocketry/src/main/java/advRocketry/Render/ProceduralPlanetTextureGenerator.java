package advRocketry.Render;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Generates procedural planet and star textures at runtime using value noise.
 * Each texture is seeded from the planet/star name, so the same name always
 * produces the same texture.
 *
 * The shader already handles clouds, atmospheres, emissive/reflective tinting,
 * so these textures only provide the base surface color pattern.
 */
public class ProceduralPlanetTextureGenerator {

    public static final int TYPE_ROCKY = 0;
    public static final int TYPE_EARTH_LIKE = 1;
    public static final int TYPE_VENUS_LIKE = 2;
    public static final int TYPE_GAS_GIANT = 3;
    public static final int TYPE_ICE_GIANT = 4;
    public static final int TYPE_LAVA = 5;
    public static final int TYPE_ICE_WORLD = 6;

    private static final int TEX_WIDTH = 128;
    private static final int TEX_HEIGHT = 64;

    private static final Map<ResourceLocation, Boolean> generatedTextures = new ConcurrentHashMap<>();

    private static int[] buildPermTable(int seed) {
        int[] p = new int[512];
        int[] base = new int[256];
        for (int i = 0; i < 256; i++) base[i] = i;
        long s = seed;
        for (int i = 255; i > 0; i--) {
            s = (s * 6364136223846793005L + 1442695040888963407L);
            int j = (int) ((s >>> 16) % (i + 1));
            if (j < 0) j += (i + 1);
            int tmp = base[i];
            base[i] = base[j];
            base[j] = tmp;
        }
        for (int i = 0; i < 256; i++) {
            p[i] = base[i];
            p[i + 256] = base[i];
        }
        return p;
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double lerp(double a, double b, double t) {
        return a + t * (b - a);
    }

    private static double valueNoise2D(double x, double y, int[] perm) {
        int xi = (int) Math.floor(x) & 255;
        int yi = (int) Math.floor(y) & 255;
        double xf = x - Math.floor(x);
        double yf = y - Math.floor(y);
        double u = fade(xf);
        double v = fade(yf);

        int aa = perm[perm[xi] + yi];
        int ab = perm[perm[xi] + yi + 1];
        int ba = perm[perm[xi + 1] + yi];
        int bb = perm[perm[xi + 1] + yi + 1];

        return lerp(
                lerp(aa / 255.0, ba / 255.0, u),
                lerp(ab / 255.0, bb / 255.0, u),
                v
        );
    }

    private static double fbm(double x, double y, int octaves, int[] perm) {
        double value = 0;
        double amplitude = 0.5;
        double frequency = 1.0;
        for (int i = 0; i < octaves; i++) {
            value += amplitude * valueNoise2D(x * frequency, y * frequency, perm);
            amplitude *= 0.5;
            frequency *= 2.0;
        }
        return value;
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static int argb(int r, int g, int b) {
        return 0xFF000000 | (clamp(r, 0, 255) << 16) | (clamp(g, 0, 255) << 8) | clamp(b, 0, 255);
    }

    private static int toABGR(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        return (a << 24) | (b << 16) | (g << 8) | r;
    }

    private static int[] lerpColor(int[] c1, int[] c2, double t) {
        t = Math.max(0, Math.min(1, t));
        return new int[]{
                clamp((int) (c1[0] + (c2[0] - c1[0]) * t), 0, 255),
                clamp((int) (c1[1] + (c2[1] - c1[1]) * t), 0, 255),
                clamp((int) (c1[2] + (c2[2] - c1[2]) * t), 0, 255)
        };
    }

    public static void generateAndRegister(ResourceLocation texLocation, int planetType, int seed) {
        if (generatedTextures.containsKey(texLocation)) return;

        int[] perm = buildPermTable(seed);
        NativeImage image = new NativeImage(TEX_WIDTH, TEX_HEIGHT, false);

        for (int y = 0; y < TEX_HEIGHT; y++) {
            for (int x = 0; x < TEX_WIDTH; x++) {
                double nx = x / (double) TEX_WIDTH;
                double ny = y / (double) TEX_HEIGHT;

                int[] color = generatePixelColor(nx, ny, planetType, seed, perm);
                int argb = argb(color[0], color[1], color[2]);
                image.setPixelRGBA(x, y, toABGR(argb));
            }
        }

        DynamicTexture dynTex = new DynamicTexture(image);
        Minecraft.getInstance().getTextureManager().register(texLocation, dynTex);
        generatedTextures.put(texLocation, true);
    }

    public static void generateAndRegisterStar(ResourceLocation texLocation, int seed) {
        generateAndRegister(texLocation, -1, seed);
    }

    private static int[] generatePixelColor(double nx, double ny, int planetType, int seed, int[] perm) {
        double n1, n2, band;
        int[] result;
        int variant = Math.abs(seed) % 5;
        double latitude = Math.abs(ny - 0.5) * 2;

        switch (planetType) {
            case TYPE_GAS_GIANT: {
                band = Math.sin(ny * 20 + fbm(nx * 6, ny * 3, 3, perm) * 4) * 0.5 + 0.5;
                n1 = fbm(nx * 4, ny * 8, 4, perm);
                n2 = fbm(nx * 8 + 100, ny * 4 + 100, 3, perm);

                int[][] palettes = {
                    {220, 180, 130, 180, 120, 70, 200, 150, 100},
                    {210, 190, 140, 170, 130, 90, 190, 160, 110},
                    {200, 140, 90, 160, 90, 50, 180, 110, 70},
                    {180, 200, 210, 140, 160, 180, 160, 180, 200},
                    {100, 80, 60, 70, 50, 40, 85, 65, 50},
                };
                int[] pal = palettes[variant];
                int[] c1 = {pal[0], pal[1], pal[2]};
                int[] c2 = {pal[3], pal[4], pal[5]};
                int[] c3 = {pal[6], pal[7], pal[8]};

                result = lerpColor(c1, c2, band);
                result = lerpColor(result, c3, n1 * 0.3);

                double stormN = fbm((nx + 0.3) * 12, ny * 6, 4, perm);
                if (stormN > 0.68) {
                    int[] stormColor = lerpColor(c1, c3, 0.5);
                    result = lerpColor(result, stormColor, (stormN - 0.68) / 0.32 * 0.6);
                }

                if (variant == 0 || variant == 2) {
                    double spotDist = Math.sqrt(Math.pow(nx - 0.6, 2) * 4 + Math.pow(ny - 0.4, 2) * 16);
                    if (spotDist < 0.15) {
                        int[] spotColor = {200, 100, 60};
                        result = lerpColor(result, spotColor, Math.max(0, (0.15 - spotDist) / 0.15));
                    }
                }
                return result;
            }

            case TYPE_ICE_GIANT: {
                band = Math.sin(ny * 15 + fbm(nx * 5, ny * 2, 2, perm) * 3) * 0.5 + 0.5;
                n1 = fbm(nx * 6, ny * 4, 3, perm);
                n2 = fbm(nx * 10 + 70, ny * 5 + 70, 3, perm);

                int[][] icePalettes = {
                    {120, 180, 210, 80, 150, 180, 140, 200, 200},
                    {100, 160, 200, 60, 120, 170, 120, 180, 190},
                    {140, 190, 180, 100, 160, 160, 160, 210, 200},
                    {160, 170, 200, 120, 130, 180, 150, 160, 210},
                    {90, 140, 170, 60, 100, 140, 110, 160, 180},
                };
                int[] ice = icePalettes[variant];
                int[] ice1 = {ice[0], ice[1], ice[2]};
                int[] ice2 = {ice[3], ice[4], ice[5]};
                int[] ice3 = {ice[6], ice[7], ice[8]};

                result = lerpColor(ice1, ice2, band);
                result = lerpColor(result, ice3, n1 * 0.25);
                if (n2 > 0.65) {
                    result = lerpColor(result, ice3, (n2 - 0.65) * 0.8);
                }
                return result;
            }

            case TYPE_EARTH_LIKE: {
                n1 = fbm(nx * 6, ny * 4, 5, perm);
                n2 = fbm(nx * 10 + 50, ny * 8 + 50, 4, perm);
                double n3 = fbm(nx * 3, ny * 2, 3, perm);
                double n4 = fbm(nx * 14 + 20, ny * 12 + 20, 4, perm);

                int[][] earthPalettes = {
                    {20, 50, 140, 40, 100, 180, 50, 130, 50, 130, 110, 60},
                    {15, 40, 120, 35, 80, 160, 80, 140, 70, 140, 120, 50},
                    {25, 60, 150, 50, 110, 190, 40, 120, 60, 120, 100, 55},
                    {30, 55, 130, 55, 95, 170, 60, 100, 45, 110, 90, 50},
                    {20, 45, 130, 40, 90, 170, 55, 125, 55, 125, 105, 60},
                };
                int[] ep = earthPalettes[variant];
                int[] oceanDeep = {ep[0], ep[1], ep[2]};
                int[] oceanShallow = {ep[3], ep[4], ep[5]};
                int[] landGreen = {ep[6], ep[7], ep[8]};
                int[] landBrown = {ep[9], ep[10], ep[11]};

                if (n1 < 0.40) {
                    return lerpColor(oceanDeep, oceanShallow, n2);
                } else if (n1 < 0.44) {
                    return lerpColor(oceanShallow, new int[]{180, 170, 130}, (n1 - 0.40) / 0.04);
                } else if (latitude > 0.78 && n3 > 0.35) {
                    int[] ice = {230, 235, 240};
                    int[] snow = {210, 220, 230};
                    return lerpColor(snow, ice, n4);
                } else if (latitude > 0.65 && n3 > 0.5) {
                    int[] boreal = {90, 110, 70};
                    return lerpColor(landGreen, boreal, (latitude - 0.65) * 3);
                } else {
                    double vegNoise = fbm(nx * 8 + 30, ny * 6 + 30, 3, perm);
                    if (latitude < 0.3 && variant == 0) {
                        int[] tropical = {30, 110, 35};
                        result = lerpColor(landGreen, tropical, 0.4 + vegNoise * 0.3);
                    } else {
                        result = lerpColor(landGreen, landBrown, n2 * 0.4 + n1 * 0.2);
                    }
                    if (latitude < 0.25 && variant == 1 && n2 > 0.5) {
                        int[] desert = {190, 170, 120};
                        result = lerpColor(result, desert, 0.5);
                    }
                    if (n4 > 0.78) {
                        int[] mountain = {120, 110, 90};
                        result = lerpColor(result, mountain, (n4 - 0.78) / 0.22 * 0.4);
                    }
                    return result;
                }
            }

            case TYPE_VENUS_LIKE: {
                n1 = fbm(nx * 5, ny * 5, 4, perm);
                n2 = fbm(nx * 8 + 30, ny * 6 + 30, 3, perm);
                double n3 = fbm(nx * 12, ny * 10, 3, perm);

                int[][] venusPalettes = {
                    {200, 180, 100, 220, 190, 80, 180, 150, 70},
                    {190, 170, 110, 210, 185, 95, 175, 145, 75},
                    {170, 150, 90, 195, 170, 75, 155, 130, 65},
                    {210, 195, 130, 230, 210, 110, 195, 175, 100},
                    {180, 160, 100, 200, 175, 85, 165, 140, 70},
                };
                int[] vp = venusPalettes[variant];
                int[] v1 = {vp[0], vp[1], vp[2]};
                int[] v2 = {vp[3], vp[4], vp[5]};
                int[] v3 = {vp[6], vp[7], vp[8]};

                result = lerpColor(v1, v2, n1);
                result = lerpColor(result, v3, n2 * 0.4);
                if (n3 > 0.6) {
                    int[] cloud = {230, 210, 150};
                    result = lerpColor(result, cloud, (n3 - 0.6) * 0.5);
                }
                if (n1 > 0.72) {
                    int[] highland = lerpColor(v1, v3, 0.5);
                    result = lerpColor(result, highland, (n1 - 0.72) * 0.8);
                }
                return result;
            }

            case TYPE_LAVA: {
                n1 = fbm(nx * 6, ny * 4, 5, perm);
                n2 = fbm(nx * 10, ny * 8, 3, perm);
                double n3 = fbm(nx * 14 + 60, ny * 10 + 60, 4, perm);

                int[] lavaPalettes = {
                    255, 130, 20, 255, 200, 50, 40, 25, 20, 70, 40, 30,
                    255, 100, 10, 255, 170, 30, 35, 20, 15, 60, 35, 25,
                    240, 120, 30, 255, 190, 60, 45, 30, 25, 75, 45, 35,
                    255, 80, 5, 255, 150, 20, 30, 18, 12, 55, 30, 20,
                    250, 140, 25, 255, 210, 55, 42, 28, 22, 68, 38, 28,
                };
                int li = variant * 12;
                int[] lavaH = {lavaPalettes[li], lavaPalettes[li + 1], lavaPalettes[li + 2]};
                int[] lavaB = {lavaPalettes[li + 3], lavaPalettes[li + 4], lavaPalettes[li + 5]};
                int[] rock1 = {lavaPalettes[li + 6], lavaPalettes[li + 7], lavaPalettes[li + 8]};
                int[] rock2 = {lavaPalettes[li + 9], lavaPalettes[li + 10], lavaPalettes[li + 11]};

                if (n1 > 0.55) {
                    result = lerpColor(lavaH, lavaB, (n1 - 0.55) / 0.45);
                    if (n3 > 0.75) {
                        int[] bright = {255, 230, 80};
                        result = lerpColor(result, bright, (n3 - 0.75) * 2);
                    }
                } else {
                    result = lerpColor(rock1, rock2, n2);
                    if (n1 > 0.48) {
                        int[] glow = lerpColor(rock1, lavaH, 0.5);
                        result = lerpColor(result, glow, (n1 - 0.48) / 0.07);
                    }
                }
                return result;
            }

            case TYPE_ICE_WORLD: {
                n1 = fbm(nx * 6, ny * 4, 4, perm);
                n2 = fbm(nx * 10 + 20, ny * 8 + 20, 3, perm);
                double n3 = fbm(nx * 14 + 40, ny * 10 + 40, 3, perm);

                int[][] icePalettes = {
                    {200, 210, 230, 170, 190, 220, 230, 235, 245},
                    {180, 200, 225, 150, 175, 210, 220, 230, 240},
                    {210, 215, 235, 185, 195, 225, 235, 238, 248},
                    {190, 205, 228, 160, 185, 215, 225, 232, 243},
                    {195, 208, 232, 165, 188, 218, 228, 234, 246},
                };
                int[] ip = icePalettes[variant];
                int[] iceW1 = {ip[0], ip[1], ip[2]};
                int[] iceW2 = {ip[3], ip[4], ip[5]};
                int[] iceW3 = {ip[6], ip[7], ip[8]};

                result = lerpColor(iceW1, iceW2, n1);
                if (n2 > 0.6) {
                    result = lerpColor(result, iceW3, (n2 - 0.6) / 0.4);
                }
                if (n3 > 0.75) {
                    int[] crack = {140, 160, 200};
                    result = lerpColor(result, crack, (n3 - 0.75) * 0.6);
                }
                return result;
            }

            case TYPE_ROCKY: {
                n1 = fbm(nx * 8, ny * 6, 5, perm);
                n2 = fbm(nx * 12 + 40, ny * 10 + 40, 4, perm);
                double n3 = fbm(nx * 16 + 80, ny * 12 + 80, 3, perm);

                int[][] rockyPalettes = {
                    {80, 75, 70, 170, 160, 150, 140, 130, 120},
                    {90, 80, 75, 160, 140, 120, 130, 115, 100},
                    {75, 70, 65, 155, 145, 135, 125, 115, 105},
                    {85, 78, 72, 175, 165, 155, 145, 135, 125},
                    {82, 76, 71, 165, 155, 145, 135, 125, 115},
                };
                int[] rp = rockyPalettes[variant];
                int[] rockDark = {rp[0], rp[1], rp[2]};
                int[] rockLight = {rp[3], rp[4], rp[5]};
                int[] rockGray = {rp[6], rp[7], rp[8]};

                result = lerpColor(rockDark, rockLight, n1);
                if (n2 > 0.72) {
                    result = lerpColor(result, rockGray, (n2 - 0.72) / 0.28);
                }
                if (variant % 3 == 0) {
                    int[] rust = {180, 100, 60};
                    result = lerpColor(result, rust, 0.3);
                }
                if (variant == 2) {
                    int[] volcanic = {100, 60, 40};
                    result = lerpColor(result, volcanic, n3 * 0.25);
                }
                return result;
            }

            case -1: {
                n1 = fbm(nx * 4, ny * 4, 4, perm);
                n2 = fbm(nx * 8, ny * 6, 3, perm);
                double distFromCenter = Math.sqrt((nx - 0.5) * (nx - 0.5) + (ny - 0.5) * (ny - 0.5)) * 2;
                double brightness = 1.0 - distFromCenter * 0.3;

                int[] starPalettes = {
                    255, 250, 230, 255, 220, 150, 240, 180, 80,
                    200, 220, 255, 170, 190, 240, 140, 160, 220,
                    255, 240, 220, 255, 200, 120, 230, 160, 60,
                    255, 255, 250, 240, 240, 230, 220, 220, 200,
                    255, 180, 120, 255, 140, 60, 230, 100, 30,
                };
                int si = variant * 9;
                int[] starBright = {starPalettes[si], starPalettes[si + 1], starPalettes[si + 2]};
                int[] starMid = {starPalettes[si + 3], starPalettes[si + 4], starPalettes[si + 5]};
                int[] starDark = {starPalettes[si + 6], starPalettes[si + 7], starPalettes[si + 8]};

                result = lerpColor(starDark, starBright, n1 * brightness);
                if (n2 > 0.7) {
                    result = lerpColor(result, starDark, (n2 - 0.7) * 2);
                }
                return result;
            }

            default:
                return new int[]{128, 128, 128};
        }
    }

    public static boolean isGenerated(ResourceLocation texLocation) {
        return generatedTextures.containsKey(texLocation);
    }
}
