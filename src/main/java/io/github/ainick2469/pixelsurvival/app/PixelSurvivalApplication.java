package io.github.ainick2469.pixelsurvival.app;

import com.jme3.input.KeyInput;
import com.jme3.input.controls.ActionListener;
import com.jme3.input.controls.KeyTrigger;
import com.jme3.app.SimpleApplication;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.input.MouseInput;
import com.jme3.light.AmbientLight;
import com.jme3.light.DirectionalLight;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector3f;
import com.jme3.input.controls.MouseButtonTrigger;
import com.jme3.scene.Spatial.CullHint;
import io.github.ainick2469.pixelsurvival.rendering.world.ChunkRenderManager;
import io.github.ainick2469.pixelsurvival.rendering.world.ChunkRuntimeMetrics;
import io.github.ainick2469.pixelsurvival.settings.GraphicsSettings;
import io.github.ainick2469.pixelsurvival.session.LocalHostSession;
import io.github.ainick2469.pixelsurvival.ui.PauseMenuCommand;
import io.github.ainick2469.pixelsurvival.ui.PauseMenuController;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PixelSurvivalApplication extends SimpleApplication {
    private static final String INPUT_TOGGLE_PAUSE_MENU = "pixel_survival_toggle_pause_menu";
    private static final String INPUT_QUIT_GAME = "pixel_survival_quit_game";
    private static final String INPUT_MENU_SELECT = "pixel_survival_menu_select";
    private static final Logger LOGGER = LoggerFactory.getLogger(PixelSurvivalApplication.class);

    private LocalHostSession session;
    private ChunkRenderManager chunkRenderManager;
    private PauseMenuController pauseMenuController;
    private BitmapText hud;
    private boolean mouseLookCaptured;
    private float smoothedFrameTimeSeconds = 1f / 60f;
    private GraphicsSettings graphicsSettings;

    private final ActionListener inputListener = (name, isPressed, timePerFrame) -> {
        if (!isPressed) {
            return;
        }

        if (INPUT_TOGGLE_PAUSE_MENU.equals(name)) {
            togglePauseMenu();
        } else if (INPUT_QUIT_GAME.equals(name)) {
            stop();
        } else if (INPUT_MENU_SELECT.equals(name) && pauseMenuController != null && pauseMenuController.isVisible()) {
            handlePauseMenuCommand(pauseMenuController.handleClick(inputManager.getCursorPosition()));
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
        chunkRenderManager.primeAround(cam.getLocation(), cam.getDirection(), horizontalViewDegrees());
        configureInput();
        attachHud();
    }

    private void configureCamera() {
        float focusX = 8f;
        float focusZ = 8f;
        float surfaceY = session.worldService().findSurfaceY((int) focusX, (int) focusZ);

        flyCam.setMoveSpeed(42f);
        flyCam.setRotationSpeed(2.5f);
        applyViewDistanceSettings();
        cam.setLocation(new Vector3f(focusX + 18f, surfaceY + 22f, focusZ + 20f));
        cam.lookAt(new Vector3f(focusX, surfaceY + 2f, focusZ), Vector3f.UNIT_Y);
    }

    private void configureLighting() {
        AmbientLight ambientLight = new AmbientLight();
        ambientLight.setColor(new ColorRGBA(0.82f, 0.86f, 0.93f, 1f).mult(0.18f));
        rootNode.addLight(ambientLight);

        DirectionalLight sunlight = new DirectionalLight();
        sunlight.setDirection(new Vector3f(-0.62f, -1.0f, -0.45f).normalizeLocal());
        sunlight.setColor(new ColorRGBA(1.00f, 0.97f, 0.91f, 1f).mult(1.08f));
        rootNode.addLight(sunlight);
    }

    private void configureViewport() {
        viewPort.setBackgroundColor(new ColorRGBA(0.56f, 0.72f, 0.88f, 1f));
    }

    private void bootstrapSession() {
        session = LocalHostSession.bootstrap();
        session.start();
        graphicsSettings = session.gameSettings().graphicsSettings();
        chunkRenderManager = new ChunkRenderManager(
                rootNode,
                assetManager,
                session.worldService(),
                session.registries(),
                graphicsSettings.toChunkRuntimeConfig());
        pauseMenuController = new PauseMenuController(
                assetManager,
                guiNode,
                settings.getWidth(),
                settings.getHeight(),
                graphicsSettings.renderDistanceChunks(),
                GraphicsSettings.DEFAULT_RENDER_DISTANCE_CHUNKS,
                GraphicsSettings.MAX_RENDER_DISTANCE_CHUNKS);

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

        inputManager.addMapping(INPUT_TOGGLE_PAUSE_MENU, new KeyTrigger(KeyInput.KEY_ESCAPE));
        inputManager.addMapping(INPUT_QUIT_GAME, new KeyTrigger(KeyInput.KEY_F10));
        inputManager.addMapping(INPUT_MENU_SELECT, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addListener(inputListener, INPUT_TOGGLE_PAUSE_MENU, INPUT_QUIT_GAME, INPUT_MENU_SELECT);
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
            hud.setCullHint(captured ? CullHint.Inherit : CullHint.Always);
            hud.setText(buildHudText());
        }
    }

    private String buildHudText() {
        ChunkRuntimeMetrics runtimeMetrics =
                chunkRenderManager == null ? ChunkRuntimeMetrics.empty() : chunkRenderManager.metrics();
        Runtime runtime = Runtime.getRuntime();
        long usedHeapMegabytes = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        float framesPerSecond = 1f / Math.max(smoothedFrameTimeSeconds, 0.0001f);

        return "Pixel Survival v" + GameVersion.CURRENT + " | Mode: " + session.mode().name()
                + "\nFPS " + Math.round(framesPerSecond)
                + " | Frame " + String.format("%.1f", smoothedFrameTimeSeconds * 1000f) + " ms"
                + " | Render Distance " + graphicsSettings.renderDistanceChunks()
                + " | Loaded " + runtimeMetrics.loadedChunkCount()
                + " | Rendered " + runtimeMetrics.renderedChunkCount()
                + " | Sim " + runtimeMetrics.simulatedChunkCount()
                + " | LoadQ " + runtimeMetrics.pendingLoadCount()
                + " | MeshQ " + runtimeMetrics.pendingMeshBuildCount()
                + " | Faces " + runtimeMetrics.renderedFaceCount()
                + " | Heap " + usedHeapMegabytes + " MB"
                + "\nWASD move | Mouse look | Shift fast | Esc menu | F10 quit";
    }

    @Override
    public void simpleUpdate(float timePerFrame) {
        smoothedFrameTimeSeconds = (smoothedFrameTimeSeconds * 0.9f) + (timePerFrame * 0.1f);
        if (chunkRenderManager != null) {
            chunkRenderManager.update(cam.getLocation(), cam.getDirection(), horizontalViewDegrees());
        }
        if (pauseMenuController != null && pauseMenuController.isVisible()) {
            pauseMenuController.updateHover(inputManager.getCursorPosition());
        }
        if (hud != null) {
            hud.setText(buildHudText());
        }
    }

    @Override
    public void reshape(int width, int height) {
        super.reshape(width, height);
        if (hud != null) {
            hud.setLocalTranslation(16f, height - 16f, 0f);
        }
        if (pauseMenuController != null) {
            pauseMenuController.reshape(width, height);
        }
        applyViewDistanceSettings();
    }

    @Override
    public void destroy() {
        if (chunkRenderManager != null) {
            chunkRenderManager.close();
        }
        super.destroy();
    }

    private void togglePauseMenu() {
        if (pauseMenuController == null) {
            return;
        }

        if (!pauseMenuController.isVisible()) {
            openPauseMenu();
            return;
        }

        if (pauseMenuController.isShowingOptions()) {
            pauseMenuController.showMainMenu();
            return;
        }

        resumeGame();
    }

    private void openPauseMenu() {
        pauseMenuController.setRenderDistanceChunks(graphicsSettings.renderDistanceChunks());
        pauseMenuController.showMainMenu();
        setMouseLookCaptured(false);
    }

    private void resumeGame() {
        pauseMenuController.hide();
        setMouseLookCaptured(true);
    }

    private void handlePauseMenuCommand(PauseMenuCommand command) {
        switch (command) {
            case NONE -> {
            }
            case RESUME_GAME -> resumeGame();
            case QUIT_GAME -> stop();
            case RENDER_DISTANCE_CHANGED -> applyRenderDistance(pauseMenuController.renderDistanceChunks());
        }
    }

    private void applyRenderDistance(int renderDistanceChunks) {
        graphicsSettings = graphicsSettings.withRenderDistanceChunks(renderDistanceChunks);
        chunkRenderManager.setRuntimeConfig(graphicsSettings.toChunkRuntimeConfig());
        applyViewDistanceSettings();
        LOGGER.info("Updated render distance to {} chunks", graphicsSettings.renderDistanceChunks());
    }

    private void applyViewDistanceSettings() {
        if (graphicsSettings == null || cam == null) {
            return;
        }
        float aspectRatio = (float) cam.getWidth() / Math.max(1, cam.getHeight());
        float renderRadiusWorldUnits = (graphicsSettings.renderDistanceChunks() + 4f) * ChunkData.SIZE_X;
        float farClip = Math.max(384f, renderRadiusWorldUnits * 2.2f);
        cam.setFrustumPerspective(45f, aspectRatio, 0.1f, farClip);
    }

    private float horizontalViewDegrees() {
        float nearClip = Math.max(0.0001f, cam.getFrustumNear());
        float halfHorizontal = Math.max(Math.abs(cam.getFrustumLeft()), Math.abs(cam.getFrustumRight()));
        return (float) Math.toDegrees(Math.atan(halfHorizontal / nearClip) * 2.0);
    }
}
