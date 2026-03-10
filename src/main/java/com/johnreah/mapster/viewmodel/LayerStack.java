package com.johnreah.mapster.viewmodel;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

/**
 * Ordered list of layers and tracks which drawing layer is currently active.
 * Layers are ordered bottom-first (index 0 is rendered first/below).
 */
public class LayerStack {

    private final ObservableList<LayerViewModel> layers = FXCollections.observableArrayList();
    private final ObjectProperty<LayerViewModel> activeDrawingLayer = new SimpleObjectProperty<>();

    public ObservableList<LayerViewModel> getLayers() { return layers; }

    public ObjectProperty<LayerViewModel> activeDrawingLayerProperty() { return activeDrawingLayer; }
    public LayerViewModel getActiveDrawingLayer() { return activeDrawingLayer.get(); }

    public void addLayer(LayerViewModel layer) {
        layers.add(layer);
    }

    public void removeLayer(LayerViewModel layer) {
        layers.remove(layer);
        if (activeDrawingLayer.get() == layer) {
            activeDrawingLayer.set(null);
        }
    }

    public void moveUp(LayerViewModel layer) {
        int idx = layers.indexOf(layer);
        if (idx < layers.size() - 1) {
            layers.remove(idx);
            layers.add(idx + 1, layer);
        }
    }

    public void moveDown(LayerViewModel layer) {
        int idx = layers.indexOf(layer);
        if (idx > 0) {
            layers.remove(idx);
            layers.add(idx - 1, layer);
        }
    }

    /** Returns the minimum zoom level across all tile layers, or 0 if none exist. */
    public int getEffectiveMinZoom() {
        return layers.stream()
                .filter(lvm -> lvm instanceof TileLayerViewModel)
                .mapToInt(lvm -> ((TileLayerViewModel) lvm).getMinZoom())
                .min()
                .orElse(0);
    }
}
