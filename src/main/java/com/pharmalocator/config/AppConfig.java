package com.pharmalocator.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Singleton configuration manager for the application.
 * Loads properties from application.properties and supports environment variable overrides.
 */
public class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private static AppConfig instance;
    private final Properties properties;

    private AppConfig() {
        properties = new Properties();
        loadProperties();
    }

    public static synchronized AppConfig getInstance() {
        if (instance == null) {
            instance = new AppConfig();
        }
        return instance;
    }

    private void loadProperties() {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("application.properties")) {
            if (input == null) {
                logger.warn("application.properties not found, using defaults");
                setDefaults();
                return;
            }
            properties.load(input);
            logger.info("Configuration loaded successfully");
        } catch (IOException e) {
            logger.error("Error loading configuration", e);
            setDefaults();
        }
    }

    private void setDefaults() {
        properties.setProperty("app.name", "GeoPharFinder");
        properties.setProperty("app.version", "1.0.0");
        properties.setProperty("app.window.width", "1400");
        properties.setProperty("app.window.height", "900");
        properties.setProperty("search.default.radius", "5000");
        properties.setProperty("map.default.zoom", "13");
        properties.setProperty("db.path", "geopharfinder.db");
    }

    public String getProperty(String key, String defaultValue) {
        // Check environment variable first (with dots replaced by underscores)
        String envKey = key.toUpperCase().replace('.', '_');
        String envValue = System.getenv(envKey);
        if (envValue != null) {
            return envValue;
        }
        return properties.getProperty(key, defaultValue);
    }

    public String getProperty(String key) {
        return getProperty(key, null);
    }

    public int getIntProperty(String key, int defaultValue) {
        try {
            String value = getProperty(key);
            return value != null ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            logger.warn("Invalid integer value for {}, using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    public double getDoubleProperty(String key, double defaultValue) {
        try {
            String value = getProperty(key);
            return value != null ? Double.parseDouble(value) : defaultValue;
        } catch (NumberFormatException e) {
            logger.warn("Invalid double value for {}, using default: {}", key, defaultValue);
            return defaultValue;
        }
    }

    public boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = getProperty(key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    // Convenience methods for common properties
    public String getAppName() {
        return getProperty("app.name", "GeoPharFinder");
    }

    public String getAppVersion() {
        return getProperty("app.version", "1.0.0");
    }

    public int getWindowWidth() {
        return getIntProperty("app.window.width", 1400);
    }

    public int getWindowHeight() {
        return getIntProperty("app.window.height", 900);
    }

    public String getOverpassUrl() {
        return getProperty("api.overpass.url", "https://overpass-api.de/api/interpreter");
    }

    public String getNominatimUrl() {
        return getProperty("api.nominatim.url", "https://nominatim.openstreetmap.org");
    }

    public String getIpApiUrl() {
        return getProperty("api.ipapi.url", "https://ipapi.co/json/");
    }

    public int getDefaultSearchRadius() {
        return getIntProperty("search.default.radius", 5000);
    }

    public int getMaxSearchRadius() {
        return getIntProperty("search.max.radius", 20000);
    }

    public int getDefaultMapZoom() {
        return getIntProperty("map.default.zoom", 13);
    }

    public int getMaxMarkers() {
        return getIntProperty("map.max.markers", 100);
    }

    public String getDatabasePath() {
        return getProperty("db.path", "geopharfinder.db");
    }

    public boolean isCacheEnabled() {
        return getBooleanProperty("db.cache.enabled", true);
    }

    public int getCacheExpiryHours() {
        return getIntProperty("db.cache.expiry.hours", 24);
    }

    public double getDefaultLatitude() {
        // Default to Casablanca, Morocco (not Paris!)
        return getDoubleProperty("location.default.latitude", 33.5731);
    }

    public double getDefaultLongitude() {
        // Default to Casablanca, Morocco (not Paris!)
        return getDoubleProperty("location.default.longitude", -7.5898);
    }

    public String getUserAgent() {
        return getProperty("http.user.agent", "GeoPharFinder/1.0.0");
    }

    public String getMapTileUrl() {
        return getProperty("map.tile.url", "https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png");
    }

    public String getMapAttribution() {
        return getProperty("map.tile.attribution", 
            "&copy; <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> contributors");
    }
}

