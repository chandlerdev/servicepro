package com.servicepros.route;

import java.util.List;

import com.servicepros.route.RouteDtos.RouteRequest;
import com.servicepros.route.RouteDtos.RouteResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin · Routes")
@RestController
@RequestMapping("/api/admin/routes")
@RequiredArgsConstructor
public class AdminRouteController {

    private final ServiceRouteService service;

    @GetMapping
    public List<RouteResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public RouteResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RouteResponse create(@Valid @RequestBody RouteRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public RouteResponse update(@PathVariable Long id, @Valid @RequestBody RouteRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
