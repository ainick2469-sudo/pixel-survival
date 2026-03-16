package io.github.ainick2469.pixelsurvival.rendering.world;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.texture.Image;
import com.jme3.texture.Texture;
import com.jme3.texture.TextureArray;
import com.jme3.texture.plugins.AWTLoader;
import com.jme3.util.MipMapGenerator;
import io.github.ainick2469.pixelsurvival.world.block.BlockFaceTextureReference;
import java.awt.RenderingHints;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

public final class TerrainMaterialLibrary {
    private static final ColorRGBA DEFAULT_SEAM_MASK_COLOR = new ColorRGBA(0.56f, 0.72f, 0.88f, 1f);
    // The seam mask was tinting the ultra-distance band into a bright sky-colored ring at 192.
    // Keep the shader path intact, but default it off until we have a subtler band-specific mask.
    private static final float DEFAULT_SEAM_MASK_STRENGTH = 0f;
    private final AssetManager assetManager;
    private final TerrainTexturePalette terrainTexturePalette;
    private final Map<TerrainMaterialKey, Material> materialCache = new ConcurrentHashMap<>();

    public TerrainMaterialLibrary(AssetManager assetManager, TerrainTexturePalette terrainTexturePalette) {
        this.assetManager = assetManager;
        this.terrainTexturePalette = terrainTexturePalette;
    }

    public Material materialFor(TerrainMaterialKey materialKey) {
        return materialCache.computeIfAbsent(materialKey, this::createMaterial);
    }

    private Material createMaterial(TerrainMaterialKey materialKey) {
        if (materialKey.usesTexture()) {
            return createSharedTerrainMaterial();
        }

        return createDebugMaterial(materialKey);
    }

    private Material createDebugMaterial(TerrainMaterialKey materialKey) {
        Material material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Ambient", ColorRGBA.White.mult(0.5f));
        material.setColor("Diffuse", ColorRGBA.White);
        material.setColor("Specular", ColorRGBA.Black);
        material.setFloat("Shininess", 1f);

        ColorRGBA color = parseHexColor(materialKey.debugColor());
        material.setColor("Ambient", color.mult(0.45f));
        material.setColor("Diffuse", color);
        return material;
    }

    private Material createSharedTerrainMaterial() {
        Material material = new Material(assetManager, "Materials/TerrainArrayLighting.j3md");
        material.setTexture("DiffuseMapArray", buildTextureArray());
        material.setColor("SeamMaskColor", DEFAULT_SEAM_MASK_COLOR);
        material.setFloat("SeamMaskStrength", DEFAULT_SEAM_MASK_STRENGTH);
        return material;
    }

    private ColorRGBA parseHexColor(String debugColor) {
        String normalized = debugColor.startsWith("#") ? debugColor.substring(1) : debugColor;
        int rgb = Integer.parseInt(normalized, 16);
        float red = ((rgb >> 16) & 0xFF) / 255f;
        float green = ((rgb >> 8) & 0xFF) / 255f;
        float blue = (rgb & 0xFF) / 255f;
        return new ColorRGBA(red, green, blue, 1f);
    }

    private Texture buildTextureArray() {
        List<BufferedImage> layerImages = new ArrayList<>();
        int targetLayerSize = 1;
        for (BlockFaceTextureReference textureReference : terrainTexturePalette.textureReferences()) {
            BufferedImage layerImage = textureReference.usesCubeNet()
                    ? loadCubeNetFaceImage(textureReference)
                    : loadDirectTextureImage(textureReference.texturePath());
            layerImages.add(layerImage);
            targetLayerSize = Math.max(targetLayerSize, Math.max(layerImage.getWidth(), layerImage.getHeight()));
        }

        targetLayerSize = nextPowerOfTwo(targetLayerSize);
        List<Image> normalizedLayers = new ArrayList<>(layerImages.size());
        for (BufferedImage layerImage : layerImages) {
            BufferedImage normalizedImage = layerImage.getWidth() == targetLayerSize && layerImage.getHeight() == targetLayerSize
                    ? layerImage
                    : resizeImage(layerImage, targetLayerSize);
            Image image = new AWTLoader().load(normalizedImage, false);
            MipMapGenerator.generateMipMaps(image);
            normalizedLayers.add(image);
        }

        TextureArray textureArray = new TextureArray(normalizedLayers);
        applyTextureSettings(textureArray);
        return textureArray;
    }

    private BufferedImage loadDirectTextureImage(String texturePath) {
        return readImage(Objects.requireNonNull(texturePath, "texturePath"));
    }

    private BufferedImage loadCubeNetFaceImage(BlockFaceTextureReference textureReference) {
        BufferedImage sourceImage = readImage(textureReference.cubeNetTexturePath());
        int faceSize = textureReference.cubeNetLayout().faceSize(sourceImage.getWidth(), sourceImage.getHeight());
        int cropX = textureReference.cubeNetLayout().tileX(textureReference.cubeNetFace()) * faceSize;
        int cropY = textureReference.cubeNetLayout().tileY(textureReference.cubeNetFace()) * faceSize;

        BufferedImage faceImage = new BufferedImage(faceSize, faceSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = faceImage.createGraphics();
        graphics.drawImage(
                sourceImage,
                0,
                0,
                faceSize,
                faceSize,
                cropX,
                cropY,
                cropX + faceSize,
                cropY + faceSize,
                null);
        graphics.dispose();
        return faceImage;
    }

    private BufferedImage readImage(String assetPath) {
        AssetInfo assetInfo = assetManager.locateAsset(new AssetKey<>(assetPath));
        if (assetInfo == null) {
            throw new IllegalStateException("Unable to locate terrain texture asset: " + assetPath);
        }

        try (InputStream stream = assetInfo.openStream()) {
            BufferedImage sourceImage = ImageIO.read(stream);
            if (sourceImage == null) {
                throw new IllegalStateException("Failed to decode terrain texture image: " + assetPath);
            }
            if (sourceImage.getType() == BufferedImage.TYPE_INT_ARGB) {
                return sourceImage;
            }

            BufferedImage normalizedImage = new BufferedImage(
                    sourceImage.getWidth(),
                    sourceImage.getHeight(),
                    BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = normalizedImage.createGraphics();
            graphics.drawImage(sourceImage, 0, 0, null);
            graphics.dispose();
            return normalizedImage;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load terrain texture asset: " + assetPath, exception);
        }
    }

    private BufferedImage resizeImage(BufferedImage sourceImage, int targetLayerSize) {
        BufferedImage resizedImage = new BufferedImage(targetLayerSize, targetLayerSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = resizedImage.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        graphics.drawImage(sourceImage, 0, 0, targetLayerSize, targetLayerSize, null);
        graphics.dispose();
        return resizedImage;
    }

    private int nextPowerOfTwo(int value) {
        int powerOfTwo = 1;
        while (powerOfTwo < value) {
            powerOfTwo <<= 1;
        }
        return powerOfTwo;
    }

    private void applyTextureSettings(Texture texture) {
        texture.setWrap(Texture.WrapMode.Repeat);
        texture.setMinFilter(Texture.MinFilter.Trilinear);
        texture.setMagFilter(Texture.MagFilter.Nearest);
        texture.setAnisotropicFilter(4);
    }
}
