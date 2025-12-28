package com.pharmalocator.services;

import com.pharmalocator.config.AppConfig;
import com.pharmalocator.models.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * Service responsible for providing and validating user's geographical location.
 * 
 * By default, a predefined location is used.
 * The user can update it later using the "Locate Me" feature.
 */
public class LocationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocationService.class);

    private final AppConfig config;
    private Location userLocation;

    public LocationService() {
        this.config = AppConfig.getInstance();
    }

    /**
     * Returns the user's current location.
     * 
     * For now, this method always returns the default location.
     * The UI is responsible for updating the location when the user
     * clicks the "Locate Me" button.
     */
    public Location getUserLocation() {

    if (userLocation != null) {
        LOGGER.info("Returning user-selected location");
        return userLocation;
    }

    LOGGER.info("Returning default location");
    return getDefaultLocation();
}


    /**
     * Creates a Location object from given coordinates.
     *
     * @param latitude  latitude value
     * @param longitude longitude value
     * @return valid Location or default location if invalid
     */
    public Location createLocation(double latitude, double longitude) {

        if (!isValidCoordinates(latitude, longitude)) {
            LOGGER.warn("Invalid coordinates received: ({}, {})", latitude, longitude);
            return getDefaultLocation();
        }

        Location location = new Location(latitude, longitude);
        LOGGER.info("Location created: ({}, {})", latitude, longitude);
        return location;
    }

    public void setUserLocation(double latitude, double longitude) {

    if (!isValidCoordinates(latitude, longitude)) {
        LOGGER.warn("Attempted to set invalid user location: ({}, {})", latitude, longitude);
        return;
    }

    this.userLocation = new Location(latitude, longitude);
    this.userLocation.setCity("Custom Location");
    this.userLocation.setCountry("User Selected");

    LOGGER.info("User location updated to ({}, {})", latitude, longitude);
}


    /**
     * Returns the application's default location.
     * This acts as a fallback until the user chooses their real location.
     */
    public Location getDefaultLocation() {

        double latitude = config.getDefaultLatitude();
        double longitude = config.getDefaultLongitude();

        Location location = new Location(latitude, longitude);
        location.setCity("Default Location");
        location.setCountry("Use 'Locate Me' to update");

        LOGGER.info("Default location used: ({}, {})", latitude, longitude);
        return location;
    }

    /**
     * Validates geographic coordinate ranges.
     *
     * @param latitude  latitude to validate
     * @param longitude longitude to validate
     * @return true if valid, false otherwise
     */
    public boolean isValidCoordinates(double latitude, double longitude) {
        return latitude >= -90.0 && latitude <= 90.0
            && longitude >= -180.0 && longitude <= 180.0;
    }
}
