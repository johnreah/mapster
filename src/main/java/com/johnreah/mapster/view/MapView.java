package com.johnreah.mapster.view;

import com.johnreah.mapster.viewmodel.DrawingLayerViewModel;
import com.johnreah.mapster.viewmodel.LayerStack;
import com.johnreah.mapster.viewmodel.LayerViewModel;
import com.johnreah.mapster.viewmodel.MapViewport;
import com.johnreah.mapster.viewmodel.TileLayerViewModel;

import javafx.collections.ListChangeListener;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Container for all map layers. Maintains a StackPane of {@link TileLayerView} and
 * {@link DrawingLayerView} instances, with an {@link InputOverlayPane} on top to handle
 * all mouse and keyboard input.
 */
public class MapView extends StackPane {

    private final MapViewport viewport;
    private final LayerStack layerStack;
    private final InputOverlayPane inputOverlay;

    private final Map<LayerViewModel, javafx.scene.layout.Pane> layerViewMap = new LinkedHashMap<>();

    public MapView(MapViewport viewport, LayerStack layerStack) {
        this.viewport = viewport;
        this.layerStack = layerStack;

        inputOverlay = new InputOverlayPane(viewport);

        rebuildChildren();

        layerStack.getLayers().addListener((ListChangeListener<LayerViewModel>) c -> rebuildChildren());
        layerStack.activeDrawingLayerProperty().addListener((obs, old, val) -> updateActiveDrawingLayer());
    }

    private void rebuildChildren() {
        // Build the new ordered list of layer nodes
        List<javafx.scene.Node> nodes = new ArrayList<>();
        for (LayerViewModel lvm : layerStack.getLayers()) {
            javafx.scene.layout.Pane view = layerViewMap.computeIfAbsent(lvm, this::createLayerView);
            nodes.add(view);
        }
        nodes.add(inputOverlay);
        getChildren().setAll(nodes);

        // Shut down and remove views for layers that are no longer in the stack
        Set<LayerViewModel> current = layerStack.getLayers().stream().collect(Collectors.toSet());
        layerViewMap.entrySet().removeIf(entry -> {
            if (!current.contains(entry.getKey())) {
                if (entry.getValue() instanceof TileLayerView tlv) {
                    tlv.shutdown();
                }
                return true;
            }
            return false;
        });

        updateActiveDrawingLayer();
        inputOverlay.setMinZoom(getEffectiveMinZoom());
    }

    private javafx.scene.layout.Pane createLayerView(LayerViewModel lvm) {
        if (lvm instanceof TileLayerViewModel tlvm) {
            return new TileLayerView(tlvm, viewport);
        } else if (lvm instanceof DrawingLayerViewModel dlvm) {
            return new DrawingLayerView(dlvm, viewport);
        }
        throw new IllegalArgumentException("Unknown layer type: " + lvm.getClass());
    }

    private void updateActiveDrawingLayer() {
        LayerViewModel activeVM = layerStack.getActiveDrawingLayer();
        DrawingLayerView activeView = null;
        if (activeVM != null) {
            javafx.scene.layout.Pane view = layerViewMap.get(activeVM);
            if (view instanceof DrawingLayerView dlv) {
                activeView = dlv;
            }
        }
        inputOverlay.setActiveDrawingLayer(activeView);
    }

    private int getEffectiveMinZoom() {
        return layerStack.getLayers().stream()
            .filter(lvm -> lvm instanceof TileLayerViewModel)
            .map(lvm -> ((TileLayerViewModel) lvm).getTileSource().getMinZoom())
            .min(Integer::compare)
            .orElse(0);
    }

    // --- Public API ---

    public void setNavigationMode() { inputOverlay.setNavigationMode(); }
    public void setDrawingMode()    { inputOverlay.setDrawingMode(); }

    public void zoomIn()  { viewport.zoomIn(); }
    public void zoomOut() { viewport.zoomOut(getEffectiveMinZoom()); }

    public int getZoom()              { return viewport.getZoom(); }
    public double[] getCenterLatLon() { return viewport.getCenterLatLon(); }

    /**
     * Registers a listener that fires whenever the map centre or zoom changes.
     * Provided for backward compatibility; callers may alternatively listen to
     * viewport properties directly.
     */
    public void setStateUpdateListener(Runnable listener) {
        viewport.centerXProperty().addListener(obs -> listener.run());
        viewport.centerYProperty().addListener(obs -> listener.run());
        viewport.zoomProperty().addListener(obs -> listener.run());
    }

    public void shutdown() {
        layerViewMap.values().forEach(view -> {
            if (view instanceof TileLayerView tlv) tlv.shutdown();
        });
    }
}
