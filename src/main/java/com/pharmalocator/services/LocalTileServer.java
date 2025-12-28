package com.pharmalocator.services;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * LocalTileServer - Serves cached map tiles from local storage
 *
 * This allows the map to work offline by serving tiles from cache
 * instead of downloading them from OpenStreetMap
 */
public class LocalTileServer {

    private static final Logger logger = LoggerFactory.getLogger(LocalTileServer.class);

    private static final int PORT = 8765; // Local server port
    private HttpServer server;
    private final OfflineTileCache tileCache;
    private boolean isRunning = false;

    public LocalTileServer(OfflineTileCache tileCache) {
        this.tileCache = tileCache;
    }

    /**
     * Start the local tile server
     */
    public void start() {
        if (isRunning) {
            logger.warn("Tile server already running");
            return;
        }

        try {
            server = HttpServer.create(new InetSocketAddress("localhost", PORT), 0);
            server.createContext("/tiles", new TileHandler());
            server.setExecutor(Executors.newFixedThreadPool(4)); // 4 threads for handling requests
            server.start();
            isRunning = true;
            logger.info("✅ Local tile server started on port {}", PORT);
        } catch (IOException e) {
            logger.error("Failed to start local tile server", e);
        }
    }

    /**
     * Stop the local tile server
     */
    public void stop() {
        if (server != null) {
            try {
                logger.info("Stopping local tile server...");
                server.stop(0); // Stop immediately without delay
                isRunning = false;
                
                // Shutdown the executor service to kill all threads
                if (server.getExecutor() instanceof java.util.concurrent.ExecutorService) {
                    ((java.util.concurrent.ExecutorService) server.getExecutor()).shutdownNow();
                }
                
                logger.info("Local tile server stopped");
            } catch (Exception e) {
                logger.error("Error stopping tile server", e);
            }
        }
    }

    /**
     * Check if server is running
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Get the tile server URL template
     */
    public String getTileUrl() {
        return "http://localhost:" + PORT + "/tiles/{z}/{x}/{y}.png";
    }

    /**
     * HTTP handler for tile requests
     */
    private class TileHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();

            // Parse tile coordinates from path: /tiles/z/x/y.png
            String[] parts = path.split("/");
            if (parts.length != 5 || !parts[4].endsWith(".png")) {
                sendNotFound(exchange);
                return;
            }

            try {
                int z = Integer.parseInt(parts[2]);
                int x = Integer.parseInt(parts[3]);
                int y = Integer.parseInt(parts[4].replace(".png", ""));

                // Get cached tile
                byte[] tileData = tileCache.getCachedTile(z, x, y);

                if (tileData != null) {
                    // Tile found in cache - serve it
                    exchange.getResponseHeaders().set("Content-Type", "image/png");
                    exchange.getResponseHeaders().set("Cache-Control", "max-age=86400"); // Cache for 1 day
                    exchange.sendResponseHeaders(200, tileData.length);

                    try (OutputStream os = exchange.getResponseBody()) {
                        os.write(tileData);
                    }
                } else {
                    // Tile not in cache - return 404
                    sendNotFound(exchange);
                }

            } catch (NumberFormatException e) {
                sendNotFound(exchange);
            }
        }

        private void sendNotFound(HttpExchange exchange) throws IOException {
            byte[] response = "Tile not found".getBytes();
            exchange.sendResponseHeaders(404, response.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(response);
            }
        }
    }
}

