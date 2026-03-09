package com.johnreah.mapster.viewmodel;

/**
 * ViewModel for a drawing layer. Owns the {@link DrawingTool} that holds all
 * drawing state and behaviour for this layer.
 */
public class DrawingLayerViewModel extends LayerViewModel {

    private final DrawingTool drawingTool = new DrawingTool();

    public DrawingLayerViewModel(String id, String displayName) {
        super(id, displayName);
    }

    public DrawingTool getDrawingTool() { return drawingTool; }
}
