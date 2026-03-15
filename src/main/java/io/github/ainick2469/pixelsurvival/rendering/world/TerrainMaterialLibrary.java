package io.github.ainick2469.pixelsurvival.rendering.world;

import com.jme3.asset.AssetInfo;
import com.jme3.asset.AssetKey;
import com.jme3.asset.AssetManager;
import com.jme3.asset.TextureKey;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.texture.Texture;
import com.jme3.texture.Texture2D;
import com.jme3.texture.plugins.AWTLoader;
import io.github.ainick2469.pixelsurvival.world.block.BlockFaceTextureReference;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

public final class TerrainMaterialLibrary {
    private final AssetManager assetManager;
    private final Map<TerrainMaterialKey, Material> materialCache = new ConcurrentHashMap<>();
    private final Map<BlockFaceTextureReference, Texture> textureCache = new ConcurrentHashMap<>();

    public TerrainMaterialLibrary(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    public Material materialFor(TerrainMaterialKey materialKey) {
        return materialCache.computeIfAbsent(materialKey, this::createMaterial);
    }

    private Material createMaterial(TerrainMaterialKey materialKey) {
        Material material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Ambient", ColorRGBA.White.mult(0.5f));
        material.setColor("Diffuse", ColorRGBA.White);
        material.setColor("Specular", ColorRGBA.Black);
        material.setFloat("Shininess", 1f);

        if (materialKey.usesTexture()) {
            material.setTexture("DiffuseMap", textureFor(materialKey.textureReference()));
            return material;
        }

        ColorRGBA color = parseHexColor(materialKey.debugColor());
        material.setColor("Ambient", color.mult(0.45f));
        material.setColor("Diffuse", color);
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

    private Texture textureFor(BlockFaceTextureReference textureReference) {
        return textureCache.computeIfAbsent(textureReference, this::loadTexture);
    }

    private Texture loadTexture(BlockFaceTextureReference textureReference) {
        Texture texture = textureReference.usesCubeNet()
                ? loadCubeNetFace(textureReference)
                : loadDirectTexture(textureReference.texturePath());
        applyTextureSettings(texture);
        return texture;
    }

    private Texture loadDirectTexture(String texturePath) {
        TextureKey textureKey = new TextureKey(Objects.requireNonNull(texturePath, "texturePath"), false);
        textureKey.setGenerateMips(true);
        return assetManager.loadTexture(textureKey);
    }

    private Texture loadCubeNetFace(BlockFaceTextureReference textureReference) {
        AssetInfo assetInfo = assetManager.locateAsset(new AssetKey<>(textureReference.cubeNetTexturePath()));
        if (assetInfo == null) {
            throw new IllegalStateException("Unable to locate cube-net texture asset: " + textureReference.cubeNetTexturePath());
        }

        try (InputStream stream = assetInfo.openStream()) {
            BufferedImage sourceImage = ImageIO.read(stream);
            if (sourceImage == null) {
                throw new IllegalStateException(
                        "Failed to decode cube-net texture image: " + textureReference.cubeNetTexturePath());
            }

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

            return new Texture2D(new AWTLoader().load(faceImage, false));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to load cube-net texture asset: " + textureReference.cubeNetTexturePath(),
                    exception);
        }
    }

    private void applyTextureSettings(Texture texture) {
        texture.setWrap(Texture.WrapMode.Repeat);
        texture.setMinFilter(Texture.MinFilter.Trilinear);
        texture.setMagFilter(Texture.MagFilter.Nearest);
        texture.setAnisotropicFilter(4);
    }
}
