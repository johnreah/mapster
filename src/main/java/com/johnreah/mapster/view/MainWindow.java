package com.johnreah.mapster.view;

import com.johnreah.mapster.view.maptiles.GoogleSatelliteTileSource;
import com.johnreah.mapster.view.maptiles.GoogleStreetMapsTileSource;
import com.johnreah.mapster.view.maptiles.OrdnanceSurveyTileSource;
import com.johnreah.mapster.view.maptiles.OsmTileSource;
import com.johnreah.mapster.view.maptiles.TileMath;
import com.johnreah.mapster.view.maptiles.TileSource;
import com.johnreah.mapster.viewmodel.DrawingLayerViewModel;
import com.johnreah.mapster.viewmodel.LayerStack;
import com.johnreah.mapster.viewmodel.LayerViewModel;
import com.johnreah.mapster.viewmodel.MapViewport;
import com.johnreah.mapster.viewmodel.TileLayerViewModel;

import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainWindow {

    private MapView mapView;
    private MapViewport viewport;
    private LayerStack layerStack;

    private Label centerLatLabel;
    private Label centerLonLabel;
    private Label zoomLabel;

    public void show(Stage stage) {
        viewport = new MapViewport();
        layerStack = new LayerStack();

        List<TileSource> sources = buildAvailableSources();
        TileSource defaultSource = sources.get(0);

        TileLayerViewModel tileLayer = new TileLayerViewModel("tile-0", defaultSource.getDisplayName(), defaultSource);
        layerStack.addLayer(tileLayer);

        TileLayerViewModel satelliteLayer = new TileLayerViewModel(
                "tile-satellite", "Google Satellite", new GoogleSatelliteTileSource());
        layerStack.addLayer(satelliteLayer);

        OrdnanceSurveyTileSource osRoad = new OrdnanceSurveyTileSource(
                OrdnanceSurveyTileSource.ROAD_LAYER, OrdnanceSurveyTileSource.ROAD_DISPLAY_NAME);
        if (osRoad.isAvailable()) {
            layerStack.addLayer(new TileLayerViewModel("tile-os-road", osRoad.getDisplayName(), osRoad));
        }

        DrawingLayerViewModel drawingLayer = new DrawingLayerViewModel("drawing-0", "Drawing Layer");
        layerStack.addLayer(drawingLayer);
        layerStack.activeDrawingLayerProperty().set(drawingLayer);

        mapView = new MapView(viewport, layerStack);
        mapView.setMinWidth(200);

        MenuBar menuBar = buildMenuBar(sources, defaultSource, tileLayer);
        ToolBar toolBar = buildToolBar();
        VBox topArea = new VBox(menuBar, toolBar);
        VBox sidePanel = buildSidePanel();

        SplitPane splitPane = new SplitPane();
        splitPane.setOrientation(Orientation.HORIZONTAL);
        splitPane.getItems().addAll(sidePanel, mapView);
        splitPane.setDividerPositions(0.2);
        SplitPane.setResizableWithParent(sidePanel, false);

        BorderPane root = new BorderPane();
        root.setTop(topArea);
        root.setCenter(splitPane);

        Scene scene = new Scene(root, 1024, 768);
        stage.setTitle("Mapster");
        stage.setScene(scene);
        stage.show();

        viewport.centerXProperty().addListener(obs -> updateState());
        viewport.centerYProperty().addListener(obs -> updateState());
        viewport.zoomProperty().addListener(obs -> updateState());
        updateState();
    }

    public void shutdown() {
        if (mapView != null) {
            mapView.shutdown();
        }
    }

    private List<TileSource> buildAvailableSources() {
        List<TileSource> sources = new ArrayList<>();
        sources.add(new OsmTileSource());
        sources.add(new GoogleStreetMapsTileSource());
        sources.add(new GoogleSatelliteTileSource());

        OrdnanceSurveyTileSource osRoad = new OrdnanceSurveyTileSource(
            OrdnanceSurveyTileSource.ROAD_LAYER, OrdnanceSurveyTileSource.ROAD_DISPLAY_NAME);
        if (osRoad.isAvailable()) sources.add(osRoad);

        OrdnanceSurveyTileSource osOutdoor = new OrdnanceSurveyTileSource(
            OrdnanceSurveyTileSource.OUTDOOR_LAYER, OrdnanceSurveyTileSource.OUTDOOR_DISPLAY_NAME);
        if (osOutdoor.isAvailable()) sources.add(osOutdoor);

        return sources;
    }

    private VBox buildSidePanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-background-color: #f5f5f5;");
        panel.setMinWidth(150);
        panel.setMaxWidth(500);
        panel.setPrefWidth(200);

        Label mapInfoTitle = new Label("Map Information");
        mapInfoTitle.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        centerLatLabel = new Label("Latitude: -");
        centerLonLabel = new Label("Longitude: -");
        zoomLabel = new Label("Zoom: -");

        VBox layerListPanel = buildLayerListPanel();

        panel.getChildren().addAll(
            mapInfoTitle, new Separator(),
            centerLatLabel, centerLonLabel, zoomLabel,
            new Separator(),
            layerListPanel
        );

        return panel;
    }

    private VBox buildLayerListPanel() {
        VBox panel = new VBox(6);

        Label heading = new Label("Layers");
        heading.setStyle("-fx-font-weight: bold;");
        panel.getChildren().add(heading);

        layerStack.getLayers().addListener((ListChangeListener<LayerViewModel>) c ->
            rebuildLayerRows(panel));
        rebuildLayerRows(panel);

        return panel;
    }

    private void rebuildLayerRows(VBox panel) {
        // Keep the heading (first child), replace the rest
        javafx.scene.Node heading = panel.getChildren().get(0);
        panel.getChildren().clear();
        panel.getChildren().add(heading);

        // Show layers top-first (reverse of stack order)
        List<LayerViewModel> reversed = new ArrayList<>(layerStack.getLayers());
        Collections.reverse(reversed);
        for (LayerViewModel layer : reversed) {
            panel.getChildren().add(buildLayerRow(layer));
        }
    }

    private HBox buildLayerRow(LayerViewModel layer) {
        CheckBox visibleCheck = new CheckBox();
        visibleCheck.selectedProperty().bindBidirectional(layer.visibleProperty());

        Label nameLabel = new Label(layer.getDisplayName());
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        Slider opacitySlider = new Slider(0.0, 1.0, layer.getOpacity());
        opacitySlider.setPrefWidth(70);
        opacitySlider.setTooltip(new Tooltip("Opacity"));
        opacitySlider.valueProperty().bindBidirectional(layer.opacityProperty());

        return new HBox(4, visibleCheck, nameLabel, opacitySlider);
    }

    private void updateState() {
        double[] center = viewport.getCenterLatLon();
        centerLatLabel.setText(String.format("Latitude: %.6f", center[0]));
        centerLonLabel.setText(String.format("Longitude: %.6f", center[1]));
        zoomLabel.setText("Zoom: " + viewport.getZoom());
    }

    private ToolBar buildToolBar() {
        Button zoomInButton = new Button("+");
        zoomInButton.setOnAction(e -> mapView.zoomIn());
        Button zoomOutButton = new Button("\u2013");
        zoomOutButton.setOnAction(e -> mapView.zoomOut());

        ToggleGroup modeGroup = new ToggleGroup();

        ToggleButton navigationModeButton = new ToggleButton("\u2630");
        navigationModeButton.setToggleGroup(modeGroup);
        navigationModeButton.setSelected(true);
        navigationModeButton.setOnAction(e -> mapView.setNavigationMode());

        ToggleButton drawingModeButton = new ToggleButton("\u270E");
        drawingModeButton.setToggleGroup(modeGroup);
        drawingModeButton.setOnAction(e -> mapView.setDrawingMode());

        return new ToolBar(zoomInButton, zoomOutButton, new Separator(), navigationModeButton, drawingModeButton);
    }

    private MenuBar buildMenuBar(List<TileSource> sources, TileSource defaultSource,
                                  TileLayerViewModel tileLayer) {
        Menu layersMenu = new Menu("Layers");
        ToggleGroup toggleGroup = new ToggleGroup();

        for (TileSource source : sources) {
            RadioMenuItem item = new RadioMenuItem(source.getDisplayName());
            item.setToggleGroup(toggleGroup);
            item.setSelected(source == defaultSource);
            item.setOnAction(e -> {
                tileLayer.setTileSource(source);
                // Clamp zoom if the new source has a higher minimum zoom
                if (viewport.getZoom() < source.getMinZoom()) {
                    double[] latLon = viewport.getCenterLatLon();
                    int newZoom = source.getMinZoom();
                    viewport.zoomProperty().set(newZoom);
                    viewport.moveTo(
                        TileMath.lonToTileX(latLon[1], newZoom),
                        TileMath.latToTileY(latLon[0], newZoom)
                    );
                }
            });
            layersMenu.getItems().add(item);
        }

        MenuBar menuBar = new MenuBar();
        menuBar.getMenus().add(layersMenu);
        return menuBar;
    }
}
