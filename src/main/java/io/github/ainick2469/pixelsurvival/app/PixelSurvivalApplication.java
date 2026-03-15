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
import io.github.ainick2469.pixelsurvival.rendering.world.ChunkDebugRenderer;
import io.github.ainick2469.pixelsurvival.session.LocalHostSession;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PixelSurvivalApplication extends SimpleApplication {
    private static final String INPUT_TOGGLE_MOUSE_CAPTURE = "pixel_survival_toggle_mouse_capture";
    private static final String INPUT_QUIT_GAME = "pixel_survival_quit_game";
    private static final Logger LOGGER = LoggerFactory.getLogger(PixelSurvivalApplication.class);

    private LocalHostSession session;
    private BitmapText hud;
    private boolean mouseLookCaptured;

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
        configureInput();
        attachHud();
    }

    private void configureCamera() {
        float focusX = 8f;
        float focusZ = 8f;
        float surfaceY = session.worldService().findSurfaceY((int) focusX, (int) focusZ);

        flyCam.setMoveSpeed(35f);
        flyCam.setRotationSpeed(2.5f);
        cam.setLocation(new Vector3f(focusX + 18f, surfaceY + 22f, focusZ + 20f));
        cam.lookAt(new Vector3f(focusX, surfaceY + 2f, focusZ), Vector3f.UNIT_Y);
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
        return "Pixel Survival v" + GameVersion.CURRENT + " | Mode: " + session.mode().name()
                + "\nWASD move | Mouse look | Shift fast | Esc mouse " + mouseMode + " | F10 quit";
    }
}
