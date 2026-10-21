package com.servicepros.customer;

import java.util.List;
import java.util.Map;

import com.servicepros.appointment.Appointment;
import com.servicepros.appointment.AppointmentRepository;
import com.servicepros.appointment.AppointmentStatus;
import com.servicepros.city.CityService;
import com.servicepros.city.City;
import com.servicepros.common.ApiException;
import com.servicepros.common.PageResponse;
import com.servicepros.common.TimeService;
import com.servicepros.customer.CustomerDtos.CustomerOption;
import com.servicepros.customer.CustomerDtos.CustomerRequest;
import com.servicepros.customer.CustomerDtos.CustomerResponse;
import com.servicepros.geo.GeocodingService;
import com.servicepros.geo.LatLng;
import com.servicepros.geo.PlacesService;
import com.servicepros.geo.PlacesService.PlaceDetails;
import com.servicepros.schedule.PlanStaleMarker;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private static final List<AppointmentStatus> OPEN = List.of(AppointmentStatus.SCHEDULED,
            AppointmentStatus.CONFIRMED, AppointmentStatus.EN_ROUTE, AppointmentStatus.DELAYED);

    static final String PICK_ADDRESS = "Choose the address from the suggestions list";

    private final CustomerRepository repo;
    private final CityService cities;
    private final GeocodingService geocoding;
    private final PlacesService places;
    private final AppointmentRepository appointments;
    private final PlanStaleMarker staleMarker;
    private final TimeService time;

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> search(String q, String city, Integer page, Integer size) {
        Specification<Customer> spec = (root, query, cb) -> {
            List<Predicate> ps = new java.util.ArrayList<>();
            ps.add(cb.isFalse(root.get("deleted")));
            if (StringUtils.hasText(q)) {
                String like = "%" + q.trim().toLowerCase() + "%";
                ps.add(cb.or(cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("address")), like),
                        cb.like(cb.lower(root.get("city")), like),
                        cb.like(root.get("phone"), like),
                        cb.like(cb.lower(root.get("email")), like)));
            }
            if (StringUtils.hasText(city)) {
                ps.add(cb.equal(cb.lower(root.get("city")), city.trim().toLowerCase()));
            }
            return cb.and(ps.toArray(Predicate[]::new));
        };
        return PageResponse.of(repo.findAll(spec, PageResponse.pageable(page, size, Sort.by("name"))),
                CustomerResponse::from);
    }

    @Transactional(readOnly = true)
    public List<CustomerOption> options() {
        return repo.findByDeletedFalseOrderByNameAsc().stream().map(CustomerOption::from).toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return CustomerResponse.from(find(id));
    }

    public Customer find(Long id) {
        return repo.findById(id).filter(c -> !c.isDeleted())
                .orElseThrow(() -> ApiException.notFound("Customer", id));
    }

    @Transactional
    public CustomerResponse create(CustomerRequest r) {
        Customer c = new Customer();
        apply(c, r);
        return CustomerResponse.from(repo.save(c));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest r) {
        Customer c = find(id);
        String oldAddress = c.getAddress();
        double oldLat = c.getLat(), oldLng = c.getLng();
        apply(c, r);
        boolean moved = !c.getAddress().equals(oldAddress) || c.getLat() != oldLat || c.getLng() != oldLng;
        if (moved) {
            staleMarker.markAll(appointments.findUpcomingForCustomer(id, time.today(), OPEN));
        }
        return CustomerResponse.from(c);
    }

    /** Cancels open appointments and soft-deletes so job history keeps its customer. */
    @Transactional
    public void delete(Long id) {
        Customer c = find(id);
        List<Appointment> open = appointments.findUpcomingForCustomer(id, java.time.LocalDate.of(2000, 1, 1), OPEN);
        open.forEach(a -> a.setStatus(AppointmentStatus.CANCELLED));
        staleMarker.markAll(open);
        c.setDeleted(true);
    }

    private void apply(Customer c, CustomerRequest r) {
        City city = cities.require(r.city());
        boolean addressChanged = c.getAddress() == null || !c.getAddress().equalsIgnoreCase(r.address().trim());
        c.setName(r.name().trim());
        c.setPhone(r.phone().trim());
        c.setEmail(StringUtils.hasText(r.email()) ? r.email().trim().toLowerCase() : null);
        c.setCity(city.getName());
        if (places.isConfigured()) {
            // only an address picked from the suggestions is accepted; Google supplies the exact address + coordinates
            if (StringUtils.hasText(r.placeId())) {
                PlaceDetails d = places.details(r.placeId(), r.placeSession());
                c.setAddress(d.address());
                c.setLat(d.lat());
                c.setLng(d.lng());
                return;
            }
            if (addressChanged) {
                throw new ApiException(HttpStatus.BAD_REQUEST, PICK_ADDRESS, Map.of("address", PICK_ADDRESS));
            }
            return; // address unchanged — keep it and its coordinates
        }
        // no Google key (local / tests): accept the typed address, geocoding falls back to the city centre
        c.setAddress(r.address().trim());
        if (r.lat() != null && r.lng() != null) {
            c.setLat(r.lat());
            c.setLng(r.lng());
        } else if (addressChanged || (c.getLat() == 0 && c.getLng() == 0)) {
            LatLng p = geocoding.geocode(c.getAddress())
                    .orElse(new LatLng(city.getLat(), city.getLng()));
            c.setLat(p.lat());
            c.setLng(p.lng());
        }
    }
}
