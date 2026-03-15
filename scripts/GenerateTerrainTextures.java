import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import javax.imageio.ImageIO;

public final class GenerateTerrainTextures {
    private static final int SIZE = 128;

    public static void main(String[] args) throws IOException {
        Path root = Path.of(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        Path terrainOutputDirectory = root.resolve("src/main/resources/Textures/Terrain");
        Path cubeNetOutputDirectory = root.resolve("src/main/resources/Textures/BlockCubeNets");
        Files.createDirectories(terrainOutputDirectory);
        Files.createDirectories(cubeNetOutputDirectory);

        BufferedImage dirtTexture = dirtTexture();
        BufferedImage stoneTexture = stoneTexture();

        writeTexture(terrainOutputDirectory.resolve("grass_top.png"), grassTopTexture());
        writeTexture(terrainOutputDirectory.resolve("grass_side.png"), grassSideTexture());
        writeTexture(terrainOutputDirectory.resolve("dirt.png"), dirtTexture);
        writeTexture(terrainOutputDirectory.resolve("stone.png"), stoneTexture);
        writeTexture(terrainOutputDirectory.resolve("sand.png"), sandTexture());
        writeTexture(terrainOutputDirectory.resolve("cloud_solid.png"), cloudSolidTexture());
        writeTexture(terrainOutputDirectory.resolve("missing_block.png"), missingTexture());
        writeTexture(cubeNetOutputDirectory.resolve("dirt_cube_net.png"), cubeNetTexture(
                dirtTexture,
                dirtTexture,
                dirtTexture,
                dirtTexture,
                dirtTexture,
                dirtTexture));
        writeTexture(cubeNetOutputDirectory.resolve("stone_cube_net.png"), cubeNetTexture(
                stoneTexture,
                stoneTexture,
                stoneTexture,
                stoneTexture,
                stoneTexture,
                stoneTexture));
    }

    private static BufferedImage grassTopTexture() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        forEachPixel(image, (x, y) -> {
            double meadow = fbm(x * 0.042, y * 0.042, 101);
            double detail = fbm(x * 0.12, y * 0.12, 109);
            double shadow = fbm(x * 0.022, y * 0.022, 113);

            double tone = clamp01(0.5 + (meadow * 0.28) + (detail * 0.18) - (shadow * 0.06));
            int color = lerpColor(rgb(28, 67, 26), rgb(72, 128, 44), tone);
            color = lerpColor(color, rgb(102, 157, 71), clamp01((detail - 0.08) * 0.48));
            color = lerpColor(color, rgb(21, 52, 20), clamp01((shadow - 0.42) * 0.55));
            return color;
        });

        Graphics2D graphics = graphics(image);
        drawLeafClusters(graphics, 280, 1201, new Color(34, 89, 28, 210), new Color(106, 167, 72, 195));
        drawCloverClusters(graphics, 42, 1213);
        drawFlowers(graphics, 16, 1231);
        drawMicroPebbles(graphics, 150, 1237, new Color(26, 52, 22, 72), 0.8f, 1.6f);
        graphics.dispose();
        return image;
    }

    private static BufferedImage grassSideTexture() {
        BufferedImage image = dirtTexture();
        Graphics2D graphics = graphics(image);

        for (int x = 0; x < SIZE; x++) {
            int grassLip = 16 + (int) Math.round(noise01(x * 0.08, 4.6, 1301) * 8.0);
            int canopyDepth = grassLip + 5 + (int) Math.round(noise01(x * 0.055, 8.2, 1303) * 6.0);
            for (int y = 0; y < canopyDepth; y++) {
                double falloff = 1.0 - (y / (double) Math.max(1, canopyDepth));
                double detail = fbm(x * 0.085, y * 0.11, 1309);
                double grassTone = clamp01(0.34 + (falloff * 0.46) + (detail * 0.12));
                int grassColor = lerpColor(rgb(31, 74, 26), rgb(92, 147, 58), grassTone);
                grassColor = lerpColor(grassColor, rgb(118, 165, 76), clamp01((detail - 0.08) * 0.28));
                double soilBlend = clamp01((y - (grassLip * 0.68)) / Math.max(1.0, canopyDepth - (grassLip * 0.68)));
                int mixedColor = lerpColor(grassColor, image.getRGB(x, y), soilBlend * 0.62);
                if (y >= grassLip - 1 && y <= grassLip + 2) {
                    mixedColor = lerpColor(mixedColor, rgb(42, 55, 28), 0.28);
                }
                image.setRGB(x, y, mixedColor);
            }
        }

        drawGrassFringe(graphics, 96, 1319);
        drawRootThreads(graphics, 22, 1343, new Color(96, 73, 47, 150));
        drawPebbles(graphics, 84, 1351, new Color[] {
            new Color(55, 40, 27, 235),
            new Color(78, 57, 36, 235),
            new Color(102, 74, 48, 235),
            new Color(89, 92, 94, 215)
        }, 1.8f, 4.8f);
        graphics.dispose();
        return image;
    }

    private static BufferedImage dirtTexture() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        forEachPixel(image, (x, y) -> {
            double clump = fbm(x * 0.05, y * 0.05, 1401);
            double grain = fbm(x * 0.17, y * 0.17, 1409);
            double darkPockets = fbm(x * 0.025, y * 0.025, 1417);

            double tone = clamp01(0.44 + (clump * 0.22) + (grain * 0.12) - (darkPockets * 0.1));
            int color = lerpColor(rgb(50, 31, 19), rgb(97, 63, 38), tone);
            color = lerpColor(color, rgb(134, 95, 58), clamp01((grain - 0.08) * 0.4));
            color = lerpColor(color, rgb(31, 19, 12), clamp01((darkPockets - 0.45) * 0.6));
            return color;
        });

        Graphics2D graphics = graphics(image);
        drawPebbles(graphics, 120, 1423, new Color[] {
            new Color(58, 37, 23, 240),
            new Color(84, 57, 35, 240),
            new Color(118, 84, 55, 220),
            new Color(98, 103, 106, 210)
        }, 1.7f, 4.6f);
        drawRootThreads(graphics, 34, 1439, new Color(116, 88, 54, 155));
        drawMicroPebbles(graphics, 280, 1453, new Color(188, 157, 121, 96), 0.6f, 1.35f);
        graphics.dispose();
        return image;
    }

    private static BufferedImage stoneTexture() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        forEachPixel(image, (x, y) -> {
            double strata = fbm(x * 0.038, y * 0.038, 1501);
            double fracture = fbm(x * 0.1, y * 0.1, 1511);
            double cold = fbm(x * 0.022, y * 0.022, 1523);

            double tone = clamp01(0.42 + (strata * 0.22) + (fracture * 0.14));
            int color = lerpColor(rgb(74, 79, 85), rgb(138, 144, 149), tone);
            color = lerpColor(color, rgb(182, 186, 189), clamp01((fracture - 0.05) * 0.26));
            color = lerpColor(color, rgb(56, 61, 67), clamp01((cold - 0.42) * 0.45));
            return color;
        });

        Graphics2D graphics = graphics(image);
        drawRockFaces(graphics, 58, 1531);
        drawStoneCracks(graphics, 26, 1543);
        drawMossPatches(graphics, 22, 1559);
        graphics.dispose();
        return image;
    }

    private static BufferedImage sandTexture() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        forEachPixel(image, (x, y) -> {
            double dune = fbm(x * 0.03, y * 0.03, 1601);
            double fine = fbm(x * 0.12, y * 0.12, 1613);
            double ripples = Math.sin((x * 0.19) + (fbm(x * 0.015, y * 0.015, 1621) * 1.6) + (y * 0.05));

            double tone = clamp01(0.56 + (dune * 0.18) + (fine * 0.12) + (ripples * 0.08));
            int color = lerpColor(rgb(180, 163, 120), rgb(221, 209, 168), tone);
            color = lerpColor(color, rgb(241, 230, 198), clamp01((fine - 0.06) * 0.34));
            color = lerpColor(color, rgb(146, 130, 91), clamp01((dune - 0.44) * 0.36));
            return color;
        });

        Graphics2D graphics = graphics(image);
        drawSandRipples(graphics, 10, 1637);
        drawPebbles(graphics, 85, 1651, new Color[] {
            new Color(233, 225, 210, 225),
            new Color(198, 186, 162, 225),
            new Color(164, 145, 117, 220),
            new Color(164, 172, 185, 205)
        }, 1.2f, 4.2f);
        graphics.dispose();
        return image;
    }

    private static BufferedImage cloudSolidTexture() {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        forEachPixel(image, (x, y) -> {
            double billow = fbm(x * 0.034, y * 0.034, 1701);
            double swirl = fbm(x * 0.08, y * 0.08, 1717);
            double shadow = fbm(x * 0.02, y * 0.02, 1729);

            double tone = clamp01(0.58 + (billow * 0.22) + (swirl * 0.12));
            int color = lerpColor(rgb(194, 208, 218), rgb(245, 247, 252), tone);
            color = lerpColor(color, rgb(172, 188, 201), clamp01((shadow - 0.45) * 0.3));
            color = lerpColor(color, rgb(255, 255, 255), clamp01((swirl - 0.06) * 0.22));
            return color;
        });

        Graphics2D graphics = graphics(image);
        drawCloudPuffs(graphics, 46, 1733);
        drawCloudVeins(graphics, 10, 1741);
        graphics.dispose();
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

    private static BufferedImage cubeNetTexture(
            BufferedImage back,
            BufferedImage top,
            BufferedImage left,
            BufferedImage front,
            BufferedImage right,
            BufferedImage bottom) {
        BufferedImage image = new BufferedImage(SIZE * 3, SIZE * 4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = graphics(image);
        graphics.drawImage(back, SIZE, 0, null);
        graphics.drawImage(top, SIZE, SIZE, null);
        graphics.drawImage(left, 0, SIZE * 2, null);
        graphics.drawImage(front, SIZE, SIZE * 2, null);
        graphics.drawImage(right, SIZE * 2, SIZE * 2, null);
        graphics.drawImage(bottom, SIZE, SIZE * 3, null);
        graphics.dispose();
        return image;
    }

    private static void drawLeafClusters(
            Graphics2D graphics,
            int count,
            int seed,
            Color shadowColor,
            Color highlightColor) {
        Random random = new Random(seed);
        for (int index = 0; index < count; index++) {
            float x = random.nextFloat() * SIZE;
            float y = random.nextFloat() * SIZE;
            float clusterRadius = 8f + (random.nextFloat() * 10f);
            withWrapPositions(x, y, clusterRadius, (drawX, drawY) -> {
                int leaves = 2 + random.nextInt(3);
                for (int leafIndex = 0; leafIndex < leaves; leafIndex++) {
                    float angle = (float) (random.nextDouble() * Math.PI * 2.0);
                    float length = 9f + (random.nextFloat() * 12f);
                    float width = 3.4f + (random.nextFloat() * 2.4f);
                    drawLeaf(graphics, drawX, drawY, angle, length, width, shadowColor, highlightColor);
                }
            });
        }
    }

    private static void drawCloverClusters(Graphics2D graphics, int count, int seed) {
        Random random = new Random(seed);
        for (int index = 0; index < count; index++) {
            float x = random.nextFloat() * SIZE;
            float y = random.nextFloat() * SIZE;
            withWrapPositions(x, y, 7f, (drawX, drawY) -> {
                Color clover = new Color(70 + random.nextInt(20), 128 + random.nextInt(24), 58 + random.nextInt(18), 192);
                float radius = 2.4f + (random.nextFloat() * 1.2f);
                for (int leafIndex = 0; leafIndex < 4; leafIndex++) {
                    double angle = (Math.PI * 0.5 * leafIndex) + (random.nextDouble() * 0.12);
                    float leafX = drawX + (float) Math.cos(angle) * (radius * 1.35f);
                    float leafY = drawY + (float) Math.sin(angle) * (radius * 1.35f);
                    graphics.setColor(clover);
                    graphics.fill(new Ellipse2D.Float(leafX - radius, leafY - radius, radius * 2f, radius * 2f));
                }
                graphics.setStroke(new BasicStroke(1.05f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.setColor(new Color(64, 98, 51, 180));
                graphics.drawLine(Math.round(drawX), Math.round(drawY), Math.round(drawX), Math.round(drawY + 5f));
            });
        }
    }

    private static void drawFlowers(Graphics2D graphics, int count, int seed) {
        Random random = new Random(seed);
        Color[] petalColors = {
            new Color(238, 241, 243, 215),
            new Color(118, 160, 226, 210),
            new Color(244, 216, 104, 210)
        };
        for (int index = 0; index < count; index++) {
            float x = random.nextFloat() * SIZE;
            float y = random.nextFloat() * SIZE;
            float radius = 3.2f + (random.nextFloat() * 1.8f);
            Color petals = petalColors[random.nextInt(petalColors.length)];
            withWrapPositions(x, y, radius + 2f, (drawX, drawY) -> {
                for (int petalIndex = 0; petalIndex < 5; petalIndex++) {
                    double angle = (Math.PI * 2.0 * petalIndex / 5.0);
                    float petalX = drawX + (float) Math.cos(angle) * radius;
                    float petalY = drawY + (float) Math.sin(angle) * radius;
                    graphics.setColor(petals);
                    graphics.fill(new Ellipse2D.Float(petalX - 1.7f, petalY - 1.7f, 3.4f, 3.4f));
                }
                graphics.setColor(new Color(222, 185, 74, 225));
                graphics.fill(new Ellipse2D.Float(drawX - 1.35f, drawY - 1.35f, 2.7f, 2.7f));
            });
        }
    }

    private static void drawGrassFringe(Graphics2D graphics, int count, int seed) {
        Random random = new Random(seed);
        for (int index = 0; index < count; index++) {
            float x = random.nextFloat() * SIZE;
            float baseY = 6f + (random.nextFloat() * 10f);
            float length = 8f + (random.nextFloat() * 11f);
            float bend = -0.28f + (random.nextFloat() * 0.56f);
            float width = 0.95f + (random.nextFloat() * 0.65f);
            float radius = length + 3f;
            withWrapPositions(x, baseY, radius, (drawX, drawY) -> {
                graphics.setStroke(new BasicStroke(width + 0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.setColor(new Color(29, 66, 27, 150));
                Path2D.Float shadowBlade = new Path2D.Float();
                shadowBlade.moveTo(drawX, drawY + length * 0.16f);
                shadowBlade.quadTo(drawX + (bend * 5f), drawY - length * 0.04f, drawX + (bend * 11f), drawY - length);
                graphics.draw(shadowBlade);

                graphics.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.setColor(new Color(94, 156, 62, 205));
                Path2D.Float blade = new Path2D.Float();
                blade.moveTo(drawX, drawY + length * 0.1f);
                blade.quadTo(drawX + (bend * 4f), drawY - length * 0.1f, drawX + (bend * 8f), drawY - length);
                graphics.draw(blade);
            });
        }
    }

    private static void drawPebbles(Graphics2D graphics, int count, int seed, Color[] palette, float minSize, float maxSize) {
        Random random = new Random(seed);
        for (int index = 0; index < count; index++) {
            float x = random.nextFloat() * SIZE;
            float y = random.nextFloat() * SIZE;
            float width = minSize + (random.nextFloat() * (maxSize - minSize));
            float height = Math.max(minSize * 0.85f, width * (0.55f + (random.nextFloat() * 0.7f)));
            float angle = random.nextFloat() * 360f;
            Color fill = palette[random.nextInt(palette.length)];
            float radius = Math.max(width, height);

            withWrapPositions(x, y, radius + 2f, (drawX, drawY) -> {
                Graphics2D pebbleGraphics = (Graphics2D) graphics.create();
                pebbleGraphics.translate(drawX, drawY);
                pebbleGraphics.rotate(Math.toRadians(angle));
                pebbleGraphics.setColor(new Color(0, 0, 0, 60));
                pebbleGraphics.fill(new RoundRectangle2D.Float(
                        -(width * 0.52f),
                        -(height * 0.45f) + 0.8f,
                        width * 1.04f,
                        height * 0.9f,
                        height * 0.6f,
                        height * 0.6f));
                pebbleGraphics.setColor(fill);
                pebbleGraphics.fill(new RoundRectangle2D.Float(
                        -(width * 0.5f),
                        -(height * 0.5f),
                        width,
                        height,
                        height * 0.65f,
                        height * 0.65f));
                pebbleGraphics.setColor(new Color(255, 255, 255, 52));
                pebbleGraphics.fill(new Ellipse2D.Float(-(width * 0.2f), -(height * 0.3f), width * 0.32f, height * 0.24f));
                pebbleGraphics.dispose();
            });
        }
    }

    private static void drawMicroPebbles(Graphics2D graphics, int count, int seed, Color color, float minSize, float maxSize) {
        drawPebbles(graphics, count, seed, new Color[] {color}, minSize, maxSize);
    }

    private static void drawRootThreads(Graphics2D graphics, int count, int seed, Color color) {
        Random random = new Random(seed);
        graphics.setComposite(AlphaComposite.SrcOver);
        for (int index = 0; index < count; index++) {
            float x = random.nextFloat() * SIZE;
            float y = random.nextFloat() * SIZE;
            float radius = 16f + (random.nextFloat() * 12f);
            withWrapPositions(x, y, radius, (drawX, drawY) -> {
                Path2D.Float root = new Path2D.Float();
                root.moveTo(drawX, drawY);
                root.curveTo(
                        drawX + (-8f + (random.nextFloat() * 16f)),
                        drawY + (6f + (random.nextFloat() * 10f)),
                        drawX + (-10f + (random.nextFloat() * 20f)),
                        drawY + (12f + (random.nextFloat() * 16f)),
                        drawX + (-12f + (random.nextFloat() * 24f)),
                        drawY + (16f + (random.nextFloat() * 20f)));
                graphics.setStroke(new BasicStroke(1f + (random.nextFloat() * 0.7f), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.setColor(color);
                graphics.draw(root);
            });
        }
    }

    private static void drawRockFaces(Graphics2D graphics, int count, int seed) {
        Random random = new Random(seed);
        for (int index = 0; index < count; index++) {
            float x = random.nextFloat() * SIZE;
            float y = random.nextFloat() * SIZE;
            float width = 14f + (random.nextFloat() * 18f);
            float height = 12f + (random.nextFloat() * 16f);
            float radius = Math.max(width, height);
            withWrapPositions(x, y, radius, (drawX, drawY) -> {
                Color baseShadow = new Color(72 + random.nextInt(12), 76 + random.nextInt(12), 82 + random.nextInt(12), 124);
                Color baseFill = new Color(126 + random.nextInt(16), 131 + random.nextInt(16), 137 + random.nextInt(14), 156);
                Color edge = new Color(74 + random.nextInt(10), 79 + random.nextInt(10), 84 + random.nextInt(10), 176);

                Path2D.Float rock = new Path2D.Float();
                rock.moveTo(drawX - width * 0.48f, drawY - height * 0.18f);
                rock.lineTo(drawX - width * 0.2f, drawY - height * 0.48f);
                rock.lineTo(drawX + width * 0.12f, drawY - height * 0.42f);
                rock.lineTo(drawX + width * 0.42f, drawY - height * 0.08f);
                rock.lineTo(drawX + width * 0.34f, drawY + height * 0.32f);
                rock.lineTo(drawX - width * 0.06f, drawY + height * 0.46f);
                rock.lineTo(drawX - width * 0.42f, drawY + height * 0.18f);
                rock.closePath();

                graphics.setColor(new Color(44, 48, 54, 54));
                graphics.fill(new RoundRectangle2D.Float(
                        drawX - width * 0.45f,
                        drawY - height * 0.42f + 1.1f,
                        width * 0.9f,
                        height * 0.84f,
                        6f,
                        6f));
                graphics.setColor(baseShadow);
                graphics.fill(rock);
                graphics.setColor(baseFill);
                graphics.fill(rock);
                graphics.setColor(edge);
                graphics.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.draw(rock);

                Path2D.Float highlightFacet = new Path2D.Float();
                highlightFacet.moveTo(drawX - width * 0.18f, drawY - height * 0.3f);
                highlightFacet.lineTo(drawX + width * 0.06f, drawY - height * 0.36f);
                highlightFacet.lineTo(drawX + width * 0.18f, drawY - height * 0.1f);
                highlightFacet.lineTo(drawX - width * 0.05f, drawY + height * 0.02f);
                highlightFacet.closePath();
                graphics.setColor(new Color(193, 198, 201, 76));
                graphics.fill(highlightFacet);

                Path2D.Float shadowFacet = new Path2D.Float();
                shadowFacet.moveTo(drawX + width * 0.02f, drawY + height * 0.02f);
                shadowFacet.lineTo(drawX + width * 0.32f, drawY - height * 0.05f);
                shadowFacet.lineTo(drawX + width * 0.24f, drawY + height * 0.23f);
                shadowFacet.lineTo(drawX - width * 0.02f, drawY + height * 0.28f);
                shadowFacet.closePath();
                graphics.setColor(new Color(72, 76, 82, 88));
                graphics.fill(shadowFacet);
            });
        }
    }

    private static void drawStoneCracks(Graphics2D graphics, int count, int seed) {
        Random random = new Random(seed);
        for (int index = 0; index < count; index++) {
            float x = random.nextFloat() * SIZE;
            float y = random.nextFloat() * SIZE;
            float radius = 26f + (random.nextFloat() * 18f);
            withWrapPositions(x, y, radius, (drawX, drawY) -> {
                Path2D.Float crack = new Path2D.Float();
                crack.moveTo(drawX, drawY);
                crack.curveTo(
                        drawX + (-10f + (random.nextFloat() * 14f)),
                        drawY + (-4f + (random.nextFloat() * 8f)),
                        drawX + (-18f + (random.nextFloat() * 26f)),
                        drawY + (8f + (random.nextFloat() * 14f)),
                        drawX + (-24f + (random.nextFloat() * 34f)),
                        drawY + (14f + (random.nextFloat() * 18f)));
                graphics.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                graphics.setColor(new Color(56, 61, 67, 138));
                graphics.draw(crack);
            });
        }
    }

    private static void drawMossPatches(Graphics2D graphics, int count, int seed) {
        Random random = new Random(seed);
        for (int index = 0; index < count; index++) {
            float x = random.nextFloat() * SIZE;
            float y = random.nextFloat() * SIZE;
            float radius = 5f + (random.nextFloat() * 5f);
            withWrapPositions(x, y, radius, (drawX, drawY) -> {
                for (int lobe = 0; lobe < 3; lobe++) {
                    float offsetX = (-radius * 0.35f) + (random.nextFloat() * radius * 0.7f);
                    float offsetY = (-radius * 0.28f) + (random.nextFloat() * radius * 0.56f);
                    float width = radius * (0.6f + (random.nextFloat() * 0.45f));
                    float height = radius * (0.34f + (random.nextFloat() * 0.28f));
                    graphics.setColor(new Color(69, 109, 52, 98));
                    graphics.fill(new Ellipse2D.Float(
                            drawX + offsetX - (width * 0.5f),
                            drawY + offsetY - (height * 0.5f),
                            width,
                            height));
                    graphics.setColor(new Color(103, 146, 76, 68));
                    graphics.fill(new Ellipse2D.Float(
                            drawX + offsetX - (width * 0.26f),
                            drawY + offsetY - (height * 0.2f),
                            width * 0.52f,
                            height * 0.4f));
                }
            });
        }
    }

    private static void drawSandRipples(Graphics2D graphics, int lines, int seed) {
        Random random = new Random(seed);
        graphics.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int index = 0; index < lines; index++) {
            float y = 8f + (index * (SIZE / (float) lines)) + (random.nextFloat() * 6f);
            Path2D.Float ripple = new Path2D.Float();
            ripple.moveTo(-6f, y);
            for (int x = 0; x <= SIZE + 10; x += 12) {
                float offsetY = y + (float) Math.sin((x * 0.16f) + (index * 0.85f)) * (2.2f + random.nextFloat());
                ripple.lineTo(x, offsetY);
            }
            graphics.setColor(new Color(138, 121, 87, 42));
            graphics.draw(ripple);
        }
    }

    private static void drawCloudPuffs(Graphics2D graphics, int count, int seed) {
        Random random = new Random(seed);
        for (int index = 0; index < count; index++) {
            float x = random.nextFloat() * SIZE;
            float y = random.nextFloat() * SIZE;
            float radius = 10f + (random.nextFloat() * 10f);
            withWrapPositions(x, y, radius, (drawX, drawY) -> {
                for (int lobe = 0; lobe < 4; lobe++) {
                    float offsetX = (-radius * 0.45f) + (random.nextFloat() * radius * 0.9f);
                    float offsetY = (-radius * 0.25f) + (random.nextFloat() * radius * 0.5f);
                    float width = radius * (0.7f + (random.nextFloat() * 0.45f));
                    float height = radius * (0.48f + (random.nextFloat() * 0.3f));
                    graphics.setColor(new Color(204, 219, 230, 84));
                    graphics.fill(new Ellipse2D.Float(
                            drawX + offsetX - (width * 0.5f),
                            drawY + offsetY - (height * 0.5f),
                            width,
                            height));
                    graphics.setColor(new Color(248, 249, 253, 54));
                    graphics.fill(new Ellipse2D.Float(
                            drawX + offsetX - (width * 0.22f),
                            drawY + offsetY - (height * 0.18f),
                            width * 0.44f,
                            height * 0.36f));
                }
            });
        }
    }

    private static void drawCloudVeins(Graphics2D graphics, int count, int seed) {
        Random random = new Random(seed);
        for (int index = 0; index < count; index++) {
            float y = 10f + (index * (SIZE / (float) count)) + (random.nextFloat() * 8f);
            Path2D.Float vein = new Path2D.Float();
            vein.moveTo(-4f, y);
            for (int x = 0; x <= SIZE + 8; x += 10) {
                float offsetY = y + (float) Math.sin((x * 0.14f) + (index * 0.72f)) * (1.8f + random.nextFloat() * 1.2f);
                vein.lineTo(x, offsetY);
            }
            graphics.setStroke(new BasicStroke(2.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.setColor(new Color(255, 255, 255, 22));
            graphics.draw(vein);
        }
    }

    private static void drawLeaf(
            Graphics2D graphics,
            float centerX,
            float centerY,
            float angle,
            float length,
            float width,
            Color shadowColor,
            Color highlightColor) {
        Graphics2D leafGraphics = (Graphics2D) graphics.create();
        leafGraphics.translate(centerX, centerY);
        leafGraphics.rotate(angle);
        leafGraphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        Path2D.Float shadowLeaf = new Path2D.Float();
        shadowLeaf.moveTo(-width * 0.24f, -length * 0.52f);
        shadowLeaf.quadTo(width * 0.76f, -length * 0.18f, 0f, length * 0.5f);
        shadowLeaf.quadTo(-width * 0.88f, -length * 0.18f, -width * 0.24f, -length * 0.52f);
        shadowLeaf.closePath();
        leafGraphics.setColor(shadowColor);
        leafGraphics.fill(shadowLeaf);

        Path2D.Float highlightLeaf = new Path2D.Float();
        highlightLeaf.moveTo(0f, -length * 0.5f);
        highlightLeaf.quadTo(width * 0.58f, -length * 0.1f, 0f, length * 0.5f);
        highlightLeaf.quadTo(-width * 0.62f, -length * 0.16f, 0f, -length * 0.5f);
        highlightLeaf.closePath();
        leafGraphics.setColor(highlightColor);
        leafGraphics.fill(highlightLeaf);

        leafGraphics.setColor(new Color(20, 47, 18, 110));
        leafGraphics.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        leafGraphics.drawLine(0, Math.round(-length * 0.48f), 0, Math.round(length * 0.42f));
        leafGraphics.dispose();
    }

    private static void forEachPixel(BufferedImage image, PixelColorFunction pixelColorFunction) {
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                image.setRGB(x, y, pixelColorFunction.colorAt(x, y));
            }
        }
    }

    private static Graphics2D graphics(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setComposite(AlphaComposite.SrcOver);
        return graphics;
    }

    private static void withWrapPositions(float x, float y, float radius, WrappedPainter painter) {
        float[] xPositions = wrapPositions(x, radius);
        float[] yPositions = wrapPositions(y, radius);
        for (float drawX : xPositions) {
            if (Float.isNaN(drawX)) {
                continue;
            }
            for (float drawY : yPositions) {
                if (Float.isNaN(drawY)) {
                    continue;
                }
                painter.draw(drawX, drawY);
            }
        }
    }

    private static float[] wrapPositions(float coordinate, float radius) {
        float[] positions = {coordinate, Float.NaN, Float.NaN};
        int index = 1;
        if (coordinate - radius < 0f) {
            positions[index++] = coordinate + SIZE;
        }
        if (coordinate + radius >= SIZE) {
            positions[index] = coordinate - SIZE;
        }
        return positions;
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

    private static double smoothStep(double value) {
        return value * value * (3.0 - (2.0 * value));
    }

    private static double lerp(double first, double second, double amount) {
        return first + ((second - first) * amount);
    }

    private static int lerpColor(int first, int second, double amount) {
        int firstRed = (first >> 16) & 0xFF;
        int firstGreen = (first >> 8) & 0xFF;
        int firstBlue = first & 0xFF;
        int secondRed = (second >> 16) & 0xFF;
        int secondGreen = (second >> 8) & 0xFF;
        int secondBlue = second & 0xFF;

        int red = clampColor((int) Math.round(lerp(firstRed, secondRed, amount)));
        int green = clampColor((int) Math.round(lerp(firstGreen, secondGreen, amount)));
        int blue = clampColor((int) Math.round(lerp(firstBlue, secondBlue, amount)));
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

    @FunctionalInterface
    private interface WrappedPainter {
        void draw(float x, float y);
    }

    @FunctionalInterface
    private interface PixelColorFunction {
        int colorAt(int x, int y);
    }
}
