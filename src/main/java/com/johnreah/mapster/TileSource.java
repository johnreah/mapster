package com.johnreah.mapster;

public interface TileSource {
    String getId();
    String getDisplayName();
    String getAttribution();
    String getTileUrl(int zoom, int x, int y);
    int getMinZoom();
    int getMaxZoom();
    boolean isAvailable();
}
