package com.pharmalocator.controllers;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pharmalocator.models.Location;
import com.pharmalocator.models.Pharmacy;
import com.pharmalocator.services.ApiService;
import com.pharmalocator.services.CacheService;
import com.pharmalocator.services.GeocodingService;
import com.pharmalocator.services.IpGeolocationService;
import com.pharmalocator.services.LocalTileServer;
import com.pharmalocator.services.LocationService;
import com.pharmalocator.services.MapService;
import com.pharmalocator.services.OfflineManager;
import com.pharmalocator.services.OfflineTileCache;

import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.util.Duration;

public class MainViewController {

    private static final Logger logger =
            LoggerFactory.getLogger(MainViewController.class);

    /* =========================
       FXML REFERENCES
       ========================= */

    @FXML private WebView mapWebView;

    @FXML private TextField searchField;
    @FXML private ListView<Pharmacy> pharmacyListView;
    @FXML private ComboBox<String> sortComboBox;
    @FXML private ComboBox<String> displayLimitComboBox;
    @FXML private Label countLabel;

    @FXML private VBox pharmacyInfoBox;
    @FXML private Label pharmacyNameLabel;
    @FXML private Label addressLabel;
    @FXML private Label phoneLabel;
    @FXML private Label hoursLabel;
    @FXML private Label distanceLabel;

    @FXML private VBox sidebar;
    @FXML private Button sidebarToggleButton;

    @FXML private Label statusLabel;
    @FXML private Label locationLabel;

    @FXML private Pane loadingBox;

    @FXML private Button pickLocationButton;
    @FXML private Button themeToggleButton;
    @FXML private Button maximizeButton;
    @FXML private HBox sidebarContainer;
    @FXML private ImageView logoImageView;

    

    /* =========================
       SERVICES
       ========================= */

    private final ApiService apiService = new ApiService();
    private final LocationService locationService = new LocationService();
    private final IpGeolocationService ipGeolocationService = new IpGeolocationService();
    private final GeocodingService geocodingService = new GeocodingService();
    private final MapService mapService = new MapService();
    private final CacheService cacheService = new CacheService();
    private final OfflineTileCache tileCache = new OfflineTileCache();
    private final LocalTileServer tileServer = new LocalTileServer(tileCache);
    private final OfflineManager offlineManager = new OfflineManager();

    private Location userLocation;
    private boolean sidebarOpen = true;
    private boolean pickLocationModeActive = false;
    private boolean isDarkMode = false; // Track current theme
    private int pharmacyDisplayLimit = 50; // User-configurable pharmacy display limit (default 50)
    private int pharmacyLoadRetryCount = 0; // Track retry attempts (0 = initial, 1 = first retry, 2 = second retry)
    private static final int MAX_RETRIES = 2; // Max 2 retries (total 3 attempts: initial + 2 retries)
    private java.util.concurrent.ScheduledFuture<?> currentRetryTask = null; // Track current retry task
    private java.util.concurrent.ScheduledExecutorService retryScheduler = 
        java.util.concurrent.Executors.newScheduledThreadPool(1); // For timeout/retry
    private volatile boolean isLoadingPharmacies = false; // Track if currently loading
    
    // Freeze detection and recovery (DISABLED for performance - rely on timeout instead)
    private volatile long lastActivityTimestamp = System.currentTimeMillis();
    private java.util.concurrent.ScheduledFuture<?> freezeWatchdog = null;
    private static final long FREEZE_THRESHOLD_MS = 5000; // 5 seconds (increased)

    /* =========================
       THREAD MANAGEMENT (OPTIMIZED - Single pool for all background tasks)
       ========================= */
    private final ExecutorService executorService = Executors.newFixedThreadPool(2); // Reduced from 3 to 2

    /* =========================
       AUTOCOMPLETE
       ========================= */

    private final ContextMenu suggestionsPopup = new ContextMenu();
    private final Set<String> searchHistory = new HashSet<>();

    /* =========================
       INITIALIZATION
       ========================= */

    @FXML
    public void initialize() {
        // Start local tile server for offline map support
        tileServer.start();

        // Setup offline manager and listeners
        setupOfflineManager();

        setupMap();
        setupUI();
        setupAutocomplete();
        setupListView();
        loadLogoImage();
        
        // Initialize button position (sidebar is open by default, so button starts at translateX=0)
        sidebarToggleButton.setTranslateX(0);

        // FREEZE WATCHDOG DISABLED FOR PERFORMANCE - Rely on timeout mechanism instead
        // startFreezeWatchdog();

        // Show UI immediately, load data in background for instant startup
        Platform.runLater(() -> {
            // Try to restore from cache first for instant display
            tryRestoreFromCache();
            
            // If no cache, show default location immediately
            if (userLocation == null) {
                Location def = locationService.getDefaultLocation();
                mapService.setUserLocation(def.getLatitude(), def.getLongitude());
                updateLocationLabel();
            }
            
            // Then detect real location in background (won't block UI)
            detectLocationOnStartup();
        });
    }
    
    private void startFreezeWatchdog() {
        // Monitor for UI freezes and auto-recover
        freezeWatchdog = retryScheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            long timeSinceActivity = now - lastActivityTimestamp;
            
            if (timeSinceActivity > FREEZE_THRESHOLD_MS && isLoadingPharmacies) {
                logger.warn("⚠️ Potential freeze detected! Auto-recovering...");
                
                Platform.runLater(() -> {
                    try {
                        // Force stop any stuck operations
                        isLoadingPharmacies = false;
                        setLoading(false);
                        
                        // Update activity timestamp
                        updateActivity();
                        
                        // Retry loading if needed
                        if (pharmacyListView.getItems().isEmpty() && userLocation != null) {
                            logger.info("🔄 Auto-recovery: Restarting pharmacy load...");
                            setStatus("🔄 Auto-recovering from freeze...");
                            loadNearbyPharmacies();
                        }
                    } catch (Exception e) {
                        logger.error("Error during freeze recovery", e);
                    }
                });
            }
        }, 2, 2, java.util.concurrent.TimeUnit.SECONDS); // Check every 2 seconds
    }
    
    /**
     * Setup offline manager and handle online/offline transitions
     */
    private void setupOfflineManager() {
        offlineManager.addListener(new OfflineManager.OfflineStateListener() {
            @Override
            public void onOnline() {
                Platform.runLater(() -> {
                    setStatus("🌐 Online - Full functionality available");
                    searchField.setDisable(false);
                    searchField.setPromptText("Search location...");

                    // Refresh pharmacies if we have a location
                    if (userLocation != null) {
                        loadNearbyPharmacies();
                    }

                    // Switch map back to online tiles if available
                    mapService.useOnlineTiles();
                });
            }

            @Override
            public void onOffline() {
                Platform.runLater(() -> {
                    setStatus("📵 Offline Mode - Showing cached data only");
                    searchField.setDisable(true);
                    searchField.setPromptText("Search disabled (offline)");

                    // Switch map to use cached tiles
                    mapService.useOfflineTiles(tileServer.getTileUrl());

                    // Load cached pharmacies for last known location
                    loadCachedPharmaciesOnly();
                });
            }
        });
    }

    /**
     * Load only cached pharmacies when offline
     */
    private void loadCachedPharmaciesOnly() {
        if (userLocation == null) {
            // Try to get cached location
            Location cachedLocation = cacheService.getCachedUserLocation();
            if (cachedLocation != null) {
                userLocation = cachedLocation;
                mapService.setUserLocation(cachedLocation.getLatitude(), cachedLocation.getLongitude());
                updateLocationLabel();
            } else {
                setStatus("📵 Offline - No cached location available");
                return;
            }
        }

        // Load cached pharmacies
        List<Pharmacy> cachedPharmacies = cacheService.getCachedPharmacies(
            userLocation.getLatitude(),
            userLocation.getLongitude()
        );

        if (cachedPharmacies != null && !cachedPharmacies.isEmpty()) {
            cachedPharmacies.forEach(p -> p.calculateDistanceFrom(userLocation));
            displayPharmacies(cachedPharmacies);
            setStatus("📵 Offline - Showing " + cachedPharmacies.size() + " cached pharmacies from last location");
            logger.info("Loaded {} cached pharmacies in offline mode", cachedPharmacies.size());
        } else {
            pharmacyListView.getItems().clear();
            mapService.clearPharmacyMarkers();
            setStatus("📵 Offline - No cached pharmacies available");
            pharmacyListView.setPlaceholder(
                new Label("📵 No cached data available for this location")
            );
        }
    }

    private void updateActivity() {
        lastActivityTimestamp = System.currentTimeMillis();
    }

    /* =========================
       MAP
       ========================= */

    private void setupMap() {
        WebEngine engine = mapWebView.getEngine();
        mapService.setWebEngine(engine);
        mapService.registerJavaBridge(this);

        // Load map immediately without waiting
        engine.load(getClass().getResource("/map.html").toExternalForm());

        engine.documentProperty().addListener((obs, o, n) -> {
            if (n != null) {
                // Map loaded - initialize with default location quickly
                Location def = locationService.getDefaultLocation();
                mapService.initializeMap(
                        def.getLatitude(),
                        def.getLongitude(),
                        13 // Slightly zoomed out for faster tile loading
                );
            }
        });
    }
    @FXML
private void handlePickLocation() {
    pickLocationModeActive = !pickLocationModeActive;

    if (pickLocationModeActive) {
        // Enable pick location mode
        setStatus("📍 Click anywhere on the map to set your location (pharmacies will update automatically)");
        mapService.enablePickLocationMode();
        mapService.setPharmacyMarkersOpacity(0.0); // Hide markers completely

        // Update button style to show it's active
        pickLocationButton.setStyle("-fx-background-color: #ff6f00; -fx-text-fill: white;");
    } else {
        // Disable pick location mode
        setStatus("Pick location mode disabled");
        mapService.disablePickLocationMode();
        mapService.setPharmacyMarkersOpacity(1.0); // Restore markers to full visibility

        // Reset button style
        pickLocationButton.setStyle("");
    }
}

public void onLocationPicked(double lat, double lon) {
    Platform.runLater(() -> {
        logger.info("Picked location: {}, {}", lat, lon);

        // Disable pick mode and restore marker visibility
        pickLocationModeActive = false;
        mapService.disablePickLocationMode();
        mapService.setPharmacyMarkersOpacity(1.0);
        pickLocationButton.setStyle(""); // Reset button style

        // Get address for the new location
        new Thread(() -> {
            String placeName = geocodingService.getShortAddress(lat, lon);
            String displayName = placeName != null
                ? placeName
                : String.format("Lat %.4f, Lon %.4f", lat, lon);

            Platform.runLater(() -> {
                // Update user location and automatically search nearby pharmacies
                setUserLocation(lat, lon);
                setStatus("Location updated: " + displayName + " - Loading nearby pharmacies...");

                // The setUserLocation() method already calls loadNearbyPharmacies()
                // which will automatically refresh the pharmacy list
            });
        }).start();
    });
}



    public void onPharmacyClicked(String pharmacyId) {
    Platform.runLater(() -> {
        Pharmacy pharmacy = pharmacyListView.getItems()
                .stream()
                .filter(p -> pharmacyId.equals(p.getId()))
                .findFirst()
                .orElse(null);

        if (pharmacy == null) return;

        // Select in list
        pharmacyListView.getSelectionModel().select(pharmacy);
        pharmacyListView.scrollTo(pharmacy);
        
        // Show details
        showPharmacyDetails(pharmacy);

        // Center map on pharmacy
        mapService.centerMap(
                pharmacy.getLatitude(),
                pharmacy.getLongitude(),
                17
        );
    });
}


    /* =========================
       UI SETUP
       ========================= */

    private void setupUI() {
        sortComboBox.getItems().addAll("Distance", "Name");
        sortComboBox.getSelectionModel().selectFirst();

        // Setup display limit dropdown
        displayLimitComboBox.getItems().addAll("10", "20", "50", "100");
        displayLimitComboBox.setValue("50"); // Default to 50

        pharmacyInfoBox.setVisible(false);
        setLoading(false); // Ensure loading indicator is hidden on startup

        pharmacyListView.setPlaceholder(
                new Label("🔍 Search for nearby pharmacies")
        );

        setStatus("🚀 Ready - Loading your location...");
        locationLabel.setText("📍 Detecting...");
    }

    @FXML
private void toggleSidebar() {

    double start = sidebarOpen ? 320 : 0;
    double end   = sidebarOpen ? 0 : 320;
    double startOpacity = sidebarOpen ? 1.0 : 0.0;
    double endOpacity = sidebarOpen ? 0.0 : 1.0;
    
    // Button position: when sidebar closes, move button to more visible left position
    // When open: translateX = 0 (button right next to sidebar)
    // When closed: translateX = -280 (button at x=40 from left edge - more visible!)
    double buttonStartX = sidebarOpen ? 0 : -280;
    double buttonEndX = sidebarOpen ? -280 : 0;

    Timeline timeline = new Timeline(
        new KeyFrame(Duration.ZERO,
            new KeyValue(sidebar.prefWidthProperty(), start),
            new KeyValue(sidebar.opacityProperty(), startOpacity),
            new KeyValue(sidebarToggleButton.translateXProperty(), buttonStartX)
        ),
        new KeyFrame(Duration.millis(350),
            new KeyValue(sidebar.prefWidthProperty(), end, Interpolator.EASE_BOTH),
            new KeyValue(sidebar.opacityProperty(), endOpacity, Interpolator.EASE_BOTH),
            new KeyValue(sidebarToggleButton.translateXProperty(), buttonEndX, Interpolator.EASE_BOTH)
        )
    );

    timeline.setOnFinished(e -> {
        // After animation, when sidebar is closed, make the sidebar mouse transparent
        // so clicks pass through to the map, but keep the container non-transparent
        // so the toggle button remains clickable
        if (!sidebarOpen) {
            sidebar.setMouseTransparent(true);
            sidebar.setVisible(false);
        }
    });

    timeline.play();

    sidebarOpen = !sidebarOpen;
    
    // When opening, make sidebar visible and interactive again
    if (sidebarOpen) {
        sidebar.setVisible(true);
        sidebar.setMouseTransparent(false);
    }

    // Update button with creative icons and gradients
    if (sidebarOpen) {
        sidebarToggleButton.setText("✓\nClose");
        // Blue gradient with enhanced shadow for open state
        sidebarToggleButton.setStyle("-fx-background-color: linear-gradient(to bottom, #1565c0, #1043a9); -fx-effect: dropshadow(gaussian, rgba(21,101,192,0.4), 12, 0, 0, 3);");
    } else {
        sidebarToggleButton.setText("◆\nOpen");
        // Green gradient with enhanced shadow for closed state
        sidebarToggleButton.setStyle("-fx-background-color: linear-gradient(to bottom, #2e7d32, #1b5e20); -fx-effect: dropshadow(gaussian, rgba(46,125,50,0.4), 12, 0, 0, 3);");
    }
}


    @FXML
private void handleCloseOverlay() {
    pharmacyInfoBox.setVisible(false);
}

    @FXML
private void handleBackToList() {
    pharmacyInfoBox.setVisible(false);
}


    /* =========================
       LISTVIEW (UX UPGRADE)
       ========================= */

    private void setupListView() {

        pharmacyListView.setFocusTraversable(true);

        pharmacyListView.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(Pharmacy p, boolean empty) {
                super.updateItem(p, empty);

                if (empty || p == null) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                // Pharmacy info container (no arrow button - clicking item does everything)
                VBox box = new VBox(3);
                box.getStyleClass().add("pharmacy-item");
                box.setMaxWidth(Double.MAX_VALUE);
                box.setStyle("-fx-padding: 8; -fx-cursor: hand;");

                Label name = new Label(p.getName());
                name.getStyleClass().add("pharmacy-item-name");
                name.setWrapText(true);
                name.setMaxWidth(Double.MAX_VALUE);

                Label address = new Label(
                        p.getAddress() != null
                                ? p.getAddress()
                                : "Address unavailable"
                );
                address.getStyleClass().add("pharmacy-item-address");
                address.setWrapText(true);
                address.setMaxWidth(Double.MAX_VALUE);

                Label distance = new Label(
                        p.getFormattedDistance() != null
                                ? p.getFormattedDistance()
                                : ""
                );
                distance.getStyleClass().add("pharmacy-item-distance");

                box.getChildren().addAll(name, address, distance);
                setGraphic(box);
            }
        });

        pharmacyListView.getSelectionModel()
                .selectedItemProperty()
                .addListener((obs, old, selected) -> {
                    if (selected == null) return;

                    // Highlight marker FIRST (turn red immediately)
                    mapService.selectPharmacyMarker(selected.getId());

                    // THEN show details panel after 0.25 second delay
                    executorService.submit(() -> {
                        try {
                            Thread.sleep(250); // 0.25 second delay (250ms)
                            Platform.runLater(() -> {
                                showPharmacyDetails(selected);
                            });
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                });
    }

    /**
     * Center map on pharmacy and highlight marker (called by arrow button)
     * OPTIMIZED FOR SPEED WITHOUT GLITCHES
     */
    private void centerOnPharmacyWithArrow(Pharmacy pharmacy) {
        if (pharmacy == null) return;

        try {
            // ONLY change marker color - nothing else!
            mapService.selectPharmacyMarker(pharmacy.getId());
            
            logger.info("Highlighted pharmacy marker: {}", pharmacy.getId());

        } catch (Exception e) {
            logger.error("Error in centerOnPharmacyWithArrow", e);
        }
    }

    /* =========================
       AUTOCOMPLETE
       ========================= */

    private void setupAutocomplete() {
        // Add Enter key handler to search field
        searchField.setOnAction(event -> {
            // When Enter is pressed, trigger search
            handleSearch();
            suggestionsPopup.hide();
        });

        searchField.textProperty().addListener((obs, old, text) -> {
            if (text == null || text.length() < 2) {
                suggestionsPopup.hide();
                return;
            }

            List<String> suggestions = searchHistory.stream()
                    .filter(s -> s != null && s.toLowerCase().startsWith(text.toLowerCase()))
                    .distinct()
                    .limit(5)
                    .collect(Collectors.toList());

            if (suggestions.isEmpty()) {
                suggestionsPopup.hide();
                return;
            }

            // Clear old items to prevent memory buildup
            suggestionsPopup.getItems().clear();

            for (String s : suggestions) {
                MenuItem item = new MenuItem(s);
                item.setOnAction(e -> {
                    searchField.setText(s);
                    suggestionsPopup.hide();
                });
                suggestionsPopup.getItems().add(item);
            }

            suggestionsPopup.show(searchField, Side.BOTTOM, 0, 0);
        });
    }

    /* =========================
       LOCATION FLOW
       ========================= */

    private void detectLocationOnStartup() {
        setStatus("Detecting location...");

        executorService.submit(() -> {
            try {
                var result = ipGeolocationService.detectLocationFromIP();
                if (result == null) {
                    Platform.runLater(this::useDefaultLocation);
                    return;
                }

                // Auto-accept location without confirmation dialog (for instant startup)
                autoSetLocation(result.latitude, result.longitude);

            } catch (Exception e) {
                logger.error("Location detection failed", e);
                Platform.runLater(this::useDefaultLocation);
            }
        });
    }

    private void autoSetLocation(double lat, double lon) {
        executorService.submit(() -> {
            try {
                String placeName = geocodingService.getShortAddress(lat, lon);
                String displayName =
                        placeName != null
                                ? placeName
                                : String.format("Lat %.4f, Lon %.4f", lat, lon);

                logger.info("Auto-detected location: {} ({}, {})",
                        displayName, lat, lon);

                Platform.runLater(() -> {
                    setUserLocation(lat, lon);
                    setStatus("📍 " + displayName);
                });
            } catch (Exception e) {
                logger.error("Error setting location", e);
                Platform.runLater(this::useDefaultLocation);
            }
        });
    }

    private void useDefaultLocation() {
        Location loc = locationService.getDefaultLocation();
        setUserLocation(loc.getLatitude(), loc.getLongitude());
    }

    private void setUserLocation(double lat, double lon) {
        userLocation = locationService.createLocation(lat, lon);
        mapService.setUserLocation(lat, lon);
        mapService.centerMap(lat, lon, 15);
        updateLocationLabel();

        // Cache the location for fast startup next time
        cacheService.cacheUserLocation(userLocation);
        saveMapState();

        // DISABLED: Pre-cache tiles causes lag - only cache on-demand
        // Pre-cache tiles for offline use (in background)
        // if (offlineManager.isOnline()) {
        //     executorService.submit(() -> {
        //         logger.info("🔽 Starting background tile download for offline use...");
        //         tileCache.preCacheTilesAroundLocation(lat, lon);
        //     });
        // }

        loadNearbyPharmacies();
    }

    /* =========================
       PHARMACY LOGIC
       ========================= */

    private void loadNearbyPharmacies() {
        if (userLocation == null) return;

        // Cancel any existing retry task
        if (currentRetryTask != null && !currentRetryTask.isDone()) {
            currentRetryTask.cancel(false);
        }

        setLoading(true);
        isLoadingPharmacies = true;
        
        // Clear old markers from previous location
        Platform.runLater(() -> {
            pharmacyListView.getItems().clear();
            mapService.clearPharmacyMarkers();
        });
        
        pharmacyListView.setPlaceholder(
                new Label("⏳ Loading pharmacies...")
        );
        
        // Reset retry count for new search
        pharmacyLoadRetryCount = 0;
        
        loadNearbyPharmaciesInternal();
    }
    
    private void scheduleRetryIfNeeded() {
        // Only schedule retry if we haven't exceeded max retries
        if (pharmacyLoadRetryCount >= MAX_RETRIES) {
            logger.info("Max retries ({}) reached, stopping", MAX_RETRIES);
            Platform.runLater(() -> {
                isLoadingPharmacies = false;
                setLoading(false);
                countLabel.setText("0");
                setStatus("❌ No pharmacies found after " + (MAX_RETRIES + 1) + " attempts");
                pharmacyListView.setPlaceholder(
                    new Label("😔 No pharmacies found in your search radius")
                );
            });
            return;
        }

        // Schedule next retry after 2 seconds if still loading and no results
        currentRetryTask = retryScheduler.schedule(() -> {
            Platform.runLater(() -> {
                if (isLoadingPharmacies && pharmacyListView.getItems().isEmpty()) {
                    pharmacyLoadRetryCount++;
                    logger.warn("⏰ 2-second timeout! Auto-retrying... (Retry {}/{})",
                        pharmacyLoadRetryCount, MAX_RETRIES);
                    setStatus("⏰ Still loading... Retrying automatically (Attempt " + (pharmacyLoadRetryCount + 1) + "/" + (MAX_RETRIES + 1) + ")");

                    // Retry the same query and schedule next retry
                    loadNearbyPharmaciesInternal();
                }
            });
        }, 2, java.util.concurrent.TimeUnit.SECONDS);
    }
    
    private void loadNearbyPharmaciesInternal() {
        updateActivity(); // Update activity timestamp
        
        // If offline, only use cache
        if (offlineManager.isOffline()) {
            logger.info("📵 Offline mode - loading from cache only");
            List<Pharmacy> cachedList = cacheService.getCachedPharmacies(
                userLocation.getLatitude(),
                userLocation.getLongitude()
            );

            if (cachedList != null && !cachedList.isEmpty()) {
                cachedList.forEach(p -> p.calculateDistanceFrom(userLocation));
                Platform.runLater(() -> {
                    updateActivity();
                    displayPharmacies(cachedList);
                    isLoadingPharmacies = false;
                    setStatus("📵 Offline - Showing " + cachedList.size() + " cached pharmacies");
                });
            } else {
                Platform.runLater(() -> {
                    updateActivity();
                    isLoadingPharmacies = false;
                    setLoading(false);
                    countLabel.setText("0");
                    setStatus("📵 Offline - No cached pharmacies available");
                    pharmacyListView.setPlaceholder(
                        new Label("📵 No cached data for this location")
                    );
                });
            }
            return;
        }

        // Online mode - proceed as normal
        executorService.submit(() -> {
            try {
                // Try cache first for instant loading
                List<Pharmacy> cachedList = cacheService.getCachedPharmacies(
                    userLocation.getLatitude(),
                    userLocation.getLongitude()
                );

                if (cachedList != null && !cachedList.isEmpty()) {
                    // Cache HIT! Display immediately
                    logger.info("✅ Cache HIT! Loading {} pharmacies from cache", cachedList.size());
                    cachedList.forEach(p -> p.calculateDistanceFrom(userLocation));
                    Platform.runLater(() -> {
                        updateActivity();
                        displayPharmacies(cachedList);
                        isLoadingPharmacies = false; // Stop retry loop
                        setStatus("Loaded from cache (fast!)");
                    });

                    // Still fetch fresh data in background to update cache
                    fetchAndUpdateCache();
                } else {
                    // Cache MISS - fetch from API
                    logger.info("❌ Cache MISS - Fetching from API");
                    fetchAndUpdateCache();
                    
                    // Schedule retry if results don't come back soon
                    scheduleRetryIfNeeded();
                }

            } catch (Exception e) {
                logger.error("Failed to load pharmacies", e);
                Platform.runLater(() -> {
                    updateActivity();
                    showError("Failed to load pharmacies");
                    // Retry on error
                    scheduleRetryIfNeeded();
                });
            }
        });
    }

    /**
     * Fetch pharmacies from API and update cache
     */
    private void fetchAndUpdateCache() {
        executorService.submit(() -> {
            try {
                List<Pharmacy> list = apiService.getNearbyPharmacies(
                        userLocation.getLatitude(),
                        userLocation.getLongitude()
                );

                if (list == null || list.isEmpty()) {
                    Platform.runLater(() -> {
                        updateActivity();

                        // Set counter to 0 when no pharmacies found
                        countLabel.setText("0");

                        // Check if we should retry
                        if (pharmacyLoadRetryCount < MAX_RETRIES) {
                            // Still have retries left - keep loading state active
                            logger.info("No pharmacies found, will retry if timeout occurs (Retry {}/{})",
                                pharmacyLoadRetryCount, MAX_RETRIES);
                            setStatus("⏳ No pharmacies found, waiting for retry...");
                            pharmacyListView.setPlaceholder(
                                new Label("⏳ No pharmacies yet, will retry automatically...")
                            );
                            // isLoadingPharmacies stays true so scheduleRetryIfNeeded will trigger

                        } else {
                            // Max retries reached - give up
                            isLoadingPharmacies = false;
                            setLoading(false);

                            // Cancel any pending retry
                            if (currentRetryTask != null && !currentRetryTask.isDone()) {
                                currentRetryTask.cancel(false);
                            }

                            setStatus("❌ No pharmacies found after " + (MAX_RETRIES + 1) + " attempts");
                            pharmacyListView.setPlaceholder(
                                new Label("😔 No pharmacies found in your search radius")
                            );
                            logger.info("Stopped retrying - no pharmacies found after {} attempts", MAX_RETRIES + 1);
                        }
                    });
                    return;
                }

                list.forEach(p -> p.calculateDistanceFrom(userLocation));

                // Update cache with fresh data
                cacheService.cachePharmacies(
                    userLocation.getLatitude(),
                    userLocation.getLongitude(),
                    list
                );

                // Cache user location
                cacheService.cacheUserLocation(userLocation);

                Platform.runLater(() -> {
                    displayPharmacies(list);
                    setStatus(list.size() + " pharmacies found");
                });

            } catch (Exception e) {
                logger.error("Failed to fetch pharmacies from API", e);
                Platform.runLater(() -> {
                    showError("Failed to load pharmacies");
                    setLoading(false); // STOP LOADING on error
                });
            }
        });
    }

    private void selectPharmacy(Pharmacy pharmacy) {
    if (pharmacy == null) return;

    pharmacyListView.getSelectionModel().select(pharmacy);
    pharmacyListView.scrollTo(pharmacy);
    showPharmacyDetails(pharmacy);

    // Highlight the marker on the map
    mapService.selectPharmacyMarker(pharmacy.getId());

    mapService.centerMap(
            pharmacy.getLatitude(),
            pharmacy.getLongitude(),
            16
    );
}


    private void displayPharmacies(List<Pharmacy> pharmacies) {
        updateActivity();

        // Cancel retry task since we have results (or final display)
        if (currentRetryTask != null && !currentRetryTask.isDone()) {
            currentRetryTask.cancel(false);
        }
        isLoadingPharmacies = false;

        // PERFORMANCE OPTIMIZATION: Limit pharmacies based on user preference
        List<Pharmacy> displayList = pharmacies;
        if (pharmacies.size() > pharmacyDisplayLimit) {
            logger.info("Limiting display from {} to {} pharmacies (user preference)",
                    pharmacies.size(), pharmacyDisplayLimit);
            displayList = pharmacies.stream()
                    .sorted(Comparator.comparingDouble(Pharmacy::getDistance))
                    .limit(pharmacyDisplayLimit)
                    .collect(Collectors.toList());
        }

        pharmacyListView.getItems().setAll(displayList);
        countLabel.setText(String.valueOf(displayList.size()));

        mapService.clearPharmacyMarkers();
        mapService.addPharmacyMarkers(displayList);
        mapService.fitBounds();

        displayList.forEach(p -> searchHistory.add(p.getName()));

        setStatus(displayList.isEmpty()
                ? "No pharmacies found"
                : displayList.size() + " pharmacies found");

        setLoading(false);
    }

    private void showPharmacyDetails(Pharmacy p) {
        pharmacyNameLabel.setText(p.getName());
        addressLabel.setText(
                p.getAddress() != null ? p.getAddress() : "—"
        );
        phoneLabel.setText(
                p.getPhone() != null ? p.getPhone() : "—"
        );
        hoursLabel.setText(
                p.getOpeningHours() != null ? p.getOpeningHours() : "—"
        );
        distanceLabel.setText(p.getFormattedDistance());
        pharmacyInfoBox.setVisible(true);
    }

    /* =========================
       UI EVENTS
       ========================= */

    @FXML
    private void handleSearch() {
        // Disable search in offline mode
        if (offlineManager.isOffline()) {
            setStatus("📵 Search disabled in offline mode");
            return;
        }

        String query = searchField.getText().trim();
        if (query.isEmpty()) {
            setStatus("Enter a location");
            return;
        }

        searchHistory.add(query);
        suggestionsPopup.hide();
        setStatus("Searching...");
        setLoading(true);

        executorService.submit(() -> {
            try {
                Location loc = geocodingService.geocode(query);
                if (loc == null) {
                    Platform.runLater(() -> showError("Location not found"));
                    return;
                }

                Platform.runLater(() ->
                        setUserLocation(loc.getLatitude(), loc.getLongitude())
                );

            } catch (Exception e) {
                logger.error("Search failed", e);
                Platform.runLater(() -> showError("Search failed"));
            }
        });
    }

    @FXML
    private void handleRefresh() {
        loadNearbyPharmacies();
    }

    @FXML
    private void handleZoomIn() {
        mapService.zoomIn();
    }

    @FXML
    private void handleZoomOut() {
        mapService.zoomOut();
    }

    @FXML
    private void handleRoute() {
        System.out.println("Route feature not implemented yet.");
    }
    @FXML
    private void handleFavorite() {
        System.out.println("Favorite feature not implemented yet.");
    }

    @FXML
    private void handleSort() {
        Comparator<Pharmacy> comparator =
                "Name".equals(sortComboBox.getValue())
                        ? Comparator.comparing(Pharmacy::getName)
                        : Comparator.comparingDouble(Pharmacy::getDistance);

        pharmacyListView.getItems().sort(comparator);
    }

    @FXML
    private void handleLocate() {
        detectLocationOnStartup();
    }

    @FXML
    private void handleCenterOnUser() {
        if (userLocation != null) {
            mapService.centerMap(
                userLocation.getLatitude(),
                userLocation.getLongitude(),
                16
            );
            setStatus("📍 Centered on your location");
        } else {
            setStatus("❌ Location not available yet");
        }
    }

    @FXML
    private void handleToggleTheme() {
        isDarkMode = !isDarkMode;
        applyTheme();
    }

    @FXML
    private void handleDisplayLimitChange() {
        String selected = displayLimitComboBox.getValue();
        if (selected == null || selected.isEmpty()) {
            return;
        }

        try {
            int newLimit = Integer.parseInt(selected);
            if (newLimit != pharmacyDisplayLimit) {
                pharmacyDisplayLimit = newLimit;
                logger.info("Pharmacy display limit changed to: {}", pharmacyDisplayLimit);

                // Refresh the display with new limit if we have pharmacies
                if (!pharmacyListView.getItems().isEmpty() && userLocation != null) {
                    setStatus("Display limit changed to " + pharmacyDisplayLimit + " - Refreshing...");
                    loadNearbyPharmacies();
                }
            }
        } catch (NumberFormatException e) {
            logger.error("Invalid display limit: {}", selected);
        }
    }

    @FXML
    private void handleMinimize() {
        Stage stage = (Stage) themeToggleButton.getScene().getWindow();
        stage.setIconified(true);
    }

    @FXML
    private void handleMaximize() {
        Stage stage = (Stage) themeToggleButton.getScene().getWindow();
        if (stage.isMaximized()) {
            stage.setMaximized(false);
            maximizeButton.setText("☐");
        } else {
            stage.setMaximized(true);
            maximizeButton.setText("❐");
        }
    }

    @FXML
    private void handleClose() {
        Stage stage = (Stage) themeToggleButton.getScene().getWindow();
        stage.close();
    }

    private void applyTheme() {
        // Use Platform.runLater to prevent freezing
        Platform.runLater(() -> {
            try {
                Scene scene = themeToggleButton.getScene();
                if (scene == null) {
                    logger.warn("Scene not ready yet for theme change");
                    return;
                }

                Parent root = scene.getRoot();
                if (root == null) {
                    logger.warn("Root not ready yet for theme change");
                    return;
                }

                if (isDarkMode) {
                    // Apply Dark Mode
                    if (!root.getStyleClass().contains("dark-mode")) {
                        root.getStyleClass().add("dark-mode");
                    }
                    themeToggleButton.setText("☀");
                    themeToggleButton.setStyle("-fx-font-size: 26px; -fx-cursor: hand;");

                    setStatus("🌙 Dark mode enabled");
                    logger.info("✓ Dark mode applied");
                } else {
                    // Apply Light Mode
                    root.getStyleClass().remove("dark-mode");
                    themeToggleButton.setText("🌙");
                    themeToggleButton.setStyle("-fx-font-size: 20px; -fx-cursor: hand;");

                    setStatus("☀️ Light mode enabled");
                    logger.info("✓ Light mode applied");
                }

                // Update activity timestamp to prevent freeze detection
                updateActivity();

            } catch (Exception e) {
                logger.error("❌ Error applying theme", e);
                showError("Failed to change theme");
            }
        });
    }


    /* =========================
       CACHE MANAGEMENT
       ========================= */

    /**
     * Try to restore app state from cache for instant startup
     */
    private void tryRestoreFromCache() {
        logger.info("Attempting to restore from cache...");

        // Try to restore map state
        CacheService.MapState mapState = cacheService.getCachedMapState();
        if (mapState != null) {
            logger.info("✅ Restored map state from cache: {}, {} @ zoom {}",
                       mapState.getLatitude(), mapState.getLongitude(), mapState.getZoomLevel());

            mapService.centerMap(
                mapState.getLatitude(),
                mapState.getLongitude(),
                mapState.getZoomLevel()
            );

            // Restore last search query if exists
            // Commented out - keep search field empty on startup
            // if (mapState.getLastSearchQuery() != null && !mapState.getLastSearchQuery().isEmpty()) {
            //     searchField.setText(mapState.getLastSearchQuery());
            // }
            searchField.clear();
        }

        // Try to restore user location
        Location cachedLocation = cacheService.getCachedUserLocation();
        if (cachedLocation != null) {
            logger.info("✅ Restored user location from cache: {}, {}",
                       cachedLocation.getLatitude(), cachedLocation.getLongitude());

            userLocation = cachedLocation;
            mapService.setUserLocation(
                cachedLocation.getLatitude(),
                cachedLocation.getLongitude()
            );
            updateLocationLabel();

            // Try to load cached pharmacies
            List<Pharmacy> cachedPharmacies = cacheService.getCachedPharmacies(
                cachedLocation.getLatitude(),
                cachedLocation.getLongitude()
            );

            if (cachedPharmacies != null && !cachedPharmacies.isEmpty()) {
                logger.info("✅ Loaded {} pharmacies from cache - INSTANT STARTUP!", cachedPharmacies.size());
                cachedPharmacies.forEach(p -> p.calculateDistanceFrom(userLocation));
                displayPharmacies(cachedPharmacies);
                setStatus("Loaded from cache (" + cachedPharmacies.size() + " pharmacies)");
            }
        }

        // Log cache stats
        CacheService.CacheStats stats = cacheService.getStats();
        logger.info("Cache stats: {}", stats);
    }

    /**
     * Save current map state to cache
     */
    private void saveMapState() {
        if (userLocation != null) {
            String lastSearch = searchField.getText();
            cacheService.cacheMapState(
                userLocation.getLatitude(),
                userLocation.getLongitude(),
                15, // Default zoom
                lastSearch != null ? lastSearch : ""
            );
            logger.info("💾 Saved map state to cache");
        }
    }

    /* =========================
       UTILITIES
       ========================= */

    private void setLoading(boolean loading) {
        loadingBox.setVisible(loading);
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            setLoading(false);
            setStatus(message);
        });
    }

    private void setStatus(String text) {
        statusLabel.setText(text);
    }

    private void loadLogoImage() {
        try {
            // Try to load logo.png from resources
            Image logoImage = new Image(getClass().getResourceAsStream("/logo.png"));
            if (!logoImage.isError()) {
                logoImageView.setImage(logoImage);
            } else {
                logger.warn("Logo image not found or failed to load. Using default icon.");
                // Fallback - you can set a default image or keep it empty
            }
        } catch (NullPointerException e) {
            logger.warn("Logo image not found at /logo.png. Please add your logo image.", e);
            // Logo will just be empty, but app will still work
        } catch (Exception e) {
            logger.error("Error loading logo image", e);
        }
    }

    private void updateLocationLabel() {
        locationLabel.setText(String.format(
                "Location: %.4f, %.4f",
                userLocation.getLatitude(),
                userLocation.getLongitude()
        ));
    }

    /**
     * Gracefully shutdown the executor service
     */
    /**
     * Public shutdown method called when application closes
     */
    public void shutdown() {
        logger.info("MainViewController shutdown initiated...");

        // Cancel any ongoing retry tasks
        if (currentRetryTask != null && !currentRetryTask.isDone()) {
            currentRetryTask.cancel(true);
        }

        // Cancel freeze watchdog
        if (freezeWatchdog != null && !freezeWatchdog.isDone()) {
            freezeWatchdog.cancel(true);
        }

        // Save map state
        saveMapState();

        // Shutdown offline services
        tileServer.stop();
        tileCache.shutdown();
        offlineManager.shutdown();

        // Shutdown retry scheduler
        shutdownRetryScheduler();

        // Shutdown main executor
        shutdownExecutor();

        logger.info("MainViewController shutdown complete");
    }

    private void shutdownRetryScheduler() {
        if (retryScheduler != null && !retryScheduler.isShutdown()) {
            try {
                logger.info("Shutting down retry scheduler...");
                retryScheduler.shutdownNow();

                if (!retryScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    logger.warn("Retry scheduler did not terminate cleanly");
                    retryScheduler.shutdownNow();

                    if (!retryScheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                        logger.error("Retry scheduler failed to terminate");
                    }
                }
                logger.info("Retry scheduler shut down successfully");
            } catch (InterruptedException e) {
                logger.error("Interrupted while shutting down retry scheduler", e);
                retryScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    private void shutdownExecutor() {
        if (executorService != null && !executorService.isShutdown()) {
            try {
                logger.info("Shutting down executor service...");
                executorService.shutdown();
                
                // Wait up to 2 seconds for tasks to complete
                if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                    logger.warn("Executor did not terminate cleanly, forcing shutdown");
                    List<Runnable> droppedTasks = executorService.shutdownNow();
                    logger.info("Dropped {} pending tasks", droppedTasks.size());

                    // Wait another second for forced shutdown
                    if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                        logger.error("Executor failed to terminate even after shutdownNow()");
                    }
                }
                logger.info("Executor service shut down successfully");
            } catch (InterruptedException e) {
                logger.error("Interrupted while shutting down executor", e);
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}

