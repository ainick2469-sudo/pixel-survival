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
import com.jme3.system.AppSettings;
import com.jme3.system.JmeContext;
import com.jme3.system.lwjgl.LwjglWindow;
import io.github.ainick2469.pixelsurvival.rendering.world.ChunkRenderManager;
import io.github.ainick2469.pixelsurvival.rendering.world.ChunkRuntimeMetrics;
import io.github.ainick2469.pixelsurvival.settings.GraphicsSettings;
import io.github.ainick2469.pixelsurvival.session.LocalHostSession;
import io.github.ainick2469.pixelsurvival.ui.PauseMenuCommand;
import io.github.ainick2469.pixelsurvival.ui.PauseMenuController;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.List;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVidMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PixelSurvivalApplication extends SimpleApplication implements ScreenshotCaptureProcessor.ScreenshotFeedbackSink {
    private static final String INPUT_TOGGLE_PAUSE_MENU = "pixel_survival_toggle_pause_menu";
    private static final String INPUT_QUIT_GAME = "pixel_survival_quit_game";
    private static final String INPUT_MENU_SELECT = "pixel_survival_menu_select";
    private static final String INPUT_TAKE_SCREENSHOT = "pixel_survival_take_screenshot";
    private static final String INPUT_TOGGLE_FULLSCREEN = "pixel_survival_toggle_fullscreen";
    private static final Logger LOGGER = LoggerFactory.getLogger(PixelSurvivalApplication.class);
    private static final List<GarbageCollectorMXBean> GARBAGE_COLLECTORS = ManagementFactory.getGarbageCollectorMXBeans();
    private static final long STATUS_MESSAGE_DURATION_NANOS = 4_000_000_000L;

    private LocalHostSession session;
    private ChunkRenderManager chunkRenderManager;
    private ScreenshotCaptureProcessor screenshotCaptureProcessor;
    private PauseMenuController pauseMenuController;
    private BitmapText hud;
    private boolean mouseLookCaptured;
    private float smoothedFrameTimeSeconds = 1f / 60f;
    private float smoothedChunkUpdateMilliseconds;
    private float smoothedUiUpdateMilliseconds;
    private float smoothedGarbageCollectionMilliseconds;
    private GraphicsSettings graphicsSettings;
    private long previousGarbageCollectionTimeMilliseconds = currentGarbageCollectionTimeMilliseconds();
    private long statusMessageExpiresAtNanos;
    private String statusMessage;
    private int windowedWidth = 1600;
    private int windowedHeight = 900;
    private int windowedX = 160;
    private int windowedY = 90;

    private final ActionListener inputListener = (name, isPressed, timePerFrame) -> {
        if (!isPressed) {
            return;
        }

        if (INPUT_TOGGLE_PAUSE_MENU.equals(name)) {
            togglePauseMenu();
        } else if (INPUT_QUIT_GAME.equals(name)) {
            stop();
        } else if (INPUT_TAKE_SCREENSHOT.equals(name)) {
            takeScreenshot();
        } else if (INPUT_TOGGLE_FULLSCREEN.equals(name)) {
            toggleFullscreen();
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
        screenshotCaptureProcessor =
                new ScreenshotCaptureProcessor(session.registries().dataRoot().getParent().resolve("screenshots"), this);
        guiViewPort.addProcessor(screenshotCaptureProcessor);

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
        inputManager.addMapping(
                INPUT_TAKE_SCREENSHOT,
                new KeyTrigger(KeyInput.KEY_F2),
                new KeyTrigger(KeyInput.KEY_PRTSCR),
                new KeyTrigger(KeyInput.KEY_SYSRQ));
        inputManager.addMapping(INPUT_TOGGLE_FULLSCREEN, new KeyTrigger(KeyInput.KEY_F11));
        inputManager.addMapping(INPUT_MENU_SELECT, new MouseButtonTrigger(MouseInput.BUTTON_LEFT));
        inputManager.addListener(
                inputListener,
                INPUT_TOGGLE_PAUSE_MENU,
                INPUT_QUIT_GAME,
                INPUT_TAKE_SCREENSHOT,
                INPUT_TOGGLE_FULLSCREEN,
                INPUT_MENU_SELECT);
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
        long chunkStorageMegabytes = runtimeMetrics.estimatedLoadedChunkStorageBytes() / (1024 * 1024);
        float approximateRenderAndEngineMilliseconds = Math.max(
                0f,
                (smoothedFrameTimeSeconds * 1000f)
                        - smoothedChunkUpdateMilliseconds
                        - smoothedUiUpdateMilliseconds
                        - smoothedGarbageCollectionMilliseconds);

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
                + " | ChunkMem " + chunkStorageMegabytes + " MB"
                + "\nChunk " + String.format("%.1f", smoothedChunkUpdateMilliseconds) + " ms"
                + " | UI " + String.format("%.1f", smoothedUiUpdateMilliseconds) + " ms"
                + " | Render+Engine " + String.format("%.1f", approximateRenderAndEngineMilliseconds) + " ms"
                + " | GC " + String.format("%.1f", smoothedGarbageCollectionMilliseconds) + " ms"
                + "\nWASD move | Mouse look | Shift fast | F2/PrtSc screenshot | F11 fullscreen | Esc menu | F10 quit"
                + buildStatusMessageSuffix();
    }

    @Override
    public void simpleUpdate(float timePerFrame) {
        long updateStartNanos = System.nanoTime();
        smoothedFrameTimeSeconds = (smoothedFrameTimeSeconds * 0.9f) + (timePerFrame * 0.1f);
        if (chunkRenderManager != null) {
            long chunkUpdateStartNanos = System.nanoTime();
            chunkRenderManager.update(cam.getLocation(), cam.getDirection(), horizontalViewDegrees());
            long chunkUpdateEndNanos = System.nanoTime();
            smoothedChunkUpdateMilliseconds = smoothMilliseconds(
                    smoothedChunkUpdateMilliseconds,
                    nanosToMilliseconds(chunkUpdateEndNanos - chunkUpdateStartNanos));
        }
        long uiUpdateStartNanos = System.nanoTime();
        if (pauseMenuController != null && pauseMenuController.isVisible()) {
            pauseMenuController.updateHover(inputManager.getCursorPosition());
        }
        if (hud != null) {
            hud.setText(buildHudText());
        }
        long uiUpdateEndNanos = System.nanoTime();
        smoothedUiUpdateMilliseconds = smoothMilliseconds(
                smoothedUiUpdateMilliseconds,
                nanosToMilliseconds(uiUpdateEndNanos - uiUpdateStartNanos));
        updateGarbageCollectionTelemetry();
        long updateEndNanos = System.nanoTime();
        float totalUpdateMilliseconds = nanosToMilliseconds(updateEndNanos - updateStartNanos);
        float knownMilliseconds =
                smoothedChunkUpdateMilliseconds + smoothedUiUpdateMilliseconds + smoothedGarbageCollectionMilliseconds;
        if (knownMilliseconds > totalUpdateMilliseconds * 2f) {
            smoothedUiUpdateMilliseconds = Math.max(0f, totalUpdateMilliseconds - smoothedChunkUpdateMilliseconds);
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
        showStatusMessage("Render distance set to " + graphicsSettings.renderDistanceChunks() + " chunks");
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

    private void takeScreenshot() {
        if (screenshotCaptureProcessor == null) {
            return;
        }
        screenshotCaptureProcessor.requestScreenshot();
        showStatusMessage("Saving screenshot to folder + clipboard...");
    }

    private void toggleFullscreen() {
        JmeContext context = getContext();
        if (!(context instanceof LwjglWindow lwjglWindow)) {
            showStatusMessage("Fullscreen toggle unavailable on this context");
            return;
        }

        long windowHandle = lwjglWindow.getWindowHandle();
        if (windowHandle == 0L) {
            showStatusMessage("Fullscreen toggle unavailable before window init");
            return;
        }

        if (settings.isFullscreen()) {
            long monitor = GLFW.glfwGetPrimaryMonitor();
            GLFWVidMode videoMode = monitor == 0L ? null : GLFW.glfwGetVideoMode(monitor);
            if (videoMode != null) {
                int fallbackWidth = Math.max(1280, Math.min(1600, (int) (videoMode.width() * 0.75f)));
                int fallbackHeight = Math.max(720, Math.min(900, (int) (videoMode.height() * 0.75f)));
                if (windowedWidth <= 0 || windowedHeight <= 0) {
                    windowedWidth = fallbackWidth;
                    windowedHeight = fallbackHeight;
                }
                windowedX = Math.max(0, (videoMode.width() - windowedWidth) / 2);
                windowedY = Math.max(0, (videoMode.height() - windowedHeight) / 2);
            }
            GLFW.glfwSetWindowMonitor(windowHandle, 0L, windowedX, windowedY, windowedWidth, windowedHeight, GLFW.GLFW_DONT_CARE);
            settings.setFullscreen(false);
            settings.setResolution(windowedWidth, windowedHeight);
            settings.setResizable(true);
            showStatusMessage("Windowed mode");
            return;
        }

        windowedWidth = context.getFramebufferWidth();
        windowedHeight = context.getFramebufferHeight();
        windowedX = context.getWindowXPosition();
        windowedY = context.getWindowYPosition();

        long monitor = GLFW.glfwGetPrimaryMonitor();
        if (monitor == 0L) {
            showStatusMessage("No fullscreen monitor detected");
            return;
        }
        GLFWVidMode videoMode = GLFW.glfwGetVideoMode(monitor);
        if (videoMode == null) {
            showStatusMessage("No fullscreen video mode detected");
            return;
        }

        GLFW.glfwSetWindowMonitor(windowHandle, monitor, 0, 0, videoMode.width(), videoMode.height(), videoMode.refreshRate());
        settings.setFullscreen(true);
        settings.setResolution(videoMode.width(), videoMode.height());
        settings.setResizable(false);
        showStatusMessage("Fullscreen mode");
    }

    private void updateGarbageCollectionTelemetry() {
        long currentGarbageCollectionTimeMilliseconds = currentGarbageCollectionTimeMilliseconds();
        long deltaMilliseconds =
                Math.max(0L, currentGarbageCollectionTimeMilliseconds - previousGarbageCollectionTimeMilliseconds);
        previousGarbageCollectionTimeMilliseconds = currentGarbageCollectionTimeMilliseconds;
        smoothedGarbageCollectionMilliseconds =
                smoothMilliseconds(smoothedGarbageCollectionMilliseconds, deltaMilliseconds);
    }

    private long currentGarbageCollectionTimeMilliseconds() {
        long collectionTimeMilliseconds = 0L;
        for (GarbageCollectorMXBean garbageCollector : GARBAGE_COLLECTORS) {
            long collectorTime = garbageCollector.getCollectionTime();
            if (collectorTime >= 0L) {
                collectionTimeMilliseconds += collectorTime;
            }
        }
        return collectionTimeMilliseconds;
    }

    private float smoothMilliseconds(float previousValue, float sampleValue) {
        return (previousValue * 0.9f) + (sampleValue * 0.1f);
    }

    private float nanosToMilliseconds(long nanoseconds) {
        return nanoseconds / 1_000_000f;
    }

    private void showStatusMessage(String message) {
        statusMessage = message;
        statusMessageExpiresAtNanos = System.nanoTime() + STATUS_MESSAGE_DURATION_NANOS;
    }

    private String buildStatusMessageSuffix() {
        if (statusMessage == null) {
            return "";
        }
        if (System.nanoTime() >= statusMessageExpiresAtNanos) {
            statusMessage = null;
            return "";
        }
        return "\n" + statusMessage;
    }

    @Override
    public void onScreenshotSaved(Path screenshotPath, boolean copiedToClipboard) {
        LOGGER.info(
                "Saved screenshot to {}{}",
                screenshotPath,
                copiedToClipboard ? " and copied it to the clipboard" : " but clipboard copy was unavailable");
        showStatusMessage(
                copiedToClipboard
                        ? "Screenshot saved + copied: " + screenshotPath.getFileName()
                        : "Screenshot saved: " + screenshotPath.getFileName() + " (clipboard unavailable)");
    }

    @Override
    public void onScreenshotFailed(Exception exception) {
        LOGGER.error("Failed to save screenshot", exception);
        showStatusMessage("Screenshot failed: " + exception.getClass().getSimpleName());
    }
}
