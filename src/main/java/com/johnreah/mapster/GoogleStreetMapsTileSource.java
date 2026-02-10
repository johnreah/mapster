package com.johnreah.mapster;

public class GoogleStreetMapsTileSource implements TileSource {

    @Override
    public String getId() {
        return "google-streets";
    }

    @Override
    public String getDisplayName() {
        return "Google Street Maps";
    }

    @Override
    public String getAttribution() {
        return "\u00A9 Google";
    }

    @Override
    public String getTileUrl(int zoom, int x, int y) {
        return String.format("https://mt1.google.com/vt/lyrs=m&x=%d&y=%d&z=%d", x, y, zoom);
    }

    @Override
    public int getMinZoom() {
        return 0;
    }

    @Override
    public int getMaxZoom() {
        return 20;
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
