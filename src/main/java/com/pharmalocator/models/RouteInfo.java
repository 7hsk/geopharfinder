package com.pharmalocator.models;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents route information between two points.
 */
public class RouteInfo {
    private List<double[]> coordinates;
    private double distance; // in kilometers
    private double duration; // in minutes

    public RouteInfo() {
        this.coordinates = new ArrayList<>();
    }

    public RouteInfo(List<double[]> coordinates, double distance, double duration) {
        this.coordinates = coordinates != null ? coordinates : new ArrayList<>();
        this.distance = distance;
        this.duration = duration;
    }

    /**
     * Creates a simple straight-line route between two points.
     */
    public static RouteInfo createSimpleRoute(double startLat, double startLon, 
                                             double endLat, double endLon) {
        List<double[]> coords = new ArrayList<>();
        coords.add(new double[]{startLat, startLon});
        coords.add(new double[]{endLat, endLon});
        
        double distance = Location.calculateDistance(startLat, startLon, endLat, endLon);
        // Estimate duration: assume 40 km/h average speed
        double duration = (distance / 40.0) * 60.0;
        
        return new RouteInfo(coords, distance, duration);
    }

    /**
     * Returns formatted distance string.
     */
    public String getFormattedDistance() {
        if (distance < 1.0) {
            return String.format("%.0f m", distance * 1000);
        } else {
            return String.format("%.2f km", distance);
        }
    }

    /**
     * Returns formatted duration string.
     */
    public String getFormattedDuration() {
        if (duration < 60) {
            return String.format("%.0f min", duration);
        } else {
            int hours = (int) (duration / 60);
            int minutes = (int) (duration % 60);
            return String.format("%d h %d min", hours, minutes);
        }
    }

    // Getters and Setters
    public List<double[]> getCoordinates() {
        return coordinates;
    }

    public void setCoordinates(List<double[]> coordinates) {
        this.coordinates = coordinates;
    }

    public double getDistance() {
        return distance;
    }

    public void setDistance(double distance) {
        this.distance = distance;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    @Override
    public String toString() {
        return String.format("Route: %s, %s", getFormattedDistance(), getFormattedDuration());
    }
}

