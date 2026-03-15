package io.github.ainick2469.pixelsurvival.app;

import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.app.SimpleApplication;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import io.github.ainick2469.pixelsurvival.rendering.world.ChunkRenderManager;
import io.github.ainick2469.pixelsurvival.rendering.world.ChunkRuntimeConfig;
import io.github.ainick2469.pixelsurvival.rendering.world.ChunkRuntimeMetrics;
import io.github.ainick2469.pixelsurvival.session.LocalHostSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PixelSurvivalApplication extends SimpleApplication {
    private static final String INPUT_TOGGLE_MOUSE_CAPTURE = "pixel_survival_toggle_mouse_capture";
    private static final String INPUT_QUIT_GAME = "pixel_survival_quit_game";
    private static final Logger LOGGER = LoggerFactory.getLogger(PixelSurvivalApplication.class);

    private LocalHostSession session;
    private ChunkRenderManager chunkRenderManager;
    private BitmapText hud;
    private boolean mouseLookCaptured;
    private float smoothedFrameTimeSeconds = 1f / 60f;

    private final ActionListener inputListener = (name, isPressed, timePerFrame) -> {
        if (!isPressed) {
            return;
        }

        if (INPUT_TOGGLE_MOUSE_CAPTURE.equals(name)) {
            setMouseLookCaptured(!mouseLookCaptured);
        } else if (INPUT_QUIT_GAME.equals(name)) {
            stop();
        }
    };

    @Override
    public void simpleInitApp() {
        setDisplayFps(false);
        setDisplayStatView(false);

        configureLighting();
        configureViewport();
        bootstrapSession();
        configureCamera();
        chunkRenderManager.primeAround(cam.getLocation());
        configureInput();
        attachHud();
    }

    private void configureCamera() {
        float focusX = 8f;
        float focusZ = 8f;
        float surfaceY = session.worldService().findSurfaceY((int) focusX, (int) focusZ);

        flyCam.setMoveSpeed(42f);
        flyCam.setRotationSpeed(2.5f);
        cam.setLocation(new Vector3f(focusX + 18f, surfaceY + 22f, focusZ + 20f));
        cam.lookAt(new Vector3f(focusX, surfaceY + 2f, focusZ), Vector3f.UNIT_Y);
    }

    private void configureLighting() {
        AmbientLight ambientLight = new AmbientLight();
        ambientLight.setColor(new ColorRGBA(0.84f, 0.87f, 0.93f, 1f).mult(0.26f));
        rootNode.addLight(ambientLight);

        DirectionalLight sunlight = new DirectionalLight();
        sunlight.setDirection(new Vector3f(-0.7f, -1.0f, -0.4f).normalizeLocal());
        sunlight.setColor(new ColorRGBA(1.00f, 0.97f, 0.90f, 1f).mult(1.15f));
        rootNode.addLight(sunlight);
    }

    private void configureViewport() {
        viewPort.setBackgroundColor(new ColorRGBA(0.58f, 0.74f, 0.90f, 1f));
    }

    private void bootstrapSession() {
        session = LocalHostSession.bootstrap();
        session.start();
        chunkRenderManager = new ChunkRenderManager(
                rootNode,
                assetManager,
                session.worldService(),
                session.registries(),
                ChunkRuntimeConfig.productionDefaults());

        LOGGER.info(
                "Loaded {} block definitions and {} settings presets from {}",
                session.registries().blocks().size(),
                session.registries().survivalPresets().size(),
                session.registries().dataRoot());
    }

    private void configureInput() {
        if (inputManager.hasMapping(SimpleApplication.INPUT_MAPPING_EXIT)) {
            inputManager.deleteMapping(SimpleApplication.INPUT_MAPPING_EXIT);
        }

        inputManager.addMapping(INPUT_TOGGLE_MOUSE_CAPTURE, new KeyTrigger(KeyInput.KEY_ESCAPE));
        inputManager.addMapping(INPUT_QUIT_GAME, new KeyTrigger(KeyInput.KEY_F10));
        inputManager.addListener(inputListener, INPUT_TOGGLE_MOUSE_CAPTURE, INPUT_QUIT_GAME);
        setMouseLookCaptured(true);
    }

    private void attachHud() {
        BitmapFont font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        hud = new BitmapText(font);
        hud.setSize(font.getCharSet().getRenderedSize() * 1.1f);
        hud.setColor(ColorRGBA.White);
        hud.setText(buildHudText());
        hud.setLocalTranslation(16f, settings.getHeight() - 16f, 0f);
        guiNode.attachChild(hud);
    }

    private void setMouseLookCaptured(boolean captured) {
        mouseLookCaptured = captured;
        flyCam.setEnabled(captured);
        inputManager.setCursorVisible(!captured);
        if (hud != null) {
            hud.setText(buildHudText());
        }
    }

    private String buildHudText() {
        String mouseMode = mouseLookCaptured ? "Captured" : "Released";
        ChunkRuntimeMetrics runtimeMetrics =
                chunkRenderManager == null ? ChunkRuntimeMetrics.empty() : chunkRenderManager.metrics();
        Runtime runtime = Runtime.getRuntime();
        long usedHeapMegabytes = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        float framesPerSecond = 1f / Math.max(smoothedFrameTimeSeconds, 0.0001f);

        return "Pixel Survival v" + GameVersion.CURRENT + " | Mode: " + session.mode().name()
                + "\nFPS " + Math.round(framesPerSecond)
                + " | Frame " + String.format("%.1f", smoothedFrameTimeSeconds * 1000f) + " ms"
                + " | Loaded " + runtimeMetrics.loadedChunkCount()
                + " | Rendered " + runtimeMetrics.renderedChunkCount()
                + " | Sim " + runtimeMetrics.simulatedChunkCount()
                + " | LoadQ " + runtimeMetrics.pendingLoadCount()
                + " | MeshQ " + runtimeMetrics.pendingMeshBuildCount()
                + " | Faces " + runtimeMetrics.renderedFaceCount()
                + " | Heap " + usedHeapMegabytes + " MB"
                + "\nWASD move | Mouse look | Shift fast | Esc mouse " + mouseMode + " | F10 quit";
    }

    @Override
    public void simpleUpdate(float timePerFrame) {
        smoothedFrameTimeSeconds = (smoothedFrameTimeSeconds * 0.9f) + (timePerFrame * 0.1f);
        if (chunkRenderManager != null) {
            chunkRenderManager.update(cam.getLocation());
        }
        if (hud != null) {
            hud.setText(buildHudText());
        }
    }

    @Override
    public void destroy() {
        if (chunkRenderManager != null) {
            chunkRenderManager.close();
        }
        super.destroy();
    }
}
