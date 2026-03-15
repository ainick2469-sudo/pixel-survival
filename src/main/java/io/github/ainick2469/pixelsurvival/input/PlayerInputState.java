package io.github.ainick2469.pixelsurvival.input;

public record PlayerInputState(
        float moveForward,
        float moveRight,
        boolean jumpPressed,
        boolean primaryUsePressed,
        boolean secondaryUsePressed) {
}
