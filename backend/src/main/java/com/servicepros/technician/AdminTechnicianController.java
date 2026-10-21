package com.servicepros.technician;

import java.util.List;

import com.servicepros.technician.TechnicianDtos.TechnicianRequest;
import com.servicepros.technician.TechnicianDtos.TechnicianResponse;
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

@Tag(name = "Admin · Technicians")
/** Technicians ("resources") of the logged-in admin; each gets an app login (email + password) when created. */
@RestController
@RequestMapping("/api/admin/technicians")
@RequiredArgsConstructor
public class AdminTechnicianController {

    private final TechnicianService service;

    @GetMapping
    public List<TechnicianResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public TechnicianResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TechnicianResponse create(@Valid @RequestBody TechnicianRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public TechnicianResponse update(@PathVariable Long id, @Valid @RequestBody TechnicianRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
