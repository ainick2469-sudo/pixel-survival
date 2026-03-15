package io.github.ainick2469.pixelsurvival.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.ainick2469.pixelsurvival.world.block.BlockTextureFace;
import io.github.ainick2469.pixelsurvival.world.block.CubeNetLayout;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoxelBlockImporterTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void importsVoxelBlockIntoRuntimeCubeNetAndBlockDefinition() throws Exception {
        Path repoRoot = tempDir.resolve("repo");
        Files.createDirectories(repoRoot.resolve("src/main/resources/Textures/BlockCubeNets"));
        Files.createDirectories(repoRoot.resolve("data/blocks"));

        Path inputPath = tempDir.resolve("sample.voxelblock");
        Files.writeString(inputPath, sampleVoxelBlockJson());

        VoxelBlockImporter.ImportResult result = VoxelBlockImporter.importVoxelBlock(
                new VoxelBlockImporter.ImportOptions(
                        inputPath,
                        repoRoot,
                        "pixel_survival:test_imported_block",
                        "Test Imported Block",
                        "decorative",
                        null,
                        true,
                        true,
                        java.util.List.of("test_block")));

        assertEquals("pixel_survival:test_imported_block", result.blockId());
        assertTrue(Files.exists(result.blockDefinitionPath()));
        assertTrue(Files.exists(result.cubeNetTexturePath()));
        assertEquals(CubeNetLayout.BACK_TOP_LEFT_FRONT_RIGHT_BOTTOM, result.cubeNetLayout());

        JsonNode blockJson = OBJECT_MAPPER.readTree(result.blockDefinitionPath().toFile());
        assertEquals("pixel_survival:test_imported_block", blockJson.get("id").asText());
        assertEquals("Textures/BlockCubeNets/test_imported_block_cube_net.png",
                blockJson.get("visuals").get("cubeNetTexture").asText());
        assertEquals("cube_net", blockJson.get("visuals").get("textureMode").asText());

        BufferedImage cubeNetImage = ImageIO.read(result.cubeNetTexturePath().toFile());
        assertFaceCenterColor(cubeNetImage, BlockTextureFace.BACK, new Color(220, 20, 60));
        assertFaceCenterColor(cubeNetImage, BlockTextureFace.TOP, new Color(60, 179, 113));
        assertFaceCenterColor(cubeNetImage, BlockTextureFace.LEFT, new Color(65, 105, 225));
        assertFaceCenterColor(cubeNetImage, BlockTextureFace.FRONT, new Color(255, 165, 0));
        assertFaceCenterColor(cubeNetImage, BlockTextureFace.RIGHT, new Color(138, 43, 226));
        assertFaceCenterColor(cubeNetImage, BlockTextureFace.BOTTOM, new Color(210, 180, 140));
    }

    @Test
    void rejectsUnsupportedVoxelBlockLayout() throws Exception {
        Path repoRoot = tempDir.resolve("repo_invalid_layout");
        Files.createDirectories(repoRoot.resolve("src/main/resources/Textures/BlockCubeNets"));
        Files.createDirectories(repoRoot.resolve("data/blocks"));

        Path inputPath = tempDir.resolve("invalid-layout.voxelblock");
        Files.writeString(inputPath, sampleVoxelBlockJson().replace("\"cross-3x4\"", "\"row-major\""));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> VoxelBlockImporter.importVoxelBlock(
                        new VoxelBlockImporter.ImportOptions(
                                inputPath,
                                repoRoot,
                                "pixel_survival:invalid_layout_block",
                                "Invalid Layout Block",
                                "decorative",
                                null,
                                true,
                                true,
                                java.util.List.of("invalid_layout"))));

        assertTrue(exception.getMessage().contains("Unsupported voxelblock layout"));
    }

    private void assertFaceCenterColor(BufferedImage cubeNetImage, BlockTextureFace face, Color expected) {
        int tileSize = cubeNetImage.getWidth() / 3;
        int sampleX = (CubeNetLayout.BACK_TOP_LEFT_FRONT_RIGHT_BOTTOM.tileX(face) * tileSize) + (tileSize / 2);
        int sampleY = (CubeNetLayout.BACK_TOP_LEFT_FRONT_RIGHT_BOTTOM.tileY(face) * tileSize) + (tileSize / 2);
        Color actual = new Color(cubeNetImage.getRGB(sampleX, sampleY), true);
        assertEquals(expected.getRed(), actual.getRed());
        assertEquals(expected.getGreen(), actual.getGreen());
        assertEquals(expected.getBlue(), actual.getBlue());
    }

    private String sampleVoxelBlockJson() throws Exception {
        Map<String, Color> faceColors = Map.of(
                "back", new Color(220, 20, 60),
                "top", new Color(60, 179, 113),
                "left", new Color(65, 105, 225),
                "front", new Color(255, 165, 0),
                "right", new Color(138, 43, 226),
                "bottom", new Color(210, 180, 140));

        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"type\": \"survivalcraft2.voxel-block-asset\",\n");
        builder.append("  \"version\": 1,\n");
        builder.append("  \"name\": \"test-imported-block\",\n");
        builder.append("  \"tileSize\": 128,\n");
        builder.append("  \"layout\": \"cross-3x4\",\n");
        builder.append("  \"selectedFace\": \"top\",\n");
        builder.append("  \"exportedAt\": \"2026-03-15T18:30:26.605Z\",\n");
        builder.append("  \"faces\": {\n");

        int index = 0;
        for (Map.Entry<String, Color> entry : faceColors.entrySet()) {
            builder.append("    \"").append(entry.getKey()).append("\": {\n");
            builder.append("      \"sourceName\": \"").append(entry.getKey()).append(".png\",\n");
            builder.append("      \"rotation\": 0,\n");
            builder.append("      \"zoom\": 1,\n");
            builder.append("      \"offsetX\": 0,\n");
            builder.append("      \"offsetY\": 0,\n");
            builder.append("      \"fitMode\": \"cover\",\n");
            builder.append("      \"imageDataUrl\": \"").append(dataUrl(entry.getValue())).append("\"\n");
            builder.append("    }");
            if (index < faceColors.size() - 1) {
                builder.append(',');
            }
            builder.append('\n');
            index++;
        }

        builder.append("  }\n");
        builder.append("}\n");
        return builder.toString();
    }

    private String dataUrl(Color color) throws Exception {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, color.getRGB());
            }
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageIO.write(image, "png", outputStream);
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(outputStream.toByteArray());
    }
}
