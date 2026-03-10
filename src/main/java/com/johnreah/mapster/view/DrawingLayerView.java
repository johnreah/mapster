package com.johnreah.mapster.view;

import com.johnreah.mapster.util.TileMath;
import com.johnreah.mapster.viewmodel.DrawingLayerViewModel;
import com.johnreah.mapster.viewmodel.DrawingTool;
import com.johnreah.mapster.viewmodel.MapViewport;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

import java.util.List;

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

        // Render whenever drawing state changes
        layerViewModel.getDrawingTool().setOnChanged(this::render);

        viewport.centerXProperty().addListener((obs, old, val) -> render());
        viewport.centerYProperty().addListener((obs, old, val) -> render());
        viewport.zoomProperty().addListener((obs, old, val) -> render());
    }

    public void render() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);
        DrawingTool tool = layerViewModel.getDrawingTool();
        DrawingTool.CoordinateConverter converter = createCoordinateConverter();

        // Draw completed lines and their points
        renderLines(gc, tool.getCompletedLines(), Color.BLUE, 2.0, converter);
        gc.setFill(Color.BLUE);
        for (List<double[]> line : tool.getCompletedLines()) {
            for (double[] point : line) {
                double[] screenPos = converter.latLonToScreen(point[0], point[1]);
                gc.fillOval(screenPos[0] - 4, screenPos[1] - 4, 8, 8);
            }
        }

        // Draw current line being drawn
        List<double[]> currentLine = tool.getCurrentLinePoints();
        if (!currentLine.isEmpty()) {
            renderLines(gc, List.of(currentLine), Color.RED, 3.0, converter);

            // Draw preview line from last point to current mouse position
            double[] lastPoint = currentLine.get(currentLine.size() - 1);
            double[] lastScreenPos = converter.latLonToScreen(lastPoint[0], lastPoint[1]);
            gc.setStroke(Color.rgb(255, 100, 100, 0.6));
            gc.setLineWidth(2.0);
            gc.strokeLine(lastScreenPos[0], lastScreenPos[1], tool.getCurrentMouseX(), tool.getCurrentMouseY());

            // Draw points
            gc.setFill(Color.RED);
            for (double[] point : currentLine) {
                double[] screenPos = converter.latLonToScreen(point[0], point[1]);
                gc.fillOval(screenPos[0] - 4, screenPos[1] - 4, 8, 8);
            }
        }
    }

    private void renderLines(GraphicsContext gc, List<List<double[]>> lines, Color color, double lineWidth,
                             DrawingTool.CoordinateConverter converter) {
        gc.setStroke(color);
        gc.setLineWidth(lineWidth);
        for (List<double[]> line : lines) {
            if (line.size() < 2) continue;
            for (int i = 0; i < line.size() - 1; i++) {
                double[] p1 = converter.latLonToScreen(line.get(i)[0], line.get(i)[1]);
                double[] p2 = converter.latLonToScreen(line.get(i + 1)[0], line.get(i + 1)[1]);
                gc.strokeLine(p1[0], p1[1], p2[0], p2[1]);
            }
        }
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
    }

    public void handleDrawingMouseMove(double x, double y) {
        layerViewModel.getDrawingTool().handleDrawingMouseMove(x, y);
    }

    public void handleKeyEscape() {
        layerViewModel.getDrawingTool().abortCurrentLine();
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
