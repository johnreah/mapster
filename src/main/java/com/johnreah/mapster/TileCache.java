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
        synchronized (cache) {
            Image img = cache.get(key);
            if (img != null) return img;
        }
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
