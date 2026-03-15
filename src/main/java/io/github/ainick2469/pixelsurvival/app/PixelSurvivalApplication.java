package io.github.ainick2469.pixelsurvival.app;

import com.jme3.app.SimpleApplication;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import io.github.ainick2469.pixelsurvival.rendering.world.ChunkDebugRenderer;
import io.github.ainick2469.pixelsurvival.session.LocalHostSession;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PixelSurvivalApplication extends SimpleApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(PixelSurvivalApplication.class);

    private LocalHostSession session;

    @Override
    public void simpleInitApp() {
        setDisplayFps(false);
        setDisplayStatView(false);

        configureCamera();
        configureLighting();
        configureViewport();
        bootstrapSession();
        attachHud();
    }

    private void configureCamera() {
        flyCam.setMoveSpeed(35f);
        flyCam.setRotationSpeed(2.5f);
        cam.setLocation(new Vector3f(20f, 30f, 28f));
        cam.lookAt(new Vector3f(8f, 20f, 8f), Vector3f.UNIT_Y);
    }

    private void configureLighting() {
        AmbientLight ambientLight = new AmbientLight();
        ambientLight.setColor(ColorRGBA.White.mult(0.5f));
        rootNode.addLight(ambientLight);

        DirectionalLight sunlight = new DirectionalLight();
        sunlight.setDirection(new Vector3f(-0.7f, -1.0f, -0.4f).normalizeLocal());
        sunlight.setColor(ColorRGBA.White.mult(1.2f));
        rootNode.addLight(sunlight);
    }

    private void configureViewport() {
        viewPort.setBackgroundColor(new ColorRGBA(0.66f, 0.82f, 0.95f, 1f));
    }

    private void bootstrapSession() {
        session = LocalHostSession.bootstrap();
        session.start();

        ChunkDebugRenderer chunkRenderer = new ChunkDebugRenderer(assetManager);
        for (ChunkData chunkData : session.worldService().getLoadedChunks()) {
            rootNode.attachChild(chunkRenderer.buildChunkNode(chunkData, session.registries().blocks()));
        }

        LOGGER.info(
                "Loaded {} block definitions, {} settings presets, and {} chunk(s) from {}",
                session.registries().blocks().size(),
                session.registries().survivalPresets().size(),
                session.worldService().getLoadedChunks().size(),
                session.registries().dataRoot());
    }

    private void attachHud() {
        BitmapFont font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        BitmapText hud = new BitmapText(font);
        hud.setSize(font.getCharSet().getRenderedSize() * 1.1f);
        hud.setColor(ColorRGBA.White);
        hud.setText("Pixel Survival v" + GameVersion.CURRENT + " | Mode: " + session.mode().name());
        hud.setLocalTranslation(16f, settings.getHeight() - 16f, 0f);
        guiNode.attachChild(hud);
    }
}
