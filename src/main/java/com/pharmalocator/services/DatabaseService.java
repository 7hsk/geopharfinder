package com.pharmalocator.services;

import com.pharmalocator.config.AppConfig;
import com.pharmalocator.models.Pharmacy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for SQLite database operations:
 * - Favorites
 * - Cached pharmacies
 * - Search history
 */
public class DatabaseService {

    private static final Logger logger =
            LoggerFactory.getLogger(DatabaseService.class);

    private static DatabaseService instance;

    private final AppConfig config;
    private Connection connection;

    public DatabaseService() {
        this.config = AppConfig.getInstance();
        initializeDatabase();
    }

    public static synchronized DatabaseService getInstance() {
        if (instance == null) {
            instance = new DatabaseService();
        }
        return instance;
    }

    /* =========================
       INITIALIZATION
       ========================= */

    private void initializeDatabase() {
        try {
            String dbPath = config.getDatabasePath();
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            createTables();
            logger.info("Database initialized at {}", dbPath);
        } catch (SQLException e) {
            logger.error("Database initialization failed", e);
        }
    }

    private void createTables() throws SQLException {
        String[] queries = {

                """
                CREATE TABLE IF NOT EXISTS favorites (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    address TEXT,
                    phone TEXT,
                    opening_hours TEXT,
                    date_added TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """,

                """
                CREATE TABLE IF NOT EXISTS pharmacy_cache (
                    id TEXT PRIMARY KEY,
                    name TEXT NOT NULL,
                    latitude REAL NOT NULL,
                    longitude REAL NOT NULL,
                    address TEXT,
                    phone TEXT,
                    opening_hours TEXT,
                    search_lat REAL NOT NULL,
                    search_lon REAL NOT NULL,
                    search_radius INTEGER NOT NULL,
                    cached_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """,

                """
                CREATE TABLE IF NOT EXISTS search_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    search_query TEXT NOT NULL,
                    latitude REAL,
                    longitude REAL,
                    results_count INTEGER,
                    searched_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """
        };

        try (Statement stmt = connection.createStatement()) {
            for (String q : queries) {
                stmt.execute(q);
            }
        }
    }

    /* =========================
       FAVORITES
       ========================= */

    public void addFavorite(Pharmacy pharmacy) {
        String sql = """
                INSERT OR REPLACE INTO favorites
                (id, name, latitude, longitude, address, phone, opening_hours)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            fillPharmacyStatement(ps, pharmacy);
            ps.executeUpdate();
            logger.info("Favorite added: {}", pharmacy.getName());
        } catch (SQLException e) {
            logger.error("Failed to add favorite", e);
        }
    }

    public void removeFavorite(String pharmacyId) {
        String sql = "DELETE FROM favorites WHERE id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, pharmacyId);
            ps.executeUpdate();
            logger.info("Favorite removed: {}", pharmacyId);
        } catch (SQLException e) {
            logger.error("Failed to remove favorite", e);
        }
    }

    public boolean isFavorite(String pharmacyId) {
        String sql = "SELECT 1 FROM favorites WHERE id = ?";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, pharmacyId);
            ResultSet rs = ps.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            logger.error("Favorite check failed", e);
            return false;
        }
    }

    public List<Pharmacy> getFavorites() {
        List<Pharmacy> favorites = new ArrayList<>();
        String sql = "SELECT * FROM favorites ORDER BY date_added DESC";

        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                favorites.add(mapPharmacy(rs));
            }

        } catch (SQLException e) {
            logger.error("Failed to load favorites", e);
        }

        return favorites;
    }

    /* =========================
       CACHE
       ========================= */

    public void cachePharmacies(
            List<Pharmacy> pharmacies,
            double searchLat,
            double searchLon,
            int radius) {

        if (!config.isCacheEnabled() || pharmacies.isEmpty()) {
            return;
        }

        String sql = """
                INSERT OR REPLACE INTO pharmacy_cache
                (id, name, latitude, longitude, address, phone,
                 opening_hours, search_lat, search_lon, search_radius)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {

            for (Pharmacy p : pharmacies) {
                ps.setString(1, p.getId());
                ps.setString(2, p.getName());
                ps.setDouble(3, p.getLatitude());
                ps.setDouble(4, p.getLongitude());
                ps.setString(5, p.getAddress());
                ps.setString(6, p.getPhone());
                ps.setString(7, p.getOpeningHours());
                ps.setDouble(8, searchLat);
                ps.setDouble(9, searchLon);
                ps.setInt(10, radius);
                ps.executeUpdate();
            }

            logger.info("Cached {} pharmacies", pharmacies.size());

        } catch (SQLException e) {
            logger.error("Caching failed", e);
        }
    }

    public List<Pharmacy> getCachedPharmacies(
            double searchLat,
            double searchLon,
            int radius) {

        if (!config.isCacheEnabled()) {
            return new ArrayList<>();
        }

        List<Pharmacy> pharmacies = new ArrayList<>();

        String sql = """
                SELECT * FROM pharmacy_cache
                WHERE ABS(search_lat - ?) < 0.01
                  AND ABS(search_lon - ?) < 0.01
                  AND ABS(search_radius - ?) < 2000
                  AND datetime(cached_at) >
                      datetime('now', '-' || ? || ' hours')
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setDouble(1, searchLat);
            ps.setDouble(2, searchLon);
            ps.setInt(3, radius);
            ps.setInt(4, config.getCacheExpiryHours());

            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                Pharmacy pharmacy = mapPharmacy(rs);
                pharmacy.calculateDistanceFrom(searchLat, searchLon);
                pharmacies.add(pharmacy);
            }

            if (!pharmacies.isEmpty()) {
                logger.info("Cache hit: {} pharmacies", pharmacies.size());
            }

        } catch (SQLException e) {
            logger.error("Cache retrieval failed", e);
        }

        return pharmacies;
    }

    public void clearExpiredCache() {
        String sql = """
                DELETE FROM pharmacy_cache
                WHERE datetime(cached_at) <
                      datetime('now', '-' || ? || ' hours')
                """;

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, config.getCacheExpiryHours());
            int deleted = ps.executeUpdate();
            logger.info("Expired cache entries removed: {}", deleted);
        } catch (SQLException e) {
            logger.error("Cache cleanup failed", e);
        }
    }

    /* =========================
       HELPERS
       ========================= */

    private Pharmacy mapPharmacy(ResultSet rs) throws SQLException {
        Pharmacy pharmacy = new Pharmacy(
                rs.getString("id"),
                rs.getString("name"),
                rs.getDouble("latitude"),
                rs.getDouble("longitude")
        );
        pharmacy.setAddress(rs.getString("address"));
        pharmacy.setPhone(rs.getString("phone"));
        pharmacy.setOpeningHours(rs.getString("opening_hours"));
        return pharmacy;
    }

    private void fillPharmacyStatement(
            PreparedStatement ps,
            Pharmacy pharmacy) throws SQLException {

        ps.setString(1, pharmacy.getId());
        ps.setString(2, pharmacy.getName());
        ps.setDouble(3, pharmacy.getLatitude());
        ps.setDouble(4, pharmacy.getLongitude());
        ps.setString(5, pharmacy.getAddress());
        ps.setString(6, pharmacy.getPhone());
        ps.setString(7, pharmacy.getOpeningHours());
    }

    /* =========================
       SHUTDOWN
       ========================= */

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("Database connection closed");
            }
        } catch (SQLException e) {
            logger.error("Error closing database", e);
        }
    }
}
