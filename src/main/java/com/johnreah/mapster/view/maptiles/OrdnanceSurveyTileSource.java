package com.johnreah.mapster.view.maptiles;

public class OrdnanceSurveyTileSource implements TileSource {

    public static final String ROAD_LAYER = "Road_3857";
    public static final String ROAD_DISPLAY_NAME = "OS Road";
    public static final String OUTDOOR_LAYER = "Outdoor_3857";
    public static final String OUTDOOR_DISPLAY_NAME = "OS Outdoor";

    private final String layer;
    private final String displayName;
    private final String apiKey;

    public OrdnanceSurveyTileSource(String layer, String displayName) {
        this.layer = layer;
        this.displayName = displayName;
        this.apiKey = System.getenv("OS_API_KEY");
    }

    @Override
    public String getId() {
        return "os-" + layer.toLowerCase().replace('_', '-');
    }

    @Override
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String getAttribution() {
        return "\u00A9 Crown copyright and database rights Ordnance Survey";
    }

    @Override
    public String getTileUrl(int zoom, int x, int y) {
        return String.format(
                "https://api.os.uk/maps/raster/v1/zxy/%s/%d/%d/%d.png?key=%s",
                layer, zoom, x, y, apiKey);
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
        return apiKey != null && !apiKey.isBlank();
    }
}
