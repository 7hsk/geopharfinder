package com.pharmalocator.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.*;

/**
 * OfflineManager - Detects and manages offline/online state
 *
 * Features:
 * - Periodic connectivity checks
 * - Callbacks for online/offline state changes
 * - Graceful degradation to cached data when offline
 */
public class OfflineManager {

    private static final Logger logger = LoggerFactory.getLogger(OfflineManager.class);

    private static final String[] CHECK_HOSTS = {
        "8.8.8.8",           // Google DNS
        "1.1.1.1",           // Cloudflare DNS
        "208.67.222.222"     // OpenDNS
    };

    private static final int CHECK_INTERVAL_SECONDS = 10; // Check every 10 seconds
    private static final int TIMEOUT_MS = 3000; // 3 second timeout

    private volatile boolean isOnline = true;
    private volatile boolean lastKnownState = true;
    private final ScheduledExecutorService scheduler;
    private final CopyOnWriteArrayList<OfflineStateListener> listeners;

    public interface OfflineStateListener {
        void onOnline();
        void onOffline();
    }

    public OfflineManager() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "OfflineManager-Thread");
            t.setDaemon(true);
            return t;
        });
        this.listeners = new CopyOnWriteArrayList<>();

        // Initial check
        checkConnectivity();

        // Start periodic checks
        startPeriodicChecks();
    }

    /**
     * Add a listener for offline/online state changes
     */
    public void addListener(OfflineStateListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove a listener
     */
    public void removeListener(OfflineStateListener listener) {
        listeners.remove(listener);
    }

    /**
     * Check if currently online
     */
    public boolean isOnline() {
        return isOnline;
    }

    /**
     * Check if currently offline
     */
    public boolean isOffline() {
        return !isOnline;
    }

    /**
     * Start periodic connectivity checks
     */
    private void startPeriodicChecks() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                checkConnectivity();
            } catch (Exception e) {
                logger.error("Error during connectivity check", e);
            }
        }, CHECK_INTERVAL_SECONDS, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Check connectivity by pinging known hosts
     */
    private void checkConnectivity() {
        boolean online = false;

        // Try to reach any of the check hosts
        for (String host : CHECK_HOSTS) {
            if (canReachHost(host)) {
                online = true;
                break;
            }
        }

        // Update state
        boolean stateChanged = (online != lastKnownState);
        lastKnownState = online;
        isOnline = online;

        // Notify listeners if state changed
        if (stateChanged) {
            if (online) {
                logger.info("🌐 Connection RESTORED - Online mode");
                notifyOnline();
            } else {
                logger.warn("📵 Connection LOST - Offline mode activated");
                notifyOffline();
            }
        }
    }

    /**
     * Try to reach a host
     */
    private boolean canReachHost(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isReachable(TIMEOUT_MS);
        } catch (UnknownHostException e) {
            // DNS resolution failed - definitely offline
            return false;
        } catch (IOException e) {
            // Network error
            return false;
        }
    }

    /**
     * Notify listeners that we're online
     */
    private void notifyOnline() {
        for (OfflineStateListener listener : listeners) {
            try {
                listener.onOnline();
            } catch (Exception e) {
                logger.error("Error notifying listener of online state", e);
            }
        }
    }

    /**
     * Notify listeners that we're offline
     */
    private void notifyOffline() {
        for (OfflineStateListener listener : listeners) {
            try {
                listener.onOffline();
            } catch (Exception e) {
                logger.error("Error notifying listener of offline state", e);
            }
        }
    }

    /**
     * Force an immediate connectivity check
     */
    public void checkNow() {
        checkConnectivity();
    }

    /**
     * Shutdown the manager
     */
    public void shutdown() {
        logger.info("Shutting down OfflineManager...");

        // Clear listeners first to prevent callbacks during shutdown
        listeners.clear();

        // Force immediate shutdown of scheduler
        scheduler.shutdownNow();

        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                logger.warn("OfflineManager scheduler failed to terminate in time");
                // Force again
                scheduler.shutdownNow();

                // Wait one more time
                if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
                    logger.error("OfflineManager scheduler could not be terminated");
                }
            }
        } catch (InterruptedException e) {
            logger.error("Interrupted during OfflineManager shutdown", e);
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        logger.info("OfflineManager shut down complete");
    }
}

