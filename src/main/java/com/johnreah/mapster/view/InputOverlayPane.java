package com.johnreah.mapster.view;

import com.johnreah.mapster.view.maptiles.TileMath;
import com.johnreah.mapster.viewmodel.DrawingTool;
import com.johnreah.mapster.viewmodel.MapViewport;

import javafx.scene.Cursor;
import javafx.scene.layout.Background;
import javafx.scene.layout.Pane;

/**
 * Transparent top pane that captures all mouse and keyboard events for the map.
 * Routes pan/zoom updates to {@link MapViewport} and drawing events to the
 * active {@link DrawingLayerView}.
 */
public class InputOverlayPane extends Pane {

    private static final int TILE_SIZE = TileMath.TILE_SIZE;

    private final MapViewport viewport;
    private DrawingLayerView activeDrawingLayer;
    private int minZoom = 0;

    private enum OperationMode { NAVIGATION, DRAWING }
    private OperationMode currentMode = OperationMode.NAVIGATION;

    private double dragStartX, dragStartY;
    private double dragStartCenterX, dragStartCenterY;
    private double lastMouseX, lastMouseY;

    public InputOverlayPane(MapViewport viewport) {
        this.viewport = viewport;
        setBackground(Background.EMPTY);
        setPickOnBounds(true);
        setFocusTraversable(true);
        setCursor(Cursor.CLOSED_HAND);
        setupInputHandlers();
    }

    public void setActiveDrawingLayer(DrawingLayerView layer) {
        this.activeDrawingLayer = layer;
    }

    public void setMinZoom(int minZoom) {
        this.minZoom = minZoom;
    }

    public void setNavigationMode() {
        if (currentMode != OperationMode.NAVIGATION) {
            currentMode = OperationMode.NAVIGATION;
            if (activeDrawingLayer != null) {
                activeDrawingLayer.handleKeyEscape();
            }
            setCursor(Cursor.CLOSED_HAND);
        }
    }

    public void setDrawingMode() {
        if (currentMode != OperationMode.DRAWING) {
            currentMode = OperationMode.DRAWING;
            requestFocus();
            setCursor(Cursor.DEFAULT);
        }
    }

    private void setupInputHandlers() {
        setOnMousePressed(e -> {
            requestFocus();
            dragStartX = e.getX();
            dragStartY = e.getY();
            dragStartCenterX = viewport.getCenterX();
            dragStartCenterY = viewport.getCenterY();

            if (currentMode == OperationMode.NAVIGATION && activeDrawingLayer != null) {
                if (activeDrawingLayer.selectPointNearMouse(e.getX(), e.getY())) {
                    activeDrawingLayer.startDraggingPoint();
                }
            }
        });

        setOnMouseDragged(e -> {
            lastMouseX = e.getX();
            lastMouseY = e.getY();

            if (activeDrawingLayer != null && activeDrawingLayer.isDraggingPoint()) {
                activeDrawingLayer.updateDraggedPoint(e.getX(), e.getY());
                updateNavigationCursor();
            } else if (currentMode == OperationMode.NAVIGATION) {
                double dx = e.getX() - dragStartX;
                double dy = e.getY() - dragStartY;
                viewport.moveTo(
                    dragStartCenterX - dx / TILE_SIZE,
                    dragStartCenterY - dy / TILE_SIZE
                );
                updateNavigationCursor();
            }
        });

        setOnMouseReleased(e -> {
            if (activeDrawingLayer != null && activeDrawingLayer.isDraggingPoint()) {
                activeDrawingLayer.stopDraggingPoint();
            }
        });

        setOnMouseClicked(e -> {
            if (currentMode == OperationMode.DRAWING && activeDrawingLayer != null) {
                activeDrawingLayer.handleDrawingClick(e.getX(), e.getY());
            }
        });

        setOnMouseMoved(e -> {
            lastMouseX = e.getX();
            lastMouseY = e.getY();

            if (currentMode == OperationMode.DRAWING
                    && activeDrawingLayer != null
                    && activeDrawingLayer.hasCurrentLine()) {
                activeDrawingLayer.handleDrawingMouseMove(e.getX(), e.getY());
            } else if (currentMode == OperationMode.NAVIGATION) {
                updateNavigationCursor();
            }
        });

        setOnMouseEntered(e -> {
            if (currentMode == OperationMode.NAVIGATION) setCursor(Cursor.CLOSED_HAND);
            else setCursor(Cursor.DEFAULT);
        });

        setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ESCAPE
                    && currentMode == OperationMode.DRAWING
                    && activeDrawingLayer != null
                    && activeDrawingLayer.hasCurrentLine()) {
                activeDrawingLayer.handleKeyEscape();
            }
        });

        setOnScroll(e -> {
            double mouseX = e.getX();
            double mouseY = e.getY();
            double w = getWidth();
            double h = getHeight();
            if (w <= 0 || h <= 0) return;

            int currentZoom = viewport.getZoom();
            double[] latLon = TileMath.screenToLatLon(
                mouseX, mouseY, currentZoom,
                viewport.getCenterX(), viewport.getCenterY(), w, h
            );

            int newZoom = currentZoom;
            if (e.getDeltaY() > 0 && currentZoom < MapViewport.MAX_ZOOM) newZoom++;
            else if (e.getDeltaY() < 0 && currentZoom > minZoom) newZoom--;

            if (newZoom != currentZoom) {
                viewport.zoomTo(newZoom, latLon[0], latLon[1],
                    mouseX - w / 2.0, mouseY - h / 2.0);
            }
        });
    }

    private void updateNavigationCursor() {
        if (currentMode == OperationMode.NAVIGATION && activeDrawingLayer != null) {
            DrawingTool.CursorType ct =
                activeDrawingLayer.getCursorType(lastMouseX, lastMouseY, true);
            Cursor desired = ct == DrawingTool.CursorType.CLOSED_HAND ? Cursor.CLOSED_HAND : Cursor.DEFAULT;
            if (getCursor() != desired) setCursor(desired);
        }
    }
}
