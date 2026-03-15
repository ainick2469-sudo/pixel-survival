package io.github.ainick2469.pixelsurvival.app;

import com.jme3.post.SceneProcessor;
import com.jme3.profile.AppProfiler;
import com.jme3.renderer.RenderManager;
import com.jme3.renderer.Renderer;
import com.jme3.renderer.ViewPort;
import com.jme3.renderer.queue.RenderQueue;
import com.jme3.texture.FrameBuffer;
import com.jme3.util.BufferUtils;
import com.jme3.util.Screenshots;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.imageio.ImageIO;

public final class ScreenshotCaptureProcessor implements SceneProcessor {
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private final Path screenshotDirectory;
    private final ScreenshotFeedbackSink feedbackSink;
    private Renderer renderer;
    private boolean initialized;
    private int width;
    private int height;
    private ByteBuffer pixelBuffer;
    private BufferedImage screenshotImage;
    private int pendingScreenshots;

    public ScreenshotCaptureProcessor(Path screenshotDirectory, ScreenshotFeedbackSink feedbackSink) {
        this.screenshotDirectory = screenshotDirectory;
        this.feedbackSink = feedbackSink;
    }

    public void requestScreenshot() {
        pendingScreenshots++;
    }

    @Override
    public void initialize(RenderManager renderManager, ViewPort viewPort) {
        this.renderer = renderManager.getRenderer();
        this.width = viewPort.getCamera().getWidth();
        this.height = viewPort.getCamera().getHeight();
        allocateBuffers(width, height);
        initialized = true;
    }

    @Override
    public void reshape(ViewPort viewPort, int width, int height) {
        this.width = width;
        this.height = height;
        allocateBuffers(width, height);
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void preFrame(float timePerFrame) {
    }

    @Override
    public void postQueue(RenderQueue renderQueue) {
    }

    @Override
    public void postFrame(FrameBuffer out) {
        if (pendingScreenshots <= 0 || renderer == null) {
            return;
        }

        pendingScreenshots--;
        try {
            pixelBuffer.clear();
            renderer.readFrameBuffer(out, pixelBuffer);
            Screenshots.convertScreenShot(pixelBuffer, screenshotImage);
            Files.createDirectories(screenshotDirectory);
            Path screenshotPath =
                    screenshotDirectory.resolve("pixel-survival-" + FILE_TIMESTAMP.format(LocalDateTime.now()) + ".png");
            ImageIO.write(screenshotImage, "png", screenshotPath.toFile());
            feedbackSink.onScreenshotSaved(screenshotPath);
        } catch (Exception exception) {
            feedbackSink.onScreenshotFailed(exception);
        }
    }

    @Override
    public void cleanup() {
        initialized = false;
        pixelBuffer = null;
        screenshotImage = null;
    }

    @Override
    public void setProfiler(AppProfiler profiler) {
    }

    private void allocateBuffers(int width, int height) {
        int pixelCount = Math.max(1, width * height);
        pixelBuffer = BufferUtils.createByteBuffer(pixelCount * 4);
        screenshotImage = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);
    }

    public interface ScreenshotFeedbackSink {
        void onScreenshotSaved(Path screenshotPath);

        void onScreenshotFailed(Exception exception);
    }
}
