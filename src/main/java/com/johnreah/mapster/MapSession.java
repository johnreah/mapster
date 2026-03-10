package com.johnreah.mapster;

import com.johnreah.mapster.util.TileSource;
import com.johnreah.mapster.view.maptiles.GoogleSatelliteTileSource;
import com.johnreah.mapster.view.maptiles.GoogleStreetMapsTileSource;
import com.johnreah.mapster.view.maptiles.OrdnanceSurveyTileSource;
import com.johnreah.mapster.view.maptiles.OsmTileSource;
import com.johnreah.mapster.viewmodel.DrawingLayerViewModel;
import com.johnreah.mapster.viewmodel.LayerStack;
import com.johnreah.mapster.viewmodel.MapViewport;
import com.johnreah.mapster.viewmodel.TileLayerViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Wires together the application's ViewModels and tile sources into a ready-to-use session.
 * Created by {@link App} and handed to {@link com.johnreah.mapster.view.MainWindow}.
 */
public class MapSession {

    public final MapViewport viewport;
    public final LayerStack layerStack;
    public final TileLayerViewModel baseTileLayer;
    public final List<TileSource> availableSources;

    private MapSession(MapViewport viewport, LayerStack layerStack,
                       TileLayerViewModel baseTileLayer, List<TileSource> availableSources) {
        this.viewport = viewport;
        this.layerStack = layerStack;
        this.baseTileLayer = baseTileLayer;
        this.availableSources = availableSources;
    }

    public static MapSession create() {
        MapViewport viewport = new MapViewport();
        LayerStack layerStack = new LayerStack();

        List<TileSource> sources = buildAvailableSources();
        TileSource defaultSource = sources.get(0);

        TileLayerViewModel baseTileLayer = new TileLayerViewModel(
                "tile-0", defaultSource.getDisplayName(), defaultSource);
        layerStack.addLayer(baseTileLayer);

        sources.stream()
                .filter(s -> "google-satellite".equals(s.getId()))
                .findFirst()
                .ifPresent(s -> layerStack.addLayer(
                        new TileLayerViewModel("tile-satellite", s.getDisplayName(), s)));

        sources.stream()
                .filter(s -> "os-road-3857".equals(s.getId()))
                .findFirst()
                .ifPresent(s -> layerStack.addLayer(
                        new TileLayerViewModel("tile-os-road", s.getDisplayName(), s)));

        DrawingLayerViewModel drawingLayer = new DrawingLayerViewModel("drawing-0", "Drawing Layer");
        layerStack.addLayer(drawingLayer);
        layerStack.activeDrawingLayerProperty().set(drawingLayer);

        // Enforce the source's minimum zoom reactively whenever the base tile source changes
        baseTileLayer.tileSourceProperty().addListener((obs, old, newSource) ->
                viewport.ensureMinZoom(newSource.getMinZoom()));

        return new MapSession(viewport, layerStack, baseTileLayer, sources);
    }

    private static List<TileSource> buildAvailableSources() {
        List<TileSource> sources = new ArrayList<>();
        sources.add(new OsmTileSource());
        sources.add(new GoogleStreetMapsTileSource());
        sources.add(new GoogleSatelliteTileSource());

        OrdnanceSurveyTileSource osRoad = new OrdnanceSurveyTileSource(
                OrdnanceSurveyTileSource.ROAD_LAYER, OrdnanceSurveyTileSource.ROAD_DISPLAY_NAME);
        if (osRoad.isAvailable()) sources.add(osRoad);

        OrdnanceSurveyTileSource osOutdoor = new OrdnanceSurveyTileSource(
                OrdnanceSurveyTileSource.OUTDOOR_LAYER, OrdnanceSurveyTileSource.OUTDOOR_DISPLAY_NAME);
        if (osOutdoor.isAvailable()) sources.add(osOutdoor);

        return sources;
    }
}
