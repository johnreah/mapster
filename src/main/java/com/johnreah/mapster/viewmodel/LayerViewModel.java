package com.johnreah.mapster.viewmodel;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;

/**
 * Base ViewModel for a single map layer. Holds display state (opacity, visibility)
 * shared by all layer types.
 */
public class LayerViewModel {

    private final String id;
    private final String displayName;
    private final DoubleProperty opacity = new SimpleDoubleProperty(1.0);
    private final BooleanProperty visible = new SimpleBooleanProperty(true);

    public LayerViewModel(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() { return id; }
    public String getDisplayName() { return displayName; }

    public DoubleProperty opacityProperty() { return opacity; }
    public BooleanProperty visibleProperty() { return visible; }

    public double getOpacity() { return opacity.get(); }
    public boolean isVisible() { return visible.get(); }
}
