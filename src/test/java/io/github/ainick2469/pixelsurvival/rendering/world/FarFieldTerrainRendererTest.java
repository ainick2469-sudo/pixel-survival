package io.github.ainick2469.pixelsurvival.rendering.world;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarFieldTerrainRendererTest {
    @Test
    void refreshPlanKeepsUnchangedInteriorRegionsCleanOnAnchorMovement() {
        FarFieldTerrainTarget unchangedInterior = new FarFieldTerrainTarget(
                new FarFieldTerrainRegionCoord(4, 0),
                FarFieldClipMode.FULL_REGION);
        FarFieldTerrainTarget boundaryRegion = new FarFieldTerrainTarget(
                new FarFieldTerrainRegionCoord(3, 0),
                FarFieldClipMode.CLIP_INNER);
        FarFieldTerrainTarget staleRegion = new FarFieldTerrainTarget(
                new FarFieldTerrainRegionCoord(6, 0),
                FarFieldClipMode.CLIP_OUTER);
        Map<FarFieldTerrainRegionCoord, FarFieldTerrainTarget> activeTargets = new LinkedHashMap<>();
        activeTargets.put(boundaryRegion.regionCoord(), boundaryRegion);
        activeTargets.put(unchangedInterior.regionCoord(), unchangedInterior);
        activeTargets.put(staleRegion.regionCoord(), staleRegion);

        FarFieldTerrainTarget newBoundaryRegion = new FarFieldTerrainTarget(
                new FarFieldTerrainRegionCoord(2, 1),
                FarFieldClipMode.CLIP_INNER);
        FarFieldTerrainRenderer.RefreshTargetsPlan refreshPlan = FarFieldTerrainRenderer.calculateRefreshTargetsPlan(
                activeTargets,
                List.of(boundaryRegion, unchangedInterior, newBoundaryRegion),
                false,
                true);

        assertEquals(
                List.of(boundaryRegion, unchangedInterior, newBoundaryRegion),
                refreshPlan.orderedTargets());
        assertTrue(refreshPlan.dirtyRegionCoords().contains(boundaryRegion.regionCoord()));
        assertTrue(refreshPlan.dirtyRegionCoords().contains(newBoundaryRegion.regionCoord()));
        assertFalse(refreshPlan.dirtyRegionCoords().contains(unchangedInterior.regionCoord()));
        assertTrue(refreshPlan.staleRegionCoords().contains(staleRegion.regionCoord()));
    }

    @Test
    void refreshPlanDirtiesRegionsWhenClipModeChangesWithoutDirtyingStableMatches() {
        FarFieldTerrainTarget stableInterior = new FarFieldTerrainTarget(
                new FarFieldTerrainRegionCoord(4, 0),
                FarFieldClipMode.FULL_REGION);
        FarFieldTerrainTarget priorBoundary = new FarFieldTerrainTarget(
                new FarFieldTerrainRegionCoord(5, 0),
                FarFieldClipMode.CLIP_INNER);
        Map<FarFieldTerrainRegionCoord, FarFieldTerrainTarget> activeTargets = Map.of(
                stableInterior.regionCoord(), stableInterior,
                priorBoundary.regionCoord(), priorBoundary);
        FarFieldTerrainTarget updatedBoundary = new FarFieldTerrainTarget(
                priorBoundary.regionCoord(),
                FarFieldClipMode.CLIP_OUTER);

        FarFieldTerrainRenderer.RefreshTargetsPlan refreshPlan = FarFieldTerrainRenderer.calculateRefreshTargetsPlan(
                activeTargets,
                List.of(stableInterior, updatedBoundary),
                false,
                false);

        assertEquals(Set.of(updatedBoundary.regionCoord()), refreshPlan.dirtyRegionCoords());
        assertTrue(refreshPlan.staleRegionCoords().isEmpty());
    }
}
