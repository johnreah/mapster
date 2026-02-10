package com.johnreah.mapster;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TileCacheTest {

    static class FakeTileSource implements TileSource {
        private final String id;

        FakeTileSource(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String getDisplayName() {
            return "Fake " + id;
        }

        @Override
        public String getAttribution() {
            return "Fake attribution";
        }

        @Override
        public String getTileUrl(int zoom, int x, int y) {
            return "https://fake.example.com/" + id + "/" + zoom + "/" + x + "/" + y + ".png";
        }

        @Override
        public int getMinZoom() {
            return 0;
        }

        @Override
        public int getMaxZoom() {
            return 18;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }

    @Test
    void uncachedTileReturnsNull() {
        FakeTileSource source = new FakeTileSource("test");
        TileCache cache = new TileCache(source, () -> {});
        assertNull(cache.getTile(10, 511, 340));
        cache.shutdown();
    }

    @Test
    void sourceSwitching() {
        FakeTileSource source1 = new FakeTileSource("source1");
        FakeTileSource source2 = new FakeTileSource("source2");
        TileCache cache = new TileCache(source1, () -> {});

        assertEquals(source1, cache.getTileSource());
        cache.setTileSource(source2);
        assertEquals(source2, cache.getTileSource());
        cache.shutdown();
    }
}
