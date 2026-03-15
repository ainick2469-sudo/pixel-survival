package io.github.ainick2469.pixelsurvival.rendering.world;

import com.jme3.math.Vector3f;
import io.github.ainick2469.pixelsurvival.world.chunk.ChunkCoord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChunkVisibilityPlannerTest {
    @Test
    void prioritizesChunksInFrontOfTheCameraForLongRangeTargets() {
        ChunkVisibilityPlanner planner = new ChunkVisibilityPlanner();
        ChunkRuntimeConfig runtimeConfig = new ChunkRuntimeConfig(18, 16, 4);

        ChunkVisibilityPlanner.RuntimeTargets targets =
                planner.plan(new Vector3f(8f, 32f, 8f), new Vector3f(0f, -0.25f, 1f), 74f, runtimeConfig);

        assertTrue(targets.renderTargets().contains(new ChunkCoord(0, 10)));
        assertTrue(targets.loadTargets().contains(new ChunkCoord(0, 14)));
        assertFalse(targets.renderTargets().contains(new ChunkCoord(0, -12)));
        assertFalse(targets.loadTargets().contains(new ChunkCoord(0, -17)));
    }

    @Test
    void keepsNearChunksAvailableEvenWhenTheyFallOutsideTheMainViewCone() {
        ChunkVisibilityPlanner planner = new ChunkVisibilityPlanner();
        ChunkRuntimeConfig runtimeConfig = new ChunkRuntimeConfig(12, 10, 3);

        ChunkVisibilityPlanner.RuntimeTargets targets =
                planner.plan(new Vector3f(8f, 28f, 8f), new Vector3f(0f, -0.2f, 1f), 74f, runtimeConfig);

        assertTrue(targets.renderTargets().contains(new ChunkCoord(-2, 0)));
        assertTrue(targets.loadTargets().contains(new ChunkCoord(2, -1)));
        assertTrue(targets.simulationTargets().contains(new ChunkCoord(0, 2)));
    }
}
