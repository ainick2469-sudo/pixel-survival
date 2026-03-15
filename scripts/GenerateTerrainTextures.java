import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

public final class GenerateTerrainTextures {
    private static final int SIZE = 128;

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        Path outputDirectory = root.resolve("src/main/resources/Textures/Terrain");
        Files.createDirectories(outputDirectory);

        writeTexture(outputDirectory.resolve("grass_top.png"), grassTopTexture());
        writeTexture(outputDirectory.resolve("grass_side.png"), grassSideTexture());
        writeTexture(outputDirectory.resolve("dirt.png"), dirtTexture());
        writeTexture(outputDirectory.resolve("stone.png"), stoneTexture());
        writeTexture(outputDirectory.resolve("missing_block.png"), missingTexture());
    }

    private static BufferedImage grassTopTexture() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double macro = fbm(x * 0.045, y * 0.045, 11);
                double detail = fbm(x * 0.13, y * 0.13, 23);
                double blades = ridge(x * 0.21, y * 0.21, 37);
                double patch = fbm(x * 0.025, y * 0.025, 53);

                double tone = clamp01(0.52 + macro * 0.22 + detail * 0.12 + blades * 0.08);
                int color = lerpColor(rgb(47, 81, 31), rgb(94, 135, 59), tone);
                color = lerpColor(color, rgb(122, 154, 75), clamp01((detail + blades - 0.15) * 0.65));
                color = lerpColor(color, rgb(34, 62, 24), clamp01((patch - 0.58) * 0.9));

                if (hash01(x / 2, y / 2, 71) > 0.91) {
                    color = lerpColor(color, rgb(28, 52, 20), 0.45);
                }
                if (hash01(x, y, 79) > 0.986) {
                    color = lerpColor(color, rgb(143, 168, 92), 0.55);
                }

                image.setRGB(x, y, color);
            }
        }
        return image;
    }

    private static BufferedImage grassSideTexture() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                int dirtColor = dirtColorAt(x, y);
                int lipDepth = 26 + (int) Math.round(noise01(x * 0.085, 3.75, 101) * 10.0);
                int transitionDepth = lipDepth + 8;

                if (y < lipDepth) {
                    double grassTone = clamp01(0.48
                            + fbm(x * 0.06, y * 0.08, 131) * 0.25
                            + ridge(x * 0.18, y * 0.18, 137) * 0.09);
                    int grassColor = lerpColor(rgb(44, 75, 29), rgb(96, 136, 59), grassTone);
                    if (hash01(x, y, 149) > 0.988) {
                        grassColor = lerpColor(grassColor, rgb(132, 160, 84), 0.45);
                    }
                    image.setRGB(x, y, grassColor);
                    continue;
                }

                if (y < transitionDepth) {
                    double blend = clamp01((y - lipDepth) / 8.0);
                    double fringe = ridge(x * 0.22, y * 0.18, 163);
                    int fringeGrass = lerpColor(rgb(42, 70, 28), rgb(84, 118, 52), clamp01(0.42 + fringe * 0.28));
                    int blended = lerpColor(fringeGrass, dirtColor, blend);
                    if (hash01(x, y, 167) > 0.95) {
                        blended = lerpColor(blended, rgb(32, 54, 20), 0.32);
                    }
                    image.setRGB(x, y, blended);
                    continue;
                }

                if (hash01(x, y, 173) > 0.992) {
                    dirtColor = lerpColor(dirtColor, rgb(70, 96, 38), 0.26);
                }
                image.setRGB(x, y, dirtColor);
            }
        }
        return image;
    }

    private static BufferedImage dirtTexture() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                image.setRGB(x, y, dirtColorAt(x, y));
            }
        }
        return image;
    }

    private static BufferedImage stoneTexture() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                double macro = fbm(x * 0.05, y * 0.05, 211);
                double detail = fbm(x * 0.16, y * 0.16, 223);
                double mineral = ridge(x * 0.12, y * 0.12, 227);

                double tone = clamp01(0.47 + macro * 0.24 + detail * 0.12);
                int color = lerpColor(rgb(84, 88, 96), rgb(126, 131, 140), tone);
                color = lerpColor(color, rgb(152, 158, 166), clamp01((detail - 0.12) * 0.5));
                color = lerpColor(color, rgb(63, 67, 74), clamp01((mineral - 0.58) * 0.85));

                if (hash01(x, y, 229) > 0.988) {
                    color = lerpColor(color, rgb(171, 176, 183), 0.48);
                }
                if (hash01(x / 2, y / 2, 233) > 0.97) {
                    color = lerpColor(color, rgb(56, 60, 67), 0.35);
                }

                image.setRGB(x, y, color);
            }
        }
        return image;
    }

    private static BufferedImage missingTexture() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                boolean magenta = ((x / 16) + (y / 16)) % 2 == 0;
                image.setRGB(x, y, magenta ? rgb(179, 45, 179) : rgb(30, 30, 30));
            }
        }
        return image;
    }

    private static int dirtColorAt(int x, int y) {
        double macro = fbm(x * 0.055, y * 0.055, 181);
        double detail = fbm(x * 0.17, y * 0.17, 191);
        double grain = fbm(x * 0.38, y * 0.38, 197);

        double tone = clamp01(0.43 + macro * 0.24 + detail * 0.1 + grain * 0.05);
        int color = lerpColor(rgb(64, 42, 25), rgb(107, 75, 45), tone);
        color = lerpColor(color, rgb(129, 94, 58), clamp01((detail - 0.08) * 0.35));
        color = lerpColor(color, rgb(44, 28, 18), clamp01((grain - 0.55) * 0.6));

        if (hash01(x, y, 199) > 0.983) {
            color = lerpColor(color, rgb(142, 111, 76), 0.45);
        }
        if (hash01(x / 2, y / 2, 207) > 0.992) {
            color = lerpColor(color, rgb(55, 34, 21), 0.5);
        }

        return color;
    }

    private static void writeTexture(Path path, BufferedImage image) throws IOException {
        ImageIO.write(image, "png", path.toFile());
    }

    private static double fbm(double x, double y, int seed) {
        double amplitude = 0.55;
        double frequency = 1.0;
        double sum = 0.0;
        double normalization = 0.0;

        for (int octave = 0; octave < 4; octave++) {
            sum += sampleValueNoise(x * frequency, y * frequency, seed + (octave * 17)) * amplitude;
            normalization += amplitude;
            amplitude *= 0.5;
            frequency *= 2.0;
        }

        return sum / normalization;
    }

    private static double ridge(double x, double y, int seed) {
        return 1.0 - Math.abs(sampleValueNoise(x, y, seed));
    }

    private static double noise01(double x, double y, int seed) {
        return (sampleValueNoise(x, y, seed) + 1.0) * 0.5;
    }

    private static double sampleValueNoise(double sampleX, double sampleY, int seed) {
        int x0 = (int) Math.floor(sampleX);
        int y0 = (int) Math.floor(sampleY);
        int x1 = x0 + 1;
        int y1 = y0 + 1;

        double tx = smoothStep(sampleX - x0);
        double ty = smoothStep(sampleY - y0);

        double v00 = latticeValue(x0, y0, seed);
        double v10 = latticeValue(x1, y0, seed);
        double v01 = latticeValue(x0, y1, seed);
        double v11 = latticeValue(x1, y1, seed);

        double blendX0 = lerp(v00, v10, tx);
        double blendX1 = lerp(v01, v11, tx);
        return lerp(blendX0, blendX1, ty);
    }

    private static double latticeValue(int x, int y, int seed) {
        long hash = 1469598103934665603L;
        hash ^= x * 0x9E3779B97F4A7C15L;
        hash *= 1099511628211L;
        hash ^= y * 0xC2B2AE3D27D4EB4FL;
        hash *= 1099511628211L;
        hash ^= seed * 0x165667B19E3779F9L;
        hash *= 1099511628211L;
        long positive = hash & 0x7fffffffffffffffL;
        return (positive / (double) Long.MAX_VALUE) * 2.0 - 1.0;
    }

    private static double hash01(int x, int y, int seed) {
        return (latticeValue(x, y, seed) + 1.0) * 0.5;
    }

    private static double smoothStep(double value) {
        return value * value * (3.0 - 2.0 * value);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static int lerpColor(int first, int second, double t) {
        int firstRed = (first >> 16) & 0xFF;
        int firstGreen = (first >> 8) & 0xFF;
        int firstBlue = first & 0xFF;
        int secondRed = (second >> 16) & 0xFF;
        int secondGreen = (second >> 8) & 0xFF;
        int secondBlue = second & 0xFF;

        int red = clampColor((int) Math.round(lerp(firstRed, secondRed, t)));
        int green = clampColor((int) Math.round(lerp(firstGreen, secondGreen, t)));
        int blue = clampColor((int) Math.round(lerp(firstBlue, secondBlue, t)));
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    private static int rgb(int red, int green, int blue) {
        return 0xFF000000 | (clampColor(red) << 16) | (clampColor(green) << 8) | clampColor(blue);
    }

    private static int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
