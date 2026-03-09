package com.johnreah.mapster.viewmodel;

import com.johnreah.mapster.view.maptiles.TileSource;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * ViewModel for a tile-based map layer. The observable {@code tileSource} property
 * allows the view to react when the source is switched (e.g. OSM → Satellite).
 */
public class TileLayerViewModel extends LayerViewModel {

    private final ObjectProperty<TileSource> tileSource = new SimpleObjectProperty<>();

    public TileLayerViewModel(String id, String displayName, TileSource source) {
        super(id, displayName);
        tileSource.set(source);
    }

    public ObjectProperty<TileSource> tileSourceProperty() { return tileSource; }
    public TileSource getTileSource() { return tileSource.get(); }
    public void setTileSource(TileSource source) { tileSource.set(source); }
}
