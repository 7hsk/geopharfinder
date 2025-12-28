package com.pharmalocator.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pharmalocator.config.AppConfig;
import com.pharmalocator.models.Pharmacy;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service responsible for communicating with OpenStreetMap APIs.
 */
public class ApiService {

    private static final Logger logger = LoggerFactory.getLogger(ApiService.class);

    private final OkHttpClient httpClient;
    private final AppConfig config;

    public ApiService() {
        this.config = AppConfig.getInstance();
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS) // Reduced from 30s
                .readTimeout(15, TimeUnit.SECONDS)    // Reduced from 30s
                .build();
    }

    /* =========================
       PUBLIC API
       ========================= */

    public List<Pharmacy> getNearbyPharmacies(double latitude, double longitude) {
        return getNearbyPharmacies(latitude, longitude, config.getDefaultSearchRadius());
    }

    public List<Pharmacy> getNearbyPharmacies(double latitude, double longitude, int radius) {
        try {
            logger.info("Fetching pharmacies near ({}, {}) within {}m",
                    latitude, longitude, radius);

            String query = buildOverpassQuery(latitude, longitude, radius);
            String response = executeOverpassQuery(query);

            List<Pharmacy> pharmacies =
                    parsePharmacies(response, latitude, longitude);

            logger.info("Found {} pharmacies", pharmacies.size());
            return pharmacies;

        } catch (Exception e) {
            logger.error("Failed to fetch pharmacies", e);
            return new ArrayList<>();
        }
    }

    /**
     * Reverse geocoding using Nominatim.
     * ⚠️ Must NOT be called on JavaFX UI thread.
     */
    public String getAddressFromCoordinates(double latitude, double longitude) {
        try {
            String url = String.format(
                    "%s/reverse?format=json&lat=%.6f&lon=%.6f",
                    config.getNominatimUrl(), latitude, longitude
            );

            Request request = new Request.Builder()
                    .url(url)
                    .header("User-Agent", config.getUserAgent())
                    .build();

            // Respect Nominatim policy
            Thread.sleep(1000);

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    JsonObject json = JsonParser
                            .parseString(response.body().string())
                            .getAsJsonObject();

                    if (json.has("display_name")) {
                        return json.get("display_name").getAsString();
                    }
                }
            }

        } catch (Exception e) {
            logger.warn("Reverse geocoding failed: {}", e.getMessage());
        }

        return null;
    }

    /* =========================
       INTERNAL HELPERS
       ========================= */

    private String buildOverpassQuery(double lat, double lon, int radius) {
        return String.format(
                "[out:json][timeout:25];" +
                "(node[\"amenity\"=\"pharmacy\"](around:%d,%.6f,%.6f);" +
                "way[\"amenity\"=\"pharmacy\"](around:%d,%.6f,%.6f););" +
                "out body geom;>;out skel qt;",
                radius, lat, lon,
                radius, lat, lon
        );
    }

    private String executeOverpassQuery(String query) throws IOException {
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = config.getOverpassUrl() + "?data=" + encodedQuery;

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", config.getUserAgent())
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Overpass API error: " + response.code());
            }
            return response.body().string();
        }
    }

    private List<Pharmacy> parsePharmacies(
            String response, double userLat, double userLon) {

        List<Pharmacy> pharmacies = new ArrayList<>();

        try {
            JsonArray elements = JsonParser
                    .parseString(response)
                    .getAsJsonObject()
                    .getAsJsonArray("elements");

            for (JsonElement element : elements) {
                JsonObject obj = element.getAsJsonObject();

                if (!obj.has("type") || !obj.has("tags")) {
                    continue;
                }

                String type = obj.get("type").getAsString();
                
                // Handle both nodes (points) and ways (building shapes)
                if ("node".equals(type)) {
                    Pharmacy pharmacy = parsePharmacy(obj);
                    if (pharmacy != null) {
                        pharmacy.calculateDistanceFrom(userLat, userLon);
                        pharmacies.add(pharmacy);
                    }
                } else if ("way".equals(type)) {
                    Pharmacy pharmacy = parsePharmacyWay(obj);
                    if (pharmacy != null) {
                        pharmacy.calculateDistanceFrom(userLat, userLon);
                        pharmacies.add(pharmacy);
                    }
                }
            }

            pharmacies.sort(Comparator.comparingDouble(Pharmacy::getDistance));

            int max = config.getMaxMarkers();
            if (pharmacies.size() > max) {
                return new ArrayList<>(pharmacies.subList(0, max));
            }

        } catch (Exception e) {
            logger.error("Failed to parse pharmacies", e);
        }

        return pharmacies;
    }

    private Pharmacy parsePharmacy(JsonObject obj) {
        try {
            String id = obj.get("id").getAsString();
            double lat = obj.get("lat").getAsDouble();
            double lon = obj.get("lon").getAsDouble();

            JsonObject tags = obj.getAsJsonObject("tags");

            String name = tags.has("name")
                    ? tags.get("name").getAsString()
                    : "Pharmacie";

            Pharmacy pharmacy = new Pharmacy(id, name, lat, lon);

            String address = buildAddress(tags);
            if (address != null) {
                pharmacy.setAddress(address);
            }

            if (tags.has("phone")) {
                pharmacy.setPhone(tags.get("phone").getAsString());
            }

            if (tags.has("opening_hours")) {
                pharmacy.setOpeningHours(tags.get("opening_hours").getAsString());
            }

            for (String key : tags.keySet()) {
                pharmacy.addTag(key, tags.get(key).getAsString());
            }

            return pharmacy;

        } catch (Exception e) {
            logger.warn("Invalid pharmacy object: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Parse pharmacy from a way (building shape) with geometry
     */
    private Pharmacy parsePharmacyWay(JsonObject obj) {
        try {
            String id = obj.get("id").getAsString();
            JsonObject tags = obj.getAsJsonObject("tags");

            String name = tags.has("name")
                    ? tags.get("name").getAsString()
                    : "Pharmacie";

            // Get center point (average of all coordinates)
            double centerLat = 0;
            double centerLon = 0;

            if (obj.has("geometry")) {
                JsonArray geometry = obj.getAsJsonArray("geometry");
                if (geometry.size() > 0) {
                    for (JsonElement coord : geometry) {
                        JsonObject coordObj = coord.getAsJsonObject();
                        centerLat += coordObj.get("lat").getAsDouble();
                        centerLon += coordObj.get("lon").getAsDouble();
                    }
                    centerLat /= geometry.size();
                    centerLon /= geometry.size();

                    Pharmacy pharmacy = new Pharmacy(id, name, centerLat, centerLon);

                    // Store the building geometry
                    for (JsonElement coord : geometry) {
                        JsonObject coordObj = coord.getAsJsonObject();
                        pharmacy.addGeometryPoint(
                            coordObj.get("lat").getAsDouble(),
                            coordObj.get("lon").getAsDouble()
                        );
                    }

                    String address = buildAddress(tags);
                    if (address != null) {
                        pharmacy.setAddress(address);
                    }

                    if (tags.has("phone")) {
                        pharmacy.setPhone(tags.get("phone").getAsString());
                    }

                    if (tags.has("opening_hours")) {
                        pharmacy.setOpeningHours(tags.get("opening_hours").getAsString());
                    }

                    for (String key : tags.keySet()) {
                        pharmacy.addTag(key, tags.get(key).getAsString());
                    }

                    return pharmacy;
                }
            }

        } catch (Exception e) {
            logger.warn("Invalid way object: {}", e.getMessage());
        }

        return null;
    }

    private String buildAddress(JsonObject tags) {
        String number = tags.has("addr:housenumber")
                ? tags.get("addr:housenumber").getAsString()
                : "";
        String street = tags.has("addr:street")
                ? tags.get("addr:street").getAsString()
                : "";
        String city = tags.has("addr:city")
                ? tags.get("addr:city").getAsString()
                : "";

        String address = (number + " " + street).trim();
        if (!city.isEmpty()) {
            address += ", " + city;
        }

        return address.isEmpty() ? null : address;
    }
}
