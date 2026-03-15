package io.github.ainick2469.pixelsurvival.rendering.world;

import com.jme3.asset.AssetManager;
import com.jme3.asset.TextureKey;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.texture.Texture;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class TerrainMaterialLibrary {
    private final AssetManager assetManager;
    private final Map<TerrainMaterialKey, Material> materialCache = new ConcurrentHashMap<>();

    public TerrainMaterialLibrary(AssetManager assetManager) {
        this.assetManager = assetManager;
    }

    public Material materialFor(TerrainMaterialKey materialKey) {
        return materialCache.computeIfAbsent(materialKey, this::createMaterial);
    }

    private Material createMaterial(TerrainMaterialKey materialKey) {
        Material material = new Material(assetManager, "Common/MatDefs/Light/Lighting.j3md");
        material.setBoolean("UseMaterialColors", true);
        material.setColor("Ambient", ColorRGBA.White.mult(0.7f));
        material.setColor("Diffuse", ColorRGBA.White);
        material.setColor("Specular", ColorRGBA.Black);
        material.setFloat("Shininess", 1f);

        if (materialKey.usesTexture()) {
            TextureKey textureKey = new TextureKey(materialKey.texturePath(), false);
            textureKey.setGenerateMips(true);
            Texture texture = assetManager.loadTexture(textureKey);
            texture.setWrap(Texture.WrapMode.Repeat);
            texture.setMinFilter(Texture.MinFilter.Trilinear);
            texture.setMagFilter(Texture.MagFilter.Bilinear);
            texture.setAnisotropicFilter(4);
            material.setTexture("DiffuseMap", texture);
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
}
