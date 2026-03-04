package com.johnreah.mapster.model;

public final class TileMath {

    public static final int TILE_SIZE = 256;

    private TileMath() {}

    public static double lonToTileX(double lon, int zoom) {
        return (lon + 180.0) / 360.0 * maxTile(zoom);
    }

    public static double latToTileY(double lat, int zoom) {
        double latRad = Math.toRadians(lat);
        return (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * maxTile(zoom);
    }

    public static double tileXToLon(double tileX, int zoom) {
        return tileX / maxTile(zoom) * 360.0 - 180.0;
    }

    public static double tileYToLat(double tileY, int zoom) {
        double n = Math.PI - 2.0 * Math.PI * tileY / maxTile(zoom);
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    public static double maxTile(int zoom) {
        return 1 << zoom;
    }

    public static int clampTile(int tile, int zoom) {
        int max = (int) maxTile(zoom);
        if (tile < 0) return 0;
        if (tile >= max) return max - 1;
        return tile;
    }

    public static double[] latLonToScreen(double lat, double lon, int zoom,
            double centerX, double centerY, double canvasWidth, double canvasHeight) {
        double tileX = lonToTileX(lon, zoom);
        double tileY = latToTileY(lat, zoom);
        double offsetX = canvasWidth / 2.0 - centerX * TILE_SIZE;
        double offsetY = canvasHeight / 2.0 - centerY * TILE_SIZE;
        return new double[]{offsetX + tileX * TILE_SIZE, offsetY + tileY * TILE_SIZE};
    }

    public static double[] screenToLatLon(double screenX, double screenY, int zoom,
            double centerX, double centerY, double canvasWidth, double canvasHeight) {
        double tileX = centerX + (screenX - canvasWidth / 2.0) / TILE_SIZE;
        double tileY = centerY + (screenY - canvasHeight / 2.0) / TILE_SIZE;
        return new double[]{tileYToLat(tileY, zoom), tileXToLon(tileX, zoom)};
    }
}
