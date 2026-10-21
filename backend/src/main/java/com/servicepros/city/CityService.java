package com.servicepros.city;

import java.util.List;

import com.servicepros.common.ApiException;
import com.servicepros.common.PageResponse;
import com.servicepros.customer.CustomerRepository;
import com.servicepros.geo.GeocodingService;
import com.servicepros.geo.LatLng;
import com.servicepros.route.ServiceRouteRepository;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * City Master: ONE shared list of cities, managed by the Super Admin (/api/super/cities). Admins only read it
 * (/api/admin/cities) and their customers and routes must pick one of these.
 * Super Admin requests run unfiltered, so renames and usage checks below cover every admin's data.
 */
@Service
@RequiredArgsConstructor
public class CityService {

    /** lat/lng are optional — when blank the city name is looked up on Google Maps. */
    public record CityRequest(@NotBlank @Size(max = 80) String name,
                              @DecimalMin("-90") @DecimalMax("90") Double lat,
                              @DecimalMin("-180") @DecimalMax("180") Double lng) {
    }

    public record CityResponse(Long id, String name, double lat, double lng, long customers, long routes) {
    }

    /** Lightweight row for the admins' City dropdowns (no usage counts — cheap even for thousands of cities). */
    public record CityOption(Long id, String name, double lat, double lng) {
    }

    private final CityRepository repo;
    private final CustomerRepository customers;
    private final ServiceRouteRepository routes;
    private final GeocodingService geocoding;

    /** Every city, for the admins' dropdowns. */
    @Transactional(readOnly = true)
    public List<CityOption> options() {
        return repo.findAllByOrderByNameAsc().stream()
                .map(c -> new CityOption(c.getId(), c.getName(), c.getLat(), c.getLng())).toList();
    }

    /** Super Admin list: search by name + pages; usage counts only for the cities on the page. */
    @Transactional(readOnly = true)
    public PageResponse<CityResponse> page(String q, Integer page, Integer size) {
        String term = StringUtils.hasText(q) ? q.trim() : "";
        return PageResponse.of(repo.findByNameContainingIgnoreCase(term, PageResponse.pageable(page, size, Sort.by("name"))),
                this::toResponse);
    }

    @Transactional(readOnly = true)
    public CityResponse get(Long id) {
        return toResponse(find(id));
    }

    /**
     * An admin adds a city that isn't on the shared list yet (from a City dropdown). If it already exists (any case)
     * that city is returned instead; otherwise it is created and becomes available to every admin. The map centre is
     * looked up from the name, so a name Google can't find is refused.
     */
    @Transactional
    public CityOption addFromAdmin(CityRequest r) {
        City c = repo.findByNameIgnoreCase(r.name().trim()).orElseGet(() -> {
            City n = new City();
            apply(n, r);
            return repo.save(n);
        });
        return new CityOption(c.getId(), c.getName(), c.getLat(), c.getLng());
    }

    @Transactional
    public CityResponse create(CityRequest r) {
        City c = new City();
        apply(c, r);
        return toResponse(repo.save(c));
    }

    /** Renaming a city also renames it on every customer and route (of every admin) that uses it. */
    @Transactional
    public CityResponse update(Long id, CityRequest r) {
        City c = find(id);
        String oldName = c.getName();
        apply(c, r);
        if (!oldName.equals(c.getName())) {
            customers.findByCityIgnoreCase(oldName).forEach(x -> x.setCity(c.getName()));
            routes.findByCityIgnoreCase(oldName).forEach(x -> x.setCity(c.getName()));
        }
        return toResponse(c);
    }

    @Transactional
    public void delete(Long id) {
        City c = find(id);
        long usedBy = customers.countByCityIgnoreCaseAndDeletedFalse(c.getName())
                + routes.countByCityIgnoreCaseAndDeletedFalse(c.getName());
        if (usedBy > 0) {
            throw ApiException.conflict(c.getName() + " is used by " + usedBy
                    + " customer(s) / route(s) of your admins — they must move them to another city first");
        }
        repo.delete(c);
    }

    /** Returns the city (canonical spelling) or 400 if the Super Admin hasn't added it. */
    @Transactional(readOnly = true)
    public City require(String name) {
        String n = name == null ? "" : name.trim();
        return repo.findByNameIgnoreCase(n).orElseThrow(() -> ApiException.badRequest(
                "Unknown city '" + n + "' — ask the Super Admin to add it to the city list"));
    }

    private City find(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("City", id));
    }

    private void apply(City c, CityRequest r) {
        String name = r.name().trim();
        repo.findByNameIgnoreCase(name).filter(other -> !other.getId().equals(c.getId())).ifPresent(other -> {
            throw ApiException.conflict("City '" + other.getName() + "' already exists");
        });
        boolean renamed = c.getName() == null || !c.getName().equalsIgnoreCase(name);
        c.setName(name);
        if (r.lat() != null && r.lng() != null) {
            c.setLat(r.lat());
            c.setLng(r.lng());
        } else if (renamed || c.getId() == null) {
            LatLng p = geocoding.geocode(name).orElseThrow(() -> ApiException.badRequest(
                    "Couldn't find '" + name + "' on the map — enter its latitude and longitude"));
            c.setLat(p.lat());
            c.setLng(p.lng());
        }
    }

    /** Customer / route counts: all admins for the Super Admin, the admin's own for an admin. */
    private CityResponse toResponse(City c) {
        return new CityResponse(c.getId(), c.getName(), c.getLat(), c.getLng(),
                customers.countByCityIgnoreCaseAndDeletedFalse(c.getName()),
                routes.countByCityIgnoreCaseAndDeletedFalse(c.getName()));
    }
}
