package com.pharmalocator.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.pharmalocator.models.Location;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Service responsible for reverse geocoding
 * (converting latitude/longitude into human-readable addresses).
 */
public class GeocodingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeocodingService.class);
    private static final String USER_AGENT = "GeoPharFinder/1.0";

    private final OkHttpClient httpClient;

    public GeocodingService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Returns a full address from geographic coordinates using
     * OpenStreetMap Nominatim API.
     *
     * @param latitude  latitude
     * @param longitude longitude
     * @return full address or null if not found
     */
    public String getAddressFromCoordinates(double latitude, double longitude) {

        String url = String.format(
                "https://nominatim.openstreetmap.org/reverse?format=json&lat=%.6f&lon=%.6f&zoom=18&addressdetails=1",
                latitude, longitude
        );

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {

            if (!response.isSuccessful() || response.body() == null) {
                LOGGER.warn("Geocoding failed with HTTP code {}", response.code());
                return null;
            }

            JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();

            // Preferred: full display name
            if (json.has("display_name")) {
                String address = json.get("display_name").getAsString();
                LOGGER.info("Reverse geocoding success: {}", address);
                return address;
            }

            // Fallback: manual address construction
            if (json.has("address")) {
                return buildAddressFromComponents(json.getAsJsonObject("address"));
            }

        } catch (IOException e) {
            LOGGER.error("I/O error during reverse geocoding", e);
        } catch (Exception e) {
            LOGGER.error("Unexpected error during reverse geocoding", e);
        }

        return null;
    }

    /**
     * Builds an address manually from Nominatim address components.
     */
    private String buildAddressFromComponents(JsonObject address) {
        StringBuilder result = new StringBuilder();

        if (address.has("house_number")) {
            result.append(address.get("house_number").getAsString()).append(" ");
        }

        if (address.has("road")) {
            result.append(address.get("road").getAsString());
        } else if (address.has("pedestrian")) {
            result.append(address.get("pedestrian").getAsString());
        }

        appendLocation(result, address, "city", "town", "village");
        appendLocation(result, address, "country");

        String finalAddress = result.toString().trim();
        return finalAddress.isEmpty() ? null : finalAddress;
    }

    /**
     * Utility method to append location fields safely.
     */
    private void appendLocation(StringBuilder builder, JsonObject address, String... keys) {
        for (String key : keys) {
            if (address.has(key)) {
                if (builder.length() > 0) builder.append(", ");
                builder.append(address.get(key).getAsString());
                return;
            }
        }
    }

    public Location geocode(String query) {

    String url = String.format(
            "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=%s",
            query.replace(" ", "%20")
    );

    Request request = new Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build();

    try (Response response = httpClient.newCall(request).execute()) {

        if (!response.isSuccessful() || response.body() == null) {
            LOGGER.warn("Geocoding failed with HTTP code {}", response.code());
            return null;
        }

        JsonArray results = JsonParser
                .parseString(response.body().string())
                .getAsJsonArray();

        if (results.isEmpty()) {
            LOGGER.info("No geocoding result for query: {}", query);
            return null;
        }

        JsonObject obj = results.get(0).getAsJsonObject();

        double lat = obj.get("lat").getAsDouble();
        double lon = obj.get("lon").getAsDouble();

        LOGGER.info("Geocoding success: {} -> ({}, {})", query, lat, lon);

        return new Location(lat, lon);

    } catch (IOException e) {
        LOGGER.error("I/O error during geocoding", e);
    } catch (Exception e) {
        LOGGER.error("Unexpected error during geocoding", e);
    }

    return null;
}

    /**
     * Returns a short address (city + country only).
     */
    public String getShortAddress(double latitude, double longitude) {

        String url = String.format(
                "https://nominatim.openstreetmap.org/reverse?format=json&lat=%.6f&lon=%.6f&zoom=10",
                latitude, longitude
        );

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {

            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }

            JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();

            if (!json.has("address")) return null;

            JsonObject address = json.getAsJsonObject("address");
            StringBuilder shortAddress = new StringBuilder();

            appendLocation(shortAddress, address, "city", "town", "village");
            appendLocation(shortAddress, address, "country");

            return shortAddress.toString();

        } catch (Exception e) {
            LOGGER.error("Error while getting short address", e);
            return null;
        }
    }
}
