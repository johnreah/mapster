package com.johnreah.mapster;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TileMathTest {

    private static final double DELTA = 1e-6;

    @Test
    void lonRoundTrip() {
        double lon = -0.09;
        int zoom = 10;
        double tileX = TileMath.lonToTileX(lon, zoom);
        double result = TileMath.tileXToLon(tileX, zoom);
        assertEquals(lon, result, DELTA);
    }

    @Test
    void latRoundTrip() {
        double lat = 51.505;
        int zoom = 10;
        double tileY = TileMath.latToTileY(lat, zoom);
        double result = TileMath.tileYToLat(tileY, zoom);
        assertEquals(lat, result, DELTA);
    }

    @Test
    void londonAtZoom10() {
        int zoom = 10;
        double tileX = TileMath.lonToTileX(-0.09, zoom);
        double tileY = TileMath.latToTileY(51.505, zoom);
        assertEquals(511, (int) tileX);
        assertEquals(340, (int) tileY);
    }

    @Test
    void originAtZoom0() {
        assertEquals(0.5, TileMath.lonToTileX(0, 0), DELTA);
        // Equator should map to 0.5 at zoom 0
        assertEquals(0.5, TileMath.latToTileY(0, 0), DELTA);
    }

    @Test
    void antimeridian() {
        int zoom = 5;
        double max = TileMath.maxTile(zoom);
        // -180 degrees -> tile 0
        assertEquals(0.0, TileMath.lonToTileX(-180, zoom), DELTA);
        // +180 degrees -> tile max
        assertEquals(max, TileMath.lonToTileX(180, zoom), DELTA);
    }

    @Test
    void maxTileValues() {
        assertEquals(1, TileMath.maxTile(0));
        assertEquals(2, TileMath.maxTile(1));
        assertEquals(1024, TileMath.maxTile(10));
    }

    @Test
    void clampTileInRange() {
        assertEquals(5, TileMath.clampTile(5, 10));
    }

    @Test
    void clampTileBelowZero() {
        assertEquals(0, TileMath.clampTile(-1, 10));
    }

    @Test
    void clampTileAboveMax() {
        assertEquals(1023, TileMath.clampTile(1024, 10));
    }

    @Test
    void multipleZoomLevelsRoundTrip() {
        double lon = 13.405;  // Berlin
        double lat = 52.52;
        for (int zoom = 0; zoom <= 18; zoom++) {
            double tileX = TileMath.lonToTileX(lon, zoom);
            double tileY = TileMath.latToTileY(lat, zoom);
            assertEquals(lon, TileMath.tileXToLon(tileX, zoom), DELTA, "lon round-trip failed at zoom " + zoom);
            assertEquals(lat, TileMath.tileYToLat(tileY, zoom), DELTA, "lat round-trip failed at zoom " + zoom);
        }
    }
}
