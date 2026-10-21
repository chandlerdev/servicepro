package com.servicepros.geo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.servicepros.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Travel distance/time between every pair of points.
 * Uses the Google Routes API (computeRouteMatrix) when a key is configured, otherwise
 * straight-line distance × road factor at the configured average speed.
 */
@Slf4j
@Service
public class DistanceMatrixService {

    /** Routes API limit for TRAFFIC_UNAWARE is 625 elements → 25 × 25. */
    private static final int GOOGLE_MAX_POINTS = 25;

    public record Matrix(double[][] km, double[][] minutes, boolean fromGoogle) {
    }

    private final AppProperties props;
    private final RestClient client;

    public DistanceMatrixService(AppProperties props, RestClient.Builder builder) {
        this.props = props;
        this.client = builder.baseUrl("https://routes.googleapis.com").build();
    }

    public Matrix compute(List<LatLng> points) {
        if (props.getGoogle().isConfigured() && points.size() > 1 && points.size() <= GOOGLE_MAX_POINTS) {
            try {
                return google(points);
            } catch (Exception e) {
                log.warn("Google Routes matrix failed ({}), falling back to straight-line estimate", e.getMessage());
            }
        }
        return haversine(points);
    }

    Matrix haversine(List<LatLng> points) {
        int n = points.size();
        double[][] km = new double[n][n];
        double[][] min = new double[n][n];
        double factor = props.getOptimizer().getRoadFactor();
        double speed = props.getOptimizer().getAvgSpeedKmh();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i == j) continue;
                km[i][j] = points.get(i).haversineKm(points.get(j)) * factor;
                min[i][j] = km[i][j] / speed * 60.0;
            }
        }
        return new Matrix(km, min, false);
    }

    @SuppressWarnings("unchecked")
    private Matrix google(List<LatLng> points) {
        List<Map<String, Object>> waypoints = new ArrayList<>();
        for (LatLng p : points) {
            waypoints.add(Map.of("waypoint", Map.of("location",
                    Map.of("latLng", Map.of("latitude", p.lat(), "longitude", p.lng())))));
        }
        Map<String, Object> body = Map.of(
                "origins", waypoints,
                "destinations", waypoints,
                "travelMode", "DRIVE",
                "routingPreference", "TRAFFIC_UNAWARE");

        List<Map<String, Object>> rows = client.post()
                .uri("/distanceMatrix/v2:computeRouteMatrix")
                .header("X-Goog-Api-Key", props.getGoogle().getMapsApiKey())
                .header("X-Goog-FieldMask", "originIndex,destinationIndex,distanceMeters,duration,condition")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(List.class);

        int n = points.size();
        Matrix fallback = haversine(points);
        double[][] km = fallback.km();
        double[][] min = fallback.minutes();
        if (rows != null) {
            for (Map<String, Object> r : rows) {
                if (r.get("originIndex") == null || r.get("destinationIndex") == null) continue;
                int i = ((Number) r.get("originIndex")).intValue();
                int j = ((Number) r.get("destinationIndex")).intValue();
                if (i == j || i >= n || j >= n || !"ROUTE_EXISTS".equals(r.get("condition"))) continue;
                if (r.get("distanceMeters") instanceof Number d) km[i][j] = d.doubleValue() / 1000.0;
                if (r.get("duration") instanceof String s && s.endsWith("s")) {
                    min[i][j] = Double.parseDouble(s.substring(0, s.length() - 1)) / 60.0;
                }
            }
        }
        return new Matrix(km, min, true);
    }
}
