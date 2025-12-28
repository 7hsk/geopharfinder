package com.pharmalocator.services;

import com.pharmalocator.models.Location;
import com.pharmalocator.models.Pharmacy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CacheService - Fast local caching for pharmacies and map data
 *
 * Features:
 * - Persistent file-based cache
 * - In-memory cache for ultra-fast access
 * - Automatic expiration (configurable)
 * - Thread-safe operations
 * - Compression support
 */
public class CacheService {

    private static final Logger logger = LoggerFactory.getLogger(CacheService.class);

    // Cache configuration
    private static final String CACHE_DIR = "cache";
    private static final String PHARMACY_CACHE_FILE = "pharmacies.cache";
    private static final String LOCATION_CACHE_FILE = "location.cache";
    private static final String MAP_STATE_CACHE_FILE = "map_state.cache";

    // Cache expiration (default: 24 hours)
    private static final Duration CACHE_EXPIRATION = Duration.ofHours(24);

    // In-memory cache for fast access
    private final Map<String, CacheEntry<List<Pharmacy>>> pharmacyCache = new ConcurrentHashMap<>();
    private final Map<String, CacheEntry<Location>> locationCache = new ConcurrentHashMap<>();
    private CacheEntry<MapState> mapStateCache;

    // Cache directory path
    private final Path cacheDirectory;

    /**
     * Cache entry with timestamp for expiration
     */
    private static class CacheEntry<T> implements Serializable {
        private static final long serialVersionUID = 1L;

        private final T data;
        private final Instant timestamp;

        public CacheEntry(T data) {
            this.data = data;
            this.timestamp = Instant.now();
        }

        public T getData() {
            return data;
        }

        public boolean isExpired(Duration maxAge) {
            return Duration.between(timestamp, Instant.now()).compareTo(maxAge) > 0;
        }

        public Instant getTimestamp() {
            return timestamp;
        }
    }

    /**
     * Map state for caching map view
     */
    public static class MapState implements Serializable {
        private static final long serialVersionUID = 1L;

        private double latitude;
        private double longitude;
        private int zoomLevel;
        private String lastSearchQuery;

        public MapState(double latitude, double longitude, int zoomLevel, String lastSearchQuery) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.zoomLevel = zoomLevel;
            this.lastSearchQuery = lastSearchQuery;
        }

        // Getters
        public double getLatitude() { return latitude; }
        public double getLongitude() { return longitude; }
        public int getZoomLevel() { return zoomLevel; }
        public String getLastSearchQuery() { return lastSearchQuery; }
    }

    /**
     * Constructor - Initialize cache directory
     */
    public CacheService() {
        this.cacheDirectory = Paths.get(CACHE_DIR);
        initializeCacheDirectory();
        loadCachesFromDisk();
    }

    /**
     * Initialize cache directory
     */
    private void initializeCacheDirectory() {
        try {
            if (!Files.exists(cacheDirectory)) {
                Files.createDirectories(cacheDirectory);
                logger.info("Cache directory created: {}", cacheDirectory.toAbsolutePath());
            }
        } catch (IOException e) {
            logger.error("Failed to create cache directory", e);
        }
    }

    /**
     * Load all caches from disk on startup
     */
    private void loadCachesFromDisk() {
        logger.info("Loading caches from disk...");

        // Load pharmacy cache
        loadPharmacyCacheFromDisk();

        // Load location cache
        loadLocationCacheFromDisk();

        // Load map state cache
        loadMapStateCacheFromDisk();

        logger.info("Cache loading complete");
    }

    // ========================
    // PHARMACY CACHE
    // ========================

    /**
     * Cache pharmacies for a specific location
     */
    public void cachePharmacies(double lat, double lon, List<Pharmacy> pharmacies) {
        String key = generateLocationKey(lat, lon);
        CacheEntry<List<Pharmacy>> entry = new CacheEntry<>(new ArrayList<>(pharmacies));

        // Store in memory
        pharmacyCache.put(key, entry);

        // Persist to disk
        savePharmacyCacheToDisk();

        logger.info("Cached {} pharmacies for location: {}", pharmacies.size(), key);
    }

    /**
     * Get cached pharmacies for a location
     */
    public List<Pharmacy> getCachedPharmacies(double lat, double lon) {
        String key = generateLocationKey(lat, lon);
        CacheEntry<List<Pharmacy>> entry = pharmacyCache.get(key);

        if (entry == null) {
            logger.debug("No cache found for location: {}", key);
            return null;
        }

        if (entry.isExpired(CACHE_EXPIRATION)) {
            logger.info("Cache expired for location: {}", key);
            pharmacyCache.remove(key);
            return null;
        }

        logger.info("Cache HIT! Retrieved {} pharmacies for location: {}",
                   entry.getData().size(), key);
        return new ArrayList<>(entry.getData());
    }

    /**
     * Save pharmacy cache to disk
     */
    private void savePharmacyCacheToDisk() {
        Path filePath = cacheDirectory.resolve(PHARMACY_CACHE_FILE);
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(Files.newOutputStream(filePath)))) {
            oos.writeObject(pharmacyCache);
            logger.debug("Pharmacy cache saved to disk");
        } catch (IOException e) {
            logger.error("Failed to save pharmacy cache to disk", e);
        }
    }

    /**
     * Load pharmacy cache from disk
     */
    @SuppressWarnings("unchecked")
    private void loadPharmacyCacheFromDisk() {
        Path filePath = cacheDirectory.resolve(PHARMACY_CACHE_FILE);
        if (!Files.exists(filePath)) {
            logger.debug("No pharmacy cache file found");
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(filePath)))) {
            Map<String, CacheEntry<List<Pharmacy>>> loadedCache =
                (Map<String, CacheEntry<List<Pharmacy>>>) ois.readObject();

            // Remove expired entries
            loadedCache.entrySet().removeIf(entry ->
                entry.getValue().isExpired(CACHE_EXPIRATION));

            pharmacyCache.putAll(loadedCache);
            logger.info("Loaded {} pharmacy cache entries from disk", pharmacyCache.size());
        } catch (IOException | ClassNotFoundException e) {
            logger.error("Failed to load pharmacy cache from disk", e);
        }
    }

    // ========================
    // LOCATION CACHE
    // ========================

    /**
     * Cache user location
     */
    public void cacheUserLocation(Location location) {
        CacheEntry<Location> entry = new CacheEntry<>(location);
        locationCache.put("user_location", entry);
        saveLocationCacheToDisk();
        logger.info("Cached user location: {}, {}", location.getLatitude(), location.getLongitude());
    }

    /**
     * Get cached user location
     */
    public Location getCachedUserLocation() {
        CacheEntry<Location> entry = locationCache.get("user_location");

        if (entry == null) {
            logger.debug("No cached user location found");
            return null;
        }

        if (entry.isExpired(CACHE_EXPIRATION)) {
            logger.info("User location cache expired");
            locationCache.remove("user_location");
            return null;
        }

        logger.info("Cache HIT! Retrieved user location");
        return entry.getData();
    }

    /**
     * Save location cache to disk
     */
    private void saveLocationCacheToDisk() {
        Path filePath = cacheDirectory.resolve(LOCATION_CACHE_FILE);
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(Files.newOutputStream(filePath)))) {
            oos.writeObject(locationCache);
            logger.debug("Location cache saved to disk");
        } catch (IOException e) {
            logger.error("Failed to save location cache to disk", e);
        }
    }

    /**
     * Load location cache from disk
     */
    @SuppressWarnings("unchecked")
    private void loadLocationCacheFromDisk() {
        Path filePath = cacheDirectory.resolve(LOCATION_CACHE_FILE);
        if (!Files.exists(filePath)) {
            logger.debug("No location cache file found");
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(filePath)))) {
            Map<String, CacheEntry<Location>> loadedCache =
                (Map<String, CacheEntry<Location>>) ois.readObject();

            // Remove expired entries
            loadedCache.entrySet().removeIf(entry ->
                entry.getValue().isExpired(CACHE_EXPIRATION));

            locationCache.putAll(loadedCache);
            logger.info("Loaded {} location cache entries from disk", locationCache.size());
        } catch (IOException | ClassNotFoundException e) {
            logger.error("Failed to load location cache from disk", e);
        }
    }

    // ========================
    // MAP STATE CACHE
    // ========================

    /**
     * Cache map state (position, zoom, last search)
     */
    public void cacheMapState(double lat, double lon, int zoom, String lastSearch) {
        MapState state = new MapState(lat, lon, zoom, lastSearch);
        mapStateCache = new CacheEntry<>(state);
        saveMapStateCacheToDisk();
        logger.info("Cached map state: {}, {} @ zoom {}", lat, lon, zoom);
    }

    /**
     * Get cached map state
     */
    public MapState getCachedMapState() {
        if (mapStateCache == null) {
            logger.debug("No cached map state found");
            return null;
        }

        if (mapStateCache.isExpired(Duration.ofDays(7))) {
            logger.info("Map state cache expired");
            mapStateCache = null;
            return null;
        }

        logger.info("Cache HIT! Retrieved map state");
        return mapStateCache.getData();
    }

    /**
     * Save map state cache to disk
     */
    private void saveMapStateCacheToDisk() {
        Path filePath = cacheDirectory.resolve(MAP_STATE_CACHE_FILE);
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new BufferedOutputStream(Files.newOutputStream(filePath)))) {
            oos.writeObject(mapStateCache);
            logger.debug("Map state cache saved to disk");
        } catch (IOException e) {
            logger.error("Failed to save map state cache to disk", e);
        }
    }

    /**
     * Load map state cache from disk
     */
    @SuppressWarnings("unchecked")
    private void loadMapStateCacheFromDisk() {
        Path filePath = cacheDirectory.resolve(MAP_STATE_CACHE_FILE);
        if (!Files.exists(filePath)) {
            logger.debug("No map state cache file found");
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(
                new BufferedInputStream(Files.newInputStream(filePath)))) {
            mapStateCache = (CacheEntry<MapState>) ois.readObject();

            // Check expiration
            if (mapStateCache.isExpired(Duration.ofDays(7))) {
                logger.info("Map state cache expired on load");
                mapStateCache = null;
            } else {
                logger.info("Loaded map state cache from disk");
            }
        } catch (IOException | ClassNotFoundException e) {
            logger.error("Failed to load map state cache from disk", e);
        }
    }

    // ========================
    // UTILITY METHODS
    // ========================

    /**
     * Generate cache key for a location (rounded to ~100m precision)
     */
    private String generateLocationKey(double lat, double lon) {
        // Round to 3 decimal places (~100m precision)
        return String.format("%.3f,%.3f", lat, lon);
    }

    /**
     * Clear all caches
     */
    public void clearAllCaches() {
        pharmacyCache.clear();
        locationCache.clear();
        mapStateCache = null;

        // Delete cache files
        deleteAllCacheFiles();

        logger.info("All caches cleared");
    }

    /**
     * Clear expired caches only
     */
    public void clearExpiredCaches() {
        // Remove expired pharmacy caches
        pharmacyCache.entrySet().removeIf(entry ->
            entry.getValue().isExpired(CACHE_EXPIRATION));

        // Remove expired location caches
        locationCache.entrySet().removeIf(entry ->
            entry.getValue().isExpired(CACHE_EXPIRATION));

        // Check map state
        if (mapStateCache != null && mapStateCache.isExpired(Duration.ofDays(7))) {
            mapStateCache = null;
        }

        logger.info("Expired caches cleared");
    }

    /**
     * Delete all cache files from disk
     */
    private void deleteAllCacheFiles() {
        try {
            Files.deleteIfExists(cacheDirectory.resolve(PHARMACY_CACHE_FILE));
            Files.deleteIfExists(cacheDirectory.resolve(LOCATION_CACHE_FILE));
            Files.deleteIfExists(cacheDirectory.resolve(MAP_STATE_CACHE_FILE));
            logger.info("Cache files deleted");
        } catch (IOException e) {
            logger.error("Failed to delete cache files", e);
        }
    }

    /**
     * Get cache statistics
     */
    public CacheStats getStats() {
        int pharmacyCacheCount = pharmacyCache.size();
        int locationCacheCount = locationCache.size();
        boolean hasMapState = mapStateCache != null;

        return new CacheStats(pharmacyCacheCount, locationCacheCount, hasMapState);
    }

    /**
     * Cache statistics class
     */
    public static class CacheStats {
        private final int pharmacyCacheEntries;
        private final int locationCacheEntries;
        private final boolean hasMapState;

        public CacheStats(int pharmacyCacheEntries, int locationCacheEntries, boolean hasMapState) {
            this.pharmacyCacheEntries = pharmacyCacheEntries;
            this.locationCacheEntries = locationCacheEntries;
            this.hasMapState = hasMapState;
        }

        public int getPharmacyCacheEntries() { return pharmacyCacheEntries; }
        public int getLocationCacheEntries() { return locationCacheEntries; }
        public boolean hasMapState() { return hasMapState; }

        @Override
        public String toString() {
            return String.format("CacheStats{pharmacies=%d, locations=%d, mapState=%s}",
                               pharmacyCacheEntries, locationCacheEntries, hasMapState);
        }
    }
}

