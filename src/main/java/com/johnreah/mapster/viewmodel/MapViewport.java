package com.johnreah.mapster.viewmodel;

import com.johnreah.mapster.view.maptiles.TileMath;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;

/**
 * Shared viewport state (center position and zoom level) for all map layers.
 * All layers observe these properties and re-render when they change.
 */
public class MapViewport {

    public static final int MAX_ZOOM = 20;

    private final DoubleProperty centerX = new SimpleDoubleProperty();
    private final DoubleProperty centerY = new SimpleDoubleProperty();
    private final IntegerProperty zoom = new SimpleIntegerProperty();

    public MapViewport() {
        int defaultZoom = 10;
        zoom.set(defaultZoom);
        centerX.set(TileMath.lonToTileX(-0.09, defaultZoom));
        centerY.set(TileMath.latToTileY(51.505, defaultZoom));
    }

    public DoubleProperty centerXProperty() { return centerX; }
    public DoubleProperty centerYProperty() { return centerY; }
    public IntegerProperty zoomProperty() { return zoom; }

    public double getCenterX() { return centerX.get(); }
    public double getCenterY() { return centerY.get(); }
    public int getZoom() { return zoom.get(); }

    /**
     * Pan to a new center position, clamping to valid tile bounds.
     */
    public void moveTo(double newCenterX, double newCenterY) {
        double max = TileMath.maxTile(zoom.get());
        newCenterX = ((newCenterX % max) + max) % max;
        if (newCenterY < 0) newCenterY = 0;
        if (newCenterY > max) newCenterY = max;
        centerX.set(newCenterX);
        centerY.set(newCenterY);
    }

    /**
     * Zoom to a new level, preserving the geographic point at the given mouse offset from center.
     *
     * @param newZoom              new zoom level
     * @param pivotLat             latitude of the pivot point (e.g. point under cursor)
     * @param pivotLon             longitude of the pivot point
     * @param mouseOffsetXPixels   horizontal pixel distance of pivot from canvas centre
     * @param mouseOffsetYPixels   vertical pixel distance of pivot from canvas centre
     */
    public void zoomTo(int newZoom, double pivotLat, double pivotLon,
                       double mouseOffsetXPixels, double mouseOffsetYPixels) {
        double tileXAfter = TileMath.lonToTileX(pivotLon, newZoom);
        double tileYAfter = TileMath.latToTileY(pivotLat, newZoom);
        double newCenterX = tileXAfter - mouseOffsetXPixels / TileMath.TILE_SIZE;
        double newCenterY = tileYAfter - mouseOffsetYPixels / TileMath.TILE_SIZE;
        double max = TileMath.maxTile(newZoom);
        newCenterX = ((newCenterX % max) + max) % max;
        if (newCenterY < 0) newCenterY = 0;
        if (newCenterY > max) newCenterY = max;
        zoom.set(newZoom);
        centerX.set(newCenterX);
        centerY.set(newCenterY);
    }

    /** Zoom in by one level, keeping the current center. */
    public void zoomIn() {
        if (zoom.get() < MAX_ZOOM) changeZoom(zoom.get() + 1);
    }

    /** Zoom out by one level, keeping the current center, down to {@code minZoom}. */
    public void zoomOut(int minZoom) {
        if (zoom.get() > minZoom) changeZoom(zoom.get() - 1);
    }

    /** Force a specific zoom level, preserving the current geographic centre. */
    public void setZoom(int newZoom) {
        changeZoom(newZoom);
    }

    /** Returns [lat, lon] of the current map centre. */
    public double[] getCenterLatLon() {
        int z = zoom.get();
        return new double[]{
            TileMath.tileYToLat(centerY.get(), z),
            TileMath.tileXToLon(centerX.get(), z)
        };
    }

    private void changeZoom(int newZoom) {
        int currentZoom = zoom.get();
        double lon = TileMath.tileXToLon(centerX.get(), currentZoom);
        double lat = TileMath.tileYToLat(centerY.get(), currentZoom);
        double newCX = TileMath.lonToTileX(lon, newZoom);
        double newCY = TileMath.latToTileY(lat, newZoom);
        double max = TileMath.maxTile(newZoom);
        newCX = ((newCX % max) + max) % max;
        if (newCY < 0) newCY = 0;
        if (newCY > max) newCY = max;
        zoom.set(newZoom);
        centerX.set(newCX);
        centerY.set(newCY);
    }
}
