package com.pharmalocator.services;

import com.pharmalocator.models.RouteInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for calculating routes between locations.
 */
public class RouteService {
    private static final Logger logger = LoggerFactory.getLogger(RouteService.class);

    /**
     * Gets route information between two points.
     * Currently uses simple straight-line calculation.
     * Can be extended to use OpenRouteService API for real routing.
     */
    public RouteInfo getRoute(double startLat, double startLon, double endLat, double endLon) {
        logger.debug("Calculating route from ({}, {}) to ({}, {})", 
                    startLat, startLon, endLat, endLon);
        
        RouteInfo route = RouteInfo.createSimpleRoute(startLat, startLon, endLat, endLon);
        
        logger.info("Route calculated: {} - {}", 
                   route.getFormattedDistance(), route.getFormattedDuration());
        
        return route;
    }
}

