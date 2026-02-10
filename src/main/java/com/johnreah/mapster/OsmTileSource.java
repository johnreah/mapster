package com.johnreah.mapster;

public class OsmTileSource implements TileSource {

    @Override
    public String getId() {
        return "osm";
    }

    @Override
    public String getDisplayName() {
        return "OpenStreetMap";
    }

    @Override
    public String getAttribution() {
        return "\u00A9 OpenStreetMap contributors";
    }

    @Override
    public String getTileUrl(int zoom, int x, int y) {
        return String.format("https://tile.openstreetmap.org/%d/%d/%d.png", zoom, x, y);
    }

    @Override
    public int getMinZoom() {
        return 0;
    }

    @Override
    public int getMaxZoom() {
        return 19;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
