package io.github.ainick2469.pixelsurvival.settings;

import java.util.Objects;

public record SurvivalSettings(
        double hungerDrainMultiplier,
        double temperatureMultiplier,
        double wetnessSeverity,
        double fatigueDrainMultiplier,
        double damageMultiplier,
        String deathPenaltyMode,
        double hostileSpawnRate,
        double lootAbundance) {
    public SurvivalSettings {
        if (hungerDrainMultiplier <= 0
                || temperatureMultiplier <= 0
                || wetnessSeverity < 0
                || fatigueDrainMultiplier <= 0
                || damageMultiplier <= 0
                || hostileSpawnRate < 0
                || lootAbundance <= 0) {
            throw new IllegalArgumentException("Survival settings values must be positive and non-negative where appropriate");
        }
        Objects.requireNonNull(deathPenaltyMode, "deathPenaltyMode");
    }
}
