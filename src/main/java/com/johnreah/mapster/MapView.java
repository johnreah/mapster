package com.johnreah.mapster;

import javafx.scene.Cursor;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

public class MapView extends Pane {

    private static final int TILE_SIZE = TileMath.TILE_SIZE;
    private static final int MAX_ZOOM = 20;

    private final Canvas canvas = new Canvas();
    private final TileCache tileCache;
    private final DrawingTool drawingTool = new DrawingTool();

    private TileSource tileSource;
    private double centerX;
    private double centerY;
    private int zoom;

    private double dragStartX, dragStartY;
    private double dragStartCenterX, dragStartCenterY;

    private Runnable stateUpdateListener;

    // Operation mode
    private enum OperationMode {
        NAVIGATION,
        DRAWING
    }
    private OperationMode currentMode = OperationMode.NAVIGATION;

    private double lastMouseX = 0;
    private double lastMouseY = 0;

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

        // Set default cursor for navigation mode
        setCursor(Cursor.CLOSED_HAND);

        getChildren().add(canvas);

        setupInputHandlers();
    }

    public void selectSource(TileSource source) {
        this.tileSource = source;
        tileCache.setTileSource(source);

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

            // Check if clicking on a point of a completed line in navigation mode
            if (currentMode == OperationMode.NAVIGATION) {
                if (drawingTool.selectPointNearMouse(e.getX(), e.getY(), createCoordinateConverter())) {
                    drawingTool.startDraggingPoint();
                }
            }
        });

        canvas.setOnMouseDragged(e -> {
            lastMouseX = e.getX();
            lastMouseY = e.getY();

            if (drawingTool.isDraggingPoint()) {
                // Dragging a point on a completed line
                drawingTool.updateDraggedPoint(e.getX(), e.getY(), createCoordinateConverter());
                render();
                updateNavigationCursor();
            } else if (currentMode == OperationMode.NAVIGATION) {
                // Normal map panning
                double dx = e.getX() - dragStartX;
                double dy = e.getY() - dragStartY;
                centerX = dragStartCenterX - dx / TILE_SIZE;
                centerY = dragStartCenterY - dy / TILE_SIZE;
                clampCenter();
                render();
                updateNavigationCursor();
                notifyStateUpdate();
            }
        });

        canvas.setOnMouseReleased(e -> {
            if (drawingTool.isDraggingPoint()) {
                drawingTool.stopDraggingPoint();
            }
        });

        canvas.setOnMouseClicked(e -> {
            if (currentMode == OperationMode.DRAWING) {
                drawingTool.handleDrawingClick(e.getX(), e.getY(), createCoordinateConverter());
                render();
            }
        });

        canvas.setOnMouseMoved(e -> {
            lastMouseX = e.getX();
            lastMouseY = e.getY();

            if (currentMode == OperationMode.DRAWING && drawingTool.hasCurrentLine()) {
                drawingTool.handleDrawingMouseMove(e.getX(), e.getY());
                render();
            } else if (currentMode == OperationMode.NAVIGATION) {
                updateNavigationCursor();
            }
        });

        setOnMouseEntered(e -> {
            if (currentMode == OperationMode.NAVIGATION) {
                setCursor(Cursor.CLOSED_HAND);
            } else if (currentMode == OperationMode.DRAWING) {
                setCursor(Cursor.DEFAULT);
            }
        });

        canvas.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE && currentMode == OperationMode.DRAWING && drawingTool.hasCurrentLine()) {
                // Abort current line drawing
                drawingTool.abortCurrentLine();
                render();
            }
        });

        // Make canvas focusable so it can receive keyboard events
        canvas.setFocusTraversable(true);

        canvas.setOnScroll(e -> {
            double mouseX = e.getX();
            double mouseY = e.getY();

            // Convert mouse position to geographic coordinates before zoom
            double[] latLon = TileMath.screenToLatLon(mouseX, mouseY, zoom, centerX, centerY, canvas.getWidth(), canvas.getHeight());
            double lat = latLon[0];
            double lon = latLon[1];

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

        // Draw lines and points using DrawingTool
        drawingTool.render(gc, createCoordinateConverter());
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

    public void setNavigationMode() {
        if (currentMode != OperationMode.NAVIGATION) {
            currentMode = OperationMode.NAVIGATION;
            // Clear any current line being drawn
            drawingTool.abortCurrentLine();
            render();
            setCursor(Cursor.CLOSED_HAND);
        }
    }

    public void setDrawingMode() {
        if (currentMode != OperationMode.DRAWING) {
            currentMode = OperationMode.DRAWING;
            canvas.requestFocus();
            render();
            setCursor(Cursor.DEFAULT);
        }
    }

    private void updateNavigationCursor() {
        if (currentMode == OperationMode.NAVIGATION) {
            Cursor desiredCursor = drawingTool.getCursor(lastMouseX, lastMouseY, createCoordinateConverter(), true);
            if (getCursor() != desiredCursor) {
                setCursor(desiredCursor);
            }
        }
    }

    private DrawingTool.CoordinateConverter createCoordinateConverter() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        return new DrawingTool.CoordinateConverter() {
            @Override
            public double[] latLonToScreen(double lat, double lon) {
                return TileMath.latLonToScreen(lat, lon, zoom, centerX, centerY, w, h);
            }

            @Override
            public double[] screenToLatLon(double screenX, double screenY) {
                return TileMath.screenToLatLon(screenX, screenY, zoom, centerX, centerY, w, h);
            }
        };
    }
}
