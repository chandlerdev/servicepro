package com.servicepros.vehicle;

import java.util.List;

import com.servicepros.common.ApiException;
import com.servicepros.route.ServiceRouteRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Vehicles are a tiny master with no business rules beyond "can't delete while a route uses it". */
@Tag(name = "Admin · Vehicles")
@RestController
@RequestMapping("/api/admin/vehicles")
@RequiredArgsConstructor
public class AdminVehicleController {

    public record VehicleRequest(@NotBlank @Size(max = 80) String name, @Size(max = 30) String plate,
                                 @Size(max = 60) String capacity) {
    }

    public record VehicleResponse(Long id, String name, String plate, String capacity) {
        static VehicleResponse from(Vehicle v) {
            return new VehicleResponse(v.getId(), v.getName(), v.getPlate(), v.getCapacity());
        }
    }

    private final VehicleRepository repo;
    private final ServiceRouteRepository routes;

    @GetMapping
    @Transactional(readOnly = true)
    public List<VehicleResponse> list() {
        return repo.findAll(Sort.by("name")).stream().map(VehicleResponse::from).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public VehicleResponse get(@PathVariable Long id) {
        return VehicleResponse.from(find(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public VehicleResponse create(@Valid @RequestBody VehicleRequest r) {
        Vehicle v = new Vehicle();
        apply(v, r);
        return VehicleResponse.from(repo.save(v));
    }

    @PutMapping("/{id}")
    @Transactional
    public VehicleResponse update(@PathVariable Long id, @Valid @RequestBody VehicleRequest r) {
        Vehicle v = find(id);
        apply(v, r);
        return VehicleResponse.from(v);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void delete(@PathVariable Long id) {
        Vehicle v = find(id);
        if (routes.existsByVehicleIdAndDeletedFalse(id)) {
            throw ApiException.conflict("Vehicle is assigned to a route — change the route's vehicle first");
        }
        repo.delete(v);
    }

    private Vehicle find(Long id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Vehicle", id));
    }

    private void apply(Vehicle v, VehicleRequest r) {
        v.setName(r.name().trim());
        v.setPlate(r.plate());
        v.setCapacity(r.capacity());
    }
}
