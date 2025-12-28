package com.pharmalocator.models;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a pharmacy with its location, contact information, and metadata.
 */
public class Pharmacy implements Serializable {
    private static final long serialVersionUID = 1L;

    private String id;
    private String name;
    private double latitude;
    private double longitude;
    private String address;
    private String phone;
    private String openingHours;
    private double distance; // Distance from user in kilometers
    private boolean isOpen;
    private Map<String, String> tags;
    private List<double[]> geometry; // Building polygon coordinates [[lat,lon], [lat,lon], ...]

    public Pharmacy() {
        this.tags = new HashMap<>();
        this.geometry = new ArrayList<>();
    }

    public Pharmacy(String id, String name, double latitude, double longitude) {
        this();
        this.id = id;
        this.name = name;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * Calculates and sets the distance from a given location.
     */
    public void calculateDistanceFrom(Location userLocation) {
        this.distance = Location.calculateDistance(
            userLocation.getLatitude(), 
            userLocation.getLongitude(),
            this.latitude, 
            this.longitude
        );
    }

    /**
     * Calculates and sets the distance from given coordinates.
     */
    public void calculateDistanceFrom(double userLat, double userLon) {
        this.distance = Location.calculateDistance(userLat, userLon, this.latitude, this.longitude);
    }

    /**
     * Returns a formatted distance string.
     */
    public String getFormattedDistance() {
        if (distance < 1.0) {
            return String.format("%.0f m", distance * 1000);
        } else {
            return String.format("%.2f km", distance);
        }
    }

    /**
     * Adds a tag to the pharmacy.
     */
    public void addTag(String key, String value) {
        if (tags == null) {
            tags = new HashMap<>();
        }
        tags.put(key, value);
    }

    /**
     * Gets a tag value by key.
     */
    public String getTag(String key) {
        return tags != null ? tags.get(key) : null;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name != null ? name : "Pharmacie";
    }

    public void setName(String name) {
        this.name = name;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getOpeningHours() {
        return openingHours;
    }

    public void setOpeningHours(String openingHours) {
        this.openingHours = openingHours;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void setOpen(boolean open) {
        isOpen = open;
    }

    public Map<String, String> getTags() {
        return tags;
    }

    public void setTags(Map<String, String> tags) {
        this.tags = tags;
    }

    public List<double[]> getGeometry() {
        return geometry;
    }

    public void setGeometry(List<double[]> geometry) {
        this.geometry = geometry;
    }

    public void addGeometryPoint(double lat, double lon) {
        if (geometry == null) {
            geometry = new ArrayList<>();
        }
        geometry.add(new double[]{lat, lon});
    }

    public boolean hasGeometry() {
        return geometry != null && !geometry.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pharmacy pharmacy = (Pharmacy) o;
        return Objects.equals(id, pharmacy.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("%s - %s", getName(), getFormattedDistance());
    }
}

