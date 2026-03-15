package io.github.ainick2469.pixelsurvival.rendering.world;

import com.jme3.math.Vector3f;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkVisibilityPlannerTest {
    @Test
    void keepsTheSameChunkTargetsWhenTheCameraTurnsAround() {
        ChunkVisibilityPlanner planner = new ChunkVisibilityPlanner();
        ChunkRuntimeConfig runtimeConfig = new ChunkRuntimeConfig(18, 16, 4);

        ChunkVisibilityPlanner.RuntimeTargets forwardTargets =
                planner.plan(new Vector3f(8f, 32f, 8f), new Vector3f(0f, -0.25f, 1f), 74f, runtimeConfig);
        ChunkVisibilityPlanner.RuntimeTargets backwardTargets =
                planner.plan(new Vector3f(8f, 32f, 8f), new Vector3f(0f, -0.25f, -1f), 74f, runtimeConfig);

        assertEquals(forwardTargets.loadTargets(), backwardTargets.loadTargets());
        assertEquals(forwardTargets.renderTargets(), backwardTargets.renderTargets());
        assertEquals(forwardTargets.simulationTargets(), backwardTargets.simulationTargets());
    }

    @Test
    void usesCircularTargetsAroundThePlayerInsteadOfViewConeTargets() {
        ChunkVisibilityPlanner planner = new ChunkVisibilityPlanner();
        ChunkRuntimeConfig runtimeConfig = new ChunkRuntimeConfig(12, 10, 3);

        ChunkVisibilityPlanner.RuntimeTargets targets =
                planner.plan(new Vector3f(8f, 28f, 8f), new Vector3f(0f, -0.2f, 1f), 74f, runtimeConfig);

        assertTrue(targets.renderTargets().contains(new ChunkCoord(0, 10)));
        assertTrue(targets.renderTargets().contains(new ChunkCoord(0, -10)));
        assertFalse(targets.renderTargets().contains(new ChunkCoord(8, 8)));
        assertTrue(targets.loadTargets().contains(new ChunkCoord(0, -11)));
        assertTrue(targets.simulationTargets().contains(new ChunkCoord(0, 3)));
    }

    @Test
    void prioritizesNearChunksBeforeFarChunks() {
        ChunkVisibilityPlanner planner = new ChunkVisibilityPlanner();
        ChunkRuntimeConfig runtimeConfig = new ChunkRuntimeConfig(6, 5, 2);

        List<ChunkCoord> orderedLoadTargets = new ArrayList<>(planner
                .plan(new Vector3f(8f, 20f, 8f), new Vector3f(1f, 0f, 0f), 74f, runtimeConfig)
                .loadTargets());

        assertEquals(new ChunkCoord(0, 0), orderedLoadTargets.get(0));
        assertTrue(orderedLoadTargets.indexOf(new ChunkCoord(1, 0)) < orderedLoadTargets.indexOf(new ChunkCoord(4, 0)));
        assertTrue(orderedLoadTargets.indexOf(new ChunkCoord(0, 1)) < orderedLoadTargets.indexOf(new ChunkCoord(0, 5)));
    }
}
