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

import java.util.List;

public class MapView extends StackPane {

    private static final int TILE_SIZE = 256;

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

        // Clamp zoom to the new source's range
        if (zoom < source.getMinZoom()) {
            zoom = source.getMinZoom();
            centerX = TileMath.lonToTileX(TileMath.tileXToLon(centerX, zoom), zoom);
            centerY = TileMath.latToTileY(TileMath.tileYToLat(centerY, zoom), zoom);
        } else if (zoom > source.getMaxZoom()) {
            // Preserve geographic position when clamping zoom down
            double lon = TileMath.tileXToLon(centerX, zoom);
            double lat = TileMath.tileYToLat(centerY, zoom);
            zoom = source.getMaxZoom();
            centerX = TileMath.lonToTileX(lon, zoom);
            centerY = TileMath.latToTileY(lat, zoom);
        }

        clampCenter();
        render();
        notifyStateUpdate();
    }

    private void setupInputHandlers() {
        canvas.setOnMousePressed(e -> {
            dragStartX = e.getX();
            dragStartY = e.getY();
            dragStartCenterX = centerX;
            dragStartCenterY = centerY;
        });

        canvas.setOnMouseDragged(e -> {
            double dx = e.getX() - dragStartX;
            double dy = e.getY() - dragStartY;
            centerX = dragStartCenterX - dx / TILE_SIZE;
            centerY = dragStartCenterY - dy / TILE_SIZE;
            clampCenter();
            render();
            notifyStateUpdate();
        });

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
            if (e.getDeltaY() > 0 && zoom < tileSource.getMaxZoom()) {
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

    public int getZoom() {
        return zoom;
    }

    public double[] getCenterLatLon() {
        double lon = TileMath.tileXToLon(centerX, zoom);
        double lat = TileMath.tileYToLat(centerY, zoom);
        return new double[]{lat, lon};
    }
}
