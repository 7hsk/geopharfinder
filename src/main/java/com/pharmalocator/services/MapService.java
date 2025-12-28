package com.pharmalocator.services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.pharmalocator.models.Pharmacy;

import javafx.application.Platform;
import javafx.concurrent.Worker;
import javafx.scene.web.WebEngine;
import netscape.javascript.JSObject;

/**
 * Service responsible for communicating with Leaflet.js inside JavaFX WebView.
 *
 * Handles Java ↔ JavaScript synchronization safely.
 */
public class MapService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MapService.class);

    private WebEngine webEngine;
    private final Gson gson;

    private boolean mapInitialized = false;

    // Stored init params (used once WebView is ready)
    private double initLat;
    private double initLon;
    private int initZoom;

    public MapService() {
        this.gson = new GsonBuilder().disableHtmlEscaping().create();
    }

    /**
     * Sets WebEngine and attaches load listener.
     */
    public void setWebEngine(WebEngine webEngine) {
        this.webEngine = webEngine;
        LOGGER.info("WebEngine set for MapService");

        webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
            if (newState == Worker.State.SUCCEEDED) {
                LOGGER.info("Map HTML fully loaded");

                Platform.runLater(() -> {
                    try {
                        webEngine.executeScript(
                                String.format(
                                        "initMap(%.6f, %.6f, %d);",
                                        initLat, initLon, initZoom
                                )
                        );
                        mapInitialized = true;
                        LOGGER.info("Map initialized at ({}, {}) zoom {}",
                                initLat, initLon, initZoom);
                    } catch (Exception e) {
                        LOGGER.error("Failed to initialize map", e);
                    }
                });
            }
        });
    }

    /**
     * Requests map initialization.
     * Actual JS call happens only after WebView is ready.
     */
    public void initializeMap(double latitude, double longitude, int zoom) {
        this.initLat = latitude;
        this.initLon = longitude;
        this.initZoom = zoom;

        LOGGER.info("Map initialization requested ({}, {}) zoom {}",
                latitude, longitude, zoom);
    }

    /**
     * Sets user's location marker.
     */
    public void setUserLocation(double latitude, double longitude) {
        if (!isMapReady()) return;

        executeScript(String.format(
                "setUserLocation(%.6f, %.6f);",
                latitude, longitude
        ));
    }

    /**
     * Adds pharmacy markers to the map.
     */
    public void addPharmacyMarkers(List<Pharmacy> pharmacies) {
        if (!isMapReady()) {
            LOGGER.warn("Map not ready, cannot add pharmacy markers");
            return;
        }

        if (pharmacies == null || pharmacies.isEmpty()) {
            clearPharmacyMarkers();
            return;
        }

        List<Map<String, Object>> markerData = pharmacies.stream()
                .map(this::pharmacyToMap)
                .collect(Collectors.toList());

        executeScript("addPharmacyMarkers(" + gson.toJson(markerData) + ");");

        LOGGER.info("Added {} pharmacy markers", pharmacies.size());
    }

    /**
     * Clears pharmacy markers.
     */
    public void clearPharmacyMarkers() {
        if (!isMapReady()) return;
        executeScript("clearPharmacies();");
    }

    /**
     * Draws a route between two points.
     */
    public void drawRoute(double startLat, double startLon,
                          double endLat, double endLon) {
        if (!isMapReady()) return;

        executeScript(String.format(
                "drawRoute(%.6f, %.6f, %.6f, %.6f);",
                startLat, startLon, endLat, endLon
        ));
    }

    public void enablePickLocationMode() {
    if (webEngine == null) return;

    Platform.runLater(() ->
        webEngine.executeScript("enablePickLocationMode()")
    );
}

    public void disablePickLocationMode() {
        if (webEngine == null) return;

        Platform.runLater(() ->
            webEngine.executeScript("disablePickLocationMode()")
        );
    }

    public void setPharmacyMarkersOpacity(double opacity) {
        if (!isMapReady()) return;

        executeScript(String.format("setPharmacyMarkersOpacity(%.2f);", opacity));
    }


    /**
     * Clears the route.
     */
    public void clearRoute() {
        if (!isMapReady()) return;
        executeScript("clearRoute();");
    }

    /**
     * Centers the map.
     */
    public void centerMap(double latitude, double longitude, int zoom) {
        if (!isMapReady()) return;

        executeScript(String.format(
                "centerMap(%.6f, %.6f, %d);",
                latitude, longitude, zoom
        ));
    }

    /**
     * Fits bounds to markers.
     */
    public void fitBounds() {
        if (!isMapReady()) return;
        executeScript("fitBounds();");
    }

    /**
     * Refreshes map rendering.
     */
    public void refreshMap() {
        if (!isMapReady()) return;
        executeScript("refreshMap();");
    }

    /**
     * Selects a pharmacy marker by ID and highlights it
     */
    public void selectPharmacyMarker(String pharmacyId) {
        if (!isMapReady() || pharmacyId == null) return;
        executeScript(String.format("selectMarkerById('%s');", pharmacyId));
    }

    /**
     * Zoom in the map
     */
    public void zoomIn() {
        if (!isMapReady()) return;
        executeScript("map.zoomIn();");
    }

    /**
     * Zoom out the map
     */
    public void zoomOut() {
        if (!isMapReady()) return;
        executeScript("map.zoomOut();");
    }

    /**
     * Switch map to use online tiles (from OpenStreetMap)
     */
    public void useOnlineTiles() {
        if (!isMapReady()) return;
        
        String script = "if (window.switchToOnlineTiles) { window.switchToOnlineTiles(); }";
        executeScript(script);
        LOGGER.info("Switched to online tiles");
    }

    /**
     * Switch map to use offline cached tiles (from local server)
     */
    public void useOfflineTiles(String tileServerUrl) {
        if (!isMapReady()) return;
        
        String script = String.format(
            "if (window.switchToOfflineTiles) { window.switchToOfflineTiles('%s'); }",
            tileServerUrl
        );
        executeScript(script);
        LOGGER.info("Switched to offline tiles: {}", tileServerUrl);
    }

    /**
     * Returns true only when JS map is fully ready.
     */
    public boolean isMapReady() {
        return webEngine != null && mapInitialized;
    }

    /**
     * Executes JavaScript safely on JavaFX thread.
     */
    private void executeScript(String script) {
        Platform.runLater(() -> {
            try {
                webEngine.executeScript(script);
            } catch (Exception e) {
                LOGGER.error("JavaScript execution failed: {}", script, e);
            }
        });
    }

    /**
     * Converts Pharmacy into JS-friendly structure.
     */
    private Map<String, Object> pharmacyToMap(Pharmacy pharmacy) {
        Map<String, Object> map = new HashMap<>();

        map.put("id", pharmacy.getId());
        map.put("name", pharmacy.getName());
        map.put("latitude", pharmacy.getLatitude());
        map.put("longitude", pharmacy.getLongitude());
        map.put("address", safe(pharmacy.getAddress()));
        map.put("phone", safe(pharmacy.getPhone()));
        map.put("openingHours", safe(pharmacy.getOpeningHours()));
        map.put("distance", pharmacy.getFormattedDistance());

        return map;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public void registerJavaBridge(Object controller) {
    webEngine.getLoadWorker().stateProperty().addListener((obs, old, state) -> {
        if (state == javafx.concurrent.Worker.State.SUCCEEDED) {
            JSObject window = (JSObject) webEngine.executeScript("window");
            window.setMember("java", controller);
        }
    });


}
}
