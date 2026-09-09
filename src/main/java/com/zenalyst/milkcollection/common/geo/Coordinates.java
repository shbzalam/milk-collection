package com.zenalyst.milkcollection.common.geo;

/**
 * A WGS-84 point. Immutable value object so that planning and ETA code can pass positions
 * around without dragging JPA entities along.
 */
public record Coordinates(double latitude, double longitude) {

    private static final double EARTH_RADIUS_KM = 6371.0088;

    public Coordinates {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("latitude must be between -90 and 90, got " + latitude);
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("longitude must be between -180 and 180, got " + longitude);
        }
    }

    /**
     * Great-circle ("as the crow flies") distance in kilometres.
     *
     * <p>This is a straight-line distance, not a road distance. Callers that need a road
     * estimate apply a winding factor - see
     * {@link com.zenalyst.milkcollection.common.travel.SimpleTravelTimeProvider}.
     */
    public double haversineDistanceKm(Coordinates other) {
        double latDelta = Math.toRadians(other.latitude - this.latitude);
        double lonDelta = Math.toRadians(other.longitude - this.longitude);
        double lat1 = Math.toRadians(this.latitude);
        double lat2 = Math.toRadians(other.latitude);

        double a = Math.pow(Math.sin(latDelta / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(lonDelta / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
