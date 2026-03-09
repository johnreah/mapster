package com.johnreah.mapster.view;

import com.johnreah.mapster.view.maptiles.TileMath;
import com.johnreah.mapster.viewmodel.DrawingLayerViewModel;
import com.johnreah.mapster.viewmodel.DrawingTool;
import com.johnreah.mapster.viewmodel.MapViewport;

import javafx.scene.canvas.Canvas;
import javafx.scene.layout.Pane;

/**
 * Renders a single drawing layer on a transparent canvas. Mouse events are not
 * consumed here — all input is routed via {@link InputOverlayPane}.
 */
public class DrawingLayerView extends Pane {

    private final Canvas canvas = new Canvas();
    private final DrawingLayerViewModel layerViewModel;
    private final MapViewport viewport;

    public DrawingLayerView(DrawingLayerViewModel layerViewModel, MapViewport viewport) {
        this.layerViewModel = layerViewModel;
        this.viewport = viewport;

        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.widthProperty().addListener(e -> render());
        canvas.heightProperty().addListener(e -> render());
        getChildren().add(canvas);

        opacityProperty().bind(layerViewModel.opacityProperty());
        visibleProperty().bind(layerViewModel.visibleProperty());

        // Drawing layers never consume mouse events — the InputOverlayPane handles all input
        setMouseTransparent(true);

        viewport.centerXProperty().addListener((obs, old, val) -> render());
        viewport.centerYProperty().addListener((obs, old, val) -> render());
        viewport.zoomProperty().addListener((obs, old, val) -> render());
    }

    public void render() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;
        canvas.getGraphicsContext2D().clearRect(0, 0, w, h);
        layerViewModel.getDrawingTool().render(canvas.getGraphicsContext2D(), createCoordinateConverter());
    }

    // --- Interaction methods called by InputOverlayPane ---

    public boolean selectPointNearMouse(double x, double y) {
        return layerViewModel.getDrawingTool().selectPointNearMouse(x, y, createCoordinateConverter());
    }

    public void startDraggingPoint() {
        layerViewModel.getDrawingTool().startDraggingPoint();
    }

    public void updateDraggedPoint(double x, double y) {
        layerViewModel.getDrawingTool().updateDraggedPoint(x, y, createCoordinateConverter());
        render();
    }

    public void stopDraggingPoint() {
        layerViewModel.getDrawingTool().stopDraggingPoint();
    }

    public boolean isDraggingPoint() {
        return layerViewModel.getDrawingTool().isDraggingPoint();
    }

    public boolean hasCurrentLine() {
        return layerViewModel.getDrawingTool().hasCurrentLine();
    }

    public DrawingTool.CursorType getCursorType(double mouseX, double mouseY, boolean isNavigationMode) {
        return layerViewModel.getDrawingTool().getCursorType(mouseX, mouseY, createCoordinateConverter(), isNavigationMode);
    }

    public void handleDrawingClick(double x, double y) {
        layerViewModel.getDrawingTool().handleDrawingClick(x, y, createCoordinateConverter());
        render();
    }

    public void handleDrawingMouseMove(double x, double y) {
        layerViewModel.getDrawingTool().handleDrawingMouseMove(x, y);
        render();
    }

    public void handleKeyEscape() {
        layerViewModel.getDrawingTool().abortCurrentLine();
        render();
    }

    private DrawingTool.CoordinateConverter createCoordinateConverter() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        int zoom = viewport.getZoom();
        double cx = viewport.getCenterX();
        double cy = viewport.getCenterY();
        return new DrawingTool.CoordinateConverter() {
            @Override
            public double[] latLonToScreen(double lat, double lon) {
                return TileMath.latLonToScreen(lat, lon, zoom, cx, cy, w, h);
            }

            @Override
            public double[] screenToLatLon(double screenX, double screenY) {
                return TileMath.screenToLatLon(screenX, screenY, zoom, cx, cy, w, h);
            }
        };
    }
}
