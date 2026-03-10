package com.johnreah.mapster.viewmodel;

import java.util.ArrayList;
import java.util.Collections;
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

    private Runnable onChanged;

    /** Registers a callback invoked whenever drawing state changes and a re-render is needed. */
    public void setOnChanged(Runnable onChanged) {
        this.onChanged = onChanged;
    }

    private void notifyChanged() {
        if (onChanged != null) onChanged.run();
    }

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
        notifyChanged();
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
            notifyChanged();
        }
    }

    /**
     * Abort the current line being drawn.
     */
    public void abortCurrentLine() {
        currentLinePoints.clear();
        notifyChanged();
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
            notifyChanged();
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

    public List<List<double[]>> getCompletedLines() {
        return Collections.unmodifiableList(completedLines);
    }

    public List<double[]> getCurrentLinePoints() {
        return Collections.unmodifiableList(currentLinePoints);
    }

    public double getCurrentMouseX() { return currentMouseX; }
    public double getCurrentMouseY() { return currentMouseY; }

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
