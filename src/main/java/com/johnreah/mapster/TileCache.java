package com.johnreah.mapster;

import javafx.application.Platform;
import javafx.scene.image.Image;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TileCache {

    private static final int MAX_ENTRIES = 512;
    private static final String TILE_URL = "https://tile.openstreetmap.org/%d/%d/%d.png";

    private final Map<String, Image> cache;
    private final Set<String> inflight = ConcurrentHashMap.newKeySet();
    private final ExecutorService executor = Executors.newFixedThreadPool(2);
    private final HttpClient httpClient;
    private final Runnable onTileLoaded;

    public TileCache(Runnable onTileLoaded) {
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

    public Image getTile(int zoom, int x, int y) {
        String key = zoom + "/" + x + "/" + y;
        synchronized (cache) {
            Image img = cache.get(key);
            if (img != null) return img;
        }
        if (inflight.add(key)) {
            executor.submit(() -> fetchTile(key, zoom, x, y));
        }
        return null;
    }

    private void fetchTile(String key, int zoom, int x, int y) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(String.format(TILE_URL, zoom, x, y)))
                    .header("User-Agent", "Mapster/1.0")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200) {
                Image img = new Image(new ByteArrayInputStream(response.body()));
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

    public void shutdown() {
        executor.shutdownNow();
    }
}
