package com.pharmalocator.services;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Service that detects user's location based on IP address.
 * Works without GPS and provides instant results.
 */
public class IpGeolocationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IpGeolocationService.class);
    private static final String USER_AGENT = "GeoPharFinder/1.0";

    private final OkHttpClient httpClient;

    public IpGeolocationService() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
    }

    /**
     * Attempts to detect location using multiple IP-based APIs.
     *
     * @return LocationResult or null if all services fail
     */
    public LocationResult detectLocationFromIP() {

        LOGGER.info("Attempting IP-based location detection...");

        LocationResult result;

        result = tryIpApiCom();
        if (result != null) return result;

        result = tryIpApiCo();
        if (result != null) return result;

        result = tryIpInfo();
        if (result != null) return result;

        LOGGER.warn("All IP geolocation providers failed");
        return null;
    }

    private LocationResult tryIpApiCom() {
        return executeRequest(
                "http://ip-api.com/json/?fields=status,country,city,lat,lon",
                json -> {
                    if (!"success".equals(json.get("status").getAsString())) return null;
                    return new LocationResult(
                            json.get("lat").getAsDouble(),
                            json.get("lon").getAsDouble(),
                            json.get("city").getAsString(),
                            json.get("country").getAsString(),
                            "ip-api.com"
                    );
                }
        );
    }

    private LocationResult tryIpApiCo() {
        return executeRequest(
                "https://ipapi.co/json/",
                json -> {
                    if (!json.has("latitude") || !json.has("longitude")) return null;
                    return new LocationResult(
                            json.get("latitude").getAsDouble(),
                            json.get("longitude").getAsDouble(),
                            json.get("city").getAsString(),
                            json.get("country_name").getAsString(),
                            "ipapi.co"
                    );
                }
        );
    }

    private LocationResult tryIpInfo() {
        return executeRequest(
                "https://ipinfo.io/json",
                json -> {
                    if (!json.has("loc")) return null;
                    String[] coords = json.get("loc").getAsString().split(",");
                    return new LocationResult(
                            Double.parseDouble(coords[0]),
                            Double.parseDouble(coords[1]),
                            json.get("city").getAsString(),
                            json.get("country").getAsString(),
                            "ipinfo.io"
                    );
                }
        );
    }

    /**
     * Generic helper to execute IP geolocation HTTP requests.
     */
    private LocationResult executeRequest(String url, LocationParser parser) {

        Request request = new Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {

            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }

            JsonObject json = JsonParser.parseString(response.body().string()).getAsJsonObject();
            LocationResult result = parser.parse(json);

            if (result != null) {
                LOGGER.info("Location detected: {}", result);
            }

            return result;

        } catch (Exception e) {
            LOGGER.warn("IP geolocation request failed: {}", e.getMessage());
            return null;
        }
    }

    @FunctionalInterface
    private interface LocationParser {
        LocationResult parse(JsonObject json);
    }

    /**
     * Immutable result object.
     */
    public static class LocationResult {

        public final double latitude;
        public final double longitude;
        public final String city;
        public final String country;
        public final String source;

        public LocationResult(double latitude, double longitude, String city, String country, String source) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.city = city;
            this.country = country;
            this.source = source;
        }

        public String getFullAddress() {
            return city + ", " + country;
        }

        @Override
        public String toString() {
            return String.format(
                    "%s, %s (%.4f, %.4f) via %s",
                    city, country, latitude, longitude, source
            );
        }
    }
}
