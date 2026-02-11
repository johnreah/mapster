package com.johnreah.mapster;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TileSourceTest {

    @Test
    void osmId() {
        OsmTileSource osm = new OsmTileSource();
        assertEquals("osm", osm.getId());
    }

    @Test
    void osmDisplayName() {
        OsmTileSource osm = new OsmTileSource();
        assertEquals("OpenStreetMap", osm.getDisplayName());
    }

    @Test
    void osmZoomRange() {
        OsmTileSource osm = new OsmTileSource();
        assertEquals(0, osm.getMinZoom());
        assertEquals(19, osm.getMaxZoom());
    }

    @Test
    void osmAttribution() {
        OsmTileSource osm = new OsmTileSource();
        assertTrue(osm.getAttribution().contains("OpenStreetMap"));
    }

    @Test
    void osmTileUrl() {
        OsmTileSource osm = new OsmTileSource();
        String url = osm.getTileUrl(10, 511, 340);
        assertEquals("https://tile.openstreetmap.org/10/511/340.png", url);
    }

    @Test
    void osmIsAlwaysAvailable() {
        OsmTileSource osm = new OsmTileSource();
        assertTrue(osm.isAvailable());
    }

    @Test
    void osRoadId() {
        OrdnanceSurveyTileSource os = new OrdnanceSurveyTileSource("Road_3857", "OS Road");
        assertEquals("os-road-3857", os.getId());
    }

    @Test
    void osOutdoorId() {
        OrdnanceSurveyTileSource os = new OrdnanceSurveyTileSource("Outdoor_3857", "OS Outdoor");
        assertEquals("os-outdoor-3857", os.getId());
    }

    @Test
    void osDisplayName() {
        OrdnanceSurveyTileSource os = new OrdnanceSurveyTileSource("Road_3857", "OS Road");
        assertEquals("OS Road", os.getDisplayName());
    }

    @Test
    void osZoomRange() {
        OrdnanceSurveyTileSource os = new OrdnanceSurveyTileSource("Road_3857", "OS Road");
        assertEquals(0, os.getMinZoom());
        assertEquals(20, os.getMaxZoom());
    }

    @Test
    void osAttribution() {
        OrdnanceSurveyTileSource os = new OrdnanceSurveyTileSource("Road_3857", "OS Road");
        assertTrue(os.getAttribution().contains("Ordnance Survey"));
    }

    @Test
    void osTileUrl() {
        OrdnanceSurveyTileSource os = new OrdnanceSurveyTileSource("Road_3857", "OS Road");
        String url = os.getTileUrl(10, 511, 340);
        assertTrue(url.startsWith("https://api.os.uk/maps/raster/v1/zxy/Road_3857/10/511/340.png"));
        assertTrue(url.contains("?key="));
    }
}
