package com.servicepros.geo;

import java.util.List;

import com.servicepros.common.ApiException;
import com.servicepros.geo.PlacesService.AddressSuggestion;
import com.servicepros.geo.PlacesService.PlaceDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Address look-up for every signed-in user (admin pages, Super Admin, app). */
@Tag(name = "Address look-up")
@RestController
@RequestMapping("/api/geo")
@RequiredArgsConstructor
public class GeoController {

    private final PlacesService places;

    /** Suggestions while typing; {@code session} groups one typing session for Google billing. */
    @GetMapping("/autocomplete")
    public List<AddressSuggestion> autocomplete(@RequestParam String q, @RequestParam(required = false) String session) {
        return places.autocomplete(q, session);
    }

    /** Full address + coordinates of a picked suggestion. */
    @GetMapping("/place/{placeId}")
    public PlaceDetails place(@PathVariable String placeId, @RequestParam(required = false) String session) {
        return places.details(placeId, session);
    }

    /** The street address at a position (used by "Get current location"). */
    @GetMapping("/reverse")
    public PlaceDetails reverse(@RequestParam double lat, @RequestParam double lng) {
        return places.reverse(lat, lng).orElseThrow(() -> ApiException.notFound("Address at", lat + "," + lng));
    }
}
