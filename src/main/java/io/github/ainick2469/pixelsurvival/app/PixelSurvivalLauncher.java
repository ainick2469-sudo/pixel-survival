package io.github.ainick2469.pixelsurvival.app;

import com.jme3.system.AppSettings;

public final class PixelSurvivalLauncher {
    private PixelSurvivalLauncher() {
    }

    public static void main(String[] args) {
        AppSettings settings = new AppSettings(true);
        settings.setTitle("Pixel Survival");
        settings.setResolution(1600, 900);
        settings.setResizable(true);
        settings.setVSync(true);
        settings.setGammaCorrection(true);

        PixelSurvivalApplication application = new PixelSurvivalApplication();
        application.setShowSettings(false);
        application.setSettings(settings);
        application.setPauseOnLostFocus(false);
        application.start();
    }
}
