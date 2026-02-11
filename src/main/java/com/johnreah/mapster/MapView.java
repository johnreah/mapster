package com.johnreah.mapster;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.List;

public class MapView extends StackPane {

    private static final int TILE_SIZE = 256;
    private static final int MAX_ZOOM = 20;
    private static final double FINALIZE_DISTANCE = 10.0; // pixels

    private final Canvas canvas = new Canvas();
    private final TileCache tileCache;
    private final Label attribution;
    private final ComboBox<TileSource> sourceCombo = new ComboBox<>();

    private TileSource tileSource;
    private double centerX;
    private double centerY;
    private int zoom;

    private double dragStartX, dragStartY;
    private double dragStartCenterX, dragStartCenterY;

    private Runnable stateUpdateListener;

    // Drawing mode state
    private boolean drawingMode = false;
    private List<double[]> currentLinePoints = new ArrayList<>(); // [lat, lon]
    private List<List<double[]>> completedLines = new ArrayList<>();
    private double currentMouseX = 0;
    private double currentMouseY = 0;

    // Editing state
    private int selectedLineIndex = -1;
    private int selectedPointIndex = -1;
    private boolean isDraggingPoint = false;

    public MapView(TileSource tileSource) {
        this.tileSource = tileSource;

        // Default view: London
        zoom = 10;
        centerX = TileMath.lonToTileX(-0.09, zoom);
        centerY = TileMath.latToTileY(51.505, zoom);

        tileCache = new TileCache(tileSource, this::render);

        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.widthProperty().addListener(e -> render());
        canvas.heightProperty().addListener(e -> render());

        getChildren().add(canvas);

        attribution = new Label(tileSource.getAttribution());
        attribution.setFont(Font.font(11));
        attribution.setStyle("-fx-background-color: rgba(255,255,255,0.7); -fx-padding: 2 6 2 6;");
        StackPane.setAlignment(attribution, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(attribution, new Insets(0, 4, 4, 0));
        getChildren().add(attribution);

        sourceCombo.setConverter(new StringConverter<>() {
            @Override
            public String toString(TileSource source) {
                return source == null ? "" : source.getDisplayName();
            }

            @Override
            public TileSource fromString(String string) {
                return null;
            }
        });
        sourceCombo.setOnAction(e -> {
            TileSource selected = sourceCombo.getValue();
            if (selected != null && selected != this.tileSource) {
                selectSource(selected);
            }
        });
        StackPane.setAlignment(sourceCombo, Pos.TOP_RIGHT);
        StackPane.setMargin(sourceCombo, new Insets(8, 8, 0, 0));
        getChildren().add(sourceCombo);

        setupInputHandlers();
    }

    public void setAvailableSources(List<TileSource> sources) {
        sourceCombo.getItems().setAll(sources);
        sourceCombo.setValue(tileSource);
    }

    public void selectSource(TileSource source) {
        this.tileSource = source;
        tileCache.setTileSource(source);
        attribution.setText(source.getAttribution());

        // Update combo without re-firing the action
        sourceCombo.setValue(source);

        // Clamp zoom to the minimum zoom level only
        if (zoom < source.getMinZoom()) {
            zoom = source.getMinZoom();
            centerX = TileMath.lonToTileX(TileMath.tileXToLon(centerX, zoom), zoom);
            centerY = TileMath.latToTileY(TileMath.tileYToLat(centerY, zoom), zoom);
        }

        clampCenter();
        render();
        notifyStateUpdate();
    }

    private void setupInputHandlers() {
        canvas.setOnMousePressed(e -> {
            // Request focus so keyboard events work
            canvas.requestFocus();

            dragStartX = e.getX();
            dragStartY = e.getY();
            dragStartCenterX = centerX;
            dragStartCenterY = centerY;

            // Check if clicking on a point of a completed line
            if (!drawingMode) {
                findPointNearMouse(e.getX(), e.getY());
                if (selectedLineIndex != -1 && selectedPointIndex != -1) {
                    isDraggingPoint = true;
                }
            }
        });

        canvas.setOnMouseDragged(e -> {
            if (isDraggingPoint) {
                // Dragging a point on a completed line
                double[] newLatLon = screenToLatLon(e.getX(), e.getY());
                completedLines.get(selectedLineIndex).get(selectedPointIndex)[0] = newLatLon[0];
                completedLines.get(selectedLineIndex).get(selectedPointIndex)[1] = newLatLon[1];
                render();
            } else if (!drawingMode) {
                // Normal map panning
                double dx = e.getX() - dragStartX;
                double dy = e.getY() - dragStartY;
                centerX = dragStartCenterX - dx / TILE_SIZE;
                centerY = dragStartCenterY - dy / TILE_SIZE;
                clampCenter();
                render();
                notifyStateUpdate();
            }
        });

        canvas.setOnMouseReleased(e -> {
            if (isDraggingPoint) {
                isDraggingPoint = false;
                selectedLineIndex = -1;
                selectedPointIndex = -1;
            }
        });

        canvas.setOnMouseClicked(e -> {
            if (drawingMode) {
                handleDrawingClick(e.getX(), e.getY());
            }
        });

        canvas.setOnMouseMoved(e -> {
            if (drawingMode && !currentLinePoints.isEmpty()) {
                currentMouseX = e.getX();
                currentMouseY = e.getY();
                render();
            }
        });

        canvas.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE && drawingMode && !currentLinePoints.isEmpty()) {
                // Abort current line drawing
                currentLinePoints.clear();
                render();
            }
        });

        // Make canvas focusable so it can receive keyboard events
        canvas.setFocusTraversable(true);

        canvas.setOnScroll(e -> {
            double mouseX = e.getX();
            double mouseY = e.getY();

            // Convert mouse position to tile coordinates before zoom
            double tileXBefore = centerX + (mouseX - canvas.getWidth() / 2.0) / TILE_SIZE;
            double tileYBefore = centerY + (mouseY - canvas.getHeight() / 2.0) / TILE_SIZE;

            // Convert to geographic coordinates
            double lon = TileMath.tileXToLon(tileXBefore, zoom);
            double lat = TileMath.tileYToLat(tileYBefore, zoom);

            int oldZoom = zoom;
            if (e.getDeltaY() > 0 && zoom < MAX_ZOOM) {
                zoom++;
            } else if (e.getDeltaY() < 0 && zoom > tileSource.getMinZoom()) {
                zoom--;
            }

            if (zoom != oldZoom) {
                // Convert geographic coordinates back to tile coordinates at new zoom
                double tileXAfter = TileMath.lonToTileX(lon, zoom);
                double tileYAfter = TileMath.latToTileY(lat, zoom);

                // Adjust center so the point under the cursor stays fixed
                centerX = tileXAfter - (mouseX - canvas.getWidth() / 2.0) / TILE_SIZE;
                centerY = tileYAfter - (mouseY - canvas.getHeight() / 2.0) / TILE_SIZE;
                clampCenter();
                render();
                notifyStateUpdate();
            }
        });
    }

    private void clampCenter() {
        double max = TileMath.maxTile(zoom);
        // Wrap X horizontally
        centerX = ((centerX % max) + max) % max;
        // Clamp Y
        if (centerY < 0) centerY = 0;
        if (centerY > max) centerY = max;
    }

    private void render() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.LIGHTGRAY);
        gc.fillRect(0, 0, w, h);

        double max = TileMath.maxTile(zoom);

        // Pixel offset of top-left corner relative to tile grid origin
        double offsetX = w / 2.0 - centerX * TILE_SIZE;
        double offsetY = h / 2.0 - centerY * TILE_SIZE;

        // Visible tile range
        int tileLeft = (int) Math.floor(-offsetX / TILE_SIZE);
        int tileRight = (int) Math.floor((-offsetX + w) / TILE_SIZE);
        int tileTop = (int) Math.floor(-offsetY / TILE_SIZE);
        int tileBottom = (int) Math.floor((-offsetY + h) / TILE_SIZE);

        for (int ty = tileTop; ty <= tileBottom; ty++) {
            if (ty < 0 || ty >= (int) max) continue;
            for (int tx = tileLeft; tx <= tileRight; tx++) {
                // Wrap X for horizontal wrapping
                int wrappedX = ((tx % (int) max) + (int) max) % (int) max;

                double px = offsetX + tx * TILE_SIZE;
                double py = offsetY + ty * TILE_SIZE;

                Image tile = tileCache.getTile(zoom, wrappedX, ty);
                if (tile != null) {
                    gc.drawImage(tile, px, py, TILE_SIZE, TILE_SIZE);
                } else {
                    gc.setFill(Color.rgb(220, 220, 220));
                    gc.fillRect(px, py, TILE_SIZE, TILE_SIZE);
                    gc.setStroke(Color.rgb(200, 200, 200));
                    gc.strokeRect(px, py, TILE_SIZE, TILE_SIZE);
                }
            }
        }

        // Draw completed lines
        renderLines(gc, completedLines, Color.BLUE, 2.0);

        // Draw points on completed lines
        gc.setFill(Color.BLUE);
        for (List<double[]> line : completedLines) {
            for (double[] point : line) {
                double[] screenPos = latLonToScreen(point[0], point[1]);
                gc.fillOval(screenPos[0] - 4, screenPos[1] - 4, 8, 8);
            }
        }

        // Draw current line being drawn
        if (!currentLinePoints.isEmpty()) {
            List<List<double[]>> currentLineAsList = new ArrayList<>();
            currentLineAsList.add(currentLinePoints);
            renderLines(gc, currentLineAsList, Color.RED, 3.0);

            // Draw preview line from last point to current mouse position
            double[] lastPoint = currentLinePoints.get(currentLinePoints.size() - 1);
            double[] lastScreenPos = latLonToScreen(lastPoint[0], lastPoint[1]);
            gc.setStroke(Color.rgb(255, 100, 100, 0.6));
            gc.setLineWidth(2.0);
            gc.strokeLine(lastScreenPos[0], lastScreenPos[1], currentMouseX, currentMouseY);

            // Draw points
            gc.setFill(Color.RED);
            for (double[] point : currentLinePoints) {
                double[] screenPos = latLonToScreen(point[0], point[1]);
                gc.fillOval(screenPos[0] - 4, screenPos[1] - 4, 8, 8);
            }
        }
    }

    private void renderLines(GraphicsContext gc, List<List<double[]>> lines, Color color, double lineWidth) {
        gc.setStroke(color);
        gc.setLineWidth(lineWidth);

        for (List<double[]> line : lines) {
            if (line.size() < 2) continue;

            for (int i = 0; i < line.size() - 1; i++) {
                double[] p1 = latLonToScreen(line.get(i)[0], line.get(i)[1]);
                double[] p2 = latLonToScreen(line.get(i + 1)[0], line.get(i + 1)[1]);
                gc.strokeLine(p1[0], p1[1], p2[0], p2[1]);
            }
        }
    }

    private double[] latLonToScreen(double lat, double lon) {
        double tileX = TileMath.lonToTileX(lon, zoom);
        double tileY = TileMath.latToTileY(lat, zoom);

        double w = canvas.getWidth();
        double h = canvas.getHeight();
        double offsetX = w / 2.0 - centerX * TILE_SIZE;
        double offsetY = h / 2.0 - centerY * TILE_SIZE;

        double screenX = offsetX + tileX * TILE_SIZE;
        double screenY = offsetY + tileY * TILE_SIZE;

        return new double[]{screenX, screenY};
    }

    private double[] screenToLatLon(double screenX, double screenY) {
        double w = canvas.getWidth();
        double h = canvas.getHeight();

        double tileX = centerX + (screenX - w / 2.0) / TILE_SIZE;
        double tileY = centerY + (screenY - h / 2.0) / TILE_SIZE;

        double lon = TileMath.tileXToLon(tileX, zoom);
        double lat = TileMath.tileYToLat(tileY, zoom);

        return new double[]{lat, lon};
    }

    public void shutdown() {
        tileCache.shutdown();
    }

    public void setStateUpdateListener(Runnable listener) {
        this.stateUpdateListener = listener;
    }

    private void notifyStateUpdate() {
        if (stateUpdateListener != null) {
            stateUpdateListener.run();
        }
    }

    public void zoomIn() {
        if (zoom < MAX_ZOOM) {
            double lon = TileMath.tileXToLon(centerX, zoom);
            double lat = TileMath.tileYToLat(centerY, zoom);
            zoom++;
            centerX = TileMath.lonToTileX(lon, zoom);
            centerY = TileMath.latToTileY(lat, zoom);
            clampCenter();
            render();
            notifyStateUpdate();
        }
    }

    public void zoomOut() {
        if (zoom > tileSource.getMinZoom()) {
            double lon = TileMath.tileXToLon(centerX, zoom);
            double lat = TileMath.tileYToLat(centerY, zoom);
            zoom--;
            centerX = TileMath.lonToTileX(lon, zoom);
            centerY = TileMath.latToTileY(lat, zoom);
            clampCenter();
            render();
            notifyStateUpdate();
        }
    }

    public int getZoom() {
        return zoom;
    }

    public double[] getCenterLatLon() {
        double lon = TileMath.tileXToLon(centerX, zoom);
        double lat = TileMath.tileYToLat(centerY, zoom);
        return new double[]{lat, lon};
    }

    public void setDrawingMode(boolean enabled) {
        this.drawingMode = enabled;
        if (enabled) {
            // Request focus when entering drawing mode
            canvas.requestFocus();
        } else {
            // Exit drawing mode - clear current line
            currentLinePoints.clear();
            render();
        }
    }

    private void handleDrawingClick(double screenX, double screenY) {
        double[] latLon = screenToLatLon(screenX, screenY);

        // Check if this click is close to the last point (finalize)
        if (currentLinePoints.size() >= 2) {
            double[] lastPoint = currentLinePoints.get(currentLinePoints.size() - 1);
            double[] lastScreenPos = latLonToScreen(lastPoint[0], lastPoint[1]);

            double distance = Math.sqrt(
                Math.pow(screenX - lastScreenPos[0], 2) +
                Math.pow(screenY - lastScreenPos[1], 2)
            );

            if (distance < FINALIZE_DISTANCE) {
                // Finalize the line
                if (currentLinePoints.size() >= 2) {
                    completedLines.add(new ArrayList<>(currentLinePoints));
                }
                currentLinePoints.clear();
                render();
                return;
            }
        }

        // Add new point to the current line
        currentLinePoints.add(latLon);

        // Update mouse position so preview line is correct
        currentMouseX = screenX;
        currentMouseY = screenY;

        render();
    }

    private void findPointNearMouse(double mouseX, double mouseY) {
        selectedLineIndex = -1;
        selectedPointIndex = -1;

        double threshold = 8.0; // pixels

        for (int lineIdx = 0; lineIdx < completedLines.size(); lineIdx++) {
            List<double[]> line = completedLines.get(lineIdx);
            for (int pointIdx = 0; pointIdx < line.size(); pointIdx++) {
                double[] point = line.get(pointIdx);
                double[] screenPos = latLonToScreen(point[0], point[1]);

                double distance = Math.sqrt(
                    Math.pow(mouseX - screenPos[0], 2) +
                    Math.pow(mouseY - screenPos[1], 2)
                );

                if (distance < threshold) {
                    selectedLineIndex = lineIdx;
                    selectedPointIndex = pointIdx;
                    return;
                }
            }
        }
    }
}
