package com.johnreah.mapster.view.maptiles;

import com.johnreah.mapster.util.TileMath;
import com.johnreah.mapster.util.TileSource;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TileCache {

    private static final int MAX_ENTRIES = 512;
    private static final long TILE_MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000;
    private static final Path DISK_CACHE_DIR = Path.of(System.getProperty("user.home"), ".mapster", "tiles");

    private final Map<String, Image> cache;
    private final Set<String> inflight = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final HttpClient httpClient;
    private final Runnable onTileLoaded;
    private volatile TileSource tileSource;

    public TileCache(TileSource tileSource, Runnable onTileLoaded) {
        this.tileSource = tileSource;
        this.onTileLoaded = onTileLoaded;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.cache = new LinkedHashMap<>(MAX_ENTRIES, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
                return size() > MAX_ENTRIES;
            }
        };
    }

    public TileSource getTileSource() {
        return tileSource;
    }

    public void setTileSource(TileSource tileSource) {
        this.tileSource = tileSource;
        inflight.clear();
        synchronized (cache) {
            cache.clear();
        }
    }

    public Image getTile(int zoom, int x, int y) {
        TileSource source = this.tileSource;
        String key = source.getId() + "/" + zoom + "/" + x + "/" + y;

        // Check cache first
        synchronized (cache) {
            Image img = cache.get(key);
            if (img != null) return img;
        }

        // If zoom exceeds source's max zoom, scale from the highest available zoom
        if (zoom > source.getMaxZoom()) {
            Image scaledTile = getScaledTile(source, zoom, x, y);
            if (scaledTile != null) {
                synchronized (cache) {
                    cache.put(key, scaledTile);
                }
                return scaledTile;
            }
            // Try to fetch the lower zoom tile if not in cache
            int effectiveZoom = source.getMaxZoom();
            int zoomDiff = zoom - effectiveZoom;
            int divisor = 1 << zoomDiff;
            int effectiveX = x / divisor;
            int effectiveY = y / divisor;
            String effectiveKey = source.getId() + "/" + effectiveZoom + "/" + effectiveX + "/" + effectiveY;
            if (inflight.add(effectiveKey)) {
                executor.submit(() -> loadTile(source, effectiveKey, effectiveZoom, effectiveX, effectiveY));
            }
            return null;
        }

        // Normal flow: load from disk or network on background thread
        if (inflight.add(key)) {
            executor.submit(() -> loadTile(source, key, zoom, x, y));
        }
        return null;
    }

    private Image getScaledTile(TileSource source, int requestedZoom, int x, int y) {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("getScaledTile() must be called on the JavaFX Application Thread");
        }
        int effectiveZoom = source.getMaxZoom();
        int zoomDiff = requestedZoom - effectiveZoom;
        int divisor = 1 << zoomDiff;

        // Find parent tile coordinates at effective zoom
        int parentX = x / divisor;
        int parentY = y / divisor;

        // Check if parent tile is in cache
        String parentKey = source.getId() + "/" + effectiveZoom + "/" + parentX + "/" + parentY;
        Image parentTile;
        synchronized (cache) {
            parentTile = cache.get(parentKey);
        }

        if (parentTile == null) {
            return null;
        }

        // Calculate which sub-region of the parent tile to extract and scale up
        int subX = x % divisor;
        int subY = y % divisor;
        int tileSize = TileMath.TILE_SIZE;

        // Scale the sub-region to a full tile via pixel manipulation.
        // NOTE: WritableImage is a JavaFX class. This method is only called from getTile(),
        // which is called from TileLayerView.render() on the JavaFX Application Thread.
        int[] pixels = new int[tileSize * tileSize];
        var reader = parentTile.getPixelReader();
        for (int dy = 0; dy < tileSize; dy++) {
            for (int dx = 0; dx < tileSize; dx++) {
                pixels[dy * tileSize + dx] = reader.getArgb(
                        (subX * tileSize + dx) / divisor,
                        (subY * tileSize + dy) / divisor);
            }
        }
        WritableImage scaled = new WritableImage(tileSize, tileSize);
        scaled.getPixelWriter().setPixels(0, 0, tileSize, tileSize,
                PixelFormat.getIntArgbInstance(), pixels, 0, tileSize);
        return scaled;
    }

    private void loadTile(TileSource source, String key, int zoom, int x, int y) {
        try {
            byte[] bytes = readBytesFromDisk(source, zoom, x, y);
            if (bytes == null) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(source.getTileUrl(zoom, x, y)))
                        .header("User-Agent", "Mapster/1.0")
                        .GET()
                        .build();
                HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() == 200) {
                    bytes = response.body();
                    writeToDisk(source, zoom, x, y, bytes);
                }
            }
            if (bytes != null) {
                final byte[] finalBytes = bytes;
                Platform.runLater(() -> {
                    Image img = new Image(new ByteArrayInputStream(finalBytes));
                    synchronized (cache) {
                        cache.put(key, img);
                    }
                    onTileLoaded.run();
                });
            }
        } catch (Exception e) {
            // Load failed — will be retried on next render
        } finally {
            inflight.remove(key);
        }
    }

    private byte[] readBytesFromDisk(TileSource source, int zoom, int x, int y) {
        Path file = DISK_CACHE_DIR.resolve(source.getId() + "/" + zoom + "/" + x + "/" + y + ".png");
        try {
            if (Files.exists(file)) {
                long age = System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis();
                if (age < TILE_MAX_AGE_MS) {
                    return Files.readAllBytes(file);
                }
            }
        } catch (IOException e) {
            // Disk read failed — fall through to network fetch
        }
        return null;
    }

    private void writeToDisk(TileSource source, int zoom, int x, int y, byte[] bytes) {
        Path file = DISK_CACHE_DIR.resolve(source.getId() + "/" + zoom + "/" + x + "/" + y + ".png");
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, bytes);
        } catch (IOException e) {
            // Disk write failed — tile still served from memory
        }
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
