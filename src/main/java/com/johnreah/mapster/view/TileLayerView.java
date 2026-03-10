package com.johnreah.mapster.view;

import com.johnreah.mapster.view.maptiles.TileCache;
import com.johnreah.mapster.util.TileMath;
import com.johnreah.mapster.util.TileSource;
import com.johnreah.mapster.viewmodel.MapViewport;
import com.johnreah.mapster.viewmodel.TileLayerViewModel;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

/**
 * Renders a single tile-based map layer. Owns a {@link TileCache} and re-renders
 * whenever the shared viewport changes or tiles finish loading.
 */
public class TileLayerView extends Pane {

    private static final int TILE_SIZE = TileMath.TILE_SIZE;

    private final Canvas canvas = new Canvas();
    private final TileLayerViewModel layerViewModel;
    private final MapViewport viewport;
    private TileCache tileCache;

    public TileLayerView(TileLayerViewModel layerViewModel, MapViewport viewport) {
        this.layerViewModel = layerViewModel;
        this.viewport = viewport;

        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        canvas.widthProperty().addListener(e -> render());
        canvas.heightProperty().addListener(e -> render());
        getChildren().add(canvas);

        opacityProperty().bind(layerViewModel.opacityProperty());
        visibleProperty().bind(layerViewModel.visibleProperty());

        tileCache = new TileCache(layerViewModel.getTileSource(), this::render);

        viewport.centerXProperty().addListener((obs, old, val) -> render());
        viewport.centerYProperty().addListener((obs, old, val) -> render());
        viewport.zoomProperty().addListener((obs, old, val) -> render());

        layerViewModel.tileSourceProperty().addListener((obs, oldSource, newSource) -> {
            TileCache old = tileCache;
            tileCache = new TileCache(newSource, this::render);
            old.shutdown();
            render();
        });
    }

    private void render() {
        double w = canvas.getWidth();
        double h = canvas.getHeight();
        if (w <= 0 || h <= 0) return;

        double centerX = viewport.getCenterX();
        double centerY = viewport.getCenterY();
        int zoom = viewport.getZoom();

        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);

        double max = TileMath.maxTile(zoom);
        double offsetX = w / 2.0 - centerX * TILE_SIZE;
        double offsetY = h / 2.0 - centerY * TILE_SIZE;

        int tileLeft   = (int) Math.floor(-offsetX / TILE_SIZE);
        int tileRight  = (int) Math.floor((-offsetX + w) / TILE_SIZE);
        int tileTop    = (int) Math.floor(-offsetY / TILE_SIZE);
        int tileBottom = (int) Math.floor((-offsetY + h) / TILE_SIZE);

        for (int ty = tileTop; ty <= tileBottom; ty++) {
            if (ty < 0 || ty >= (int) max) continue;
            for (int tx = tileLeft; tx <= tileRight; tx++) {
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

    public TileSource getTileSource() {
        return layerViewModel.getTileSource();
    }

    public void shutdown() {
        tileCache.shutdown();
    }
}
