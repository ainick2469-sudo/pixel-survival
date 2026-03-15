package io.github.ainick2469.pixelsurvival.tools;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.ainick2469.pixelsurvival.world.block.BlockTextureFace;
import io.github.ainick2469.pixelsurvival.world.block.CubeNetLayout;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import javax.imageio.ImageIO;

public final class VoxelBlockImporter {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    private static final Set<String> SUPPORTED_INPUT_LAYOUTS = Set.of("cross-3x4", "top-center-cross-3x4");
    private static final CubeNetLayout OUTPUT_LAYOUT = CubeNetLayout.CENTER_TOP_SURROUNDING_SIDES_OUTER_BOTTOM;
    private static final List<BlockTextureFace> REQUIRED_FACES = List.of(
            BlockTextureFace.BACK,
            BlockTextureFace.TOP,
            BlockTextureFace.LEFT,
            BlockTextureFace.FRONT,
            BlockTextureFace.RIGHT,
            BlockTextureFace.BOTTOM);
    private static final Map<BlockTextureFace, String> INPUT_FACE_FIELDS = Map.of(
            BlockTextureFace.BACK, "top",
            BlockTextureFace.TOP, "front",
            BlockTextureFace.LEFT, "left",
            BlockTextureFace.FRONT, "bottom",
            BlockTextureFace.RIGHT, "right",
            BlockTextureFace.BOTTOM, "back");

    public static void main(String[] args) throws Exception {
        ImportOptions options = ImportOptions.parse(args);
        ImportResult result = importVoxelBlock(options);
        System.out.println("Imported voxel block asset:");
        System.out.println("  Input:      " + options.inputPath().toAbsolutePath());
        System.out.println("  Block ID:   " + result.blockId());
        System.out.println("  Block JSON: " + result.blockDefinitionPath().toAbsolutePath());
        System.out.println("  Cube Net:   " + result.cubeNetTexturePath().toAbsolutePath());
    }

    public static ImportResult importVoxelBlock(ImportOptions options) throws IOException {
        Objects.requireNonNull(options, "options");
        VoxelBlockAssetDocument assetDocument = OBJECT_MAPPER.readValue(
                options.inputPath().toFile(),
                VoxelBlockAssetDocument.class);
        validateAssetDocument(assetDocument, options.inputPath());

        String blockId = options.blockId() == null || options.blockId().isBlank()
                ? "pixel_survival:" + slugify(assetDocument.name())
                : options.blockId().trim();
        String localName = localNameOf(blockId);
        String displayName = options.displayName() == null || options.displayName().isBlank()
                ? titleCase(localName)
                : options.displayName().trim();
        String materialFamily = options.materialFamily() == null || options.materialFamily().isBlank()
                ? "decorative"
                : slugify(options.materialFamily());
        String tintKey = options.tintKey() == null || options.tintKey().isBlank()
                ? null
                : options.tintKey().trim();

        Path repoRoot = options.repoRoot().toAbsolutePath().normalize();
        Path cubeNetOutputPath = repoRoot.resolve("src/main/resources/Textures/BlockCubeNets")
                .resolve(localName + "_cube_net.png");
        Path blockDefinitionPath = repoRoot.resolve("data/blocks").resolve(localName + ".json");

        Files.createDirectories(cubeNetOutputPath.getParent());
        Files.createDirectories(blockDefinitionPath.getParent());

        EnumMap<BlockTextureFace, BufferedImage> renderedFaces = new EnumMap<>(BlockTextureFace.class);
        for (BlockTextureFace face : REQUIRED_FACES) {
            VoxelBlockFaceAsset faceAsset = faceAssetFor(assetDocument, face);
            renderedFaces.put(face, renderFace(faceAsset, assetDocument.tileSize()));
        }
        applyUniformSideFace(renderedFaces, options.uniformSideFace());

        BufferedImage cubeNetImage = buildCubeNet(renderedFaces, assetDocument.tileSize());
        ImageIO.write(cubeNetImage, "png", cubeNetOutputPath.toFile());
        writeBlockDefinitionJson(
                blockDefinitionPath,
                blockId,
                displayName,
                materialFamily,
                tintKey,
                options.solid(),
                options.opaque(),
                options.tags(),
                assetDocument.tileSize(),
                localName,
                averageHexColor(renderedFaces.get(BlockTextureFace.TOP)));

        return new ImportResult(blockId, blockDefinitionPath, cubeNetOutputPath, OUTPUT_LAYOUT);
    }

    private static void validateAssetDocument(VoxelBlockAssetDocument assetDocument, Path inputPath) {
        if (!"survivalcraft2.voxel-block-asset".equals(assetDocument.type())) {
            throw new IllegalArgumentException(
                    "Unsupported voxel block asset type in " + inputPath.toAbsolutePath() + ": " + assetDocument.type());
        }
        if (assetDocument.version() != 1) {
            throw new IllegalArgumentException(
                    "Unsupported voxel block asset version in " + inputPath.toAbsolutePath() + ": " + assetDocument.version());
        }
        if (!SUPPORTED_INPUT_LAYOUTS.contains(assetDocument.layout())) {
            throw new IllegalArgumentException(
                    "Unsupported voxelblock layout in " + inputPath.toAbsolutePath()
                            + ". Supported layouts: " + SUPPORTED_INPUT_LAYOUTS + ", found " + assetDocument.layout());
        }
        if (assetDocument.tileSize() < 16) {
            throw new IllegalArgumentException(
                    "voxelblock tileSize must be at least 16. Found " + assetDocument.tileSize());
        }
        if (assetDocument.faces() == null || assetDocument.faces().isEmpty()) {
            throw new IllegalArgumentException("voxelblock asset is missing face definitions.");
        }
        for (BlockTextureFace face : REQUIRED_FACES) {
            if (faceAssetFor(assetDocument, face) == null) {
                throw new IllegalArgumentException("voxelblock asset is missing required face: " + face.name().toLowerCase(Locale.ROOT));
            }
        }
    }

    private static VoxelBlockFaceAsset faceAssetFor(VoxelBlockAssetDocument assetDocument, BlockTextureFace face) {
        return assetDocument.faces().get(INPUT_FACE_FIELDS.get(face));
    }

    private static BufferedImage renderFace(VoxelBlockFaceAsset faceAsset, int tileSize) throws IOException {
        BufferedImage sourceImage = decodeImageDataUrl(faceAsset.imageDataUrl());
        BufferedImage outputImage = new BufferedImage(tileSize, tileSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = outputImage.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);

        FitMode fitMode = FitMode.fromValue(faceAsset.fitMode());
        double baseScale = switch (fitMode) {
            case COVER -> Math.max(tileSize / (double) sourceImage.getWidth(), tileSize / (double) sourceImage.getHeight());
            case CONTAIN -> Math.min(tileSize / (double) sourceImage.getWidth(), tileSize / (double) sourceImage.getHeight());
        };
        double scale = baseScale * Math.max(0.01d, faceAsset.zoom());

        AffineTransform transform = new AffineTransform();
        transform.translate((tileSize / 2.0d) + faceAsset.offsetX(), (tileSize / 2.0d) + faceAsset.offsetY());
        transform.rotate(Math.toRadians(faceAsset.rotation()));
        transform.scale(scale, scale);
        transform.translate(-(sourceImage.getWidth() / 2.0d), -(sourceImage.getHeight() / 2.0d));

        graphics.drawImage(sourceImage, transform, null);
        graphics.dispose();
        return outputImage;
    }

    private static BufferedImage decodeImageDataUrl(String imageDataUrl) throws IOException {
        if (imageDataUrl == null || imageDataUrl.isBlank()) {
            throw new IllegalArgumentException("voxelblock face is missing imageDataUrl.");
        }
        int commaIndex = imageDataUrl.indexOf(',');
        String base64Payload = commaIndex >= 0 ? imageDataUrl.substring(commaIndex + 1) : imageDataUrl;
        byte[] imageBytes = Base64.getDecoder().decode(base64Payload);
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
        if (image == null) {
            throw new IllegalArgumentException("Unable to decode voxelblock face imageDataUrl.");
        }
        if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
            return image;
        }

        BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = converted.createGraphics();
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return converted;
    }

    private static BufferedImage buildCubeNet(Map<BlockTextureFace, BufferedImage> faces, int tileSize) {
        BufferedImage cubeNetImage = new BufferedImage(tileSize * 3, tileSize * 4, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = cubeNetImage.createGraphics();
        for (BlockTextureFace face : REQUIRED_FACES) {
            BufferedImage faceImage = faces.get(face);
            int x = OUTPUT_LAYOUT.tileX(face) * tileSize;
            int y = OUTPUT_LAYOUT.tileY(face) * tileSize;
            graphics.drawImage(faceImage, x, y, null);
        }
        graphics.dispose();
        return cubeNetImage;
    }

    private static void applyUniformSideFace(
            EnumMap<BlockTextureFace, BufferedImage> renderedFaces,
            BlockTextureFace uniformSideFace) {
        if (uniformSideFace == null) {
            return;
        }
        if (!isSideFace(uniformSideFace)) {
            throw new IllegalArgumentException(
                    "uniformSideFace must be one of back, left, front, or right. Found " + uniformSideFace.name().toLowerCase(Locale.ROOT));
        }

        BufferedImage sideImage = renderedFaces.get(uniformSideFace);
        if (sideImage == null) {
            throw new IllegalArgumentException(
                    "Unable to apply uniform side face because " + uniformSideFace.name().toLowerCase(Locale.ROOT)
                            + " was not rendered.");
        }

        for (BlockTextureFace face : List.of(
                BlockTextureFace.BACK,
                BlockTextureFace.LEFT,
                BlockTextureFace.FRONT,
                BlockTextureFace.RIGHT)) {
            renderedFaces.put(face, sideImage);
        }
    }

    private static void writeBlockDefinitionJson(
            Path blockDefinitionPath,
            String blockId,
            String displayName,
            String materialFamily,
            String tintKey,
            boolean solid,
            boolean opaque,
            List<String> tags,
            int tileSize,
            String localName,
            String debugColor) throws IOException {
        ObjectNode block = OBJECT_MAPPER.createObjectNode();
        block.put("id", blockId);
        block.put("displayName", displayName);
        block.put("materialFamily", materialFamily);
        block.put("solid", solid);
        block.put("opaque", opaque);
        block.put("debugColor", debugColor);

        ObjectNode visuals = block.putObject("visuals");
        visuals.put("textureMode", "cube_net");
        visuals.put("cubeNetTexture", "Textures/BlockCubeNets/" + localName + "_cube_net.png");
        visuals.put("cubeNetLayout", OUTPUT_LAYOUT.id());
        if (tintKey == null) {
            visuals.putNull("tintKey");
        } else {
            visuals.put("tintKey", tintKey);
        }

        ArrayNode tagArray = block.putArray("tags");
        for (String tag : tags) {
            tagArray.add(tag);
        }
        tagArray.add("imported");
        tagArray.add("tile_" + tileSize);

        OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(blockDefinitionPath.toFile(), block);
    }

    private static String averageHexColor(BufferedImage image) {
        long totalRed = 0;
        long totalGreen = 0;
        long totalBlue = 0;
        long countedPixels = 0;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (color.getAlpha() == 0) {
                    continue;
                }
                totalRed += color.getRed();
                totalGreen += color.getGreen();
                totalBlue += color.getBlue();
                countedPixels++;
            }
        }

        if (countedPixels == 0) {
            return "#FF00FF";
        }

        int red = (int) Math.round(totalRed / (double) countedPixels);
        int green = (int) Math.round(totalGreen / (double) countedPixels);
        int blue = (int) Math.round(totalBlue / (double) countedPixels);
        return String.format(Locale.ROOT, "#%02X%02X%02X", red, green, blue);
    }

    private static boolean isSideFace(BlockTextureFace face) {
        return switch (face) {
            case BACK, LEFT, FRONT, RIGHT -> true;
            case TOP, BOTTOM -> false;
        };
    }

    private static String localNameOf(String blockId) {
        int separatorIndex = blockId.indexOf(':');
        if (separatorIndex <= 0 || separatorIndex == blockId.length() - 1) {
            throw new IllegalArgumentException("Block ID must be namespaced, for example pixel_survival:custom_block");
        }
        return slugify(blockId.substring(separatorIndex + 1));
    }

    private static String slugify(String value) {
        StringBuilder builder = new StringBuilder();
        for (char character : value.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((character >= 'a' && character <= 'z') || (character >= '0' && character <= '9')) {
                builder.append(character);
            } else if (character == '_' || character == '-' || character == ' ') {
                if (!builder.isEmpty() && builder.charAt(builder.length() - 1) != '_') {
                    builder.append('_');
                }
            }
        }
        String slug = builder.toString();
        if (slug.endsWith("_")) {
            slug = slug.substring(0, slug.length() - 1);
        }
        if (slug.isBlank()) {
            throw new IllegalArgumentException("Unable to derive a block name from: " + value);
        }
        return slug;
    }

    private static String titleCase(String slug) {
        String[] words = slug.replace('-', '_').split("_+");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0)));
            builder.append(word.substring(1));
        }
        return builder.toString();
    }

    public record ImportResult(
            String blockId,
            Path blockDefinitionPath,
            Path cubeNetTexturePath,
            CubeNetLayout cubeNetLayout) {
    }

    public record ImportOptions(
            Path inputPath,
            Path repoRoot,
            String blockId,
            String displayName,
            String materialFamily,
            String tintKey,
            BlockTextureFace uniformSideFace,
            boolean solid,
            boolean opaque,
            List<String> tags) {
        public static ImportOptions parse(String[] args) {
            Path inputPath = null;
            Path repoRoot = Path.of(".");
            String blockId = null;
            String displayName = null;
            String materialFamily = null;
            String tintKey = null;
            BlockTextureFace uniformSideFace = null;
            boolean solid = true;
            boolean opaque = true;
            List<String> tags = List.of("custom_block");

            for (int index = 0; index < args.length; index++) {
                String argument = args[index];
                if (!argument.startsWith("--")) {
                    throw new IllegalArgumentException("Unexpected argument: " + argument);
                }
                if (index + 1 >= args.length) {
                    throw new IllegalArgumentException("Missing value for " + argument);
                }
                String value = args[++index];
                switch (argument) {
                    case "--input" -> inputPath = Path.of(value);
                    case "--repo-root" -> repoRoot = Path.of(value);
                    case "--block-id" -> blockId = value;
                    case "--display-name" -> displayName = value;
                    case "--material-family" -> materialFamily = value;
                    case "--tint-key" -> tintKey = value;
                    case "--uniform-side-face" -> uniformSideFace = BlockTextureFace.valueOf(value.trim().toUpperCase(Locale.ROOT));
                    case "--solid" -> solid = Boolean.parseBoolean(value);
                    case "--opaque" -> opaque = Boolean.parseBoolean(value);
                    case "--tags" -> tags = List.of(value.split(","));
                    default -> throw new IllegalArgumentException("Unknown argument: " + argument);
                }
            }

            if (inputPath == null) {
                throw new IllegalArgumentException("Missing required --input <path-to-voxelblock>");
            }

            return new ImportOptions(
                    inputPath,
                    repoRoot,
                    blockId,
                    displayName,
                    materialFamily,
                    tintKey,
                    uniformSideFace,
                    solid,
                    opaque,
                    tags);
        }
    }

    private enum FitMode {
        COVER,
        CONTAIN;

        private static FitMode fromValue(String value) {
            if (value == null || value.isBlank()) {
                return COVER;
            }
            return switch (value.trim().toLowerCase(Locale.ROOT)) {
                case "contain" -> CONTAIN;
                case "cover" -> COVER;
                default -> throw new IllegalArgumentException("Unsupported voxelblock fitMode: " + value);
            };
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    private record VoxelBlockAssetDocument(
            String type,
            int version,
            String name,
            int tileSize,
            String layout,
            String selectedFace,
            String exportedAt,
            Map<String, VoxelBlockFaceAsset> faces) {
        private VoxelBlockAssetDocument {
            faces = faces == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(faces));
            name = name == null || name.isBlank() ? "imported_block" : name;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    private record VoxelBlockFaceAsset(
            String sourceName,
            double rotation,
            double zoom,
            double offsetX,
            double offsetY,
            String fitMode,
            String imageDataUrl) {
        private VoxelBlockFaceAsset {
            zoom = zoom == 0.0d ? 1.0d : zoom;
            fitMode = fitMode == null || fitMode.isBlank() ? "cover" : fitMode;
        }
    }

    private VoxelBlockImporter() {
    }
}
