package com.johnreah.mapster.viewmodel;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates drawing behavior for multi-segment lines on a map.
 * Handles both drawing new lines and editing existing ones.
 */
public class DrawingTool {

    private static final double FINALIZE_DISTANCE = 10.0; // pixels
    private static final double NODE_PROXIMITY_THRESHOLD = 10.0; // pixels

    // Drawing state
    private List<double[]> currentLinePoints = new ArrayList<>(); // [lat, lon]
    private List<List<double[]>> completedLines = new ArrayList<>();
    private double currentMouseX = 0;
    private double currentMouseY = 0;

    // Editing state
    private int selectedLineIndex = -1;
    private int selectedPointIndex = -1;
    private boolean isDraggingPoint = false;

    /**
     * Handle a click event during drawing mode.
     * @param screenX Screen X coordinate
     * @param screenY Screen Y coordinate
     * @param converter Coordinate converter
     */
    public void handleDrawingClick(double screenX, double screenY, CoordinateConverter converter) {
        double[] latLon = converter.screenToLatLon(screenX, screenY);

        // Check if this click is close to the last point (finalize)
        if (currentLinePoints.size() >= 2) {
            double[] lastPoint = currentLinePoints.get(currentLinePoints.size() - 1);
            double[] lastScreenPos = converter.latLonToScreen(lastPoint[0], lastPoint[1]);

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
                return;
            }
        }

        // Add new point to the current line
        currentLinePoints.add(latLon);

        // Update mouse position so preview line is correct
        currentMouseX = screenX;
        currentMouseY = screenY;
    }

    /**
     * Handle mouse move event during drawing mode.
     * @param screenX Screen X coordinate
     * @param screenY Screen Y coordinate
     */
    public void handleDrawingMouseMove(double screenX, double screenY) {
        if (!currentLinePoints.isEmpty()) {
            currentMouseX = screenX;
            currentMouseY = screenY;
        }
    }

    /**
     * Abort the current line being drawn.
     */
    public void abortCurrentLine() {
        currentLinePoints.clear();
    }

    /**
     * Check if a point near the mouse position can be selected for editing.
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param converter Coordinate converter
     * @return true if a point was selected
     */
    public boolean selectPointNearMouse(double mouseX, double mouseY, CoordinateConverter converter) {
        selectedLineIndex = -1;
        selectedPointIndex = -1;

        for (int lineIdx = 0; lineIdx < completedLines.size(); lineIdx++) {
            List<double[]> line = completedLines.get(lineIdx);
            for (int pointIdx = 0; pointIdx < line.size(); pointIdx++) {
                double[] point = line.get(pointIdx);
                double[] screenPos = converter.latLonToScreen(point[0], point[1]);

                double distance = Math.sqrt(
                    Math.pow(mouseX - screenPos[0], 2) +
                    Math.pow(mouseY - screenPos[1], 2)
                );

                if (distance < NODE_PROXIMITY_THRESHOLD) {
                    selectedLineIndex = lineIdx;
                    selectedPointIndex = pointIdx;
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Find if there is a point near the mouse without selecting it.
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param converter Coordinate converter
     * @return true if a point is near the mouse
     */
    public boolean isPointNearMouse(double mouseX, double mouseY, CoordinateConverter converter) {
        for (List<double[]> line : completedLines) {
            for (double[] point : line) {
                double[] screenPos = converter.latLonToScreen(point[0], point[1]);

                double distance = Math.sqrt(
                    Math.pow(mouseX - screenPos[0], 2) +
                    Math.pow(mouseY - screenPos[1], 2)
                );

                if (distance < NODE_PROXIMITY_THRESHOLD) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Start dragging the currently selected point.
     */
    public void startDraggingPoint() {
        if (selectedLineIndex != -1 && selectedPointIndex != -1) {
            isDraggingPoint = true;
        }
    }

    /**
     * Update the position of the point being dragged.
     * @param screenX Screen X coordinate
     * @param screenY Screen Y coordinate
     * @param converter Coordinate converter
     */
    public void updateDraggedPoint(double screenX, double screenY, CoordinateConverter converter) {
        if (isDraggingPoint && selectedLineIndex != -1 && selectedPointIndex != -1) {
            double[] newLatLon = converter.screenToLatLon(screenX, screenY);
            completedLines.get(selectedLineIndex).get(selectedPointIndex)[0] = newLatLon[0];
            completedLines.get(selectedLineIndex).get(selectedPointIndex)[1] = newLatLon[1];
        }
    }

    /**
     * Stop dragging the currently selected point.
     */
    public void stopDraggingPoint() {
        isDraggingPoint = false;
        selectedLineIndex = -1;
        selectedPointIndex = -1;
    }

    public enum CursorType { DEFAULT, CLOSED_HAND }

    /**
     * Get the appropriate cursor type for the current state.
     * @param mouseX Mouse X coordinate
     * @param mouseY Mouse Y coordinate
     * @param converter Coordinate converter
     * @param isNavigationMode true if in navigation mode
     * @return CursorType to display
     */
    public CursorType getCursorType(double mouseX, double mouseY, CoordinateConverter converter, boolean isNavigationMode) {
        if (isNavigationMode) {
            return isPointNearMouse(mouseX, mouseY, converter) ? CursorType.DEFAULT : CursorType.CLOSED_HAND;
        } else {
            return CursorType.DEFAULT;
        }
    }

    /**
     * Render all lines on the canvas.
     * @param gc Graphics context
     * @param converter Coordinate converter
     */
    public void render(GraphicsContext gc, CoordinateConverter converter) {
        // Draw completed lines
        renderLines(gc, completedLines, Color.BLUE, 2.0, converter);

        // Draw points on completed lines
        gc.setFill(Color.BLUE);
        for (List<double[]> line : completedLines) {
            for (double[] point : line) {
                double[] screenPos = converter.latLonToScreen(point[0], point[1]);
                gc.fillOval(screenPos[0] - 4, screenPos[1] - 4, 8, 8);
            }
        }

        // Draw current line being drawn
        if (!currentLinePoints.isEmpty()) {
            List<List<double[]>> currentLineAsList = new ArrayList<>();
            currentLineAsList.add(currentLinePoints);
            renderLines(gc, currentLineAsList, Color.RED, 3.0, converter);

            // Draw preview line from last point to current mouse position
            double[] lastPoint = currentLinePoints.get(currentLinePoints.size() - 1);
            double[] lastScreenPos = converter.latLonToScreen(lastPoint[0], lastPoint[1]);
            gc.setStroke(Color.rgb(255, 100, 100, 0.6));
            gc.setLineWidth(2.0);
            gc.strokeLine(lastScreenPos[0], lastScreenPos[1], currentMouseX, currentMouseY);

            // Draw points
            gc.setFill(Color.RED);
            for (double[] point : currentLinePoints) {
                double[] screenPos = converter.latLonToScreen(point[0], point[1]);
                gc.fillOval(screenPos[0] - 4, screenPos[1] - 4, 8, 8);
            }
        }
    }

    private void renderLines(GraphicsContext gc, List<List<double[]>> lines, Color color, double lineWidth, CoordinateConverter converter) {
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

    public boolean isDraggingPoint() {
        return isDraggingPoint;
    }

    public boolean hasCurrentLine() {
        return !currentLinePoints.isEmpty();
    }

    /**
     * Interface for coordinate conversion between screen and geographic coordinates.
     */
    public interface CoordinateConverter {
        double[] latLonToScreen(double lat, double lon);
        double[] screenToLatLon(double screenX, double screenY);
    }
}
