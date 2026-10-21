package com.servicepros.geo;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.servicepros.common.ApiException;
import com.servicepros.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

/**
 * Address look-up with Google Places API (New): suggestions while typing, then the full address + coordinates of
 * the picked suggestion. Address fields accept only a picked (verified) address. The key never leaves the server.
 */
@Slf4j
@Service
public class PlacesService {

    /** One suggestion in the dropdown. */
    public record AddressSuggestion(String placeId, String text, String mainText, String secondaryText) {
    }

    /** A verified address. {@code city} = the town Google reports (used to pre-select the City field). */
    public record PlaceDetails(String placeId, String address, double lat, double lng, String city, String state,
                               String postalCode) {
    }

    private final AppProperties.Google cfg;
    private final List<String> regions;
    private final RestClient places;
    private final RestClient geocode;

    public PlacesService(AppProperties props, RestClient.Builder builder) {
        this.cfg = props.getGoogle();
        this.regions = props.getGoogle().getPlacesRegions();
        this.places = builder.clone().baseUrl("https://places.googleapis.com/v1").build();
        this.geocode = builder.clone().baseUrl("https://maps.googleapis.com/maps/api/geocode").build();
    }

    public boolean isConfigured() {
        return cfg.isConfigured();
    }

    /** Suggestions for what was typed (at least 3 characters). */
    @SuppressWarnings("unchecked")
    public List<AddressSuggestion> autocomplete(String input, String sessionToken) {
        requireConfigured();
        String q = input == null ? "" : input.trim();
        if (q.length() < 3) return List.of();
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("input", q);
        if (!regions.isEmpty()) body.put("includedRegionCodes", regions);
        if (StringUtils.hasText(sessionToken)) body.put("sessionToken", sessionToken);
        try {
            Map<String, Object> res = places.post().uri("/places:autocomplete")
                    .header("X-Goog-Api-Key", cfg.getMapsApiKey())
                    .contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(Map.class);
            List<Map<String, Object>> suggestions = res == null ? List.of()
                    : (List<Map<String, Object>>) res.getOrDefault("suggestions", List.of());
            return suggestions.stream()
                    .map(s -> (Map<String, Object>) s.get("placePrediction"))
                    .filter(java.util.Objects::nonNull)
                    .map(p -> {
                        Map<String, Object> fmt = (Map<String, Object>) p.getOrDefault("structuredFormat", Map.of());
                        return new AddressSuggestion((String) p.get("placeId"), text(p.get("text")),
                                text(fmt.get("mainText")), text(fmt.get("secondaryText")));
                    })
                    .toList();
        } catch (Exception e) {
            log.warn("Address suggestions for '{}' failed: {}", q, e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Address lookup failed — please try again");
        }
    }

    /** Full address + coordinates of a picked suggestion; 400 when the id isn't a real place. */
    @SuppressWarnings("unchecked")
    public PlaceDetails details(String placeId, String sessionToken) {
        requireConfigured();
        if (!StringUtils.hasText(placeId) || !placeId.matches("[A-Za-z0-9_-]{10,300}")) {
            throw ApiException.badRequest("Choose the address from the suggestions list");
        }
        try {
            Map<String, Object> p = places.get()
                    .uri(u -> {
                        u.path("/places/{id}");
                        if (StringUtils.hasText(sessionToken)) u.queryParam("sessionToken", sessionToken);
                        return u.build(placeId);
                    })
                    .header("X-Goog-Api-Key", cfg.getMapsApiKey())
                    .header("X-Goog-FieldMask", "id,formattedAddress,location,addressComponents")
                    .retrieve().body(Map.class);
            Map<String, Object> loc = p == null ? null : (Map<String, Object>) p.get("location");
            if (loc == null || p.get("formattedAddress") == null) {
                throw ApiException.badRequest("Choose the address from the suggestions list");
            }
            String city = null, state = null, zip = null;
            for (Map<String, Object> c : (List<Map<String, Object>>) p.getOrDefault("addressComponents", List.of())) {
                List<String> types = (List<String>) c.getOrDefault("types", List.of());
                if (types.contains("locality") || (city == null && (types.contains("postal_town") || types.contains("sublocality")))) {
                    city = (String) c.get("longText");
                } else if (types.contains("administrative_area_level_1")) {
                    state = (String) c.get("shortText");
                } else if (types.contains("postal_code")) {
                    zip = (String) c.get("longText");
                }
            }
            return new PlaceDetails((String) p.get("id"), tidy((String) p.get("formattedAddress")),
                    ((Number) loc.get("latitude")).doubleValue(), ((Number) loc.get("longitude")).doubleValue(),
                    city, state, zip);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Place details for {} failed: {}", placeId, e.getMessage());
            throw ApiException.badRequest("Choose the address from the suggestions list");
        }
    }

    /** The address at a position (for "Get current location"), as a verified place. */
    @SuppressWarnings("unchecked")
    public Optional<PlaceDetails> reverse(double lat, double lng) {
        requireConfigured();
        try {
            Map<String, Object> res = geocode.get()
                    .uri(u -> u.path("/json").queryParam("latlng", lat + "," + lng)
                            .queryParam("result_type", "street_address|premise|subpremise")
                            .queryParam("key", cfg.getMapsApiKey()).build())
                    .retrieve().body(Map.class);
            if (res == null || !"OK".equals(res.get("status"))) return Optional.empty();
            var first = ((List<Map<String, Object>>) res.get("results")).getFirst();
            return Optional.of(details((String) first.get("place_id"), null));
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Reverse geocoding {},{} failed: {}", lat, lng, e.getMessage());
            return Optional.empty();
        }
    }

    private void requireConfigured() {
        if (!cfg.isConfigured()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Address lookup isn't set up (no Google Maps key) — type the address manually");
        }
    }

    @SuppressWarnings("unchecked")
    private static String text(Object o) {
        return o instanceof Map<?, ?> m ? (String) ((Map<String, Object>) m).get("text") : null;
    }

    /** "2100 Travis St, Houston, TX 77002, USA" → "2100 Travis St, Houston, TX 77002". */
    private static String tidy(String address) {
        return address.endsWith(", USA") ? address.substring(0, address.length() - 5) : address;
    }
}
