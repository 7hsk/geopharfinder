package com.pharmalocator.services;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * OfflineTileCache - Downloads and caches OpenStreetMap tiles for offline use
 *
 * Features:
 * - Downloads map tiles in the background
 * - Stores tiles in local cache directory
 * - Serves tiles from cache when offline
 * - Pre-caches tiles around user's location
 */
public class OfflineTileCache {

    private static final Logger logger = LoggerFactory.getLogger(OfflineTileCache.class);

    private static final String CACHE_DIR = "cache/tiles";
    private static final String TILE_URL_TEMPLATE = "https://tile.openstreetmap.org/{z}/{x}/{y}.png";

    // Cache radius (download tiles within this radius of user location)
    // OPTIMIZED: Reduced zoom range to cache fewer tiles
    private static final int MIN_ZOOM = 12; // Increased from 10 (fewer tiles)
    private static final int MAX_ZOOM = 16; // Decreased from 17 (fewer tiles)

    private final Path cacheDirectory;
    private final OkHttpClient httpClient;
    private final ExecutorService downloadExecutor;
    private volatile boolean isDownloading = false;
    private final AtomicInteger downloadedTiles = new AtomicInteger(0);
    private final AtomicInteger totalTilesToDownload = new AtomicInteger(0);

    public OfflineTileCache() {
        this.cacheDirectory = Paths.get(CACHE_DIR);
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS) // Reduced from 10s
                .readTimeout(5, TimeUnit.SECONDS)    // Reduced from 10s
                .build();
        this.downloadExecutor = Executors.newFixedThreadPool(2); // Reduced from 4 to 2 threads for less resource usage

        initializeCacheDirectory();
    }

    /**
     * Initialize tile cache directory
     */
    private void initializeCacheDirectory() {
        try {
            if (!Files.exists(cacheDirectory)) {
                Files.createDirectories(cacheDirectory);
                logger.info("Tile cache directory created: {}", cacheDirectory.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("Failed to create tile cache directory", e);
        }
    }

    /**
     * Pre-cache tiles around a location for offline use
     * Downloads tiles at multiple zoom levels in the background
     */
    public void preCacheTilesAroundLocation(double latitude, double longitude) {
        if (isDownloading) {
            logger.info("Tile download already in progress, skipping");
            return;
        }

        logger.info("Starting tile pre-cache for location: {}, {}", latitude, longitude);
        isDownloading = true;
        downloadedTiles.set(0);

        CompletableFuture.runAsync(() -> {
            try {
                // Download tiles at different zoom levels
                for (int zoom = MIN_ZOOM; zoom <= MAX_ZOOM; zoom++) {
                    downloadTilesForZoom(latitude, longitude, zoom);
                }

                logger.info("✅ Tile pre-caching complete! Downloaded {} tiles", downloadedTiles.get());
            } catch (Exception e) {
                logger.error("Error during tile pre-caching", e);
            } finally {
                isDownloading = false;
            }
        }, downloadExecutor);
    }

    /**
     * Download tiles for a specific zoom level around a location
     */
    private void downloadTilesForZoom(double latitude, double longitude, int zoom) {
        // Convert lat/lon to tile coordinates
        int centerX = (int) Math.floor((longitude + 180.0) / 360.0 * (1 << zoom));
        int centerY = (int) Math.floor((1.0 - Math.log(Math.tan(Math.toRadians(latitude)) +
                      1.0 / Math.cos(Math.toRadians(latitude))) / Math.PI) / 2.0 * (1 << zoom));

        // Download tiles in a radius around center
        // Smaller radius for higher zoom levels (more detailed = fewer tiles needed)
        // OPTIMIZED: Further reduced radius for better performance
        int radius = Math.max(1, 6 - (zoom - MIN_ZOOM)); // Reduced from 8 to 6

        for (int x = centerX - radius; x <= centerX + radius; x++) {
            for (int y = centerY - radius; y <= centerY + radius; y++) {
                if (x >= 0 && y >= 0 && x < (1 << zoom) && y < (1 << zoom)) {
                    downloadTile(zoom, x, y);

                    // Add small delay to avoid overwhelming the server and reduce CPU usage
                    try {
                        Thread.sleep(100); // 100ms delay (increased from 50ms)
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }
    }

    /**
     * Download a single tile if not already cached
     */
    private void downloadTile(int z, int x, int y) {
        Path tilePath = getTilePath(z, x, y);

        // Skip if already cached
        if (Files.exists(tilePath)) {
            return;
        }

        try {
            // Create parent directories
            Files.createDirectories(tilePath.getParent());

            // Download tile
            String url = TILE_URL_TEMPLATE
                    .replace("{z}", String.valueOf(z))
                    .replace("{x}", String.valueOf(x))
                    .replace("{y}", String.valueOf(y));

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", "GeoPharFinder/1.0 (Offline Caching)")
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    // Save tile to cache
                    try (InputStream in = response.body().byteStream();
                         OutputStream out = Files.newOutputStream(tilePath)) {
                        byte[] buffer = new byte[8192];
                        int bytesRead;
                        while ((bytesRead = in.read(buffer)) != -1) {
                            out.write(buffer, 0, bytesRead);
                        }
                    }

                    int downloaded = downloadedTiles.incrementAndGet();
                    if (downloaded % 50 == 0) {
                        logger.info("Downloaded {} tiles so far...", downloaded);
                    }
                } else {
                    logger.warn("Failed to download tile {}/{}/{}: {}", z, x, y, response.code());
                }
            }
        } catch (IOException e) {
            logger.debug("Error downloading tile {}/{}/{}: {}", z, x, y, e.getMessage());
        }
    }

    /**
     * Get the file path for a tile
     */
    private Path getTilePath(int z, int x, int y) {
        return cacheDirectory.resolve(String.format("%d/%d/%d.png", z, x, y));
    }

    /**
     * Check if a tile is cached
     */
    public boolean isTileCached(int z, int x, int y) {
        return Files.exists(getTilePath(z, x, y));
    }

    /**
     * Get cached tile as byte array
     */
    public byte[] getCachedTile(int z, int x, int y) {
        Path tilePath = getTilePath(z, x, y);
        if (!Files.exists(tilePath)) {
            return null;
        }

        try {
            return Files.readAllBytes(tilePath);
        } catch (IOException e) {
            logger.error("Error reading cached tile {}/{}/{}", z, x, y, e);
            return null;
        }
    }

    /**
     * Get total number of cached tiles
     */
    public long getCachedTileCount() {
        try {
            return Files.walk(cacheDirectory)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".png"))
                    .count();
        } catch (IOException e) {
            logger.error("Error counting cached tiles", e);
            return 0;
        }
    }

    /**
     * Get total cache size in MB
     */
    public double getCacheSizeMB() {
        try {
            long bytes = Files.walk(cacheDirectory)
                    .filter(Files::isRegularFile)
                    .mapToLong(p -> {
                        try {
                            return Files.size(p);
                        } catch (IOException e) {
                            return 0;
                        }
                    })
                    .sum();
            return bytes / (1024.0 * 1024.0);
        } catch (IOException e) {
            logger.error("Error calculating cache size", e);
            return 0;
        }
    }

    /**
     * Clear all cached tiles
     */
    public void clearCache() {
        try {
            Files.walk(cacheDirectory)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            logger.error("Error deleting tile: {}", path, e);
                        }
                    });
            logger.info("Tile cache cleared");
        } catch (IOException e) {
            logger.error("Error clearing tile cache", e);
        }
    }

    /**
     * Shutdown the download executor
     */
    public void shutdown() {
        logger.info("Shutting down tile cache...");
        isDownloading = false;

        downloadExecutor.shutdownNow(); // Force immediate shutdown
        try {
            if (!downloadExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                logger.warn("Tile cache executor failed to terminate");
                downloadExecutor.shutdownNow(); // Force again
            }
        } catch (InterruptedException e) {
            downloadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("Tile cache shut down complete");
    }

    /**
     * Check if currently downloading tiles
     */
    public boolean isDownloading() {
        return isDownloading;
    }

    /**
     * Get download progress (0.0 to 1.0)
     */
    public double getDownloadProgress() {
        int total = totalTilesToDownload.get();
        if (total == 0) return 0.0;
        return (double) downloadedTiles.get() / total;
    }
}

