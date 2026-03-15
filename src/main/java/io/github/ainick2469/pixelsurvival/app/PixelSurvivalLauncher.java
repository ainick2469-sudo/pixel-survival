package io.github.ainick2469.pixelsurvival.app;

import com.jme3.system.AppSettings;
import java.awt.DisplayMode;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;

public final class PixelSurvivalLauncher {
    private PixelSurvivalLauncher() {
    }

    public static void main(String[] args) {
        AppSettings settings = new AppSettings(true);
        settings.setTitle("Pixel Survival");
        settings.setVSync(true);
        settings.setGammaCorrection(true);
        configureDisplayMode(settings);

        PixelSurvivalApplication application = new PixelSurvivalApplication();
        application.setShowSettings(false);
        application.setSettings(settings);
        application.setPauseOnLostFocus(false);
        application.start();
    }

    private static void configureDisplayMode(AppSettings settings) {
        try {
            GraphicsDevice graphicsDevice =
                    GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            DisplayMode displayMode = graphicsDevice.getDisplayMode();

            settings.setResolution(displayMode.getWidth(), displayMode.getHeight());
            if (displayMode.getBitDepth() > 0) {
                settings.setBitsPerPixel(displayMode.getBitDepth());
            }
            if (displayMode.getRefreshRate() > 0) {
                settings.setFrequency(displayMode.getRefreshRate());
            }
            settings.setFullscreen(true);
            settings.setResizable(false);
        } catch (Throwable ignored) {
            settings.setResolution(1920, 1080);
            settings.setFullscreen(false);
            settings.setResizable(true);
        }
    }
}
