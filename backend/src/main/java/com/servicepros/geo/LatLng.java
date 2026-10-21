package com.servicepros.geo;

public record LatLng(double lat, double lng) {

    private static final double EARTH_RADIUS_KM = 6371.0;

    /** Great-circle distance in km. */
    public double haversineKm(LatLng o) {
        double dLat = Math.toRadians(o.lat - lat);
        double dLng = Math.toRadians(o.lng - lng);
        double a = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(lat)) * Math.cos(Math.toRadians(o.lat)) * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }

    public String asParam() {
        return lat + "," + lng;
    }
}
