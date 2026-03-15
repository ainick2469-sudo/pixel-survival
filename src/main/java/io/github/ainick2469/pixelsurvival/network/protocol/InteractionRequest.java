package io.github.ainick2469.pixelsurvival.network.protocol;

public record InteractionRequest(
        String actionId,
        int worldX,
        int worldY,
        int worldZ,
        String heldItemId) {
}
