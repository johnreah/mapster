package com.johnreah.mapster;

import javafx.application.Platform;
import javafx.scene.image.Image;

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
                executor.submit(() -> fetchTile(source, effectiveKey, effectiveZoom, effectiveX, effectiveY));
            }
            return null;
        }

        // Normal flow for supported zoom levels
        Image diskImg = loadFromDisk(source, zoom, x, y);
        if (diskImg != null) {
            synchronized (cache) {
                cache.put(key, diskImg);
            }
            return diskImg;
        }
        if (inflight.add(key)) {
            executor.submit(() -> fetchTile(source, key, zoom, x, y));
        }
        return null;
    }

    private Image getScaledTile(TileSource source, int requestedZoom, int x, int y) {
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
            // Try loading from disk
            parentTile = loadFromDisk(source, effectiveZoom, parentX, parentY);
            if (parentTile != null) {
                synchronized (cache) {
                    cache.put(parentKey, parentTile);
                }
            }
        }

        if (parentTile == null) {
            return null;
        }

        // Calculate which quadrant of the parent tile to extract
        int subX = x % divisor;
        int subY = y % divisor;
        double tileSize = 256.0;
        double subTileSize = tileSize / divisor;

        // Create a scaled image from the appropriate region
        javafx.scene.image.WritableImage scaled = new javafx.scene.image.WritableImage(256, 256);
        javafx.scene.canvas.Canvas tempCanvas = new javafx.scene.canvas.Canvas(256, 256);
        javafx.scene.canvas.GraphicsContext gc = tempCanvas.getGraphicsContext2D();

        // Draw the sub-region scaled up to 256x256
        gc.drawImage(parentTile,
            subX * subTileSize, subY * subTileSize, subTileSize, subTileSize,
            0, 0, 256, 256);

        tempCanvas.snapshot(null, scaled);
        return scaled;
    }

    private void fetchTile(TileSource source, String key, int zoom, int x, int y) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(source.getTileUrl(zoom, x, y)))
                    .header("User-Agent", "Mapster/1.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                byte[] bytes = response.body();
                writeToDisk(source, zoom, x, y, bytes);
                Image img = new Image(new ByteArrayInputStream(bytes));
                synchronized (cache) {
                    cache.put(key, img);
                }
                Platform.runLater(onTileLoaded);
            }
        } catch (Exception e) {
            // Tile load failed — will be retried on next render
        } finally {
            inflight.remove(key);
        }
    }

    private Image loadFromDisk(TileSource source, int zoom, int x, int y) {
        Path file = DISK_CACHE_DIR.resolve(source.getId() + "/" + zoom + "/" + x + "/" + y + ".png");
        try {
            if (Files.exists(file)) {
                long age = System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis();
                if (age < TILE_MAX_AGE_MS) {
                    byte[] bytes = Files.readAllBytes(file);
                    return new Image(new ByteArrayInputStream(bytes));
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
