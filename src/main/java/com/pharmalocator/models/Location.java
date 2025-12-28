package com.pharmalocator.models;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents a geographical location with coordinates and address information.
 */
public class Location implements Serializable {
    private static final long serialVersionUID = 1L;

    private double latitude;
    private double longitude;
    private String city;
    private String country;
    private String address;

    // Earth's radius in kilometers
    private static final double EARTH_RADIUS_KM = 6371.0;

    public Location() {
    }

    public Location(double latitude, double longitude) {
        validateCoordinates(latitude, longitude);
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Location(double latitude, double longitude, String city, String country) {
        validateCoordinates(latitude, longitude);
        this.latitude = latitude;
        this.longitude = longitude;
        this.city = city;
        this.country = country;
    }

    /**
     * Validates GPS coordinates.
     */
    private void validateCoordinates(double lat, double lon) {
        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90, got: " + lat);
        }
        if (lon < -180 || lon > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180, got: " + lon);
        }
    }

    /**
     * Calculates the distance between two locations using the Haversine formula.
     * 
     * @param other The other location
     * @return Distance in kilometers
     */
    public double distanceTo(Location other) {
        return calculateDistance(this.latitude, this.longitude, 
                                other.latitude, other.longitude);
    }

    /**
     * Calculates distance between two points using Haversine formula.
     * Formula: a = sin²(Δlat/2) + cos(lat1) × cos(lat2) × sin²(Δlon/2)
     *          c = 2 × atan2(√a, √(1−a))
     *          distance = R × c
     */
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return EARTH_RADIUS_KM * c;
    }

    // Getters and Setters
    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        validateCoordinates(latitude, this.longitude);
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        validateCoordinates(this.latitude, longitude);
        this.longitude = longitude;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return Double.compare(location.latitude, latitude) == 0 &&
               Double.compare(location.longitude, longitude) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(latitude, longitude);
    }

    @Override
    public String toString() {
        return String.format("Location{lat=%.6f, lon=%.6f, city='%s', country='%s'}", 
                           latitude, longitude, city, country);
    }
}

