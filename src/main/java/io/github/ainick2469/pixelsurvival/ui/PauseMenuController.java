package io.github.ainick2469.pixelsurvival.ui;

import com.jme3.asset.AssetManager;
import com.jme3.font.BitmapFont;
import com.jme3.font.BitmapText;
import com.jme3.material.Material;
import com.jme3.material.RenderState;
import com.jme3.math.ColorRGBA;
import com.jme3.math.Vector2f;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.shape.Quad;
import com.jme3.scene.Spatial.CullHint;
import java.util.ArrayList;
import java.util.List;

public final class PauseMenuController {
    private static final float LARGE_BUTTON_WIDTH = 320f;
    private static final float SMALL_BUTTON_WIDTH = 154f;
    private static final float BUTTON_HEIGHT = 36f;
    private static final float BUTTON_GAP = 12f;

    private static final String BUTTON_RESUME = "resume";
    private static final String BUTTON_OPTIONS = "options";
    private static final String BUTTON_RENDER_DISTANCE = "render_distance";
    private static final String BUTTON_QUIT = "quit";
    private static final String BUTTON_DISTANCE_DOWN = "distance_down";
    private static final String BUTTON_DISTANCE_UP = "distance_up";
    private static final String BUTTON_DISTANCE_DEFAULT = "distance_default";
    private static final String BUTTON_DISTANCE_MAX = "distance_max";
    private static final String BUTTON_OPTIONS_BACK = "options_back";

    private final AssetManager assetManager;
    private final BitmapFont font;
    private final Node guiNode;
    private final Node root = new Node("pause_menu");
    private final List<MenuButton> buttons = new ArrayList<>();
    private final int defaultRenderDistanceChunks;
    private final int maxRenderDistanceChunks;
    private final Material overlayMaterial;
    private final Material interactiveBorderMaterial;
    private final Material interactiveFillMaterial;
    private final Material interactiveHoverFillMaterial;
    private final Material panelBorderMaterial;
    private final Material panelFillMaterial;

    private int screenWidth;
    private int screenHeight;
    private int renderDistanceChunks;
    private Screen screen = Screen.MAIN;

    public PauseMenuController(
            AssetManager assetManager,
            Node guiNode,
            int screenWidth,
            int screenHeight,
            int initialRenderDistanceChunks,
            int defaultRenderDistanceChunks,
            int maxRenderDistanceChunks) {
        this.assetManager = assetManager;
        this.font = assetManager.loadFont("Interface/Fonts/Default.fnt");
        this.guiNode = guiNode;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.renderDistanceChunks = initialRenderDistanceChunks;
        this.defaultRenderDistanceChunks = defaultRenderDistanceChunks;
        this.maxRenderDistanceChunks = maxRenderDistanceChunks;
        this.overlayMaterial = colorMaterial(new ColorRGBA(0f, 0f, 0f, 0.58f));
        this.interactiveBorderMaterial = colorMaterial(new ColorRGBA(0.14f, 0.14f, 0.14f, 0.94f));
        this.interactiveFillMaterial = colorMaterial(new ColorRGBA(0.52f, 0.52f, 0.52f, 0.96f));
        this.interactiveHoverFillMaterial = colorMaterial(new ColorRGBA(0.68f, 0.68f, 0.68f, 0.98f));
        this.panelBorderMaterial = colorMaterial(new ColorRGBA(0.09f, 0.09f, 0.09f, 0.86f));
        this.panelFillMaterial = colorMaterial(new ColorRGBA(0.23f, 0.23f, 0.23f, 0.88f));
        guiNode.attachChild(root);
        hide();
        rebuild();
    }

    public void showMainMenu() {
        screen = Screen.MAIN;
        rebuild();
        root.setCullHint(CullHint.Inherit);
    }

    public void showOptionsMenu() {
        screen = Screen.OPTIONS;
        rebuild();
        root.setCullHint(CullHint.Inherit);
    }

    public void hide() {
        root.setCullHint(CullHint.Always);
        buttons.clear();
        root.detachAllChildren();
    }

    public boolean isVisible() {
        return root.getCullHint() != CullHint.Always;
    }

    public boolean isShowingOptions() {
        return screen == Screen.OPTIONS;
    }

    public int renderDistanceChunks() {
        return renderDistanceChunks;
    }

    public void setRenderDistanceChunks(int renderDistanceChunks) {
        this.renderDistanceChunks = renderDistanceChunks;
        if (isVisible()) {
            rebuild();
        }
    }

    public void reshape(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        if (isVisible()) {
            rebuild();
        }
    }

    public PauseMenuCommand handleClick(Vector2f cursorPosition) {
        if (!isVisible()) {
            return PauseMenuCommand.NONE;
        }

        for (MenuButton button : buttons) {
            if (!button.contains(cursorPosition.x, cursorPosition.y)) {
                continue;
            }

            return switch (button.id) {
                case BUTTON_RESUME -> PauseMenuCommand.RESUME_GAME;
                case BUTTON_OPTIONS, BUTTON_RENDER_DISTANCE -> {
                    showOptionsMenu();
                    yield PauseMenuCommand.NONE;
                }
                case BUTTON_QUIT -> PauseMenuCommand.QUIT_GAME;
                case BUTTON_DISTANCE_DOWN -> changeRenderDistance(-1);
                case BUTTON_DISTANCE_UP -> changeRenderDistance(1);
                case BUTTON_DISTANCE_DEFAULT -> setRenderDistance(defaultRenderDistanceChunks);
                case BUTTON_DISTANCE_MAX -> setRenderDistance(maxRenderDistanceChunks);
                case BUTTON_OPTIONS_BACK -> {
                    showMainMenu();
                    yield PauseMenuCommand.NONE;
                }
                default -> PauseMenuCommand.NONE;
            };
        }

        return PauseMenuCommand.NONE;
    }

    public void updateHover(Vector2f cursorPosition) {
        if (!isVisible()) {
            return;
        }
        for (MenuButton button : buttons) {
            button.setHovered(button.contains(cursorPosition.x, cursorPosition.y));
        }
    }

    private PauseMenuCommand changeRenderDistance(int delta) {
        int clamped = Math.max(2, Math.min(maxRenderDistanceChunks, renderDistanceChunks + delta));
        if (clamped == renderDistanceChunks) {
            return PauseMenuCommand.NONE;
        }
        renderDistanceChunks = clamped;
        rebuild();
        return PauseMenuCommand.RENDER_DISTANCE_CHANGED;
    }

    private PauseMenuCommand setRenderDistance(int newRenderDistanceChunks) {
        if (renderDistanceChunks == newRenderDistanceChunks) {
            return PauseMenuCommand.NONE;
        }
        renderDistanceChunks = newRenderDistanceChunks;
        rebuild();
        return PauseMenuCommand.RENDER_DISTANCE_CHANGED;
    }

    private void rebuild() {
        buttons.clear();
        root.detachAllChildren();
        root.attachChild(createOverlay());
        if (screen == Screen.MAIN) {
            buildMainScreen();
        } else {
            buildOptionsScreen();
        }
    }

    private void buildMainScreen() {
        float centerX = screenWidth * 0.5f;
        float topY = screenHeight * 0.68f;

        root.attachChild(createTitle("Game Menu", centerX, topY + 86f, 1.45f));
        root.attachChild(createSubtitle("Esc resumes the game from here.", centerX, topY + 56f, ColorRGBA.LightGray));

        addButton(BUTTON_RESUME, "Back to Game", centerX - LARGE_BUTTON_WIDTH * 0.5f, topY, LARGE_BUTTON_WIDTH, BUTTON_HEIGHT);
        addButton(
                BUTTON_OPTIONS,
                "Options...",
                centerX - LARGE_BUTTON_WIDTH * 0.5f,
                topY - (BUTTON_HEIGHT + BUTTON_GAP),
                SMALL_BUTTON_WIDTH,
                BUTTON_HEIGHT);
        addButton(
                BUTTON_RENDER_DISTANCE,
                "Distant Horizons",
                centerX + LARGE_BUTTON_WIDTH * 0.5f - SMALL_BUTTON_WIDTH,
                topY - (BUTTON_HEIGHT + BUTTON_GAP),
                SMALL_BUTTON_WIDTH,
                BUTTON_HEIGHT);
        addButton(
                BUTTON_QUIT,
                "Save and Quit to Desktop",
                centerX - LARGE_BUTTON_WIDTH * 0.5f,
                topY - ((BUTTON_HEIGHT + BUTTON_GAP) * 2f),
                LARGE_BUTTON_WIDTH,
                BUTTON_HEIGHT);
    }

    private void buildOptionsScreen() {
        float centerX = screenWidth * 0.5f;
        float topY = screenHeight * 0.68f;
        float valuePanelWidth = 220f;
        float distanceRowY = topY - (BUTTON_HEIGHT + BUTTON_GAP);

        root.attachChild(createTitle("Options", centerX, topY + 86f, 1.45f));
        root.attachChild(createSubtitle("Render distance is streamed in gradually for stability.", centerX, topY + 56f, ColorRGBA.LightGray));

        addPanel("Render Distance", centerX - LARGE_BUTTON_WIDTH * 0.5f, topY, LARGE_BUTTON_WIDTH, BUTTON_HEIGHT);
        addButton(BUTTON_DISTANCE_DOWN, "-", centerX - 160f, distanceRowY, 56f, BUTTON_HEIGHT);
        addPanel(
                renderDistanceLabel(renderDistanceChunks),
                centerX - valuePanelWidth * 0.5f,
                distanceRowY,
                valuePanelWidth,
                BUTTON_HEIGHT);
        addButton(BUTTON_DISTANCE_UP, "+", centerX + 104f, distanceRowY, 56f, BUTTON_HEIGHT);

        addButton(
                BUTTON_DISTANCE_DEFAULT,
                "Default",
                centerX - LARGE_BUTTON_WIDTH * 0.5f,
                distanceRowY - (BUTTON_HEIGHT + BUTTON_GAP),
                SMALL_BUTTON_WIDTH,
                BUTTON_HEIGHT);
        addButton(
                BUTTON_DISTANCE_MAX,
                "Max Horizon",
                centerX + LARGE_BUTTON_WIDTH * 0.5f - SMALL_BUTTON_WIDTH,
                distanceRowY - (BUTTON_HEIGHT + BUTTON_GAP),
                SMALL_BUTTON_WIDTH,
                BUTTON_HEIGHT);
        addButton(
                BUTTON_OPTIONS_BACK,
                "Done",
                centerX - LARGE_BUTTON_WIDTH * 0.5f,
                distanceRowY - ((BUTTON_HEIGHT + BUTTON_GAP) * 2f),
                LARGE_BUTTON_WIDTH,
                BUTTON_HEIGHT);

        ColorRGBA noteColor = renderDistanceChunks >= 16 ? new ColorRGBA(1f, 0.92f, 0.58f, 1f) : ColorRGBA.LightGray;
        root.attachChild(createSubtitle(
                renderDistanceChunks >= 16
                        ? "Extreme ranges are experimental and will stream in over time."
                        : "Higher values trade startup speed and memory for longer horizons.",
                centerX,
                distanceRowY - ((BUTTON_HEIGHT + BUTTON_GAP) * 2f) - 30f,
                noteColor));
    }

    private Spatial createOverlay() {
        Geometry overlay = new Geometry("pause_menu_overlay", new Quad(screenWidth, screenHeight));
        overlay.setLocalTranslation(0f, 0f, 0f);
        overlay.setMaterial(overlayMaterial);
        overlay.setQueueBucket(RenderQueue.Bucket.Gui);
        return overlay;
    }

    private Spatial createTitle(String text, float centerX, float y, float scale) {
        BitmapText title = new BitmapText(font);
        title.setSize(font.getCharSet().getRenderedSize() * scale);
        title.setColor(ColorRGBA.White);
        title.setText(text);
        title.setLocalTranslation(centerX - title.getLineWidth() * 0.5f, y, 2f);
        return title;
    }

    private Spatial createSubtitle(String text, float centerX, float y, ColorRGBA color) {
        BitmapText subtitle = new BitmapText(font);
        subtitle.setSize(font.getCharSet().getRenderedSize() * 0.9f);
        subtitle.setColor(color);
        subtitle.setText(text);
        subtitle.setLocalTranslation(centerX - subtitle.getLineWidth() * 0.5f, y, 2f);
        return subtitle;
    }

    private void addButton(String id, String text, float x, float y, float width, float height) {
        MenuButton button = new MenuButton(id, text, x, y, width, height, true);
        buttons.add(button);
        root.attachChild(button.node);
    }

    private void addPanel(String text, float x, float y, float width, float height) {
        MenuButton panel = new MenuButton("panel_" + text, text, x, y, width, height, false);
        root.attachChild(panel.node);
    }

    private String renderDistanceLabel(int renderDistanceChunks) {
        return renderDistanceChunks + " chunks | " + renderDistanceTier(renderDistanceChunks);
    }

    private String renderDistanceTier(int renderDistanceChunks) {
        if (renderDistanceChunks <= 4) {
            return "Near";
        }
        if (renderDistanceChunks <= 8) {
            return "Far";
        }
        if (renderDistanceChunks <= 12) {
            return "Distant";
        }
        if (renderDistanceChunks <= 18) {
            return "Ultra";
        }
        return "Horizon";
    }

    private Material colorMaterial(ColorRGBA color) {
        Material material = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
        material.setColor("Color", color);
        material.getAdditionalRenderState().setBlendMode(RenderState.BlendMode.Alpha);
        return material;
    }

    private enum Screen {
        MAIN,
        OPTIONS
    }

    private final class MenuButton {
        private static final float BORDER_PADDING = 2f;

        private final String id;
        private final float x;
        private final float y;
        private final float width;
        private final float height;
        private final boolean interactive;
        private final Node node = new Node("pause_button");
        private final Geometry inner;
        private boolean hovered;

        private MenuButton(String id, String text, float x, float y, float width, float height, boolean interactive) {
            this.id = id;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.interactive = interactive;

            Geometry border = new Geometry("button_border", new Quad(width, height));
            border.setLocalTranslation(x, y, 1f);
            border.setMaterial(interactive ? interactiveBorderMaterial : panelBorderMaterial);
            border.setQueueBucket(RenderQueue.Bucket.Gui);
            node.attachChild(border);

            inner = new Geometry("button_inner", new Quad(width - (BORDER_PADDING * 2f), height - (BORDER_PADDING * 2f)));
            inner.setLocalTranslation(x + BORDER_PADDING, y + BORDER_PADDING, 1.2f);
            inner.setMaterial(interactive ? interactiveFillMaterial : panelFillMaterial);
            inner.setQueueBucket(RenderQueue.Bucket.Gui);
            node.attachChild(inner);

            BitmapText label = new BitmapText(font);
            label.setSize(font.getCharSet().getRenderedSize() * 1.02f);
            label.setColor(interactive ? ColorRGBA.White : ColorRGBA.LightGray);
            label.setText(text);
            label.setLocalTranslation(
                    x + (width - label.getLineWidth()) * 0.5f,
                    y + (height * 0.68f),
                    1.4f);
            node.attachChild(label);
        }

        private boolean contains(float cursorX, float cursorY) {
            return interactive
                    && cursorX >= x
                    && cursorX <= x + width
                    && cursorY >= y
                    && cursorY <= y + height;
        }

        private void setHovered(boolean hovered) {
            if (!interactive || this.hovered == hovered) {
                return;
            }
            this.hovered = hovered;
            inner.setMaterial(hovered ? interactiveHoverFillMaterial : interactiveFillMaterial);
        }
    }
}
