package io.github.ainick2469.pixelsurvival.rendering.world;

final class FarFieldMeshSectionBuilder {
    private final boolean textured;
    private final FloatCollector positions = new FloatCollector(1_024);
    private final FloatCollector normals = new FloatCollector(1_024);
    private final FloatCollector textureCoordinates = new FloatCollector(1_024);
    private final FloatCollector seamWeights = new FloatCollector(1_024);
    private final IntCollector indices = new IntCollector(1_024);
    private int vertexCount;
    private int faceCount;

    FarFieldMeshSectionBuilder(boolean textured) {
        this.textured = textured;
    }

    void appendQuad(
            float[] quadPositions,
            float[] quadNormals,
            float[] quadTextureCoordinates,
            float[] quadSeamWeights,
            int textureLayer,
            int emittedFaceCount) {
        for (float positionComponent : quadPositions) {
            positions.add(positionComponent);
        }
        for (float normalComponent : quadNormals) {
            normals.add(normalComponent);
        }
        if (textured) {
            for (int coordinateIndex = 0; coordinateIndex < quadTextureCoordinates.length; coordinateIndex += 2) {
                textureCoordinates.add(quadTextureCoordinates[coordinateIndex]);
                textureCoordinates.add(quadTextureCoordinates[coordinateIndex + 1]);
                textureCoordinates.add(textureLayer);
            }
        } else {
            for (float coordinate : quadTextureCoordinates) {
                textureCoordinates.add(coordinate);
            }
        }
        for (float seamWeight : quadSeamWeights) {
            seamWeights.add(seamWeight);
        }

        indices.add(vertexCount);
        indices.add(vertexCount + 1);
        indices.add(vertexCount + 2);
        indices.add(vertexCount);
        indices.add(vertexCount + 2);
        indices.add(vertexCount + 3);
        vertexCount += 4;
        faceCount += emittedFaceCount;
    }

    ChunkMeshSectionData build() {
        return new ChunkMeshSectionData(
                positions.toArray(),
                normals.toArray(),
                textureCoordinates.toArray(),
                textured ? 3 : 2,
                seamWeights.toArray(),
                seamWeights.size() == 0 ? 0 : 1,
                indices.toArray(),
                faceCount);
    }

    private static final class FloatCollector {
        private float[] values;
        private int size;

        private FloatCollector(int initialCapacity) {
            this.values = new float[initialCapacity];
        }

        private void add(float value) {
            ensureCapacity(1);
            values[size++] = value;
        }

        private void ensureCapacity(int additionalValues) {
            int requiredSize = size + additionalValues;
            if (requiredSize <= values.length) {
                return;
            }

            int newCapacity = Math.max(requiredSize, values.length * 2);
            float[] expanded = new float[newCapacity];
            System.arraycopy(values, 0, expanded, 0, size);
            values = expanded;
        }

        private int size() {
            return size;
        }

        private float[] toArray() {
            float[] compact = new float[size];
            System.arraycopy(values, 0, compact, 0, size);
            return compact;
        }
    }

    private static final class IntCollector {
        private int[] values;
        private int size;

        private IntCollector(int initialCapacity) {
            this.values = new int[initialCapacity];
        }

        private void add(int value) {
            ensureCapacity(1);
            values[size++] = value;
        }

        private void ensureCapacity(int additionalValues) {
            int requiredSize = size + additionalValues;
            if (requiredSize <= values.length) {
                return;
            }

            int newCapacity = Math.max(requiredSize, values.length * 2);
            int[] expanded = new int[newCapacity];
            System.arraycopy(values, 0, expanded, 0, size);
            values = expanded;
        }

        private int[] toArray() {
            int[] compact = new int[size];
            System.arraycopy(values, 0, compact, 0, size);
            return compact;
        }
    }
}
