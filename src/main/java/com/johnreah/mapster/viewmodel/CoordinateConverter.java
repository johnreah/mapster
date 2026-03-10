package com.johnreah.mapster.viewmodel;

/**
 * Contract for converting between screen pixel coordinates and geographic (lat/lon) coordinates.
 * Implemented by the View layer and passed into DrawingTool on each render or input event.
 * A fresh instance must be created per render — do not cache between renders.
 */
public interface CoordinateConverter {
    double[] latLonToScreen(double lat, double lon);
    double[] screenToLatLon(double screenX, double screenY);
}
