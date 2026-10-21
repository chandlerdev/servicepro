package com.servicepros.geo;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.servicepros.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** Google Geocoding API. Returns empty when no key is configured or the address can't be resolved. */
@Slf4j
@Service
public class GeocodingService {

    private final AppProperties.Google cfg;
    private final RestClient client;

    public GeocodingService(AppProperties props, RestClient.Builder builder) {
        this.cfg = props.getGoogle();
        this.client = builder.baseUrl("https://maps.googleapis.com/maps/api/geocode").build();
    }

    @SuppressWarnings("unchecked")
    public Optional<LatLng> geocode(String address) {
        if (!cfg.isConfigured() || address == null || address.isBlank()) {
            return Optional.empty();
        }
        try {
            Map<String, Object> res = client.get()
                    .uri(u -> u.path("/json").queryParam("address", address).queryParam("region", "us")
                            .queryParam("key", cfg.getMapsApiKey()).build())
                    .retrieve().body(Map.class);
            if (res == null || !"OK".equals(res.get("status"))) {
                log.warn("Geocoding '{}' returned {}", address, res == null ? "null" : res.get("status"));
                return Optional.empty();
            }
            var first = ((List<Map<String, Object>>) res.get("results")).getFirst();
            var loc = (Map<String, Object>) ((Map<String, Object>) first.get("geometry")).get("location");
            return Optional.of(new LatLng(((Number) loc.get("lat")).doubleValue(), ((Number) loc.get("lng")).doubleValue()));
        } catch (Exception e) {
            log.warn("Geocoding failed for '{}': {}", address, e.getMessage());
            return Optional.empty();
        }
    }
}
