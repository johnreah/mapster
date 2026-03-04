package com.johnreah.mapster.view;

import com.johnreah.mapster.view.maptiles.GoogleSatelliteTileSource;
import com.johnreah.mapster.view.maptiles.GoogleStreetMapsTileSource;
import com.johnreah.mapster.view.maptiles.OrdnanceSurveyTileSource;
import com.johnreah.mapster.view.maptiles.OsmTileSource;
import com.johnreah.mapster.view.maptiles.TileSource;

import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class MainWindow {

    private MapView mapView;
    private Label centerLatLabel;
    private Label centerLonLabel;
    private Label zoomLabel;

    public void show(Stage stage) {
        List<TileSource> sources = buildAvailableSources();
        TileSource defaultSource = sources.get(0);

        mapView = new MapView(defaultSource);
        mapView.setMinWidth(200);

        MenuBar menuBar = buildMenuBar(sources, defaultSource);
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

        mapView.setStateUpdateListener(this::updateState);
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

        OrdnanceSurveyTileSource osRoad = new OrdnanceSurveyTileSource(OrdnanceSurveyTileSource.ROAD_LAYER, OrdnanceSurveyTileSource.ROAD_DISPLAY_NAME);
        if (osRoad.isAvailable()) {
            sources.add(osRoad);
        }

        OrdnanceSurveyTileSource osOutdoor = new OrdnanceSurveyTileSource(OrdnanceSurveyTileSource.OUTDOOR_LAYER, OrdnanceSurveyTileSource.OUTDOOR_DISPLAY_NAME);
        if (osOutdoor.isAvailable()) {
            sources.add(osOutdoor);
        }

        return sources;
    }

    private VBox buildSidePanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setStyle("-fx-background-color: #f5f5f5;");
        panel.setMinWidth(150);
        panel.setMaxWidth(500);
        panel.setPrefWidth(200);

        Label titleLabel = new Label("Map Information");
        titleLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        centerLatLabel = new Label("Latitude: -");
        centerLonLabel = new Label("Longitude: -");
        zoomLabel = new Label("Zoom: -");

        panel.getChildren().addAll(titleLabel, new Separator(), centerLatLabel, centerLonLabel, zoomLabel);

        return panel;
    }

    private void updateState() {
        double[] center = mapView.getCenterLatLon();
        centerLatLabel.setText(String.format("Latitude: %.6f", center[0]));
        centerLonLabel.setText(String.format("Longitude: %.6f", center[1]));
        zoomLabel.setText("Zoom: " + mapView.getZoom());
    }

    private ToolBar buildToolBar() {
        Button zoomInButton = new Button("+");
        zoomInButton.setOnAction(e -> mapView.zoomIn());
        Button zoomOutButton = new Button("\u2013");
        zoomOutButton.setOnAction(e -> mapView.zoomOut());

        // Mode toggle buttons (radio-style)
        ToggleGroup modeGroup = new ToggleGroup();

        ToggleButton navigationModeButton = new ToggleButton("\u2630"); // ☰ navigation icon
        navigationModeButton.setToggleGroup(modeGroup);
        navigationModeButton.setSelected(true); // Default to navigation mode
        navigationModeButton.setOnAction(e -> mapView.setNavigationMode());

        ToggleButton drawingModeButton = new ToggleButton("\u270E"); // ✎ pencil icon
        drawingModeButton.setToggleGroup(modeGroup);
        drawingModeButton.setOnAction(e -> mapView.setDrawingMode());

        return new ToolBar(zoomInButton, zoomOutButton, new Separator(), navigationModeButton, drawingModeButton);
    }

    private MenuBar buildMenuBar(List<TileSource> sources, TileSource defaultSource) {
        Menu layersMenu = new Menu("Layers");
        ToggleGroup toggleGroup = new ToggleGroup();

        for (TileSource source : sources) {
            RadioMenuItem item = new RadioMenuItem(source.getDisplayName());
            item.setToggleGroup(toggleGroup);
            item.setSelected(source == defaultSource);
            item.setOnAction(e -> mapView.selectSource(source));
            layersMenu.getItems().add(item);
        }

        MenuBar menuBar = new MenuBar();
        menuBar.getMenus().add(layersMenu);
        return menuBar;
    }
}
