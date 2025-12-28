package com.pharmalocator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pharmalocator.config.AppConfig;
import com.pharmalocator.services.DatabaseService;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;

/**
 * Main application entry point for GeoPharFinder.
 */
public class MainApp extends Application {
    private static final Logger logger = LoggerFactory.getLogger(MainApp.class);
    private AppConfig config;
    private com.pharmalocator.controllers.MainViewController controller;

    @Override
    public void start(Stage primaryStage) {
        try {
            logger.info("Starting GeoPharFinder application");

            // Register shutdown hook to ensure cleanup on JVM exit
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("JVM shutdown hook triggered");

                // Delete marker file to signal batch script
                String markerFile = System.getProperty("pharmalocator.marker");
                if (markerFile != null) {
                    try {
                        java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get(markerFile));
                        logger.info("Marker file deleted: {}", markerFile);
                    } catch (Exception e) {
                        logger.warn("Failed to delete marker file", e);
                    }
                }

                if (controller != null) {
                    try {
                        controller.shutdown();
                    } catch (Exception e) {
                        logger.error("Error in shutdown hook", e);
                    }
                }
            }));

            // Load configuration
            config = AppConfig.getInstance();
            
            // Initialize database
            DatabaseService.getInstance();
            
            // Load FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/main_view.fxml"));
            Parent root = loader.load();
            
            // Get controller reference for shutdown
            controller = loader.getController();

            // Create scene
            Scene scene = new Scene(root, config.getWindowWidth(), config.getWindowHeight());
            
            // Setup stage - DECORATED WINDOW (default Windows title bar with our enhanced header)
            primaryStage.setTitle(config.getAppName() + " - Find Nearby Pharmacies");
            // No initStyle() call = default DECORATED style with Windows controls
            primaryStage.setMaximized(true);  // Start maximized
            primaryStage.setScene(scene);
            
            // Optional: Allow F11 key to toggle fullscreen
            scene.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.F11) {
                    primaryStage.setFullScreen(!primaryStage.isFullScreen());
                } else if (event.getCode() == KeyCode.ESCAPE && primaryStage.isFullScreen()) {
                    primaryStage.setFullScreen(false);
                }
            });
            
            // Set application icon (use logo.png, fallback to icon.png)
            try {
                // Try to load logo.png first (your custom logo)
                Image icon = new Image(getClass().getResourceAsStream("/logo.png"));
                if (!icon.isError()) {
                    primaryStage.getIcons().add(icon);
                    logger.info("Application icon loaded from logo.png");
                } else {
                    // Fallback to icon.png if logo fails
                    Image fallbackIcon = new Image(getClass().getResourceAsStream("/icon.png"));
                    primaryStage.getIcons().add(fallbackIcon);
                    logger.info("Application icon loaded from icon.png (fallback)");
                }
            } catch (NullPointerException e) {
                logger.warn("Application icon not found. Using default.");
            } catch (Exception e) {
                logger.warn("Error loading application icon", e);
            }
            
            // Handle window close
            primaryStage.setOnCloseRequest(event -> {
                logger.info("Window close requested - initiating shutdown");
                shutdown();
                Platform.exit();
                System.exit(0);
            });
            
            // Show stage
            primaryStage.show();
            
            logger.info("Application started successfully");
            
        } catch (Exception e) {
            logger.error("Error starting application", e);
            showErrorAndExit("Failed to start application: " + e.getMessage());
        }
    }

    /**
     * Override stop() to ensure proper cleanup when application terminates
     */
    @Override
    public void stop() throws Exception {
        logger.info("JavaFX Application stop() called - cleaning up resources");
        shutdown();
        super.stop();

        // Force JVM exit to kill any remaining threads
        System.exit(0);
    }

    /**
     * Cleanup resources on shutdown.
     */
    private void shutdown() {
        try {
            logger.info("Shutting down application resources...");

            // Shutdown controller resources if available
            if (controller != null) {
                controller.shutdown();
            }

            // Close database
            DatabaseService.getInstance().close();

            logger.info("Application shutdown complete");
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        }
    }

    /**
     * Shows error dialog and exits.
     */
    private void showErrorAndExit(String message) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.ERROR
        );
        alert.setTitle("Error");
        alert.setHeaderText("Application Error");
        alert.setContentText(message);
        alert.showAndWait();
        System.exit(1);
    }

    public static void main(String[] args) {
        // Optimized system properties for better performance and lower resource usage
        System.setProperty("prism.lcdtext", "false");
        System.setProperty("prism.text", "t2k");
        System.setProperty("javafx.animation.fullspeed", "false"); // Disabled for lower CPU usage
        System.setProperty("javafx.animation.framerate", "30"); // Reduced from 60 to 30 FPS
        System.setProperty("prism.vsync", "true"); // Enable vsync to reduce tearing and CPU usage
        System.setProperty("prism.order", "sw"); // Software rendering
        System.setProperty("prism.poolstats", "false"); // Disable pool stats
        System.setProperty("prism.dirtyopts", "true"); // Enable dirty region optimization
        System.setProperty("javafx.pulseLogger", "false"); // Disable pulse logger

        launch(args);
    }
}

